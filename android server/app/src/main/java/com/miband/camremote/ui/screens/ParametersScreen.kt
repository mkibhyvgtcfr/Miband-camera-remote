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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miband.camremote.camera.CameraProperty
import com.miband.camremote.camera.CameraSnapshot
import com.miband.camremote.camera.PropertyGroup
import com.miband.camremote.ui.CameraViewModel
import com.miband.camremote.ui.components.EmptyConnection
import com.miband.camremote.ui.components.MiCard
import com.miband.camremote.ui.components.PropertyPickerSheet
import com.miband.camremote.ui.components.PropertyRow
import com.miband.camremote.ui.components.ScreenHeader
import com.miband.camremote.ui.components.SectionHeading
import com.miband.camremote.ui.theme.MiOrange

@Composable
fun ParametersScreen(
    vm: CameraViewModel,
    snapshot: CameraSnapshot,
    onOpenConnect: () -> Unit,
) {
    if (!snapshot.isConnected) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyConnection(
                title = "参数等待相机",
                body = "连接后可读取并调整 ISO、快门、光圈、白平衡与更多相机设置。",
                onConnect = onOpenConnect,
            )
        }
        return
    }

    var selected by remember { mutableStateOf<CameraProperty?>(null) }
    val primary = listOf("iso", "shutter", "aperture", "ev").mapNotNull { snapshot.property(it) }
    val mode = snapshot.property("mode")
    val others = snapshot.properties.filter {
        it.key !in setOf("iso", "shutter", "aperture", "ev", "mode")
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
            Column(Modifier.weight(1f)) {
                Text("CAMERA SETTINGS", style = MaterialTheme.typography.labelSmall, color = MiOrange)
                Text("参数", style = MaterialTheme.typography.displayMedium)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.refresh() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(
            "${snapshot.identity?.title ?: "相机"} · ${snapshot.endpointLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        mode?.let { m ->
            SectionHeading("拍摄模式", if (m.selectable) "点击切换" else "由机身拨盘决定")
            MiCard(
                Modifier.fillMaxWidth(),
                onClick = if (m.selectable) ({ selected = m }) else null,
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("曝光模式", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (m.selectable) "P / A / S / M · 与机身同步"
                            else "当前相机不支持通过 USB 改变拍摄模式",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(m.valueLabel, style = MaterialTheme.typography.displayMedium, color = MiOrange)
                    if (m.selectable) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionHeading("曝光三角", "点击调整")
        MiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                primary.filter { it.key != "ev" }.forEachIndexed { index, property ->
                    ExposureControl(
                        property = property,
                        onSelect = { selected = property },
                        onAdjust = { dir -> vm.adjust(property.key, dir) },
                    )
                    if (index < primary.filter { it.key != "ev" }.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        primary.firstOrNull { it.key == "ev" }?.let { ev ->
            Spacer(Modifier.height(10.dp))
            MiCard(Modifier.fillMaxWidth(), onClick = { selected = ev }) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("曝光补偿", style = MaterialTheme.typography.titleMedium)
                        Text("以 1/3 档调整画面明暗", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(ev.valueLabel, style = MaterialTheme.typography.headlineMedium, color = MiOrange)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        PropertyGroup.entries.forEach { group ->
            val props = others.filter { it.group == group }
            if (props.isNotEmpty()) {
                SectionHeading(group.title, "${props.size} 项")
                MiCard(Modifier.fillMaxWidth()) {
                    Column {
                        props.forEachIndexed { index, property ->
                            PropertyRow(property, onClick = { selected = property })
                            if (index < props.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "参数由相机实时提供。灰色项为只读信息；相机在拍摄或回放时可能暂时锁定部分设置。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(30.dp))
    }

    selected?.let { property ->
        PropertyPickerSheet(
            property = property,
            onDismiss = { selected = null },
            onSelect = { option ->
                vm.setProperty(property, option)
                selected = null
            },
        )
    }
}

@Composable
private fun ExposureControl(property: CameraProperty, onSelect: () -> Unit, onAdjust: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSelect() }.padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(property.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                property.valueLabel,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AdjustButton(Icons.Rounded.ChevronLeft, "降低") { onAdjust(-1) }
        Spacer(Modifier.width(7.dp))
        AdjustButton(Icons.Rounded.ChevronRight, "提高") { onAdjust(1) }
    }
}

@Composable
private fun AdjustButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
