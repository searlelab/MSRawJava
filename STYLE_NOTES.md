# STYLE NOTES

## CORE house style summary
- Tabs for indentation; K&R brace style.
- Compact spacing around operators (e.g., `a=b`, `if (x==y)`), minimal whitespace.
- Imports grouped with blank line between JDK/third‑party/org when multiple groups are present.
- Class-level Javadocs for public API classes; method Javadocs used for key entry points.
- Public APIs expose concrete types if available (`ArrayList`, `HashMap`) rather than interfaces.
- Heavy use of primitive arrays (`double[]`, `float[]`) and primitive math utilities.
- Null checks with early returns are common (`if (x==null) return ...`).
- Optional is used for absent values (although not always consistently).
- Comparators implemented via nested classes or inline lambdas; `Collections.sort` and `List.sort` both appear.
- Custom `Logger` is the primary logging mechanism; do not use System.out/err or third party loggers for logging.
- Exceptions are typically propagated upward rather than wrapped in domain-specific result types.
- Immutability is implied by `final` fields; defensive copying is generally unnecessary.

## GUI house style summary (when different than CORE)
- Swing classes define `serialVersionUID`.
- UI construction is done in constructors or `init`/`build` helpers.
- Swing updates happen on EDT via `SwingWorker` or `SwingUtilities.invokeLater`.
- `GUIPreferences` is the canonical place for persistent UI settings.
- Background work uses a mix of `SwingWorker`, custom pools, and ad-hoc `ExecutorService`s, depending on the need.
