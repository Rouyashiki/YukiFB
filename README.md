# YukiFB

A backend-agnostic Material 3 Expressive file browser UI for Jetpack Compose.

YukiFB is an Android library rather than a file-manager app. It renders a compact,
single-column browser and supplies reusable navigation, selection, clipboard, and
file-operation state. Your backend decides what an entry actually is: an in-memory
CPIO node, a ZIP member, a root filesystem path, or something else.

This checkout is version `0.2.0`, supporting Android API 23 and newer.

## Installation

**Starting with `0.2.0`, the Maven Central coordinate changes from
`com.anatdx:yukifb` to `com.rouyashiki:yukifb`.** The optional Hex module uses
`com.rouyashiki:yukifb-hex`. Package names remain `com.anatdx.yukifb` and
`com.anatdx.yukifb.hex`; existing Kotlin and Java imports do not change.

Version `0.2.0` is available from Maven Central. Add `mavenCentral()` to your
dependency repositories, then:

```kotlin
dependencies {
    implementation("com.rouyashiki:yukifb:0.2.0")
}
```

For local `0.2.0` integration, run `gradlew.bat publishToMavenLocal`, add
`mavenLocal()` to the host's dependency repositories, and use version `0.2.0` for
`com.rouyashiki:yukifb` and the optional `com.rouyashiki:yukifb-hex` module.

## Design

- Stable opaque entry IDs instead of paths as identity
- A minimal read-only backend
- Small optional capability interfaces for mutations and Unix features
- Stateless Compose UI plus a ready-to-use headless controller
- Recursive filename search implemented over the minimal directory-listing backend
- Native Material 3 Expressive list states, motion, FAB menu, and action surfaces
- Conservative MIME and filename-based file icons with a host override
- A lightweight state-hoisted plain-text editor with selectable encodings and
  automatic LF/CR/CRLF detection and conversion
- An optional [binary editor module](yukifb-hex/README.md) with paged reads,
  byte editing, undo across saves, search/replace and character byte mappings
- No dependency on `File`, SAF, or an application module; the headless controller
  has no navigation dependency

## Basic usage

```kotlin
val controller = rememberFileBrowserController(
    backend = ramdiskBackend,
    initialDirectory = rootEntry,
)

FileBrowser(
    controller = controller,
    onOpenFile = { entry ->
        // Open the host application's editor or viewer.
    },
    onImport = { directory ->
        // Launch the host application's picker and import into directory.
    },
)
```

Implement `FileBrowserBackend` for listing and stat calls, then add only the
capability interfaces supported by the backend. Recursive filename search is
available automatically through `list`; a backend-specific search API is not
required.

## Host styling and navigation (0.1.2)

Place `YukiFBProvider` inside your existing `MaterialTheme`. Choose `Classic` or
`Expressive`, override container colors independently, and optionally supply an
icon renderer. Typography, shapes and motion specifications inherit the host theme;
transparent containers do not dim their contents. `YukiFBMotion.Reduced` uses brief
fades, and `None` switches immediately. The system animation setting also applies.

For a transparent host with a custom background, pass a `sceneBackground`
composable to `YukiFBProvider` so moving pages retain the same backdrop. The
background must cover the scene; container transparency remains independent.

Keep the controller and presentation above your browser/editor/viewer switch:

```kotlin
val presentation = rememberFileBrowserPresentationState(controller)
val page by controller.state.collectAsState()

YukiFBProvider(style = YukiFBStyle.Expressive) {
    if (showBrowser) {
        FileBrowserBackHandler(
            state = page,
            presentationState = presentation,
            onAction = controller::dispatch,
            predictiveBackEnabled = hostPredictiveBackEnabled,
        )
        FileBrowser(controller, presentation, onOpenFile = ::openDocument)
    }
}
```

Back handling is opt-in and uses the host's NavigationEvent dispatcher. Existing
`FileBrowser` overloads do not take ownership of system back. The local handler
closes the creation menu, clears selection, stops search, or returns to the parent;
root back remains a host decision. Gesture previews never invoke backend work.
Ancestor previews are bounded and non-interactive, and a committed return reloads
the destination. Call `presentation.invalidatePreviews()` after mutations made
outside the controller, then refresh the controller.

Hosts can use `YukiFBScene` and `YukiFBBackHandler` for viewer transitions without
exposing NavigationEvent types through their UI. Set `previewEnabled = false` when
the action requires confirmation. If the host owns unsaved-text confirmation,
use `TextFileEditor(..., confirmClose = false)` and send toolbar and system back to
the same host decision. Otherwise the editor retains its own close confirmation.

For text files, decode content in the host using the selected encoding and pass it
to the optional editor. YukiFB detects and normalizes line endings for editing,
then includes the selected encoding and globally converted line endings in each
save request:

```kotlin
val initialEncoding = TextFileEncoding.UTF_8
val editorState = rememberTextFileEditorState(
    initialText = contentSource.decode(initialEncoding),
    initialEncoding = initialEncoding,
)

TextFileEditor(
    fileName = entry.name,
    state = editorState,
    onReopenWithEncoding = { encoding ->
        // The editor confirms before discarding unsaved changes.
        editorState.replaceDocument(
            text = contentSource.decode(encoding),
            encoding = encoding,
        )
    },
    onSave = { request ->
        // request.text already uses request.lineEnding throughout.
        contentSource.encodeAndPersist(request.text, request.encoding)
        editorState.markSaved()
    },
    onClose = ::closeEditor,
)
```

The built-in choices are UTF-8, UTF-8 BOM, UTF-16BE, UTF-16LE, UTF-32BE,
UTF-32LE, LF, CR, and CRLF. Pass restricted lists to `availableEncodings` or
`availableLineEndings` when a backend supports only a subset; pass an empty list
to hide the corresponding control.

## File actions and checksums

The core library also provides a centered, responsive `FileActionsDialog` and a
`FileChecksumsDialog`. Neither requires `yukifb-hex`. Supply only available actions
through the existing `FileBrowser.onOpenFile` callback; action IDs, like entry IDs,
must remain stable. Directories and selection keep their existing behavior.

```kotlin
// Keep these in the browser's scope. FileBrowser.onOpenFile sets selectedFile.
var selectedFile by remember { mutableStateOf<FileEntry?>(null) }
var showChecksums by remember { mutableStateOf(false) }
var selectedAlgorithmIds by remember { mutableStateOf(setOf("MD5", "SHA-256")) }
val scope = rememberCoroutineScope()
var checksumController by remember { mutableStateOf<FileChecksumController?>(null) }
val checksumState = checksumController?.state?.collectAsState()?.value
val contentBusy = checksumState?.running == true // Disable conflicting host operations.
DisposableEffect(checksumController) {
    val controller = checksumController
    onDispose { controller?.cancel() }
}

selectedFile?.let { file ->
    if (showChecksums) {
        val controller = checkNotNull(checksumController)
        FileChecksumsDialog(controller) {
            selectedAlgorithmIds = controller.state.value.selectedIds
            showChecksums = false
            selectedFile = null
        }
    } else {
        FileActionsDialog(
            entry = file,
            actions = availableHostActions(file), // Already filtered by capabilities.
            onAction = { id ->
                if (id == "checksums" && !contentBusy) {
                    checksumController = FileChecksumController(file, checksumCapability, scope, selectedAlgorithmIds)
                    showChecksums = true
                }
            },
            onDismiss = { selectedFile = null },
        )
    }
}
```

`FileChecksumCapability` describes algorithms using stable IDs, display names and
hexadecimal lengths, and calculates the selected batch from one consistent content
snapshot. Return one `FileChecksumValue` per requested algorithm, including leading
zeroes. Report `FileByteProgress` using `Long` byte counts and propagate cancellation.
Throw `FileContentChangedException` if the snapshot becomes invalid. The controller
validates results, discards stale batches and compares explicitly entered values.

`FileShareCapability` and `FileExternalOpenCapability` are independent optional host
interfaces. The host owns temporary exports, URIs, MIME detection, system choosers
and retention. Use `FileActionProgressDialog` while preparing a copy. Cancel on
close and wait for transport cleanup before allowing mutations or another request;
the checksum controller reports `running` until its cancelled task actually ends.
Retain the controller above conditional dialog rendering while waiting for cleanup.
Successful copies must remain readable for their recipients after the page closes.
All dialogs inherit `YukiFBProvider` and the host Material theme.

## License

Apache License 2.0.
