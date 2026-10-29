import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class LocalManager implements AutoCloseable {
    @FunctionalInterface interface FolderChooser { Path choose() throws IOException; }
    private final Path root;
    private final Toolbox toolbox;
    private final FolderChooser chooser;
    private final HttpServer management;
    private final HttpServer content;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final String token = randomToken();
    private ImportPlan pending;
    private String previewId;

    public LocalManager(Path root) throws IOException {
        this(root, LocalManager::chooseNativeFolder);
    }

    LocalManager(Path root, FolderChooser chooser) throws IOException {
        this.root = root.toRealPath();
        this.toolbox = new Toolbox(this.root);
        this.chooser = chooser;
        management = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        try {
            content = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        } catch (IOException e) {
            management.stop(0);
            executor.close();
            throw e;
        }
        management.createContext("/", this::manage);
        content.createContext("/", this::serveContent);
        management.setExecutor(executor);
        content.setExecutor(executor);
        management.start();
        content.start();
    }

    public String url() { return origin(management) + "/"; }
    String contentUrl() { return origin(content) + "/"; }
    private static String origin(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static Path chooseNativeFolder() throws IOException {
        if (GraphicsEnvironment.isHeadless())
            throw new IOException("Native folder selection requires a graphical desktop on the computer running Java.");
        Path[] selection = new Path[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser dialog = new JFileChooser();
                dialog.setDialogTitle("Vibe Toolbox - select a project folder");
                dialog.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                dialog.setAcceptAllFileFilterUsed(false);
                if (dialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                    selection[0] = dialog.getSelectedFile().toPath();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Folder selection interrupted.", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IOException("Could not open folder chooser: " + e.getCause(), e);
        }
        return selection[0];
    }

    private boolean validHost(HttpExchange exchange, HttpServer server) {
        return exchange.getRequestHeaders().getOrDefault("Host", List.of()).equals(
                List.of("127.0.0.1:" + server.getAddress().getPort()));
    }

    private void manage(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                manageRequest(exchange);
            } catch (IOException | IllegalArgumentException e) {
                System.err.println("Management request failed: " + e.getMessage());
                if (exchange.getResponseCode() == -1)
                    send(exchange, 400, page("<h2>Import not completed</h2><pre>" + html(e.getMessage())
                            + "</pre><p>Return home to select and review again. Existing projects are never overwritten.</p>"), "text/html");
                else throw e;
            }
        }
    }

    private void manageRequest(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none'; style-src 'nonce-" + token
                        + "'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        if (!validHost(exchange, management)) {
            send(exchange, 403, "Invalid Host", "text/plain"); return;
        }
        String path = exchange.getRequestURI().getPath();
        if (exchange.getRequestMethod().equals("GET")) {
            if (path.equals("/")) send(exchange, 200, page(home(exchange.getRequestURI().getQuery())), "text/html");
            else if (path.equals("/about")) send(exchange, 200, page(about()), "text/html");
            else if (path.matches("/project/[a-z][a-z0-9]*(?:_[a-z0-9]+)*"))
                send(exchange, 200, page(details(path.substring(9))), "text/html");
            else send(exchange, 404, page("<h2>Not found</h2>"), "text/html");
            return;
        }
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, "Use GET or POST.", "text/plain"); return;
        }
        if (!exchange.getRequestHeaders().getOrDefault("Origin", List.of()).equals(List.of(origin(management)))) {
            send(exchange, 403, "Invalid Origin", "text/plain"); return;
        }
        if (!"application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
            send(exchange, 415, "Expected a form submission.", "text/plain"); return;
        }
        Map<String, String> form = form(exchange);
        if (!token.equals(form.get("token"))) {
            send(exchange, 403, "Invalid launch token", "text/plain"); return;
        }
        synchronized (this) {
            switch (path) {
                case "/choose" -> {
                    pending = null;
                    previewId = null;
                    Path source = chooser.choose();
                    if (source == null) {
                        redirectHome(exchange, "selection_cancelled");
                        return;
                    }
                    ImportPlan plan = ImportPlan.inspect(source);
                    if (!Files.isDirectory(plan.source())) throw new IOException("Select a folder, not a file.");
                    if (root.startsWith(plan.source()) || plan.source().startsWith(root.resolve("projects")))
                        throw new IOException("Select a source outside the collection's projects; overlapping imports are unsafe.");
                    if (plan.files().isEmpty()) throw new IOException("No importable files remain after exclusions.");
                    pending = plan;
                    previewId = randomToken();
                    send(exchange, 200, page(preview()), "text/html");
                }
                case "/cancel" -> {
                    requirePreview(form);
                    pending = null;
                    previewId = null;
                    redirectHome(exchange, "cancelled");
                }
                case "/confirm" -> confirm(exchange, form);
                default -> send(exchange, 404, "Unknown action", "text/plain");
            }
        }
    }

    private void confirm(HttpExchange exchange, Map<String, String> form) throws IOException {
        requirePreview(form);
        String slug = required(form, "slug"), name = required(form, "name");
        String description = required(form, "description");
        Path main = Path.of(required(form, "main"));
        if (!pending.files().containsKey(main)) throw new IllegalArgumentException("Select a reviewed main file.");
        Toolbox.Check before = toolbox.check(true);
        if (before.errors() != 0)
            throw new IOException("Fix existing collection errors before importing:\n" + String.join("\n", before.messages()));
        toolbox.add(slug, name, pending, description, main);
        pending = null;
        previewId = null;
        String link = "<p><a id=\"details\" href=\"/project/" + slug + "\">View imported project details</a></p>";
        try {
            Toolbox.Check check = toolbox.check(true);
            if (check.errors() != 0) {
                send(exchange, 409, page("<h2>Project imported; catalog needs repair</h2>"
                        + "<p>Original files were preserved. Catalog was not updated because validation failed. "
                        + "Review supplied Markdown links and repair collection documentation without altering originals.</p>"
                        + link + "<p>See project Details and Help for diagnostics.</p>" + home(null)), "text/html");
            } else {
                toolbox.catalog(check);
                redirectHome(exchange, "imported&project=" + slug);
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Imported project " + slug + "; catalog failed: " + e.getMessage());
            if (exchange.getResponseCode() != -1) throw e;
            send(exchange, 500, page("<h2>Project imported; catalog generation failed</h2>"
                    + link + "<pre>" + html(e.getMessage()) + "</pre>" + home(null)), "text/html");
        }
    }

    private void requirePreview(Map<String, String> form) {
        if (pending == null || !Objects.equals(previewId, form.get("preview")))
            throw new IllegalArgumentException("Preview expired. Return home and select the folder again.");
    }

    private static String required(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(65537);
        if (bytes.length > 65536) throw new IllegalArgumentException("Form is too large.");
        Map<String, String> fields = new HashMap<>();
        if (bytes.length == 0) return fields;
        for (String pair : new String(bytes, StandardCharsets.UTF_8).split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid form field.");
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (fields.put(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null)
                throw new IllegalArgumentException("Duplicate form field.");
        }
        return fields;
    }

    private void redirectHome(HttpExchange exchange, String status) throws IOException {
        exchange.getResponseHeaders().set("Location", "/?status=" + status);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(303, -1);
    }

    private String home(String query) throws IOException {
        StringBuilder text = new StringBuilder();
        if ("status=selection_cancelled".equals(query))
            text.append("<section class=\"notice\" role=\"status\"><h2>Selection cancelled</h2><p>No project was imported.</p></section>");
        else if ("status=cancelled".equals(query))
            text.append("<section class=\"notice\" role=\"status\"><h2>Import cancelled</h2><p>No files were copied.</p></section>");
        else if (query != null && query.matches("status=imported&project=[a-z][a-z0-9]*(?:_[a-z0-9]+)*")) {
            String slug = query.substring(query.indexOf("&project=") + 9);
            text.append("<section class=\"notice\" role=\"status\"><h2 id=\"success\">Import successful</h2>")
                    .append("<p>Project added; offline catalog regenerated.</p><a id=\"details\" href=\"/project/")
                    .append(slug).append("\">View imported project details</a></section>");
        }
        text.append(formStart("/choose")).append("<button id=\"choose\">Add project</button></form>");
        Toolbox.Check check = toolbox.check(true);
        if (check.errors() != 0)
            text.append("<p role=\"alert\">Some project records need repair. <a href=\"/about\">View diagnostics</a>.</p>");
        text.append("<section class=\"cards\" aria-label=\"Apps\">");
        for (Toolbox.Project project : check.projects()) {
            String slug = project.directory().getFileName().toString();
            String description = project.fields().getOrDefault("Use", "Description not supplied.");
            ImportPlan.rejectSymlinks(project.directory().resolve("README.md"));
            String readme = Files.readString(project.directory().resolve("README.md"));
            if (description.equals("TBD")) {
                var intro = Pattern.compile("(?s)^# [^\\n]+\\n\\s*([^#].*?)\\n## Run\\s*\\n").matcher(readme);
                description = intro.find() ? intro.group(1).strip() : "Description not supplied.";
            }
            String app = htmlMain(project, readme);
            text.append("<article><h2>").append(html(project.title())).append("</h2><p>")
                    .append(html(description)).append("</p><div class=\"card-actions\">");
            if (app != null)
                text.append("<a class=\"action\" target=\"_blank\" rel=\"noopener noreferrer\" href=\"")
                        .append(contentUrl()).append("projects/").append(slug).append("/").append(html(app))
                        .append("\">Open app</a>");
            else text.append("<a class=\"action\" href=\"/project/").append(slug).append("#run\">Run instructions</a>");
            text.append(" <a class=\"details\" href=\"/project/").append(slug).append("\">Details</a></div></article>");
        }
        if (check.projects().isEmpty()) text.append("<p>No projects yet. Use Add project to get started.</p>");
        return text.append("</section><p><a href=\"/about\">Help and technical information</a></p>").toString();
    }

    private String htmlMain(Toolbox.Project project, String readme) throws IOException {
        List<String> candidates = new ArrayList<>();
        var selected = Pattern.compile("\\[selected HTML main file]\\(([^)]+)\\)").matcher(readme);
        if (selected.find()) candidates.add(selected.group(1));
        var files = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)").matcher(project.fields().getOrDefault("Files", ""));
        while (files.find()) candidates.add(files.group(1));
        for (String candidate : candidates) {
            URI reference = URI.create(candidate);
            String decoded = reference.getPath();
            if (reference.isAbsolute() || reference.getRawAuthority() != null || decoded == null
                    || decoded.contains("\\") || decoded.contains(":") || !decoded.toLowerCase(Locale.ROOT).matches(".*\\.html?"))
                continue;
            Path relative = Path.of(decoded);
            Path file = project.directory().resolve(relative).normalize();
            if (relative.isAbsolute() || !relative.equals(relative.normalize()) || !file.startsWith(project.directory())
                    || ImportPlan.exclusion(relative) != null) continue;
            ImportPlan.rejectSymlinks(file);
            if (Files.isRegularFile(file)) return Toolbox.url(relative);
        }
        return null;
    }

    private String about() throws IOException {
        return """
                <h2>Local management and import help</h2>
                <p>The live catalog is separate from the generated offline catalog.</p>
                <p>Click Add project to open a native Java folder chooser on this computer.
                It may appear behind your browser. Select a folder, then review files and details here
                before confirming. Cancelling never copies files. No source path typing is needed.</p>
                <p>Imports exclude known VCS metadata, IDE settings, build/dependency outputs, and secret
                filenames. This is not a content-based secret scan: review every included file before confirming.</p>
                """ + "<p><a href=\"" + contentUrl() + "index.html\">Open offline catalog (read-only)</a></p>"
                + "<h3>Collection diagnostics</h3><pre>" + html(String.join("\n", toolbox.check(true).messages())) + "</pre>";
    }

    private String formStart(String action) {
        return "<form method=\"post\" action=\"" + action + "\"><input type=\"hidden\" name=\"token\" value=\"" + token + "\">";
    }

    private String preview() {
        String name = pending.source().getFileName().toString();
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (!slug.matches("[a-z].*")) slug = "project_" + slug;
        StringBuilder text = new StringBuilder("<h2>Review import</h2><p>Selected folder: <strong id=\"source\">"
                + html(pending.source().toString()) + "</strong></p><p>Source files will not be modified. "
                + "Retained bytes and paths, including available history, are preserved under original. Unknown metadata stays TBD.</p>");
        text.append("<h3>Included files (").append(pending.files().size()).append(")</h3><ul id=\"included\">");
        pending.files().keySet().forEach(p -> text.append("<li>").append(html(p.toString())).append("</li>"));
        text.append("</ul><h3>Exclusions (").append(pending.exclusions().size()).append(")</h3><ul id=\"excluded\">");
        pending.exclusions().forEach((p, reason) -> text.append("<li>").append(html(p.toString()))
                .append(" - ").append(html(reason)).append("</li>"));
        if (pending.exclusions().isEmpty()) text.append("<li>None.</li>");
        text.append("</ul>").append(formStart("/confirm")).append(previewField())
                .append("<label>Project folder (snake_case)<input name=\"slug\" required pattern=\"[a-z][a-z0-9]*(_[a-z0-9]+)*\" value=\"")
                .append(html(slug)).append("\"></label><label>Project name<input name=\"name\" required maxlength=\"200\" value=\"")
                .append(html(name)).append("\"></label><label>Description<input name=\"description\" required maxlength=\"2000\" value=\"TBD\"></label>")
                .append("<label>Main file<select name=\"main\" required>");
        pending.files().keySet().forEach(p -> text.append("<option value=\"").append(html(p.toString())).append("\">")
                .append(html(p.toString())).append("</option>"));
        return text.append("</select></label><p>HTML opens in a sandboxed browser view. Arbitrary imported Java is not executed; details show run guidance.</p>")
                .append("<button id=\"confirm\">Confirm import</button></form>").append(formStart("/cancel")).append(previewField())
                .append("<button id=\"cancel\">Cancel import</button></form>").toString();
    }

    private String previewField() {
        return "<input type=\"hidden\" name=\"preview\" value=\"" + previewId + "\">";
    }

    private String details(String slug) throws IOException {
        Path dir = root.resolve("projects").resolve(slug);
        ImportPlan.rejectSymlinks(dir);
        if (!Files.isDirectory(dir)) throw new IOException("Project not found.");
        ImportPlan.rejectSymlinks(dir.resolve("README.md"));
        ImportPlan.rejectSymlinks(dir.resolve("prompts.md"));
        String readme = Files.readString(dir.resolve("README.md"));
        String record = Files.readString(dir.resolve("prompts.md"));
        String title = record.lines().filter(line -> line.startsWith("# Project: ")).findFirst().orElse("# Project: " + slug).substring(11);
        var main = Pattern.compile("\\[(?:selected HTML main file|main/source file)]\\(([^)]+)\\)").matcher(readme);
        String action = "";
        if (main.find()) {
            String target = main.group(1);
            URI reference = URI.create(target);
            String decoded = reference.getPath();
            boolean bundledWheelApp = slug.equals("student_spin_wheel")
                    && "student_spin_wheel.html".equals(decoded);
            if (reference.isAbsolute() || reference.getRawAuthority() != null || decoded == null
                    || decoded.contains("\\") || decoded.contains(":")
                    || (!decoded.startsWith("original/") && !bundledWheelApp))
                throw new IOException("Invalid main-file link.");
            Path relative = Path.of(decoded);
            if (!relative.equals(relative.normalize()) || relative.isAbsolute())
                throw new IOException("Invalid main-file path.");
            boolean htmlApp = decoded.toLowerCase(Locale.ROOT).matches(".*\\.html?");
            action = htmlApp ? "<p><a id=\"openApp\" target=\"_blank\" rel=\"noopener noreferrer\" href=\""
                    + contentUrl() + "projects/" + slug + "/" + html(target) + "\">Open HTML app</a>"
                    + " (isolated read-only content origin; app code is not trusted management code)</p>"
                    : "<h3>Run instructions</h3><p>Imported Java apps are not executed by the manager. "
                    + "Review the README below for JDK 21 guidance and any supplied instructions.</p>";
        }
        StringBuilder originals = new StringBuilder("<h3>Preserved original files and available history</h3><ul>");
        Path original = dir.resolve("original");
        ImportPlan.rejectSymlinks(original);
        if (Files.isDirectory(original)) {
            try (var paths = Files.walk(original)) {
                for (Path file : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    Path relative = dir.relativize(file);
                    if (ImportPlan.exclusion(relative) == null)
                        originals.append("<li><a target=\"_blank\" rel=\"noopener noreferrer\" href=\"")
                                .append(contentUrl()).append("projects/").append(slug).append("/")
                                .append(html(Toolbox.url(relative))).append("\">").append(html(relative.toString()))
                                .append("</a></li>");
                }
            }
        }
        originals.append("</ul>");
        return "<h2 id=\"projectTitle\">" + html(title) + "</h2>" + action
                + "<h3 id=\"run\">Project README: description, run instructions, evidence</h3><pre id=\"readme\">" + html(readme) + "</pre>"
                + originals + "<p><a href=\"" + contentUrl() + "projects/" + slug + "/prompts.md\">Canonical metadata/history (unknowns TBD)</a></p>";
    }

    private String page(String body) {
        return "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>Vibe Toolbox</title><style nonce=\"" + token + "\">"
                + "body{font:1rem system-ui;max-width:65rem;margin:2rem auto;padding:0 1rem;line-height:1.6}"
                + "label{display:block;margin:1rem 0}input,select{display:block;width:100%;font:inherit}"
                + "button{font:inherit;padding:.6rem 1rem;margin:.5rem 0}pre{white-space:pre-wrap;overflow-wrap:anywhere}"
                + "li,strong{overflow-wrap:anywhere}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,18rem),1fr));gap:1rem}"
                + "article{border:1px solid #bbc6d8;border-radius:.5rem;padding:1rem;overflow-wrap:anywhere}"
                + "article h2{margin-top:0}.action,#choose{background:#174c9c;color:white;border:0;border-radius:.35rem;"
                + "padding:.6rem 1rem;text-decoration:none;display:inline-block;cursor:pointer}.details{margin-left:.5rem}"
                + ".card-actions{display:flex;align-items:center;gap:.5rem}.card-actions form{margin:0}"
                + ".notice{background:#edf4f7;padding:1rem;border-radius:.5rem}"
                + "</style><header><h1>Vibe Toolbox</h1><nav><a href=\"/\">Catalog</a></nav></header><main>"
                + body + "</main></html>";
    }

    private void serveContent(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "sandbox allow-scripts; default-src 'self' data: blob:; script-src 'self' 'unsafe-inline'; "
                            + "style-src 'self' 'unsafe-inline'; connect-src 'none'; base-uri 'none'; form-action 'none'");
            if (!validHost(exchange, content)) { send(exchange, 403, "Invalid Host", "text/plain"); return; }
            if (!exchange.getRequestMethod().equals("GET")) { send(exchange, 405, "Read only", "text/plain"); return; }
            String name = exchange.getRequestURI().getPath();
            if (name.contains("\\") || name.contains(":") || name.contains("\0")) {
                send(exchange, 403, "Unsafe path", "text/plain"); return;
            }
            Path relative = Path.of(name.startsWith("/") ? name.substring(1) : name);
            Path file = root.resolve(relative).normalize();
            if (!relative.equals(relative.normalize()) || !file.startsWith(root) || relative.isAbsolute()
                    || ImportPlan.exclusion(relative) != null
                    || !(name.equals("/index.html") || name.equals("/README.md") || name.equals("/DESIGN.md")
                    || name.startsWith("/projects/")) || name.contains("/.import-")) {
                send(exchange, 403, "Not served", "text/plain"); return;
            }
            try {
                ImportPlan.rejectSymlinks(file);
                if (!Files.isRegularFile(file)) { send(exchange, 404, "File not found", "text/plain"); return; }
                String type = file.toString().toLowerCase(Locale.ROOT);
                String mime = type.endsWith(".html") || type.endsWith(".htm") ? "text/html"
                        : type.endsWith(".js") ? "text/javascript" : type.endsWith(".css") ? "text/css"
                        : type.endsWith(".svg") ? "image/svg+xml" : type.endsWith(".png") ? "image/png"
                        : type.endsWith(".jpg") || type.endsWith(".jpeg") ? "image/jpeg" : "text/plain";
                exchange.getResponseHeaders().set("Content-Type", mime + "; charset=utf-8");
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                long length = Files.size(file);
                exchange.sendResponseHeaders(200, length == 0 ? -1 : length);
                if (length != 0) Files.copy(file, exchange.getResponseBody());
            } catch (IOException e) {
                if (exchange.getResponseCode() == -1) send(exchange, 403, "File unavailable: " + e.getMessage(), "text/plain");
                else throw e;
            }
        }
    }

    static String html(String value) {
        return Toolbox.escape(value);
    }

    private static void send(HttpExchange exchange, int status, String body, String type) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() {
        management.stop(0);
        content.stop(0);
        executor.shutdownNow();
    }
}
