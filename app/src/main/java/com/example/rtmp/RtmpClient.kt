package com.example.rtmp

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

class RtmpClient {

    companion object {
        private const val RTMP_VERSION = 3
        private const val HANDSHAKE_SIZE = 1536
        private const val DEFAULT_OUT_CHUNK_SIZE = 4096

        private const val CSID_PROTOCOL = 2
        private const val CSID_COMMAND = 3
        private const val CSID_AUDIO = 4
        private const val CSID_VIDEO = 6

        private const val MSG_SET_CHUNK_SIZE = 1
        private const val MSG_ABORT = 2
        private const val MSG_ACK = 3
        private const val MSG_USER_CONTROL = 4
        private const val MSG_WINDOW_ACK_SIZE = 5
        private const val MSG_SET_PEER_BANDWIDTH = 6
        private const val MSG_AUDIO = 8
        private const val MSG_VIDEO = 9
        private const val MSG_DATA_AMF0 = 18
        private const val MSG_COMMAND_AMF0 = 20
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: BufferedOutputStream? = null
    private val writeLock = Any()

    private var inChunkSize = 128
    private var outChunkSize = DEFAULT_OUT_CHUNK_SIZE
    private var streamId = 1

    private val nextTxId = AtomicInteger(1)
    private val pendingTx = ConcurrentHashMap<Int, Pair<CountDownLatch, Array<List<AmfValue>?>>>()
    @Volatile
    private var publishLatch: CountDownLatch? = null
    @Volatile
    private var publishResultError: String? = null

    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null

    val totalBytesSent = AtomicLong(0L)

    private data class IncomingChunkState(
        var timestamp: Int = 0,
        var timestampDelta: Int = 0,
        var msgLength: Int = 0,
        var msgTypeId: Int = 0,
        var msgStreamId: Int = 0,
        var buffer: ByteArray = ByteArray(0),
        var bytesRead: Int = 0
    )

    fun connectAndPublish(
        parsedUrl: ParsedRtmpUrl,
        streamKey: String,
        timeoutMs: Int = 12000
    ) {
        val key = streamKey.trim()
        if (key.isEmpty()) {
            throw IllegalArgumentException("Stream Key cannot be empty.")
        }

        close()

        val rawSocket: Socket = if (parsedUrl.isTls) {
            val baseSocket = Socket()
            baseSocket.connect(InetSocketAddress(parsedUrl.host, parsedUrl.port), timeoutMs)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = factory.createSocket(
                baseSocket,
                parsedUrl.host,
                parsedUrl.port,
                true
            ) as SSLSocket
            val params = sslSocket.sslParameters
            params.serverNames = listOf(SNIHostName(parsedUrl.host))
            sslSocket.sslParameters = params
            sslSocket.soTimeout = 15000
            sslSocket.startHandshake()
            sslSocket
        } else {
            Socket().apply {
                connect(InetSocketAddress(parsedUrl.host, parsedUrl.port), timeoutMs)
                soTimeout = 15000
            }
        }

        rawSocket.tcpNoDelay = true
        rawSocket.sendBufferSize = 256 * 1024
        socket = rawSocket

        val din = DataInputStream(BufferedInputStream(rawSocket.getInputStream(), 65536))
        val bout = BufferedOutputStream(rawSocket.getOutputStream(), 65536)
        input = din
        output = bout

        performHandshake(din, bout)
        // Clear read timeout once connected; reader thread handles incoming control packets
        rawSocket.soTimeout = 0

        running.set(true)
        startReaderThread(din)

        sendSetChunkSize(DEFAULT_OUT_CHUNK_SIZE)
        sendWindowAckSize(2_500_000)

        // 1. connect
        val connectProps = AmfValue.Obj(
            linkedMapOf(
                "app" to AmfValue.Str(parsedUrl.app),
                "type" to AmfValue.Str("nonprivate"),
                "flashVer" to AmfValue.Str("FMLE/3.0 (compatible; FMSc/1.0)"),
                "tcUrl" to AmfValue.Str(parsedUrl.tcUrl),
                "fpad" to AmfValue.Bool(false),
                "capabilities" to AmfValue.Number(15.0),
                "audioCodecs" to AmfValue.Number(3191.0),
                "videoCodecs" to AmfValue.Number(252.0),
                "videoFunction" to AmfValue.Number(1.0),
                "objectEncoding" to AmfValue.Number(0.0)
            )
        )
        val connectRes = sendCommandAndAwait(
            cmd = "connect",
            streamId = 0,
            args = arrayOf(connectProps)
        )
        verifyCommandSuccess("connect", connectRes)

        // 2. releaseStream & FCPublish (best-effort for Twitch/YouTube/FMS servers)
        sendCommandFireAndForget("releaseStream", 0, AmfValue.Null, AmfValue.Str(key))
        sendCommandFireAndForget("FCPublish", 0, AmfValue.Null, AmfValue.Str(key))

        // 3. createStream
        val createRes = sendCommandAndAwait(
            cmd = "createStream",
            streamId = 0,
            args = arrayOf(AmfValue.Null)
        )
        val createdId = createRes.getOrNull(3) as? AmfValue.Number
        streamId = createdId?.value?.toInt()?.takeIf { it > 0 } ?: 1

        // 4. publish
        publishResultError = null
        val pubLatch = CountDownLatch(1)
        publishLatch = pubLatch
        sendCommandFireAndForget("publish", streamId, AmfValue.Null, AmfValue.Str(key), AmfValue.Str("live"))

        val published = pubLatch.await(10, TimeUnit.SECONDS)
        if (!published) {
            throw IOException("Timed out waiting for RTMP server publish confirmation.")
        }
        publishResultError?.let { err ->
            throw IOException("RTMP publish rejected: $err")
        }
    }

    fun sendMetadata(
        width: Int,
        height: Int,
        videoBitrateKbps: Int,
        fps: Int,
        hasAudio: Boolean,
        audioBitrateKbps: Int,
        sampleRateHz: Int
    ) {
        val props = linkedMapOf<String, AmfValue>(
            "width" to AmfValue.Number(width.toDouble()),
            "height" to AmfValue.Number(height.toDouble()),
            "videodatarate" to AmfValue.Number(videoBitrateKbps.toDouble()),
            "framerate" to AmfValue.Number(fps.toDouble()),
            "videocodecid" to AmfValue.Number(7.0), // AVC
            "encoder" to AmfValue.Str("Makwana_Hitu.07 RTMP Studio")
        )
        if (hasAudio) {
            props["audiodatarate"] = AmfValue.Number(audioBitrateKbps.toDouble())
            props["audiosamplerate"] = AmfValue.Number(sampleRateHz.toDouble())
            props["audiosamplesize"] = AmfValue.Number(16.0)
            props["stereo"] = AmfValue.Bool(true)
            props["audiocodecid"] = AmfValue.Number(10.0) // AAC
        }

        val payload = Amf0.encodeValues(
            AmfValue.Str("@setDataFrame"),
            AmfValue.Str("onMetaData"),
            AmfValue.EcmaArray(props)
        )
        sendRtmpMessage(
            csid = CSID_COMMAND,
            timestampMs = 0L,
            msgTypeId = MSG_DATA_AMF0,
            msgStreamId = streamId,
            payload = payload
        )
    }

    fun sendVideoPacket(flvVideoTag: ByteArray, timestampMs: Long) {
        sendRtmpMessage(
            csid = CSID_VIDEO,
            timestampMs = timestampMs,
            msgTypeId = MSG_VIDEO,
            msgStreamId = streamId,
            payload = flvVideoTag
        )
    }

    fun sendAudioPacket(flvAudioTag: ByteArray, timestampMs: Long) {
        sendRtmpMessage(
            csid = CSID_AUDIO,
            timestampMs = timestampMs,
            msgTypeId = MSG_AUDIO,
            msgStreamId = streamId,
            payload = flvAudioTag
        )
    }

    private fun performHandshake(din: DataInputStream, bout: BufferedOutputStream) {
        val c1 = ByteArray(HANDSHAKE_SIZE)
        Random.nextBytes(c1)
        // Timestamp = 0, Zero = 0
        for (i in 0 until 8) c1[i] = 0

        bout.write(RTMP_VERSION)
        bout.write(c1)
        bout.flush()

        val s0 = din.readUnsignedByte()
        if (s0 != RTMP_VERSION) {
            throw IOException("Unsupported RTMP server version: $s0")
        }
        val s1 = ByteArray(HANDSHAKE_SIZE)
        din.readFully(s1)
        val s2 = ByteArray(HANDSHAKE_SIZE)
        din.readFully(s2)

        // Send C2 (echo S1)
        bout.write(s1)
        bout.flush()
    }

    private fun startReaderThread(din: DataInputStream) {
        val t = Thread({
            val chunks = HashMap<Int, IncomingChunkState>()
            try {
                while (running.get()) {
                    val b0 = din.readUnsignedByte()
                    val fmt = (b0 ushr 6) and 0x03
                    var csid = b0 and 0x3F
                    if (csid == 0) {
                        csid = din.readUnsignedByte() + 64
                    } else if (csid == 1) {
                        val b1 = din.readUnsignedByte()
                        val b2 = din.readUnsignedByte()
                        csid = (b2 shl 8) + b1 + 64
                    }

                    val state = chunks.getOrPut(csid) { IncomingChunkState() }
                    var rawTs = 0
                    when (fmt) {
                        0 -> {
                            rawTs = readUint24(din)
                            state.msgLength = readUint24(din)
                            state.msgTypeId = din.readUnsignedByte()
                            state.msgStreamId = readInt32LE(din)
                            state.timestamp = rawTs
                        }
                        1 -> {
                            rawTs = readUint24(din)
                            state.msgLength = readUint24(din)
                            state.msgTypeId = din.readUnsignedByte()
                            state.timestampDelta = rawTs
                            state.timestamp += rawTs
                        }
                        2 -> {
                            rawTs = readUint24(din)
                            state.timestampDelta = rawTs
                            state.timestamp += rawTs
                        }
                        3 -> {
                            // Uses previous header
                        }
                    }

                    if (fmt != 3 && rawTs == 0xFFFFFF) {
                        val extTs = din.readInt()
                        if (fmt == 0) state.timestamp = extTs else state.timestamp += extTs
                    }

                    if (state.bytesRead == 0) {
                        state.buffer = ByteArray(state.msgLength)
                    }
                    val remaining = state.msgLength - state.bytesRead
                    val toRead = remaining.coerceAtMost(inChunkSize)
                    if (toRead > 0) {
                        din.readFully(state.buffer, state.bytesRead, toRead)
                        state.bytesRead += toRead
                    }

                    if (state.bytesRead >= state.msgLength) {
                        val fullPayload = state.buffer
                        val msgType = state.msgTypeId
                        state.bytesRead = 0
                        state.buffer = ByteArray(0)
                        handleIncomingMessage(msgType, fullPayload)
                    }
                }
            } catch (_: EOFException) {
            } catch (_: Throwable) {
            } finally {
                running.set(false)
                // Release any waiting latches
                publishLatch?.countDown()
                pendingTx.values.forEach { it.first.countDown() }
            }
        }, "RtmpReaderThread")
        t.isDaemon = true
        t.start()
        readerThread = t
    }

    private fun handleIncomingMessage(msgType: Int, payload: ByteArray) {
        when (msgType) {
            MSG_SET_CHUNK_SIZE -> {
                if (payload.size >= 4) {
                    inChunkSize = ((payload[0].toInt() and 0x7F) shl 24) or
                        ((payload[1].toInt() and 0xFF) shl 16) or
                        ((payload[2].toInt() and 0xFF) shl 8) or
                        (payload[3].toInt() and 0xFF)
                }
            }
            MSG_USER_CONTROL -> {
                if (payload.size >= 6) {
                    val eventType = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                    if (eventType == 6) { // PingRequest -> respond with PingResponse (7)
                        val resp = payload.copyOf()
                        resp[0] = 0x00
                        resp[1] = 0x07
                        runCatching {
                            sendRtmpMessage(CSID_PROTOCOL, 0L, MSG_USER_CONTROL, 0, resp)
                        }
                    }
                }
            }
            MSG_COMMAND_AMF0 -> {
                val values = runCatching { Amf0.decodeAll(payload) }.getOrNull() ?: return
                val cmdName = (values.getOrNull(0) as? AmfValue.Str)?.value ?: return
                val txId = (values.getOrNull(1) as? AmfValue.Number)?.value?.toInt() ?: 0

                if (cmdName == "_result" || cmdName == "_error") {
                    val waiter = pendingTx.remove(txId)
                    if (waiter != null) {
                        waiter.second[0] = values
                        waiter.first.countDown()
                    }
                } else if (cmdName == "onStatus") {
                    val info = values.getOrNull(3) as? AmfValue.Obj
                    val code = (info?.props?.get("code") as? AmfValue.Str)?.value ?: ""
                    val desc = (info?.props?.get("description") as? AmfValue.Str)?.value ?: code
                    if (code.contains("Publish.Start", ignoreCase = true)) {
                        publishResultError = null
                        publishLatch?.countDown()
                    } else if (code.contains("Failed", ignoreCase = true) ||
                        code.contains("BadName", ignoreCase = true) ||
                        code.contains("Rejected", ignoreCase = true)
                    ) {
                        publishResultError = desc.ifBlank { code }
                        publishLatch?.countDown()
                    }
                }
            }
        }
    }

    private fun verifyCommandSuccess(cmd: String, values: List<AmfValue>) {
        if (values.isEmpty()) throw IOException("No response from server for $cmd")
        val cmdName = (values.getOrNull(0) as? AmfValue.Str)?.value
        if (cmdName == "_error") {
            val info = values.getOrNull(3) as? AmfValue.Obj
            val desc = (info?.props?.get("description") as? AmfValue.Str)?.value
                ?: (info?.props?.get("code") as? AmfValue.Str)?.value
                ?: "RTMP $cmd rejected by server"
            throw IOException(desc)
        }
    }

    private fun sendCommandAndAwait(
        cmd: String,
        streamId: Int,
        vararg args: AmfValue
    ): List<AmfValue> {
        val txId = nextTxId.getAndIncrement()
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<List<AmfValue>>(1)
        pendingTx[txId] = latch to holder

        val payload = Amf0.encodeCommand(cmd, txId.toDouble(), *args)
        sendRtmpMessage(CSID_COMMAND, 0L, MSG_COMMAND_AMF0, streamId, payload)

        if (!latch.await(10, TimeUnit.SECONDS)) {
            pendingTx.remove(txId)
            throw IOException("RTMP command '$cmd' timed out.")
        }
        return holder[0] ?: throw IOException("RTMP connection closed during '$cmd'.")
    }

    private fun sendCommandFireAndForget(
        cmd: String,
        streamId: Int,
        vararg args: AmfValue
    ) {
        val txId = nextTxId.getAndIncrement()
        val payload = Amf0.encodeCommand(cmd, txId.toDouble(), *args)
        sendRtmpMessage(CSID_COMMAND, 0L, MSG_COMMAND_AMF0, streamId, payload)
    }

    private fun sendSetChunkSize(size: Int) {
        outChunkSize = size
        val data = byteArrayOf(
            ((size ushr 24) and 0x7F).toByte(),
            ((size ushr 16) and 0xFF).toByte(),
            ((size ushr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte()
        )
        sendRtmpMessage(CSID_PROTOCOL, 0L, MSG_SET_CHUNK_SIZE, 0, data)
    }

    private fun sendWindowAckSize(size: Int) {
        val data = byteArrayOf(
            ((size ushr 24) and 0xFF).toByte(),
            ((size ushr 16) and 0xFF).toByte(),
            ((size ushr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte()
        )
        sendRtmpMessage(CSID_PROTOCOL, 0L, MSG_WINDOW_ACK_SIZE, 0, data)
    }

    private fun sendRtmpMessage(
        csid: Int,
        timestampMs: Long,
        msgTypeId: Int,
        msgStreamId: Int,
        payload: ByteArray
    ) {
        val out = output ?: throw IOException("RTMP socket not connected")
        val ts = timestampMs.coerceAtLeast(0L)
        val hasExtendedTs = ts >= 0xFFFFFFL
        val headerTs = if (hasExtendedTs) 0xFFFFFF else ts.toInt()
        val msgLen = payload.size

        synchronized(writeLock) {
            var offset = 0
            var firstChunk = true
            while (offset < msgLen || (msgLen == 0 && firstChunk)) {
                val chunkBytes = (msgLen - offset).coerceAtMost(outChunkSize)
                if (firstChunk) {
                    // fmt = 0 (Type 0 full header, 11 bytes + 1 basic header byte)
                    out.write(csid and 0x3F)
                    out.write((headerTs ushr 16) and 0xFF)
                    out.write((headerTs ushr 8) and 0xFF)
                    out.write(headerTs and 0xFF)
                    out.write((msgLen ushr 16) and 0xFF)
                    out.write((msgLen ushr 8) and 0xFF)
                    out.write(msgLen and 0xFF)
                    out.write(msgTypeId and 0xFF)
                    // Message Stream ID is Little Endian
                    out.write(msgStreamId and 0xFF)
                    out.write((msgStreamId ushr 8) and 0xFF)
                    out.write((msgStreamId ushr 16) and 0xFF)
                    out.write((msgStreamId ushr 24) and 0xFF)
                    if (hasExtendedTs) {
                        val ext = ts.toInt()
                        out.write((ext ushr 24) and 0xFF)
                        out.write((ext ushr 16) and 0xFF)
                        out.write((ext ushr 8) and 0xFF)
                        out.write(ext and 0xFF)
                    }
                    firstChunk = false
                } else {
                    // fmt = 3 (Type 3 continuation header)
                    out.write(0xC0 or (csid and 0x3F))
                    if (hasExtendedTs) {
                        val ext = ts.toInt()
                        out.write((ext ushr 24) and 0xFF)
                        out.write((ext ushr 16) and 0xFF)
                        out.write((ext ushr 8) and 0xFF)
                        out.write(ext and 0xFF)
                    }
                }
                if (chunkBytes > 0) {
                    out.write(payload, offset, chunkBytes)
                    offset += chunkBytes
                } else {
                    break
                }
            }
            out.flush()
            totalBytesSent.addAndGet(msgLen.toLong() + 12L)
        }
    }

    private fun readUint24(din: DataInputStream): Int {
        val b1 = din.readUnsignedByte()
        val b2 = din.readUnsignedByte()
        val b3 = din.readUnsignedByte()
        return (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readInt32LE(din: DataInputStream): Int {
        val b1 = din.readUnsignedByte()
        val b2 = din.readUnsignedByte()
        val b3 = din.readUnsignedByte()
        val b4 = din.readUnsignedByte()
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    fun close() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        readerThread?.interrupt()
        readerThread = null
    }
}
