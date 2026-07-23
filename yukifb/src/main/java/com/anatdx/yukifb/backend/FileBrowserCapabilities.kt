package com.anatdx.yukifb.backend

import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.UnixMetadataPatch
import java.io.InputStream

/** Creates regular files and directories inside a backend. */
interface CreateEntryCapability {
    suspend fun createFile(parentId: EntryId, name: String): FileEntry

    suspend fun createDirectory(parentId: EntryId, name: String): FileEntry
}

/** Renames an entry without changing its stable ID. */
interface RenameEntryCapability {
    suspend fun rename(entryId: EntryId, newName: String): FileEntry
}

/** Deletes entries and, when applicable, their descendants. */
interface DeleteEntriesCapability {
    suspend fun delete(entryIds: List<EntryId>)
}

/** Copies entries within the backend. */
interface CopyEntriesCapability {
    suspend fun copy(entryIds: List<EntryId>, destinationId: EntryId)
}

/** Moves entries within the backend. */
interface MoveEntriesCapability {
    suspend fun move(entryIds: List<EntryId>, destinationId: EntryId)
}

/** Changes the supported subset of Unix metadata. */
interface UnixMetadataCapability {
    suspend fun updateMetadata(entryIds: List<EntryId>, patch: UnixMetadataPatch)
}

/** Creates a symbolic link. */
interface SymbolicLinkCapability {
    suspend fun createSymbolicLink(
        parentId: EntryId,
        name: String,
        target: String,
    ): FileEntry
}

/** Creates a hard link to an existing backend entry. */
interface HardLinkCapability {
    suspend fun createHardLink(
        parentId: EntryId,
        name: String,
        targetId: EntryId,
    ): FileEntry
}

/**
 * Re-openable source used for streamed content replacement.
 *
 * The backend owns and closes each stream returned by [openStream].
 */
fun interface FileContentSource {
    suspend fun openStream(): InputStream
}

/**
 * Optional streamed file-content access.
 *
 * The input stream passed to [read] remains owned by the backend and is valid only
 * for the duration of [reader].
 */
interface FileContentCapability {
    suspend fun <T> read(
        entryId: EntryId,
        reader: suspend (InputStream) -> T,
    ): T

    suspend fun replace(entryId: EntryId, source: FileContentSource)
}

/** Snapshot of operations supported by a backend instance. */
data class FileBrowserCapabilities(
    val canCreateEntries: Boolean = false,
    val canRename: Boolean = false,
    val canDelete: Boolean = false,
    val canCopy: Boolean = false,
    val canMove: Boolean = false,
    val canEditUnixMetadata: Boolean = false,
    val canCreateSymbolicLinks: Boolean = false,
    val canCreateHardLinks: Boolean = false,
    val canAccessContent: Boolean = false,
) {
    companion object {
        /** Inspects the optional interfaces implemented by [backend]. */
        fun from(backend: FileBrowserBackend): FileBrowserCapabilities =
            FileBrowserCapabilities(
                canCreateEntries = backend is CreateEntryCapability,
                canRename = backend is RenameEntryCapability,
                canDelete = backend is DeleteEntriesCapability,
                canCopy = backend is CopyEntriesCapability,
                canMove = backend is MoveEntriesCapability,
                canEditUnixMetadata = backend is UnixMetadataCapability,
                canCreateSymbolicLinks = backend is SymbolicLinkCapability,
                canCreateHardLinks = backend is HardLinkCapability,
                canAccessContent = backend is FileContentCapability,
            )
    }
}
