package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.BinaryBytes
import com.anatdx.yukifb.hex.backend.BinaryDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.EOFException

internal class BinaryPageCache(private val pageSize: Int, private val cacheBytes: Int) {
    private data class Key(val source: BinaryDataSource, val offset: Long)
    private val pages = LinkedHashMap<Key, BinaryBytes>(16, 0.75f, true)
    private val mutex = Mutex()

    suspend fun read(source: BinaryDataSource, offset: Long, length: Int): BinaryBytes {
        require(offset >= 0 && length >= 0 && offset <= source.size && length <= source.size - offset)
        val result = ByteArray(length)
        var copied = 0
        while (copied < length) {
            val current = offset + copied
            val pageOffset = current / pageSize * pageSize
            val key = Key(source, pageOffset)
            val cached = mutex.withLock { pages[key] }
            val page = cached ?: source.read(pageOffset, minOf(pageSize.toLong(), source.size - pageOffset).toInt()).also {
                if (it.size.toLong() != minOf(pageSize.toLong(), source.size - pageOffset)) throw EOFException("Incomplete binary page")
                mutex.withLock {
                    pages[key] = it
                    while (pages.size > cacheBytes / pageSize) pages.remove(pages.keys.first())
                }
            }
            val start = (current - pageOffset).toInt()
            val count = minOf(length - copied, page.size - start)
            page.copyInto(result, copied, start, start + count)
            copied += count
        }
        return BinaryBytes.owned(result)
    }

    suspend fun clear() = mutex.withLock { pages.clear() }
}
