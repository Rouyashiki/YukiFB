package com.anatdx.yukifb.ui

import com.anatdx.yukifb.model.FileEntry
import com.anatdx.yukifb.model.FileEntryType

/**
 * Semantic icon categories used by the default YukiFB file list.
 *
 * This is deliberately separate from [FileEntryType]: the latter describes
 * filesystem semantics, while this type only controls presentation.
 */
enum class FileIconType {
    DIRECTORY,
    SYMBOLIC_LINK,
    SPECIAL_FILE,
    GENERIC_FILE,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    ARCHIVE,
    PDF,
    ANDROID_PACKAGE,
}

/**
 * Infers a suitable [FileIconType] without opening [entry].
 *
 * Structural entry types take priority. Regular files are classified from an
 * optional MIME type and then from conservative filename and extension rules.
 * Executables, kernel modules, binary libraries, and unknown files intentionally
 * remain [FileIconType.GENERIC_FILE].
 */
fun inferFileIconType(entry: FileEntry): FileIconType {
    when (entry.type) {
        FileEntryType.DIRECTORY -> return FileIconType.DIRECTORY
        FileEntryType.SYMBOLIC_LINK -> return FileIconType.SYMBOLIC_LINK
        FileEntryType.BLOCK_DEVICE,
        FileEntryType.CHARACTER_DEVICE,
        FileEntryType.FIFO,
        FileEntryType.SOCKET,
        -> return FileIconType.SPECIAL_FILE

        FileEntryType.REGULAR_FILE,
        FileEntryType.UNKNOWN,
        -> Unit
    }

    iconTypeFromMime(entry.mimeType)?.let { return it }
    return iconTypeFromName(entry.name)
}

private fun iconTypeFromMime(rawMimeType: String?): FileIconType? {
    val mimeType = rawMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    return when {
        mimeType.startsWith("text/") -> FileIconType.TEXT
        mimeType.startsWith("image/") -> FileIconType.IMAGE
        mimeType.startsWith("audio/") -> FileIconType.AUDIO
        mimeType.startsWith("video/") -> FileIconType.VIDEO
        mimeType == "application/pdf" -> FileIconType.PDF
        mimeType == "application/vnd.android.package-archive" -> {
            FileIconType.ANDROID_PACKAGE
        }

        mimeType in archiveMimeTypes -> FileIconType.ARCHIVE
        mimeType in textualApplicationMimeTypes -> FileIconType.TEXT
        else -> null
    }
}

private fun iconTypeFromName(rawName: String): FileIconType {
    val name = rawName.lowercase()
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")

    return when {
        name in knownTextFileNames ||
            name.startsWith("fstab.") ||
            name.endsWith("_contexts") ||
            extension in textExtensions -> FileIconType.TEXT

        extension in imageExtensions -> FileIconType.IMAGE
        extension in audioExtensions -> FileIconType.AUDIO
        extension in videoExtensions -> FileIconType.VIDEO
        extension == "pdf" -> FileIconType.PDF
        extension in androidPackageExtensions -> FileIconType.ANDROID_PACKAGE
        extension in archiveExtensions -> FileIconType.ARCHIVE
        else -> FileIconType.GENERIC_FILE
    }
}

private val knownTextFileNames = setOf(
    "android.bp",
    "android.mk",
    "build.prop",
    "default.prop",
    "dockerfile",
    "fstab",
    "license",
    "makefile",
    "notice",
    "prop.default",
)

private val textExtensions = setOf(
    "c",
    "cc",
    "cfg",
    "cil",
    "conf",
    "cpp",
    "css",
    "csv",
    "gradle",
    "h",
    "hpp",
    "html",
    "ini",
    "java",
    "js",
    "json",
    "kt",
    "kts",
    "log",
    "md",
    "mk",
    "properties",
    "prop",
    "py",
    "rc",
    "rs",
    "sh",
    "te",
    "toml",
    "ts",
    "txt",
    "xml",
    "yaml",
    "yml",
)

private val imageExtensions = setOf(
    "avif",
    "bmp",
    "gif",
    "heic",
    "heif",
    "jpeg",
    "jpg",
    "png",
    "svg",
    "webp",
)

private val audioExtensions = setOf(
    "aac",
    "flac",
    "m4a",
    "mid",
    "midi",
    "mp3",
    "ogg",
    "opus",
    "wav",
)

private val videoExtensions = setOf(
    "3gp",
    "avi",
    "m4v",
    "mkv",
    "mov",
    "mp4",
    "mpeg",
    "mpg",
    "webm",
)

private val archiveExtensions = setOf(
    "7z",
    "bz2",
    "cpio",
    "gz",
    "jar",
    "rar",
    "tar",
    "tgz",
    "xz",
    "zip",
    "zst",
)

private val androidPackageExtensions = setOf(
    "aab",
    "apk",
    "apks",
    "xapk",
)

private val archiveMimeTypes = setOf(
    "application/gzip",
    "application/java-archive",
    "application/vnd.rar",
    "application/x-7z-compressed",
    "application/x-bzip2",
    "application/x-cpio",
    "application/x-gzip",
    "application/x-rar-compressed",
    "application/x-tar",
    "application/x-xz",
    "application/zip",
    "application/zstd",
)

private val textualApplicationMimeTypes = setOf(
    "application/javascript",
    "application/json",
    "application/toml",
    "application/x-httpd-php",
    "application/x-sh",
    "application/xhtml+xml",
    "application/xml",
    "application/yaml",
)
