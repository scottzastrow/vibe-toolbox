import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class LocalManagerTests {
    private static int assertions;
    private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("vibe-manager-tests-");
        try {
            if (args.length == 1 && args[0].equals("browser")) browserFixture(temp);
            else {
                test(temp);
                System.out.println("PASS " + assertions + " local management checks");
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static Path collection(Path temp) throws IOException {
        Path root = Files.createDirectory(temp.resolve("collection"));
        Files.createDirectory(root.resolve("projects"));
        Files.writeString(root.resolve("README.md"), "# Collection\n");
        Files.writeString(root.resolve("DESIGN.md"), "# Design\n");
        return root;
    }

    private static Path source(Path temp) throws IOException {
        Path source = Files.createDirectory(temp.resolve("sample_app"));
        Files.writeString(source.resolve("app.html"), """
                <!doctype html><title>Imported fixture</title><h1 id="fixture">Waiting for app script</h1>
                <script>document.querySelector("#fixture").textContent = "Original HTML works";</script>
                """);
        Files.writeString(source.resolve("prompts.md"), "Exact available history.\r\nNo reconstruction.\r\n");
        Files.write(source.resolve("binary.dat"), new byte[]{0, 1, (byte) 255});
        for (String directory : List.of(".git", ".idea", "build", "node_modules")) {
            Files.createDirectory(source.resolve(directory));
            Files.writeString(source.resolve(directory).resolve("ignored.txt"), "excluded fixture");
        }
        for (String secret : List.of(".env", ".env.local", "private.pem", "credentials.json", "token.txt"))
            Files.writeString(source.resolve(secret), "EXCLUDED TEST FIXTURE - not a real secret");
        Files.writeString(source.resolve("Compiled.class"), "build fixture");
        return source;
    }

    private static Map<String, String> snapshot(Path source) throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).toList())
                snapshot.put(source.relativize(file).toString(), ImportPlan.digest(file));
        }
        return snapshot;
    }

    private static void test(Path temp) throws Exception {
        Path root = collection(temp), source = source(temp);
        Map<String, String> original = snapshot(source);
        AtomicReference<Path> selected = new AtomicReference<>();
        AtomicInteger choices = new AtomicInteger();
        try (LocalManager manager = new LocalManager(root, () -> {
            choices.incrementAndGet();
            return selected.get();
        })) {
            String origin = manager.url().replaceAll("/$", "");
            HttpResponse<String> home = get(manager.url());
            expect(home.statusCode() == 200 && home.body().contains("Vibe Toolbox")
                    && home.body().contains(">Add project</button>"), "catalog home with Add project");
            expect(!home.body().contains("native Java folder chooser"), "technical explanations stay off home");
            expect(home.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"), "management CSP");
            String token = field(home.body(), "token");
            expect(choices.get() == 0, "GET does not open chooser");
            expect(get(origin + "/choose").statusCode() == 404 && choices.get() == 0, "GET chooser forbidden");
            expect(get(origin + "/launch/student_spin_wheel").statusCode() == 404, "desktop launch route removed");
            expect(post(origin + "/launch/student_spin_wheel", Map.of("token", token), origin).statusCode() == 404,
                    "desktop launch POST route removed");
            expect(post(origin + "/launch/java_app", Map.of("token", token), origin).statusCode() == 404,
                    "arbitrary project cannot be launched");
            expect(post(origin + "/choose", Map.of("token", token), null).statusCode() == 403, "missing Origin rejected");
            expect(post(origin + "/choose", Map.of("token", token), "http://evil.invalid").statusCode() == 403, "foreign Origin rejected");
            expect(post(origin + "/choose", Map.of("token", token), "null").statusCode() == 403, "sandbox opaque Origin rejected");
            expect(post(origin + "/choose", Map.of("token", "wrong"), origin).statusCode() == 403, "invalid token rejected");
            expect(post(origin + "/choose", Map.of(), origin).statusCode() == 403, "missing token rejected");
            HttpResponse<String> badHost = get(manager.url().replace("127.0.0.1", "localhost"));
            expect(badHost.statusCode() == 403, "Host rebinding rejected");
            expect(choices.get() == 0, "rejected requests do not open chooser");
            HttpResponse<String> cancelled = post(origin + "/choose", Map.of("token", token), origin);
            expect(cancelled.body().contains("Selection cancelled"), "native selection cancellation");
            expect(cancelled.uri().getPath().equals("/") && cancelled.body().contains(">Add project</button>"),
                    "native cancellation redirects to catalog with status");
            expect(countProjects(root) == 0, "selection cancellation writes no projects");
            selected.set(source);
            HttpResponse<String> preview = post(origin + "/choose", Map.of("token", token), origin);
            expect(preview.body().contains("Review import") && preview.body().contains(source.toString()), "source reviewed");
            for (String exclusion : List.of(".git", ".idea", "build", "node_modules", ".env", "private.pem", "Compiled.class"))
                expect(preview.body().contains(exclusion), "preview reports " + exclusion);
            expect(countProjects(root) == 0 && !Files.exists(root.resolve("index.html")), "preview does not publish");
            String previewId = field(preview.body(), "preview");
            expect(post(origin + "/cancel", Map.of("token", token, "preview", previewId), origin)
                    .body().contains("Import cancelled"), "review cancellation");
            expect(countProjects(root) == 0, "review cancellation writes no project");
            expect(post(origin + "/confirm", confirmation(token, previewId, "sample_app", "app.html"), origin)
                    .statusCode() == 400, "cancelled preview cannot confirm");

            preview = post(origin + "/choose", Map.of("token", token), origin);
            previewId = field(preview.body(), "preview");
            expect(post(origin + "/confirm", confirmation(token, previewId, "../escape", "app.html"), origin)
                    .statusCode() == 400, "unsafe slug rejected");
            expect(post(origin + "/confirm", confirmation(token, previewId, "safe", "../app.html"), origin)
                    .statusCode() == 400, "unreviewed main rejected");
            Files.writeString(source.resolve("late.txt"), "new unreviewed content");
            HttpResponse<String> changed = post(origin + "/confirm", confirmation(token, previewId, "sample_app", "app.html"), origin);
            expect(changed.statusCode() == 400 && changed.body().contains("Source changed"), "changed source rejected");
            expect(countProjects(root) == 0, "changed source not published");
            Files.delete(source.resolve("late.txt"));
            previewId = field(post(origin + "/choose", Map.of("token", token), origin).body(), "preview");
            Map<String, String> confirm = confirmation(token, previewId, "sample_app", "app.html");
            confirm.put("description", "Reviewed local app <no HTML injection> [not a link]");
            HttpResponse<String> success = post(origin + "/confirm", confirm, origin);
            expect(success.statusCode() == 200 && success.body().contains("Import successful"), "import success");
            expect(success.uri().getPath().equals("/") && success.body().contains("<article>")
                    && success.body().contains(">Details</a>") && success.body().contains(">Open app</a>"),
                    "successful import redirects to live catalog with actions and details");
            expect(success.body().contains("Reviewed local app"), "reviewed import description appears on card");
            Path imported = root.resolve("projects").resolve("sample_app");
            expect(snapshot(source).equals(original), "all source bytes and file set preserved, including excluded files");
            expect(snapshot(imported.resolve("original")).equals(Map.of(
                    "app.html", original.get("app.html"), "prompts.md", original.get("prompts.md"),
                    "binary.dat", original.get("binary.dat"))), "only retained files copied byte-exact");
            String record = Files.readString(imported.resolve("prompts.md"));
            expect(record.contains("**Status:** TBD") && record.contains("**Verified:** TBD"), "unknowns remain TBD");
            String readme = Files.readString(imported.resolve("README.md"));
            expect(readme.contains("Reviewed local app") && readme.contains("## Import exclusions"), "description and exclusions persisted");
            expect(Files.readString(root.resolve("index.html")).contains("Sample &lt;App&gt;"), "catalog regenerated with imported title");
            expect(get(origin + "/project/sample_app").body().contains("Open HTML app"), "details offer HTML app");
            expect(get(manager.contentUrl() + "projects/sample_app/original/app.html").body().contains("Original HTML works"), "HTML served from separate origin");
            expect(!manager.contentUrl().equals(manager.url()), "content separated from management origin");
            expect(get(manager.contentUrl() + "projects/sample_app/original/app.html").headers()
                    .firstValue("Content-Security-Policy").orElse("").startsWith("sandbox allow-scripts;"), "untrusted app sandboxed");
            expect(get(manager.contentUrl() + "src/Main.java").statusCode() == 403, "content does not expose organizer sources");
            expect(get(manager.contentUrl() + "projects/../README.md").statusCode() == 403, "traversal rejected");
            expect(get(manager.contentUrl() + "projects/sample_app/original/.env").statusCode() == 403, "secret content route rejected");
            expect(post(manager.contentUrl() + "choose", Map.of("token", token), origin).statusCode() == 405, "content server is read only");
            expect(post(origin + "/confirm", confirm, origin).statusCode() == 400, "replay rejected");
            String catalog = Files.readString(root.resolve("index.html"));
            Map<String, String> published = snapshot(imported);
            previewId = field(post(origin + "/choose", Map.of("token", token), origin).body(), "preview");
            expect(post(origin + "/confirm", confirmation(token, previewId, "sample_app", "app.html"), origin)
                    .body().contains("Destination already exists"), "collision explicitly reported");
            expect(snapshot(imported).equals(published) && Files.readString(root.resolve("index.html")).equals(catalog), "collision leaves project/catalog unchanged");
            selected.set(root);
            expect(post(origin + "/choose", Map.of("token", token), origin).statusCode() == 400, "overlap rejected");
            Path excludedOnly = Files.createDirectory(temp.resolve("excluded_only"));
            Files.writeString(excludedOnly.resolve(".env"), "excluded fixture");
            selected.set(excludedOnly);
            expect(post(origin + "/choose", Map.of("token", token), origin).statusCode() == 400, "empty filtered import rejected");
            selected.set(source.resolve(".git"));
            expect(post(origin + "/choose", Map.of("token", token), origin).statusCode() == 400, "excluded directory cannot be selected as import root");

            Path java = Files.createDirectory(temp.resolve("java_source"));
            Files.writeString(java.resolve("Example.java"), "public class Example { public static void main(String[] args) {} }\n");
            selected.set(java);
            previewId = field(post(origin + "/choose", Map.of("token", token), origin).body(), "preview");
            expect(post(origin + "/confirm", confirmation(token, previewId, "java_app", "Example.java"), origin)
                    .body().contains("Import successful"), "Java project imported");
            String javaDetails = get(origin + "/project/java_app").body();
            expect(javaDetails.contains("Java apps are not executed") && javaDetails.contains("javac --release 21"), "Java run guidance displayed");
            expect(!javaDetails.contains("id=\"openApp\""), "Java not offered as executable HTML");
            String liveCatalog = get(manager.url()).body();
            expect(liveCatalog.contains("/project/java_app#run") && liveCatalog.contains("/project/sample_app"),
                    "live catalog includes all additional projects with Java Run instructions");
            expect(new Toolbox(root).check().errors() == 0, "imported records validate");

            Path broken = Files.createDirectory(temp.resolve("broken_docs"));
            Files.writeString(broken.resolve("app.html"), "<title>broken docs fixture</title>");
            Files.writeString(broken.resolve("README.md"), "[missing](absent.txt)\n");
            selected.set(broken);
            previewId = field(post(origin + "/choose", Map.of("token", token), origin).body(), "preview");
            catalog = Files.readString(root.resolve("index.html"));
            HttpResponse<String> repair = post(origin + "/confirm", confirmation(token, previewId, "broken_docs", "app.html"), origin);
            expect(repair.statusCode() == 409 && repair.body().contains("catalog needs repair"), "invalid original links explicitly report catalog failure");
            expect(Files.readString(root.resolve("index.html")).equals(catalog), "failed catalog validation retains old catalog");
            expect(Files.readString(root.resolve("projects/broken_docs/original/README.md")).equals("[missing](absent.txt)\n"), "invalid supplied docs preserved");
        }
    }

    private static void browserFixture(Path temp) throws Exception {
        Path root = collection(temp), source = source(temp);
        Path javaSource = Files.createDirectory(temp.resolve("java_app"));
        Files.writeString(javaSource.resolve("Example.java"), "public class Example { public static void main(String[] args) {} }\n");
        Map<String, String> original = snapshot(source), javaOriginal = snapshot(javaSource);
        AtomicInteger selections = new AtomicInteger();
        try (LocalManager manager = new LocalManager(root, () -> switch (selections.incrementAndGet()) {
            case 1 -> null;
            case 5 -> javaSource;
            default -> source;
        })) {
            System.out.println(manager.url());
            System.out.flush();
            System.in.read();
            expect(snapshot(source).equals(original), "browser source preservation");
            expect(snapshot(javaSource).equals(javaOriginal), "browser Java source preservation");
            expect(countProjects(root) == 2, "browser imported only two fixture projects");
            expect(new Toolbox(root).check().errors() == 0, "browser imported valid projects");
            expect(Files.readString(root.resolve("index.html")).contains("Browser HTML"), "browser catalog persisted");
            expect(Files.readString(root.resolve("projects/browser_html/original/prompts.md"))
                    .equals(Files.readString(source.resolve("prompts.md"))), "browser history preserved");
            System.out.println("PASS " + assertions + " browser persistence checks");
        }

    }

    private static long countProjects(Path root) throws IOException {
        try (var paths = Files.list(root.resolve("projects"))) { return paths.count(); }
    }

    private static Map<String, String> confirmation(String token, String preview, String slug, String main) {
        return new HashMap<>(Map.of("token", token, "preview", preview, "slug", slug,
                "name", "Sample <App>", "description", "User-reviewed description", "main", main));
    }

    private static String field(String body, String name) {
        var matcher = Pattern.compile("name=\"" + name + "\" value=\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) throw new AssertionError("Missing field " + name + " in " + body);
        return matcher.group(1);
    }

    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String url, Map<String, String> form, String origin) throws IOException, InterruptedException {
        String body = String.join("&", form.entrySet().stream().map(e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).toList());
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded");
        if (origin != null) request.header("Origin", origin);
        return CLIENT.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }
}
