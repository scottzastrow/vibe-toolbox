# Vibe Toolbox

SEIS 606 Homework 8, Option 2: a lightweight organizer for small AI-built apps.
For classmates, instructors, and future maintainers who want runnable
artifacts, exact available AI history, and evidence rather than unsupported
"it works" claims.

## Download and run

Download and extract the published repository's ZIP, or clone its actual
Git URL. A remote has not been configured in this local collection.

For the local catalog with **Add project** and **Open app** for all three bundled
apps, install a full **JDK 21**, ensure `java` and `javac` are on `PATH`, and open
PowerShell in the extracted/cloned **Vibe Toolbox root** (the folder containing
`src` and this README). Run:

```powershell
java --version
javac --version
javac --release 21 -Xlint:all -d out src\*.java
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; do not start stale classes.' }
java -cp out Main serve
```

Open the exact `http://127.0.0.1:<port>/` URL printed after `Vibe Toolbox:`.
Keep that terminal running. **Ctrl+C** stops the server and its launched
Student Spin Wheel. No npm install, Maven, Gradle, or external runtime service
is needed. Student Spin Wheel and the Add project folder chooser require a
graphical desktop on the server computer. See [current limitations](#current-limitations).

For the independent offline catalog instead, no Java is required:
Open [index.html](index.html) directly in a modern browser. It searches local
project records and filters all statuses, including unknown (`TBD`), without
a server or internet connection. Markdown documentation links may display
as plain text in a browser; they are also readable in an editor.

[CSV Quick Check](projects/csv_quick_check/csv_quick_check.html) runs directly
in the browser, with no installation or uploads. The Presentation Timer is
also a single-file browser app; the Student Spin Wheel is a Java 21 desktop app.

| Demonstration | Availability |
|---|---|
| CSV Quick Check | Implemented; see its run instructions and evidence |
| Presentation Timer | Imported; open its app link in the catalog and see its README |
| Student Spin Wheel | Imported; see its README for Java 21 run and check commands |

The sibling source folders are unchanged. Available Spin Wheel specifications,
plans, task notes, and tests are preserved; prompt history not present in the
sources remains marked `TBD`.

## Organizer commands (JDK 21)

Use the existing IntelliJ Java build: select a locally installed JDK 21 in
Project Structure (the SDK label may need mapping), build, and run `Main`.
Set the working directory to the collection root and program arguments to
`serve`, `check`, `catalog`, or an `add` command. Imported Java projects are excluded
from the toolbox module so unrelated sources do not enter its compilation.
No Maven, Gradle, or external Java dependencies are used.

Equivalent PowerShell commands from the root:

```powershell
javac --release 21 -Xlint:all -d out src\*.java
java -cp out Main help
java -cp out Main check
java -cp out Main catalog
java -cp out ToolboxTests
```

Stop if compilation fails; do not run stale classes. Java is needed only for
the organizer (and as required by a supplied Java app), not for browsing HTML.

```powershell
java -cp out Main add my_app --name "My App"
java -cp out Main add another_timer --name "Another Timer" --from "C:\supplied\timer.html"
java -cp out Main add another_wheel --name "Another Wheel" --from "C:\supplied\student_spin_wheel"
```

These last two are additional import examples, not claims that those source
paths exist. The three bundled apps are already included; do not reimport them.
Supply actual paths. `add` refuses an existing destination and writes
complete `TBD` templates; it does not generate an app. Import copies into
`original`, preserves names/bytes/relative paths, verifies SHA-256, and never
modifies supplied files. It rejects symlinks, overlapping paths, and empty
imports. Both interfaces now exclude known metadata, build/dependency outputs,
and secret filenames as described below. This is not a content-based secret
scanner or a determination of redistribution rights; review all retained files.

Complete the new app's documentation using real evidence. If source history
is missing, keep `TBD`. Preserve supplied documentation under `original`;
copy available exact exchanges into canonical `prompts.md`, retaining the
source history file too. Derive desktop launch instructions from supplied
files, not assumptions. Put any compilation outputs outside `original`.

## Local browser management

Compile using the command above, then run `Main` with the `serve` argument in
IntelliJ, or run this once from the collection root:

```powershell
java -cp out Main serve
```

Open the printed `http://127.0.0.1:<port>/` URL. The OS assigns the port each
launch; use the exact printed address, not `localhost`. Keep Java running;
Ctrl+C stops the server. No database, external service, or paid API is used.
The printed URL opens the **Vibe Toolbox catalog**, showing all registered apps
with descriptions, **Open app** or **Run instructions** actions, and **Details**
links. **Add project** is above the cards. Details and **Help and technical
information** hold run evidence, metadata, import explanations, and diagnostics.
The independent **Offline catalog (read-only)** remains available through Help;
opening `index.html` directly still works when Java is stopped.

Click **Add project** to open Java's native folder chooser on this computer
(it may appear behind your browser). No source path typing is needed. A
graphical desktop is required; this chooser cannot select folders on a different
computer. Review the selected folder, included files, exclusions with reasons,
and editable project folder/slug, name, description, and main file. Click
**Confirm import** only after reviewing; cancelling either the chooser or
review copies nothing. Successful import and either form of cancellation return
to the catalog URL with a visible status message; new apps appear immediately.
Selecting again replaces the previous preview.

Imports retain paths and exact bytes under `projects\<slug>\original`, including
available prompts/history. Originals remain unchanged. The importer refuses
existing destinations, unsafe slugs, symlinks, overlapping paths, empty filtered
imports, and source changes since preview. Description and main-file links go
in the new README; canonical unknown metadata/history stays `TBD`. Importing
does not prove an app works or reconstruct its unavailable history.

The shared import policy excludes VCS directories (`.git`, `.hg`, `.svn`),
IDE settings (`.idea`, `.vscode`, `.iml`, etc.), common build/dependency
directories (`out`, `build`, `target`, `dist`, `bin`, `obj`, `node_modules`,
virtual environments, etc.), compiled artifacts, environment files (`.env`,
`.env.*`, `*.env`), credential stores, secret/token/password filenames, and
private-key/keystore extensions. Directory exclusions cover their entire
subtrees. This conservative filename policy can exclude legitimate files and
cannot detect secrets hidden in arbitrary source/configuration files. Review
the complete preview; do not import sensitive files under unrecognized names.
There is deliberately no browser bypass for exclusions.

After successful import, the catalog is regenerated and a details link shows
the description, README, main file, and history link. HTML apps open on a
separate sandboxed, read-only loopback content origin. Arbitrary imported Java apps are not executed:
details show JDK 21 guidance and mark unverified main-class/build details `TBD`.
Consult supplied instructions before compiling into a separate output directory.
The sandbox can limit apps needing downloads, storage, network access, or other
privileged browser features; after reviewing an app's trustworthiness, it can
also be opened directly from its imported `original` folder.

If preserved Markdown has invalid/missing links (including references to
excluded files), the UI explicitly reports **Project imported; catalog needs
repair** and retains the previous catalog. Review the diagnostics, repair
collection documentation without changing preserved originals, and rerun
`Main catalog` once validation succeeds. Catalog-write failures likewise
report a partial result, not success. Do not retry under a new name to hide errors.

Management and content servers bind only to `127.0.0.1`. Changing actions
require an exact Host, same-origin Origin, and random per-launch form token;
GET requests never open the chooser, launch apps, or mutate projects. Management
cannot execute arbitrary imported apps. Do not proxy/expose these ports to other machines.
These protections do not defend against malware or processes already running
as your user. Use one organizer process at a time; don't run CLI writes during
a browser import.

The bundled **Student Spin Wheel** is the sole desktop-launch exception:
its **Open app** button submits a token- and Origin-protected POST to a fixed
endpoint. The server verifies the five source files against reviewed SHA-256
fingerprints, compiles private copies using the running JDK 21 (with annotation
processing disabled), and starts the fixed `Main` class in a separate JVM.
Builds and desktop logs stay under ignored `out\student-wheel-*`; originals are
never compiled into or changed. Modified sources fail explicitly instead of
running unreviewed code. No request can choose commands, arguments, or paths.
Repeated clicks reuse the existing running instance; closing the server also
closes its desktop process. The window opens on the server computer, which must
have a graphical desktop and a full JDK 21. Startup failures appear on the page.
Run instructions remain under **Details** and in the independent offline
catalog; that offline page cannot launch Java.

## Records and checks

[DESIGN.md](DESIGN.md) describes the small structure and its relationship to
[the professor's conventions](https://github.com/paulkaefer/vibe-coding/blob/main/CONVENTIONS.md).
`projects\<slug>\prompts.md` is the metadata source of truth. Do not duplicate
its header into a hand-maintained catalog or JSON file. Project READMEs contain
run instructions, observed tests, and known limitations.

The checker supports this deliberately small Markdown record format:

- A `# Project: Name` title followed by the professor's `> - **Field:** value`
  header lines. Fields are mandatory and unique; unknowns are literal `TBD`.
- `Files` is `TBD` or comma-separated inline Markdown links to deliverables.
  Encode spaces, parentheses, and other special path characters as URL
  escapes. The importer does this automatically.
- Prompt sections use consecutive `# Prompt 1 (purpose):` and `## Output 1:`
  headings, with Tool, Verified, and Human edits context. Use fences for
  exact bodies; unavailable bodies are `TBD`, never reconstructed.
- Status is `prototype`, `keeper`, `abandoned`, or unknown `TBD`; reusability
  uses `artifact low; prompt pattern medium` (each rating may be `TBD`).
  Dates use `YYYY-MM-DD -> YYYY-MM-DD`, describing prompts, not import time.
  Tags and verification values follow the professor's vocabulary.
- Each project README requires `## Run`, `## Test results`, and
  `## Known limitations`. Links/images use inline `[label](relative-path)`
  syntax outside code. Absolute filesystem paths, symlink/collection escapes,
  missing targets/fragments, and unsafe URI schemes fail checks. HTTP(S)
  and mail links are not fetched. Reference-style links are reported as
  unsupported; raw HTML links and JavaScript-created links are not checked.

Unknown history/incomplete instructions cause warnings; invalid or missing
fields/references cause nonzero exit status. The checker verifies structure
and references, not the truth of claims, full Markdown semantics, arbitrary
HTML, or whether run commands actually work. Review imported Markdown that
uses other syntax rather than changing original files to appease the checker.
`catalog` runs checks, refuses errors, and regenerates `index.html` in stable
order. For first-time generation only, it permits a missing link to its own
pending `index.html` output; plain `check` still reports that missing target.
It never rewrites prompt bodies. Do not edit the generated index.

## Verification

Run Java checks as above. With Chrome or Edge installed, run the offline
browser harnesses using their isolated temporary browser profile under `out`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tests\run_browser_tests.ps1
```

The runner enables local-file iframe access only in that isolated test
browser, not your personal profile. It loads the real app and generated
catalog from disk; no test packages or server are used. See
[CSV browser fixtures](tests/csv_tests.html) and
[catalog browser fixtures](tests/catalog_tests.html).
IntelliJ itself and a human-operated browser still require manual verification.

For local management/import regression checks, compile first, then run:

```powershell
java -cp out LocalManagerTests
node tests\management_browser_tests.mjs
```

The second command additionally needs Node 22+ (built-in WebSocket), Chrome
or Edge, and a graphical desktop for the Java launch; no npm packages are needed.
Import checks use disposable sample projects. The browser test drives the real server
and HTML forms with Chrome mouse events, substituting a deterministic folder
selection provider for the native OS dialog. It checks visible review,
cancellation, exclusions, confirmation, details, HTML rendering, Java guidance,
collision feedback, and updated catalog entries, then verifies persisted bytes.
It also starts the real `Main serve` process and opens its printed URL to verify
the bundled app cards, descriptions, actions, Details links, and Add project
button together, clicks Student Spin Wheel's Open app, verifies the actual
desktop window on Windows, and checks that repeated clicks do not duplicate it.
Screenshots and console evidence are written under ignored `out`.

To rerun the bundled desktop app's own checks from the collection root:

```powershell
javac --release 21 -Xlint:all -d out\wheel-tests projects\student_spin_wheel\original\src\*.java projects\student_spin_wheel\original\tests\*.java
if ($LASTEXITCODE -ne 0) { throw 'Wheel compilation failed.' }
java '-Djava.awt.headless=true' -cp out\wheel-tests SpinWheelChecks
java -cp out\wheel-tests DesktopChecks
```

`DesktopChecks` opens Swing windows and needs a graphical desktop.

### Current baseline results

On 2026-10-29, on Windows with JDK 21 and Chrome:

| Executed check | Expected | Observed |
|---|---|---|
| `javac --release 21 -Xlint:all -d out src\*.java` | Clean Java 21 compilation | PASS; no compiler warnings |
| `java -cp out ToolboxTests` | Add/import integrity, error paths, CLI exit codes, exact history preservation, escaped/deterministic catalog | PASS; 79 assertions |
| `java -cp out LocalManagerTests` | Import protections, cancellation, exclusions, collisions, source preservation, desktop-launch protections, explicit failures | PASS; 68 checks |
| `node tests\management_browser_tests.mjs` | Browser import/cancel workflow, actual printed URL with all three apps, protected desktop launch and duplicate prevention | PASS; 43 browser checks plus 6 persistence checks |
| `java -cp out Main catalog` then `java -cp out Main check` | Valid records/references and generated offline index | PASS; 3 projects, 0 errors, 28 warnings |
| `tests\run_browser_tests.ps1` CSV harness | Supported CSV counts/errors, separate paste/file actions, filename/disabled state, stale reads | PASS; 92 checks |
| Same runner, catalog harness | Offline search, no-match feedback, every status filter | PASS; 24 checks |
| Copied wheel `SpinWheelChecks` and `DesktopChecks` | Roster/math/drawing and real Swing behavior | PASS; 204,350 assertions and 102 Swing checks |
| `git diff --check` and `git diff --cached --check` | No whitespace errors, including the staged baseline | PASS |

The 28 checker warnings retain unavailable metadata and history.
No rating, model name, or human verification was guessed to remove warnings.
The copied wheel compiles with four existing Java serialization lint warnings;
its original sources are retained rather than edited to suppress them.
The Java fixtures include binary/nested/single-file imports, unchanged
supplied history and source file sets, failed import cleanup, malformed
records, missing/escaping references, catalog bootstrapping, and actual CLI
success/failure exit codes. They use disposable import fixtures; the browser
launch check also exercises the actual bundled Student Spin Wheel.

These are agent checks, including interaction with the app in a real but
headless browser, not proof of human use. A graphical IntelliJ run was not
performed.

## Current limitations

- The local server is for a trusted local desktop only. Do not proxy or expose
  either loopback port to other computers.
- Only the exact reviewed bundled Student Spin Wheel can launch Java through the
  server. Other imported Java apps retain manual instructions. The offline catalog
  cannot launch Java, and changed bundled sources fail fingerprint verification.
- HTML apps opened through the server run on a sandboxed content origin. Storage,
  downloads, and network features may be restricted; open trusted HTML directly
  from disk when needed. Timer progress is session-only and local-file storage
  behavior varies by browser.
- Presentation Timer retains the original Prompt Mixer presentation script,
  slide references, and demo/deployment commands, reviewed and retained with the
  owner's approval. These are sample presentation content, not setup instructions
  or verified claims about Vibe Toolbox. Its interactive timer/backup workflow
  still needs manual verification; only script syntax and copied bytes were checked.
- Filename-based import exclusions and baseline content review are not a guarantee
  that arbitrary future imports contain no secrets. Review every proposed import.
- Native folder chooser behavior, human visual/accessibility testing, large-file
  performance, and other browser engines still require manual verification.

### Remaining manual verification

Run `Main serve` on the desktop, open its printed URL, click **Add project**,
and verify the native chooser opens (check behind the browser). Use a disposable
folder, cancel at the chooser and review stages, then confirm a fixture import.
Check keyboard/focus behavior, exclusion review, main-file selection, HTML
launch, Java supplied instructions, and visible success/details on your normal
browser. The native Swing chooser and human visual/accessibility interaction
have not been automated or claimed as manually verified.

Open the catalog normally (no test flags), try search plus each status
filter, and follow the CSV app/documentation links. Open CSV Quick Check,
select a real local CSV, try malformed input, and check keyboard navigation,
screen-reader announcements, and layout. Browser automation is not human
testing. Personally verify the imported Presentation Timer and Student Spin
Wheel using their project READMEs; do not upgrade historical verification
on that basis. Large-file performance and other browser engines are not yet
assessed.

## Update and version history

This baseline is versioned in Git; publishing/configuring a remote is a separate
step. Build/test output, local IDE workspace state, and common credentials are
ignored. Shared IntelliJ module configuration is included with relative paths.
Attributes preserve supplied original files
and prompt transcript bytes during Git checkout. Their whitespace rules allow
CRLF line endings without treating the preserved carriage returns as trailing
whitespace; other trailing-space and indentation checks remain enabled.

ZIP users download/extract a fresh version; Git users save their local work
before `git pull` from the eventual configured remote. Contributors edit
canonical records/app files, run checks and appropriate tests, regenerate the
catalog, review `git diff`/`git status`, then commit intentionally. Git records
future changes; it cannot recover unknown pre-import history. No old dates,
prompts, outputs, or verification outcomes should be invented.
