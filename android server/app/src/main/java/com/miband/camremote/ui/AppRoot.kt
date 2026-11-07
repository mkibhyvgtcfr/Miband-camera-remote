package com.miband.camremote.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miband.camremote.ui.screens.CaptureScreen
import com.miband.camremote.ui.screens.ConnectScreen
import com.miband.camremote.ui.screens.MediaScreen
import com.miband.camremote.ui.screens.ParametersScreen
import com.miband.camremote.ui.screens.SettingsScreen
import com.miband.camremote.ui.theme.MiBandCameraTheme
import com.miband.camremote.ui.theme.MiOrange

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("connect", "连接", Icons.Rounded.Link),
    Dest("capture", "拍摄", Icons.Rounded.CameraAlt),
    Dest("parameters", "参数", Icons.Rounded.Tune),
    Dest("media", "影像", Icons.Rounded.PhotoLibrary),
    Dest("settings", "设置", Icons.Rounded.Settings),
)

@Composable
fun AppRoot(
    vm: CameraViewModel,
    onUsbDeviceSelected: (android.hardware.usb.UsbDevice) -> Unit,
) {
    var darkTheme by remember { mutableStateOf(vm.settingsStore.darkTheme) }
    var keepScreenOn by remember { mutableStateOf(vm.settingsStore.keepScreenOn) }
    MiBandCameraTheme(darkTheme = darkTheme) {
        val snapshot by vm.snapshot.collectAsStateWithLifecycle()
        val band by vm.band.collectAsStateWithLifecycle()
        val logs by vm.logs.collectAsStateWithLifecycle()
        val liveBitmap by vm.liveBitmap.collectAsStateWithLifecycle()
        val flashTick by vm.flash.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(vm) {
            vm.messages.collect { snackbar.showSnackbar(it) }
        }

        val view = LocalView.current
        DisposableEffect(keepScreenOn) {
            val window = (view.context as? Activity)?.window
            if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbar, Modifier.padding(bottom = 86.dp))
            },
            bottomBar = {
                MiBottomBar(nav)
            },
        ) { _ ->
            NavHost(
                navController = nav,
                startDestination = "connect",
                modifier = Modifier.fillMaxSize().padding(bottom = 72.dp),
            ) {
                composable("connect") {
                    ConnectScreen(
                        vm = vm,
                        snapshot = snapshot,
                        band = band,
                        logs = logs,
                        onOpenCapture = { nav.navigate("capture") { launchSingleTop = true } },
                        onUsbDeviceSelected = onUsbDeviceSelected,
                    )
                }
                composable("capture") {
                    CaptureScreen(
                        vm = vm,
                        snapshot = snapshot,
                        band = band,
                        liveBitmap = liveBitmap,
                        flashTick = flashTick,
                        onOpenConnect = { nav.navigate("connect") { launchSingleTop = true } },
                        onOpenMedia = { nav.navigate("media") { launchSingleTop = true } },
                    )
                }
                composable("parameters") {
                    ParametersScreen(
                        vm = vm,
                        snapshot = snapshot,
                        onOpenConnect = { nav.navigate("connect") { launchSingleTop = true } },
                    )
                }
                composable("media") {
                    MediaScreen(
                        vm = vm,
                        snapshot = snapshot,
                        onOpenConnect = { nav.navigate("connect") { launchSingleTop = true } },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        vm = vm,
                        band = band,
                        darkTheme = darkTheme,
                        onDarkTheme = { darkTheme = it },
                        onKeepScreenOn = { keepScreenOn = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiBottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: "connect"
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { dest ->
                val active = current == dest.route
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            if (current != dest.route) {
                                nav.navigate(dest.route) {
                                    launchSingleTop = true
                                    popUpTo("connect") { inclusive = false }
                                }
                            }
                        }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(38.dp, 28.dp)
                            .clip(CircleShape)
                            .background(if (active) MiOrange.copy(alpha = 0.13f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            dest.icon,
                            dest.label,
                            Modifier.size(21.dp),
                            tint = if (active) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dest.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
