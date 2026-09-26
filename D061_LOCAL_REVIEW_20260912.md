# D-061：A+B 本地候选交付与审查报告

日期：2026-09-12。本轮本地实现、构建、验收与打包完成，可用于有限目标车机验证；不代表整个 V0.3.5 或实车 5Hz 已完成。

主要结论：保留画面的前提下，相同 debug 构建模式的仪表回放 CPU 约降低31%。另提供非调试测试版，进一步隔离调试开销。蓝牙接收/解析已优化，STPX/STBC 批量路径仍未启用。

交付目录：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\deliverables\D061-AB-local-20260912`。

- 建议测试包：`RX400h-Monitor-v0.3.5-v28-D061-AB-test.apk`（benchmark，非调试），SHA256 `ea2525dc148dfc5247bc3f1ed7c623cfd17e6213a421330d1838c820522d8835`。
- 审核/性能对照包：`RX400h-Monitor-v0.3.5-v28-D061-AB-debug-control.apk`，SHA256 `56779960efde0668f0aaf33d366cb229133c48ac3e6dac6b69a7ccec23cf4db7`。不用两份都装；它们覆盖同一个应用。
- `D061-source-review.zip`、`manifest.json`、`source.sha256`、`D060-to-D061-app.diff`、`tracked-only.diff`。源码 ZIP 不含签名私钥、缓存或原始行车 ZIP；完整目录仍在本地原仓库，固定签名构建需使用仓库原有测试 key。

## 1. 身份与边界

- 源码：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\rx400h-Monitor-crt`。
- 分支 `v0.3.0`；HEAD `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45` + 原有 D055–D060 dirty 内容 + 本轮 D061。不是 exact-clean commit，也没有提交、push、PR、远端 CI 或正式基线晋升。
- origin 是本机 mirror，已 fetch 但未 merge；不是已核查 GitHub 最新分支的声明。
- 对照旧 APK：`../../deliverables/D060-local-20260908/RX400h-Monitor-v0.3.5-v28-D060-local-debug.apk`，SHA256 `db6b33fa2038ae6f9711fc87e0d83907d99fb09d42d1d3b8f156f51965811297`，原文件保留。
- 修改前 app 源码：`../../outputs/d061-ab-20260911/pre-change/`。不能把普通 `git diff` 中原有 scheduler/Idle/UI 修改全部归到本轮。
- V0.1.9 仍 VOID；曾出现辉光像素差异的直接 HWUI 方案仍未采用。

## 2. 本次真正进入运行路径的内容

### A：减少重复绘制，保留用户画面

`PixelDashboardView` 将原来仅用于辉光合成的 8×8 离屏分区，延伸到清晰图层的重绘。每块缓存其第一个动态元素之前的静态内容，其后的静态/动态元素仍按原顺序绘制；不重新分组透明度、不改字体或坐标。SOC、能量填充和滑块比较实际整数像素位置，位置没变时不重复重建画面。车轮沿用已有独立绘制，不改方向。

新增一张固定 1280×720 ARGB 缓存，理论约 3.52 MiB。没有实时 shader/blur、Compose、WebView 或新依赖。原图形、中文物理像素规则、扫描线与辉光参数、10%/100% Idle 显示均保持。

正确性 oracle 独立完整绘制清晰图层，再完整合成辉光，分别与优化缓冲逐像素比较；不再拿同一份可能已经缺像素的缓存自己对自己。昂贵的 oracle 仅显式验收时运行，不用于性能数据或正常会话。

### B：数据到达即唤醒，解析一次复用

- 每条蓝牙连接一个阻塞读取线程、一个命令执行者；取消原来的 `available()` + 10ms 轮询等待。不是向串行适配器同时发送多个命令。
- 固定 16 KiB 字节环、对应到达时间数组、2 KiB 批量缓冲；内存不随会话长度增长。关闭先关闭该线程捕获的 socket，唤醒等待读取；不得关闭后来重连的 socket。
- 缺少 `>`、EOF、超量回复、溢出或异常边界会使连接失效，不假定回复已结束。命令之间只以零等待方式清除已缓冲的行尾空白；发现实质性旧回复直接报错。
- CAN 文本不再反复用正则拆成多个副本；解析出的帧在状态识别和解码入口间共享。接收和解析代码有改动，**解码公式没有改动**。CF 原有部分帧策略也没变。
- First-byte / prompt 诊断时间现在来自输入线程观察到的批量到达时间，不是旧轮询醒来时间，也不是 CAN 总线硬件时间戳；旧/新细分延迟不能不加说明直接比较。

### 仍未启用的 B 扩展

STPX 与小批次 STBC 仅有白名单编码/错误边界的 JVM 研究测试，没有生产 socket 调用、用户设置或自动探测。现有记录为 STN2256 v5.6.24，STBC 固件支持、STPX 计数与分段格式都还不能由本地模拟证明。此次 `mxp_wire=legacy`，仍发送原七条车辆请求：

```text
7E0 -> 7E8 : 01040C0D0E10 2 / 01050607 1 / 21CDF3 3
7E2 -> 7EA : 21C3 6 / 21C4 5 / 21CF 4
ATRV
```

初始化序列、ATSH 切换仍在；没有开启连续 CAN 监听、任意请求、固件更新或并行 ECU 会话。不能宣称本版已经消除了地址切换耗时，或已经实现 HA 的全部通信方式。

### 非调试对照与测量

增加 `benchmark` 构建，版本名 `0.3.5-D061-AB`、versionCode 28、包名 `com.guanyu.rx400hprobe.debug`，固定原测试签名。`debuggable=false`、不压缩混淆，保留同源码诊断入口，用于隔离 ART 调试开销；它不是正式 release 构建。

`performance.csv` 末尾新增六列，旧列顺序不变：

```text
renderer_window_end_uptime_ms,renderer_window_ms,renderer_rebuilds,
renderer_rebuild_wall_ns,renderer_rebuild_thread_cpu_ns,renderer_hardware_canvas
```

每约 5 秒发布一个不可变渲染窗口，运行线程读取最后一个已发布窗口。空值代表没有数据，不能记作零；重复 `end_uptime_ms` 是同一窗口，不能累加两次。该窗口与 performance 行时间不必严格对齐，不能据此把 CPU 分项强行相加。渲染 thread CPU 不是 GPU 时间，hardware Canvas 也不证明辉光已经在 GPU 算。

## 3. 修改文件清单（相对于 D060 app 快照）

| 文件 | 本轮作用 |
|---|---|
| `app/build.gradle.kts` | 非调试 benchmark 构建，固定签名/同测试源 |
| `PixelDashboardView.kt` | 清晰图层局部重绘、静态前缀缓存、像素变化抑制、独立 oracle、渲染窗口 |
| `PixelMotionDamage.kt`（新增） | 固定 5 个整数，跟踪实际填充/滑块变化 |
| `RendererWorkSample.kt`（新增） | 低频不可变诊断窗口 |
| `ArrivalInput.kt`（新增） | 有界阻塞接收及断开唤醒 |
| `ElmPromptReader.kt`（新增） | 批量读取、prompt/超时/边界与初始化 drain |
| `ElmResponseParser.kt`（新增） | 复用 CAN 帧进行状态分类 |
| `Elm327Client.kt` | 接入接收线程、单次帧解析，保持单命令所有者 |
| `ObdParsers.kt` | 字符扫描、缓存帧参数、预存常量，公式不变 |
| `ProbeModels.kt` | CommandResult 附加可选已解析帧 |
| `MainActivity.kt` | 六个解码入口复用帧、记录传输标识和渲染窗口 |
| `ProbeLogger.kt` | performance.csv 追加六列，无会话保留语义变化 |
| `ArrivalInputTest.kt`、`SharedCanParserTest.kt`、`PixelMotionDamageTest.kt`、`MxPlusWireExperimentTest.kt`（新增） | 接收边界、解析一致、像素量化、未启用封装测试 |

另有 `D061_AB_PLAN.md`、本文、项目状态/决策/路线图/变更/证据/交接/基线入口文档，以及 `tools/prepare_d061_parser_corpus.py`、`tools/export_d061_manifest.py`、`tools/capture_d061_benchmark_gui.py`。交付清单包含所有已有未跟踪源码，附 D060→D061 专用 app diff，避免旧 dirty 变化混入本轮摘要。

RequestTable、CapacityAwareScheduler、SamplingStrategy、SignalStore、IdleCheckState、会话策略、用户布局 JSON/图片/字体没有因 D061 改动。两策略仍是稳健和默认 HA 复刻，不新增四档、不自动降级。

## 4. 已运行验证

证据根目录：`../../outputs/d061-ab-20260911/`（任务于 9 月 11 日开始，9 月 12 日收尾）。

- `build-final.log`：导出当前编译后的测试 runtime、lintDebug、lintBenchmark、assembleDebug、assembleBenchmark 全部成功。两种 lint 均 0 errors / 8 warnings，原弃用/资源警告保留。
- `junit-final.log`：165/165 direct JUnit 通过，没有跳过所配置的实车 corpus。24 个编译测试类。最后一次仅调整研究测试的命名，错误回复不叫“成功完成”；两个最终 APK 哈希未变。
- `standard-test-01.log`：标准 Gradle 测试 worker 因 `ClassNotFoundException: GradleWorkerMain` 启动失败；不能称标准测试任务通过。独立 JUnit 使用当前 Gradle 编译的 runtime JAR 与同一测试类，不使用旧产物。
- 新解析器与旧解析器核对 7,000 条生成/畸形 CAN 行；另将 9 月 9 日原始 ZIP 的 13,465 次事务逐一核对帧、日志中的状态分类及共享帧解码输出，全部一致。原 ZIP SHA256 `b8e7ea50456f806739d33d6832c5b700acf0d919b00c64059f4feecf1c5996e3`。派生 corpus 在 outputs，未改原文件、未并入源码。
- Debug 精确 APK `56779960efde0668f0aaf33d366cb229133c48ac3e6dac6b69a7ccec23cf4db7`：`pixel-debug-final` 148 帧独立完整清晰/辉光对照通过；覆盖自检、正负过渡、失效/恢复和 Idle。
- `gui-debug-final`：优化/完整绘制截图文件哈希相同；设置选择稳健→重开持久化→恢复 HA，原画面恢复。API26 1280×720 framebuffer，App View 1280×672（系统导航占 48px），不是隐藏导航后的全屏车机验证。
- `smoke-debug-final`：7/7 隔离 Android 文件故障通过。没有调用实际蓝牙；不能当真实 LIVE 自动切换端到端通过。
- 最终非调试 APK `ea2525dc148dfc5247bc3f1ed7c623cfd17e6213a421330d1838c820522d8835`：`pixel-benchmark-final` 146 帧独立 oracle 通过，`smoke-benchmark-final` 同样 7/7 通过；`gui-benchmark-final` 九张截图与对应 debug 图逐像素一致，其中动态预览排除未同步相位的两处车轮，比较 903,582 像素，其余每张完整比较 921,600 像素。
- 初次 benchmark GUI 核对脚本因系统 Python 缺少 numpy/Pillow 停在导入阶段；改用本机已捆绑运行库后完成，无下载/新安装。此为验收工具环境问题，不是 App 失败。早期 build-01 编译错误和修正前输出也仍保留，不隐藏失败记录。
- 回放导出 Python 3/3 通过（`python-tests.log`），统计回归单独记录在 `summary-tests.log`。
- 固定 v2 签名均通过，证书 SHA256 `77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192`。

## 5. 同输入性能对照

固定 API26/x86_64/WHPX/SwiftShader；单 guest 核（online_cpus=0）、实际 1536 MiB 配置（MemTotal=1,531,004kB），1280×720/160dpi。每次新进程，不清数据，原 CRT 设置保持：扫描线75%/宽1px/间隔1px、辉光100%/半径8px。非调试包不能 run-as 读私有偏好，改以相同设置页面像素及未改效果的操作序列核对，不能把空的 effect_preferences 当成默认参数。

601 次输入/120 秒、排除前 10 秒及收尾重叠窗口；CPU100%约一个 guest 核。以下是整段仪表测试进程（含测量），不是单独 UI 线程CPU。数据来源：`runtime-summary.json`。

| 精确 APK / 轮次 | 加权 CPU | 窗口绘制 Hz | 帧总耗时均值 | 重建 thread CPU / 次 | PSS范围 kB |
|---|---:|---:|---:|---:|---:|
| D060 debug 原包 | 14.55% | 20.23 | 18.07ms | 4.51ms | 47,077–48,169 |
| D061 debug 第1轮 | 10.07% | 19.08 | 17.65ms | 3.02ms | 50,103–51,391 |
| D061 debug 第2轮 | 10.12% | 19.57 | 17.93ms | 3.01ms | 50,309–51,561 |
| D061 非调试测试包 | 9.07% | 18.83 | 19.24ms | 2.75ms | 46,236–47,472 |

四轮均601/601输入、零跳过、稳态帧总耗时超过50ms为0、FrameMetrics回调丢失0，未观察到AndroidRuntime崩溃。稳态输入最大迟到分别24/31/28/30ms。渲染重建从旧版约20.22次/秒降到新版约15.93–16.10次/秒，仍按实际像素变化更新；不是采集降速。

相同debug方式的两次新版平均CPU约10.10%，对旧版14.55%约降低30.6%；非调试与旧debug的整体差约37.7%，但其中含构建方式差异，**不能全算代码优化**。非调试帧总耗时均值本轮略高于debug，不能声称所有指标全面变快或60fps。仅一轮旧版和一轮非调试、两轮新版debug，属于受控短测，不是跨多日显著性结论。

相同debug方式PSS约增加3MiB，和新增固定缓存相符；非调试PSS更低不能用来抵消/隐瞒缓存的实际增加。每轮约两分钟也不能证明没有长期内存增长。接收环/时间数组只在连接时分配，本组UI-only测试未包含其约0.15MiB成本。

这组只加载真实仪表 View 和合成的 5Hz 输入，不含蓝牙、ECU、运行日志持续写入或实际车机 GPU。它不是 80% 车机拟合，不是 CAN 5Hz 验证，也不能从渲染帧率推导采集频率。

## 6. 未关闭项 / WORK_FOLLOWUP

1. 优先使用最终 test APK 进行日常、有限验证，保持设置里的 HA 复刻；没有必要为这版专门长途。正常停车后再操作测试。对比实际请求Hz、超时、断开/重连、有效数据年龄、CPU/PSS和新增渲染窗口，不只看动画流畅度。
2. 确认 MX+ 的真实阻塞流关闭/唤醒、分段/延迟 padding、系统休眠后重连。这些 JVM 边界测试不能替代 Android Bluetooth 栈。
3. STPX 先取得目标固件下正负/超时/多帧回复及计数含义证据；STBC 先确认该固件支持，再另行受控实验。不能为了凑 A+B“完成”默认打开。
4. 车轮方向反向、扭矩 -256/254 边界、Idle 最新原始回放、完整 LIVE 策略交接、长时内存及真实5Hz继续保留为独立未关闭项；本轮没有用改公式、删日志或改用户画面掩盖问题。
5. 本轮回滚方式是保留的 D060 APK；先正常结束并保存当前会话再切换安装。原日志和旧交付不删除。相同签名已在本机模拟器覆盖安装；不保证目标车机安装器行为。

只关闭本轮已明确执行的本地 gate，绝不把全部 V0.3.5 工程或实车性能宣布完成。
