package com.miband.camremote

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.miband.camremote.ui.AppRoot
import com.miband.camremote.ui.CameraViewModel

class MainActivity : ComponentActivity() {
    private val vm: CameraViewModel by viewModels()
    private var pendingUsbDevice: UsbDevice? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: pendingUsbDevice
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            pendingUsbDevice = null
            if (granted && device != null) {
                vm.connectUsb(device)
            } else {
                Toast.makeText(this@MainActivity, "未获得 USB 相机访问权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var bandBridgeStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppRoot(vm = vm, onUsbDeviceSelected = { requestUsbPermission(it) })
        }
        startBandBridgeWhenVisible()
        handleUsbIntent(intent)
    }

    private fun startBandBridgeWhenVisible() {
        if (bandBridgeStarted) return
        bandBridgeStarted = true
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                val app = application as MiBandCameraApp
                if (app.settings.bandEnabled) {
                    app.wearableBridge.start(this)
                }
            }
        }, 900)
    }

    override fun onResume() {
        super.onResume()
        if (bandBridgeStarted) {
            (application as MiBandCameraApp).wearableBridge.bindActivity(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(usbReceiver) }
        super.onDestroy()
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
        requestUsbPermission(device)
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (manager.hasPermission(device)) {
            vm.connectUsb(device)
            return
        }
        pendingUsbDevice = device
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val permissionIntent = PendingIntent.getBroadcast(
            this,
            41,
            Intent(ACTION_USB_PERMISSION).setPackage(packageName),
            flags,
        )
        manager.requestPermission(device, permissionIntent)
        Toast.makeText(this, "请在系统弹窗中允许访问 USB 相机", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.miband.camremote.USB_PERMISSION"
    }
}
