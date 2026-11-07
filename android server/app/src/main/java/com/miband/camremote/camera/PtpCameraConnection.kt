package com.miband.camremote.camera

import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs

class PtpCameraConnection(
    override val endpoint: CameraEndpoint,
    private val transport: PtpTransport,
) : CameraConnection {
    override val transportLabel: String get() = transport.description

    private var info: PtpDeviceInfo? = null
    private var identity: CameraIdentity? = null
    private var brand: CameraBrand = CameraBrand.GENERIC
    private var specs: List<PropertySpec> = emptyList()
    private var liveViewRunning = false
    private var recording = false
    private var lastProps: List<CameraProperty> = emptyList()
    private var opCount = 0
    private val thumbnailCache = HashMap<Int, ByteArray>()
    private var lastSnapshot = CameraSnapshot()

    override suspend fun connect(): CameraIdentity {
        transport.open()
        // qDslrDashboard's USB implementation uses the transport's normal
        // transaction sequence (GetDeviceInfo=1, OpenSession=2), while PTP/IP
        // starts with transaction 0 for both handshake commands. Keep that
        // distinction; fixed transaction 0 on USB breaks several bodies.
        val tx0 = if (endpoint is CameraEndpoint.PtpIp) 0 else null
        val deviceInfoResult = transport.transact(
            Ptp.OP_GET_DEVICE_INFO,
            transactionId = tx0,
            timeoutMs = 15_000,
        )
        deviceInfoResult.requireOk()
        val di = PtpParser.deviceInfo(deviceInfoResult.data ?: ByteArray(0))
        info = di
        brand = CameraBrand.from(di.manufacturer, di.modelName, di.vendorExtensionId)
        specs = PropertyCatalog.forBrand(brand)
        identity = CameraIdentity(
            brand = brand,
            manufacturer = di.manufacturer,
            model = di.modelName,
            firmware = di.deviceVersion,
            serial = di.serialNumber,
            vendor = di.vendorExtensionDescription,
        )

        val open = transport.transact(
            Ptp.OP_OPEN_SESSION,
            intArrayOf(1),
            transactionId = tx0,
            timeoutMs = 10_000,
        )
        if (!open.isOk && open.responseCode != Ptp.RC_SESSION_ALREADY_OPEN) open.requireOk()

        if (brand == CameraBrand.CANON) {
            runCatching {
                if (di.operationsSupported.contains(Ptp.OP_CANON_EOS_SET_REMOTE_MODE)) {
                    transport.transact(Ptp.OP_CANON_EOS_SET_REMOTE_MODE, intArrayOf(1))
                }
                if (di.operationsSupported.contains(Ptp.OP_CANON_EOS_SET_EVENT_MODE)) {
                    transport.transact(Ptp.OP_CANON_EOS_SET_EVENT_MODE, intArrayOf(1))
                }
            }
        }
        if (brand == CameraBrand.NIKON) {
            // Put the body into PC-control mode. On a D800E this is what makes
            // the shutter release behave like the body's own release (AF then
            // full press) instead of only starting the AF motor. Some Nikon
            // bodies do not advertise this command even though they support it.
            runCatching {
                val r = transport.transact(Ptp.OP_NIKON_CHANGE_CAMERA_MODE, intArrayOf(1), timeoutMs = 8000)
                if (!r.isOk && r.responseCode != 0xA003) r.requireOk()
            }
        }
        val loaded = runCatching { readAllProperties() }.getOrDefault(emptyList())
        lastProps = loaded
        lastSnapshot = buildSnapshot(LinkState.CONNECTED, "已连接 · ${di.displayName}")
        return identity!!
    }

    override suspend fun disconnect() {
        runCatching { if (liveViewRunning) stopLiveView() }
        if (brand == CameraBrand.NIKON) {
            runCatching { transport.transact(Ptp.OP_NIKON_CHANGE_CAMERA_MODE, intArrayOf(0), timeoutMs = 3000) }
        }
        runCatching { transport.transact(Ptp.OP_CLOSE_SESSION, timeoutMs = 3000) }
        runCatching { transport.close() }
    }

    override fun closeQuietly() {
        runCatching { transport.close() }
    }

    override suspend fun refresh(): CameraSnapshot {
        if (info == null) return lastSnapshot
        val loaded = readAllProperties()
        if (loaded.isNotEmpty()) lastProps = mergeProperties(lastProps, loaded)
        lastSnapshot = buildSnapshot(LinkState.CONNECTED, "已连接 · ${info?.displayName ?: "相机"}")
        return lastSnapshot
    }

    private fun mergeProperties(old: List<CameraProperty>, new: List<CameraProperty>): List<CameraProperty> {
        val map = old.associateBy { it.key }.toMutableMap()
        new.forEach { map[it.key] = it }
        return specs.mapNotNull { map[it.key] }
    }

    private suspend fun readAllProperties(): List<CameraProperty> {
        val out = ArrayList<CameraProperty>()
        for (spec in specs) {
            runCatching {
                val prop = if (brand == CameraBrand.CANON) readCanonProperty(spec) else readStandardProperty(spec)
                if (prop != null) out += prop
            }
        }
        return out
    }

    private suspend fun readCanonProperty(spec: PropertySpec): CameraProperty? {
        val res = transport.transact(Ptp.OP_CANON_EOS_GET_PROP_VALUE, intArrayOf(spec.code), timeoutMs = 8000)
        if (!res.isOk) return null
        val data = res.data ?: return null
        val raw = pad4(data)
        val value = PropertyFormat.display(spec, raw, Ptp.DATA_TYPE_UINT32)
        return CameraProperty(
            key = spec.key,
            label = spec.label,
            group = spec.group,
            valueLabel = value,
            rawHex = PtpHex.encode(raw),
            options = spec.fixedOptions.orEmpty(),
            writable = spec.writable,
            primary = spec.primary,
        )
    }

    private suspend fun readStandardProperty(spec: PropertySpec): CameraProperty? {
        val desc = runCatching { getPropertyDescription(spec.code) }.getOrNull()
        if (desc != null && desc.readable) {
            val options = optionsFor(spec, desc).toMutableList()
            if (brand == CameraBrand.NIKON && spec.key == "iso" &&
                options.none { it.label.equals("AUTO", true) }
            ) {
                options.add(0, PropertyOption("AUTO（自动 ISO）", "AUTO"))
            }
            val value = if (spec.key == "shutter" && spec.code == Ptp.DPC_NIKON_EXPOSURE_TIME) {
                PropertyFormat.display(spec, desc.currentRaw, Ptp.DATA_TYPE_UINT32)
            } else {
                PropertyFormat.display(spec, desc.currentRaw, desc.dataType)
            }
            return CameraProperty(
                key = spec.key,
                label = spec.label,
                group = spec.group,
                valueLabel = value,
                rawHex = PtpHex.encode(desc.currentRaw),
                options = options,
                writable = spec.writable && desc.writable,
                primary = spec.primary,
                hint = if (desc.formFlag == Ptp.FORM_RANGE) "范围设置" else null,
            )
        }

        val valueResult = transport.transact(Ptp.OP_GET_DEVICE_PROP_VALUE, intArrayOf(spec.code), timeoutMs = 6000)
        if (!valueResult.isOk) return null
        val raw = valueResult.data?.takeIf { it.isNotEmpty() } ?: return null
        return CameraProperty(
            key = spec.key,
            label = spec.label,
            group = spec.group,
            valueLabel = PropertyFormat.display(spec, raw, spec.dataType),
            rawHex = PtpHex.encode(raw),
            options = spec.fixedOptions.orEmpty(),
            writable = spec.writable,
            primary = spec.primary,
        )
    }

    private suspend fun getPropertyDescription(code: Int): PtpParser.PropDesc {
        val res = transport.transact(Ptp.OP_GET_DEVICE_PROP_DESC, intArrayOf(code), timeoutMs = 6000)
        res.requireOk()
        return PtpParser.propertyDescription(res.data ?: throw IllegalStateException("缺少属性描述数据"))
    }

    private fun optionsFor(spec: PropertySpec, desc: PtpParser.PropDesc): List<PropertyOption> {
        if (desc.enumValues.isNotEmpty()) {
            return desc.enumValues.mapNotNull { v ->
                val raw = v.raw.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                PropertyOption(PropertyFormat.display(spec, raw, desc.dataType), PtpHex.encode(raw))
            }
        }
        if (desc.formFlag != Ptp.FORM_RANGE) return emptyList()
        val min = desc.rangeMin?.number ?: return emptyList()
        val max = desc.rangeMax?.number ?: return emptyList()
        val dataType = desc.dataType
        return when (spec.key) {
            "iso" -> listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 51200)
                .filter { it >= min && it <= max }
                .map { PropertyOption("$it", PtpHex.encode(encodeNumber(it.toLong(), dataType))) } +
                if (min >= 0xFFFF || max >= 0xFFFF) listOf(PropertyOption("AUTO", PtpHex.encode(encodeNumber(0xFFFF, dataType)))) else emptyList()
            "aperture" -> listOf(120, 140, 160, 180, 200, 220, 250, 280, 320, 350, 400, 450, 500, 560, 630, 710, 800, 900, 1000, 1100, 1300, 1400, 1600, 1800, 2000, 2200, 2500, 2900, 3200)
                .filter { it >= min && it <= max }
                .map { PropertyOption("f/${"%.1f".format(Locale.US, it / 100.0)}", PtpHex.encode(encodeNumber(it.toLong(), dataType))) }
            "ev" -> (-12..12).map { it / 3.0 }.distinct().filter { ev ->
                val rawEv = (ev * 1000).toLong()
                rawEv in min..max
            }.map { ev ->
                val label = if (ev > 0) "+${"%.1f".format(Locale.US, ev)}" else "%.1f".format(Locale.US, ev)
                PropertyOption(label, PtpHex.encode(encodeNumber((ev * 1000).toLong(), dataType)))
            }
            else -> emptyList()
        }
    }

    private fun encodeNumber(value: Long, dataType: Int): ByteArray {
        val size = when (dataType) {
            Ptp.DATA_TYPE_INT8, Ptp.DATA_TYPE_UINT8 -> 1
            Ptp.DATA_TYPE_INT16, Ptp.DATA_TYPE_UINT16 -> 2
            else -> 4
        }
        val bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        when (size) {
            1 -> bb.put((value and 0xFF).toByte())
            2 -> bb.putShort(value.toShort())
            else -> bb.putInt(value.toInt())
        }
        return bb.array()
    }

    override suspend fun updateProperty(property: CameraProperty, option: PropertyOption) {
        val spec = specs.firstOrNull { it.key == property.key } ?: throw IllegalArgumentException("未知参数")
        if (brand == CameraBrand.NIKON && spec.key == "iso" && option.rawHex == "AUTO") {
            setNikonAutoIso(true).requireOk()
            val updated = property.copy(valueLabel = "AUTO（自动 ISO）", rawHex = "AUTO")
            lastProps = mergeProperties(lastProps, listOf(updated))
            lastSnapshot = lastSnapshot.copy(properties = lastProps)
            opCount++
            return
        }
        val raw = PtpHex.decode(option.rawHex)
        val res = if (brand == CameraBrand.CANON) {
            val payload = PtpWriter()
                .u32(0x0C)
                .u32(spec.code.toLong())
                .bytes(pad4(raw))
                .toByteArray()
            transport.transact(Ptp.OP_CANON_EOS_SET_PROP_EX, dataOut = payload, timeoutMs = 10_000)
        } else {
            if (brand == CameraBrand.NIKON && spec.key == "iso") runCatching { setNikonAutoIso(false) }
            transport.transact(Ptp.OP_SET_DEVICE_PROP_VALUE, intArrayOf(spec.code), dataOut = raw, timeoutMs = 10_000)
        }
        res.requireOk()
        val updated = property.copy(
            valueLabel = option.label,
            rawHex = option.rawHex,
        )
        lastProps = mergeProperties(lastProps, listOf(updated))
        lastSnapshot = lastSnapshot.copy(properties = lastProps)
        opCount++
    }

    private suspend fun setNikonAutoIso(on: Boolean): PtpResult {
        val value = PtpWriter().u8(if (on) 1 else 0).toByteArray()
        val first = transport.transact(
            Ptp.OP_SET_DEVICE_PROP_VALUE,
            intArrayOf(Ptp.DPC_NIKON_ISOAUTO),
            value,
            timeoutMs = 8000,
        )
        if (first.isOk) return first
        return transport.transact(
            Ptp.OP_SET_DEVICE_PROP_VALUE,
            intArrayOf(Ptp.DPC_NIKON_ISO_AUTO),
            value,
            timeoutMs = 8000,
        )
    }

    override suspend fun capture(withAutofocus: Boolean): CaptureInfo {
        // Do NOT send NikonAfDrive immediately before the shutter. In AF-S
        // that leaves the body focusing, and the following capture command is
        // ignored; the user only sees the AF motor run. InitiateCapture does
        // its own AF and releases the shutter in both AF and M modes.
        val before = runCatching { allObjectHandles() }.getOrDefault(emptySet())
        // A previous session may have left RecordingMedia pointing at SDRAM.
        // Explicitly select the card for still capture.
        runCatching {
            transport.transact(
                Ptp.OP_SET_DEVICE_PROP_VALUE,
                intArrayOf(Ptp.DPC_NIKON_RECORDING_MEDIA),
                PtpWriter().u8(0).toByteArray(),
                timeoutMs = 5000,
            )
        }
        val wasLive = liveViewRunning
        if (wasLive) {
            // Some Nikon bodies reject InitiateCapture while live view owns
            // the sensor. Stop LV for the still, then restore it afterwards.
            runCatching { stopLiveView() }
            delay(350)
        }
        val ops = info?.operationsSupported.orEmpty()
        var captured = false
        var message = "已发送拍摄指令"
        if (brand == CameraBrand.CANON && ops.contains(Ptp.OP_CANON_EOS_REMOTE_RELEASE_ON)) {
            val on = transport.transact(Ptp.OP_CANON_EOS_REMOTE_RELEASE_ON, intArrayOf(3, 0), timeoutMs = 8000)
            if (!on.isOk) on.requireOk()
            delay(120)
            runCatching { transport.transact(Ptp.OP_CANON_EOS_REMOTE_RELEASE_OFF, intArrayOf(3), timeoutMs = 8000) }
            captured = true
        } else {
            // Nikon 0x9207 is the modern D800/D4 capture command:
            //   param 1 = 0xFFFFFFFE (autofocus on) / 0xFFFFFFFF (off)
            //   param 2 = 0 (card) / 1 (SDRAM)
            // It performs AF and the full shutter release, unlike sending
            // NikonAfDrive and a capture command back-to-back.
            if (brand == CameraBrand.NIKON) {
                var attempts = 0
                while (attempts < 20) {
                    val r = runCatching {
                        transport.transact(
                            Ptp.OP_NIKON_CAPTURE_REC_IN_MEDIA,
                            intArrayOf(0xFFFFFFFE.toInt(), 0),
                            timeoutMs = 15_000,
                        )
                    }.getOrNull()
                    if (r?.isOk == true) {
                        captured = true
                        break
                    }
                    if (r?.responseCode == Ptp.RC_PARAMETER_NOT_SUPPORTED) {
                        val oneArg = runCatching {
                            transport.transact(
                                Ptp.OP_NIKON_CAPTURE_REC_IN_MEDIA,
                                intArrayOf(0xFFFFFFFF.toInt()),
                                timeoutMs = 15_000,
                            )
                        }.getOrNull()
                        if (oneArg?.isOk == true) {
                            captured = true
                            break
                        }
                    }
                    if (r?.responseCode != Ptp.RC_DEVICE_BUSY && r?.responseCode != 0xA207) break
                    delay(300)
                    attempts++
                }
            }
            if (!captured) {
                // Standard PTP capture also stores to card and lets the body
                // perform its own AF.
                val standard = runCatching {
                    transport.transact(Ptp.OP_INITIATE_CAPTURE, intArrayOf(0, 0), timeoutMs = 15_000)
                }.getOrNull()
                if (standard?.isOk == true) captured = true
            }
            if (!captured) {
                // Last resort: SDRAM capture. It releases the shutter even when
                // the card is full or the body rejects the card command.
                val nikon = runCatching {
                    transport.transact(Ptp.OP_NIKON_CAPTURE, timeoutMs = 12_000)
                }.getOrNull()
                if (nikon?.isOk == true) {
                    captured = true
                    message = "已拍摄（相机缓存）"
                }
            }
        }
        if (!captured) {
            if (wasLive) runCatching { startLiveView() }
            throw IllegalStateException("相机未释放快门，请检查对焦模式/存储卡")
        }

        // Wait for the card object to appear before reading it. Never reuse an
        // old card handle here: that was why the gallery opened yesterday's photo.
        val newMedia = if (brand == CameraBrand.NIKON) {
            waitForNewObject(before, 20_000)
        } else {
            delay(700)
            emptyList()
        }
        val enriched = newMedia.map { m ->
            m.copy(thumbnail = runCatching { loadThumbnail(m) }.getOrNull())
        }
        val latest = enriched.firstOrNull { it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) }
            ?: enriched.firstOrNull()
        val preview = latest?.thumbnail
        if (wasLive) runCatching { startLiveView() }
        opCount++
        if (latest != null) message = "已拍摄 · ${latest.name}"
        else if (captured && message == "已发送拍摄指令") message = "快门已释放，但未检测到新照片（请检查存储卡/对焦）"
        return CaptureInfo(media = latest, preview = preview, message = message, mediaList = enriched)
    }

    private suspend fun allObjectHandles(): Set<Int> {
        val all = runCatching {
            transport.transact(
                Ptp.OP_GET_OBJECT_HANDLES,
                intArrayOf(-1, 0, 0),
                timeoutMs = 10_000,
            )
        }.getOrNull()
        if (all?.isOk == true) {
            val parsed = runCatching { PtpParser.u32Array(all.data ?: ByteArray(0)).toSet() }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        val out = HashSet<Int>()
        val storageRes = transport.transact(Ptp.OP_GET_STORAGE_IDS, timeoutMs = 6000)
        if (!storageRes.isOk) return out
        for (storage in PtpParser.u32Array(storageRes.data ?: return out)) {
            val res = transport.transact(
                Ptp.OP_GET_OBJECT_HANDLES,
                intArrayOf(storage, 0, 0),
                timeoutMs = 8000,
            )
            if (res.isOk) runCatching { out.addAll(PtpParser.u32Array(res.data ?: return@runCatching).toList()) }
        }
        return out
    }

    private suspend fun waitForNewObject(before: Set<Int>, timeoutMs: Long): List<CameraMedia> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val now = runCatching { allObjectHandles() }.getOrDefault(emptySet())
            val added = now.filter { it !in before }.sortedDescending()
            if (added.isNotEmpty()) {
                delay(500)
                return added.mapNotNull { runCatching { mediaForHandle(it) }.getOrNull() }
            }
            delay(350)
        }
        return emptyList()
    }

    private suspend fun mediaForHandle(handle: Int): CameraMedia? {
        val res = transport.transact(Ptp.OP_GET_OBJECT_INFO, intArrayOf(handle), timeoutMs = 6000)
        if (!res.isOk) return null
        val oi = runCatching { PtpParser.objectInfo(res.data ?: return null) }.getOrNull() ?: return null
        if (oi.objectFormat == 0x3001 || oi.filename.isBlank()) return null
        return CameraMedia(
            id = handle.toString(),
            name = oi.filename,
            date = oi.captureDate,
            sizeBytes = oi.objectCompressedSize,
            isVideo = oi.isVideo,
            thumbnail = thumbnailCache[handle],
        )
    }

    override suspend fun focus(): Boolean {
        val ops = info?.operationsSupported.orEmpty()
        return if (brand == CameraBrand.CANON && ops.contains(Ptp.OP_CANON_EOS_REMOTE_RELEASE_ON)) {
            val on = transport.transact(Ptp.OP_CANON_EOS_REMOTE_RELEASE_ON, intArrayOf(1, 0), timeoutMs = 8000)
            delay(900)
            runCatching { transport.transact(Ptp.OP_CANON_EOS_REMOTE_RELEASE_OFF, intArrayOf(1), timeoutMs = 5000) }
            on.isOk
        } else if (ops.contains(Ptp.OP_NIKON_AF_DRIVE)) {
            val r = transport.transact(Ptp.OP_NIKON_AF_DRIVE, timeoutMs = 9000)
            if (!r.isOk && r.responseCode == Ptp.RC_DEVICE_BUSY) throw IllegalStateException("相机正在对焦")
            r.isOk
        } else false
    }

    override suspend fun startLiveView(): Boolean {
        if (liveViewRunning) return true
        val ops = info?.operationsSupported.orEmpty()
        val ok = when (brand) {
            CameraBrand.CANON -> startCanonLiveView(ops)
            CameraBrand.NIKON -> startNikonLiveView(ops)
            else -> false
        }
        liveViewRunning = ok
        lastSnapshot = lastSnapshot.copy(liveViewActive = ok, liveViewAvailable = ok)
        return ok
    }

    private suspend fun startCanonLiveView(ops: Set<Int>): Boolean {
        if (!ops.contains(Ptp.OP_CANON_EOS_GET_VIEWFINDER_DATA)) return false
        runCatching { transport.transact(Ptp.OP_CANON_EOS_SET_REMOTE_MODE, intArrayOf(1)) }
        runCatching { transport.transact(Ptp.OP_CANON_EOS_SET_EVENT_MODE, intArrayOf(1)) }
        runCatching { setEosProp(Ptp.DPC_EOS_VF_OUTPUT, 3) }
        runCatching { setEosProp(Ptp.DPC_EOS_CAPTURE_DESTINATION, 4) }
        if (ops.contains(Ptp.OP_CANON_EOS_INITIATE_VIEWFINDER)) {
            runCatching { transport.transact(Ptp.OP_CANON_EOS_INITIATE_VIEWFINDER) }
        }
        return true
    }

    private suspend fun startNikonLiveView(ops: Set<Int>): Boolean {
        if (!ops.contains(Ptp.OP_NIKON_START_LIVEVIEW)) return false
        // Do not force SDRAM here. RecordingMedia=1 redirected still captures
        // away from the card, so the just-shot file never appeared in
        // GetObjectHandles. Keep the body's current destination.
        val res = transport.transact(Ptp.OP_NIKON_START_LIVEVIEW, timeoutMs = 12_000)
        return res.isOk
    }

    private suspend fun setEosProp(code: Int, value: Int) {
        val payload = PtpWriter().u32(0x0C).u32(code.toLong()).u32(value.toLong()).toByteArray()
        transport.transact(Ptp.OP_CANON_EOS_SET_PROP_EX, dataOut = payload, timeoutMs = 8000)
    }

    override suspend fun stopLiveView() {
        if (!liveViewRunning) return
        val ops = info?.operationsSupported.orEmpty()
        runCatching {
            when (brand) {
                CameraBrand.CANON -> {
                    if (ops.contains(Ptp.OP_CANON_EOS_TERMINATE_VIEWFINDER)) {
                        transport.transact(Ptp.OP_CANON_EOS_TERMINATE_VIEWFINDER)
                    }
                    setEosProp(Ptp.DPC_EOS_CAPTURE_DESTINATION, 2)
                }
                CameraBrand.NIKON -> {
                    if (ops.contains(Ptp.OP_NIKON_END_LIVEVIEW)) transport.transact(Ptp.OP_NIKON_END_LIVEVIEW)
                }
                else -> Unit
            }
        }
        liveViewRunning = false
        lastSnapshot = lastSnapshot.copy(liveViewActive = false)
    }

    override suspend fun liveViewFrame(): ByteArray? {
        if (!liveViewRunning) return null
        return when (brand) {
            CameraBrand.CANON -> {
                val r = transport.transact(Ptp.OP_CANON_EOS_GET_VIEWFINDER_DATA, intArrayOf(0x200000), timeoutMs = 9000)
                if (!r.isOk) return null
                val data = r.data ?: return null
                if (data.size <= 8) return null
                PtpParser.findJpeg(data)
            }
            CameraBrand.NIKON -> {
                val r = transport.transact(Ptp.OP_NIKON_GET_LIVEVIEW_IMAGE, timeoutMs = 9000)
                if (!r.isOk) return null
                PtpParser.findJpeg(r.data ?: return null)
            }
            else -> null
        }
    }

    override suspend fun setRecording(recording: Boolean): Boolean {
        val ops = info?.operationsSupported.orEmpty()
        val target = recording != this.recording
        if (!target) return true
        return when (brand) {
            CameraBrand.NIKON -> {
                val code = if (recording) Ptp.OP_NIKON_START_MOVIE else Ptp.OP_NIKON_END_MOVIE
                val r = transport.transact(code, timeoutMs = 8000)
                if (r.isOk) this.recording = recording
                r.isOk
            }
            else -> false
        }
    }

    override suspend fun listMedia(limit: Int): List<CameraMedia> {
        val handles = allObjectHandles()
        val result = ArrayList<CameraMedia>()
        for (handle in handles.sortedDescending().take(limit)) {
            val media = mediaForHandle(handle) ?: continue
            result += media
        }
        lastSnapshot = lastSnapshot.copy(media = result)
        return result
    }

    override suspend fun loadThumbnail(media: CameraMedia): ByteArray? {
        val handle = media.id.toIntOrNull() ?: return null
        thumbnailCache[handle]?.let { return it }
        val res = transport.transact(Ptp.OP_GET_THUMB, intArrayOf(handle), timeoutMs = 12_000)
        var bytes = if (res.isOk) res.data else null
        if (bytes == null || bytes.isEmpty()) {
            val previewLen = minOf(2_000_000L, maxOf(256_000L, media.sizeBytes.coerceAtMost(2_000_000L))).toInt()
            val partial = transport.transact(
                Ptp.OP_GET_PARTIAL_OBJECT,
                intArrayOf(handle, 0, previewLen),
                timeoutMs = 15_000,
            )
            if (partial.isOk) bytes = partial.data
        }
        // GetThumb normally returns JPEG, but some Nikon bodies return the
        // raw bytes for NEF. Extract the embedded JPEG preview for the grid.
        val jpeg = bytes?.let { PtpParser.findJpeg(it) }
        if (jpeg != null && jpeg.isNotEmpty()) {
            thumbnailCache[handle] = jpeg
            return jpeg
        }
        return null
    }

    override suspend fun download(media: CameraMedia): ByteArray {
        val handle = media.id.toIntOrNull() ?: throw IllegalArgumentException("无效对象")
        val res = transport.transact(Ptp.OP_GET_OBJECT, intArrayOf(handle), timeoutMs = 120_000)
        if (res.isOk) return res.data ?: throw IllegalStateException("相机未返回图像数据")

        // Some Nikon bodies reject one huge GetObject for a 40 MB NEF.
        // Fall back to bounded GetPartialObject chunks.
        if (media.sizeBytes <= 0) res.requireOk()
        val out = java.io.ByteArrayOutputStream()
        var offset = 0L
        val chunk = 2 * 1024 * 1024
        while (offset < media.sizeBytes) {
            val part = transport.transact(
                Ptp.OP_GET_PARTIAL_OBJECT,
                intArrayOf(handle, offset.toInt(), chunk),
                timeoutMs = 60_000,
            )
            if (!part.isOk) break
            val data = part.data ?: break
            if (data.isEmpty()) break
            out.write(data)
            offset += data.size
            if (offset >= media.sizeBytes) break
        }
        if (out.size() > 0) return out.toByteArray()
        res.requireOk()
        return res.data ?: throw IllegalStateException("相机未返回图像数据")
    }

    private fun pad4(data: ByteArray): ByteArray {
        val out = ByteArray(4)
        data.copyInto(out, 0, 0, minOf(4, data.size))
        return out
    }

    private fun buildSnapshot(state: LinkState, message: String): CameraSnapshot {
        var battery: Int? = null
        var shots: Int? = null
        for (p in lastProps) {
            if (p.key == "battery") battery = p.valueLabel.toIntOrNull()
            if (p.key == "shots") shots = p.valueLabel.toIntOrNull()
        }
        return CameraSnapshot(
            linkState = state,
            identity = identity,
            endpointLabel = transportLabel,
            statusMessage = message,
            batteryPercent = battery,
            availableShots = shots,
            properties = lastProps,
            liveViewActive = liveViewRunning,
            liveViewAvailable = liveViewRunning,
            recording = recording,
            captureCount = opCount,
            lastCapture = lastSnapshot.lastCapture,
            media = lastSnapshot.media,
        )
    }
}
