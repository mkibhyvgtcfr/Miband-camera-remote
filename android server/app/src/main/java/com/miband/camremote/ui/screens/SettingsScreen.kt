package com.miband.camremote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.miband.camremote.BuildConfig
import com.miband.camremote.data.BandLinkState
import com.miband.camremote.ui.CameraViewModel
import com.miband.camremote.ui.components.LensMark
import com.miband.camremote.ui.components.MiCard
import com.miband.camremote.ui.components.SectionHeading
import com.miband.camremote.ui.theme.MiGreen
import com.miband.camremote.ui.theme.MiOrange

@Composable
fun SettingsScreen(
    vm: CameraViewModel,
    band: BandLinkState,
    darkTheme: Boolean,
    onDarkTheme: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    var grid by remember { mutableStateOf(vm.settingsStore.gridOverlay) }
    var keepScreenOn by remember { mutableStateOf(vm.settingsStore.keepScreenOn) }
    var autoDownload by remember { mutableStateOf(vm.settingsStore.autoDownloadCapture) }
    var bandEnabled by remember { mutableStateOf(vm.settingsStore.bandEnabled) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PREFERENCES", style = MaterialTheme.typography.labelSmall, color = MiOrange)
                Text("设置", style = MaterialTheme.typography.displayMedium)
            }
            LensMark(size = 44.dp)
        }
        Text(
            "为取景和手环联动调整体验。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeading("外观与取景")
        MiCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow(
                    icon = if (darkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    title = "深色界面",
                    subtitle = "HyperOS 风格深色取景",
                    checked = darkTheme,
                ) {
                    onDarkTheme(it)
                    vm.saveDarkTheme(it)
                }
                HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(
                    icon = Icons.Rounded.GridOn,
                    title = "取景网格",
                    subtitle = "三分法辅助线",
                    checked = grid,
                ) {
                    grid = it
                    vm.saveGrid(it)
                }
                HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(
                    icon = Icons.Rounded.ScreenLockPortrait,
                    title = "保持屏幕常亮",
                    subtitle = "拍摄时不自动熄屏",
                    checked = keepScreenOn,
                ) {
                    keepScreenOn = it
                    vm.saveKeepScreenOn(it)
                    onKeepScreenOn(it)
                }
                HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "拍摄后自动下载 JPEG",
                    subtitle = "从存储卡取回刚拍的照片",
                    checked = autoDownload,
                ) {
                    autoDownload = it
                    vm.saveAutoDownload(it)
                }
            }
        }

        SectionHeading("小米手环")
        MiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                            .background(if (band.connected) MiOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Bluetooth, null, Modifier.size(22.dp), tint = if (band.connected) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(band.nodeName ?: "小米手环 10", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (band.connected) "官方 interconnect 通道已连接" else band.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (band.connected) MiGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = bandEnabled, onCheckedChange = {
                        bandEnabled = it
                        vm.setBandEnabled(it)
                    })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { vm.restartBand() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Text("重新检测", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "手环端需安装同一签名与包名的「米环相机」应用，并在小米运动健康中完成连接授权。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionHeading("关于")
        MiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LensMark(size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("米环相机", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME} · 手机服务端",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.Info, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "兼容 ISO 15740 PTP、PTP/IP、Canon CCAPI 与 Sony Camera Remote API。支持 Nikon、Canon、Sony、Panasonic 等相机的连接、实时取景、快门、对焦、曝光参数、相册与下载。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "开源组件：libpict (Apache-2.0) 协议参考、OkHttp (Apache-2.0)、Coil (Apache-2.0)、Xiaomi Wearable SDK。本应用不收集账号或影像数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

