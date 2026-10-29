import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ToolboxTests {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("vibe-toolbox-tests-");
        try {
            run(temp);
            System.out.println("PASS " + assertions + " Java checks");
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void run(Path temp) throws Exception {
        Path root = Files.createDirectory(temp.resolve("collection"));
        Files.writeString(root.resolve("README.md"), "# Test collection\n");
        Files.writeString(root.resolve("DESIGN.md"), "# Test design\n");
        Toolbox toolbox = new Toolbox(root);
        toolbox.add("new_app", "<script>alert('x')</script>", null);
        Toolbox.Check check = toolbox.check();
        expect(check.errors() == 0, "template accepted");
        expect(check.warnings() >= 10, "TBD remains visible");
        Path record = root.resolve("projects").resolve("new_app").resolve("prompts.md");
        String initial = Files.readString(record);
        toolbox.catalog(check);
        String html = Files.readString(root.resolve("index.html"));
        expect(html.contains("&lt;script&gt;") && !html.contains("<script>alert"), "catalog escapes titles");
        expect(html.contains("<option>abandoned</option>") && html.contains("data-status=\"TBD\""), "all status choices");
        toolbox.catalog(toolbox.check());
        expect(html.equals(Files.readString(root.resolve("index.html"))), "deterministic catalog");
        expect(initial.equals(Files.readString(record)), "catalog/check never rewrite history");
        Files.writeString(root.resolve("README.md"), "# Test collection\n[Catalog](index.html)\n");
        Files.delete(root.resolve("index.html"));
        expect(toolbox.check().errors() > 0, "missing catalog reported by check");
        expect(toolbox.check(true).errors() == 0, "catalog bootstrap checks all but its own pending output");
        toolbox.catalog(toolbox.check(true));
        expect(toolbox.check().errors() == 0, "bootstrap generates valid local target");
        fails(() -> toolbox.add("../bad", "Bad", null), "invalid slug");
        fails(() -> toolbox.add("new_app", "Existing", null), "no overwrites");
        fails(() -> toolbox.add("blank", " \n", null), "invalid title");
        fails(() -> toolbox.add("recursive", "Recursive", root), "overlapping source");
        Path source = Files.createDirectory(temp.resolve("supplied"));
        Path nested = Files.createDirectory(source.resolve("nested"));
        byte[] binary = {0, 1, 2, (byte) 255};
        Files.write(nested.resolve("bytes.bin"), binary);
        Files.writeString(source.resolve("original name (1).html"), "<!doctype html><title>original</title>");
        Files.writeString(source.resolve("prompts.md"), "Supplied history, not rewritten.");
        toolbox.add("imported_app", "Import", source);
        Path original = root.resolve("projects").resolve("imported_app").resolve("original");
        expect(Arrays.equals(binary, Files.readAllBytes(original.resolve("nested").resolve("bytes.bin"))), "binary copy exact");
        expect(Files.readString(original.resolve("prompts.md")).equals("Supplied history, not rewritten."), "supplied history exact");
        expect(Files.readString(source.resolve("prompts.md")).equals("Supplied history, not rewritten."), "source unchanged");
        try (var paths = Files.walk(source)) {
            expect(paths.filter(Files::isRegularFile).count() == 3, "no documentation added to source");
        }
        expect(toolbox.check().errors() == 0, "import with spaces/parentheses validates");
        Path importedRecord = original.getParent().resolve("prompts.md");
        String importedMetadata = Files.readString(importedRecord).replace("**Use:** TBD",
                "**Use:** An imported browser app for this catalog test.");
        Files.writeString(importedRecord, importedMetadata);
        toolbox.catalog(toolbox.check());
        String catalog = Files.readString(root.resolve("index.html"));
        expect(catalog.contains("<p class=\"description\">An imported browser app for this catalog test.</p>"),
                "catalog shows the project description");
        expect(catalog.contains(">Open app</a>"), "browser app has an Open action");
        Path javaSource = Files.createDirectory(temp.resolve("java-supplied"));
        Files.writeString(javaSource.resolve("Main.java"), "public class Main {}\n");
        toolbox.add("java_app", "Java App", javaSource);
        toolbox.catalog(toolbox.check());
        catalog = Files.readString(root.resolve("index.html"));
        expect(catalog.contains(">Run instructions</a>"), "Java app has a Run instructions action");
        toolbox.add("single_file", "Single", nested.resolve("bytes.bin"));
        expect(Arrays.equals(binary, Files.readAllBytes(root.resolve("projects").resolve("single_file")
                .resolve("original").resolve("bytes.bin"))), "single file import");

        assertError(toolbox, record, initial.replace("> - **Status:** TBD\n", ""), "missing field");
        assertError(toolbox, record, initial.replace("**Status:** TBD", "**Status:** perfect"), "invalid status");
        assertError(toolbox, record, initial.replace("**Verified:** TBD", "**Verified:** guessed"), "invalid verification");
        assertError(toolbox, record, initial.replace("**Tags:** TBD", "**Tags:** made-up"), "unknown tag");
        assertError(toolbox, record, initial.replace("**Dates:** TBD", "**Dates:** 2026-02-31 -> 2026-10-28"), "invalid dates");
        assertError(toolbox, record, initial.replace("**Dates:** TBD", "**Dates:** 2026-10-28 -> 2026-10-01"), "reversed dates");
        assertError(toolbox, record, initial.replace("**Reusability:** TBD", "**Reusability:** high"), "two reusability ratings");
        assertError(toolbox, record, initial.replace("**Status:** TBD", "**Status:** TBD\n> - **Status:** keeper"), "duplicate fields");
        assertError(toolbox, record, initial.replace("## Output 1:", "## Output 2:"), "paired prompt numbers");
        assertError(toolbox, record, initial.replace("**Files:** TBD", "**Files:** [missing](missing.html)"), "missing deliverable");
        assertError(toolbox, record, initial.replace("**Files:** TBD", "**Files:** [bad](https://example.com)"), "deliverable not external");
        assertError(toolbox, record, initial + "\n[escape](../../../outside.txt)\n", "traversal rejected");
        assertError(toolbox, record, initial + "\n[escape](%2Fetc%2Fpasswd)\n", "absolute local path rejected");
        assertError(toolbox, record, initial + "\n[bad](javascript:alert)\n", "unsafe scheme rejected");
        assertError(toolbox, record, initial + "\n[bad](README.md#absent)\n", "missing fragment");
        assertError(toolbox, record, initial + "\n[bad](file with spaces.html)\n", "unencoded link reported");
        assertError(toolbox, record, initial + "\n[bad][id]\n[id]: missing.html\n", "unsupported references reported");
        assertError(toolbox, record, initial + "\n```text\nunclosed\n", "unclosed transcript fence");
        Files.writeString(record, initial + "\n[valid](README.md#run)\n");
        expect(toolbox.check().errors() == 0, "Markdown fragment accepted");

        String transcript = "````text\n# Prompt 999:\n> - **Status:** imaginary\n[not a link](absent)\n```\nverbatim\n````";
        String recorded = initial.replace("\nTBD\n\n## Output 1:", "\n" + transcript + "\n\n## Output 1:");
        Files.writeString(record, recorded);
        expect(toolbox.check().errors() == 0, "fenced transcript not interpreted");
        toolbox.catalog(toolbox.check());
        expect(recorded.equals(Files.readString(record)), "exact transcript preserved");
        Files.writeString(record, initial);
        Path emptySource = Files.createDirectory(temp.resolve("empty"));
        fails(() -> toolbox.add("empty_import", "Empty", emptySource), "empty import rejected");
        expect(!Files.exists(root.resolve("projects").resolve("empty_import")), "failed import not published");
        try (var paths = Files.list(root.resolve("projects"))) {
            expect(paths.noneMatch(p -> p.getFileName().toString().startsWith(".import-")), "failed import stage cleaned");
        }

        Path pending = root.resolve("projects").resolve("pending_app");
        Files.createDirectory(pending);
        expect(toolbox.check().errors() > 0, "missing record reported");
        Files.delete(pending);
        for (String status : List.of("prototype", "keeper", "abandoned", "TBD")) {
            Files.writeString(record, initial.replace("**Status:** TBD", "**Status:** " + status));
            expect(toolbox.check().errors() == 0, status + " accepted");
        }
        Files.writeString(record, initial);
        Cli help = cli(root, "help");
        expect(help.exit() == 0 && help.output().contains("add <snake_case_slug>"), "CLI help");
        expect(cli(root, "bad-command").exit() != 0, "CLI unknown command fails");
        expect(cli(root, "add", "missing_name").exit() != 0, "CLI missing name fails");
        expect(cli(root, "check", "extra").exit() != 0, "CLI unexpected arguments fail");
        expect(cli(root, "add", "new_cli", "--name", "CLI App").exit() == 0, "CLI add");
        expect(cli(root, "add", "new_cli", "--name", "Overwrite").exit() != 0, "CLI refuses existing destination");
        expect(cli(root, "catalog").exit() == 0, "CLI catalog");
        expect(cli(root, "check").exit() == 0, "CLI check permits explicit unknowns");
        Files.writeString(record, initial.replace("**Status:** TBD", "**Status:** imaginary"));
        expect(cli(root, "check").exit() != 0, "CLI check exits nonzero on invalid record");
        expect(cli(root, "catalog").exit() != 0, "CLI catalog exits nonzero on invalid record");
        Files.writeString(record, initial);
    }

    private static void assertError(Toolbox toolbox, Path path, String content, String label) throws IOException {
        String before = Files.readString(path);
        try {
            Files.writeString(path, content);
            Toolbox.Check check = toolbox.check();
            expect(check.errors() > 0, label);
            fails(() -> toolbox.catalog(check), "catalog refuses " + label);
        } finally { Files.writeString(path, before); }
    }

    private interface Action { void run() throws IOException; }

    private record Cli(int exit, String output) {}
    private static Cli cli(Path directory, String... args) throws IOException, InterruptedException {
        String classpath = String.join(File.pathSeparator, Arrays.stream(System.getProperty("java.class.path")
                .split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(p -> Path.of(p).toAbsolutePath().toString()).toList());
        var command = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, "Main"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Cli(process.waitFor(), output);
    }

    private static void fails(Action action, String label) throws IOException {
        try { action.run(); }
        catch (IOException | IllegalArgumentException e) { assertions++; return; }
        throw new AssertionError("Expected failure: " + label);
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }
}
