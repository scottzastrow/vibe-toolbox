import java.io.IOException;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        Toolbox toolbox = new Toolbox(Path.of("."));
        if (args.length == 0 || (args.length == 1 && args[0].equals("help"))) {
            System.out.println("""
                    Vibe Toolbox (JDK 21; run from the collection root)
                      add <snake_case_slug> --name <title> [--from <file-or-directory>]
                      check
                      catalog
                      serve (local browser management; Ctrl+C to stop)
                    Unknown history stays TBD. Imports copy, never modify, original files.
                    """);
            return;
        }
        switch (args[0]) {
            case "serve" -> {
                if (args.length != 1) throw new IllegalArgumentException("Unexpected arguments");
                LocalManager manager = new LocalManager(Path.of("."));
                Runtime.getRuntime().addShutdownHook(new Thread(manager::close));
                System.out.println("Vibe Toolbox: " + manager.url());
                System.out.println("Open this URL in your browser. The folder chooser opens on this computer.");
                System.out.println("The offline index.html still works without this server. Ctrl+C to stop.");
            }
            case "add" -> {
                if (args.length < 4) throw new IllegalArgumentException("add requires a slug and --name <title>");
                String name = null;
                Path source = null;
                for (int i = 2; i < args.length; i += 2) {
                    if (i + 1 == args.length) throw new IllegalArgumentException("Missing option value: " + args[i]);
                    switch (args[i]) {
                        case "--name" -> {
                            if (name != null) throw new IllegalArgumentException("Duplicate --name");
                            name = args[i + 1];
                        }
                        case "--from" -> {
                            if (source != null) throw new IllegalArgumentException("Duplicate --from");
                            source = Path.of(args[i + 1]);
                        }
                        default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                }
                if (name == null) throw new IllegalArgumentException("Missing --name");
                toolbox.add(args[1], name, source);
                System.out.println("Added " + args[1] + "; complete its documentation before publishing.");
            }
            case "check", "catalog" -> {
                if (args.length != 1) throw new IllegalArgumentException("Unexpected arguments");
                Toolbox.Check check = toolbox.check(args[0].equals("catalog"));
                check.messages().forEach(System.out::println);
                System.out.printf("%d project(s), %d error(s), %d warning(s)%n",
                        check.projects().size(), check.errors(), check.warnings());
                if (check.errors() != 0) throw new IllegalArgumentException("Fix errors before generating the catalog.");
                if (args[0].equals("catalog")) {
                    toolbox.catalog(check);
                    System.out.println("Generated index.html");
                }
            }
            default -> throw new IllegalArgumentException("Unknown command: " + args[0] + " (use help)");
        }
    }
}
