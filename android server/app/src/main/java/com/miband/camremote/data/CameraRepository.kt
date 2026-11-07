package com.miband.camremote.data

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.miband.camremote.camera.CameraConnection
import com.miband.camremote.camera.CameraEndpoint
import com.miband.camremote.camera.CameraIdentity
import com.miband.camremote.camera.CameraMedia
import com.miband.camremote.camera.CameraProperty
import com.miband.camremote.camera.CameraSnapshot
import com.miband.camremote.camera.CanonCcapiConnection
import com.miband.camremote.camera.CaptureInfo
import com.miband.camremote.camera.IntervalState
import com.miband.camremote.camera.LinkState
import com.miband.camremote.camera.PropertyOption
import com.miband.camremote.camera.PtpCameraConnection
import com.miband.camremote.camera.PtpIpTransport
import com.miband.camremote.camera.PtpTransport
import com.miband.camremote.camera.SonyApiConnection
import com.miband.camremote.camera.UsbPtpTransport
import com.miband.camremote.camera.VirtualCameraConnection
import com.miband.camremote.service.CameraKeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UsbDeviceItem(
    val device: UsbDevice,
    val label: String,
    val vendorName: String,
    val hasPermission: Boolean,
    val ptpCapable: Boolean,
)

data class BandLinkState(
    val connected: Boolean = false,
    val authorized: Boolean = false,
    val nodeName: String? = null,
    val message: String = "未连接手环",
)

sealed interface AppEvent {
    data class Message(val text: String, val isError: Boolean = false) : AppEvent
    data class Flash(val preview: ByteArray?) : AppEvent
}

class CameraRepository(
    private val context: Context,
    val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val opMutex = Mutex()
    private val _snapshot = MutableStateFlow(CameraSnapshot())
    val snapshot: StateFlow<CameraSnapshot> = _snapshot.asStateFlow()
    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()
    private val _liveFrame = MutableSharedFlow<ByteArray>(extraBufferCapacity = 2)
    val liveFrame: SharedFlow<ByteArray> = _liveFrame.asSharedFlow()
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    private val _band = MutableStateFlow(BandLinkState())
    val band: StateFlow<BandLinkState> = _band.asStateFlow()
    private val _interval = MutableStateFlow(IntervalState())
    val interval: StateFlow<IntervalState> = _interval.asStateFlow()

    private var connection: CameraConnection? = null
    private var liveJob: Job? = null
    private var intervalJob: Job? = null
    private var lastEndpoint: CameraEndpoint? = null

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _logs.update { (listOf(LogLine(message = message, level = level)) + it).take(80) }
    }

    fun updateBand(state: BandLinkState) {
        _band.value = state
    }

    fun scanUsb(): List<UsbDeviceItem> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values
            .filter { d ->
                val vendor = d.vendorId
                vendor in UsbIds.cameraVendors || (0 until d.interfaceCount).any { i ->
                    d.getInterface(i).interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_STILL_IMAGE
                }
            }
            .sortedBy { it.productName ?: it.deviceName }
            .map { d ->
                UsbDeviceItem(
                    device = d,
                    label = d.productName?.takeIf { it.isNotBlank() } ?: "USB 相机",
                    vendorName = UsbIds.vendorName(d.vendorId),
                    hasPermission = manager.hasPermission(d),
                    ptpCapable = (0 until d.interfaceCount).any { i ->
                        d.getInterface(i).interfaceClass.let { it == 6 || it == 0xFF || it == 0 }
                    },
                )
            }
    }

    fun hasUsbPermission(device: UsbDevice): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.hasPermission(device)
    }

    suspend fun connectUsb(device: UsbDevice) = opMutex.withLock {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!manager.hasPermission(device)) throw IllegalStateException("尚未授予 USB 权限")
        log("正在连接 USB：${device.productName ?: device.deviceName}")
        var last: Throwable? = null
        for (attempt in 1..2) {
            _snapshot.update {
                it.copy(
                    linkState = LinkState.CONNECTING,
                    statusMessage = if (attempt == 1) "正在打开 USB 相机…" else "USB 无响应，正在重试…",
                    error = null,
                )
            }
            try {
                val usbConnection = manager.openDevice(device) ?: throw IllegalStateException("无法打开 USB 设备")
                val transport = UsbPtpTransport(device, usbConnection)
                connectInternal(PtpCameraConnection(CameraEndpoint.Usb(device.deviceId), transport)) {
                    settings.lastMode = "USB"
                }
                return@withLock
            } catch (t: Throwable) {
                last = t
                log("USB 连接第 $attempt 次失败：${t.message}", LogLevel.WARN)
                if (attempt == 1) delay(650)
            }
        }
        throw last ?: IllegalStateException("USB 连接失败")
    }

    suspend fun connectPtpIp(host: String, port: Int = 15740) = opMutex.withLock {
        connectInternal(PtpIpConnectionFactory.create(host, port)) {
            settings.lastHost = host
            settings.lastPort = port
            settings.lastMode = "PTPIP"
        }
    }

    suspend fun connectCanon(host: String, port: Int = 8080) = opMutex.withLock {
        connectInternal(CanonCcapiConnection(CameraEndpoint.CanonCcapi(host, port), host, port)) {
            settings.lastHost = host
            settings.lastPort = port
            settings.lastMode = "CANON"
        }
    }

    suspend fun connectSony(host: String, port: Int = 10000) = opMutex.withLock {
        connectInternal(SonyApiConnection(CameraEndpoint.SonyApi(host, port), host, port)) {
            settings.lastHost = host
            settings.lastPort = port
            settings.lastMode = "SONY"
        }
    }

    suspend fun connectVirtual() = opMutex.withLock {
        connectInternal(VirtualCameraConnection()) {
            settings.lastMode = "DEMO"
        }
    }

    private suspend fun connectInternal(newConnection: CameraConnection, persist: () -> Unit) {
        stopLiveInternal()
        val old = connection
        connection = null
        runCatching { old?.disconnect() }
        _snapshot.update {
            it.copy(
                linkState = LinkState.CONNECTING,
                identity = null,
                statusMessage = "正在连接 ${newConnection.transportLabel}…",
                properties = emptyList(),
                media = emptyList(),
                error = null,
                liveViewActive = false,
                lastCapture = null,
            )
        }
        try {
            val identity = newConnection.connect()
            val fresh = runCatching { newConnection.refresh() }.getOrNull()
            connection = newConnection
            lastEndpoint = newConnection.endpoint
            val base = fresh ?: CameraSnapshot(
                linkState = LinkState.CONNECTED,
                identity = identity,
                endpointLabel = newConnection.transportLabel,
                statusMessage = "已连接 · ${identity.title}",
            )
            _snapshot.value = base.copy(
                linkState = LinkState.CONNECTED,
                identity = identity,
                endpointLabel = newConnection.transportLabel,
                statusMessage = "已连接 · ${identity.title}",
                error = null,
            )
            persist()
            log("已连接 ${identity.title}（${newConnection.transportLabel}）", LogLevel.SUCCESS)
            _events.emit(AppEvent.Message("已连接 ${identity.title}"))
            CameraKeepAliveService.start(context)
        } catch (t: Throwable) {
            runCatching { newConnection.disconnect() }
            connection = null
            val message = t.message ?: "连接失败"
            _snapshot.update {
                it.copy(
                    linkState = LinkState.ERROR,
                    statusMessage = message,
                    error = message,
                    properties = emptyList(),
                )
            }
            log("连接失败：$message", LogLevel.ERROR)
            _events.emit(AppEvent.Message(message, isError = true))
            throw t
        }
    }

    suspend fun disconnect() = opMutex.withLock {
        stopInterval()
        stopLiveInternal()
        val c = connection
        connection = null
        runCatching { c?.disconnect() }
        _snapshot.value = CameraSnapshot()
        log("已断开相机")
        CameraKeepAliveService.stop(context)
    }

    suspend fun refreshProperties() {
        val c = connection ?: return
        runCatching {
            val s = c.refresh()
            _snapshot.value = s.copy(
                identity = s.identity ?: _snapshot.value.identity,
                endpointLabel = c.transportLabel,
                linkState = LinkState.CONNECTED,
            )
        }.onFailure { log("刷新参数失败：${it.message}", LogLevel.WARN) }
    }

    suspend fun capture() {
        val c = connection ?: throw IllegalStateException("请先连接相机")
        if (_snapshot.value.busy) return
        _snapshot.update { it.copy(busy = true, error = null) }
        try {
            log("发送快门指令")
            val capture = c.capture(withAutofocus = true)
            log("相机返回：${capture.message}；本次新增 ${capture.mediaList.size} 个文件")
            val preview = capture.preview
            var media = capture.media
            if (media != null && settings.autoDownloadCapture) {
                // Download the real card object, never save the small preview
                // thumbnail as if it were the captured photo.
                val full = runCatching { c.download(media) }.getOrNull()
                if (full != null && full.isNotEmpty()) {
                    val path = LocalGallery.save(context, media.name, full)
                    if (path != null) {
                        media = media.copy(localPath = path, thumbnail = preview)
                    }
                }
            }
            val capturedList = capture.mediaList.ifEmpty { listOfNotNull(media) }
                .map { added -> if (added.id == media?.id) media ?: added else added }
            val ids = capturedList.map { it.id }.toSet()
            val mergedMedia = (capturedList + _snapshot.value.media.filter { it.id !in ids }).take(40)
            _snapshot.update {
                it.copy(
                    busy = false,
                    captureCount = it.captureCount + 1,
                    statusMessage = capture.message,
                    lastCapture = CaptureInfo(media, preview, capture.message),
                    media = mergedMedia,
                )
            }
            _events.emit(AppEvent.Flash(preview))
            _events.emit(AppEvent.Message(capture.message))
            log(capture.message, LogLevel.SUCCESS)
        } catch (t: Throwable) {
            val message = t.message ?: "拍摄失败"
            _snapshot.update { it.copy(busy = false, statusMessage = message, error = message) }
            log(message, LogLevel.ERROR)
            _events.emit(AppEvent.Message(message, isError = true))
        }
    }

    fun startInterval(count: Int, intervalSeconds: Int): Boolean {
        if (connection == null) return false
        if (_interval.value.active) return false
        val total = count.coerceIn(2, 999)
        val seconds = intervalSeconds.coerceIn(1, 3600)
        _interval.value = IntervalState(active = true, total = total, taken = 0, intervalSeconds = seconds)
        log("开始间隔拍摄：$total 张 / 每 $seconds 秒", LogLevel.SUCCESS)
        intervalJob?.cancel()
        intervalJob = scope.launch {
            for (i in 1..total) {
                if (!_interval.value.active) break
                capture()
                _interval.update { it.copy(taken = i) }
                if (i < total) delay(seconds * 1000L)
            }
            _interval.update { it.copy(active = false) }
            log("间隔拍摄结束：${_interval.value.taken} 张")
        }
        return true
    }

    fun stopInterval() {
        intervalJob?.cancel()
        intervalJob = null
        if (_interval.value.active) {
            _interval.update { it.copy(active = false) }
            log("已停止间隔拍摄")
        }
    }

    suspend fun focus() {
        val c = connection ?: return
        try {
            _snapshot.update { it.copy(busy = true) }
            val ok = c.focus()
            _snapshot.update {
                it.copy(busy = false, statusMessage = if (ok) "已对焦" else "相机不支持独立对焦")
            }
            log(if (ok) "已完成自动对焦" else "相机不支持独立对焦", if (ok) LogLevel.SUCCESS else LogLevel.WARN)
        } catch (t: Throwable) {
            _snapshot.update { it.copy(busy = false, statusMessage = t.message ?: "对焦失败") }
            log("对焦失败：${t.message}", LogLevel.ERROR)
        }
    }

    suspend fun startLiveView() {
        val c = connection ?: return
        if (_snapshot.value.liveViewActive) return
        val ok = runCatching { c.startLiveView() }.getOrDefault(false)
        if (!ok) {
            _snapshot.update { it.copy(liveViewActive = false, liveViewAvailable = false, statusMessage = "此相机暂不支持实时取景") }
            log("实时取景不可用", LogLevel.WARN)
            return
        }
        _snapshot.update { it.copy(liveViewActive = true, liveViewAvailable = true, statusMessage = "实时取景已开启") }
        log("实时取景已开启", LogLevel.SUCCESS)
        liveJob?.cancel()
        liveJob = scope.launch {
            var failures = 0
            var frames = 0
            while (isActive) {
                val con = connection ?: break
                val bytes = runCatching { con.liveViewFrame() }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    failures = 0
                    frames++
                    _liveFrame.emit(bytes)
                } else {
                    failures++
                    if (failures > 12) {
                        _snapshot.update { it.copy(liveViewActive = false) }
                        break
                    }
                }
                delay(if (frames > 0) 90 else 400)
            }
        }
    }

    suspend fun stopLiveView() {
        stopLiveInternal()
    }

    private suspend fun stopLiveInternal() {
        liveJob?.cancel()
        liveJob = null
        val c = connection
        if (c != null && _snapshot.value.liveViewActive) runCatching { c.stopLiveView() }
        _snapshot.update { it.copy(liveViewActive = false) }
    }

    suspend fun setProperty(property: CameraProperty, option: PropertyOption) {
        val c = connection ?: return
        try {
            c.updateProperty(property, option)
            _snapshot.update { snap ->
                snap.copy(properties = snap.properties.map { if (it.key == property.key) it.copy(valueLabel = option.label, rawHex = option.rawHex) else it })
            }
            log("${property.label} → ${option.label}", LogLevel.SUCCESS)
        } catch (t: Throwable) {
            val message = t.message ?: "设置失败"
            _snapshot.update { it.copy(statusMessage = message, error = message) }
            log(message, LogLevel.ERROR)
            _events.emit(AppEvent.Message(message, isError = true))
        }
    }

    suspend fun setPropertyByLabel(key: String, label: String) {
        val prop = _snapshot.value.property(key) ?: throw IllegalStateException("未知参数 $key")
        val opt = prop.options.firstOrNull { it.label == label || it.label.equals(label, true) }
            ?: throw IllegalStateException("不支持的值 $label")
        setProperty(prop, opt)
    }

    suspend fun adjustExposure(key: String, direction: Int) {
        val prop = _snapshot.value.property(key) ?: return
        if (prop.options.isEmpty()) return
        val index = prop.options.indexOfFirst { it.label == prop.valueLabel }.let { if (it < 0) 0 else it }
        val target = (index + direction).coerceIn(0, prop.options.lastIndex)
        setProperty(prop, prop.options[target])
    }

    suspend fun setRecording(recording: Boolean) {
        val c = connection ?: return
        val ok = runCatching { c.setRecording(recording) }.getOrDefault(false)
        _snapshot.update { it.copy(recording = recording && ok, statusMessage = if (ok) "录像状态已更新" else "相机不支持当前录像模式") }
        log(if (ok) (if (recording) "开始录像" else "停止录像") else "录像控制不可用", if (ok) LogLevel.SUCCESS else LogLevel.WARN)
    }

    suspend fun refreshMedia() {
        val c = connection ?: return
        try {
            _snapshot.update { it.copy(busy = true, statusMessage = "正在读取相机相册…") }
            val list = c.listMedia(40)
            val withThumbs = list.map { m ->
                if (m.thumbnail != null) m else {
                    val bytes = runCatching { c.loadThumbnail(m) }.getOrNull()
                    m.copy(thumbnail = bytes)
                }
            }
            _snapshot.update { it.copy(busy = false, media = withThumbs, statusMessage = "相机相册 · ${withThumbs.size} 项") }
            log("读取相机相册 ${withThumbs.size} 项")
        } catch (t: Throwable) {
            _snapshot.update { it.copy(busy = false, statusMessage = t.message ?: "读取相册失败") }
            log("读取相册失败：${t.message}", LogLevel.WARN)
        }
    }

    suspend fun download(media: CameraMedia) {
        val c = connection ?: return
        try {
            _snapshot.update { it.copy(busy = true, statusMessage = "正在下载 ${media.name}…") }
            val bytes = c.download(media)
            val path = LocalGallery.save(context, media.name, bytes)
            _snapshot.update { snap ->
                snap.copy(
                    busy = false,
                    media = snap.media.map { if (it.id == media.id) it.copy(localPath = path, thumbnail = it.thumbnail ?: bytes) else it },
                    statusMessage = "已保存 ${media.name}",
                )
            }
            _events.emit(AppEvent.Message("已保存到本地相册"))
            log("已下载 ${media.name} (${bytes.size / 1024} KB)", LogLevel.SUCCESS)
        } catch (t: Throwable) {
            _snapshot.update { it.copy(busy = false, statusMessage = t.message ?: "下载失败") }
            log("下载失败：${t.message}", LogLevel.ERROR)
        }
    }

    suspend fun loadThumbnail(media: CameraMedia): ByteArray? {
        val c = connection ?: return null
        return runCatching {
            val bytes = c.loadThumbnail(media)
            if (bytes != null) {
                _snapshot.update { snap ->
                    snap.copy(media = snap.media.map { if (it.id == media.id) it.copy(thumbnail = bytes) else it })
                }
            }
            bytes
        }.getOrNull()
    }

    fun hasActiveConnection(): Boolean = connection != null

    fun bandStatusJson(): String {
        val s = _snapshot.value
        val identity = s.identity
        val obj = JSONObject()
        obj.put("v", 1)
        obj.put("type", "state")
        obj.put("connected", s.isConnected)
        obj.put("brand", identity?.brand?.displayName ?: "")
        obj.put("model", identity?.model ?: "")
        obj.put("battery", s.batteryPercent ?: -1)
        obj.put("shots", s.availableShots ?: -1)
        obj.put("busy", s.busy)
        obj.put("live", s.liveViewActive)
        obj.put("recording", s.recording)
        obj.put("count", s.captureCount)
        obj.put("message", s.statusMessage)
        obj.put("iso", s.property("iso")?.valueLabel ?: "—")
        obj.put("shutter", s.property("shutter")?.valueLabel ?: "—")
        obj.put("aperture", s.property("aperture")?.valueLabel ?: "—")
        obj.put("ev", s.property("ev")?.valueLabel ?: "—")
        obj.put("band", true)
        return obj.toString()
    }

    fun propertyOptionsJson(): String {
        val s = _snapshot.value
        val arr = JSONArray()
        for (p in s.properties.filter { it.primary }) {
            val o = JSONObject()
            o.put("key", p.key)
            o.put("label", p.label)
            o.put("value", p.valueLabel)
            val options = JSONArray()
            p.options.forEach { options.put(it.label) }
            o.put("options", options)
            arr.put(o)
        }
        return JSONObject().put("v", 1).put("type", "options").put("items", arr).toString()
    }

    suspend fun handleBandCommand(text: String): String {
        val parsed = runCatching { JSONObject(text) }.getOrElse {
            return JSONObject().put("v", 1).put("type", "ack").put("ok", false)
                .put("message", "JSON 格式错误").toString()
        }
        val json = normalizeBandPayload(parsed)
        return when (json.optString("cmd")) {
            "status" -> bandStatusJson()
            "options" -> propertyOptionsJson()
            "capture" -> {
                scope.launch { runCatching { capture() } }
                JSONObject().put("v", 1).put("type", "ack").put("ok", true).put("message", "快门已发送").toString()
            }
            "focus" -> {
                scope.launch { runCatching { focus() } }
                JSONObject().put("v", 1).put("type", "ack").put("ok", true).put("message", "对焦中").toString()
            }
            "live" -> {
                if (json.optBoolean("value", true)) scope.launch { runCatching { startLiveView() } }
                else scope.launch { runCatching { stopLiveView() } }
                bandStatusJson()
            }
            "record" -> {
                val target = json.optBoolean("value", false)
                scope.launch { runCatching { setRecording(target) } }
                JSONObject().put("v", 1).put("type", "ack").put("ok", true).put("message", "录像指令已发送").toString()
            }
            "set" -> {
                val key = json.optString("key")
                val value = json.optString("value")
                val result = runCatching { setPropertyByLabel(key, value) }
                if (result.isSuccess) bandStatusJson()
                else JSONObject().put("v", 1).put("type", "ack").put("ok", false)
                    .put("message", result.exceptionOrNull()?.message ?: "设置失败").toString()
            }
            "adjust" -> {
                val key = json.optString("key")
                val dir = json.optInt("dir", 1)
                runCatching { adjustExposure(key, dir) }
                bandStatusJson()
            }
            else -> JSONObject().put("v", 1).put("type", "ack").put("ok", false).put("message", "未知指令").toString()
        }
    }

    private fun normalizeBandPayload(input: JSONObject): JSONObject {
        var current = input
        repeat(3) {
            val data = current.opt("data")
            current = when (data) {
                is JSONObject -> data
                is String -> runCatching { JSONObject(data) }.getOrNull() ?: return current
                else -> return current
            }
        }
        return current
    }

    fun resetBand() {
        _band.value = BandLinkState(message = "未连接手环")
    }

    fun shutdown() {
        scope.cancel()
        runCatching { connection?.closeQuietly() }
    }
}

object UsbIds {
    val cameraVendors = setOf(0x04B0, 0x04A9, 0x054C, 0x04CB, 0x04DA, 0x07B4, 0x1A98, 0x0945)
    fun vendorName(id: Int): String = when (id) {
        0x04B0 -> "Nikon"
        0x04A9 -> "Canon"
        0x054C -> "Sony"
        0x04CB -> "Fujifilm"
        0x04DA -> "Panasonic"
        0x07B4 -> "OM System"
        0x1A98 -> "Leica"
        0x0945 -> "Sigma"
        else -> "USB"
    }
}

private object PtpIpConnectionFactory {
    fun create(host: String, port: Int): CameraConnection =
        PtpCameraConnection(CameraEndpoint.PtpIp(host, port), PtpIpTransport(host, port))
}
