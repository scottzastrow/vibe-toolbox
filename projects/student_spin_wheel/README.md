# Run and evidence

Single-file browser classroom tool. It spins a student roster, tracks previous
results, and can remove selected winners from future draws. The original Java
implementation and its checks remain preserved under `original`.

## Run

Open the [selected HTML main file](student_spin_wheel.html) directly in a modern
browser, or click **Open app** in either catalog. It works offline and needs no
server, installation, or external resources.

The wheel starts with Scott, Emma, Liam, Olivia, Noah, Ava, James, Sophia,
Lucas, and Mia. Each spin takes five seconds and chooses one remaining student
uniformly at random. Winner removal is enabled by default; unchecking it allows
repeat winners without restoring anyone already removed. Add unique names,
review results in order, and reset to restore only the original ten students
and clear the history. Reset preserves the removal setting and any text in the
name editor.

See the preserved [Java specification](original/SPEC.md) and
[implementation plan](original/PLAN.md) for the original version's behavior.

## Test results

On 2026-10-29, `node tests\management_browser_tests.mjs` passed 89 workflow
checks in real headless Chrome, plus 6 import persistence checks. The wheel
checks cover both catalog links (including the offline catalog opened from
disk), live details, the five-second animation and pointer alignment,
adding trimmed names by click/Enter, blank and duplicate rejection (including
removed winners), control locks, winner removal on/off, repeated winners,
ordered history, full-roster exhaustion, adding after exhaustion, reset,
mobile layout dimensions, and direct-file operation without a server.

Add Name uses click/Enter handlers rather than form submission so it works
with the live catalog's existing form-blocking sandbox. History uses the
ordered list's native numbering. No sandbox permissions were relaxed.
Screenshots and console evidence are saved under ignored `out`; the only
console error was the import fixture's intentional HTTP 400 collision.

All 11 preserved files under `original` match Git HEAD byte-for-byte.
The original Java checks were rerun: `SpinWheelChecks` passed 204,350 assertions
and `DesktopChecks` passed 102 Swing checks. Compilation produced four existing
serialization warnings; preserved sources were not edited.

## Known limitations

State lasts only for the current page session; reloading restores the original
roster. The browser wheel scales to the available viewport; large rosters and
long names shrink labels, with full names available on hover.

Human visual/accessibility testing, normal-browser keyboard and screen-reader
use, large-roster readability, and other browser engines still need manual
verification. Automated Chrome and Swing interaction is not human testing.
