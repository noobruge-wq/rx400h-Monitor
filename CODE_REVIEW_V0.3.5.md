# RX400h Monitor V0.3.5/v27 代码审查报告

审查日期：2026-08-31  
审查对象：本地分支 `v0.3.0` 上的 V0.3.5/v27 工作树  
审查方式：源码、单元测试、构建产物、Git 差异和项目状态文档交叉检查

## 结论先说

本地文件足够完成一次完整的“代码级审查”：可以确认本轮改动的范围、核心逻辑、状态转换、排版计算、测试结果和 APK 身份。当前审查没有发现需要停止交付的代码级阻断问题。

但本地文件不能替代目标车机实测。因此，本报告可以确认“代码和构建是否合理”，不能单独确认“目标车机首次打开画面、实际日光可读性、蓝牙实时流和真实保存失败场景”已经全部通过。

当前 APK 是本地 dirty-worktree 调试候选，不是已推送的远端发布包，也没有在本轮安装到车机或执行车辆动作。

## 审查依据

| 项目 | 结果 |
| --- | --- |
| 分支 | `v0.3.0` |
| 基础 HEAD | `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45` |
| 工作树 | dirty；包含本轮源码、测试和文档改动 |
| 应用版本 | `0.3.5-debug` |
| versionCode | `27` |
| applicationId | `com.guanyu.rx400hprobe.debug` |
| minSdk / targetSdk | 26 / 35 |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 便于测试的复制包 | `C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\deliverables\RX400h-Monitor-v0.3.5-v27-signal-ui-recovery-debug.apk` |
| APK 大小 | 2,612,662 bytes |
| APK SHA-256 | `0b803f3cf3759d96e350e6ea4f18146ae263c1250cfa557780d8011a84bd533e` |
| 签名 | APK Signature Scheme v2 通过；固定开发证书 SHA-256 `77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192` |
| 构建 provenance | `GIT_COMMIT=8f1a3b9fb49359484c283ea6c3eaf69c631b4b45`，`GIT_DIRTY=true` |

## 本轮修改文件

### 界面和绘图

- `app/src/main/java/com/guanyu/rx400hprobe/DashboardUi.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/DashboardSchematics.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/DashboardSchematicState.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/ResponsiveLayout.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/ResponsiveViewGroups.kt`

### 实时会话恢复

- `app/src/main/java/com/guanyu/rx400hprobe/MainActivity.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/MonitorSessionState.kt`
- `app/src/main/java/com/guanyu/rx400hprobe/ProbeLogger.kt`

### 测试

- `app/src/test/java/com/guanyu/rx400hprobe/DashboardSchematicStateTest.kt`
- `app/src/test/java/com/guanyu/rx400hprobe/ResponsiveLayoutTest.kt`
- `app/src/test/java/com/guanyu/rx400hprobe/MonitorSessionStateTest.kt`
- `app/src/test/java/com/guanyu/rx400hprobe/ProbeLoggerLifecyclePolicyTest.kt`

### 版本、流程和项目记录

- `app/build.gradle.kts`
- `.github/workflows/build-apk.yml`
- `PROJECT_STATE.md`
- `DECISIONS.md`
- `ROADMAP.md`
- `CHANGELOG.md`
- `EVIDENCE_INDEX.md`
- `CODEX_HANDOFF.md`
- `BASELINE_README.md`
- `CODE_REVIEW_V0.3.5.md`

### 关键代码入口（便于人工复核）

| 文件 | 入口 |
| --- | --- |
| `DashboardUi.kt` | `DashboardUi`、`buildDomainCards`、`applyIdleCheckAppearance`、`applyWindowLayout` |
| `DashboardSchematics.kt` | `SignalSchematicView`、`BatterySchematicView`、`VehicleSchematicView`、`HvPowerGaugeView` |
| `DashboardSchematicState.kt` | 所有 Canvas 映射、未知值、Idle Check 亮度和插值规则 |
| `ResponsiveLayout.kt` / `ResponsiveViewGroups.kt` | 卡片网格、header 三模式、按钮间距、首帧测量使用的几何合同 |
| `MainActivity.kt` | `canSelectDevice`/`canStart` 调用、`SAVE_FAILED` 重启路径、Dashboard 快照组装 |
| `MonitorSessionState.kt` | 三按钮在各会话阶段的可用性策略 |
| `ProbeLogger.kt` | 新会话准入、`FINALIZE_FAILED` 安全 detach、durable writer close |

## 发现一：手工布局坐标已经被统一成程序化几何

用户提供的 JSON 被当作目标构图参考，而不是直接照抄其中可能有偏差的绝对坐标。程序在 `ResponsiveLayout` 和两个自定义 ViewGroup 中重新计算公共基线、间距和可用宽度。

在 mdpi 的 1280×720 目标几何模型中，关键结果是：

| 元素 | 几何结果 |
| --- | --- |
| 外边距 | 左右 20 |
| 三个域卡片左边界 | 20、440、860 |
| 卡片宽度 / 高度 | 400 / 556 |
| 卡片间距 | 20 |
| 卡片顶部 / 底部 | 150 / 706 |
| 标题列宽度 | 223 |
| 中间控制区宽度 | 701 |
| 状态列宽度 | 300，右边界 1260 |
| 三个按钮左边界 | 320、518、716 |
| 按钮宽度 / 间距 | 166 / 32 |

三个域内部也使用固定的行高合同：BAT 指标等高分配，VEH 的车辆图、速度/冷却液行和 12V 行分开，PWR 三个主要指标、功率表和 Idle Check 分开。这样可避免标签和值因为文字长度或首次测量时机不同而互相覆盖。

手机和窄窗口仍走原有的自适应换行及整页滚动路径；本轮没有把界面退回固定 1280×720。手机竖屏不是 V0.3.5 的主要验收门，但没有被删除。

## 发现二：三张参考图已变成真正由实时信号驱动的原生绘图

APK 不包含用户提供的三张 PNG。它们被重绘为轻量 Android `Canvas` View：

- BAT：真实 SOC 控制分段填充；真实电池最低/平均/最高温度控制整包温度范围和平均标记；HV 功率只用于表达充/放电方向。
- VEH：真实且新鲜的车速控制方向中性的轮圈运动；冷却液和 12V OBD 只控制有边界的指示条；发动机区域只由新鲜 RPM 或发动机机械功率点亮，不推断档位、MG 状态、打滑或泵流量。
- PWR：真实且新鲜的 HV 电池功率映射到 `-50 … 0 … +50 kW`；负值为充电/回收，正值为放电/牵引；未知值不伪装成 0。

动画属于显示层插值，不是采集提速：

- 最大 20 fps，普通数值过渡最多约 4 帧。
- View 脱离窗口、不可见、窗口失去焦点或被隐藏时停止回调。
- 绘图对象和几何缓存复用，没有每帧创建 Bitmap/Paint/Path。
- CRT 扫描线和 glow 仍是低成本静态处理，不使用 WebView、Compose、实时 blur 或大型 shader。

## 发现三：Idle Check 满足“未激活低亮、激活 100%”

Idle Check 现在一直保留一个低亮度框和文字，条件真正 active 且状态为 VALID 时才切换到完整荧光亮度。它不会通过闪烁、脉冲或渐变隐藏状态。

本轮没有修改 Idle Check 的阈值、计时、判定算法或 `SignalStore` 语义；只修改显示层和快照中“只有 VALID 的 true 才算 active”的呈现门槛。

## 发现四：保存/恢复失败不再把实时监控锁死

`SAVE_FAILED` 现在同时允许：

- `设备`：重新选择设备；
- `开始`：在旧任务、写入器、蓝牙连接和进程 session lease 已安全释放后建立新实时会话；
- `结束`：继续重试旧日志的保存、打包或发布。

新会话使用新的日志目录。旧目录、ZIP、失败标记和待发布证据都保留在磁盘上，不会被删除、覆盖或伪装成成功。只有新的 `logger.start()` 真正成功后，才清除 Activity 内存中的旧重试引用；如果新会话启动失败，旧重试状态仍保留。

对 `FINALIZE_FAILED`，`ProbeLogger.start()` 会在新会话前执行 durable close，并写入 `FINALIZE_DEFERRED` 记录。若 writer 无法安全关闭，程序会保留失败 owner 和失败状态，不会伪装成空闲并打开第二个 owner。这是极少见的安全阻断，用来避免两个 logger 同时写同一证据；普通的保存/恢复失败路径不会再强制用户退出应用。

权限取消、等待旧连接关闭被中断等情况也会回到 `SAVE_FAILED`，不会错误回到“完全空闲且找不到旧证据”的状态。

## 发现五：核心边界检查

以下核心合同在本轮没有改动：

- `DeadlineScheduler` 算法和 admission 逻辑；
- request table、请求周期、header/command 白名单；
- ELM/Bluetooth 交易顺序；
- decoder 公式；
- `SignalStore` 的数据和 freshness 语义；
- logger 的证据完整性校验规则；
- Idle Check 的实际判定阈值；
- 三按钮业务语义（设备 / 开始 / 结束）。

`MainActivity.kt` 和 `ProbeLogger.kt` 虽然被修改，但修改集中在快照呈现、保存失败后的控制状态和安全的 session 交接；没有新增协议、扫描、请求或解码路径。

## 验证结果

### 已通过

- 生产源码和测试源码 Kotlin 编译：通过。
- `:app:lintDebug`：通过，0 errors、9 个非阻断既有 warning。
- `:app:assembleDebug`：通过。
- 完整 direct JUnit：113 / 113 通过。
- `git diff --check`：没有内容错误；只有 Git 的 LF/CRLF 提示。
- `aapt dump badging`：确认 package、versionCode 27、versionName 0.3.5-debug、minSdk 26、targetSdk 35。
- `apksigner verify`：v2 签名通过。

### 当前不能由本地文件替代的验证

- 本轮没有可用的 API 27 模拟器完成真实 GUI 截图；API 26 主机模拟器也曾在 ADB 前阻塞。因此不能把本报告写成“新 APK 已通过目标车机 GUI 验收”。
- 本轮没有把 APK 安装到目标车机，也没有配对 OBD、启动车辆或执行真实熄火/强制关闭恢复测试。
- 真实目标车机上的首帧、日光可读性、扫描线观感、蓝牙连接和保存失败时三个按钮是否可操作，仍需人工验收。
- 当前标准 Gradle test worker 在 Windows Unicode 路径下会在测试执行前复现 `GradleWorkerMain`/closed-pipe 启动问题；这不是测试断言失败。为避免把两者混为一谈，本次用同一编译产物通过 direct JUnit 完成了实际 113 项测试。

## 建议的目标车机验收顺序

1. 安装本报告所列的 `app-debug.apk`；首次打开时不下拉状态栏、不切换页面，直接观察标签、数值、卡片边界和三个按钮。
2. 确认 1280×720 下三张卡和顶部按钮从第一眼开始对齐，数字不覆盖标签。
3. 开始实时流，观察 BAT / VEH / PWR 三张图是否随真实数据变化；未知值应显示为未知，不应跳成 0。
4. 正常结束一次，确认 End 后保存流程不影响下一次 Start。
5. 在可控条件下复现保存或恢复失败：确认 `设备` 和 `开始` 仍可点，Start 能建立新的实时流，End 仍可重试旧证据；检查旧失败目录没有消失。
6. 如要确认扫描线和亮度，优先拍摄首次画面或直接观察，不要把下拉状态栏后的刷新画面当作唯一证据。

## 最终判断

本地资料已经足够让产品经理和高级工程师审查“这版代码做了什么、有没有越界、构建是否可靠”。当前最重要的剩余工作不是继续猜布局，而是在目标车机上安装这个 dirty-worktree APK，完成一次首帧/数据流/保存失败后重新 Start 的人工验收。通过该验收后，才适合决定是否把 V0.3.5/v27 作为下一候选继续推进。
