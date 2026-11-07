package com.miband.camremote

import android.app.Application
import com.miband.camremote.band.WearableBridge
import com.miband.camremote.data.CameraRepository
import com.miband.camremote.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MiBandCameraApp : Application() {
    lateinit var repository: CameraRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var wearableBridge: WearableBridge
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = CameraRepository(this, settings)
        wearableBridge = WearableBridge(this, repository, appScope)
        // The bridge is started from MainActivity after the first screen is
        // visible. Xiaomi Wear shows its authorization sheet for an Activity,
        // not for an Application context.
    }

    override fun onTerminate() {
        wearableBridge.stop()
        repository.shutdown()
        super.onTerminate()
    }
}
