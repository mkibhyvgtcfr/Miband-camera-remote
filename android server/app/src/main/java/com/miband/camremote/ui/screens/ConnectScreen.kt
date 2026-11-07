package com.miband.camremote.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miband.camremote.camera.CameraSnapshot
import com.miband.camremote.camera.LinkState
import com.miband.camremote.data.AppEvent
import com.miband.camremote.data.BandLinkState
import com.miband.camremote.data.LogLine
import com.miband.camremote.data.LogLevel
import com.miband.camremote.data.UsbDeviceItem
import com.miband.camremote.ui.CameraViewModel
import com.miband.camremote.ui.components.BandPill
import com.miband.camremote.ui.components.BatteryPill
import com.miband.camremote.ui.components.GhostButton
import com.miband.camremote.ui.components.LensMark
import com.miband.camremote.ui.components.MiCard
import com.miband.camremote.ui.components.PrimaryButton
import com.miband.camremote.ui.components.ScreenHeader
import com.miband.camremote.ui.components.SectionHeading
import com.miband.camremote.ui.components.StatusPill
import com.miband.camremote.ui.theme.MiGreen
import com.miband.camremote.ui.theme.MiOrange

private enum class ConnectMethod { USB, NETWORK, DEMO }
private enum class NetworkProtocol(val label: String, val defaultPort: Int, val hint: String) {
    NIKON("尼康 PTP/IP", 15740, "相机 Wi-Fi 打开后连接相机热点，IP 通常为 192.168.1.1"),
    CANON("佳能 CCAPI", 8080, "在相机菜单开启「Wi-Fi / Camera Connect」，IP 见相机网络信息"),
    SONY("索尼 API", 10000, "开启相机 Wi-Fi 与「使用智能手机控制」，连接相机热点"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    vm: CameraViewModel,
    snapshot: CameraSnapshot,
    band: BandLinkState,
    logs: List<LogLine>,
    onOpenCapture: () -> Unit,
    onUsbDeviceSelected: (android.hardware.usb.UsbDevice) -> Unit,
) {
    var method by remember { mutableStateOf(ConnectMethod.NETWORK) }
    var protocol by remember { mutableStateOf(NetworkProtocol.NIKON) }
    var host by remember { mutableStateOf(vm.settingsStore.lastHost.ifBlank { "192.168.1.1" }) }
    var port by remember { mutableStateOf(vm.settingsStore.lastPort.toString().ifBlank { "15740" }) }
    var devices by remember { mutableStateOf<List<UsbDeviceItem>>(emptyList()) }
    var scanTick by remember { mutableIntStateOf(0) }
    var showLogs by remember { mutableStateOf(false) }
    var bandSwitch by remember { mutableStateOf(vm.settingsStore.bandEnabled) }

    LaunchedEffect(scanTick) {
        devices = runCatching { vm.repository.scanUsb() }.getOrDefault(emptyList())
    }
    LaunchedEffect(protocol) {
        port = protocol.defaultPort.toString()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LensMark(size = 38.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("MIBAND CAMERA", style = MaterialTheme.typography.labelSmall, color = MiOrange)
                Text("米环相机", style = MaterialTheme.typography.titleMedium)
            }
            BandPill(band.connected)
        }
        ScreenHeader(
            eyebrow = "CAMERA REMOTE",
            title = if (snapshot.isConnected) "相机已就位" else "让手腕\n成为快门",
            subtitle = if (snapshot.isConnected) {
                "手机与手环都可独立控制。"
            } else {
                "连接有线或无线相机，在手机与小米手环之间同步曝光与快门。"
            },
        )
        ConnectionHero(
            snapshot = snapshot,
            onOpenCapture = onOpenCapture,
            onDisconnect = { vm.disconnect() },
        )

        SectionHeading("连接方式", "USB · Wi-Fi")
        MethodSelector(method) { method = it }

        when (method) {
            ConnectMethod.USB -> UsbPanel(
                devices = devices,
                onRescan = { scanTick++ },
                onSelect = onUsbDeviceSelected,
            )
            ConnectMethod.NETWORK -> NetworkPanel(
                protocol = protocol,
                host = host,
                port = port,
                onProtocol = { protocol = it },
                onHost = { host = it },
                onPort = { port = it.filter { c -> c.isDigit() } },
                onConnect = {
                    val p = port.toIntOrNull() ?: protocol.defaultPort
                    when (protocol) {
                        NetworkProtocol.NIKON -> vm.connectPtpIp(host.trim(), p)
                        NetworkProtocol.CANON -> vm.connectCanon(host.trim(), p)
                        NetworkProtocol.SONY -> vm.connectSony(host.trim(), p)
                    }
                },
            )
            ConnectMethod.DEMO -> DemoPanel(
                onStart = { vm.connectVirtual() },
                active = snapshot.identity?.brand?.displayName == "演示相机",
            )
        }

        SectionHeading("手环通道", if (band.connected) "已连接" else "待连接")
        MiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (band.connected) MiOrange.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Bluetooth, null, Modifier.size(24.dp), tint = if (band.connected) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            band.nodeName ?: "小米手环 10",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                band.connected -> "消息通道已建立 · 可控制快门与参数"
                                band.authorized -> "已授权，正在建立通道"
                                else -> band.message
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bandSwitch,
                        onCheckedChange = {
                            bandSwitch = it
                            vm.setBandEnabled(it)
                        },
                    )
                }
                if (!band.connected) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "请先在「小米运动健康」中连接手环，并安装米环相机手环端应用。若未出现授权页，请点下方按钮主动请求授权。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    val activity = LocalContext.current as? Activity
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            "请求手环授权",
                            { activity?.let { vm.requestBandAuthorization(it) } },
                            Modifier.weight(1f),
                            enabled = activity != null,
                            icon = {
                                Icon(Icons.Rounded.Bluetooth, null, Modifier.size(17.dp), tint = Color.White)
                            },
                        )
                        GhostButton("重新检测", { vm.restartBand() }, Modifier.weight(0.7f), icon = {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "若小米运动健康没有弹出授权页：先打开手环上的「米环相机」应用，再回到这里点“请求手环授权”。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionHeading("连接日志", "最近 ${logs.size} 条")
        MiCard(Modifier.fillMaxWidth(), onClick = { showLogs = true }) {
            Column(Modifier.padding(17.dp)) {
                if (logs.isEmpty()) {
                    Text("尚无日志。连接相机后会在这里显示协议握手详情。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    logs.take(4).forEach { log ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(
                                        when (log.level) {
                                            LogLevel.SUCCESS -> MiGreen
                                            LogLevel.WARN -> Color(0xFFE8A33D)
                                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                            LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(log.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(log.message, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "支持 Nikon / Canon / Sony / Panasonic 的 PTP 与网络协议；具体功能取决于相机型号。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(30.dp))
    }

    if (showLogs) {
        ModalBottomSheet(
            onDismissRequest = { showLogs = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(bottom = 34.dp)
            ) {
                Text("连接日志", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                if (logs.isEmpty()) Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                logs.forEach { log ->
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Text(log.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(log.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionHero(snapshot: CameraSnapshot, onOpenCapture: () -> Unit, onDisconnect: () -> Unit) {
    val connected = snapshot.isConnected
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF17191D), Color(0xFF24150A), Color(0xFF0E0F11))
                )
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(MiOrange.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.12f),
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.85f, size.height * 0.12f),
            )
        }
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = when (snapshot.linkState) {
                        LinkState.CONNECTED -> "在线"
                        LinkState.CONNECTING -> "连接中"
                        LinkState.ERROR -> "连接失败"
                        else -> "未连接"
                    },
                    active = connected,
                    modifier = Modifier,
                )
                Spacer(Modifier.width(8.dp))
                if (connected) BatteryPill(snapshot.batteryPercent)
                Spacer(Modifier.weight(1f))
                if (connected) {
                    Icon(
                        Icons.Rounded.CameraAlt, null,
                        Modifier.size(27.dp), tint = Color.White.copy(alpha = 0.85f)
                    )
                } else {
                    LensMark(size = 48.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                snapshot.identity?.model ?: "等待相机",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                snapshot.identity?.let { "${it.brand.displayName} · ${snapshot.endpointLabel}" }
                    ?: "USB / Wi-Fi / 演示模式",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("ISO", snapshot.property("iso")?.valueLabel ?: "—", Modifier.weight(1f))
                HeroStat("快门", snapshot.property("shutter")?.valueLabel ?: "—", Modifier.weight(1f))
                HeroStat("光圈", snapshot.property("aperture")?.valueLabel ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("进入拍摄", onOpenCapture, Modifier.weight(1f), icon = {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Color.White)
                    })
                    GhostButton("断开", onDisconnect, Modifier.weight(0.55f))
                }
            } else {
                Text(
                    snapshot.error ?: snapshot.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (snapshot.error != null) Color(0xFFFF9A8F) else Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f))
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MethodSelector(selected: ConnectMethod, onSelect: (ConnectMethod) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        ConnectMethod.entries.forEach { method ->
            val active = selected == method
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(method) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (method) {
                        ConnectMethod.USB -> Icons.Rounded.Usb
                        ConnectMethod.NETWORK -> Icons.Rounded.Wifi
                        ConnectMethod.DEMO -> Icons.Rounded.PlayArrow
                    },
                    null,
                    Modifier.size(17.dp),
                    tint = if (active) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    when (method) {
                        ConnectMethod.USB -> "USB"
                        ConnectMethod.NETWORK -> "无线"
                        ConnectMethod.DEMO -> "演示"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsbPanel(devices: List<UsbDeviceItem>, onRescan: () -> Unit, onSelect: (android.hardware.usb.UsbDevice) -> Unit) {
    MiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("USB OTG 相机", style = MaterialTheme.typography.titleMedium)
                    Text("相机需设置为 PTP / MTP 连接模式", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.Rounded.Refresh, "扫描",
                    Modifier.size(38.dp).clip(CircleShape).clickable { onRescan() }.padding(9.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "若提示读取超时：将相机 USB 模式改为 PTP/MTP，退出其他相机 App，并确认 OTG 线支持数据传输。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (devices.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Usb, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("未发现 USB 相机", style = MaterialTheme.typography.titleMedium)
                        Text("请用 OTG 线连接相机并点击右上角刷新", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                devices.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(item.device) }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MiOrange.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, Modifier.size(21.dp), tint = MiOrange)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${item.vendorName} · ${if (item.hasPermission) "已授权" else "点击授权连接"} · " +
                                    if (item.ptpCapable) "PTP 可用" else "非 PTP 模式",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.ptpCapable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (item.hasPermission) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(21.dp), tint = MiGreen)
                        } else {
                            Icon(Icons.Rounded.Link, null, Modifier.size(21.dp), tint = MiOrange)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun NetworkPanel(
    protocol: NetworkProtocol,
    host: String,
    port: String,
    onProtocol: (NetworkProtocol) -> Unit,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onConnect: () -> Unit,
) {
    MiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("选择协议", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NetworkProtocol.entries.forEach { p ->
                    val active = p == protocol
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (active) MiOrange.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (active) MiOrange.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onProtocol(p) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            p.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = host,
                onValueChange = onHost,
                label = { Text("相机 IP 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = port,
                onValueChange = onPort,
                label = { Text("端口") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(10.dp))
            Text(protocol.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("连接相机", onConnect, Modifier.fillMaxWidth(), icon = {
                Icon(Icons.Rounded.Wifi, null, Modifier.size(18.dp), tint = Color.White)
            })
        }
    }
}

@Composable
private fun DemoPanel(onStart: () -> Unit, active: Boolean) {
    MiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LensMark(size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("虚拟演示相机", style = MaterialTheme.typography.titleMedium)
                    Text("无需硬件，体验完整取景、快门与手环联动。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (active) "演示相机已连接" else "进入演示模式", onStart, Modifier.fillMaxWidth(), enabled = !active, icon = {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = Color.White)
            })
        }
    }
}
