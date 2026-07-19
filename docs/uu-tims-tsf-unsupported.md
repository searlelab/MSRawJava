# Safely reject unsupported TSF-only timsTOF directories

## Summary

Add a central preflight check for Bruker `.d` inputs: a directory containing `analysis.tsf` but no `analysis.tdf` is recognized as PASEF-off/TSF and rejected without opening SQLite. The CLI logs a concise unsupported-format error and continues processing other inputs; the GUI keeps its current parsing-failure presentation but will no longer create an empty `analysis.tdf`.

## Implementation changes

- First add this approved plan unchanged as `docs/uu-tims-tsf-unsupported.md`.
- In `BrukerTIMSFile.openFile`, validate the expected `analysis.tdf` path before constructing the SQLite JDBC URL.
  - If `analysis.tsf` exists and `analysis.tdf` does not, throw a dedicated unsupported-format exception with a clear message that TSF/PASEF-off files are not yet readable and no stack trace is appropriate for users.
  - If neither metadata file exists, fail with a normal missing-TDF input error.
  - Do not create, modify, or delete any file during detection.
- In the CLI Bruker conversion loop, catch only the dedicated TSF exception, emit it with `Logger.errorLine`, skip that directory, and continue with remaining inputs. Other failures retain existing behavior.
- Do not add GUI-specific visual state or messaging. Its existing generic failure path will receive the central validation error, while avoiding the prior SQLite side effect.

## Test plan

- Add a temporary TSF-only `.d` test for `BrukerTIMSFile.openFile`:
  - verifies the dedicated exception and user-facing message;
  - verifies `analysis.tdf` was never created.
- Add CLI coverage with a TSF-only directory plus a supported mocked `.d` input:
  - verifies a concise error is reported without a stack trace;
  - verifies conversion continues for the supported input and skips the TSF input.
- Run focused TIMS/CLI tests, then:

  `mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile`

## Assumptions

- TSF parsing and TSF Rust/native support are explicitly out of scope.
- TSF-only inputs remain discoverable as `.d` directories so users receive an explanation rather than silent omission.
- Existing accidentally created empty `analysis.tdf` files are not removed automatically; this change only prevents new ones.
- Since no CLI-outcome preference was supplied, skipped TSF inputs log an error while preserving the CLI’s existing successful completion behavior for other processed files.
