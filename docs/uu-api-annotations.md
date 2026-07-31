# Freeze Consumer API with `@API` Annotations

**Canonical plan document:** `docs/uu-api-annotations.md`

## Intent

Define and annotate the permanent Java API used by the reported consumers. Add lifecycle metadata with `status` and `since`, while preserving current behavior and deprecated compatibility paths.

All frozen declarations will use `since = "v26.7.31"`.

## Current understanding

- No API annotation currently exists.
- The project targets Java 11.
- The new conversion facade is present in the working tree, including the public caller-owned-pool overload.
- `MassTolerance` and `DemuxConfig` are explicitly accepted as permanent API, including their supported concrete types and builders.
- Existing reader/model classes expose more public members than the reported consumers require.

## Desired direction and scope

Create `org.searlelab.msrawjava.API`:

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, METHOD, CONSTRUCTOR, FIELD})
public @interface API {
    Status status();
    String since();

    enum Status {
        STABLE,
        EXPERIMENTAL,
        DEPRECATED
    }
}
```

Annotate the following as `STABLE`, always with `since = "v26.7.31"`:

- Conversion facade: `RawFileConversion`, both `convert(...)` overloads, `ConversionOptions`, `ConversionOptionsBuilder`, `ConversionRequest` factories/getters, `ConversionResult` getters, `ConversionStatus`, `OutputType`, and `ProgressIndicator`.
- Reader/lifecycle declarations: the listed `StripeFileInterface` methods, the listed `ThermoServerPool` methods, `RawFileStructureTools.getDataType`, `MzmlFile()`/`openFile`/`close`, and `ProcessingThreadPool(int, int)`/`close`.
- Model declarations: the listed `PrecursorScan`, `FragmentScan`, `Range`, `WindowData`, and `ScanSummary` methods and constructors, plus `Pair(X, Y)`.
- Tolerance/configuration API: the complete current public surfaces of `MassTolerance`, `PPMMassTolerance`, `TIMSMassTolerance`, and `DemuxConfig`, including `DemuxConfig.InterpolationMethod` and `DemuxConfig.Builder`.

Add explicit constructors where required to annotate currently implicit constructors:

- Public no-argument `MzmlFile()` constructor.
- Protected no-argument `MassTolerance()` constructor.

The protected `MassTolerance` constructor makes subclassing intentional and removes the implicit public constructor. It does not prohibit external anonymous subclasses.

Mark the existing compatibility path as `DEPRECATED`, with `since = "v26.7.31"`:

- `ConversionParameters.builder()`.
- `ConversionParameters.Builder`.
- Direct legacy writer entry points `RawFileConverters.writeStandard`, `writeDemux`, and `writeTims`.

Do not annotate the full `ConversionParameters` aggregate or unrelated public reader/writer members as stable. Do not change behavior or signatures.

The public `convert(request, pool)` overload remains stable, with its ownership contract documented: the request must not specify a thread count, the caller owns the pool, and the caller remains responsible for shared Thermo-server lifecycle.

## Implementation approach

1. Write this plan verbatim to `docs/uu-api-annotations.md` before implementation changes.
2. Add the `API` annotation in the core module with runtime retention and Javadoc visibility.
3. Add annotations to the stable facade, reader, lifecycle, model, tolerance, and configuration declarations above.
4. Add the protected `MassTolerance()` and explicit annotated `MzmlFile()` constructors.
5. Add deprecation-status annotations to the legacy builder and direct writer compatibility surfaces.
6. Update API Javadocs and conversion documentation with:
   - stable versus deprecated lifecycle meaning;
   - facade migration examples;
   - pool ownership semantics;
   - automatic demultiplexing and output-path behavior.
7. Leave unrelated public nested classes unchanged in this patch. `DemuxConfig.Builder` is the intentional supported nested builder; other nested implementation types remain outside the frozen surface.

## Validation approach

Add focused API metadata tests that verify:

- `API` has the required fields and status values.
- Every planned stable declaration has `STABLE` and `since = "v26.7.31"`.
- Legacy declarations have `DEPRECATED` and `since = "v26.7.31"`.
- `MassTolerance` has no public constructor.
- `MzmlFile()` is explicitly public and annotated.
- `DemuxConfig.Builder` and `InterpolationMethod` are annotated as stable.
- Unlisted implementation members are not accidentally annotated.

Run:

```text
mvn -pl core -am -Dskip.build.natives=true -Dtest=RawFileConversionTest,ConversionParametersTest,DemuxConfigTest,MassToleranceTest test
mvn -pl core,gui -am -Dskip.build.natives=true test
mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile
```

## Risks, decisions, and reviewer focus

- Annotating all public members of `MassTolerance` and `DemuxConfig` intentionally freezes their current dependency types and nested builder surfaces.
- Keeping `convert(request, pool)` stable commits the project to current pool and Thermo lifecycle semantics.
- `@API` is metadata only; it does not enforce compatibility automatically.
- The implementation must preserve the existing uncommitted facade changes and avoid unrelated visibility or behavior changes.
