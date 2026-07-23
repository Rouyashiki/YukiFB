package com.anatdx.yukifb.model

/** The kind of node represented by a [FileEntry]. */
enum class FileEntryType {
    REGULAR_FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    BLOCK_DEVICE,
    CHARACTER_DEVICE,
    FIFO,
    SOCKET,
    UNKNOWN,
}

/**
 * Immutable entry displayed by the browser.
 *
 * [id] is the only identity used for selection and Compose keys. A backend may
 * freely change [name] or move the entry without changing its ID.
 */
data class FileEntry(
    val id: EntryId,
    val parentId: EntryId?,
    val name: String,
    val type: FileEntryType,
    val size: Long? = null,
    val modifiedAtMillis: Long? = null,
    val mimeType: String? = null,
    val unixMetadata: UnixMetadata? = null,
    val linkTarget: String? = null,
) {
    val isDirectory: Boolean
        get() = type == FileEntryType.DIRECTORY
}
