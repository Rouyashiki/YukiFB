package com.anatdx.yukifb.hex.backend

import com.anatdx.yukifb.model.EntryId
import java.io.IOException
import java.util.Collections

/** Immutable bytes. Construction and array export copy their mutable inputs. */
class BinaryBytes private constructor(private val bytes: ByteArray) {
    /** Number of bytes in this bounded chunk. */
    val size: Int get() = bytes.size
    /** Returns the signed byte at [index]. */
    operator fun get(index: Int): Byte = bytes[index]
    /** Returns an independent mutable copy. */
    fun toByteArray(): ByteArray = bytes.copyOf()
    /** Copies this chunk into a caller-owned buffer. */
    fun copyInto(destination: ByteArray, destinationOffset: Int = 0, start: Int = 0, end: Int = size) {
        bytes.copyInto(destination, destinationOffset, start, end)
    }
    /** Factories for immutable chunks. */
    companion object {
        /** Empty chunk, also used at EOF. */
        val Empty: BinaryBytes = BinaryBytes(ByteArray(0))
        /** Copies [bytes]; later changes to the array do not affect this value. */
        fun copyOf(bytes: ByteArray): BinaryBytes = BinaryBytes(bytes.copyOf())
        internal fun owned(bytes: ByteArray): BinaryBytes = BinaryBytes(bytes)
    }
}

/** A revision-bound random-access source; implementations perform blocking I/O off the UI thread. */
interface BinaryDataSource {
    /** Stable opaque document identity. */
    val id: EntryId
    /** Opaque revision, scoped to this document's host session. */
    val revision: String
    /** Exact length of this revision. */
    val size: Long
    /**
     * Returns exactly min([length], size - [offset]) bytes, or throws.
     * A source must keep its revision readable or reject stale reads, never silently mix revisions.
     */
    suspend fun read(offset: Long, length: Int): BinaryBytes
}

/** Append-only scratch storage owned by one editor. Returned ranges remain immutable until close. */
interface BinaryEditStorage {
    /** Appends the complete chunk and returns its offset; failed appends may leave unreachable bytes. */
    suspend fun append(bytes: BinaryBytes): Long
    /** Reads exactly [length] bytes previously appended at [offset]. */
    suspend fun read(offset: Long, length: Int): BinaryBytes
    /** Releases scratch resources. The controller calls this after its operations stop. */
    suspend fun close()
}

/** One immutable range in the resulting document. */
sealed interface BinaryPiece {
    /** Length of this range. */
    val length: Long
    /** Bytes from the source revision named in the save request. */
    data class Original(
        /** Offset in the original source. */
        val offset: Long,
        override val length: Long,
    ) : BinaryPiece
    /** Bytes in the editor's append-only scratch storage. */
    data class Inserted(
        /** Offset read through [BinarySaveRequest.readInserted]. */
        val offset: Long,
        override val length: Long,
    ) : BinaryPiece
}

/** Frozen save plan. Valid for the duration of [BinaryCommitter.commit]; do not retain it afterward. */
class BinarySaveRequest internal constructor(
    /** Original source, including identity and expected revision. */
    val source: BinaryDataSource,
    /** Exact resulting length. */
    val size: Long,
    pieces: List<BinaryPiece>,
    private val storage: BinaryEditStorage,
) {
    /** Ordered ranges whose concatenation produces [size] bytes. */
    val pieces: List<BinaryPiece> = Collections.unmodifiableList(pieces.toList())
    /** Reads an inserted range without calling the original backend. */
    suspend fun readInserted(offset: Long, length: Int): BinaryBytes = storage.read(offset, length)
}

/** Optional atomic persistence. Failed commits must leave the source unchanged or report an invalid session. */
fun interface BinaryCommitter {
    /** Maximum resulting size supported by this backend. */
    val maxSize: Long get() = Long.MAX_VALUE
    /** Publishes the whole plan or throws; returns a source bound to the successfully saved revision. */
    suspend fun commit(request: BinarySaveRequest): BinaryDataSource
}

/** The host observed a changed source revision. The editor retains its pending edits. */
class BinaryRevisionConflictException(message: String = "The source revision changed") : IOException(message)

/** Bounded in-memory scratch storage for previews, tests and small host documents. */
class MemoryBinaryEditStorage(private val maxBytes: Long = 16L * 1024 * 1024) : BinaryEditStorage {
    private val chunks = java.util.TreeMap<Long, BinaryBytes>()
    private var length = 0L
    private var closed = false

    override suspend fun append(bytes: BinaryBytes): Long = synchronized(this) {
        check(!closed)
        require(bytes.size.toLong() <= maxBytes - length) { "Scratch storage limit exceeded" }
        val offset = length
        if (bytes.size != 0) chunks[offset] = bytes
        length += bytes.size
        offset
    }

    override suspend fun read(offset: Long, length: Int): BinaryBytes = synchronized(this) {
        check(!closed)
        require(offset >= 0 && length >= 0 && offset <= this.length && length <= this.length - offset)
        val result = ByteArray(length)
        var copied = 0
        while (copied < length) {
            val entry = checkNotNull(chunks.floorEntry(offset + copied))
            val start = (offset + copied - entry.key).toInt()
            val count = minOf(length - copied, entry.value.size - start)
            check(count > 0)
            entry.value.copyInto(result, copied, start, start + count)
            copied += count
        }
        BinaryBytes.owned(result)
    }

    override suspend fun close() = synchronized(this) { closed = true; chunks.clear() }
}
