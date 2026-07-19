# XIC input feedback

**Canonical plan document:** `docs/uu-xic-input-feedback.md`

## Intent

Make MSForest XIC extraction report rejected and caveated input tokens while continuing to extract every valid target. Keep peptide mass and extraction behavior unchanged.

## Current understanding

`RawBrowserXicUtils.parseXicTargets` drops tokens for which `PeptideQueryParser.parseToken` returns empty. `RawBrowserXicController.extractFromInput` clears the XIC view when the selected scan type has no targets. `PeptideQueryParser.parsePeptide` deliberately ignores unrecognized named modifications, but exposes no indication that it did so. The XIC UI already creates and binds an unused `JLabel`, providing a local feedback surface.

## Desired direction and scope

Add an ordered, deterministic diagnostic result alongside parsed XIC targets: accepted tokens remain extractable, invalid tokens are reported as rejected, and peptide tokens with ignored named modifications are reported as caveated. Surface a concise summary in the XIC controls for every extraction request, including requests that yield no targets. Do not change supported peptide modifications, target masses, scan filtering, extraction algorithms, or conversion behavior.

## Implementation approach

1. Create this canonical plan document before implementation changes.
2. Extend the peptide parsing result model additively to retain ignored named-modification text while preserving existing parsing and mass calculations.
3. Extend `RawBrowserXicUtils.ParsedXicTargets` with ordered token diagnostics and make `parseXicTargets` classify each sanitized token as accepted, rejected, or accepted-with-caveat without changing target de-duplication or ordering.
4. Use the bound XIC feedback label in `RawBrowserScansTab` and `RawBrowserXicController` to show a concise, deterministic extraction summary; valid mixed input continues into the existing asynchronous extraction path, while all-invalid input clears state and leaves an explanatory message.
5. Add focused core and GUI unit tests for invalid tokens, ignored modifications, mixed valid/invalid input, no-valid-target feedback, and unchanged valid target selection.
6. Run selected core and GUI tests, the prescribed no-native compile, inspect the diff, and report any environment limitation.

## Validation approach

Use `PeptideQueryParserTest`, `RawBrowserXicUtilsTest`, and `RawBrowserXicControllerTest` for deterministic parsing and controller-state coverage. Then run the prescribed Maven compile and, when available, the relevant GUI module test command.

## Risks, decisions, and reviewer focus

The feedback wording is UI copy rather than a new parsing contract; tests should assert stable semantic categories and concise messages. Unknown named modifications remain intentionally ignored for mass calculation, but must be visible to the user. Review that diagnostics do not suppress valid targets in a mixed request and that Swing UI updates remain on the EDT.
