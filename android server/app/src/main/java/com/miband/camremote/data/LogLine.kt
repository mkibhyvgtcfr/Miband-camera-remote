package com.miband.camremote.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogLine(
    val time: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
    val message: String,
    val level: LogLevel = LogLevel.INFO,
)

enum class LogLevel { INFO, SUCCESS, WARN, ERROR }
