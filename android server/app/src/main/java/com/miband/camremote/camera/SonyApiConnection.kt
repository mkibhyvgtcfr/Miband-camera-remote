package com.miband.camremote.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sony Camera Remote API (ScalarWebAPI) over Wi-Fi. The API is JSON-RPC-like
 * and split into system/camera/avContent services. It is intentionally
 * tolerant because the exact method set differs between Sony generations.
 */
class SonyApiConnection(
    override val endpoint: CameraEndpoint,
    private val host: String,
    private val port: Int,
) : CameraConnection {
    override val transportLabel: String = "Sony API · $host:$port"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val base = "http://$host:$port"
    private var identity: CameraIdentity? = null
    private var available: Set<String> = emptySet()
    private var props: List<CameraProperty> = emptyList()
    private var liveUrl: String? = null
    private var media: List<CameraMedia> = emptyList()
    private var lastSnapshot = CameraSnapshot()
    private var idCounter = 1

    private val isoOptions = listOf("AUTO", "50", "100", "200", "400", "800", "1600", "3200", "6400", "12800", "25600")
    private val shutterOptions = listOf("BULB", "30\"", "25\"", "20\"", "15\"", "13\"", "10\"", "8\"", "6\"", "5\"", "4\"", "3.2\"", "2.5\"", "2\"", "1.6\"", "1.3\"", "1\"", "1/2", "1/4", "1/8", "1/15", "1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000", "1/8000")
    private val apertureOptions = listOf("f/1.4", "f/1.8", "f/2.0", "f/2.8", "f/3.5", "f/4.0", "f/5.6", "f/6.3", "f/8.0", "f/11", "f/13", "f/16", "f/22")
    private val evOptions = listOf("-3.0", "-2.7", "-2.3", "-2.0", "-1.7", "-1.3", "-1.0", "-0.7", "-0.3", "0.0", "0.3", "0.7", "1.0", "1.3", "1.7", "2.0", "2.3", "2.7", "3.0")

    override suspend fun connect(): CameraIdentity = withContext(Dispatchers.IO) {
        val app = runCatching { call("system", "getApplicationInfo") }.getOrNull()
        val name = app?.optJSONArray("result")?.optJSONObject(0)?.optString("name")
            ?.takeIf { it.isNotBlank() } ?: "Sony Camera"
        available = runCatching<Set<String>> {
            call("system", "getAvailableApiList")?.optJSONArray("result")?.optJSONArray(0)
                ?.let { arr ->
                    val set = HashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.optString(i))
                    set
                } ?: emptySet()
        }.getOrDefault(emptySet())
        identity = CameraIdentity(
            brand = CameraBrand.SONY,
            manufacturer = "Sony",
            model = name,
            vendor = "ScalarWebAPI",
        )
        props = runCatching { readProperties() }.getOrDefault(emptyList())
        lastSnapshot = snapshot("已连接 · $name")
        identity!!
    }

    override suspend fun disconnect() {
        runCatching { if (liveUrl != null) stopLiveView() }
        runCatching { client.dispatcher.executorService.shutdown() }
    }

    override fun closeQuietly() {
        runCatching { client.dispatcher.executorService.shutdown() }
    }

    override suspend fun refresh(): CameraSnapshot = withContext(Dispatchers.IO) {
        val fresh = runCatching { readProperties() }.getOrDefault(emptyList())
        if (fresh.isNotEmpty()) props = fresh
        lastSnapshot = snapshot("已连接 · ${identity?.model ?: "Sony"}")
        lastSnapshot
    }

    private fun readProperties(): List<CameraProperty> {
        val out = ArrayList<CameraProperty>()
        val currentIso = callString("camera", "getIsoSpeedRate")
        out += make("iso", "ISO", PropertyGroup.EXPOSURE, currentIso ?: "AUTO", isoOptions, true)
        val currentShutter = callString("camera", "getShutterSpeed")
        out += make("shutter", "快门", PropertyGroup.EXPOSURE, currentShutter ?: "1/125", shutterOptions, true)
        val currentAperture = callString("camera", "getFNumber")
        out += make("aperture", "光圈", PropertyGroup.EXPOSURE, currentAperture ?: "f/2.8", apertureOptions, true)
        val currentEv = callString("camera", "getExposureCompensation")
        out += make("ev", "曝光补偿", PropertyGroup.EXPOSURE, currentEv ?: "0.0", evOptions, true)
        return out
    }

    private fun make(
        key: String,
        label: String,
        group: PropertyGroup,
        current: String,
        values: List<String>,
        primary: Boolean,
    ): CameraProperty {
        val opts = values.map { PropertyOption(it, stringHex(it)) }
        return CameraProperty(
            key = key,
            label = label,
            group = group,
            valueLabel = current,
            rawHex = stringHex(current),
            options = opts,
            writable = true,
            primary = primary,
            hint = "Sony API",
        )
    }

    override suspend fun updateProperty(property: CameraProperty, option: PropertyOption) {
        val value = decodeString(option.rawHex)
        val method = when (property.key) {
            "iso" -> "setIsoSpeedRate"
            "shutter" -> "setShutterSpeed"
            "aperture" -> "setFNumber"
            "ev" -> "setExposureCompensation"
            else -> throw IllegalArgumentException("不支持的参数")
        }
        withContext(Dispatchers.IO) {
            call("camera", method, JSONArray().put(value))
        }
        props = props.map { if (it.key == property.key) it.copy(valueLabel = option.label, rawHex = option.rawHex) else it }
        lastSnapshot = lastSnapshot.copy(properties = props)
    }

    override suspend fun capture(withAutofocus: Boolean): CaptureInfo = withContext(Dispatchers.IO) {
        if (withAutofocus) runCatching { focus() }
        val response = call("camera", "actTakePicture")
        val url = response?.optJSONArray("result")?.optJSONArray(0)?.optString(0)
        delay(250)
        var bytes: ByteArray? = null
        var capturedName = "Sony capture"
        if (!url.isNullOrBlank()) {
            bytes = runCatching { downloadUrl(url) }.getOrNull()
            capturedName = url.substringAfterLast('/').ifBlank { capturedName }
        }
        val media = CameraMedia(id = url ?: "sony-${System.currentTimeMillis()}", name = capturedName, isVideo = false)
        CaptureInfo(media = media, preview = bytes, message = "已拍摄")
    }

    override suspend fun focus(): Boolean = withContext(Dispatchers.IO) {
        runCatching { call("camera", "actHalfPressShutter"); true }.getOrDefault(false)
    }

    override suspend fun startLiveView(): Boolean = withContext(Dispatchers.IO) {
        val result = runCatching { call("camera", "startLiveview") }.getOrNull()
        val url = result?.optJSONArray("result")?.optString(0)?.takeIf { it.startsWith("http") }
        liveUrl = url
        lastSnapshot = lastSnapshot.copy(liveViewActive = url != null, liveViewAvailable = true)
        url != null
    }

    override suspend fun stopLiveView() {
        liveUrl = null
        lastSnapshot = lastSnapshot.copy(liveViewActive = false)
        withContext(Dispatchers.IO) { runCatching { call("camera", "stopLiveview") } }
    }

    override suspend fun liveViewFrame(): ByteArray? = withContext(Dispatchers.IO) {
        val url = liveUrl ?: return@withContext null
        runCatching {
            val res = client.newCall(Request.Builder().url(url).get().build()).execute()
            res.use { if (it.isSuccessful) it.body?.bytes() else null }
        }.getOrNull()
    }

    override suspend fun setRecording(recording: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            call("camera", "startMovieRec")
            true
        }.getOrDefault(false)
    }

    override suspend fun listMedia(limit: Int): List<CameraMedia> = withContext(Dispatchers.IO) {
        val result = runCatching {
            call("avContent", "getContentList", JSONArray().put(0).put(limit).put("flat"))
        }.getOrNull()
        val arr = result?.optJSONArray("result")?.optJSONArray(0) ?: return@withContext emptyList()
        val out = ArrayList<CameraMedia>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("contentUrl")
            if (url.isBlank()) continue
            out += CameraMedia(
                id = url,
                name = o.optString("title", url.substringAfterLast('/')),
                isVideo = o.optString("contentKind").contains("movie", true),
                sizeBytes = o.optLong("contentSize", 0),
            )
        }
        media = out.take(limit)
        lastSnapshot = lastSnapshot.copy(media = media)
        media
    }

    override suspend fun loadThumbnail(media: CameraMedia): ByteArray? = withContext(Dispatchers.IO) {
        val url = media.thumbnailUrl() ?: return@withContext null
        runCatching { downloadUrl(url) }.getOrNull()
    }

    override suspend fun download(media: CameraMedia): ByteArray = withContext(Dispatchers.IO) {
        downloadUrl(media.id)
    }

    private fun CameraMedia.thumbnailUrl(): String? {
        // Sony content list may include a thumbnailUrl; the URL is also usable
        // as a key. We simply fetch the still image when no thumbnail exists.
        return id.takeIf { !isVideo }
    }

    private fun downloadUrl(url: String): ByteArray {
        val res = client.newCall(Request.Builder().url(url).get().build()).execute()
        res.use {
            if (!it.isSuccessful) throw IOException("下载失败：HTTP ${it.code}")
            return it.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun call(service: String, method: String, params: JSONArray = JSONArray()): JSONObject? {
        val body = JSONObject()
            .put("method", method)
            .put("params", params)
            .put("id", idCounter++)
            .put("version", "1.0")
            .toString()
        val url = "$base/sony/$service"
        val res = client.newCall(
            Request.Builder().url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        res.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) return null
            val obj = JSONObject(text)
            if (obj.optJSONArray("error") != null) return null
            return obj
        }
    }

    private fun callString(service: String, method: String): String? {
        val result = call(service, method)?.optJSONArray("result") ?: return null
        for (i in 0 until result.length()) {
            val value = result.opt(i)
            if (value is String) return value
            if (value is JSONArray && value.length() > 0) {
                val nested = value.opt(0)
                if (nested is String) return nested
            }
        }
        return null
    }

    private fun snapshot(message: String): CameraSnapshot = CameraSnapshot(
        linkState = LinkState.CONNECTED,
        identity = identity,
        endpointLabel = transportLabel,
        statusMessage = message,
        properties = props,
        liveViewActive = liveUrl != null,
        liveViewAvailable = true,
        media = media,
        lastCapture = lastSnapshot.lastCapture,
    )

    private fun stringHex(value: String): String = PtpHex.encode(value.toByteArray(Charsets.UTF_8))
    private fun decodeString(hex: String): String = String(PtpHex.decode(hex), Charsets.UTF_8)
}
