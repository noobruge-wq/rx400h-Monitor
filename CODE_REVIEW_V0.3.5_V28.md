# RX400h Monitor V0.3.5/v28 代码级审查报告

审查日期：2026-09-01  
审查对象：本地分支 `v0.3.0` 上的 V0.3.5/v28 dirty working tree  
基础 HEAD：`8f1a3b9fb49359484c283ea6c3eaf69c631b4b45`

## 结论

V0.3.5/v28 已完成代码、本地测试、lint、构建、版本、资源和签名核验，可以交给目标车机测试。它准确采用用户最后保存的 `RX400h-UI-layout-v035-pixel-v2 (3).json`，没有再自动对齐、改写坐标或抽象重画车辆。

这仍是 dirty-worktree 调试候选，不是 Git 远端发布版。本轮没有安装到用户设备、没有 push、没有提交、没有创建 PR，也没有修改 scheduler、请求周期、协议、decoder、`SignalStore` 或 Idle Check 判定条件。

## APK 身份

| 项目 | 结果 |
| --- | --- |
| applicationId | `com.guanyu.rx400hprobe.debug` |
| versionName / versionCode | `0.3.5-debug` / `28` |
| minSdk / targetSdk | `26` / `35` |
| 基础提交 | `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45` |
| dirty 标记 | `GIT_DIRTY=true` |
| 交付 APK | `../../deliverables/RX400h-Monitor-v0.3.5-v28-pixel-dashboard-debug.apk` |
| 文件大小 | `2,656,023 bytes` |
| SHA-256 | `020a0d9fb05e718e0eef6e100edbdd306dcba244f7b4017b67e2967f048d30f6` |
| 签名 | APK Signature Scheme v2 验证通过 |
| 证书 SHA-256 | `77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192` |

构建目录 APK 与交付目录 APK 的 SHA-256 完全相同。

## 用户最终布局的落实

- 最终 JSON SHA-256：`1f3914510943261f3c2d29f4d8eaf4abca7236b5676e0584948c2839b63518aa`。
- APK 内置 JSON 与用户文件逐字节一致。
- 47 个图层的 `x/y/w/h/z/alpha/visible` 直接作为运行时合同，不做归一化或重新排版。
- 640×360 逻辑像素统一按最近邻放大到 1280×720；其他窗口居中并使用纯黑留边。
- 用户完成的俯视车、侧视车和独立轮辐遮罩直接使用；没有再用程序随意概括车身。
- 固定中文按 JSON 声明的物理像素尺寸，用 Noto Sans SC 预栅格化；字号和图层边界不改。实时数字继续使用用户提供的位图字形。

## 动态显示合同

- SOC 显示采用确认过的丰田区间压缩：`0–30%` 占 15%，`30–80%` 占 75%，`80–100%` 占 10%。
- 按下“开始”后不会拿旧快照触发自检；必须等开始后的新 SOC 与 HV 功率数据。自检期间真实值继续更新，动画结束会落到最新值。
- SOC 自检为 `0→100→真实值`；HV 功率为两侧同时 `0→±50`，再把真实侧落到真实值、另一侧归零。
- 自检结束后只留下一个指示滑块；正值在放电侧、负值在充电侧、零值位于中央。未知数据不伪装成零，也不显示填充或滑块。
- 轮辐独立旋转，方向中性，车速 `0–120 km/h` 映射到 `0–2.5 rps`；零速立即停止，轮动画最高 10 fps。
- Idle Check 框和文字未激活为 10% 亮度，只有现有运行时状态为有效 `true` 时才达到 100%；判定阈值和计时没有改变。

## CRT 设置与性能边界

主界面只增加“设置”按钮。浮层保持黑底、荧光绿、细线框和叉号关闭风格，参数仅保存在本机：

- 扫描线不透明度、线宽和间隔；
- 辉光强度和半径。

默认值来自最终 JSON：`75% / 1 px / 1 px`，辉光 `100% / 8 px`。扫描线在 1280×720 物理输出的最后阶段绘制。辉光使用固定八方向位图绘制，不使用 blur、shader、Compose、WebView 或新依赖。

布局 JSON、图层遮罩、字体精灵、位图字形和 36 帧轮辐全部在初始化时解析/缓存。信号字符串只在对应版本或 freshness 改变时重新格式化；绘制循环复用 Bitmap、Canvas、Paint、Rect 和功率段变量。普通 SOC/功率过渡最高 20 fps，轮辐最高 10 fps；静止、后台、窗口不可见或 View 分离时不持续调度帧。

## 修改文件

### V0.3.5/v28 像素界面

- `app/src/main/java/com/guanyu/rx400hprobe/DashboardUi.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/PixelDashboardView.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/PixelDashboardModel.kt`
- `app/src/main/assets/rx400h_ui/layout_v035_pixel_v2.json`
- `app/src/main/assets/rx400h_ui/physical_text/*.png`
- `app/src/test/java/com/guanyu/rx400hprobe/PixelDashboardModelTest.kt`
- `tools/generate_pixel_dashboard_assets.py`
- `app/build.gradle.kts`
- `.github/workflows/build-apk.yml`

被 v28 取代、且尚未进入 Git 历史的 v27 抽象车辆绘图源文件和对应测试已移除。仓库原有的响应式 helper 保留在 dirty worktree 中，但 v28 的实际 Dashboard 入口不再调用它们。

### 继续保留的 D-055 失败恢复修正

- `app/src/main/java/com/guanyu/rx400hprobe/MainActivity.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/MonitorSessionState.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/ProbeLogger.kt`
- `app/src/test/java/com/guanyu/rx400hprobe/MonitorSessionStateTest.kt`
- `app/src/test/java/com/guanyu/rx400hprobe/ProbeLoggerLifecyclePolicyTest.kt`

D-055 仍保证保存/恢复失败后，只要旧 worker、writer、连接和 lease 已安全释放，就允许重新选设备并开始新实时流；旧失败证据不删除、不覆盖、不冒充成功。

## 核心冻结检查

以下文件相对基础 HEAD 没有差异：

- `DeadlineScheduler.kt`
- `RequestTable.kt`
- `ObdParsers.kt`
- `SignalStore.kt`
- `IdleCheckState.kt`

因此本轮没有改变请求表、频率、deadline/admission、协议白名单、解码公式、freshness 语义或 Idle Check 资格判定。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 生产/测试 Kotlin 编译 | 通过 |
| 直接 JUnit | `111/111` 通过 |
| `lintDebug` | 通过，0 errors / 9 个非阻断 warnings |
| `assembleDebug` | 通过 |
| `git diff --check` | 无 whitespace error；仅 Windows LF/CRLF 提示 |
| APK 版本清单 | versionCode 28、versionName 0.3.5-debug、minSdk 26 |
| APK v2 签名 | 通过 |
| JSON 原样打包 | 通过，源文件与 APK 源资产哈希一致 |

标准 Gradle `testDebugUnitTest` 仍在测试执行前受当前 Windows 中文路径影响，报 `GradleWorkerMain` 找不到和管道关闭；同一批已经由 Gradle 编译的测试类改由 JUnit 4.13.2 直接运行，111 项全部通过。该环境故障没有被记成测试断言失败，也没有被伪装成标准任务通过。

## 尚未完成的运行态门槛

- 2026-09-01 重试已成功清除旧 QEMU 锁，并先后绕过原 userdata、建立全新隔离的 API 26 / 1280×720 AVD，再分别验证软件加速与有效的硬件加速路径；所有路径仍在 Android 启动完成前停止，ADB 缺失或仅为 `offline`，`sys.boot_completed` 不可达。因此阻塞点已收窄为本机 Android Emulator/QEMU/WHPX 运行环境，而不是旧 AVD 状态或 APK；本轮仍不声称 Android GUI 截图通过。完整重试记录见 `../../outputs/v28-gui-retry/README.md`。
- 目标 API 27/1280×720 车机的首帧、设置滑杆、动画和读取性仍需用户安装后确认。
- 配对 OBD 实时流和 D-055 强制保存失败恢复仍需目标设备验证。

以上不阻止 APK 进入人工测试，但在车机确认前不能把 v28 宣称为正式发布基线。
