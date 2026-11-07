package com.miband.camremote.camera

data class PropertySpec(
    val key: String,
    val label: String,
    val group: PropertyGroup,
    val code: Int,
    val fixedOptions: List<PropertyOption>? = null,
    val writable: Boolean = true,
    val primary: Boolean = false,
    val dataType: Int = Ptp.DATA_TYPE_UINT32,
)

object PropertyCatalog {
    private fun canonOption(label: String, value: Int) =
        PropertyOption(label, PtpHex.encode(Le.u32Bytes(value.toLong())))

    private val canonIso = listOf(
        "AUTO" to 0x00, "50" to 0x40, "100" to 0x48, "125" to 0x4B, "160" to 0x4D,
        "200" to 0x50, "250" to 0x53, "320" to 0x55, "400" to 0x58, "500" to 0x5B,
        "640" to 0x5D, "800" to 0x60, "1000" to 0x63, "1250" to 0x65, "1600" to 0x68,
        "2000" to 0x6B, "2500" to 0x6D, "3200" to 0x70, "4000" to 0x73, "5000" to 0x75,
        "6400" to 0x78, "8000" to 0x7B, "10000" to 0x7D, "12800" to 0x80, "16000" to 0x83,
        "20000" to 0x85, "25600" to 0x88,
    ).map { canonOption(it.first, it.second) }

    private val canonShutter = listOf(
        "BULB" to 0x0C,
        "30\"" to 0x10, "25\"" to 0x13, "20\"" to 0x15, "15\"" to 0x18,
        "13\"" to 0x1B, "10\"" to 0x1D, "8\"" to 0x20, "6\"" to 0x23,
        "5\"" to 0x25, "4\"" to 0x28, "3.2\"" to 0x2B, "2.5\"" to 0x2D,
        "2\"" to 0x30, "1.6\"" to 0x33, "1.3\"" to 0x35, "1\"" to 0x38,
        "0.8\"" to 0x3B, "0.6\"" to 0x3D, "0.5\"" to 0x40, "0.4\"" to 0x43,
        "1/4" to 0x45, "1/5" to 0x48, "1/6" to 0x4B, "1/8" to 0x4D,
        "1/10" to 0x50, "1/13" to 0x53, "1/15" to 0x55, "1/20" to 0x58,
        "1/25" to 0x5B, "1/30" to 0x5D, "1/40" to 0x60, "1/50" to 0x63,
        "1/60" to 0x65, "1/80" to 0x68, "1/100" to 0x6B, "1/125" to 0x6D,
        "1/160" to 0x70, "1/200" to 0x73, "1/250" to 0x75, "1/320" to 0x78,
        "1/400" to 0x7B, "1/500" to 0x7D, "1/640" to 0x80, "1/800" to 0x83,
        "1/1000" to 0x85, "1/1250" to 0x88, "1/1600" to 0x8B, "1/2000" to 0x8D,
        "1/2500" to 0x90, "1/3200" to 0x93, "1/4000" to 0x95, "1/5000" to 0x98,
        "1/6400" to 0x9A, "1/8000" to 0x9D,
    ).map { canonOption(it.first, it.second) }.reversed()

    private val canonAperture = listOf(
        "f/1.2" to 0x0D, "f/1.4" to 0x10, "f/1.6" to 0x13, "f/1.8" to 0x15,
        "f/2.0" to 0x18, "f/2.2" to 0x1B, "f/2.5" to 0x1D, "f/2.8" to 0x20,
        "f/3.2" to 0x23, "f/3.5" to 0x25, "f/4.0" to 0x28, "f/4.5" to 0x2B,
        "f/5.0" to 0x2D, "f/5.6" to 0x30, "f/6.3" to 0x33, "f/7.1" to 0x35,
        "f/8.0" to 0x38, "f/9.0" to 0x3B, "f/10" to 0x3D, "f/11" to 0x40,
        "f/13" to 0x43, "f/14" to 0x45, "f/16" to 0x48, "f/18" to 0x4B,
        "f/20" to 0x4D, "f/22" to 0x50, "f/25" to 0x53, "f/29" to 0x55,
        "f/32" to 0x58,
    ).map { canonOption(it.first, it.second) }

    private val canonEv = listOf(
        "-3" to 0xE8, "-2 2/3" to 0xEB, "-2 1/3" to 0xED, "-2" to 0xF0,
        "-1 2/3" to 0xF3, "-1 1/3" to 0xF5, "-1" to 0xF8, "-2/3" to 0xFB,
        "-1/3" to 0xFD, "0" to 0x00, "+1/3" to 0x03, "+2/3" to 0x05,
        "+1" to 0x08, "+1 1/3" to 0x0B, "+1 2/3" to 0x0D, "+2" to 0x10,
        "+2 1/3" to 0x13, "+2 2/3" to 0x15, "+3" to 0x18,
    ).map { canonOption(it.first, it.second) }

    private val canonWb = listOf(
        "自动" to 0, "日光" to 1, "阴天" to 2, "钨丝灯" to 3, "荧光灯" to 4,
        "闪光灯" to 5, "手动" to 6, "阴影" to 8, "色温" to 9,
    ).map { canonOption(it.first, it.second) }

    private val canonDrive = listOf(
        "单张" to 0x0000, "连续" to 0x0001, "视频" to 0x0002,
        "高速连拍" to 0x0004, "低速连拍" to 0x0005, "静音单张" to 0x0006,
        "10 秒定时" to 0x0010, "2 秒定时" to 0x0011,
    ).map { canonOption(it.first, it.second) }

    private val canonPictureStyle = listOf(
        "自动" to 0, "标准" to 1, "人像" to 2, "风光" to 3, "中性" to 4,
        "可靠设置" to 5, "单色" to 6, "用户定义 1" to 7, "用户定义 2" to 8,
        "用户定义 3" to 9,
    ).map { canonOption(it.first, it.second) }

    fun forBrand(brand: CameraBrand): List<PropertySpec> = when (brand) {
        CameraBrand.CANON -> canonSpecs
        CameraBrand.NIKON -> nikonSpecs
        else -> genericSpecs
    }

    private val exposureSpecs = listOf(
        PropertySpec("iso", "ISO", PropertyGroup.EXPOSURE, Ptp.DPC_EXPOSURE_INDEX, primary = true),
        PropertySpec("shutter", "快门", PropertyGroup.EXPOSURE, Ptp.DPC_EXPOSURE_TIME, primary = true),
        PropertySpec("aperture", "光圈", PropertyGroup.EXPOSURE, Ptp.DPC_F_NUMBER, primary = true),
        PropertySpec("ev", "曝光补偿", PropertyGroup.EXPOSURE, Ptp.DPC_EXPOSURE_BIAS, primary = true),
    )

    private val genericSpecs = exposureSpecs + listOf(
        PropertySpec("wb", "白平衡", PropertyGroup.COLOR, Ptp.DPC_WHITE_BALANCE),
        PropertySpec("quality", "画质", PropertyGroup.IMAGE, Ptp.DPC_COMPRESSION),
        PropertySpec("image_size", "图像尺寸", PropertyGroup.IMAGE, Ptp.DPC_IMAGE_SIZE),
        PropertySpec("metering", "测光", PropertyGroup.EXPOSURE, Ptp.DPC_METERING_MODE),
        PropertySpec("drive", "驱动模式", PropertyGroup.IMAGE, Ptp.DPC_STILL_CAPTURE_MODE),
        PropertySpec("focus", "对焦模式", PropertyGroup.FOCUS, Ptp.DPC_FOCUS_MODE),
        PropertySpec("flash", "闪光灯", PropertyGroup.EXPOSURE, Ptp.DPC_FLASH_MODE),
        PropertySpec("mode", "曝光模式", PropertyGroup.EXPOSURE, Ptp.DPC_EXPOSURE_PROGRAM),
        PropertySpec("battery", "电量", PropertyGroup.SYSTEM, Ptp.DPC_BATTERY_LEVEL, writable = false, dataType = Ptp.DATA_TYPE_UINT8),
    )

    private val nikonSpecs = listOf(
        PropertySpec("iso", "ISO", PropertyGroup.EXPOSURE, Ptp.DPC_EXPOSURE_INDEX, primary = true),
        PropertySpec("shutter", "快门", PropertyGroup.EXPOSURE, Ptp.DPC_NIKON_EXPOSURE_TIME, primary = true),
        PropertySpec("aperture", "光圈", PropertyGroup.EXPOSURE, Ptp.DPC_F_NUMBER, primary = true),
        PropertySpec("ev", "曝光补偿", PropertyGroup.EXPOSURE, Ptp.DPC_EXPOSURE_BIAS, primary = true),
        PropertySpec("wb", "白平衡", PropertyGroup.COLOR, Ptp.DPC_WHITE_BALANCE),
        PropertySpec("quality", "画质", PropertyGroup.IMAGE, Ptp.DPC_COMPRESSION),
        PropertySpec("image_size", "图像尺寸", PropertyGroup.IMAGE, Ptp.DPC_IMAGE_SIZE),
        PropertySpec("metering", "测光", PropertyGroup.EXPOSURE, Ptp.DPC_METERING_MODE),
        PropertySpec("drive", "释放模式", PropertyGroup.IMAGE, Ptp.DPC_STILL_CAPTURE_MODE),
        PropertySpec("focus", "对焦模式", PropertyGroup.FOCUS, Ptp.DPC_FOCUS_MODE),
        PropertySpec("mode", "拍摄模式", PropertyGroup.EXPOSURE, Ptp.DPC_EXPOSURE_PROGRAM),
        PropertySpec("iso_auto", "自动 ISO", PropertyGroup.EXPOSURE, Ptp.DPC_NIKON_ISOAUTO, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("iso_auto2", "ISO 自动", PropertyGroup.EXPOSURE, Ptp.DPC_NIKON_ISO_AUTO, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("iso_auto_hi", "自动 ISO 上限", PropertyGroup.EXPOSURE, Ptp.DPC_NIKON_ISOAUTO_HI_LIMIT, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("flash", "闪光模式", PropertyGroup.EXPOSURE, Ptp.DPC_NIKON_FLASH_MODE, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("active_d", "动态 D-Lighting", PropertyGroup.IMAGE, Ptp.DPC_NIKON_ACTIVE_D_LIGHTING, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("long_nr", "长曝光降噪", PropertyGroup.IMAGE, Ptp.DPC_NIKON_LONG_EXPOSURE_NR, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("high_nr", "高 ISO 降噪", PropertyGroup.IMAGE, Ptp.DPC_NIKON_HIGH_ISO_NR, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("exp_delay", "曝光延迟", PropertyGroup.EXPOSURE, Ptp.DPC_NIKON_EXPOSURE_DELAY, dataType = Ptp.DATA_TYPE_UINT8),
        PropertySpec("battery", "电量", PropertyGroup.SYSTEM, Ptp.DPC_BATTERY_LEVEL, writable = false, dataType = Ptp.DATA_TYPE_UINT8),
    )

    private val canonSpecs = listOf(
        PropertySpec("iso", "ISO", PropertyGroup.EXPOSURE, Ptp.DPC_EOS_ISO, canonIso, primary = true),
        PropertySpec("shutter", "快门", PropertyGroup.EXPOSURE, Ptp.DPC_EOS_SHUTTER_SPEED, canonShutter, primary = true),
        PropertySpec("aperture", "光圈", PropertyGroup.EXPOSURE, Ptp.DPC_EOS_APERTURE, canonAperture, primary = true),
        PropertySpec("ev", "曝光补偿", PropertyGroup.EXPOSURE, Ptp.DPC_EOS_EXPOSURE_COMP, canonEv, primary = true),
        PropertySpec("wb", "白平衡", PropertyGroup.COLOR, Ptp.DPC_EOS_WHITE_BALANCE, canonWb),
        PropertySpec("drive", "驱动模式", PropertyGroup.IMAGE, Ptp.DPC_EOS_DRIVE_MODE, canonDrive),
        PropertySpec("picture_style", "照片风格", PropertyGroup.COLOR, Ptp.DPC_EOS_PICTURE_STYLE, canonPictureStyle),
        PropertySpec("shots", "剩余张数", PropertyGroup.SYSTEM, Ptp.DPC_EOS_AVAILABLE_SHOTS, writable = false),
        PropertySpec("counter", "快门计数", PropertyGroup.SYSTEM, Ptp.DPC_EOS_SHUTTER_COUNTER, writable = false),
    )
}

object PropertyFormat {
    private val shutterLabels = mapOf(
        0x0C to "BULB", 0x10 to "30\"", 0x13 to "25\"", 0x15 to "20\"", 0x18 to "15\"",
        0x1B to "13\"", 0x1D to "10\"", 0x20 to "8\"", 0x23 to "6\"", 0x25 to "5\"",
        0x28 to "4\"", 0x2B to "3.2\"", 0x2D to "2.5\"", 0x30 to "2\"", 0x33 to "1.6\"",
        0x35 to "1.3\"", 0x38 to "1\"", 0x3B to "0.8\"", 0x3D to "0.6\"", 0x40 to "0.5\"",
        0x43 to "0.4\"", 0x45 to "1/4", 0x48 to "1/5", 0x4B to "1/6", 0x4D to "1/8",
        0x50 to "1/10", 0x53 to "1/13", 0x55 to "1/15", 0x58 to "1/20", 0x5B to "1/25",
        0x5D to "1/30", 0x60 to "1/40", 0x63 to "1/50", 0x65 to "1/60", 0x68 to "1/80",
        0x6B to "1/100", 0x6D to "1/125", 0x70 to "1/160", 0x73 to "1/200", 0x75 to "1/250",
        0x78 to "1/320", 0x7B to "1/400", 0x7D to "1/500", 0x80 to "1/640", 0x83 to "1/800",
        0x85 to "1/1000", 0x88 to "1/1250", 0x8B to "1/1600", 0x8D to "1/2000", 0x90 to "1/2500",
        0x93 to "1/3200", 0x95 to "1/4000", 0x98 to "1/5000", 0x9A to "1/6400", 0x9D to "1/8000",
    )
    private val apertureLabels = mapOf(
        0x0D to "f/1.2", 0x10 to "f/1.4", 0x13 to "f/1.6", 0x15 to "f/1.8", 0x18 to "f/2.0",
        0x1B to "f/2.2", 0x1D to "f/2.5", 0x20 to "f/2.8", 0x23 to "f/3.2", 0x25 to "f/3.5",
        0x28 to "f/4.0", 0x2B to "f/4.5", 0x2D to "f/5.0", 0x30 to "f/5.6", 0x33 to "f/6.3",
        0x35 to "f/7.1", 0x38 to "f/8.0", 0x3B to "f/9.0", 0x3D to "f/10", 0x40 to "f/11",
        0x43 to "f/13", 0x45 to "f/14", 0x48 to "f/16", 0x4B to "f/18", 0x4D to "f/20",
        0x50 to "f/22", 0x53 to "f/25", 0x55 to "f/29", 0x58 to "f/32",
    )
    private val evLabels = mapOf(
        0xE8 to "-3", 0xEB to "-2.7", 0xED to "-2.3", 0xF0 to "-2", 0xF3 to "-1.7",
        0xF5 to "-1.3", 0xF8 to "-1", 0xFB to "-0.7", 0xFD to "-0.3", 0x00 to "0",
        0x03 to "+0.3", 0x05 to "+0.7", 0x08 to "+1", 0x0B to "+1.3", 0x0D to "+1.7",
        0x10 to "+2", 0x13 to "+2.3", 0x15 to "+2.7", 0x18 to "+3",
    )
    private val canonIsoLabels = mapOf(
        0x00 to "AUTO", 0x40 to "50", 0x48 to "100", 0x4B to "125", 0x4D to "160",
        0x50 to "200", 0x53 to "250", 0x55 to "320", 0x58 to "400", 0x5B to "500",
        0x5D to "640", 0x60 to "800", 0x63 to "1000", 0x65 to "1250", 0x68 to "1600",
        0x6B to "2000", 0x6D to "2500", 0x70 to "3200", 0x73 to "4000", 0x75 to "5000",
        0x78 to "6400", 0x7B to "8000", 0x7D to "10000", 0x80 to "12800", 0x83 to "16000",
        0x85 to "20000", 0x88 to "25600",
    )
    private val canonPictureStyleLabels = mapOf(
        0 to "自动", 1 to "标准", 2 to "人像", 3 to "风光", 4 to "中性",
        5 to "可靠设置", 6 to "单色", 7 to "用户定义 1", 8 to "用户定义 2", 9 to "用户定义 3",
    )
    private val canonDriveLabels = mapOf(
        0x0000 to "单张", 0x0001 to "连续", 0x0002 to "视频", 0x0004 to "高速连拍",
        0x0005 to "低速连拍", 0x0006 to "静音单张", 0x0010 to "10 秒定时", 0x0011 to "2 秒定时",
    )
    private val canonWbLabels = mapOf(
        0 to "自动", 1 to "日光", 2 to "阴天", 3 to "钨丝灯", 4 to "荧光灯", 5 to "闪光灯",
        6 to "手动", 8 to "阴影", 9 to "色温",
    )
    private val wbLabels = mapOf(
        1 to "手动", 2 to "自动", 3 to "一键", 4 to "日光", 5 to "荧光灯",
        6 to "钨丝灯", 7 to "闪光灯", 8 to "阴天", 9 to "阴影", 0x8001 to "自动",
        0x8002 to "白炽灯", 0x8003 to "荧光灯", 0x8004 to "晴天", 0x8005 to "阴天",
        0x8006 to "阴影", 0x8007 to "色温",
    )
    private val meteringLabels = mapOf(1 to "平均", 2 to "中央重点", 3 to "点测光", 4 to "多点", 5 to "评价")
    private val driveLabels = mapOf(1 to "单张", 2 to "连拍", 3 to "定时", 4 to "间隔", 5 to "包围")
    private val focusLabels = mapOf(1 to "手动", 2 to "自动", 3 to "自动微距", 4 to "连续")
    private val exposureModeLabels = mapOf(
        1 to "M", 2 to "P", 3 to "A", 4 to "S",
        5 to "P（创意）", 6 to "P（动作）", 7 to "人像", 8 to "风景", 9 to "B门",
    )
    private val nikonIsoAutoLabels = mapOf(0 to "关闭", 1 to "开启")
    private val nikonQualityLabels = mapOf(
        0 to "JPEG Basic", 1 to "JPEG Normal", 2 to "JPEG Fine", 3 to "TIFF",
        4 to "NEF (RAW)", 5 to "NEF + Basic", 6 to "NEF + Normal", 7 to "NEF + Fine",
    )
    private val onOffLabels = mapOf(0 to "关闭", 1 to "开启")

    fun numeric(raw: ByteArray, dataType: Int): Long = when (dataType) {
        Ptp.DATA_TYPE_INT8 -> (raw.getOrNull(0)?.toLong() ?: 0)
        Ptp.DATA_TYPE_UINT8 -> (raw.getOrNull(0)?.toLong() ?: 0) and 0xFF
        Ptp.DATA_TYPE_INT16 -> Le.i16(raw).toLong()
        Ptp.DATA_TYPE_UINT16 -> Le.u16(raw).toLong()
        Ptp.DATA_TYPE_INT32 -> Le.i32(raw).toLong()
        Ptp.DATA_TYPE_UINT32, Ptp.DATA_TYPE_STRING -> Le.u32(raw)
        else -> Le.u32(raw)
    }

    fun display(spec: PropertySpec, raw: ByteArray, dataType: Int = spec.dataType): String {
        val code = spec.code
        if (code == Ptp.DPC_EOS_ISO || spec.key == "iso") {
            val v = numeric(raw, dataType)
            if (v == 0L || v == 0xFFFFL || v == 0xFFFFFFFFL) return "AUTO"
            if (code == Ptp.DPC_EOS_ISO) return canonIsoLabels[v.toInt()] ?: "$v"
            return "$v"
        }
        if (spec.key == "shutter") {
            val v = numeric(raw, Ptp.DATA_TYPE_UINT32)
            return when {
                code == Ptp.DPC_EOS_SHUTTER_SPEED -> shutterLabels[v.toInt()] ?: "1/${v}"
                code == Ptp.DPC_NIKON_EXPOSURE_TIME -> {
                    if (v == 0xFFFFFFFFL) "BULB"
                    else if (v == 0xFFFFFFFDL) "TIME"
                    else {
                        val num = (v ushr 16).toInt()
                        val den = (v and 0xFFFF).toInt()
                        if (den == 1) "$num\"" else "$num/$den"
                    }
                }
                v == 0xFFFFFFFFL -> "BULB"
                v == 0L -> "—"
                v >= 1_000_000 -> "${"%.1f".format(java.util.Locale.US, v / 1_000_000.0)}\""
                v >= 10_000 -> "${"%.1f".format(java.util.Locale.US, v / 1_000_000.0)}\""
                else -> "1/${Math.round(1_000_000.0 / v)}"
            }
        }
        if (spec.key == "aperture") {
            val v = numeric(raw, dataType).toInt()
            return if (code == Ptp.DPC_EOS_APERTURE) apertureLabels[v] ?: "f/${v / 10.0}"
            else if (v >= 100) "f/${"%.1f".format(java.util.Locale.US, v / 100.0)}"
            else if (v > 0) "f/$v" else "—"
        }
        if (spec.key == "ev") {
            val v = numeric(raw, dataType).toInt()
            if (code == Ptp.DPC_EOS_EXPOSURE_COMP) return evLabels[v and 0xFF] ?: "0"
            val ev = (numeric(raw, dataType)).toDouble() / 1000.0
            return if (ev > 0) "+${"%.1f".format(java.util.Locale.US, ev)}" else "%.1f".format(java.util.Locale.US, ev)
        }
        if (spec.key == "battery" || spec.key == "shots" || spec.key == "counter") {
            return numeric(raw, dataType).toString()
        }
        val v = numeric(raw, dataType)
        return when (spec.key) {
            "wb" -> canonWbLabels[v.toInt()] ?: wbLabels[v.toInt()] ?: v.toString()
            "metering" -> meteringLabels[v.toInt()] ?: v.toString()
            "drive" -> canonDriveLabels[v.toInt()] ?: driveLabels[v.toInt()] ?: v.toString()
            "focus" -> focusLabels[v.toInt()] ?: v.toString()
            "mode" -> exposureModeLabels[v.toInt()] ?: "0x${v.toString(16)}"
            "iso_auto", "iso_auto2" -> nikonIsoAutoLabels[v.toInt()] ?: v.toString()
            "iso_auto_hi" -> "档位 $v"
            "long_nr", "high_nr", "exp_delay" -> onOffLabels[v.toInt()] ?: v.toString()
            "picture_style" -> canonPictureStyleLabels[v.toInt()] ?: v.toString()
            "quality" -> if (code == Ptp.DPC_COMPRESSION) nikonQualityLabels[v.toInt()] ?: v.toString() else v.toString()
            "image_size" -> if (dataType == Ptp.DATA_TYPE_STRING) String(raw, Charsets.UTF_16LE).trimEnd('\u0000') else v.toString()
            else -> v.toString()
        }
    }
}
