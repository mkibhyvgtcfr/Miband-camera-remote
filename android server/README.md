# 米环相机 · Android 手机服务端

这是 qDslrDashboard 3.5.7 的独立重写版手机端。它保留“连接相机 → 实时取景 → 拍摄 → 调整参数 → 浏览/下载影像”的核心工作流，并新增小米手环 10 的官方 `interconnect` 控制通道。没有手环时，手机端仍可独立完成全部核心操作。

> 本目录是 Android 工程，手环工程在 [`../miband`](../miband)。

## 功能

- **连接**
  - USB OTG：Android USB Host + ISO 15740 PTP（Nikon / Canon / Panasonic / 通用 PTP 相机）
  - Nikon Wi-Fi：PTP/IP（默认端口 15740）
  - Canon Wi-Fi：CCAPI（默认端口 8080）
  - Sony Wi-Fi：Camera Remote API / ScalarWebAPI（默认端口 10000）
  - 演示相机：无硬件也能体验取景、快门、参数、手环联动
- **拍摄**
  - 实时取景（Nikon / Canon；Sony 取景流按机型支持）
  - 快门、自动对焦、录像（按机型支持）
  - 定时 2 秒 / 10 秒
  - 间隔拍摄（张数与间隔可调，可中途停止）
  - 取景网格、拍摄闪光与触感反馈
- **参数**
  - ISO、快门、光圈、曝光补偿
  - 白平衡、驱动模式、测光、对焦、画质、曝光模式等（由相机实际提供）
  - 相机属性来自 PTP DevicePropDesc / Canon EOS 属性 / CCAPI / Sony API；不支持的项目自动隐藏
- **影像**
  - 读取相机存储卡对象列表、缩略图、单张下载
  - 保存到应用专属 Pictures 目录，并在 Android 10+ 同步到系统相册
- **手环**
  - 通过小米穿戴官方 `interconnect` 消息 API 与 Vela 手环应用双向通信
  - 手环状态、快门、对焦、录像、ISO/快门/光圈/曝光补偿
  - 手环断开时手机端不受影响
- **界面**
  - Jetpack Compose，HyperOS / Vela 风格的暖黑与橙色视觉
  - 大字号、清晰层级、48dp 左右触控目标、明暗主题

## 环境与构建

已安装并验证：

- JDK 17.0.2：`D:\dev\jdk17\jdk-17.0.2`
- Android SDK 35：`D:\dev\android-sdk`
- Gradle 8.9（项目内 wrapper 使用腾讯镜像）
- Build Tools 35.0.0

PowerShell：

```powershell
cd "C:\Users\Admin\Desktop\deepseek工作区\米环相机控制\android server"
$env:JAVA_HOME = "D:\dev\jdk17\jdk-17.0.2"
$env:ANDROID_HOME = "D:\dev\android-sdk"
$env:ANDROID_SDK_ROOT = "D:\dev\android-sdk"

.\gradlew.bat --console=plain :app:assembleDebug
.\gradlew.bat --console=plain :app:assembleRelease
```

输出：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
- 发布副本：`dist/MiBandCameraServer-1.1.0.apk`

当前发布包 SHA-256：

```
385F8778915ED069CC7683F4B0E7EA2ADCDA0CF9B23DB5FDB7B47A494826AEC3  MiBandCameraServer-1.1.0.apk
4B0FF84C90BA15EA0511F56A0D77D7AB061DE1FBAC9132FB4E1120F978B89559  com.miband.camremote.release.1.0.1.rpk
```

`local.properties` 指向本机 SDK。换电脑后修改其中的 `sdk.dir`。

### 签名与手环配对

`keystore/miband-camremote.jks` 同时用于 APK 与 RPK。密码在 `keystore.properties`，仅用于本机开发，**发布前请更换**。

Xiaomi Wear 的 `interconnect` 要求手环 RPK 与 Android APK：

1. `package` / `applicationId` 完全一致：`com.miband.camremote`
2. 使用同一份签名证书

本工程已经把 `sign/debug`、`sign/release` 的 PEM 放在 `../miband/sign/`。如果重新生成 keystore，需要重新导出 PEM，否则手环通道无法建立。

## 安装与使用

1. 安装 `app-debug.apk` 或发布 APK。
2. 首次启动允许通知权限；连接 USB 相机时允许系统的 USB 访问弹窗。
3. 在「连接」页选择：
   - **USB**：用 OTG 线连接相机，将相机 USB 模式设为 `PTP`/`MTP`，点击刷新并选择设备。
   - **无线**：让手机连接相机 Wi-Fi 热点，选择协议、填写 IP 与端口。
   - **演示**：无需相机，直接进入虚拟相机。
4. 进入「拍摄」，轻触取景框开始实时取景，使用橙色快门。
5. 「参数」页点击参数或使用 ＋/− 调整；「影像」页读取相机存储卡。

### 相机 Wi-Fi 常见地址

| 品牌 | 模式 | 默认地址 |
|---|---|---|
| Nikon | PTP/IP | `192.168.1.1:15740` |
| Canon | CCAPI | `192.168.1.1:8080` 或相机显示的 IP |
| Sony | ScalarWebAPI | `192.168.1.1:10000`（以相机显示为准） |

相机型号和固件差异很大；若某个端点返回 404，请以相机菜单显示的服务地址为准。USB PTP 是最通用的路径。

## 手环连接

1. 先用 AIoT IDE 或 `aiot` 打出手环 RPK（见 [`../miband/README.md`](../miband/README.md)）。
2. 在手机「小米运动健康」中连接小米手环 10。
3. 安装手机 APK 和手环 RPK（相同包名/签名）。
4. 打开手机端应用与手环应用，允许小米运动健康的连接授权。
5. 手机「连接」页的手环通道显示“已连接”，手环首页会显示相机状态。

如果手环未连接：

- 确认小米运动健康正在运行且手环已连接；
- 确认 APK 与 RPK 的包名一致且签名一致；
- 在「设置 → 小米手环 → 重新检测」重试；
- 部分系统需要在小米运动健康中手动允许第三方应用连接。

## 协议实现说明

- `camera/Ptp.kt`：ISO 15740 容器、标准操作码、Nikon/Canon 厂商操作码与属性码
- `camera/PtpData.kt`：PTP 字符串、DeviceInfo、DevicePropDesc、ObjectInfo、JPEG 提取
- `camera/UsbPtpTransport.kt`：Android USB Host 批量端点传输
- `camera/PtpIpTransport.kt`：PTP/IP 双通道握手、数据阶段、事件通道
- `camera/PtpCameraConnection.kt`：会话、属性、拍摄、对焦、实时取景、相册
- `camera/CanonCcapiConnection.kt`：Canon CCAPI HTTP/JSON
- `camera/SonyApiConnection.kt`：Sony ScalarWebAPI
- `camera/VirtualCameraConnection.kt`：演示相机
- `band/WearableBridge.kt`：小米穿戴 SDK 的节点发现、权限、消息收发
- `data/CameraRepository.kt`：单例状态机、前景服务、相册保存、手环命令路由

没有把 qDslrDashboard 的 Qt `.so` 或闭源资源打进 APK；这是独立的 Kotlin 实现，仅参考公开的 PTP/PTP/IP 规范和开源协议实现（libpict 等）。

## 相机支持范围

| 品牌 / 路径 | 连接 | 快门/对焦 | 实时取景 | 参数 | 相册 |
|---|---|---|---|---|---|
| Nikon USB PTP | ✅ | ✅ | ✅（支持机型） | ✅ 标准/厂商属性 | ✅ |
| Nikon PTP/IP | ✅ | ✅ | ✅（支持机型） | ✅ | ✅ |
| Canon USB PTP | ✅ | ✅ EOS 遥控 | ✅ EOS 取景 | ✅ EOS 属性 | ✅ 标准 PTP |
| Canon CCAPI | ✅ | ✅ | ✅ | ✅ | ✅ |
| Sony Wi-Fi API | ✅ | ✅ | ✅ 按机型 | ✅ 基础曝光 | ✅ |
| Panasonic / 其他 USB PTP | ✅ 通用 | 依机型 | 通常不支持 | 标准属性 | ✅ 通用 |

> 相机的厂商扩展差异极大。某些旧机型只实现了部分 EOS/PTP 操作，界面会隐藏不支持的项目并给出日志。高级的 qDslrDashboard 专用模式（如 Nikon 闪光分组、LR Timelapse、Moco 滑轨、焦点堆栈）不在本版本范围内；核心拍摄链路、参数和手环控制已覆盖。

## 测试

协议解析测试位于 `app/src/test/.../PtpProtocolTest.kt`：PTP 字符串、DeviceInfo、属性描述、Nikon/Canon 显示转换、JPEG 提取、负曝光补偿等。

Windows + 中文路径下 AGP 测试 worker 存在 classpath 编码问题，使用脚本在 `%TEMP%` 的 ASCII 路径副本中运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-tests.ps1
```

已验证通过：8 个测试。

## 主要源文件

- [`MainActivity.kt`](app/src/main/java/com/miband/camremote/MainActivity.kt)
- [`ui/AppRoot.kt`](app/src/main/java/com/miband/camremote/ui/AppRoot.kt)
- [`ui/screens/CaptureScreen.kt`](app/src/main/java/com/miband/camremote/ui/screens/CaptureScreen.kt)
- [`data/CameraRepository.kt`](app/src/main/java/com/miband/camremote/data/CameraRepository.kt)
- [`band/WearableBridge.kt`](app/src/main/java/com/miband/camremote/band/WearableBridge.kt)

## 已知限制

- 无法在无相机硬件的环境中验证每一台相机的厂商扩展；USB/PTP/IP 与 CCAPI 已按公开协议实现并做了容错回退。
- 实时取景帧率取决于相机、USB 带宽和 JPEG 大小；高像素机型可能只有几帧/秒。
- PTP 大文件下载（尤其超过 4GB 的视频）需要分块读取；当前实现按整对象返回，超大文件可能超时。
- Sony API 的方法集合因机型/固件不同而变化，部分机型需要使用相机热点下显示的端口。
- 手环真机安装需要小米运动健康和对应的小米穿戴 SDK 授权；模拟器无法验证 interconnect。









