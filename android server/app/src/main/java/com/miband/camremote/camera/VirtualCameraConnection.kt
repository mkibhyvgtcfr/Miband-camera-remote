package com.miband.camremote.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

/** A hardware-free camera used for first-run demos and UI testing. */
class VirtualCameraConnection : CameraConnection {
    override val endpoint: CameraEndpoint = CameraEndpoint.Virtual
    override val transportLabel: String = "演示模式 · 虚拟相机"

    private var live = false
    private var recording = false
    private var frame = 0
    private var captures = 0
    private var props = defaultProps()
    private val media = ArrayList<CameraMedia>()
    private val imageCache = HashMap<String, ByteArray>()

    private fun defaultProps(): List<CameraProperty> = listOf(
        opt("iso", "ISO", PropertyGroup.EXPOSURE, "400", listOf("100", "200", "400", "800", "1600", "3200"), true),
        opt("shutter", "快门", PropertyGroup.EXPOSURE, "1/125", listOf("1/30", "1/60", "1/125", "1/250", "1/500", "1/1000"), true),
        opt("aperture", "光圈", PropertyGroup.EXPOSURE, "f/2.8", listOf("f/1.8", "f/2.0", "f/2.8", "f/4.0", "f/5.6", "f/8.0"), true),
        opt("ev", "曝光补偿", PropertyGroup.EXPOSURE, "0.0", listOf("-1.0", "-0.7", "-0.3", "0.0", "+0.3", "+0.7", "+1.0"), true),
        opt("wb", "白平衡", PropertyGroup.COLOR, "自动", listOf("自动", "日光", "阴天", "钨丝灯", "荧光灯"), false),
        opt("drive", "驱动模式", PropertyGroup.IMAGE, "单张", listOf("单张", "连拍", "定时 2s", "定时 10s"), false),
    )

    private fun opt(key: String, label: String, group: PropertyGroup, value: String, values: List<String>, primary: Boolean) =
        CameraProperty(
            key = key,
            label = label,
            group = group,
            valueLabel = value,
            rawHex = PtpHex.encode(value.toByteArray()),
            options = values.map { PropertyOption(it, PtpHex.encode(it.toByteArray())) },
            writable = true,
            primary = primary,
            hint = "演示相机",
        )

    override suspend fun connect(): CameraIdentity {
        delay(300)
        return CameraIdentity(CameraBrand.VIRTUAL, "MiBand", "Vision Demo", "1.0", "DEMO-0001", "Virtual PTP")
    }

    override suspend fun disconnect() {
        live = false
    }

    override fun closeQuietly() = Unit

    override suspend fun refresh(): CameraSnapshot = snapshot("演示相机在线")

    override suspend fun updateProperty(property: CameraProperty, option: PropertyOption) {
        props = props.map {
            if (it.key == property.key) it.copy(valueLabel = option.label, rawHex = option.rawHex) else it
        }
    }

    override suspend fun capture(withAutofocus: Boolean): CaptureInfo {
        if (withAutofocus) delay(220)
        delay(180)
        captures++
        val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        val name = "DEMO_${stamp}_$captures.JPG"
        val bytes = renderFrame(highQuality = true)
        val m = CameraMedia(
            id = "demo-$captures",
            name = name,
            date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            sizeBytes = bytes.size.toLong(),
            thumbnail = bytes,
            localPath = null,
        )
        imageCache[m.id] = bytes
        media.add(0, m)
        return CaptureInfo(m, bytes, "演示拍摄完成 · $name")
    }

    override suspend fun focus(): Boolean {
        delay(350)
        return true
    }

    override suspend fun startLiveView(): Boolean {
        live = true
        return true
    }

    override suspend fun stopLiveView() {
        live = false
    }

    override suspend fun liveViewFrame(): ByteArray? {
        if (!live) return null
        frame++
        return renderFrame(highQuality = false)
    }

    override suspend fun setRecording(recording: Boolean): Boolean {
        this.recording = recording
        return true
    }

    override suspend fun listMedia(limit: Int): List<CameraMedia> = media.take(limit)

    override suspend fun loadThumbnail(media: CameraMedia): ByteArray? =
        media.thumbnail ?: imageCache[media.id]

    override suspend fun download(media: CameraMedia): ByteArray =
        imageCache[media.id] ?: throw IllegalStateException("演示文件不存在")

    private fun snapshot(message: String) = CameraSnapshot(
        linkState = LinkState.CONNECTED,
        identity = CameraIdentity(CameraBrand.VIRTUAL, "MiBand", "Vision Demo", "1.0", "DEMO-0001"),
        endpointLabel = transportLabel,
        statusMessage = message,
        batteryPercent = 86,
        availableShots = 1234,
        properties = props,
        liveViewActive = live,
        liveViewAvailable = true,
        recording = recording,
        captureCount = captures,
        media = media,
    )

    private fun renderFrame(highQuality: Boolean): ByteArray {
        val w = if (highQuality) 1600 else 960
        val h = if (highQuality) 900 else 540
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.rgb(10, 14, 20), Color.rgb(30, 18, 10), Color.rgb(9, 9, 12)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val glow = Paint().apply {
            shader = RadialGradient(w * 0.65f, h * 0.45f, w * 0.5f,
                intArrayOf(Color.argb(140, 255, 105, 0), Color.argb(0, 255, 105, 0)),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(w * 0.65f, h * 0.45f, w * 0.5f, glow)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.008f
            color = Color.argb(180, 255, 255, 255)
        }
        val cx = w * 0.5f
        val cy = h * 0.48f
        canvas.drawCircle(cx, cy, w * 0.22f, ring)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.002f
            color = Color.argb(90, 255, 255, 255)
        }
        for (i in 1..3) canvas.drawCircle(cx, cy, w * (0.06f + i * 0.055f), inner)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.035f
            letterSpacing = 0.12f
        }
        canvas.drawText("MIBAND  VISION", w * 0.07f, h * 0.1f, text)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            textSize = w * 0.018f
        }
        val exposure = props.filter { it.primary }.joinToString("   ") { it.valueLabel }
        canvas.drawText(exposure, w * 0.07f, h * 0.86f, small)
        canvas.drawText("DEMO CAMERA  ·  LIVE VIEW", w * 0.07f, h * 0.92f, small)
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        canvas.drawText(t, w * 0.82f, h * 0.92f, small)
        // A gentle moving light edge makes the demo clearly live.
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 105, 0)
            strokeWidth = w * 0.004f
        }
        val y = (h * 0.55f + sin(frame / 6.0) * h * 0.1).toFloat()
        canvas.drawLine(w * 0.3f, y, w * 0.7f, y, edge)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, if (highQuality) 94 else 76, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
