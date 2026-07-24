# YukiFB

A backend-agnostic Material 3 Expressive file browser UI for Jetpack Compose.

YukiFB is an Android library rather than a file-manager app. It renders a compact,
single-column browser and supplies reusable navigation, selection, clipboard, and
file-operation state. Your backend decides what an entry actually is: an in-memory
CPIO node, a ZIP member, a root filesystem path, or something else.

The project is currently an early `0.1.0`.

## Installation

YukiFB is published on Maven Central:

```kotlin
dependencies {
    implementation("com.anatdx:yukifb:0.1.0")
}
```

## Design

- Stable opaque entry IDs instead of paths as identity
- A minimal read-only backend
- Small optional capability interfaces for mutations and Unix features
- Stateless Compose UI plus a ready-to-use headless controller
- Native Material 3 Expressive list states, motion, FAB menu, and action surfaces
- Conservative MIME and filename-based file icons with a host override
- A lightweight state-hoisted plain-text editor for scripts and configuration
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
capability interfaces supported by the backend.

For text files, decode content in the host and pass it to the optional editor:

```kotlin
val editorState = rememberTextFileEditorState(initialText)

TextFileEditor(
    fileName = entry.name,
    state = editorState,
    onSave = { text ->
        // Encode and persist through your backend, then:
        editorState.markSaved()
    },
    onClose = ::closeEditor,
)
```

## License

Apache License 2.0.
