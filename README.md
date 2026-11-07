#米环相机控制
此项目由DSH基于qDslrDashboard_3.5.7.apk构建，测试设备及环境：尼康d800e有线连接，目前仅实现遥控快门，查看参数及图像预览，无线连接理论可行但并未实现。手环端仅对小米手环10UI进行了适配，可通过AI进行其他设备的适配。本项目仅供演示，不作进一步维护计划。

小米手环 10 + Android 手机服务端 + 有线/无线相机的完整控制方案。

- 手机服务端：`android server/`（Kotlin / Jetpack Compose）
- 小米手环端：`miband/`（Vela JS / AIoT IDE）
- 原始 APK：`qDslrDashboard_3.5.7.apk`（仅用于功能与协议分析，不包含在交付应用中）
- 分析工作区：`work/`

## 快速开始

1. 安装 [`android server/dist/MiBandCameraServer-1.1.0.apk`](android%20server/dist/MiBandCameraServer-1.1.0.apk)
2. 打开手机端，选择 USB / Wi-Fi / 演示相机
3. 如需手环控制：在 AIoT IDE 或命令行打出手环 RPK，安装到小米手环 10，保持两端包名与签名一致

## 交付产物

| 文件 | 说明 |
|---|---|
| [`android server/dist/MiBandCameraServer-1.1.0.apk`](android%20server/dist/MiBandCameraServer-1.1.0.apk) | 手机服务端发布签名版 |
| [`android server/dist/MiBandCameraServer-1.1.0-debug.apk`](android%20server/dist/MiBandCameraServer-1.1.0-debug.apk) | 调试版（可直接安装） |
| [`miband/dist/com.miband.camremote.release.1.0.1.rpk`](miband/dist/com.miband.camremote.release.1.0.1.rpk) | 小米手环 10 发布 RPK |
| [`miband/src`](miband/src) | AIoT IDE 手环工程 |
| [`android server`](android%20server) | Android Studio / VS Code 工程 |

## 文档

- [手机服务端说明](android%20server/README.md)
- [手环端说明](miband/README.md)
- [技术分析与逆向记录](docs/APK_ANALYSIS.md)

## 签名

两端共用 `com.miband.camremote` 包名与 `android server/keystore/miband-camremote.jks` 证书。手环 `sign/debug`、`sign/release` 中是对应的 PEM。若要更换证书，必须同时重新签名 APK 和 RPK，否则小米穿戴的 `interconnect` 无法建立。

## 1.0.1 修复说明

这版针对反馈的三个问题做了修改：

1. **手环卡启动页**
   - 移除了 Vela 运行时无法稳定处理的页面 `private` 状态块；这是本次启动卡住的实际原因。
   - 不再在页面创建时加载 `@system.interconnect`，避免模块缺失/未授权时阻塞首帧。
   - 首页增加“连接手机”按钮，手动建立通道；失败时页面仍可打开和退出。
   - 旧 RPK 请先卸载再安装 1.0.1，避免残留旧页面。
2. **小米运动健康没有授权**
   - 手环授权请求改为从可见 Activity 发起（之前从 Application 启动，系统可能不弹授权页）。
   - 首页增加“请求手环授权”按钮；先打开手环端应用，再点该按钮。
   - 授权前会先显示“请打开手机端并点击请求授权”，不会静默失败。
3. **USB 读取超时**
   - 改用原软件相同的接口占用方式：不强制 `setConfiguration/setInterface`，避免重置相机端点。
   - 优先选择带 PTP 特征（still-image、subclass/protocol 1、中断端点）的接口。
   - USB 模式错误会直接提示“相机处于 USB 存储模式”，而不是只报超时。
   - 首次失败自动重试一次，并输出端点/已读字节数，便于定位。
   - UI 增加 PTP/非 PTP 标记和 USB 排查提示。

如果升级后仍失败：

```powershell
adb logcat -s MiBandUsbPtp MI
```

请把相机型号、USB 模式、手机型号，以及 App 内“连接日志”的最后几条发来。

## 1.0.2 D800E USB 修复

D800E 的 PTP 响应会以一个完整的 USB 批量包返回。此前 USB 读取只请求 8 字节头，Android USB 栈对小于实际包长的请求直接返回 `-1`，于是出现“已读 0/8、IN=0x81”。

1.0.2 改为 **按端点 maxPacketSize/16KB 读取并维护流缓冲区**，再从缓冲区中解析 PTP 容器，不再丢弃包头之外的字节。这正对应 D800E（inep 0x81 / outep 0x02 / intep 0x83）的传输方式。

## 1.1.0 拍摄与参数修复

1. **AF 模式下只对焦不释放快门**
   - 去掉按下快门前自动发送 `NikonAfDrive` 的逻辑；AF-S 下由相机自己完成对焦并释放。
   - Nikon 连接时发送 `ChangeCameraMode(1)` 进入 PC 控制模式。
   - 优先使用 `0x9207 InitiateCaptureRecInMedia`（AF 参数 + 存储卡目标），失败再退回标准 `InitiateCapture`、最后退回 SDRAM。
2. **参数全部不可调**
   - 修正 PTP `DevicePropDesc.getSet` 语义：`0=只读，1=读写`，不再错误地按位掩码判断。这是参数全部灰掉的实际原因。
   - 增加 ISO 自动（`0xD054` / `0xD16A`）、自动 ISO 上限、闪光模式、动态 D-Lighting、长曝光降噪、高 ISO 降噪、曝光延迟等 Nikon 参数。
   - 参数页增加「拍摄模式」卡片；若机身拨盘只读，会明确显示“不支持通过 USB 改变”。
   - ISO、快门、光圈、EV 和画质等仍按相机返回的枚举/范围生成选项。
3. **拍摄后影像错误**
   - 快门后轮询 `GetObjectHandles`，只读取新出现的 JPEG/NEF，不再把存储卡里旧照片当成本次拍摄。
   - RAW+JPEG 会同时加入影像页；缩略图从 `GetThumb` 或 NEF 内嵌 JPEG 提取。
   - 下载失败时按 `GetPartialObject` 分块续传；NEF 保存为 `image/x-nikon-nef`。
   - 设置里的「拍摄后自动下载 JPEG」现在下载完整 JPEG，不再把缩略图当原图保存。

> D800E 的 P/A/S/M 是机身物理拨盘；如果相机把 `0x500E` 报告为只读，App 会显示当前挡位但不能远程切换，这是机身限制。ISO 自动、曝光补偿、快门、光圈等仍可调整。

## 当前范围

手机端覆盖相机连接、实时取景、快门、对焦、曝光/画质参数、相册读取与下载、定时与间隔拍摄；手环端覆盖状态、快门、对焦、录像和曝光参数。原始 qDslrDashboard 中高度依赖厂商私有协议的高级模式（Nikon 闪光分组、Moco 滑轨、LR Timelapse、焦点堆栈等）未包含在本版本，详见手机端 README 的“相机支持范围”和“已知限制”。




