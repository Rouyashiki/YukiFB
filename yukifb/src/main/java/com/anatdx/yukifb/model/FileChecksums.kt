package com.anatdx.yukifb.model

/** Host-defined checksum algorithm; [id] is stable and [hexLength] includes leading zeroes. */
data class FileChecksumAlgorithm(val id: String, val label: String, val hexLength: Int) {
    init { require(id.isNotBlank() && label.isNotBlank() && hexLength > 0) }
}

/** One complete hexadecimal checksum, identified by its algorithm rather than its label. */
data class FileChecksumValue(val algorithmId: String, val hex: String)

/** Progress through one file snapshot. A null [totalBytes] means the length is unknown. */
data class FileByteProgress(val completedBytes: Long = 0, val totalBytes: Long? = null) {
    init {
        require(completedBytes >= 0 && (totalBytes == null || totalBytes >= completedBytes))
    }
}

/** Signals that the host could not finish reading a single consistent file version. */
class FileContentChangedException(cause: Throwable? = null) : Exception("File content changed", cause)
