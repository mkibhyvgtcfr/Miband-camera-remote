package com.miband.camremote.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miband.camremote.camera.CameraProperty
import com.miband.camremote.camera.CameraSnapshot
import com.miband.camremote.camera.exposureSummary
import com.miband.camremote.data.BandLinkState
import com.miband.camremote.ui.CameraViewModel
import com.miband.camremote.ui.components.BandPill
import com.miband.camremote.ui.components.BatteryPill
import com.miband.camremote.ui.components.ByteImage
import com.miband.camremote.ui.components.EmptyConnection
import com.miband.camremote.ui.components.LensMark
import com.miband.camremote.ui.components.MiCard
import com.miband.camremote.ui.components.PrimaryButton
import com.miband.camremote.ui.components.PropertyPickerSheet
import com.miband.camremote.ui.components.StatusPill
import com.miband.camremote.ui.theme.MiOrange
import com.miband.camremote.ui.theme.MiRed
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    vm: CameraViewModel,
    snapshot: CameraSnapshot,
    band: BandLinkState,
    liveBitmap: ImageBitmap?,
    flashTick: Long,
    onOpenConnect: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    if (!snapshot.isConnected) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyConnection(
                title = "还没有相机",
                body = "连接 USB 相机或相机 Wi-Fi，再回到这里开始取景。手环可独立触发快门。",
                onConnect = onOpenConnect,
            )
        }
        return
    }

    val haptic = LocalHapticFeedback.current
    var selectedProperty by remember { mutableStateOf<CameraProperty?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(vm.settingsStore.gridOverlay) }
    var showIntervalSheet by remember { mutableStateOf(false) }
    val interval by vm.interval.collectAsStateWithLifecycle()

    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            flashOn = true
            delay(110)
            flashOn = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("VIEWFINDER", style = MaterialTheme.typography.labelSmall, color = MiOrange)
                Text(
                    snapshot.identity?.model ?: "相机",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BandPill(band.connected)
            Spacer(Modifier.width(6.dp))
            BatteryPill(snapshot.batteryPercent)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3.2f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF050505))
                .clickable {
                    if (!snapshot.liveViewActive) vm.startLiveView()
                }
        ) {
            if (liveBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = liveBitmap,
                    contentDescription = "实时取景",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                PlaceholderViewfinder(snapshot.liveViewActive, snapshot.liveViewAvailable)
            }
            if (showGrid) ViewfinderGrid()
            FocusReticle()
            Row(
                Modifier.align(Alignment.TopStart).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (snapshot.liveViewActive) {
                    StatusPill("LIVE", true)
                } else {
                    Box(
                        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("取景未开启", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f)) }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f))
                        .clickable {
                            if (snapshot.liveViewActive) vm.stopLiveView() else vm.startLiveView()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (snapshot.liveViewActive) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        "实时取景",
                        Modifier.size(19.dp),
                        tint = Color.White,
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(15.dp)) {
                Text(
                    snapshot.exposureSummary(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Text(
                    "${snapshot.statusMessage} · ${snapshot.property("shots")?.valueLabel ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (snapshot.busy) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MiOrange, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("相机处理中", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
            if (flashOn) {
                Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.72f)))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(10.dp).clip(CircleShape)
                    .background(if (snapshot.recording) MiRed else Color.Transparent)
            )
        }

        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("iso", "shutter", "aperture").forEach { key ->
                val property = snapshot.property(key)
                ExposureCell(
                    label = property?.label ?: key.uppercase(),
                    value = property?.valueLabel ?: "—",
                    modifier = Modifier.weight(1f),
                    onClick = { if (property != null) selectedProperty = property },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenMedia() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val preview = snapshot.lastCapture?.media?.thumbnail ?: snapshot.lastCapture?.preview
                if (preview != null) {
                    ByteImage(preview, Modifier.fillMaxSize(), ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.PhotoLibrary, "相册", Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ControlButton(
                icon = { Icon(Icons.Rounded.CenterFocusStrong, "对焦", Modifier.size(27.dp), tint = MaterialTheme.colorScheme.onSurface) },
                label = "对焦",
                enabled = !snapshot.busy,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.focus()
                },
            )
            ShutterButton(busy = snapshot.busy) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.capture()
            }
            ControlButton(
                icon = {
                    Icon(
                        Icons.Rounded.Videocam,
                        "录像",
                        Modifier.size(27.dp),
                        tint = if (snapshot.recording) MiRed else MaterialTheme.colorScheme.onSurface,
                    )
                },
                label = if (snapshot.recording) "停止" else "录像",
                enabled = !snapshot.busy,
                onClick = { vm.toggleRecording() },
            )
            Column(
                Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        showGrid = !showGrid
                        vm.saveGrid(showGrid)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Bolt,
                    "网格",
                    Modifier.size(24.dp),
                    tint = if (showGrid) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        if (interval.active) {
            MiCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("间隔拍摄进行中", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${interval.taken} / ${interval.total} 张 · 每 ${interval.intervalSeconds} 秒",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.stopInterval() }) { Text("停止") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (interval.total <= 0) 0f else interval.taken.toFloat() / interval.total },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = MiOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("定时 2s", Modifier.weight(1f)) { vm.captureDelayed(2) }
                QuickAction("定时 10s", Modifier.weight(1f)) { vm.captureDelayed(10) }
                QuickAction("间隔拍摄", Modifier.weight(1f)) { showIntervalSheet = true }
            }
        }

        Spacer(Modifier.height(12.dp))
        MiCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("创作提示", style = MaterialTheme.typography.labelMedium, color = MiOrange)
                    Text(
                        if (snapshot.liveViewActive) "实时取景已开启 · 手环可同步调整曝光与触发快门"
                        else "轻触取景框开启实时取景；部分相机会在拍摄时自动关闭实时取景",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.CameraAlt, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    selectedProperty?.let { prop ->
        PropertyPickerSheet(
            property = prop,
            onDismiss = { selectedProperty = null },
            onSelect = { option ->
                vm.setProperty(prop, option)
                selectedProperty = null
            },
        )
    }
    if (showIntervalSheet) {
        IntervalSheet(
            onDismiss = { showIntervalSheet = false },
            onStart = { count, seconds -> vm.startInterval(count, seconds) },
        )
    }
}

@Composable
private fun PlaceholderViewfinder(live: Boolean, available: Boolean) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF101214), Color(0xFF1B1108), Color(0xFF070708)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(MiOrange.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.48f),
                    radius = size.width * 0.5f,
                ),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.5f, size.height * 0.48f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LensMark(size = 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    live -> "正在接收取景画面…"
                    available -> "轻触开启实时取景"
                    else -> "此相机暂不支持实时取景"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "仍可通过下方快门完成拍摄",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ViewfinderGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 1.dp.toPx()
        val c = Color.White.copy(alpha = 0.16f)
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), stroke)
            drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), stroke)
        }
        drawRect(Color.White.copy(alpha = 0.16f), style = Stroke(stroke))
    }
}

@Composable
private fun FocusReticle() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val s = w * 0.075f
        val c = Offset(w / 2f, h / 2f)
        val paint = Color.White.copy(alpha = 0.7f)
        val stroke = 1.4.dp.toPx()
        drawLine(paint, Offset(c.x - s, c.y), Offset(c.x - s * 0.35f, c.y), stroke)
        drawLine(paint, Offset(c.x + s * 0.35f, c.y), Offset(c.x + s, c.y), stroke)
        drawLine(paint, Offset(c.x, c.y - s), Offset(c.x, c.y - s * 0.35f), stroke)
        drawLine(paint, Offset(c.x, c.y + s * 0.35f), Offset(c.x, c.y + s), stroke)
        drawCircle(paint.copy(alpha = 0.35f), radius = s * 0.18f, center = c, style = Stroke(stroke))
    }
}

@Composable
private fun ExposureCell(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalSheet(onDismiss: () -> Unit, onStart: (Int, Int) -> Unit) {
    var count by remember { mutableIntStateOf(20) }
    var seconds by remember { mutableIntStateOf(5) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("间隔拍摄", style = MaterialTheme.typography.headlineMedium)
            Text(
                "按固定间隔自动释放快门，适合延时与长时间记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text("拍摄张数", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 50, 100).forEach { n ->
                    OptionPill("$n 张", n == count, Modifier.weight(1f)) { count = n }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("间隔时间", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10, 30).forEach { s ->
                    OptionPill("$s 秒", s == seconds, Modifier.weight(1f)) { seconds = s }
                }
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton("开始间隔拍摄", {
                onStart(count, seconds)
                onDismiss()
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OptionPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) MiOrange else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ControlButton(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShutterButton(busy: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(80.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !busy) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(if (busy) MaterialTheme.colorScheme.outline else MiOrange)
                    .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            )
        }
    }
}

