package com.miband.camremote.camera

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PtpReader(private val data: ByteArray, var position: Int = 0) {
    val remaining: Int get() = data.size - position

    fun u8(): Int {
        require(position < data.size) { "PTP payload truncated" }
        return data[position++].toInt() and 0xFF
    }

    fun i8(): Int = u8().toByte().toInt()

    fun u16(): Int {
        ensure(2)
        val v = (data[position].toInt() and 0xFF) or ((data[position + 1].toInt() and 0xFF) shl 8)
        position += 2
        return v
    }

    fun i16(): Int = u16().toShort().toInt()

    fun u32(): Long {
        ensure(4)
        var v = 0L
        for (i in 0 until 4) v = v or ((data[position + i].toLong() and 0xFF) shl (8 * i))
        position += 4
        return v
    }

    fun i32(): Int = u32().toInt()

    fun u64(): Long {
        ensure(8)
        var v = 0L
        for (i in 0 until 8) v = v or ((data[position + i].toLong() and 0xFF) shl (8 * i))
        position += 8
        return v
    }

    fun bytes(count: Int): ByteArray {
        ensure(count)
        val out = data.copyOfRange(position, position + count)
        position += count
        return out
    }

    fun ptpString(): String {
        if (remaining <= 0) return ""
        val chars = u8()
        if (chars == 0) return ""
        val raw = bytes(chars * 2)
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < raw.size) {
            val code = (raw[i].toInt() and 0xFF) or ((raw[i + 1].toInt() and 0xFF) shl 8)
            if (code != 0) sb.append(code.toChar())
            i += 2
        }
        return sb.toString()
    }

    fun u16Array(): IntArray {
        val count = u32().toInt()
        require(count in 0..4096) { "Invalid PTP array count $count" }
        return IntArray(count) { u16() }
    }

    fun value(dataType: Int): PtpValue {
        return when (dataType) {
            Ptp.DATA_TYPE_INT8, Ptp.DATA_TYPE_UINT8, Ptp.DATA_TYPE_INT16,
            Ptp.DATA_TYPE_UINT16, Ptp.DATA_TYPE_INT32, Ptp.DATA_TYPE_UINT32,
            Ptp.DATA_TYPE_INT64, Ptp.DATA_TYPE_UINT64 -> {
                val size = dataTypeSize(dataType)
                val raw = bytes(size)
                val number = when (dataType) {
                    Ptp.DATA_TYPE_INT8 -> raw[0].toLong()
                    Ptp.DATA_TYPE_UINT8 -> (raw[0].toLong() and 0xFF)
                    Ptp.DATA_TYPE_INT16 -> Le.i16(raw).toLong()
                    Ptp.DATA_TYPE_UINT16 -> Le.u16(raw).toLong()
                    Ptp.DATA_TYPE_INT32 -> Le.i32(raw).toLong()
                    Ptp.DATA_TYPE_UINT32 -> Le.u32(raw)
                    else -> {
                        var v = 0L
                        for (i in raw.indices) v = v or ((raw[i].toLong() and 0xFF) shl (8 * i))
                        v
                    }
                }
                PtpValue(dataType, raw, number)
            }
            Ptp.DATA_TYPE_STRING -> {
                val s = ptpString()
                PtpValue(dataType, s.toByteArray(Charsets.UTF_16LE), 0, s)
            }
            else -> PtpValue(dataType, ByteArray(0), 0)
        }
    }

    private fun dataTypeSize(dataType: Int): Int = when (dataType) {
        Ptp.DATA_TYPE_INT8, Ptp.DATA_TYPE_UINT8 -> 1
        Ptp.DATA_TYPE_INT16, Ptp.DATA_TYPE_UINT16 -> 2
        Ptp.DATA_TYPE_INT32, Ptp.DATA_TYPE_UINT32 -> 4
        Ptp.DATA_TYPE_INT64, Ptp.DATA_TYPE_UINT64 -> 8
        else -> 0
    }

    private fun ensure(n: Int) {
        require(remaining >= n) { "PTP payload truncated: need $n, have $remaining" }
    }
}

data class PtpValue(
    val dataType: Int,
    val raw: ByteArray,
    val number: Long,
    val string: String? = null,
) {
    fun text(): String = string ?: number.toString()
}

class PtpWriter {
    private val out = ByteArrayOutputStream()

    fun u8(v: Int) = apply { out.write(v and 0xFF) }
    fun u16(v: Int) = apply { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
    fun u32(v: Long) = apply {
        for (i in 0 until 4) out.write(((v ushr (8 * i)) and 0xFF).toInt())
    }
    fun bytes(v: ByteArray) = apply { out.write(v) }
    fun ptpString(v: String) = apply {
        if (v.isEmpty()) {
            u8(0)
        } else {
            val chars = v.length + 1
            require(chars <= 255)
            u8(chars)
            for (c in v) {
                u16(c.code)
            }
            u16(0)
        }
    }
    fun toByteArray(): ByteArray = out.toByteArray()
}

data class PtpDeviceInfo(
    val standardVersion: Int,
    val vendorExtensionId: Long,
    val vendorExtensionVersion: Int,
    val vendorExtensionDescription: String,
    val operationsSupported: Set<Int>,
    val eventsSupported: Set<Int>,
    val propertiesSupported: Set<Int>,
    val manufacturer: String,
    val modelName: String,
    val deviceVersion: String,
    val serialNumber: String,
) {
    val displayName: String
        get() = listOf(manufacturer, modelName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "相机" }
}

object PtpParser {
    fun deviceInfo(data: ByteArray): PtpDeviceInfo {
        val r = PtpReader(data)
        val standardVersion = r.u16()
        val vendorId = r.u32()
        val vendorVersion = r.u16()
        val vendorDesc = r.ptpString()
        r.u16() // functional mode
        val operations = r.u16Array().toSet()
        val events = r.u16Array().toSet()
        val properties = r.u16Array().toSet()
        r.u16Array() // capture formats
        r.u16Array() // image formats
        val manufacturer = r.ptpString()
        val model = r.ptpString()
        val deviceVersion = r.ptpString()
        val serial = r.ptpString()
        return PtpDeviceInfo(
            standardVersion = standardVersion,
            vendorExtensionId = vendorId,
            vendorExtensionVersion = vendorVersion,
            vendorExtensionDescription = vendorDesc,
            operationsSupported = operations,
            eventsSupported = events,
            propertiesSupported = properties,
            manufacturer = manufacturer,
            modelName = model,
            deviceVersion = deviceVersion,
            serialNumber = serial,
        )
    }

    data class PropDesc(
        val dataType: Int,
        val getSet: Int,
        val defaultRaw: ByteArray,
        val currentRaw: ByteArray,
        val current: PtpValue,
        val formFlag: Int,
        val enumValues: List<PtpValue> = emptyList(),
        val rangeMin: PtpValue? = null,
        val rangeMax: PtpValue? = null,
        val rangeStep: PtpValue? = null,
    ) {
        // PTP DevicePropDesc GetSet is 0 = read-only, 1 = read/write.
        // (It is not a bit mask; treating bit 1 as "write" made every Nikon
        // property look read-only, even in M mode.)
        val readable: Boolean get() = true
        val writable: Boolean get() = getSet != 0
    }

    fun propertyDescription(data: ByteArray): PropDesc {
        val r = PtpReader(data)
        val dataType = r.u16()
        val getSet = r.u8()
        val default = r.value(dataType)
        val current = r.value(dataType)
        val form = r.u8()
        val enums = ArrayList<PtpValue>()
        var min: PtpValue? = null
        var max: PtpValue? = null
        var step: PtpValue? = null
        when (form) {
            Ptp.FORM_ENUM -> {
                val count = r.u16()
                repeat(count) { enums += r.value(dataType) }
            }
            Ptp.FORM_RANGE -> {
                min = r.value(dataType)
                max = r.value(dataType)
                step = r.value(dataType)
            }
        }
        return PropDesc(
            dataType = dataType,
            getSet = getSet,
            defaultRaw = default.raw,
            currentRaw = current.raw,
            current = current,
            formFlag = form,
            enumValues = enums,
            rangeMin = min,
            rangeMax = max,
            rangeStep = step,
        )
    }

    fun u32Array(data: ByteArray): IntArray {
        val r = PtpReader(data)
        val count = r.u32().toInt()
        require(count in 0..100000) { "Invalid PTP object count $count" }
        return IntArray(count) { r.u32().toInt() }
    }

    fun objectInfo(data: ByteArray): PtpObjectInfo {
        val r = PtpReader(data)
        val storageId = r.u32().toInt()
        val format = r.u16()
        val protection = r.u16()
        val size = r.u32()
        val thumbFormat = r.u16()
        val thumbSize = r.u32()
        val thumbWidth = r.u32()
        val thumbHeight = r.u32()
        val imageWidth = r.u32()
        val imageHeight = r.u32()
        val imageDepth = r.u32()
        val parent = r.u32().toInt()
        val associationType = r.u16()
        val associationDesc = r.u32()
        val sequence = r.u32()
        val filename = r.ptpString()
        val created = r.ptpString()
        val modified = r.ptpString()
        val keywords = r.ptpString()
        return PtpObjectInfo(
            storageId, format, protection, size, thumbFormat, thumbSize,
            thumbWidth.toInt(), thumbHeight.toInt(), imageWidth.toInt(), imageHeight.toInt(),
            imageDepth.toInt(), parent, associationType, associationDesc.toInt(), sequence.toInt(),
            filename, created, modified, keywords
        )
    }

    fun containerHeader(data: ByteArray): PtpContainerHeader {
        val r = PtpReader(data)
        val length = r.u32().toInt()
        val type = r.u16()
        val code = r.u16()
        val transaction = r.u32().toInt()
        return PtpContainerHeader(length, type, code, transaction)
    }

    fun findJpeg(data: ByteArray): ByteArray? {
        var start = -1
        for (i in 0 until data.size - 2) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() && data[i + 2] == 0xFF.toByte()) {
                start = i
                break
            }
        }
        if (start < 0) return null
        var end = -1
        for (i in data.size - 2 downTo start + 2) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()) {
                end = i + 2
                break
            }
        }
        return if (end > start) data.copyOfRange(start, end) else data.copyOfRange(start, data.size)
    }
}

data class PtpContainerHeader(
    val length: Int,
    val type: Int,
    val code: Int,
    val transactionId: Int,
)

data class PtpObjectInfo(
    val storageId: Int,
    val objectFormat: Int,
    val protectionStatus: Int,
    val objectCompressedSize: Long,
    val thumbFormat: Int,
    val thumbCompressedSize: Long,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageBitDepth: Int,
    val parentObject: Int,
    val associationType: Int,
    val associationDescription: Int,
    val sequenceNumber: Int,
    val filename: String,
    val captureDate: String,
    val modificationDate: String,
    val keywords: String,
) {
    val isVideo: Boolean
        get() = objectFormat == 0x300D || objectFormat == 0xB982 || filename.lowercase().endsWith(".mov") ||
            filename.lowercase().endsWith(".mp4")
}

object PtpHex {
    fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun decode(hex: String): ByteArray {
        if (hex.isEmpty()) return ByteArray(0)
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}

object Le {
    fun u16(bytes: ByteArray): Int {
        if (bytes.size < 2) return 0
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }
    fun u32(bytes: ByteArray): Long {
        if (bytes.size < 4) return 0
        var v = 0L
        for (i in 0 until 4) v = v or ((bytes[i].toLong() and 0xFF) shl (8 * i))
        return v
    }
    fun i16(bytes: ByteArray): Int = u16(bytes).toShort().toInt()
    fun i32(bytes: ByteArray): Int = u32(bytes).toInt()
    fun u32Bytes(value: Long): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
    fun i16Bytes(value: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
}
