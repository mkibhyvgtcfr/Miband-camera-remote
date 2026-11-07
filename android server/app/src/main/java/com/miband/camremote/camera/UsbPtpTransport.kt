package com.miband.camremote.camera

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PTP over Android USB Host. Cameras expose a still-image interface with two
 * bulk endpoints. Some bodies re-enumerate after the first command, so the
 * caller is expected to reconnect on transport failures.
 */
class UsbPtpTransport(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
) : PtpTransport {
    override val description: String = "USB · ${device.productName ?: device.deviceName}"

    private val lock = Mutex()
    private var claimed: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var nextTransaction = 1
    private var closed = false
    // Never read a USB bulk endpoint with a short buffer. A camera may return
    // a whole 512/1024-byte packet for a PTP header; asking for only 8 bytes
    // makes Android's USB stack fail or discard the rest of the packet. Keep
    // a stream buffer and always read at least maxPacketSize bytes.
    private var rxBuffer = ByteArray(0)

    override suspend fun open() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) throw IllegalStateException("USB transport closed")
            var chosen: UsbInterface? = null
            var chosenIn: UsbEndpoint? = null
            var chosenOut: UsbEndpoint? = null
            var bestScore = -1
            var sawMassStorage = false
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) sawMassStorage = true
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                var hasInterrupt = false
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT &&
                        ep.direction == UsbConstants.USB_DIR_IN
                    ) hasInterrupt = true
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
                }
                if (inEp == null || outEp == null) continue
                val score = when (intf.interfaceClass) {
                    UsbConstants.USB_CLASS_STILL_IMAGE -> 100
                    0xFF -> 50
                    0 -> 20
                    else -> continue
                } + (if (intf.interfaceSubclass == 1) 12 else 0) +
                    (if (intf.interfaceProtocol == 1) 12 else 0) +
                    (if (hasInterrupt) 6 else 0) +
                    intf.endpointCount
                if (score > bestScore) {
                    chosen = intf
                    chosenIn = inEp
                    chosenOut = outEp
                    bestScore = score
                }
            }
            val target = chosen ?: throw IllegalStateException(
                if (sawMassStorage) {
                    "相机以 USB 存储模式连接。请在相机菜单把 USB 模式改为 PTP/MTP 后重新连接。"
                } else {
                    "未找到相机的 PTP 批量端点。请确认相机已切换到 PTP 模式，并换一根支持数据传输的 OTG 线。"
                }
            )
            // Match the behavior of the camera implementations that are known
            // to work with this class of device: claim the still-image
            // interface directly. Calling setConfiguration/setInterface here
            // resets some Nikon/Canon endpoints and causes the first PTP
            // command to time out.
            if (!connection.claimInterface(target, true)) {
                throw IllegalStateException("无法占用相机 USB 接口（可能被其他相机 App 占用）")
            }
            claimed = target
            endpointIn = chosenIn
            endpointOut = chosenOut
            clearHalt(endpointIn)
            clearHalt(endpointOut)
            Log.i(
                TAG,
                "PTP USB open: if=${target.id} class=${target.interfaceClass} " +
                    "in=0x${endpointIn?.address?.toString(16)} " +
                    "out=0x${endpointOut?.address?.toString(16)} " +
                    "maxIn=${endpointIn?.maxPacketSize} maxOut=${endpointOut?.maxPacketSize}"
            )
            Unit
        }
    }

    private fun clearHalt(ep: UsbEndpoint?) {
        if (ep == null) return
        runCatching {
            connection.controlTransfer(
                0x02, // host-to-device, standard, endpoint
                0x01, // CLEAR_FEATURE
                0,
                ep.address,
                null,
                0,
                1000,
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { claimed?.let { connection.releaseInterface(it) } }
        runCatching { connection.close() }
    }

    override suspend fun transact(
        code: Int,
        params: IntArray,
        dataOut: ByteArray?,
        transactionId: Int?,
        timeoutMs: Int,
    ): PtpResult = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) throw IllegalStateException("USB transport closed")
            val tx = transactionId ?: nextTransaction++
            val command = buildContainer(Ptp.CONTAINER_COMMAND, code, tx, params)
            writeFully(command, timeoutMs)
            if (dataOut != null) {
                writeFully(buildDataContainer(code, tx, dataOut), timeoutMs)
            }
            var response: PtpContainer? = null
            var collected: ByteArray? = null
            var guard = 0
            while (guard++ < 16) {
                val c = readContainer(timeoutMs)
                when (c.type) {
                    Ptp.CONTAINER_DATA -> {
                        val payload = c.payload ?: ByteArray(0)
                        collected = if (collected == null) payload else collected!! + payload
                    }
                    Ptp.CONTAINER_RESPONSE -> {
                        response = c
                        break
                    }
                    Ptp.CONTAINER_EVENT -> Unit
                    else -> throw IllegalStateException("未知 PTP 容器类型 ${c.type}")
                }
            }
            val r = response ?: throw IllegalStateException("相机未返回 PTP 响应")
            PtpResult(r.code, r.params, collected)
        }
    }

    private fun buildContainer(type: Int, code: Int, tx: Int, params: IntArray): ByteArray {
        val bb = ByteBuffer.allocate(12 + params.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(12 + params.size * 4)
        bb.putShort(type.toShort())
        bb.putShort(code.toShort())
        bb.putInt(tx)
        params.forEach { bb.putInt(it) }
        return bb.array()
    }

    private fun buildDataContainer(code: Int, tx: Int, data: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(12 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(12 + data.size)
        bb.putShort(Ptp.CONTAINER_DATA.toShort())
        bb.putShort(code.toShort())
        bb.putInt(tx)
        bb.put(data)
        return bb.array()
    }

    private fun writeFully(data: ByteArray, timeoutMs: Int) {
        val ep = endpointOut ?: throw IllegalStateException("USB OUT endpoint missing")
        var offset = 0
        while (offset < data.size) {
            val length = minOf(16 * 1024, data.size - offset)
            val sent = connection.bulkTransfer(ep, data, offset, length, timeoutMs)
            if (sent <= 0) throw IllegalStateException("USB 写入失败（$sent）")
            offset += sent
        }
    }

    private fun ensureBytes(count: Int, timeoutMs: Int) {
        val ep = endpointIn ?: throw IllegalStateException("USB IN endpoint missing")
        while (rxBuffer.size < count) {
            val chunk = ByteArray(maxOf(16 * 1024, ep.maxPacketSize))
            val read = connection.bulkTransfer(ep, chunk, chunk.size, timeoutMs)
            if (read <= 0) {
                throw IllegalStateException(
                    "USB 读取超时或失败（$read，IN=0x${ep.address.toString(16)}，" +
                        "OUT=0x${endpointOut?.address?.toString(16)}，" +
                        "缓冲区 ${rxBuffer.size}/$count，maxPacket=${ep.maxPacketSize}）。" +
                        "请确认相机未被其他 App 占用。"
                )
            }
            rxBuffer += chunk.copyOf(read)
            if (rxBuffer.size > 64 * 1024 * 1024) {
                throw IllegalStateException("USB PTP 数据过大")
            }
        }
    }

    private fun readContainer(timeoutMs: Int): PtpContainer {
        ensureBytes(8, timeoutMs)
        val header = rxBuffer
        val bb = ByteBuffer.wrap(header, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        val length = bb.int
        val type = bb.short.toInt() and 0xFFFF
        if (length < 8 || length > 64 * 1024 * 1024) {
            throw IllegalStateException("非法 PTP 容器长度 $length")
        }
        ensureBytes(length, timeoutMs)
        val full = rxBuffer.copyOfRange(0, length)
        rxBuffer = rxBuffer.copyOfRange(length, rxBuffer.size)
        val fbb = ByteBuffer.wrap(full).order(ByteOrder.LITTLE_ENDIAN)
        fbb.int
        fbb.short
        val code = fbb.short.toInt() and 0xFFFF
        val tx = fbb.int
        val params = ArrayList<Int>()
        while (fbb.remaining() >= 4) params += fbb.int
        return PtpContainer(type, code, tx, params.toIntArray(), if (type == Ptp.CONTAINER_DATA) full.copyOfRange(12, full.size) else null)
    }

    private data class PtpContainer(
        val type: Int,
        val code: Int,
        val tx: Int,
        val params: IntArray,
        val payload: ByteArray?,
    )

    companion object {
        private const val TAG = "MiBandUsbPtp"
    }
}


