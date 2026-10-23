# MSForest file management and Instrument column

**Canonical plan document:** `docs/uu-msforest-file-management.md`

**Scope revision approved by the user on October 23, 2026.** This revision incorporates the approved follow-up requests and accepted review corrections. It supersedes the original restrictions on EncyclopeDIA instrument values and association extensions.

## Intent

Address navigation and chooser behavior, instrument metadata, desktop file-manager actions, and OS integration. Support Windows, macOS, and Linux, reusing the existing MSForest browser when a dataset is opened externally.

File → Open, drag-and-drop, and OS associations navigate and select supported datasets without launching a visualization. Existing explicit visualization actions remain available. Filesystem roots are an exception to browser listing and selection: they show the directory-selection hint without recursively scanning the drive, and an explicit dataset-open request explains this limitation.

## Implementation context

- [RawFileBrowser.java](/Users/searle.brian/Documents/java/MSRawJava/gui/src/main/java/org/searlelab/msrawjava/gui/RawFileBrowser.java), `openAndSelectFile`, `navigateFiles`, `applyNavigationTarget`, `applyPendingSelection`, and `updateDirectory`, provides shared navigation, selection, root protection, and open-request feedback. [FileNavigation.java](/Users/searle.brian/Documents/java/MSRawJava/gui/src/main/java/org/searlelab/msrawjava/gui/FileNavigation.java) resolves paths off the EDT and discards superseded requests.
- [BrowserDatasets.java](/Users/searle.brian/Documents/java/MSRawJava/gui/src/main/java/org/searlelab/msrawjava/gui/BrowserDatasets.java), `isBrukerBundle`, `datasetPath`, and `find`, applies GUI-only TDF recognition, resolves TDF files to their containing bundle, and traverses ordinary `.d` folders. Core `VendorFile` and `VendorFileFinder` retain their existing discovery semantics and TSF diagnostic paths.
- [MenuManager.java](/Users/searle.brian/Documents/java/MSRawJava/gui/src/main/java/org/searlelab/msrawjava/gui/MenuManager.java), `createRawFileChooser`, makes valid Bruker bundles terminal selections. `chooseRawFile` passes approved selections to browser validation so unsupported inputs receive feedback.
- [GuiMain.java](/Users/searle.brian/Documents/java/MSRawJava/gui/src/main/java/org/searlelab/msrawjava/gui/GuiMain.java) and `OpenFileDispatcher` handle startup arguments and later launcher notifications. [msforest.install4j](/Users/searle.brian/Documents/java/MSRawJava/gui/msforest.install4j) configures the shared launcher and platform associations.
- [ThermoRawFile.java](/Users/searle.brian/Documents/java/MSRawJava/core/src/main/java/org/searlelab/msrawjava/io/thermo/ThermoRawFile.java) retains the open-reply instrument model without structure discovery. [MzmlFile.java](/Users/searle.brian/Documents/java/MSRawJava/core/src/main/java/org/searlelab/msrawjava/io/mzml/MzmlFile.java) resolves instrument-level parameter-group references during header parsing. The GUI's `InstrumentNames` helper normalizes optional names and reads EncyclopeDIA metadata.

## Approved scope

- Dragging a file onto the tree reveals its enclosing directory. Supported datasets also become selected in the table. The entire tree pane provides drop feedback without highlighting a directory row as the destination.
- Ordinary directory drops navigate into that directory. A GUI Bruker bundle requires a `.d` directory containing a regular `analysis.tdf` file; it reveals its parent and becomes selected. Other `.d` folders, including TSF-only acquisitions, remain ordinary navigable folders in the GUI.
- `.raw`, `.dia`, `.mzML`, and `.tdf` associations support initial startup and an already-running installed application on all three platforms. A TDF file resolves to its containing valid `.d` dataset; standalone TDF reading is outside scope.
- The chooser treats valid Bruker `.d` bundles as terminal selectable datasets. Approved unsupported selections receive an error; canceling stays silent.
- A sortable text column named **Instrument** appears immediately after **Vendor**, including when upgrading saved table layouts. EncyclopeDIA instrument metadata is included.
- Table rows and physical directory-tree nodes offer **Show in Finder**, **Show in File Explorer**, or **Show in Files**, according to the desktop. The existing **Show Enclosing Folder** action continues to navigate within MSForest.
- Keep changes within Java core/GUI, installer configuration, tests, and relevant manual text. No Rust, C#, protobuf, dependency, or toolchain changes. Preserve public core discovery and CLI diagnostics.

## Implementation approach

### 1. Shared navigation, drop feedback, and chooser support

- Use `openAndSelectFile` as the shared dataset entry point. Normalize paths and associate pending selection with the current load request so obsolete loads cannot select the wrong row.
- Handle files in the current directory without relying on a new tree-selection event. Refresh when necessary to discover a newly added file.
- Explicitly opening a dataset clears only filters that hide that dataset, then selects and scrolls its row into view.
- Accept file-list drops over the tree and its empty viewport area. Highlight the full pane, preserve tree-row selection during hover, and use navigation-only drop handling. Drops never move, copy, convert, or visualize files.
- Apply the `analysis.tdf` requirement in GUI classification, navigation, discovery, and chooser traversability. Reuse core discovery while continuing into `.d` folders that are not GUI datasets. Do not send those folders to Bruker metadata readers.
- Keep `VendorFile` and `VendorFileFinder` behavior unchanged for core/library and CLI callers. TSF `.d` inputs must still reach the specific unsupported-TSF diagnostic rather than disappearing from discovery or becoming generic unsupported inputs.
- Select the combined supported-dataset chooser filter by default and provide a Bruker `.d` filter. Valid bundles are non-traversable selections; ordinary folders remain navigable.
- Preserve the distinction between File → Open and View → Visualize Raw File. Both use the shared chooser and validation flow. Approved unsupported files or folders report a short error and preserve the current browser location; canceled selections produce no message.
- Guard filesystem roots in the shared directory-loading path, including startup restoration and programmatic navigation. Show the directory-selection hint, cancel obsolete loading, and never recursively scan or remember a root through navigation.
- When an explicit dataset-open target is at a drive or filesystem root, explain that it cannot be selected in the browser and must be moved into a subfolder for browser selection. Ordinary root navigation and startup restoration do not show an error dialog. Explicit visualization remains available.
- Keep discovery and metadata work off the EDT and dispatch Swing state changes and feedback onto it.

### 2. Instrument metadata and table integration

- Add the stable column key `instrument`, `String` type, and standard text rendering. Carry its nullable value through the existing row, metrics cache, and background loading flow.
- Add `ThermoRawFile.getInstrumentModelIfKnown(): Optional<String>`, retaining the existing open-reply model without additional RPCs or structure discovery. Reset its state for subsequent opens and closure.
- For Bruker, use trimmed `meta.InstrumentName`; leave missing values blank.
- For EncyclopeDIA `.dia` files, prefer meaningful `meta.InstrumentName`, falling back to `instrument.model`. Trim values and leave missing, blank, or generic placeholders blank.
- For mzML, resolve instrument-level parameter-group references by ID, including `CommonInstrumentParams`. Prefer an explicit instrument model over referenced metadata; prevent serial-number parameters from replacing it.
- Display meaningful mzML model names; leave generic “instrument model” placeholders blank. Join multiple distinct names in deterministic sorted order using `; `.
- Insert Instrument after Vendor when an older saved layout lacks the new key. Preserve existing widths, sort keys, legacy numeric mappings, and subsequent user column placement.
- Isolate instrument extraction failures so existing TIC and gradient previews can still complete.

### 3. Desktop file-manager actions

- Add **Show in Finder** on macOS, **Show in File Explorer** on Windows, and **Show in Files** on Linux to the table popup and physical directory-tree node popups. Skip the virtual tree root.
- Ask the desktop to reveal the selected item in its enclosing folder when supported. Otherwise, open the enclosing folder. Reveal `.d` bundles in their parent folder; filesystem roots open directly.
- Prefer Java Desktop integration, with platform command fallbacks using separate path arguments and no shell interpolation.
- Run desktop operations off the EDT and report missing paths or launch failures on the EDT.
- Preserve the existing internal **Show Enclosing Folder** action and explicit visualization actions.

### 4. Launcher and dataset associations

- Share a GUI open-request dispatcher between startup arguments and later launcher notifications. Queue requests until the browser is ready and dispatch navigation on the EDT.
- Use install4j’s existing `StartupNotification` API through an optional reflective adapter, preserving ordinary Maven/JAR launches without adding a runtime dependency.
- Register notifications on the main thread before startup dialogs. Parse quoted paths correctly and avoid duplicate handling. The [StartupNotification API](https://www.ej-technologies.com/resources/install4j/help/javadoc/com/install4j/api/launcher/StartupNotification.html) describes registration and buffering requirements.
- Enable single-instance forwarding for the same user/session. Restore and foreground the existing browser on external opens for all four associated extensions.
- Configure `.raw`, `.dia`, `.mzML`, and `.tdf` in both Windows/Linux association actions and macOS launcher document settings. Include Linux MIME/desktop integration. Modern install4j separates macOS declarations from Windows/Linux actions; see its [migration notes](https://www.ej-technologies.com/install4j/whatsnew11).
- Resolve an associated TDF file to its containing valid Bruker `.d` bundle before navigation and selection. Reject TDF inputs without such a bundle through normal unsupported-input feedback.
- Offer optional association selection on Windows/Linux, initially unchecked, and register alternate viewer declarations on macOS. Document OS “Open with”/default-app steps for each extension without silently overriding existing defaults.
- Use distinct installer screen/component/action IDs so the association UI does not collide with the license screen.
- Update manual instructions for drops, `.d` selection, Instrument values, desktop reveal actions, error feedback, and platform association setup.

## Validation approach

- Test current-directory requests, delayed loads, superseded requests, hidden rows, normalized paths, and unsupported or missing inputs deterministically.
- Test full-pane drop feedback without directory-row targeting, plus drop classification and chooser traversability using temporary `.raw`, valid `.d`, ordinary `.d`, TSF-only `.d`, ordinary-directory, and uppercase-extension fixtures.
- Test root navigation at startup and through drops without starting a recursive loader or changing the remembered directory. Test root-level dataset feedback and unsupported chooser inputs for both Open and Visualize; verify cancellation remains quiet.
- Use a test-only in-memory preferences provider for GUI tests and verify it is active before modifying preferences. Desktop-dependent JFrame tests must check headless mode before creating windows. Run focused headless coverage and verify the expected skip; do not imply the entire GUI suite supports headless execution.
- Test startup buffering and forwarding for all four extensions, including quoted paths, spaces, Unicode, multiple arguments, and absent install4j runtime classes. Test TDF-to-bundle navigation and invalid standalone TDF inputs.
- Verify installer association parity across platforms, launcher wiring, optional selection defaults, MIME declarations, and unique IDs within installer applications.
- Test desktop reveal labels, fallbacks, roots, unusual path characters, missing paths, and failures.
- Extend table and preference tests for Instrument values, sorting, cache restoration, and old-layout insertion. Include EncyclopeDIA fixtures for both metadata keys and preview/cache propagation.
- Extend Thermo stub tests to prove instrument access adds no metadata/structure RPCs. Extend mzML fixtures for inline models, referenced groups, serial numbers, multiple instruments, and missing metadata.
- Preserve regression tests for core `.d` detection, CLI TSF diagnostics, and the library's specific TSF exception.
- Run focused tests, compile Java, and run the core/GUI suite as appropriate: `mvn -pl core,gui -am -Dskip.build.natives=true test`.
- Build installers with the existing install4j profile. Manually verify cold/warm opening for all four extensions, default-app selection, uninstall behavior, and desktop reveal on each platform. Java tests and installer dry runs do not establish native desktop correctness.

## Implementation and validation status

As of October 23, 2026, the Java/GUI behavior, installer configuration, tests, and manual changes are implemented. Native installer validation remains a separate completion gate.

- Java compile passed with `mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile`.
- Before the final feedback/test-isolation corrections, the full core/GUI suite ran 1,004 tests: 999 passed and 5 skipped, with no failures or errors.
- After those corrections, all 158 GUI tests passed. The focused desktop run passed 11 tests; the corresponding headless run passed 10 and skipped the JFrame test as intended. The full core suite was not rerun after these GUI-only corrections.
- The install4j dry run with JRE bundling disabled completed launcher generation and installer compilation for Windows, macOS Folder, and Unix Installer. It reported warnings for the script-editor JDK download and absent final output directory; the local JDK was used and media files were not copied to the final destination.
- The normal bundled installer build reached install4j but failed while downloading Adoptium JRE provider metadata because of a TLS certificate trust error.
- Remaining validation: complete the bundled installer build and perform native cold/warm opens, default-app selection, uninstall, and desktop reveal checks on Windows, macOS, and Linux.

### Historical pre-implementation baseline

The original baseline compile and these 53 selected tests passed before implementation began:

```text
mvn -pl core,gui -am -Dskip.build.natives=true -Dmaven.test.skip=true compile
mvn -pl core,gui -am -Dskip.build.natives=true -Dtest=GUIPreferencesTest,DirectorySummaryPanelVendorFilterTest,ThermoRawFileStubTest,MzmlFileTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Risks, decisions, and reviewer focus

- Navigation and instrument work are separable; launcher integration depends on shared navigation. Desktop integration has its own native validation gate.
- For multi-file drops/open payloads, use the first valid target for the requested operation in supplied order. Later independent requests supersede earlier pending navigation.
- Invalid opens report a concise error and preserve the current browser location. Root-level datasets instead navigate to the guarded hint state and explain the selection limitation.
- Root guards apply to browser scanning and remembered navigation; they do not prevent opening a filesystem root in the system file manager.
- GUI Bruker recognition requires `analysis.tdf`; public core discovery and TSF diagnostics retain their existing behavior.
- Single-instance reuse applies to installed launchers; standalone JAR launches retain their existing process behavior.
- OS associations cover `.raw`, `.dia`, `.mzML`, and `.tdf`. `.d` directories are supported through the GUI chooser and drops; an associated TDF file provides an OS entry point to its containing valid bundle.
- Primary review concerns are asynchronous selection correctness, saved-layout compatibility, avoiding expensive Thermo metadata calls, preserving core diagnostics, preference-safe tests, useful error feedback, and native installer behavior.
