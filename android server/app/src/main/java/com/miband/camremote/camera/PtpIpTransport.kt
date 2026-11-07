package com.miband.camremote.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * PTP/IP (PIMA 15740) transport used by Nikon Wi-Fi and a number of Canon
 * bodies. The camera exposes a command channel and an event channel on port
 * 15740. All integers on the wire are little endian.
 */
class PtpIpTransport(
    private val host: String,
    private val port: Int = 15740,
    private val friendlyName: String = "MiBand Camera",
) : PtpTransport {
    override val description: String = "PTP/IP · $host:$port"

    private val lock = Mutex()
    private var commandSocket: Socket? = null
    private var eventSocket: Socket? = null
    private var commandIn: InputStream? = null
    private var commandOut: OutputStream? = null
    private var nextTransaction = 1
    private var closed = false
    @Volatile private var eventThread: Thread? = null

    private val guid: ByteArray by lazy {
        val uuid = UUID.randomUUID()
        val bb = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        bb.array()
    }

    override suspend fun open() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) throw IllegalStateException("PTP/IP transport closed")
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), 9000)
            socket.soTimeout = 15_000
            commandSocket = socket
            commandIn = BufferedInputStream(socket.getInputStream())
            commandOut = BufferedOutputStream(socket.getOutputStream())

            sendPacket(1, buildInitCommandRequest())
            val ack = readPacket(15_000)
            if (ack.type == 0x0002) {
                val r = PtpReader(ack.payload)
                val connectionNumber = r.u32().toInt()
                r.bytes(16)
                val responderName = r.ptpString()
                val version = r.u32()
                if ((version ushr 16).toInt() != 1) {
                    throw IllegalStateException("相机 PTP/IP 协议版本不兼容：0x%08x".format(version))
                }
                openEventChannel(connectionNumber, responderName)
            } else {
                throw IllegalStateException("相机拒绝 PTP/IP 连接请求")
            }
        }
    }

    private fun buildInitCommandRequest(): ByteArray {
        val writer = PtpWriter()
            .bytes(guid)
            .ptpString(friendlyName)
            .u32(0x00010000)
        return writer.toByteArray()
    }

    private fun openEventChannel(connectionNumber: Int, responderName: String) {
        try {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 0
            eventSocket = socket
            val out = BufferedOutputStream(socket.getOutputStream())
            sendPacketTo(out, 3, PtpWriter().u32(connectionNumber.toLong()).toByteArray())
            val input = BufferedInputStream(socket.getInputStream())
            val ack = readPacketFrom(input, 5000)
            if (ack.type != 0x0004) throw IllegalStateException("事件通道握手失败")
            eventThread = Thread {
                runCatching {
                    while (!closed && !socket.isClosed) {
                        val p = readPacketFrom(input, 0)
                        if (p.type == 12) {
                            runCatching {
                                sendPacketTo(out, 13, p.payload)
                            }
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "ptpip-events"
                start()
            }
        } catch (_: Exception) {
            // The command channel remains usable on bodies that do not expose
            // an event channel; live view polls explicitly anyway.
            runCatching { eventSocket?.close() }
            eventSocket = null
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { eventSocket?.close() }
        runCatching { commandSocket?.close() }
        eventThread = null
    }

    override suspend fun transact(
        code: Int,
        params: IntArray,
        dataOut: ByteArray?,
        transactionId: Int?,
        timeoutMs: Int,
    ): PtpResult = withContext(Dispatchers.IO) {
        lock.withLock {
            val out = commandOut ?: throw IllegalStateException("PTP/IP 未连接")
            val input = commandIn ?: throw IllegalStateException("PTP/IP 未连接")
            val tx = transactionId ?: nextTransaction++
            val phase = if (dataOut != null) 2 else 1
            val payload = PtpWriter()
                .u32(phase.toLong())
                .u16(code)
                .u32(tx.toLong())
                .apply { params.forEach { u32(it.toLong()) } }
                .toByteArray()
            sendPacket(6, payload)
            if (dataOut != null) {
                sendPacket(9, PtpWriter().u32(tx.toLong()).u32(dataOut.size.toLong()).u32(0).toByteArray())
                sendPacket(10, PtpWriter().u32(tx.toLong()).bytes(dataOut).toByteArray())
                sendPacket(11, PtpWriter().u32(tx.toLong()).bytes(ByteArray(0)).toByteArray())
            }

            var response: PtpPacket? = null
            val data = ByteArrayOutputStream()
            var guard = 0
            while (guard++ < 128) {
                val packet = readPacketFrom(input, timeoutMs)
                when (packet.type) {
                    9 -> { /* start data: transaction + total length */ }
                    10, 11 -> {
                        val r = PtpReader(packet.payload)
                        val pt = r.u32().toInt()
                        if (dataOut == null || pt == tx) {
                            // Data payload after the 4-byte transaction id.
                            if (packet.payload.size > 4) data.write(packet.payload, 4, packet.payload.size - 4)
                        }
                        if (packet.type == 11) {
                            val resp = readPacketFrom(input, timeoutMs)
                            if (resp.type == 7) { response = resp; break }
                        }
                    }
                    7 -> { response = packet; break }
                    0 -> { /* event channel probe noise */ }
                    else -> Unit
                }
            }
            val r = response ?: throw IllegalStateException("相机未返回 PTP/IP 响应")
            val rr = PtpReader(r.payload)
            val rc = rr.u16()
            val rtx = rr.u32().toInt()
            val responseParams = ArrayList<Int>()
            while (rr.remaining >= 4) responseParams += rr.u32().toInt()
            if (rtx != tx) throw IllegalStateException("PTP/IP 事务号不匹配：$rtx != $tx")
            PtpResult(rc, responseParams.toIntArray(), if (data.size() > 0) data.toByteArray() else null)
        }
    }

    private fun sendPacket(type: Int, payload: ByteArray) {
        val out = commandOut ?: throw IllegalStateException("PTP/IP 未连接")
        sendPacketTo(out, type, payload)
    }

    private fun sendPacketTo(out: OutputStream, type: Int, payload: ByteArray) {
        val bb = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(8 + payload.size)
        bb.putInt(type)
        bb.put(payload)
        out.write(bb.array())
        out.flush()
    }

    private fun readPacket(timeoutMs: Int): PtpPacket =
        readPacketFrom(commandIn ?: throw IllegalStateException("PTP/IP 未连接"), timeoutMs)

    private fun readPacketFrom(input: InputStream, timeoutMs: Int): PtpPacket {
        if (input === commandIn && commandSocket != null) commandSocket?.soTimeout = timeoutMs
        val header = readExact(input, 8)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val length = bb.int
        val type = bb.int
        if (length < 8 || length > 64 * 1024 * 1024) throw IllegalStateException("非法 PTP/IP 包长度 $length")
        val payload = readExact(input, length - 8)
        return PtpPacket(type, payload)
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(out, offset, count - offset)
            if (n < 0) throw EOFException("PTP/IP 连接被相机关闭")
            offset += n
        }
        return out
    }

    private data class PtpPacket(val type: Int, val payload: ByteArray)
}
