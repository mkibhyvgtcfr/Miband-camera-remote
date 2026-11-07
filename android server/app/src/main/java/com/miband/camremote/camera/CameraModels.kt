package com.miband.camremote.camera

enum class CameraBrand(val displayName: String) {
    NIKON("尼康"),
    CANON("佳能"),
    SONY("索尼"),
    PANASONIC("松下"),
    FUJIFILM("富士"),
    GENERIC("相机"),
    VIRTUAL("演示相机");

    companion object {
        fun from(manufacturer: String, model: String, vendorExtension: Long): CameraBrand {
            val s = "$manufacturer $model".lowercase()
            return when {
                s.contains("nikon") || vendorExtension == 0x0000000AL -> NIKON
                s.contains("canon") || vendorExtension == 0x0000000BL -> CANON
                s.contains("sony") -> SONY
                s.contains("panasonic") || s.contains("lumix") -> PANASONIC
                s.contains("fuji") -> FUJIFILM
                else -> GENERIC
            }
        }
    }
}

enum class PropertyGroup(val title: String) {
    EXPOSURE("曝光"),
    IMAGE("画质"),
    COLOR("色彩"),
    FOCUS("对焦"),
    SYSTEM("系统"),
}

data class PropertyOption(
    val label: String,
    val rawHex: String,
)

data class CameraProperty(
    val key: String,
    val label: String,
    val group: PropertyGroup,
    val valueLabel: String,
    val rawHex: String,
    val options: List<PropertyOption> = emptyList(),
    val writable: Boolean = false,
    val primary: Boolean = false,
    val hint: String? = null,
) {
    val selectable: Boolean get() = writable && options.isNotEmpty()
}

data class CameraIdentity(
    val brand: CameraBrand,
    val manufacturer: String,
    val model: String,
    val firmware: String = "",
    val serial: String = "",
    val vendor: String = "",
) {
    val title: String get() = model.ifBlank { "未识别相机" }
}

data class CameraMedia(
    val id: String,
    val name: String,
    val date: String = "",
    val sizeBytes: Long = 0,
    val isVideo: Boolean = false,
    val thumbnail: ByteArray? = null,
    val localPath: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraMedia) return false
        return id == other.id && name == other.name && localPath == other.localPath
    }

    override fun hashCode(): Int = id.hashCode() * 31 + name.hashCode()
}

data class CaptureInfo(
    val media: CameraMedia? = null,
    val preview: ByteArray? = null,
    val message: String = "",
    // A RAW+JPEG body creates two card objects for one shutter press.
    val mediaList: List<CameraMedia> = emptyList(),
)

data class IntervalState(
    val active: Boolean = false,
    val total: Int = 0,
    val taken: Int = 0,
    val intervalSeconds: Int = 0,
)

sealed class CameraEndpoint {
    data class Usb(val deviceId: Int) : CameraEndpoint()
    data class PtpIp(val host: String, val port: Int = 15740) : CameraEndpoint()
    data class CanonCcapi(val host: String, val port: Int = 8080) : CameraEndpoint()
    data class SonyApi(val host: String, val port: Int = 10000) : CameraEndpoint()
    data object Virtual : CameraEndpoint()
}

enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class CameraSnapshot(
    val linkState: LinkState = LinkState.DISCONNECTED,
    val identity: CameraIdentity? = null,
    val endpointLabel: String = "",
    val statusMessage: String = "未连接相机",
    val batteryPercent: Int? = null,
    val availableShots: Int? = null,
    val properties: List<CameraProperty> = emptyList(),
    val liveViewActive: Boolean = false,
    val liveViewAvailable: Boolean = false,
    val busy: Boolean = false,
    val recording: Boolean = false,
    val captureCount: Int = 0,
    val lastCapture: CaptureInfo? = null,
    val media: List<CameraMedia> = emptyList(),
    val error: String? = null,
) {
    fun property(key: String): CameraProperty? = properties.firstOrNull { it.key == key }
    val isConnected: Boolean get() = linkState == LinkState.CONNECTED
}

fun CameraSnapshot.exposureSummary(): String {
    val shutter = property("shutter")?.valueLabel
    val aperture = property("aperture")?.valueLabel
    val iso = property("iso")?.valueLabel
    return listOfNotNull(shutter, aperture, iso).joinToString("  ")
}
