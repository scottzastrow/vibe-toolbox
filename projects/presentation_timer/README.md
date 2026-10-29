# Run and evidence

An offline presenter workspace with an overall elapsed clock, per-entry
countdowns, editable speaking notes and slide references, and JSON backup.
The catalog calls this app Presentation Timer; its original page title remains
Prompt Mixer.

## Run

Open [Presentation Timer](original/index.html) in a modern browser. Select a
timed entry or its row to start or resume its countdown. Use Pause both clocks
to pause or Reset All to reset the rehearsal.

Notes and slide references save in browser storage when available. Export a
JSON backup before moving or replacing the file.

## Test results

The imported HTML is byte-for-byte identical to the sibling source, and its
embedded JavaScript passes a syntax check. Browser interaction has not been
manually tested here.

## Known limitations

Timer progress is session-only and resets on reload. Browser storage for local
files varies by browser. No network access is used.
