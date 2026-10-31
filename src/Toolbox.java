import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Toolbox {
    private static final List<String> HEADER = List.of("Files", "Dates", "Defaults", "Status", "Use",
            "Reusability", "Tags", "Takeaway", "Story value", "Surprises");
    private static final Set<String> TAGS = Set.of("single-file-html", "classroom-tool", "teaching-example",
            "game", "data-viz", "presentation", "productivity", "print-pdf", "audio", "drag-drop",
            "canvas", "physics", "localStorage", "export-import", "theming", "source-material", "brainstorm", "site");
    private static final Set<String> CONTEXT = Set.of("Tool", "Verified", "Human edits", "Session", "Time");
    private static final Pattern FIELD = Pattern.compile("^> - \\*\\*([^*]+):\\*\\*\\s*(.*)$");
    private static final Pattern SECTION = Pattern.compile("^(# Prompt|## Output) (\\d+)(?:[ :].*)?$");
    private static final Pattern LINK = Pattern.compile("!?\\[[^\\]\\r\\n]*]\\(([^)\\r\\n]*)\\)");
    private final Path root;

    public Toolbox(Path root) throws IOException {
        this.root = root.toRealPath();
    }

    public record Project(Path directory, String title, Map<String, String> fields, List<String> verification) {}
    public record Check(List<Project> projects, List<String> messages, int errors, int warnings) {}

    private static final class Diagnostics {
        final List<String> messages = new ArrayList<>();
        final boolean generatingCatalog;
        int errors;
        int warnings;
        Diagnostics(boolean generatingCatalog) { this.generatingCatalog = generatingCatalog; }
        void error(Path path, String message) { errors++; messages.add("ERROR " + path + ": " + message); }
        void warn(Path path, String message) { warnings++; messages.add("WARN " + path + ": " + message); }
    }

    public void add(String slug, String title, Path source) throws IOException {
        add(slug, title, source == null ? null : ImportPlan.inspect(source), null, null);
    }

    public void add(String slug, String title, ImportPlan plan, String description, Path mainFile) throws IOException {
        if (!slug.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"))
            throw new IllegalArgumentException("Use a snake_case slug starting with a letter.");
        if (title.isBlank() || title.contains("\n") || title.contains("\r"))
            throw new IllegalArgumentException("Title must be one nonblank line.");
        Path projects = root.resolve("projects");
        if (Files.isSymbolicLink(projects)) throw new IOException("projects must not be a symlink");
        Files.createDirectories(projects);
        Path destination = projects.resolve(slug);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Destination already exists: " + destination);
        if (plan != null) {
            Path input = plan.source();
            if (destination.startsWith(input) || input.startsWith(destination))
                throw new IOException("Source and destination must not overlap");
            if (!plan.equals(ImportPlan.inspect(input)))
                throw new IOException("Source changed since preview. Select the folder again and review it.");
            if (mainFile != null && !plan.files().containsKey(mainFile))
                throw new IllegalArgumentException("Main file must be one of the reviewed files.");
        }
        Path stage = Files.createTempDirectory(projects, ".import-");
        try {
            List<Path> deliverables = new ArrayList<>();
            if (plan != null) {
                Path original = Files.createDirectory(stage.resolve("original"));
                for (var entry : plan.files().entrySet()) {
                    Path relative = entry.getKey();
                    Path target = original.resolve(relative);
                    Files.createDirectories(target.getParent());
                    ImportPlan.rejectSymlinks(plan.file(relative));
                    copyVerified(plan.file(relative), target);
                    if (!ImportPlan.digest(target).equals(entry.getValue()))
                        throw new IOException("Source changed during copy: " + relative);
                    deliverables.add(Path.of("original").resolve(relative));
                }
                if (deliverables.isEmpty()) throw new IOException("Import contains no files");
                if (!plan.equals(ImportPlan.inspect(plan.source())))
                    throw new IOException("Source changed during import; select and review again.");
            }
            String files = deliverables.isEmpty() ? "TBD" : String.join(", ", deliverables.stream()
                    .map(p -> "[" + url(p.getFileName()) + "](" + url(p) + ")").toList());
            Files.writeString(stage.resolve("prompts.md"), template(title, files), StandardCharsets.UTF_8);
            Files.writeString(stage.resolve("README.md"), """
                    # Run and evidence

                    ## Run
                    TBD

                    ## Test results
                    TBD - no tests recorded. Importing does not verify an app.

                    ## Known limitations
                    TBD
                    """, StandardCharsets.UTF_8);
            if (plan != null && mainFile != null) {
                String main = url(Path.of("original").resolve(mainFile));
                String run = mainFile.toString().toLowerCase(Locale.ROOT).endsWith(".html")
                        || mainFile.toString().toLowerCase(Locale.ROOT).endsWith(".htm")
                        ? "Open the [selected HTML main file](" + main + ") in a browser."
                        : "Selected [main/source file](" + main + ").\n\n"
                        + "For Java apps, use JDK 21 and inspect the supplied README/build instructions first.\n"
                        + "Compile into a separate output directory, never into original. Typical commands are\n"
                        + "`javac --release 21 -d <output-directory> <source-files>` and\n"
                        + "`java -cp <output-directory> <fully-qualified-main-class>`.\n"
                        + "Exact source set, dependencies, main class, and arguments: TBD until verified.";
                String safeDescription = description == null || description.isBlank() ? "TBD"
                        : description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        .replace("\\", "\\\\").replaceAll("([`*_$\\[\\]()#!|])", "\\\\$1")
                        .replace("\r", " ").replace("\n", " ");
                Files.writeString(stage.resolve("README.md"), "# Run and evidence\n\n" + safeDescription
                        + "\n\n## Run\n" + run + "\n\nSupplied files and any available history are preserved under "
                        + "`original`. Unknown history remains `TBD` in [the canonical record](prompts.md).\n"
                        + "\n## Test results\nTBD - importing does not verify the app.\n"
                        + "\n## Known limitations\nTBD - review supplied instructions and files before running.\n"
                        + "\n## Import exclusions\n"
                        + (plan.exclusions().isEmpty() ? "None.\n" : String.join("\n", plan.exclusions().entrySet().stream()
                        .map(e -> "- " + url(e.getKey()) + ": " + e.getValue()).toList()) + "\n"),
                        StandardCharsets.UTF_8);
            }
            Files.move(stage, destination);
        } finally {
            if (Files.exists(stage)) {
                try (var paths = Files.walk(stage)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }

    private static void copyVerified(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) throw new IOException("Symlinks are not supported: " + source);
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target);
        } else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(source, target);
            if (!ImportPlan.digest(source).equals(ImportPlan.digest(target)))
                throw new IOException("Import hash mismatch: " + source);
        } else throw new IOException("Unsupported import entry: " + source);
    }

    private static String template(String title, String files) {
        StringBuilder text = new StringBuilder("# Project: " + title + "\n\n");
        for (String field : HEADER) text.append("> - **").append(field).append(":** ")
                .append(field.equals("Files") ? files : "TBD").append('\n');
        return text + """

                # Prompt 1 (history unavailable):

                > - **Tool:** TBD
                > - **Verified:** TBD
                > - **Human edits:** TBD

                TBD

                ## Output 1:

                TBD
                """;
    }

    public Check check() throws IOException {
        return check(false);
    }

    public Check check(boolean generatingCatalog) throws IOException {
        Diagnostics d = new Diagnostics(generatingCatalog);
        List<Project> records = new ArrayList<>();
        Path projects = root.resolve("projects");
        if (!Files.isDirectory(projects) || Files.isSymbolicLink(projects)) {
            d.error(projects, "Expected a projects directory, not a symlink");
            return new Check(records, d.messages, d.errors, d.warnings);
        }
        try (var children = Files.list(projects)) {
            for (Path dir : children.sorted().toList()) {
                if (dir.getFileName().toString().startsWith(".import-")) {
                    d.error(dir, "Incomplete import directory; inspect and remove before publishing");
                    continue;
                }
                if (!Files.isDirectory(dir) || Files.isSymbolicLink(dir)) {
                    d.error(dir, "Expected a project directory, not a file/symlink");
                    continue;
                }
                if (!dir.getFileName().toString().matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"))
                    d.error(dir, "Folder must be snake_case");
                Path prompts = dir.resolve("prompts.md");
                if (!Files.isRegularFile(prompts) || Files.isSymbolicLink(prompts)) {
                    d.error(prompts, "Missing regular prompts.md");
                    continue;
                }
                records.add(readProject(dir, d));
                Path readme = dir.resolve("README.md");
                if (!Files.isRegularFile(readme) || Files.isSymbolicLink(readme)) d.error(readme, "Missing regular README.md");
                else {
                    String text = visibleDocument(readme, d);
                    for (String section : List.of("Run", "Test results", "Known limitations"))
                        if (!Pattern.compile("(?m)^## " + section + "\\s*$").matcher(text).find())
                            d.error(readme, "Missing section: " + section);
                    if (text.contains("TBD")) d.warn(readme, "Incomplete instructions/evidence (TBD)");
                }
                try (var paths = Files.walk(dir)) {
                    for (Path file : paths.sorted().toList()) {
                        if (Files.isSymbolicLink(file)) d.error(file, "Symlinks are not supported");
                        else if (Files.isRegularFile(file) && file.toString().endsWith(".md"))
                            checkLinks(file, visibleDocument(file, d), d);
                    }
                }
            }
        }
        for (String name : List.of("README.md", "DESIGN.md")) {
            Path file = root.resolve(name);
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) d.error(file, "Missing regular collection document");
            else checkLinks(file, visibleDocument(file, d), d);
        }
        return new Check(List.copyOf(records), List.copyOf(d.messages), d.errors, d.warnings);
    }

    private Project readProject(Path dir, Diagnostics d) throws IOException {
        Path file = dir.resolve("prompts.md");
        String raw = Files.readString(file);
        String text;
        try { text = outsideFences(raw); }
        catch (IllegalArgumentException e) {
            d.error(file, e.getMessage());
            return new Project(dir, "", Map.of(), List.of());
        }
        Map<String, String> header = new LinkedHashMap<>();
        Map<String, String> context = null;
        List<String> verification = new ArrayList<>();
        String title = "";
        int prompt = 0;
        boolean output = false;
        boolean body = false;
        for (String line : text.lines().toList()) {
            if (line.startsWith("# Project: ") && prompt == 0) {
                if (!title.isEmpty()) d.error(file, "Duplicate project title");
                title = line.substring(11).strip();
                continue;
            }
            Matcher section = SECTION.matcher(line);
            if (section.matches()) {
                boolean isPrompt = section.group(1).equals("# Prompt");
                String number = section.group(2);
                if (isPrompt) {
                    if (prompt > 0 && (!output || !body)) d.error(file, "Missing output body for prompt " + prompt);
                    if (!number.equals(Integer.toString(prompt + 1))) d.error(file, "Prompts must be numbered consecutively");
                    prompt++;
                    context = new LinkedHashMap<>();
                    output = false;
                } else {
                    if (prompt == 0 || output || !number.equals(Integer.toString(prompt)))
                        d.error(file, "Output must match its preceding prompt");
                    if (!body) d.error(file, "Missing prompt body for prompt " + prompt);
                    validateContext(file, context, verification, d);
                    output = true;
                }
                body = false;
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.matches() && !output) {
                Map<String, String> fields = prompt == 0 ? header : context;
                if (fields != null && fields.putIfAbsent(field.group(1), field.group(2).strip()) != null)
                    d.error(file, "Duplicate field: " + field.group(1));
            } else if (prompt > 0 && !line.isBlank()) body = true;
        }
        if (title.isBlank()) d.error(file, "Missing project title");
        if (prompt == 0 || !output || !body) d.error(file, "Expected paired prompt and final output bodies (TBD if unavailable)");
        required(file, header, new LinkedHashSet<>(HEADER), d);
        for (String key : header.keySet()) if (!HEADER.contains(key)) d.error(file, "Unknown header field: " + key);
        choice(file, header, "Status", Set.of("prototype", "keeper", "abandoned"), d);
        choice(file, header, "Story value", Set.of("none", "anecdote", "case study", "chapter-worthy"), d);
        String reuse = header.getOrDefault("Reusability", "TBD");
        if (!reuse.equals("TBD") && !reuse.matches("artifact (low|medium|high|TBD)\\s*[;\u00b7]\\s*prompt pattern (low|medium|high|TBD)"))
            d.error(file, "Reusability must rate artifact and prompt pattern separately");
        String tags = header.getOrDefault("Tags", "TBD");
        if (!tags.equals("TBD")) for (String tag : tags.split(",", -1))
            if (!TAGS.contains(tag.strip())) d.error(file, "Unsupported tag: " + tag);
        String dates = header.getOrDefault("Dates", "TBD");
        if (!dates.equals("TBD")) {
            String[] parts = dates.split("\\s*(?:->|\u2192)\\s*", -1);
            try {
                if (parts.length != 2 || LocalDate.parse(parts[0]).isAfter(LocalDate.parse(parts[1])))
                    d.error(file, "Dates must be first -> last prompt dates");
            } catch (DateTimeParseException e) { d.error(file, "Invalid prompt dates: " + dates); }
        }
        String files = header.getOrDefault("Files", "TBD");
        if (!files.equals("TBD")) {
            Matcher links = LINK.matcher(files);
            boolean found = false;
            while (links.find()) {
                found = true;
                if (links.group(1).matches("(?i)^[a-z][a-z0-9+.-]*:.*") || links.group(1).startsWith("#"))
                    d.error(file, "Files must reference local deliverables, not URLs/fragments");
            }
            if (!found || !LINK.matcher(files).replaceAll("").replace(",", "").isBlank())
                d.error(file, "Files must be comma-separated Markdown links, or TBD");
        }
        return new Project(dir, title, Map.copyOf(header), List.copyOf(verification));
    }

    private static void required(Path file, Map<String, String> fields, Set<String> required, Diagnostics d) {
        for (String key : required) {
            String value = fields.get(key);
            if (value == null || value.isBlank()) d.error(file, "Missing field: " + key);
            else if (value.contains("TBD")) d.warn(file, key + " contains TBD");
        }
    }

    private static void choice(Path file, Map<String, String> fields, String key, Set<String> values, Diagnostics d) {
        String value = fields.getOrDefault(key, "TBD");
        if (!value.equals("TBD") && !values.contains(value)) d.error(file, "Invalid " + key + ": " + value);
    }

    private static void validateContext(Path file, Map<String, String> fields, List<String> verification, Diagnostics d) {
        if (fields == null) { d.error(file, "Output without prompt context"); return; }
        required(file, fields, Set.of("Tool", "Verified", "Human edits"), d);
        for (String key : fields.keySet()) if (!CONTEXT.contains(key)) d.error(file, "Unknown context field: " + key);
        choice(file, fields, "Verified", Set.of("not-run", "syntax-checked", "agent-tested", "human-tested"), d);
        verification.add(fields.getOrDefault("Verified", "TBD"));
    }

    // Exclude verbatim transcripts and code from metadata/link parsing, retaining a body marker.
    static String outsideFences(String text) {
        StringBuilder result = new StringBuilder();
        char fence = 0;
        int length = 0;
        for (String line : text.lines().toList()) {
            String stripped = line.stripLeading();
            Matcher match = Pattern.compile("^(`{3,}|~{3,})(.*)$").matcher(stripped);
            if (fence == 0 && match.matches()) {
                fence = stripped.charAt(0);
                length = match.group(1).length();
                result.append("[verbatim body]\n");
            } else if (fence != 0) {
                if (stripped.matches(Pattern.quote(String.valueOf(fence)) + "{" + length + ",}\\s*")) fence = 0;
            } else result.append(line).append('\n');
        }
        if (fence != 0) throw new IllegalArgumentException("Unclosed fenced block");
        return result.toString();
    }

    private void checkLinks(Path file, String text, Diagnostics d) throws IOException {
        text = text.replaceAll("`+[^`\\r\\n]*`+", "");
        if (Pattern.compile("(?m)^\\s*\\[[^]]+]:|\\[[^]\\r\\n]+]\\[[^]\\r\\n]*]").matcher(text).find())
            d.error(file, "Reference-style links are unsupported; use inline [label](encoded-relative-path) links");
        Matcher links = LINK.matcher(text);
        while (links.find()) {
            String reference = links.group(1);
            try {
                URI uri = new URI(reference);
                if (uri.isAbsolute()) {
                    if (!Set.of("http", "https", "mailto").contains(uri.getScheme().toLowerCase(Locale.ROOT)))
                        d.error(file, "Unsupported/absolute local link: " + reference);
                    continue;
                }

                if (uri.getAuthority() != null || uri.getPath() == null || uri.getPath().startsWith("/")
                        || uri.getPath().contains("\\") || uri.getQuery() != null)
                    throw new IllegalArgumentException("Expected a relative local file reference");
                Path target = uri.getPath().isEmpty() ? file : file.getParent().resolve(uri.getPath()).normalize();
                if (!target.startsWith(root)) throw new IllegalArgumentException("Reference escapes collection");
                ImportPlan.rejectSymlinks(target);
                if (!Files.exists(target)) {
                    if (d.generatingCatalog && target.equals(root.resolve("index.html")) && uri.getFragment() == null)
                        continue;
                    throw new IllegalArgumentException("Missing local target");
                }
                if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
                    if (!Files.isRegularFile(target)) throw new IllegalArgumentException("Fragment requires a file");
                    String content = Files.readString(target);
                    String fragment = uri.getFragment();
                    boolean exists = target.toString().endsWith(".md")
                            ? markdownAnchors(outsideFences(content)).contains(fragment)
                            : Pattern.compile("(?:id|name)\\s*=\\s*[\"']" + Pattern.quote(fragment) + "[\"']")
                                    .matcher(content).find();
                    if (!exists) throw new IllegalArgumentException("Missing local fragment");
                }
            } catch (URISyntaxException | IllegalArgumentException e) {
                d.error(file, reference + " - " + e.getMessage());
            }
        }
    }

    private static String visibleDocument(Path file, Diagnostics d) throws IOException {
        try { return outsideFences(Files.readString(file)); }
        catch (IllegalArgumentException e) {
            d.error(file, e.getMessage());
            return "";
        }
    }

    private static Set<String> markdownAnchors(String text) {
        Set<String> anchors = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        for (String line : text.lines().toList()) if (line.matches("^#{1,6} .+")) {
            String base = line.replaceFirst("^#+ ", "").toLowerCase(Locale.ROOT)
                    .replaceAll("[^\\p{L}\\p{N}_\\-\\s]", "").replaceAll("\\s", "-");
            int count = counts.merge(base, 1, Integer::sum);
            anchors.add(count == 1 ? base : base + "-" + (count - 1));
        }
        return anchors;
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String url(Path path) {
        try {
            return new URI(null, null, path.toString().replace('\\', '/'), null).toASCIIString()
                    .replace("(", "%28").replace(")", "%29").replace("[", "%5B").replace("]", "%5D");
        } catch (URISyntaxException e) { throw new IllegalArgumentException("Invalid file path: " + path, e); }
    }

    public void catalog(Check check) throws IOException {
        if (check.errors() != 0) throw new IllegalArgumentException("Cannot render invalid records");
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Vibe Toolbox</title>
                <style>
                body{font:1rem system-ui;max-width:65rem;margin:2rem auto;padding:0 1rem;background:#f4f6fa;color:#17233b}
                input,select{font:inherit;padding:.6rem;max-width:100%;margin:.3rem}
                article{background:white;border:1px solid #bbc6d8;border-radius:.5rem;padding:1rem;margin:1rem 0}
                a{color:#174c9c} dt{font-weight:bold} dd{margin-bottom:.5rem;overflow-wrap:anywhere}
                .badge{font-weight:bold} .description{font-size:1.05rem}
                .actions a{display:inline-block;background:#174c9c;color:white;text-decoration:none;
                  padding:.55rem .8rem;border-radius:.35rem;margin-right:.5rem}
                [hidden]{display:none}
                </style>
                <h1>Vibe Toolbox</h1>
                <p><strong>Offline catalog (read-only).</strong> Import projects through the separate
                <a href="README.md#local-browser-management">local management interface</a>.</p>
                <p>Small AI-built apps for classmates, instructors, and future maintainers.
                Generated from project prompts.md records; verification labels are claims to read alongside evidence.</p>
                <p><a href="README.md">Download, run, update and verify</a> |
                <a href="DESIGN.md">Design</a></p>
                <label>Search <input id="search" type="search" placeholder="Title, tags, takeaway..."></label>
                <label>Status <select id="status"><option value="">All statuses</option>
                <option>prototype</option><option>keeper</option><option>abandoned</option>
                <option value="TBD">TBD (unknown)</option></select></label>
                <p id="count" role="status"></p><main>
                """);
        for (Project p : check.projects()) {
            String base = url(root.relativize(p.directory())) + "/";
            String status = p.fields().get("Status");
            html.append("<article data-status=\"").append(escape(status)).append("\"><h2>")
                    .append(escape(p.title())).append("</h2><p class=\"badge\">Status: ")
                    .append(escape(status)).append(status.equals("keeper") ? " (recommended)" : "")
                    .append("</p><p class=\"description\">").append(escape(p.fields().get("Use")))
                    .append("</p><dl>");
            for (String key : HEADER) if (!key.equals("Files") && !key.equals("Status") && !key.equals("Use"))
                html.append("<dt>").append(escape(key)).append("</dt><dd>")
                        .append(escape(p.fields().get(key))).append("</dd>");
            html.append("<dt>Prompt verification</dt><dd>").append(escape(String.join(", ", p.verification())))
                    .append("</dd></dl>");
            Matcher files = LINK.matcher(p.fields().get("Files"));
            List<String> artifacts = new ArrayList<>();
            String htmlApp = null;
            boolean hasJavaSource = false;
            while (files.find()) {
                String reference = files.group(1);
                artifacts.add(reference);
                String lower = reference.toLowerCase(Locale.ROOT);
                if (htmlApp == null && (lower.endsWith(".html") || lower.endsWith(".htm"))) htmlApp = reference;
                if (lower.endsWith(".java")) hasJavaSource = true;
            }
            html.append("<p class=\"actions\">");
            if (htmlApp != null)
                html.append("<a href=\"").append(escape(base + htmlApp)).append("\">Open app</a>");
            else if (hasJavaSource)
                html.append("<a href=\"").append(base).append("README.md\">Run instructions</a>");
            html.append("</p><p>");
            if (artifacts.isEmpty()) html.append("Artifacts: TBD (not supplied). ");
            else for (String reference : artifacts)
                html.append("<a href=\"").append(escape(base + reference)).append("\">")
                        .append(escape(reference)).append("</a> ");
            html.append("</p><p><a href=\"").append(base).append("README.md\">Details</a>");
            if (p.directory().getFileName().toString().equals("csv_quick_check"))
                html.append(" | <a href=\"").append(base)
                        .append("sample.csv\" download=\"sample.csv\">Sample CSV</a>");
            html.append(" | <a href=\"").append(base).append("prompts.md\">Exact history</a></p></article>\n");
        }
        html.append("""
                </main><p id="empty" hidden>No matching projects.</p>
                <script>
                const search = document.querySelector('#search'), status = document.querySelector('#status');
                const cards = [...document.querySelectorAll('article')];
                function filter() {
                  const query = search.value.toLocaleLowerCase().trim();
                  let visible = 0;
                  for (const card of cards) {
                    card.hidden = !(card.textContent.toLocaleLowerCase().includes(query)
                      && (!status.value || card.dataset.status === status.value));
                    if (!card.hidden) visible++;
                  }
                  document.querySelector('#count').textContent = `${visible} of ${cards.length} projects`;
                  document.querySelector('#empty').hidden = visible !== 0;
                }
                search.addEventListener('input', filter); status.addEventListener('change', filter); filter();
                </script></html>
                """);
        Path temp = Files.createTempFile(root, ".catalog-", ".html");
        try {
            Files.writeString(temp, html, StandardCharsets.UTF_8);
            Files.move(temp, root.resolve("index.html"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }
}
