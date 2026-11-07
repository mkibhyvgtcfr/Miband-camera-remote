package com.miband.camremote.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miband.camremote.camera.CameraMedia
import com.miband.camremote.camera.CameraSnapshot
import com.miband.camremote.ui.CameraViewModel
import com.miband.camremote.ui.components.ByteImage
import com.miband.camremote.ui.components.EmptyConnection
import com.miband.camremote.ui.components.MiCard
import com.miband.camremote.ui.components.PrimaryButton
import com.miband.camremote.ui.components.SectionHeading
import com.miband.camremote.ui.theme.MiGreen
import com.miband.camremote.ui.theme.MiOrange

@Composable
fun MediaScreen(
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
                title = "影像还在相机里",
                body = "连接相机后可浏览存储卡，下载照片与视频，或查看刚刚拍摄的画面。",
                onConnect = onOpenConnect,
            )
        }
        return
    }

    val last = snapshot.lastCapture
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
                Text("CAMERA GALLERY", style = MaterialTheme.typography.labelSmall, color = MiOrange)
                Text("影像", style = MaterialTheme.typography.displayMedium)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.refreshMedia() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Refresh, "刷新相册", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(
            "相机存储卡 · ${snapshot.media.size} 项",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionHeading("最近拍摄", last?.media?.name ?: "本次会话")
        if (last != null) {
            MiCard(Modifier.fillMaxWidth()) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                        ByteImage(last.media?.thumbnail ?: last.preview, Modifier.fillMaxSize(), ContentScale.Crop)
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(last.message, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(last.media?.name ?: "刚拍摄的照片", style = MaterialTheme.typography.titleMedium)
                            Text(
                                last.media?.localPath?.substringAfterLast('/') ?: "可在下方相册中下载原图",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (last.media?.localPath != null) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = MiGreen)
                        }
                    }
                }
            }
        } else {
            MiCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("还没有拍摄", style = MaterialTheme.typography.titleMedium)
                    Text("拍摄后这里会出现底片缩略图", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionHeading("相机存储卡", "点击下载")
        if (snapshot.media.isEmpty()) {
            MiCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚未读取相机相册", style = MaterialTheme.typography.titleMedium)
                    Text("读取过程不会删除或修改相机中的文件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("读取相机相册", { vm.refreshMedia() }, Modifier.fillMaxWidth(), icon = {
                        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp), tint = Color.White)
                    })
                }
            }
        } else {
            MediaGrid(snapshot.media, snapshot.busy) { vm.download(it) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MediaGrid(media: List<CameraMedia>, busy: Boolean, onDownload: (CameraMedia) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        media.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !busy) { onDownload(item) }
                    ) {
                        ByteImage(item.thumbnail, Modifier.fillMaxSize(), ContentScale.Crop)
                        if (item.isVideo) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Videocam, null, Modifier.size(13.dp), tint = Color.White)
                            }
                        }
                        if (item.localPath != null) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                Modifier.align(Alignment.BottomEnd).padding(7.dp).size(18.dp),
                                tint = MiGreen,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.CloudDownload,
                                null,
                                Modifier.align(Alignment.BottomEnd).padding(7.dp).size(17.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
