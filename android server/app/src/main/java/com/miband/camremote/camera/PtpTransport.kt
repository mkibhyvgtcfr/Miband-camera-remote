package com.miband.camremote.camera

data class PtpResult(
    val responseCode: Int,
    val responseParams: IntArray = IntArray(0),
    val data: ByteArray? = null,
) {
    val isOk: Boolean get() = responseCode == Ptp.RC_OK
    fun requireOk() {
        if (!isOk) throw PtpException(responseCode, "PTP 操作失败：0x%04x".format(responseCode))
    }
}

class PtpException(val responseCode: Int, message: String) : Exception(message)

interface PtpTransport {
    val description: String

    suspend fun open()
    fun close()
    suspend fun transact(
        code: Int,
        params: IntArray = IntArray(0),
        dataOut: ByteArray? = null,
        transactionId: Int? = null,
        timeoutMs: Int = 15_000,
    ): PtpResult
}

object PtpErrors {
    fun friendly(code: Int): String = when (code) {
        Ptp.RC_OK -> "成功"
        Ptp.RC_GENERAL_ERROR -> "相机返回一般错误"
        Ptp.RC_SESSION_NOT_OPEN -> "相机会话未打开"
        Ptp.RC_OPERATION_NOT_SUPPORTED -> "相机不支持此操作"
        Ptp.RC_PARAMETER_NOT_SUPPORTED -> "相机不支持此参数"
        Ptp.RC_DEVICE_PROP_NOT_SUPPORTED -> "相机不支持此设置"
        Ptp.RC_ACCESS_DENIED -> "相机拒绝访问，请检查是否被其他 App 占用"
        Ptp.RC_DEVICE_BUSY -> "相机忙，请稍后再试"
        Ptp.RC_INVALID_DEVICE_PROP_VALUE -> "相机不接受此参数值"
        Ptp.RC_SESSION_ALREADY_OPEN -> "相机会话已打开"
        else -> "相机错误 0x%04x".format(code)
    }
}
