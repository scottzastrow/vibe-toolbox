# Vibe Toolbox design

SEIS 606 Homework 8, Option 2: a small collection for classmates, instructors,
and future maintainers to find, run, and assess AI-built apps. The offline
catalog needs no server. Optional local management uses only JDK 21 APIs;
no database, package manager, external service, or paid API is needed.

## Setup and structure

The original workspace was an IntelliJ starter (`src\Main.java`, output `out`)
without a declared SDK/module or Maven/Gradle. Keep that plain build: JDK 21,
an IntelliJ Java module, and standard-library APIs. `Main` dispatches commands;
`Toolbox` imports, validates records, and renders the catalog. `ToolboxTests`
is a dependency-free regression runner.

```text
src\                            Java organizer and tests
projects\<snake_case_slug>\
  prompts.md                    canonical metadata and exact conversation
  README.md                     run instructions, test evidence, limitations
  original\                     supplied app files, when importing
  <app's own filename>          for newly built apps
tests\                          offline browser harnesses and PowerShell runner
index.html                      generated, not manually edited
README.md                       collection usage and verification
```

CSV Quick Check, Presentation Timer, and Student Spin Wheel are registered
with their available artifacts and recorded history; unavailable history and
metadata remain marked `TBD`.

## Metadata and history

Follow [the professor's conventions](https://github.com/paulkaefer/vibe-coding/blob/main/CONVENTIONS.md)
(reviewed at commit `73f27d1c9dc04f6af42e90d7ed00145e056f8541`).
Each app owns a snake_case folder and `prompts.md`. Its project header holds
Files, Dates, Defaults, Status, Use, Reusability (artifact and prompt pattern),
Tags, Takeaway, Story value, and Surprises. Preserve all fields and use `TBD`
for unknowns. Do not infer model names, past usage, dates, or testing.

Prompt sections contain Tool/model/date, Verified, Human edits, exact prompt
text, and exact available final output. Session/Time are optional. Verbatim
bodies use fences longer than any fence within the text; the checker ignores
their contents. An unavailable final output remains `TBD` until it can actually
be captured. The shared design/implementation requests are recorded in CSV
Quick Check's history rather than copied into another metadata registry.

Verified uses `not-run`, `syntax-checked`, `agent-tested`, or `human-tested`,
at the highest evidenced level. `TBD` means unknown history, not `not-run`.
Headless parser-only checks do not prove UI operation; scripted real-browser
file/paste interaction can support `agent-tested`, never `human-tested`.
Record actual dated outcomes in README evidence, with notes for later upgrades.

## Commands and imports

Run from the collection root: `add <slug> --name <title> [--from <path>]`,
`check`, and `catalog` through Java `Main`. See [README](README.md) for commands.
Add creates templates, not application code. It refuses overwrites.

Import accepts a supplied file or directory, copies it under `original`,
checks SHA-256 per copied file, and publishes only a completed staging folder.
It preserves relative paths and bytes, including supplied history/README
files; toolbox documentation sits alongside the copy. It rejects symlinks,
overlapping source/destination paths, empty imports, and invalid slugs.
It never changes, compiles into, or writes docs into supplied sources.

### Local browser management

`Main serve` starts a management server bound explicitly to `127.0.0.1` on
an OS-assigned port and prints its URL. Keep the process running while managing;
Ctrl+C stops it. The generated `index.html` remains an independent offline
catalog, not a management page.

The management root is a live catalog rendered from project records and README
descriptions, not a fixed list or a copy of the generated index. Cards contain
the name, description, HTML Open action or Java Run instructions link, and a
secondary Details link. Technical explanations and diagnostics live at `/about`
and project Details; unknown verification metadata is not shown on the home page.
After successful import or cancellation, a 303 redirect returns to the root
with an allowlisted status message. Import failures remain explicit; partial
imports show the catalog alongside their failure status and a Details link.

The bundled Student Spin Wheel is a single-file browser app. Its live-catalog
**Open app** link targets the separate sandboxed content origin, just like other
HTML apps; the independent offline catalog links directly to the same file.
Its previous Java source, specifications, and tests remain under `original` as
source history. The management server does not launch desktop processes.

An explicit **Add project** button above the cards POSTs to open a native Swing directory
chooser on the computer running Java. The browser cannot read arbitrary
filesystem paths; users never need to type a source path. Cancellation writes
nothing. A preview displays the selected folder, included files, exclusions
with reasons, and editable slug, name, description, and main-file selection.
Confirmation is a separate POST; cancel discards the preview. Only one preview
is active per launch. Confirmation rechecks its file inventory and SHA-256
hashes; changes require a fresh selection/review rather than silently importing
unreviewed files.

Both CLI and browser imports share a filtered inventory and verified-copy
implementation. Exclude Git/other VCS metadata, IDE settings, common build and
dependency directories, compiled artifacts, environment files, private keys,
credential stores, and common secret filenames. List exclusions before browser
confirmation. This conservative filename policy is not a content-based secret
scanner: the UI requires users to review all included files. Symlinks and
overlapping paths remain forbidden. Never overwrite an existing destination.
All retained original files, including available prompts/history, remain exact
copies under `original`; canonical unknown history/metadata stays `TBD`.

Store the reviewed description and main-file link in the generated project
README, not a database or duplicate metadata registry. Details are rendered from
that README and link to original files/history. HTML main files open on a
separate loopback content origin with a sandbox policy, never the privileged
management origin. Java main files show compilation/run guidance and identify
unknown entry-point/build details as `TBD`; the manager does not execute them.

Protect management POSTs with a cryptographically random per-launch token,
exact Origin and Host validation, bounded form bodies, and same-origin fetch.
GET never opens a chooser or changes state. Do not enable CORS. Management pages
use a restrictive CSP and no-store responses; do not serve filesystem paths
through management routes. The separate read-only content server serves only
non-sensitive regular collection files, rejects traversal/symlinks, and cannot
reach management endpoints. Loopback and tokens defend browser cross-site
requests, not malware or other processes already running as the same user.

On confirmation, publish the verified import and regenerate the catalog.
Validation errors in preserved supplied Markdown are reported explicitly;
the import remains available for repair and catalog regeneration is not claimed
as successful. Never rewrite original documentation to conceal errors. Catalog
replacement is atomic so an unsuccessful generation retains the last catalog.

Project Details also supports removing bundled or imported projects through a
protected POST confirmation that names the project. It explains that removal
deletes only the toolbox copy and catalog entry, leaves the original source
folder untouched, and may make uncommitted toolbox-copy files unrecoverable.
Cancellation changes nothing. Validate the slug and resolved project path,
reject symlink escapes, move the project aside while regenerating the catalog,
and restore it if validation or catalog generation fails. Removal succeeds
only when the catalog has been regenerated; GET must never mutate projects.

`check` validates canonical required fields, supported metadata values,
paired prompt/output sections, README sections, and local inline Markdown
links/images (including supplied Markdown). It detects missing targets,
invalid fragments, and collection escapes; it does not fetch external links
or certify historical truth. The supported Markdown subset and limitations
are explicit in the README. Errors fail; `TBD` warns without inventing facts.

`catalog` refuses validation errors and deterministically generates escaped
HTML with inline CSS/JavaScript. Open it via `file:`: no fetch, CDN, or runtime
Java is needed. Search and status filtering include every record/status,
including labeled `TBD`; keepers are marked recommended. Unlike the
professor's keeper-only public index, this is a full homework inventory.
There is no second manually maintained metadata file or PROJECTS array.

## CSV contract and meaningful verification

CSV Quick Check is a self-contained HTML app. Its UI and README credit
Ezana Asfaw's presentation as inspiration for inspecting file contents rather
than trusting extensions, not as a claim of code reuse.

The first non-ignored record is a header; all data counts exclude it.
Ignore completely empty lines outside quotes, but preserve quoted line
breaks. Empty or whitespace-only data cells count as blank. Duplicate data
rows compare exact parsed cell values, retaining case and spaces; count
extra occurrences. Support UTF-8/BOM, comma delimiters, LF/CRLF, quoted
commas/newlines, and doubled quotes. Report ragged rows/malformed quoting as
structural errors and withhold totals. Processing stays in-browser.
Duplicate removal and large-file guarantees are outside this version.

Java fixtures exercise add/import integrity, validation failures, history
preservation, and escaped/deterministic generation. Browser fixtures exercise
the actual CSV parser and file/paste UI, plus offline catalog search/status
filters. Document observed results, not just planned tests; retain visual,
accessibility, and human use checks as manual work.

Use Git for future version history, not retroactive invented commits. Keep
generated catalog/source/evidence together, exclude build outputs and local
IDE state, and preserve imported/transcript bytes through Git attributes.
This implementation is intentionally left uncommitted.
