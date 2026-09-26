# D-062 加强精简：本地交付与审查报告

日期：2026-09-17。用户已批准“改”及“继续，加强精简力度”。本轮为 D061 上的行为保持型清理，不是新的采样策略或 UI 重设计。

## 结论

- 主程序 Kotlin：36 个文件 / 10,801 行 → 33 个文件 / 9,170 行，净减 **1,631 行（15.1%）**。按完整行数统计，含注释与空行，不把测试删除混入主程序缩减数字。
- 非调试上车测试 APK：8,284,266 → **7,130,205 字节**，减少 1,154,061 字节（13.9%）；未新增混淆或压缩选项。
- 144 项单元测试、41,143 次真实记录回复解析核对、148 帧独立逐像素检查、7 项 Android 隔离文件故障通过。debug / benchmark 均构建和 lint 通过。
- 同机 UI 回放 CPU 9.96% → 9.88%，基本持平，**不能称为明显 CPU 提速**。本轮主要价值是删去无用实现、减少重复工作和降低维护负担；采集路径节省尚无新实车测量。

## 身份与交付

源码工作目录：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\rx400h-Monitor-crt`。

分支 `v0.3.0`，HEAD `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45` + 原有 D055–D061 未提交内容 + D062。没有提交、push、远端 CI、实体设备安装或正式基线晋升。origin 为本地 mirror，已 fetch 未 merge；不是“GitHub 最新已同步”的声明。

开始前实查 D061 交付清单：95 个 app 路径全部匹配。比较与回退基准是 `../../deliverables/D061-AB-local-20260912/D061-source-review.zip`，不是较旧 Git HEAD。旧源码/原 APK/用户行车 ZIP 均保留；本轮未删除任何用户日志。

交付目录：`../../deliverables/D062-lean-local-20260917/`。

| 用途 | 文件 | SHA-256 |
|---|---|---|
| 上车测试候选，非调试 | RX400h-Monitor-v0.3.5-v28-D062-lean-test.apk | 2cb13247cfc0721d4b898fa1167d27ba977d636e088f770edcc0f05e9d65e509 |
| 本地验收 / 同模式性能对照 | RX400h-Monitor-v0.3.5-v28-D062-lean-debug-control.apk | af721035b3ee7095862baf9d449b3ac23eaff93c4f58f6936fe60a5a28edee7a |

二者覆盖同一包名 `com.guanyu.rx400hprobe.debug`，不需要同时安装。versionCode 仍 28；测试版 versionName 为 `0.3.5-D062-lean`。保留原项目测试签名，v2 验签通过，证书 SHA256 为 `77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192`。

目录同时提供 `D062-source-review.zip`、`manifest.json`、`source.sha256`、`D061-to-D062-app.diff`、`tracked-only.diff`。源码 ZIP 不含签名 key、构建缓存、.git 或行车原始 ZIP；原固定签名构建仍需本地仓库的原测试 key。完整逐文件变化/身份以 manifest 和 D061 专用 diff 为准，普通 git diff 含此前多轮工作。

## 实际删减与减负

| 文件 / 区域 | 本轮变化 |
|---|---|
| ResponsiveLayout.kt、ResponsiveViewGroups.kt、CrtGreenUi.kt | 删除未被当前像素仪表调用的旧布局和旧皮肤实现，共 954 行 |
| ProbeLogger.kt | 2,638 → 2,058 行；删除没有运行入口的旧启动扫描、旧中断恢复及其独占校验/补写辅助函数；保留现行未保存丢弃与正常保存、导出、校验 |
| EvidenceRecoveryPolicy.kt → ArchiveIdentity.kt | 删除过时恢复规则，仅保留正常归档仍在使用的会话身份核对；相关有效测试迁移 |
| MonitorSessionState.kt | 删除无消费者的 RECOVERING 状态和 withLease 旧接口；真实使用的可取消会话锁保留，并由串行、取消、异常释放测试覆盖；按钮状态复用固定不可变对象 |
| LatencyWindow.kt | 固定有界排序缓存；写入后第一次求分位数排序，之后重复读取不分配数组、不重复排序；相同输入结果不变，代价是一份固定 scratch 数组 |
| MainActivity.kt、ObdParsers.kt、ProbeModels.kt | 标准回复只组装一次 payload，解码与日志共用；十六进制转写去掉逐字节 Formatter；失败信号使用已有固定绑定 |
| MainActivity.kt | 数据和派生 Idle 更新后合并 UI 发布；无新数据的 header/等待循环不空发；前台保留 500ms 新鲜度心跳，后台停 UI 心跳，回前台立即发布；错误/断连仍请求发布 |
| PixelDashboardView.kt、PixelDashboardModel.kt | 显示值/有效性未变时不重复格式化；生产绘制与测试共用能量条数学，通过固定可变输出避免逐帧临时对象；删掉未读 kind 字段 |
| SignalStore.kt、ProbeModels.kt | 删除只写不读、与现有更新时间重复的字段；新鲜度遍历复用固定数组；值、版本、时间戳含义及过期阈值不变 |
| CapacityAwareScheduler.kt | 仅删除无消费者的无参数兼容访问器；策略、成本公式、准入与排序规则未改 |
| DevicePickerActivity.kt、app/build.gradle.kts、gradle.properties | 唯一 AndroidX 使用点改为 API26+ 原生边距接口，API28 缺口/API30 Insets 显式版本保护；移除 Core KTX 依赖及 Jetifier/AndroidX 开关 |
| app/build.gradle.kts | benchmark 不再编入 debug 验收 Activity、manifest 和回放素材；debug 仍含完整验收入口，低频真实诊断指标保留 |

没有靠减少日志、调低采样频率、修改刷新/自检动画时长来缩减开销。没有新增队列、后台同步架构、通用框架或新依赖。

### 保持与触碰边界

确实触碰了 MainActivity、SignalStore 的实现和解析入口，不能声称“完全没碰 core”。但没有改变七条白名单请求、两策略及默认 HA、周期/调度选择、Bluetooth/ELM 运输、数值公式、Idle 阈值或信号质量语义。

RequestTable、SamplingStrategy、IdleCheckState、DashboardFreshness、Elm327Client、PublicLogExporter 与 D061 逐字节相同；全部 main assets / resources 相同，用户布局、图像、字体、扫描线/辉光设置及动画参数没有改。日志 CSV/JSON 格式、检查点频率、正常结束和保存路径保留；不为本轮性能数字关闭取证。后台 dashboard snapshot Hz 自然变为零，不代表采集停止，勿将该指标与 acquisition Hz 混用。

## 验证证据

所有本轮证据在 `../../outputs/d062-lean-20260916/`。目录日期是开始日期，报告为本地次日。

1. **最终构建 / lint**：`build-final-03.log`，debug 与 benchmark 均成功；各 0 errors / 15 warnings。现有警告未屏蔽，包含已有资源/兼容接口提示。前两次追加验证暴露旧测试接口引用与 lint API 分支识别问题，已修复；失败日志保留，不冒充一次全过。
2. **单元测试**：Gradle 重新编译并导出实际 runtime JAR 和 25 个测试 class，直接 JUnit 每轮 **144/144**；两轮分别加载 9月14日的 27,678 次和此前的 13,465 次回复。帧、状态、共享标准 payload、原始证据 hex 核对通过，另有畸形/随机解析、分位数环回、调度、Idle、会话锁及像素模型测试。`junit-final-sep14.log`、`junit-final-sep09.log`。
3. **测试数说明**：D061 为 165。移除旧布局 17 项、无入口恢复规则 7 项、旧恢复列表 1 项；保留迁移的 2 项归档身份测试；新增分位数 2 项、复用动画输出 1 项、共享 payload 1 项，因此为 144。没有为了全绿删除仍在使用的并发/保存测试。
4. **标准测试入口的限制**：`:app:testDebugUnitTest` 再次失败，报 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`，尚未开始执行正常测试。这是之前已存在的本地启动器问题，`gradle-tests.log` 独立记录，不能写成标准 Gradle test 通过。上述 JUnit 是同一批编译产物的替代执行，不是旧缓存结果。
5. **独立完整画面 oracle**：debug 最终 APK，40 输入 / **148 帧通过**，涵盖自检、过渡、失效/恢复、Idle；`pixel-oracle/`。这比较完整独立清晰图层/辉光合成，不是把同一份缓存与自己比较。原绘图资源完全相同。
6. **文件故障**：debug 最终 APK，`lifecycle/` **7/7**；模拟器隔离的 Android 私有文件场景，包括旧未保存清理/保存失败等。并非实车完整 Start→采集→End 集成。
7. **GUI**：API26、1280×720、160dpi，debug / benchmark 均验证主界面、无已配对设备页、设置浮窗、后台返回。主界面返回前后及设置浮窗返回前后截图完全相同，两构建也相同。`gui-debug/` 验证稳态/HA 选择持久化并恢复 HA；`native-gui-debug/`、`native-gui-benchmark/` 检查原生边距/返回。未称为自然第一帧、高 API 缺口、多尺寸或 LIVE 采集验证。
8. **包检查**：`manifest-benchmark.txt` 仅 MainActivity / DevicePickerActivity，无三个 debug 验收入口，无 debug 标记；ZIP 无回放素材，DEX 不含 AndroidX Core。两包签名日志分别保存。
9. **离线工具测试**：现有 tools Python 测试 3/3；`git diff --check` 通过（另有已有行尾转换提示）。

补充旧包对照：`native-gui-d061-control/` 在同一模拟器安装原始 D061 benchmark，设备页、主界面、设置、返回后的六张截图 SHA256 与 D062 benchmark 全部相同。设备空列表的现有布局没有因原生接口替换改变。新版 API28+ 缺口路径只完成版本检查和 lint，未声称已在该系统实测。

## 性能：只报告测到的范围

同一个已存在的 API26 x86_64 模拟器，运行时限制 **1 核 / 1536MB**，1280×720、SwiftShader。D061 精确 debug APK 对 D062 精确 debug APK，各一次 120 秒 stress5，同样设置、各新进程。剔除前 10 秒与结束尾段，使用相同分析器。

| UI-only 指标 | D061 | D062 |
|---|---:|---:|
| 整个 debug 进程 CPU，100%约一核 | 9.96% | 9.88% |
| 交付模拟输入 / 计划输入 | 601 / 601 | 601 / 601 |
| 跳过输入 | 0 | 0 |
| 稳态帧率 | 19.47 Hz | 19.97 Hz |
| 稳态最大输入迟到 | 40ms | 24ms |
| 稳态 PSS | 50,054–51,310 KB | 48,394–49,638 KB |

证据 `replay-d061/`、`replay-d062/`、`runtime-summary.json`。两轮未观测到 AndroidRuntime 错误。CPU 差异约 0.08 个百分点，只做单对照，不能认定显著提速；迟到/内存也只是本轮观测，不是上限保证。此回放直接驱动仪表，不执行真实 MainActivity 采集/日志链路，不能测出本轮所有采集侧优化收益；更不能证明真实 5Hz、MX+ 加速或“八成拟合车机”。

## 未关闭问题与后续边界

- 实车真实刷新率、CPU、长时内存、断连/重连、LIVE 策略切换仍需自然使用数据；本轮不要求专程长途。
- MX+ STPX/STBC 仍是测试研究，不是启用路径；目标切换耗时没有被本轮“解决”。
- 扭矩边界/特殊值的含义、实际引擎功率可信性仍未定论；本轮未改公式或凭推测过滤。
- 日志 JSON 构造、正常保存、身份/归档校验仍是有用功能，不能因为耗时就称为废代码。若继续动它们，要单独证明取证内容、背压与故障行为不变；当前没有降低日志保留范围。
- 删除的旧代码可从 D061 源码包恢复；不恢复旧阻塞启动的业务政策。

## WORK_FOLLOWUP

使用 D062 非调试候选进行日常目标车机验证时，记录实际 APK 身份；不要再安装含调试回放入口的控制包来与非调试旧版直接比较 CPU。优先对照同策略、相近行程下的采集 Hz、迟到/丢弃、process CPU、GC、UI/后台状态和日志完整性。若出现 UI 或保存回归，保留错误证据并回到原 D061 APK，不改用户布局来绕过问题。本地候选交付不是项目整体完成或正式发布。
