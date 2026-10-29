import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;

final class StudentWheelLauncher implements AutoCloseable {
    private static final Map<String, String> SOURCES = Map.of(
            "Main.java", "ac7213a25ce368d752276ad2353c838ea666c4af5b08a171d0c7c1d3c0044476",
            "RosterState.java", "f583e03ef7a82169264556d2b388797edcd540c9751d872662e7e21ca156dbf1",
            "StudentSpinWheel.java", "99bc8d49201c8f694a20188a28b1146b213ab5994240d33898946db16ffe80d0",
            "WheelMath.java", "5ddd63befb251a6bf319e50cd67ef15a149358b949ab05ae0de0854ab0f6de89",
            "WheelPanel.java", "aaea0d0cf15511851e6f45625ade391177cd3d7eb9d95a8f0c996bcca76c6cc1");
    private final Path root;
    private Process process;

    StudentWheelLauncher(Path root) { this.root = root; }

    synchronized boolean launch() throws IOException {
        if (process != null && process.isAlive()) return false;
        if (GraphicsEnvironment.isHeadless())
            throw new IOException("Student Spin Wheel requires a graphical desktop on the computer running Java.");
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("A full JDK 21 is required to compile Student Spin Wheel.");
        Path output = root.resolve("out");
        ImportPlan.rejectSymlinks(output);
        Files.createDirectories(output);
        Path build = Files.createTempDirectory(output, "student-wheel-");
        Path classes = Files.createDirectory(build.resolve("classes"));
        Path sources = Files.createDirectory(build.resolve("sources"));
        var arguments = new ArrayList<String>();
        arguments.addAll(java.util.List.of("--release", "21", "-proc:none", "-d", classes.toString(),
                "-classpath", classes.toString(), "-sourcepath", sources.toString()));
        for (var entry : new java.util.TreeMap<>(SOURCES).entrySet()) {
            Path original = root.resolve("projects").resolve("student_spin_wheel").resolve("original")
                    .resolve("src").resolve(entry.getKey());
            ImportPlan.rejectSymlinks(original);
            Path copy = sources.resolve(entry.getKey());
            Files.copy(original, copy);
            if (!ImportPlan.digest(copy).equals(entry.getValue()))
                throw new IOException("Student Spin Wheel source differs from the reviewed bundled version: "
                        + entry.getKey() + ". Use Details for manual run instructions.");
            arguments.add(copy.toString());
        }
        var diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new));
        if (result != 0)
            throw new IOException("Student Spin Wheel compilation failed:\n" + diagnostics.toString(StandardCharsets.UTF_8));
        Path log = build.resolve("desktop.log");
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        process = new ProcessBuilder(java.toString(), "-cp", classes.toString(), "Main")
                .directory(build.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (process.waitFor(1500, TimeUnit.MILLISECONDS))
                throw new IOException("Student Spin Wheel exited during startup (code " + process.exitValue()
                        + "):\n" + Files.readString(log));
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException("Student Spin Wheel launch interrupted.", e);
        }
        return true;
    }

    @Override public synchronized void close() {
        if (process != null && process.isAlive()) process.destroy();
    }
}
