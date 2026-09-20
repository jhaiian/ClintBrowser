package com.jhaiian.clint.mediacapture

import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import com.jhaiian.clint.downloads.ClintDownloadManager
import com.jhaiian.clint.mediacapture.download.StreamRequestHeaders
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.Request

object MediaFileMetadataProbe {

    data class Info(val width: Int, val height: Int, val durationSeconds: Double?)

    private const val BLOCK_BYTES = 256 * 1024
    private const val MAX_CACHED_BLOCKS = 32
    private const val MAX_TOTAL_FETCH_BYTES = 24L * 1024 * 1024
    private const val PROBE_DEADLINE_NANOS = 10_000_000_000L
    private const val READ_CHUNK_BYTES = 16 * 1024

    private val httpClient by lazy {
        ClintDownloadManager.httpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    }

    fun probe(media: DetectedMedia, pageUrl: String): Info? {
        val source = HttpRangeDataSource(
            media.url,
            pageUrl,
            media.requestHeaders,
            media.sizeBytes ?: -1L,
            System.nanoTime() + PROBE_DEADLINE_NANOS
        )
        val extractor = MediaExtractor()
        var result: Info? = null
        try {
            extractor.setDataSource(source)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                if (!format.containsKey(MediaFormat.KEY_WIDTH) || !format.containsKey(MediaFormat.KEY_HEIGHT)) continue
                val width = format.getInteger(MediaFormat.KEY_WIDTH)
                val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                if (width <= 0 || height <= 0) continue
                val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                val swapped = rotation == 90 || rotation == 270
                val durationSeconds = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }?.let { it / 1_000_000.0 }
                } else null
                result = Info(
                    if (swapped) height else width,
                    if (swapped) width else height,
                    durationSeconds
                )
                break
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
            runCatching { source.close() }
        }
        return result
    }

    private class HttpRangeDataSource(
        private val url: String,
        private val pageUrl: String,
        private val headers: Map<String, String>,
        knownSize: Long,
        private val deadlineNanos: Long
    ) : MediaDataSource() {

        private var totalSize = knownSize
        private var rangeSupported = true
        private var fetchedBytes = 0L
        private val blocks = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean =
                this.size > MAX_CACHED_BLOCKS
        }

        @Synchronized
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0L) return -1
            if (size <= 0) return 0
            if (totalSize >= 0L && position >= totalSize) return -1
            var copied = 0
            var current = position
            while (copied < size) {
                val blockIndex = current / BLOCK_BYTES
                val block = blockAt(blockIndex) ?: break
                val inBlock = (current - blockIndex * BLOCK_BYTES).toInt()
                if (inBlock >= block.size) break
                val count = minOf(size - copied, block.size - inBlock)
                System.arraycopy(block, inBlock, buffer, offset + copied, count)
                copied += count
                current += count
                if (block.size < BLOCK_BYTES) break
            }
            return if (copied > 0) copied else -1
        }

        @Synchronized
        override fun getSize(): Long = totalSize

        @Synchronized
        override fun close() {
            blocks.clear()
        }

        private fun blockAt(index: Long): ByteArray? {
            blocks[index]?.let { return it }
            if (!rangeSupported && index > 0L) return null
            if (fetchedBytes >= MAX_TOTAL_FETCH_BYTES) return null
            if (System.nanoTime() > deadlineNanos) return null
            val start = index * BLOCK_BYTES
            if (totalSize >= 0L && start >= totalSize) return null
            val end = if (totalSize >= 0L) minOf(start + BLOCK_BYTES - 1, totalSize - 1) else start + BLOCK_BYTES - 1
            val data = fetchRange(start, end) ?: return null
            fetchedBytes += data.size
            blocks[index] = data
            return data
        }

        private fun fetchRange(start: Long, end: Long): ByteArray? {
            val builder = Request.Builder().url(url)
            StreamRequestHeaders.apply(builder, url, pageUrl, "", "", headers)
            builder.header("Accept-Encoding", "identity")
            builder.header("Range", "bytes=$start-$end")
            val want = (end - start + 1).toInt()
            val data = try {
                httpClient.newCall(builder.build()).execute().use { resp ->
                    when (resp.code) {
                        206 -> {
                            resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { totalSize = it }
                            readFully(resp.body.byteStream(), want)
                        }
                        200 -> {
                            rangeSupported = false
                            resp.header("Content-Length")?.toLongOrNull()?.let { totalSize = it }
                            if (start == 0L) readFully(resp.body.byteStream(), want) else null
                        }
                        else -> null
                    }
                }
            } catch (_: Exception) {
                null
            }
            return data?.takeIf { it.isNotEmpty() }
        }

        private fun readFully(stream: InputStream, want: Int): ByteArray {
            val out = ByteArrayOutputStream(want)
            val chunk = ByteArray(READ_CHUNK_BYTES)
            while (out.size() < want) {
                val read = stream.read(chunk, 0, minOf(chunk.size, want - out.size()))
                if (read <= 0) break
                out.write(chunk, 0, read)
            }
            return out.toByteArray()
        }
    }
}
