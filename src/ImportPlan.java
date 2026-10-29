import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Immutable, byte-level snapshot of the files a user is reviewing. */
public record ImportPlan(Path source, Map<Path, String> files, Map<Path, String> exclusions) {
    public ImportPlan {
        files = Collections.unmodifiableMap(new TreeMap<>(files));
        exclusions = Collections.unmodifiableMap(new TreeMap<>(exclusions));
    }

    public static ImportPlan inspect(Path source) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        rejectSymlinks(absolute);
        Path input = absolute.toRealPath();
        Map<Path, String> files = new TreeMap<>(), exclusions = new TreeMap<>();
        if (Files.isRegularFile(input)) {
            String reason = exclusion(input.getFileName());
            if (reason == null) files.put(input.getFileName(), digest(input));
            else exclusions.put(input.getFileName(), reason);
        } else if (Files.isDirectory(input)) {
            String rootExclusion = input.getFileName() == null ? null : exclusion(input.getFileName());
            if (rootExclusion != null) {
                exclusions.put(Path.of("."), rootExclusion + " (entire selected directory)");
                return new ImportPlan(input, files, exclusions);
            }
            Files.walkFileTree(input, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(input)) return FileVisitResult.CONTINUE;
                    Path relative = input.relativize(dir);
                    String reason = exclusion(relative);
                    if (reason == null) return FileVisitResult.CONTINUE;
                    exclusions.put(relative, reason + " (entire directory)");
                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = input.relativize(file);
                    String reason = exclusion(relative);
                    if (reason != null) exclusions.put(relative, reason);
                    else {
                        if (!attrs.isRegularFile() || attrs.isSymbolicLink())
                            throw new IOException("Unsupported file or symlink: " + file);
                        files.put(relative, digest(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else throw new IOException("Import requires a regular file or directory");
        return new ImportPlan(input, files, exclusions);
    }

    public static String exclusion(Path path) {
        for (Path part : path) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (Set.of(".git", ".hg", ".svn").contains(name)) return "Version-control metadata";
            if (Set.of(".idea", ".vscode", ".settings", ".project", ".classpath").contains(name)
                    || name.endsWith(".iml") || name.endsWith(".suo") || name.endsWith(".user"))
                return "IDE settings";
            if (Set.of("out", "build", "target", "dist", "bin", "obj", ".gradle", "node_modules",
                    ".venv", "venv", "__pycache__", "coverage").contains(name)
                    || name.endsWith(".class") || name.endsWith(".pyc") || name.endsWith(".jar")
                    || name.endsWith(".war") || name.endsWith(".exe") || name.endsWith(".dll")
                    || name.endsWith(".o") || name.endsWith(".obj"))
                return "Build output or dependencies";
            if (name.equals(".env") || name.startsWith(".env.") || name.endsWith(".env")
                    || Set.of(".ssh", ".aws", ".azure", ".gnupg", ".npmrc", ".pypirc", ".netrc",
                    ".git-credentials", ".credentials", "credentials", "credentials.json", "secrets", "secrets.json",
                    "secrets.yaml", "secrets.yml", "id_rsa", "id_ed25519", "id_dsa", "id_ecdsa").contains(name)
                    || name.startsWith("credentials.") || name.startsWith("secret.")
                    || name.startsWith("secrets.") || name.startsWith("token.") || name.startsWith("password.")
                    || name.endsWith(".key") || name.endsWith(".pem")
                    || name.endsWith(".p12") || name.endsWith(".pfx") || name.endsWith(".jks")
                    || name.endsWith(".keystore"))
                return "Potential secrets or private credentials";
        }
        return null;
    }

    public Path file(Path relative) {
        return Files.isDirectory(source) ? source.resolve(relative) : source;
    }

    static void rejectSymlinks(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path part : path) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Symlinks are not supported: " + current);
        }
    }

    static String digest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK must supply SHA-256", e);
        }
    }
}
