package com.anatdx.yukifb.backend

import com.anatdx.yukifb.model.EntryId
import com.anatdx.yukifb.model.FileByteProgress
import com.anatdx.yukifb.model.FileChecksumAlgorithm
import com.anatdx.yukifb.model.FileChecksumValue
import com.anatdx.yukifb.model.FileEntry

/** Optional host checksum service, independent of file-content read or write capabilities. */
interface FileChecksumCapability {
    /** Algorithms available for this entry; an empty list hides the checksum action. */
    fun algorithms(entry: FileEntry): List<FileChecksumAlgorithm>

    /**
     * Calculates all requested algorithms from the same file snapshot, off the UI thread.
     * Returns exactly one complete result per ID. Cancellation must finish protocol cleanup
     * before returning; a changed snapshot must throw FileContentChangedException.
     */
    suspend fun calculate(entryId: EntryId, algorithmIds: Set<String>, onProgress: (FileByteProgress) -> Unit): List<FileChecksumValue>
}

/** Optional host-owned temporary export and system sharing. No filesystem types cross this API. */
interface FileShareCapability {
    /** Whether this entry can be shared. Unsupported entries must not show the action. */
    fun canShare(entry: FileEntry): Boolean

    /** Prepares a complete read-only copy, then launches sharing only while still active. */
    suspend fun share(entryId: EntryId, onProgress: (FileByteProgress) -> Unit)
}

/** Optional host-owned read-only export and external application chooser. */
interface FileExternalOpenCapability {
    /** Whether this entry supports external opening. */
    fun canOpenExternally(entry: FileEntry): Boolean

    /** Prepares a complete read-only copy and launches the chooser; never writes changes back. */
    suspend fun openExternally(entryId: EntryId, onProgress: (FileByteProgress) -> Unit)
}
