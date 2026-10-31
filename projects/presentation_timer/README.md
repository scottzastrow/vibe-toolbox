# Presentation Timer — user guide

An offline presenter workspace with speaking notes, slide references, an overall
elapsed clock, and individual countdowns. It comes populated with Scott Zastrow's
Prompt Mixer presentation, planned for **6 minutes 30 seconds**. The catalog calls
it Presentation Timer; the page itself is titled Prompt Mixer.

## Run

Choose **Open app** on the Presentation Timer card in Vibe Toolbox, or open
[original/index.html](original/index.html) directly in a modern browser.
Direct-file use requires no Java, account, internet connection, or API key.

If the toolbox's browser sandbox prevents local saving or backup downloads,
open the trusted HTML file directly from disk.

## Read the layout

Each top-level section shows its planned start/end time on the left. These are
schedule references, not actual timestamps. Indented rows are subsections.

| Area | Meaning |
|---|---|
| Overall time | Planned position in the presentation |
| Slide reference | PowerPoint slide number and title to consult manually |
| Speaking notes and demo actions | Prepared script and presenter directions |
| Duration | Remaining countdown for timed entries; fixed budget for parent sections |

Slide references do not open or control PowerPoint. The corresponding deck is
not bundled with this app. Blue text marks recognized presenter directions;
bold blue monospace text marks terminal commands. Ordinary script text remains
dark. Color classification uses the app's predefined patterns, so it does not
identify every possible instruction automatically.

The supplied server commands and app claims belong to the original Prompt Mixer
presentation. They are sample speaking material, not Vibe Toolbox setup commands.

## Start, switch, pause, and reset

1. Click a timed entry's heading, or an empty part of its row.
2. Its countdown starts and the overall elapsed clock starts on the first timed entry.
3. Click another timed entry to stop the previous countdown and start the next.
   The previous entry retains its remaining time; click it again to resume.
4. Select **Pause both clocks** to freeze elapsed time and the active countdown.
   Select **Resume both clocks** to continue.
5. Select **Reset All** for a fresh rehearsal. This resets all clocks, selections,
   and completion states while preserving the current notes and slide references.

Notes and slide text are read-only presentation content. Clicking that text does
not start a timer. Entry headings are buttons and can also be activated with the
keyboard.

### The two clocks

- **Actual presentation elapsed** counts up, including gaps between timed entries.
  It continues after an entry expires until you pause or reset.
- **Selected entry countdown** counts down for the selected timed entry. It shows
  `--:--` when no entry, or a parent section, is selected.
- Bold durations on timed rows also count down, using labels such as
  `45 sec` and `1 min 30 sec`.

Selecting a parent section stops the active child countdown but does not pause
the overall elapsed clock once started. Use **Pause both clocks** to pause it.

### Sections and subsections

A section without children has its own countdown. A section with children is a
container: its children have countdowns, while its displayed section budget is
a fixed reference. The parent's status reports completed children.

**Nothing advances automatically.** At zero, a timed entry completes and the
next entry remains idle. A completed entry cannot restart until **Reset All**.

| Color | Meaning |
|---|---|
| Yellow | Timed entry is running |
| Gray | Selected parent section or paused active entry |
| Light red | Entry completed |
| Light red across a parent section | All its subsections completed |

Switching away from an unfinished timed entry preserves its time and marks it
stopped. The selected parent section remains highlighted while one of its
children is selected.

## Notes, storage, and JSON backups

This version displays fixed notes and slide references rather than editable
fields. It is a prepared rehearsal aid, not a general presentation editor.
Changing section titles, timing budgets, or the default script requires editing
the HTML source.

- **Export notes & slides** downloads `prompt-mixer-notes.json` through your
  browser's normal download behavior.
- **Import notes & slides** accepts a complete version-1 backup with the existing
  entry IDs and required text fields. It asks for confirmation before applying.
- Importing does not reset or restore timers. **Reset All** does not erase notes.
- Notes are saved to browser storage after a successful import when storage is
  available. Check the storage-status message and export a backup before moving
  the file or changing computers.
- **Reloading resets timer progress.** Timer state is session-only and is not
  included in a backup.

**Current implementation caveat:** although the backup includes slide number
and title fields and requires them during validation, the importer currently
applies only notes. Displayed slide references remain the built-in mapping.
The automated-test entry also retains its built-in test-demo instructions.
Do not rely on importing a backup to change slide references or the schedule.

Browser storage is specific to the browser/profile and page origin. Local-file
storage behavior varies; moving the file or restarting the toolbox on a
different port may make previously saved notes unavailable. JSON export is the
portable backup.

## Suggested rehearsal

1. Select **Reset All**.
2. Start Purpose and SpecKit, then move through entries manually.
3. Observe the row countdowns and overall elapsed time.
4. Pause before an interruption; resume when ready.
5. When a subsection finishes, choose the next one yourself.
6. Use slide references to switch to the corresponding PowerPoint slide manually.
7. Reset to rehearse again; export notes before moving to another computer.

## Test results

The imported HTML was checked byte-for-byte against its sibling source, and its
embedded JavaScript passed a syntax check. Those checks do not establish visual,
keyboard, timing, or backup correctness.

This guide was checked against the stored HTML's controls and event handlers.
No new browser execution or human verification was performed for this
documentation update. The slide-import limitation above was identified through
code inspection.

## Known limitations

- Timer progress resets on reload and is not backed up.
- Parent section budgets and planned start/end times are fixed references.
- No automatic next-entry selection, PowerPoint control, or bundled slide deck.
- Notes are not editable in the page; backups must match the existing entry layout.
- Imported slide references are currently ignored, despite the control labels.
- Storage, downloads, and local-file behavior depend on browser permissions and
  the toolbox sandbox. Open trusted HTML directly if needed.
- The original Prompt Mixer script, slide mapping, and demo commands are retained
  as sample content; customize them before using this for another presentation.
- Human visual/accessibility and full interactive timer/backup checks remain
  pending in the toolbox's verification record.
- No network requests or external runtime services are used by the app itself.
