# RX400h Monitor V0.3.5/v28 D-058-R1 — Renderer 低风险修正审查

日期：2026-09-05  
范围：Presentation-only renderer root-cause test  
Repository：`noobruge-wq/rx400h-Monitor`  
Branch：`v0.3.0`  
Base HEAD：`8f1a3b9fb49359484c283ea6c3eaf69c631b4b45`  
工作树：dirty；本文不代表已提交、推送或发布 baseline

---

## 1. 结果摘要

D-058-R1 已完成本地源码、编译、112 项直接 JUnit、lint、assemble、包身份和固定 v2 签名验证，并生成可供目标车机测试的 APK。

本轮只移除了 D-057 在动态页面变化时执行的第二级全屏软件合成：不再在 `cachedFrameCanvas` 上清黑、做八次 1280×720 位图辉光混合并复制整页。清晰页面仍按原代码绘制到 `sharpBitmap`；最终八方向辉光、清晰页面、独立车轮和扫描线改由 View Canvas 按原顺序绘制。

设置浮层打开时，如果唯一动画来源是车轮，10 fps 回调会停止；SOC、HV 功率或 Start 自检仍在运行时不会错误停止可见过渡。

本地模拟器 GUI gate 未完成。本机当前的 Android Emulator 报告“hypervisor driver is not installed”；显式 `-accel off` 也未产生 ADB 设备并自行退出。因此当前不能声称新管线已通过 API 26 截图或像素 diff。这个限制不影响编译/测试结论，但目标车机安装后的第一项工作必须是画面对照。

---

## 2. 为什么要改

精确 D-057 APK `b8cfe5d5…28bbab` 的 2026-09-03 目标车机记录显示：

| 场景 | D-057 | 旧 v28 |
|---|---:|---:|
| 行驶（speed > 1 km/h）weighted process CPU | **89.10%** | 约 **72%** |
| 停车/ICE off weighted process CPU | **17.17%** | 约 **51–57%** |

D-057 的缓存策略在没有变化时有效，但行驶中多个动态信号和 300 ms / 20 fps 过渡会频繁触发：

```text
重画完整 sharpBitmap
→ cachedFrameCanvas 清黑
→ 软件方式叠加 8 张完整 sharpBitmap 辉光
→ 再复制一次完整 sharpBitmap
→ 把变化后的完整 cachedFrameBitmap 交给 HWUI
```

D-058-R1 把它缩短为：

```text
内容变化时：只重画完整 sharpBitmap
每个 View frame：View Canvas 画 8 次辉光 → sharpBitmap → 局部车轮 → 扫描线
```

这能直接验证“Bitmap-backed Canvas 上的八次全屏 CPU 混合”是否是主要回归来源。它仍不是最终性能结论；硬件 Canvas 的 GPU 填充和完整 sharpBitmap 上传成本仍需目标车机数据判断。

---

## 3. 源码改动

### 3.1 `PixelDashboardView.kt`

主要位置：

- `51–56`：只保留 `sharpBitmap` / `sharpCanvas`，删除约 3.52 MiB 的第二个 1280×720 ARGB 页面 Bitmap。
- `122–133`：加入固定 primitive renderer 计数器；无集合或逐帧调试字符串。
- `249–271`：`onDraw()` 维持黑底与 viewport transform，在 View Canvas 上依次绘制页面辉光、清晰页、车轮、扫描线。
- `357–384`：`rebuildSharpFrame()` 只重建清晰页；debug 版使用单调墙钟和 UI 线程 CPU 时钟记录成本。
- `524–551`：独立车轮路径保留，最高帧率和已确认资源不变。
- `663–676`：原八方向辉光公式、半径和 alpha 不变，只改变执行 Canvas。
- `881–907`：内容动画与车轮动画分开判断；设置打开时轮转/轮过渡不再单独维持 callback。
- `909–946`：debug 版每约 5 秒向 `RX400hRenderer` logcat tag 输出一次聚合计数。
- `1021–1029`：打开设置时撤销已经排队的 frame callback 并清空车轮时间基准。

调试计数包括：

```text
rebuilds / rebuildHz
rebuild wall average/max
rebuild UI-thread CPU average/max
data dirty count
animation dirty count
wheel draws
settings wheel schedule suppressions
hardwareCanvasSeen
```

它们没有写入 `performance.csv`，没有进入证据 ZIP，也没有改变 logger/manifest。每 5 秒一次的字符串只存在于 debug build；逐帧路径只有 primitive 加法和时钟读取。

### 3.2 `PixelDashboardModel.kt`

增加无分配的纯函数 `animationFrameNeeded(...)`，集中表达：

- 内容过渡始终可以请求帧；
- 设置关闭时，车轮过渡或非零转速可以请求帧；
- 设置打开时，单独的车轮动画不能请求帧。

### 3.3 `PixelDashboardModelTest.kt`

增加一项回归测试，覆盖设置开/关、车轮 steady/transition 和内容过渡的组合。测试总数由 111 增至 112。

### 3.4 Docs-first 文件

- `PROJECT_STATE.md`
- `DECISIONS.md`（D-058）
- `ROADMAP.md`
- `CHANGELOG.md`
- `D057_RENDERER_OPTIMIZATION_TRIAL_PLAN.md`
- 本审查文件

---

## 4. 明确没有改动

本轮没有修改：

- `DeadlineScheduler`；
- request table 或七个 periods；
- protocol whitelist；
- Bluetooth / ELM transaction；
- decoder；
- `SignalStore`；
- `ProbeLogger` / `performance.csv` / evidence ZIP schema；
- session 保存、恢复或三按钮行为；
- `IdleCheckState` 阈值和计时；
- 用户 JSON、坐标、尺寸、z-order、alpha、字号或字距；
- SOC/HV 功率自检意义和 300 ms 过渡；
- 20 fps transition / 10 fps wheel 上限；
- 扫描线/辉光设置值和本地持久化；
- 车身或车轮图像。

---

## 5. 多角度风险复核

### 5.1 CPU

明确减少的工作是：每次页面变化不再在 CPU 软件 Canvas 上做八次完整页 alpha blend 和一次额外完整页复制。

仍然存在的成本：

- 清晰页变化时仍需完整 1280×720 软件重建和纹理上传；
- View 每个 frame 仍有八次完整页辉光 GPU draw；
- 动态 SOC/HV 功率过渡仍可请求 20 fps。

因此 `<55%` 只是目标，不是本地已证明结果。若 R1 只能降回约 55–72%，应进入 R2 小区域合成；若仍 `>=72%`，需线程级 CPU 和目标 `gfxinfo`，不能继续盲目堆缓存。

### 5.2 GPU 与硬件加速

Android 应用默认启用 HWUI，本 manifest 未关闭硬件加速。R1 还会在 debug logcat 中报告 `hardwareCanvasSeen`。但目标定制 API 27 ROM 的真实行为仍未由 `dumpsys gfxinfo` 直接证明。

若目标 View Canvas 实际是软件 Canvas，R1 可能把八次全屏绘制扩散到每个车轮帧，结果会比预期差。出现这种情况必须停止 R1，而不是用降低帧率掩盖。

### 5.3 内存

删除一个 1280×720×4 ARGB Bitmap，理论固定减少：

```text
3,686,400 bytes ≈ 3.52 MiB
```

其余 36 帧车轮、sharp page、状态 Bitmap、glyph/mask 缓存保持不变。目标 PSS 是否实际下降仍以实车 first/median/max/last 为准。

### 5.4 画面一致性

绘制顺序仍是：

```text
BLACK
→ 8-direction page halo
→ sharp page
→ wheel local halo + wheel
→ physical-output scanlines
```

清晰页生成逻辑完全保留，因此布局、文字、数值和遮挡关系没有设计变化。

未关闭的风险是合成位置改变：D-057 先在 1280×720 Bitmap 中完成辉光合成，再缩放成实际 View；R1 在已应用 viewport transform 的 View Canvas 上分别绘制各张 Bitmap。最近邻采样与整数偏移在理论上应等价，但目标内容高度若受导航栏影响而产生非整数缩放，浮点对角辉光边缘仍可能有轻微像素差异。必须以目标车机肉眼/照片或恢复模拟器后的截图 diff 关闭。

### 5.5 调度与日志

UI callback 变化不触碰 OBD `DeadlineScheduler`。现有 target log 的容量舍弃必须按 `reject/releases` 比率和碰撞窗口比较，不能直接比较不同会话里的原始 11 次。

renderer 计数没有进入用户上传的日志 ZIP。因此本轮实车可以用既有 `performance.csv` 判断总体成功/失败，但若要精确分解“重建率 × 单次成本”，仍需 ADB logcat，或另行批准 debug-only sidecar；本轮没有偷改 evidence schema。

---

## 6. 验证结果

### 6.1 Git/基线

- `git fetch origin v0.3.0` 成功；origin 当前仍为 `3b48d2d`，本地 HEAD `8f1a3b9` 领先 4 个提交。
- 工作树在本轮前已经 dirty；v28 renderer/model/tests/assets 仍是 untracked，普通 `git diff` 不会显示它们。
- 本轮未合并、提交、推送或发布。

### 6.2 编译与测试

| Gate | 结果 |
|---|---|
| `compileDebugKotlin` | PASS |
| `compileDebugUnitTestKotlin` | PASS |
| direct JUnit | **112/112 PASS** |
| `lintDebug` | **0 errors / 9 existing warnings** |
| `assembleDebug` | PASS |

标准 Gradle `testDebugUnitTest` 仍在本机中文路径下发生既有 `GradleWorkerMain` / closed-pipe 子进程故障。测试源码先由 Gradle 成功编译，再使用相同已编译 classpath 直接执行 JUnitCore，112 项实际运行并通过。不能把标准 Gradle worker 的宿主故障称为测试通过，但它没有产生代码测试失败。

### 6.3 APK 身份与签名

```text
file: RX400h-Monitor-v0.3.5-v28-D058-R1-renderer-debug.apk
size: 2,656,023 bytes
SHA-256: 33021cb603fb760e92fd190cedffcf633ef1ef51541da5360dfcae3f3ee7fafa
package: com.guanyu.rx400hprobe.debug
versionCode: 28
versionName: 0.3.5-debug
minSdk: 26
targetSdk: 35
APK Signature Scheme v2: verified
certificate SHA-256: 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
embedded base commit: 8f1a3b9fb49359484c283ea6c3eaf69c631b4b45
git dirty: true
```

源码哈希：

```text
PixelDashboardView.kt
88e04f38b997ea5f82a2d613c1f5ca0d1039b00e90da1b345049927e9d9e2a21

PixelDashboardModel.kt
6876fabed17cc4964d22228e4d13f6de2627ac8dbb5f4c0b172b8307c67c219c

PixelDashboardModelTest.kt
978ded08b29634684b72ea08c313dc4616dc65cdb3e17ea0f8c6437f9339dcb5
```

### 6.4 GUI / emulator

未通过，也未伪装成通过：

- 硬件加速启动：失败；`emulator-check accel` 返回 6，明确报告 Android Emulator hypervisor driver 未安装。
- `-accel off`：后端开始初始化 SwiftShader，但 60 秒内没有 ADB，随后进程退出。
- 没有本轮 D-058 截图、settings smoke、rotation 或 gfxinfo 成绩。

环境记录：

```text
C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\outputs\d058-r1
```

---

## 7. 目标车机测试顺序

不要求专程长途。下一次正常用车即可：

1. 安装同签名 D-058-R1 APK，首次打开先不要下拉状态栏或切换页面，确认文字、数值、辉光和扫描线从第一眼开始正常。
2. 打开设置，确认浮层、滑杆和关闭按钮正常；停留数秒后关闭，确认车轮恢复、位置不跳。
3. 按开始并收到完整 SOC/HV 功率数据，确认 SOC 和功率自检轨迹、单游标、车轮、Idle 10%/100% 外观没有变化。
4. 正常行驶一段，并保留一段停车/ICE off；无需刻意延长。
5. 按结束保存并上传 ZIP。

分析必须分别报告：

- moving `speed > 1 km/h` weighted CPU / p50 / p90；
- stopped + ICE off weighted CPU / p50 / p90；
- PSS first / median / max / last；
- request effective Hz；
- deadline miss / late / expired / transport unavailable；
- capacity reject rate，而非只报次数。

---

## 8. 决策规则

- moving `<55%`、parked `<=20%`、画面正确：R1 达到当前建议门槛，停止复杂化 renderer。
- moving `55–72%`、画面正确：R1 有效但余量不足，进入 D-058-R2 halo-expanded overlap groups。
- moving `>=72%`：R1 不足，先补线程级 CPU / gfxinfo，再决定 R2，不能只凭猜测继续改。
- 画面有任何布局、残影、辉光接缝或设置恢复错误：R1 失败并回退，性能改善不能抵销视觉违约。
- transport/deadline/late/expired 出现新增回归：停止并核查，不推进 HA。

Idle Check 防抖仍是 D-059 独立补丁。当前 APK没有调整其阈值。HA 复刻和 5 Hz 继续冻结到本轮目标车机 gate 关闭以后。

