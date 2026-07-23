package com.anatdx.yukifb.model

/**
 * Optional Unix metadata attached to an entry.
 *
 * [permissions] contains the low twelve permission and special-mode bits
 * (`07777`), without the file type bits.
 */
data class UnixMetadata(
    val permissions: Int,
    val uid: Long,
    val gid: Long,
    val inode: Long? = null,
    val hardLinkCount: Long? = null,
    val deviceMajor: Long? = null,
    val deviceMinor: Long? = null,
) {
    init {
        require(permissions in 0..0xFFF) {
            "permissions must contain only the low twelve Unix mode bits"
        }
    }
}

/** Partial update for backends that support mutable Unix metadata. */
data class UnixMetadataPatch(
    val permissions: Int? = null,
    val uid: Long? = null,
    val gid: Long? = null,
) {
    init {
        require(permissions == null || permissions in 0..0xFFF) {
            "permissions must contain only the low twelve Unix mode bits"
        }
        require(permissions != null || uid != null || gid != null) {
            "A metadata patch must change at least one value"
        }
    }
}
