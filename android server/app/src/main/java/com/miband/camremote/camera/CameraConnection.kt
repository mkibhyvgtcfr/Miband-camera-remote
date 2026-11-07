package com.miband.camremote.camera

import kotlinx.coroutines.flow.Flow

interface CameraConnection {
    val endpoint: CameraEndpoint
    val transportLabel: String

    suspend fun connect(): CameraIdentity
    suspend fun disconnect()
    suspend fun refresh(): CameraSnapshot
    suspend fun updateProperty(property: CameraProperty, option: PropertyOption)
    suspend fun capture(withAutofocus: Boolean = true): CaptureInfo
    suspend fun focus(): Boolean
    suspend fun startLiveView(): Boolean
    suspend fun stopLiveView()
    suspend fun liveViewFrame(): ByteArray?
    suspend fun setRecording(recording: Boolean): Boolean
    suspend fun listMedia(limit: Int = 40): List<CameraMedia>
    suspend fun loadThumbnail(media: CameraMedia): ByteArray?
    suspend fun download(media: CameraMedia): ByteArray
    fun closeQuietly()
}
