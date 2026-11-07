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
 * Canon Camera Connect API (CCAPI, "ver100"). Modern Canon Wi-Fi bodies expose
 * this JSON/HTTP interface on port 8080. It is deliberately independent from
 * PTP so it works while a Canon body is in Wi-Fi mode.
 */
class CanonCcapiConnection(
    override val endpoint: CameraEndpoint,
    private val host: String,
    private val port: Int,
) : CameraConnection {
    override val transportLabel: String = "Canon CCAPI · $host:$port"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val base = "http://$host:$port"
    private var identity: CameraIdentity? = null
    private var props: List<CameraProperty> = emptyList()
    private var live = false
    private var recording = false
    private var lastMedia: List<CameraMedia> = emptyList()
    private var lastInfo = JSONObject()
    private var lastSnapshot = CameraSnapshot()

    override suspend fun connect(): CameraIdentity = withContext(Dispatchers.IO) {
        val info = getJson("/ccapi/ver100/deviceinformation")
        lastInfo = info
        val name = info.optString("productname", info.optString("model", "Canon Camera"))
        val firmware = info.optString("firmwareversion", "")
        val serial = info.optString("serialnumber", "")
        identity = CameraIdentity(
            brand = CameraBrand.CANON,
            manufacturer = "Canon",
            model = name,
            firmware = firmware,
            serial = serial,
            vendor = "CCAPI",
        )
        props = runCatching { readAllSettings() }.getOrDefault(emptyList())
        lastSnapshot = snapshot("已连接 · $name")
        identity!!
    }

    override suspend fun disconnect() {
        runCatching { if (live) stopLiveView() }
        client.dispatcher.executorService.shutdown()
    }

    override fun closeQuietly() {
        runCatching { client.dispatcher.executorService.shutdown() }
    }

    override suspend fun refresh(): CameraSnapshot = withContext(Dispatchers.IO) {
        val fresh = runCatching { readAllSettings() }.getOrDefault(emptyList())
        if (fresh.isNotEmpty()) props = fresh
        lastSnapshot = snapshot("已连接 · ${identity?.model ?: "Canon"}")
        lastSnapshot
    }

    private suspend fun readAllSettings(): List<CameraProperty> = withContext(Dispatchers.IO) {
        val out = ArrayList<CameraProperty>()
        readSetting("iso", "ISO", PropertyGroup.EXPOSURE, true)?.let { out += it }
        readSetting("tv", "快门", PropertyGroup.EXPOSURE, true)?.let { out += it }
        readSetting("av", "光圈", PropertyGroup.EXPOSURE, true)?.let { out += it }
        readSetting("exposure", "曝光补偿", PropertyGroup.EXPOSURE, true)?.let { out += it }
        readSetting("wb", "白平衡", PropertyGroup.COLOR)?.let { out += it }
        readSetting("drive", "驱动模式", PropertyGroup.IMAGE)?.let { out += it }
        readSetting("picturestyle", "照片风格", PropertyGroup.COLOR)?.let { out += it }
        val battery = lastInfo.optJSONArray("batterylist")?.optJSONObject(0)
        val level = battery?.optString("level")?.toIntOrNull() ?: battery?.optInt("level", -1)?.takeIf { it >= 0 }
        if (level != null) {
            out += CameraProperty("battery", "电量", PropertyGroup.SYSTEM, "$level", "", writable = false)
        }
        out
    }

    private fun readSetting(key: String, label: String, group: PropertyGroup, primary: Boolean = false): CameraProperty? {
        val json = runCatching { getJson("/ccapi/ver100/shooting/settings/$key") }.getOrNull() ?: return null
        val value = when (val v = json.opt("value")) {
            is String -> v
            is Number -> v.toString()
            else -> json.optString("value", "")
        }
        if (value.isBlank()) return null
        val options = ArrayList<PropertyOption>()
        val ability = json.opt("ability")
        when (ability) {
            is JSONArray -> {
                for (i in 0 until ability.length()) {
                    val item = ability.opt(i)
                    val text = when (item) {
                        is String -> item
                        is Number -> item.toString()
                        is JSONObject -> item.optString("value", item.optString("name", ""))
                        else -> ""
                    }
                    if (text.isNotBlank()) options += PropertyOption(text, stringHex(text))
                }
            }
            is JSONObject -> {
                val list = ability.optJSONArray("value")
                if (list != null) for (i in 0 until list.length()) {
                    val text = list.optString(i)
                    if (text.isNotBlank()) options += PropertyOption(text, stringHex(text))
                }
                val min = ability.optDouble("min", Double.NaN)
                val max = ability.optDouble("max", Double.NaN)
                val step = ability.optDouble("step", 1.0)
                if (!min.isNaN() && !max.isNaN() && max >= min && step > 0) {
                    var v = min
                    while (v <= max + 0.0001 && options.size < 120) {
                        val text = if (step >= 1 && v == v.toInt().toDouble()) v.toInt().toString() else v.toString()
                        options += PropertyOption(text, stringHex(text))
                        v += step
                    }
                }
            }
        }
        if (options.isEmpty()) {
            options += PropertyOption(value, stringHex(value))
        }
        return CameraProperty(
            key = key,
            label = label,
            group = group,
            valueLabel = value,
            rawHex = stringHex(value),
            options = options,
            writable = options.size > 1,
            primary = primary,
            hint = "CCAPI",
        )
    }

    override suspend fun updateProperty(property: CameraProperty, option: PropertyOption) {
        val value = decodeString(option.rawHex)
        val key = when (property.key) {
            "iso" -> "iso"
            "shutter" -> "tv"
            "aperture" -> "av"
            "ev" -> "exposure"
            "wb" -> "wb"
            "drive" -> "drive"
            "picture_style" -> "picturestyle"
            else -> property.key
        }
        val body = JSONObject().put("value", value).toString()
        withContext(Dispatchers.IO) {
            putJson("/ccapi/ver100/shooting/settings/$key", body)
        }
        props = props.map { if (it.key == property.key) it.copy(valueLabel = option.label, rawHex = option.rawHex) else it }
        lastSnapshot = lastSnapshot.copy(properties = props)
    }

    override suspend fun capture(withAutofocus: Boolean): CaptureInfo {
        if (withAutofocus) runCatching { focus() }
        val body = JSONObject().put("af", withAutofocus).put("release", true).toString()
        val result = withContext(Dispatchers.IO) {
            postJson("/ccapi/ver100/shooting/control/shutterbutton", body)
        }
        delay(700)
        val media = runCatching { listMedia(8) }.getOrDefault(emptyList())
        val latest = media.firstOrNull()
        val preview = latest?.let { runCatching { loadThumbnail(it) }.getOrNull() }
        return CaptureInfo(
            media = latest,
            preview = preview ?: extractImageFromJson(result),
            message = if (latest != null) "已拍摄 · ${latest.name}" else "已发送拍摄指令",
        )
    }

    override suspend fun focus(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            postJson("/ccapi/ver100/shooting/control/shutterbutton", JSONObject().put("af", true).put("release", false).toString())
            delay(450)
            postJson("/ccapi/ver100/shooting/control/shutterbutton", JSONObject().put("af", false).put("release", false).toString())
            true
        }.getOrDefault(false)
    }

    override suspend fun startLiveView(): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching {
            val res = client.newCall(Request.Builder().url("$base/ccapi/ver100/shooting/liveview").get().build()).execute()
            res.use { it.isSuccessful }
        }.getOrDefault(false)
        live = ok
        lastSnapshot = lastSnapshot.copy(liveViewActive = ok, liveViewAvailable = ok)
        ok
    }

    override suspend fun stopLiveView() {
        live = false
        lastSnapshot = lastSnapshot.copy(liveViewActive = false)
    }

    override suspend fun liveViewFrame(): ByteArray? = withContext(Dispatchers.IO) {
        if (!live) return@withContext null
        runCatching {
            val res = client.newCall(
                Request.Builder().url("$base/ccapi/ver100/shooting/liveview").get().build()
            ).execute()
            res.use {
                if (!it.isSuccessful) return@use null
                val bytes = it.body?.bytes()
                bytes?.let { b -> PtpParser.findJpeg(b) ?: b }
            }
        }.getOrNull()
    }

    override suspend fun setRecording(recording: Boolean): Boolean {
        // Canon's CCAPI movie start is body-dependent. We try the documented
        // endpoint and leave the UI unchanged if the camera rejects it.
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("action", if (recording) "start" else "stop").toString()
                postJson("/ccapi/ver100/shooting/control/movierec", body)
                this@CanonCcapiConnection.recording = recording
                true
            }.getOrDefault(false)
        }
    }

    override suspend fun listMedia(limit: Int): List<CameraMedia> = withContext(Dispatchers.IO) {
        val dirs = runCatching {
            val arr = getJsonArray("/ccapi/ver100/contents/sd")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "")
                    if (name.isNotBlank()) add(name)
                }
            }
        }.getOrDefault(emptyList())
        val result = ArrayList<CameraMedia>()
        for (dir in dirs.takeLast(4).reversed()) {
            runCatching {
                val arr = getJsonArray("/ccapi/ver100/contents/sd/$dir")
                val files = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val name = o.optString("name", o.optString("filename", ""))
                        if (name.isBlank()) continue
                        val url = o.optString("url", "")
                        val kind = o.optString("kind", o.optString("type", ""))
                        add(
                            CameraMedia(
                                id = url.ifBlank { "$base/ccapi/ver100/contents/sd/$dir/$name" },
                                name = name,
                                date = o.optString("datetime", o.optString("date", "")),
                                sizeBytes = o.optLong("size", 0),
                                isVideo = kind.contains("movie", true) || kind.contains("video", true) ||
                                    name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".mov"),
                            )
                        )
                    }
                }
                result.addAll(files.sortedByDescending { it.name }.take(limit - result.size))
            }
        }
        lastMedia = result.take(limit)
        lastSnapshot = lastSnapshot.copy(media = lastMedia)
        lastMedia
    }

    override suspend fun loadThumbnail(media: CameraMedia): ByteArray? = withContext(Dispatchers.IO) {
        media.thumbnail?.let { return@withContext it }
        runCatching {
            val url = if (media.id.startsWith("http")) "${media.id}?kind=thumbnail" else media.id
            val res = client.newCall(Request.Builder().url(url).get().build()).execute()
            res.use { if (it.isSuccessful) it.body?.bytes() else null }
        }.getOrNull()
    }

    override suspend fun download(media: CameraMedia): ByteArray = withContext(Dispatchers.IO) {
        val url = if (media.id.startsWith("http")) media.id else "$base/ccapi/ver100/contents/sd/${media.id}"
        val res = client.newCall(Request.Builder().url(url).get().build()).execute()
        res.use {
            if (!it.isSuccessful) throw IOException("下载失败：HTTP ${it.code}")
            it.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun getJson(path: String): JSONObject {
        val res = client.newCall(Request.Builder().url(base + path).get().build()).execute()
        res.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}: ${body.take(180)}")
            return JSONObject(body)
        }
    }

    private fun getJsonArray(path: String): JSONArray {
        val res = client.newCall(Request.Builder().url(base + path).get().build()).execute()
        res.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}: ${body.take(180)}")
            return JSONArray(body)
        }
    }

    private fun putJson(path: String, body: String): JSONObject {
        val res = client.newCall(
            Request.Builder().url(base + path)
                .put(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        res.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}: ${text.take(180)}")
            return runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        }
    }

    private fun postJson(path: String, body: String): JSONObject {
        val res = client.newCall(
            Request.Builder().url(base + path)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        res.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}: ${text.take(180)}")
            return runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        }
    }

    private fun snapshot(message: String): CameraSnapshot {
        val battery = lastInfo.optJSONArray("batterylist")?.optJSONObject(0)
            ?.optString("level")?.toIntOrNull()
        return CameraSnapshot(
            linkState = LinkState.CONNECTED,
            identity = identity,
            endpointLabel = transportLabel,
            statusMessage = message,
            batteryPercent = battery,
            properties = props,
            liveViewActive = live,
            liveViewAvailable = true,
            recording = recording,
            media = lastMedia,
            lastCapture = lastSnapshot.lastCapture,
        )
    }

    private fun stringHex(value: String): String = PtpHex.encode(value.toByteArray(Charsets.UTF_8))
    private fun decodeString(hex: String): String = String(PtpHex.decode(hex), Charsets.UTF_8)
    private fun extractImageFromJson(json: JSONObject): ByteArray? = null
}
