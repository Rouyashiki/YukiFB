# YukiFB Hex

Optional Android API 23+ library for a dense, paged binary editor. The module is
`com.rouyashiki:yukifb-hex:0.2.0`, with APIs under `com.anatdx.yukifb.hex`. It depends on
YukiFB; applications that only need the file browser do not need this module.
Both artifacts are available from Maven Central.

Starting with `0.2.0`, both modules use the `com.rouyashiki` Maven group. Their
package names remain `com.anatdx.yukifb` and `com.anatdx.yukifb.hex`.

## Integration

Add `mavenCentral()` to the host's dependency repositories and depend on
`com.rouyashiki:yukifb-hex:0.2.0`. Its metadata includes `com.rouyashiki:yukifb:0.2.0`.

For local development, run `gradlew.bat publishToMavenLocal` to publish both modules
locally and add `mavenLocal()` to the host's dependency repositories.

Build the two AARs with `gradlew.bat assemble`. A local AAR dependency does not
carry Maven transitive dependencies: hosts must provide the Compose, Material 3,
coroutines and NavigationEvent dependencies listed by the two Gradle modules.
The published Maven metadata supplies these dependencies.

Keep an editor session above navigation and give it a stable opaque entry ID:

```kotlin
import com.anatdx.yukifb.hex.backend.*
import com.anatdx.yukifb.hex.state.HexEditorController
import kotlinx.coroutines.CoroutineScope

fun openEditor(
    source: BinaryDataSource,
    scope: CoroutineScope,
    scratch: BinaryEditStorage,
    committer: BinaryCommitter?,
    codecs: List<BinaryTextCodec>,
) = HexEditorController(source, scope, scratch, committer, codecs)
```

Inside the host's Material theme and `YukiFBProvider`:

```kotlin
HexEditor(
    fileName = displayName,
    controller = editor,
    onClose = ::closeDocument,
)
```

The convenience overload manages bounded viewport reads and dirty-close prompts.
The hoisted overload accepts `HexEditorUiState`, `HexEditorWindow`, actions and
read callbacks. If the host owns navigation confirmation, use
`confirmClose = false` and route toolbar/system back through the same host policy.
Call the suspending `editor.close()` when the session ends, from a cleanup scope
that can finish after the UI leaves. Stop caller-owned direct reads first. The
controller cancels its own dispatched work and closes scratch storage. Closing
does not save automatically; scratch is not a persistent draft.

## Backend contracts

- `BinaryDataSource`: revision-bound identity, `Long` size and exact bounded reads.
  A stale source may reject reads, but must never return a different revision's
  bytes. Perform blocking I/O off the main thread.
- `BinaryEditStorage`: append-only immutable ranges. File-backed scratch belongs
  to the host. The included `MemoryBinaryEditStorage` is capped at 16 MiB and is
  intended for previews and small documents.
- `BinaryCommitter`: optional atomic save with a backend size limit. Consume
  `BinarySaveRequest.pieces` in order: reference `Original` ranges from the
  request's source, and read `Inserted` ranges through `readInserted` in bounded
  chunks. Return a new source with the same identity and the committed revision.
  A failure must preserve old content or invalidate the session if the outcome
  cannot be determined. Do not retain a save request after the call returns.
- `BinaryTextCodec`: host encoding, text input and glyph-to-original-byte ranges.
  ASCII is included. A Unicode host can supply UTF-8, UTF-16LE/BE and UTF-32LE/BE,
  as the YukiSU adapter does. `contextBytes` includes lookbehind/lookahead for
  cross-row and cross-page characters. Preserve every input byte, map malformed
  sequences to placeholders, and align fixed-width encodings using absolute offsets.

No committer produces a read-only viewer. Filesystem paths, CPIO, concrete file
I/O, pickers, permissions and image rebuilding remain host responsibilities.
Never issue reads through a locked backend session while streaming a save on
that same session: server-side original references and independent scratch reads
avoid that deadlock.

## Editing and limits

The persistent piece tree references original ranges and scratch ranges. It does
not load the complete document. Defaults are 64 KiB read pages, a 4 MiB source
cache and a 4096-row scrolling window backed by absolute `Long` offsets. Column
count adapts to available width and font scale without shrinking the font.

Overwrite is the default: input starts at the selection's first byte and keeps
the document length, rejecting input beyond EOF. Insert replaces a selected range
or inserts at the caret, including EOF and empty documents. Two nibbles typed consecutively into one byte form one
undo step. Undo/redo survive saves; removed bytes are captured before publication.
Large deletions therefore need scratch space proportional to the removed data.
Cancelled or failed operations can leave unreachable scratch bytes until close,
but never publish half an edit. History and scratch usage grow with session edits.

Destructive confirmation captures a `HexEditTarget` before displaying the prompt
(before entering the fill value for Fill). `HexEditorAction.Confirmed` applies the
captured range even if a search moves the selection, and rejects the operation if
content changed before it acquires the edit lock. Hosts providing their own prompts
should use the same action to bind confirmation to its original target.

Search operates in bounded chunks. Replace-all uses the original snapshot and
non-overlapping matches; inserted replacement bytes are not searched again.
The default atomic replace-all limit is 65536 matches. Search patterns and system
clipboard conversions default to 64 KiB; large selections use the internal byte
clipboard. `HexEditorOptions` lets the host tune page/cache/input limits. Backend
content limits still apply regardless of the editor's `Long` addressing.

Save serializes content changes while browsing remains available. Once commit
starts, the controller waits for its outcome before updating the saved position.
Failures keep edits and history available. The inspector reads only up to eight
bytes, respects byte order and labels incomplete values.

## Verification

Run `gradlew.bat testDebugUnitTest lint assemble`. Instrumented Compose tests are
in `src/androidTest`, and library-only previews are in `src/debug`; no application
module is needed. Instrumentation must target a dedicated test emulator explicitly.
Tests cover randomized editing, save/undo, failed scratch/commit operations,
cross-page search, superseded searches, 128 GiB virtual sources, adaptive layouts,
partial character selection and close confirmation.
