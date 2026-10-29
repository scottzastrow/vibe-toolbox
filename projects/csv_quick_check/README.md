# CSV Quick Check

## Run

Open [csv_quick_check.html](csv_quick_check.html) directly in a modern browser.
Select a UTF-8 file of any extension, confirm its displayed filename, then click
**Check file**. This button is disabled until a file is selected; selection
alone does not process it. Alternatively, paste text and click **Check pasted
text**, which checks only the text box even when a file is selected.
No Java, installation, server, external assets, or network access
is required. Input stays in the browser's memory.

Inspired by classmate Ezana Asfaw's presentation: inspect file contents rather
than trusting the extension. This credits the idea, not borrowed source code.

### Supported CSV and counts

Support comma delimiters, UTF-8 with optional BOM, LF/CRLF, quoted commas and
newlines, and doubled quotes. The first non-ignored record is the header.
Data-row counts exclude it; blank-cell and duplicate counts also concern
data only. Header names are not validated.

Ignore completely empty lines outside quotes, but preserve line breaks
inside quotes. A whitespace-only line is a record. Empty or whitespace-only
data cells are blank. Duplicate data rows compare exact parsed values,
preserving case, spaces, and embedded line endings; count every extra
occurrence after the first. Different quoted representations of the same
parsed cells compare equal.

Report inconsistent column counts and malformed quoting as structural
errors. Withhold all totals on errors, rather than present partial counts
as success. Error records count the header but exclude ignored lines;
physical line numbers include ignored lines. Empty input has zero rows and
columns; a header-only file has zero data rows.

For `name,score` followed by two identical `Alex,10` records, expect 2 data
rows, 2 columns, 0 blank data cells, and 1 duplicate data row.

## Test results

2026-10-28, Windows, headless Chrome 156.0.8078.73:
`tests\run_browser_tests.ps1` from the collection root passed **92 CSV
assertions** against this actual HTML app. Expected counts/errors matched
observed results for empty/header-only inputs, BOM/CRLF, ignored lines,
quoted commas/newlines/escaped quotes, blank data cells (including Unicode
whitespace), header exclusion, exact case/space/newline-sensitive duplicate
values, multiple ragged rows, and malformed quoting/error line numbers.

The harness also checked the filename display, initially disabled file action,
selection without automatic processing, and separate file/paste buttons with
both inputs populated. It explicitly checked a real browser `File` named
`misleading.bin` containing valid CSV (observed 1 data row), repeated the check
without reselection, and checked malformed CSV and invalid UTF-8 bytes (observed
explicit errors and no counts). Clearing selection disabled the file action
without disabling paste checks. Controlled pending reads confirmed that pasting,
replacing a file, or clearing selection prevents stale results from overwriting
the current state. It uses the real browser DOM, File API, and decoder rather
than a substitute parser.

The automated checks above support `agent-tested`. The collection's Java
organizer and catalog results are recorded
in the [collection verification evidence](../../README.md#current-baseline-results).

### User-reported manual verification

On 2026-10-28, the user reported manually verifying both file selection with
**Check file** and pasted text with **Check pasted text**, using this sample:

```csv
name,course,score
Alex,SEIS 606,92
Jordan,SEIS 606,
Casey,SEIS 606,88
Alex,SEIS 606,92
Morgan,,95
```

Both paths displayed the expected **5 data rows, 3 columns, 2 blank cells,
and 1 duplicate row**. This records `human-tested` evidence for these two
sample-input paths only; it is user-reported, not an additional agent-observed
browser run. The browser/version was not specified. No accessibility audit,
large-file benchmark, or alternate-browser coverage is claimed.

## Known limitations

Manual verification is limited to the sample and paths above.
No duplicate removal, XLSX, delimiter/encoding detection,
data-type/semantic checks, or large-file guarantees. Entire input and
duplicate keys are in memory; large files may freeze the browser tab.
Bare CR line endings, quotes in unquoted cells, and spaces after closing
quotes are unsupported and reported as structural errors.

Any valid one-column text can be CSV; a successful parse is not a security,
authenticity, or content-quality guarantee. Pasted text uses the browser's
textarea line-ending normalization; file input preserves embedded LF/CRLF
for exact duplicate comparisons. Click **Check file** again to recheck the
selected file without reselecting it.

See [collection verification](../../README.md#verification) for automated
commands and remaining manual checks.
