# YukiFB

A backend-agnostic Material 3 Expressive file browser UI for Jetpack Compose.

YukiFB is an Android library rather than a file-manager app. It renders a compact,
single-column browser and supplies reusable navigation, selection, clipboard, and
file-operation state. Your backend decides what an entry actually is: an in-memory
CPIO node, a ZIP member, a root filesystem path, or something else.

The project is currently an early `0.1.1`.

## Installation

YukiFB is published on Maven Central:

```kotlin
dependencies {
    implementation("com.anatdx:yukifb:0.1.1")
}
```

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
- No dependency on `File`, SAF, a navigation framework, or an application module

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

## License

Apache License 2.0.
