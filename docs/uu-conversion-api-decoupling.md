# Decouple Conversion Facade Internals

## Intent

Remove legacy conversion-builder and writer coupling from the new single-input facade while preserving deprecated compatibility APIs for existing CLI, GUI, and library callers. The public facade should consume immutable request/options types, delegate through a package-private conversion execution seam, and be the only production code that constructs conversion results.

## Current understanding

- RawFileConversion directly invokes RawFileConverters.writeStandard, writeDemux, and writeTims.
- Its private parameters(...) method constructs deprecated ConversionParameters.Builder instances, so the new facade still depends on the legacy configuration aggregate.
- ConversionOptionsBuilder already provides the intended public immutable-options construction path.
- ConversionRequest and ConversionResult have public constructors; repository callers use those constructors directly, and only RawFileConversion creates results.
- RawFileConverters and ConversionParameters.builder() remain required compatibility surfaces for legacy CLI/GUI and low-level callers.
- Existing facade tests mock RawFileConverters statically and will need to target the internal execution seam after the boundary is introduced.

## Desired direction and scope

- Add a package-private ConversionExecutor (or equivalently named internal seam) that accepts ConversionOptions, resolved paths, demultiplexing state, pools, readers, and progress indicators, and bridges to the existing low-level writers during migration.
- Remove all direct RawFileConverters and ConversionParameters.Builder references from RawFileConversion.
- Construct package-private ConversionSettings directly from ConversionOptions for the new path; keep ConversionParameters adaptation confined to deprecated compatibility entry points and do not use deprecated types in the new facade path.
- Make ConversionRequest construction package-private and provide intention-revealing public factories for default output, output-directory, and explicit-output-path requests. Migrate repository callers and documentation to those factories.
- Make ConversionResult construction package-private so results are created only by the facade.
- Preserve ConversionParameters.builder(), RawFileConverters, output naming, demux selection, lifecycle ownership, exception behavior, and existing compatibility tests.
- Do not change low-level writer signatures, native/server protocols, output schemas, or unrelated conversion behavior.

## Implementation approach

1. Write this settled plan to docs/uu-conversion-api-decoupling.md before implementation changes.
2. Add the package-private execution seam and ConversionOptions-to-ConversionSettings mapping; route standard, demultiplexed, Bruker, and existing-pool execution through it.
3. Refactor RawFileConversion to depend only on the new seam plus ConversionOptions, ConversionRequest, and internal reader/writer abstractions.
4. Restrict request/result constructors and add public request factories; migrate Main, ConversionPane, the manual example, and facade tests.
5. Update facade tests to verify the internal seam receives options directly, no deprecated builder path is used, and constructors are not public while preserving output/status assertions.
6. Re-read the diff and run focused core/GUI tests, then the required Java compile.

## Validation approach

- Run focused facade, CLI compatibility, GUI conversion, and conversion-parameter tests.
- Verify static mocks and assertions now target the package-private execution seam rather than RawFileConverters.
- Run mvn -pl core,gui -am -Dskip.build.natives=true test if focused tests are green.
- Run mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile.
- Confirm no production reference from RawFileConversion to RawFileConverters or ConversionParameters.Builder, and no remaining repository call sites use public request/result constructors.

## Risks, decisions, and reviewer focus

- The internal seam may reuse RawFileConverters' package-private writer implementation, but ConversionParameters and its builder remain confined to deprecated compatibility entry points; the public facade path uses neither.
- Restricting constructors is source-incompatible for callers that directly instantiated these value types; public factories preserve supported construction without exposing result creation.
- Review factory overload validation, exact output-path propagation, and preservation of caller-owned pool/Thermo lifecycle behavior.
- Ensure the adapter does not accidentally reintroduce CLI discovery, logging flags, batch state, or deprecated builder usage into library conversion.
