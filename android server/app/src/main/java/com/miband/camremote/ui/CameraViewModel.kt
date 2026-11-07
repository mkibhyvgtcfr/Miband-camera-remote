package com.miband.camremote.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miband.camremote.MiBandCameraApp
import com.miband.camremote.camera.CameraMedia
import com.miband.camremote.camera.CameraProperty
import com.miband.camremote.camera.PropertyOption
import com.miband.camremote.data.AppEvent
import com.miband.camremote.data.BandLinkState
import com.miband.camremote.data.CameraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MiBandCameraApp
    val repository: CameraRepository = app.repository
    val settingsStore = app.settings

    val snapshot = repository.snapshot
    val band = repository.band
    val logs = repository.logs
    val interval = repository.interval

    private val _liveBitmap = MutableStateFlow<ImageBitmap?>(null)
    val liveBitmap = _liveBitmap.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    private val _flash = MutableStateFlow(0L)
    val flash = _flash.asStateFlow()

    init {
        viewModelScope.launch {
            repository.liveFrame.collectLatest { bytes ->
                val bitmap = withContext(Dispatchers.Default) {
                    runCatching {
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                            inSampleSize = 1
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                    }.getOrNull()
                }
                _liveBitmap.value = bitmap
            }
        }
        viewModelScope.launch {
            repository.events.collect { event ->
                when (event) {
                    is AppEvent.Message -> _messages.emit(event.text)
                    is AppEvent.Flash -> _flash.value = _flash.value + 1
                }
            }
        }
    }

    fun connectUsb(device: android.hardware.usb.UsbDevice) = viewModelScope.launch {
        runCatching { repository.connectUsb(device) }
            .onFailure { _messages.tryEmit(it.message ?: "USB 连接失败") }
    }

    fun connectPtpIp(host: String, port: Int) = viewModelScope.launch {
        runCatching { repository.connectPtpIp(host, port) }
            .onFailure { _messages.tryEmit(it.message ?: "连接失败") }
    }

    fun connectCanon(host: String, port: Int) = viewModelScope.launch {
        runCatching { repository.connectCanon(host, port) }
            .onFailure { _messages.tryEmit(it.message ?: "连接失败") }
    }

    fun connectSony(host: String, port: Int) = viewModelScope.launch {
        runCatching { repository.connectSony(host, port) }
            .onFailure { _messages.tryEmit(it.message ?: "连接失败") }
    }

    fun connectVirtual() = viewModelScope.launch {
        runCatching { repository.connectVirtual() }
            .onFailure { _messages.tryEmit(it.message ?: "连接失败") }
    }

    fun disconnect() = viewModelScope.launch {
        runCatching { repository.disconnect() }
        _liveBitmap.value = null
    }

    fun capture() = viewModelScope.launch { runCatching { repository.capture() } }
    fun captureDelayed(seconds: Int) = viewModelScope.launch {
        if (seconds > 0) {
            _messages.tryEmit("定时拍摄：${seconds} 秒后释放快门")
            delay(seconds * 1000L)
        }
        runCatching { repository.capture() }
    }

    fun startInterval(count: Int, seconds: Int) {
        if (repository.startInterval(count, seconds)) {
            _messages.tryEmit("间隔拍摄已开始")
        } else {
            _messages.tryEmit("请先连接相机，或等待当前间隔任务结束")
        }
    }

    fun stopInterval() = repository.stopInterval()

    fun focus() = viewModelScope.launch { runCatching { repository.focus() } }
    fun refresh() = viewModelScope.launch { runCatching { repository.refreshProperties() } }
    fun refreshMedia() = viewModelScope.launch { runCatching { repository.refreshMedia() } }
    fun download(media: CameraMedia) = viewModelScope.launch {
        runCatching { repository.download(media) }
    }

    fun startLiveView() = viewModelScope.launch { runCatching { repository.startLiveView() } }
    fun stopLiveView() = viewModelScope.launch {
        runCatching { repository.stopLiveView() }
        _liveBitmap.value = null
    }

    fun toggleLiveView() {
        if (snapshot.value.liveViewActive) stopLiveView() else startLiveView()
    }

    fun setProperty(property: CameraProperty, option: PropertyOption) = viewModelScope.launch {
        runCatching { repository.setProperty(property, option) }
    }

    fun adjust(key: String, direction: Int) = viewModelScope.launch {
        runCatching { repository.adjustExposure(key, direction) }
    }

    fun toggleRecording() = viewModelScope.launch {
        runCatching { repository.setRecording(!snapshot.value.recording) }
    }

    fun saveDarkTheme(value: Boolean) {
        settingsStore.darkTheme = value
    }

    fun saveGrid(value: Boolean) {
        settingsStore.gridOverlay = value
    }

    fun saveKeepScreenOn(value: Boolean) {
        settingsStore.keepScreenOn = value
    }

    fun saveAutoDownload(value: Boolean) {
        settingsStore.autoDownloadCapture = value
    }

    fun setBandEnabled(value: Boolean) {
        settingsStore.bandEnabled = value
        if (value) {
            restartBand()
        } else {
            app.wearableBridge.stop()
            repository.updateBand(BandLinkState(message = "手环连接已关闭"))
        }
    }

    fun restartBand() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                app.wearableBridge.stop()
                app.wearableBridge.start()
            }.onFailure { _messages.tryEmit(it.message ?: "手环重连失败") }
        }
    }

    fun requestBandAuthorization(activity: android.app.Activity) {
        runCatching { app.wearableBridge.authorize(activity) }
            .onFailure { _messages.tryEmit(it.message ?: "无法打开手环授权") }
    }
}
