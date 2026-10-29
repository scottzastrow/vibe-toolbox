# Project: CSV Quick Check

> - **Files:** [csv_quick_check.html](csv_quick_check.html)
> - **Dates:** 2026-10-28 -> 2026-10-28
> - **Defaults:** GitHub Copilot; Auto (underlying model TBD)
> - **Status:** prototype
> - **Use:** Check local CSV files or pasted CSV text for row and column counts, blank cells, and duplicate rows without uploading data.
> - **Reusability:** artifact TBD; prompt pattern TBD
> - **Tags:** single-file-html, productivity, export-import
> - **Takeaway:** Check parsed contents rather than treating a filename extension as evidence.
> - **Story value:** TBD
> - **Surprises:** TBD

These are the available shared toolbox/CSV design and implementation
exchanges, not invented earlier history. Context dates are from the supplied
conversation timestamps. Final responses below are preserved verbatim;
progress messages and tool output are not conversation-final outputs.

# Prompt 1 (design the collection):

> - **Tool:** GitHub Copilot; Auto (underlying model TBD); 2026-10-28
> - **Verified:** not-run
> - **Human edits:** none; this exchange produced a design only, not an app.
> - **Session:** fresh

````text
We are building Vibe Toolbox for SEIS 606 Homework 8, Option 2: a lightweight framework for organizing and tracking small AI-built apps.

First inspect the existing project setup. Do not implement yet.

Read the professor’s conventions:
https://github.com/paulkaefer/vibe-coding/blob/main/CONVENTIONS.md

Propose a minimal Java 21 implementation using the existing IntelliJ build setup. Avoid external dependencies unless justified.

The framework should:
- Keep each app in its own snake_case folder.
- Preserve exact available prompts and AI final messages in prompts.md.
- Record the professor’s project metadata and verification levels.
- Use TBD for unknown history; never reconstruct or invent it.
- Include run instructions, actual test results, and known limitations.
- Generate a searchable, offline HTML catalog from project records without duplicating manually maintained metadata.
- Provide commands to add a project, generate the catalog, and check required fields and local file references.
- Support importing existing projects without modifying their original files.
- Use Git for version history.
- Explain the intended audience and how someone downloads, runs, updates, and verifies the collection.

Three demonstration projects:
1. Presentation Timer — existing single-file browser app.
2. Student Spin Wheel — existing Java desktop app.
3. CSV Quick Check — new single-file HTML utility.

Do not fabricate or recreate the two existing apps; their files will be supplied.

CSV Quick Check will parse supported comma-delimited CSV, report structural errors, rows, columns, blank cells, and duplicate rows. Processing stays in the browser. Credit classmate Ezana Asfaw’s presentation as inspiration for checking file contents rather than trusting the extension. Duplicate removal is outside the initial scope.

Create a short DESIGN.md explaining the proposed structure, commands, metadata source of truth, import workflow, and meaningful verification. Keep documentation proportional to a small toolbox.

Stop after the design and summarize what was saved and any decisions needing my input.
````

## Output 1:

````text
Saved **[DESIGN.md](DESIGN.md)** after inspecting the IntelliJ starter and reading the professor’s conventions. No implementation or existing source files were changed.

The proposal uses Java 21 without external dependencies, canonical metadata/history in `prompts.md`, unchanged app imports, `add`/`check`/`catalog` commands, and a generated searchable offline catalog. It also covers verification evidence, CSV scope and attribution, Git, and distribution.

**Input needed before implementation:** confirm the proposed CSV counting rules and inclusion of clearly labeled non-keeper projects in the catalog. Importing Presentation Timer and Student Spin Wheel awaits their original files and any available history; missing information stays `TBD`.
````

# Prompt 2 (implement with revised CSV rules):

> - **Tool:** GitHub Copilot; Auto (underlying model TBD); 2026-10-28
> - **Verified:** agent-tested
> - **Human edits:** none; real-browser automated parser/file/paste tests are recorded in README.md, not human testing.
> - **Session:** continued

````text
Proceed with implementation using the proposed design.

CSV rules: treat the first record as the header; exclude it from data-row counts. Ignore completely empty lines outside quoted fields, preserving line breaks inside quoted fields. Count empty or whitespace-only cells as blank. Detect duplicate data rows by exact parsed cell values, preserving case and spaces. Report inconsistent column counts and malformed quoting as structural errors. Document these rules.

Include all project statuses in the catalog with clear labels and a status filter.

Implement the organizer and CSV Quick Check. Leave the timer and spin wheel imports pending until I supply their original files. Do not invent project history or verification results.

Run meaningful checks, summarize results and remaining manual verification, and leave changes uncommitted.
````

## Output 2:

````text
Implemented the **Java 21 organizer** (`add`, byte-preserving import, `check`, `catalog`) and **[CSV Quick Check](projects/csv_quick_check/csv_quick_check.html)**. The generated **[offline catalog](index.html)** has search and all-status filtering. CSV rules, exact available prompts/final messages, run instructions, and limitations are documented.

**Checks passed:** 76 Java assertions, 61 CSV browser checks, and 24 catalog browser checks. Record validation reports **0 errors and 6 honest `TBD` warnings**. Browser checks ran in headless Chrome; no human verification is claimed.

**Still pending:** human/visual/accessibility checks, IntelliJ launch verification, and the original timer/spin-wheel files. Git is initialized; all changes remain uncommitted.
````
