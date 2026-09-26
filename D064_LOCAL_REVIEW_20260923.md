# D-064 本地交付与复核报告

日期：2026-09-23。版本：V0.3.5 / versionCode 29 / D064-realtime-3hz。

## 1. 结论与身份

已完成用户批准的「只留实时仪表和本机设置」本地实现、构建和可执行的本地检查。不是正式实车基线；本次没有提交、推送、发布、远端CI或实体设备安装。

- 源码位置：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\rx400h-Monitor-crt`
- 本地分支：`v0.3.0`，HEAD：`8f1a3b9fb49359484c283ea6c3eaf69c631b4b45`，dirty=true。原D055–D063改动完整保留。
- origin为本地镜像；本轮已fetch，但不等于验证了GitHub最新状态。
- 精确比较基线为 `../../deliverables/D063-lean-runtime-local-20260917/D063-source-review.zip`，不是旧HEAD。
- 基线源码ZIP SHA256：`d5c759eb3557b2d9ae04b7684d9ea77c6a30c24dbfb2b529128db6c23f348bb0`。
- 新交付目录：`../../deliverables/D064-realtime-local-20260923/`。实际文件/hash/源码逐文件清单以该目录 `manifest.json` 为准。
- 原电脑日志、D063源码/APK和既有证据均未删除；本轮删除的旧模块可从D063源码ZIP恢复。

## 2. 现在怎样使用

1. 打开应用，自动尝试连接已选OBD一次；首次没有设备时，在「设置→蓝牙设备」选择已在系统配对的MX+。
2. 连接和初始化成功后自动显示实时数据，不再按开始，也没有结束按钮。
3. 侧视车辆图片原区域是手动重连入口；连接/初始化中重复点按不会启动第二条链路。
4. 切到后台仍采集，但停止仪表动画和可见刷新；返回前台不主动重连、不重新自检。
5. 断链或车辆两组都持续无有效数据后停止，等点车图重连。不会通过整机开关蓝牙去重连，不干涉音乐蓝牙连接。
6. Android返回键直接取消本应用连接并退出，不保存，不弹恢复对话框。设置浮层自身的X只关闭浮层。
7. 数据不再落盘、导出或恢复；以后故障反馈依靠可见表现、照片/录像等，不应再要求用户提供本版行车ZIP。

注意：后台继续采集指应用进程仍被系统调度时；没有增加前台服务、强制唤醒或阻止系统杀进程的机制。车辆断电不能保证运行，也不承诺留下数据。

## 3. 交叉检查：删日志后保住了什么

| 项目 | 本轮结果 | 验证边界 |
|---|---|---|
| 自动启动、重连、返回退出 | 从保存状态机中独立出来；每次连接拥有自己的客户端 | 无适配器GUI通过；真实连接中取消待实车 |
| 单一车辆连接 | 单worker、进程互斥、旧连接关闭后新连接才能工作 | 互斥单元通过；真实设备切换未验证 |
| 迟到回调 | 只有当前Run可发布状态；旧close不引用新客户端 | 实现审查，非完整蓝牙故障注入 |
| 不可用判断 | 10秒同时缺失引擎+混动有效字段时停；ATRV/OK不能续命 | 单调时钟单元测试通过；在事务边界检查，不是硬实时10秒中断 |
| 失效数据 | 停采集后数字失效、轮停、Idle暗；未知不伪装0 | 新鲜度/像素测试通过 |
| 同值新样本 | 仍更新采样时间及版本，功率两源配对规则不变 | 3Hz重复值单元通过 |
| Idle Check | 原进入/保持/退出迟滞保留；剥离原因统计和计数器 | 数值测试/40组UI样本通过；无本轮实车结论 |
| 时钟 | 新鲜度、调度、10秒检测使用单调时间 | 删除校时统计不影响以上功能 |
| UI自检/动画 | 新连接新数据触发；普通前后台不重置；车轮转向已修正 | 像素/动画测试；实车方向观感待确认 |
| 旧按钮 | 不绘制、不命中、不为它们生成缓存 | Android hitbox和两包GUI通过 |
| 设置 | 设备选择迁入；保留原CRT参数和本机偏好 | 模拟器设置/设备入口/返回通过 |
| 旧数据迁移 | 只删明确未保存的应用私有旧会话，失败不挡连接 | Android真实文件测试10项中的9项覆盖迁移 |

旧迁移采用保守策略：只有匹配session_id和application_id、status=active、明确无archive_name/ended_at且evidence_complete=false的已知工作文件夹才可删；保存/发布标记、ZIP、异常JSON、未知文件、嵌套目录、软链接、保存到一半等全部保留。不会创建新记录目录，不会删除保存的ZIP，不影响设备/CRT设置。

## 4. 调度、协议和数据语义

仅HA顺序；删除稳健策略、成本学习、策略选择/切换保存链。四个核心请求周期334ms，配置目标约2.994Hz。没有累积待补发队列；慢链路只跟随实际完成速度，不为了补齐历史频率连续补旧请求。

| 目标 | 请求 | 目标周期 |
|---|---|---|
| 7E0→7E8 | `01040C0D0E10 2` | 334ms |
| 7E0→7E8 | `21CDF3 3` | 334ms |
| 7E0→7E8 | `01050607 1` | 3000ms |
| 7E2→7EA | `21C3 6` | 334ms |
| 7E2→7EA | `21C4 5` | 334ms |
| 7E2→7EA | `21CF 4` | 5000ms |
| 适配器 | `ATRV` | 3000ms |

核心顺序std→cd→c3→c4，慢任务在对应间隙插入。所有OBD请求串行，一次只发一条。仅用于旧日志的ATI/STI/AT@1/ATDP/ATDPN识别查询不再发送；原ELM初始化/协议配置和七条车辆运行请求保留。没有STPX/STBC、CAN监听、新ECU/服务或扫描。

确实改动了scheduler、SignalStore结构、transport结果结构和decoder返回模型；不能称UI-only。删除无消费者诊断/原始HEX/重复电压电流温度副本，不改变数值解码公式、实际字段语义、prompt边界保护或新鲜度。-256/+254扭矩的车辆含义仍未确认，本轮没有把它们改成0或自创无效值规则。

Idle仍要求有效warmup及同源新鲜数据：进入900<rpm<1100、|ICE kW|≤0.05、speed≤55持续1秒；保持850<rpm<1200、|ICE kW|≤0.30、speed≤55；超出保持区持续1秒才退出。前提失效立即熄暗。非激活10%、激活100%保持。

## 5. 精简结果与性能边界

- 主程序Kotlin：32文件/9,044行→23文件/3,525行，减少5,519行（61.0%）。不含测试、工具和资源。
- benchmark APK：6,446,609→6,378,581字节（约−1.1%）；debug：9,674,208→7,812,502字节（约−19.2%）。
- 用户布局JSON、图片、字体等 `app/src/main/assets/` 与D063逐字节一致；旧按钮定义仍在原设计JSON中作参考，但加载器提前跳过，不生成View/缓存/命中区。
- 不新增依赖、WebView、Compose、shader框架。原字体与图片资源保留，因此代码减少比例不会等于APK减少比例。
- 已去除产品日志写入/序列化/性能统计/结束保存开销；这是结构事实，**没有测得本轮实际车机CPU下降百分比**。没有把92项单元通过当作3Hz或音乐共存实测。
- `System.nanoTime`等仍用于串口超时边界，不能当性能监控删除。debug-only像素验证和工程回放工具不进入推荐benchmark包；不因删除产品监控而删除本地工程验证能力。

## 6. 实际测试结果

证据目录：`../../outputs/d064-realtime-20260923/`。

| 检查 | 结果 | 文件/限制 |
|---|---|---|
| 标准testDebugUnitTest | 未通过 | `gradle-unit.log`：GradleWorkerMain宿主启动失败，非产品断言失败 |
| 最终direct JUnit | 两轮各92/92 | `junit-final-sep14.log`、`junit-final-sep09.log`；19个测试类，使用本轮编译的runtime JAR |
| 历史协议回复 | 27,678+13,465通过 | frame/status/cached decoder结果一致；不是本轮车辆采样 |
| 两种lint | 0错误/各4警告 | API12备份规则、应用图标、XML工具构造函数、固定中文；未冒称零警告 |
| assembleDebug/Benchmark | 通过 | `build-final.log` |
| 固定签名 | 两包v2通过 | `signature-debug.txt`、`signature-benchmark.txt` |
| Android迁移/命中区 | 10/10 | `native-smoke-final/result.json`；仅新建隔离fixture，未碰用户旧日志 |
| 独立像素oracle | 40组/157帧通过 | `pixel-final/result.json`，最终debug APK |
| 最终debug/benchmark GUI | 通过 | `gui-debug-final/`、`gui-benchmark-final/`，8张稳定画面逐张一致 |
| 协议/死代码/assets/diff核查 | 通过 | 白名单单元、R8无旧模块、assets哈希一致、diff --check无空白错误 |

GUI配置API26、1280×720、density160、1核/1536MB、SwiftShader、read-only AVD。导航栏占48px，实际View1280×672，按既有适配合同显示。验证主界面、旧按钮失效、设置、设备入口、前后台返回、无设备车图点按、返回退出和重新打开。

立即截图捕获了系统启动过渡，不足以证明「实际第一帧」；稳定界面可读、无布局变化。本轮不包含竖屏/多分辨率/API28+刘海测试，不声称模拟器等效目标车机。

D063的150项变为92项，是删除已退役logger/导出/策略搜索/旧状态机的测试，并加入新HA、迁移、3Hz数据合同测试；不是只选成功用例运行。首次smoke工具因等待立即finish的fixture超时，已改为不等待绘制，最终工具运行10/10成功；失败原始输出未抹去。

## 7. 精确安装包

推荐：`RX400h-Monitor-v0.3.5-v29-D064-realtime-3hz-test.apk`（benchmark，非debuggable、R8精简、无debug测试活动）。

- SHA256：`3a4ed745b18eb19843b04e3d038047d383301182161d41b67f835ac8376496f5`
- 大小：6,378,581字节；包名：`com.guanyu.rx400hprobe.debug`；versionName：`0.3.5-D064-realtime-3hz`。
- debug对照SHA256：`748151c02125f928c4010c120275ca3321fa2fc0ce6c7a57effaeeec567ae902`。不作为日常安装首选。
- 签名证书SHA256：`77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192`，与D063相同。
- R8 mapping/usage/configuration/seeds随包保存；不能拿其他构建的mapping解释此包。
- 源码包不含签名私钥、.git、缓存、电脑原始日志；在已有仓库及原密钥/本机构建工具环境下重建。文件哈希而非单一旧HEAD标识这次dirty源码。

## 8. 修改文件范围

以下Kotlin均在 `app/src/main/java/com/guanyu/rx400hprobe/`。

- 新增：`HaScheduler.kt`、`LegacySessionCleanup.kt`。
- 修改：`MainActivity.kt`、`MonitorSessionState.kt`、`PixelDashboardView.kt`、`PresentationContract.kt`、`RequestTable.kt`、`RequestSignalBindings.kt`、`SignalStore.kt`、`ProbeModels.kt`、`IdleCheckState.kt`、`ObdParsers.kt`、`Elm327Client.kt`、`ElmPromptReader.kt`、`ElmResponseParser.kt`、`ArrivalInput.kt`。
- 删除：`ProbeLogger.kt`、`PublicLogExporter.kt`、`ArchiveIdentity.kt`、`LogArchiveNaming.kt`、`PerformanceTracker.kt`、`RendererWorkSample.kt`、`WallClockAdjustmentDetector.kt`、`LatencyWindow.kt`、`CapacityAwareScheduler.kt`、`DeadlineScheduler.kt`、`SamplingStrategy.kt`。
- 资源/构建：`app/src/main/AndroidManifest.xml`、`app/src/main/res/values/colors.xml`、`app/build.gradle.kts`、`.github/workflows/build-apk.yml`。
- debug：修改manifest/`DashboardPreviewActivity.kt`/`DashboardReplayActivity.kt`；新`RealtimeSmokeActivity.kt`替换旧`LoggerRecordParityChecks.kt`和`SessionLifecycleSmokeActivity.kt`。
- 单元：新增`HaSchedulerTest.kt`/`RealtimeClosureTest.kt`；调整`ArrivalInputTest.kt`/`IdleCheckStateTest.kt`/`MonitorSessionStateTest.kt`/`RequestSignalBindingsTest.kt`/`SharedCanParserTest.kt`/`SignalStoreTest.kt`；旧日志/策略测试退役。
- 工具：`capture_d064_main_gui.py`、`capture_d064_smoke.py`、`export_d064_manifest.py`；工程测试工具不进入benchmark APK。
- 文档：本报告、D064清单、PROJECT_STATE/DECISIONS/ROADMAP/CHANGELOG/CODEX_HANDOFF/EVIDENCE_INDEX/BASELINE_README/BASELINE_MANIFEST/GITHUB_BUILD_AND_BASELINE_WORKFLOW。

完整逐文件SHA与变化清单见manifest；专用 `D063-to-D064-app.diff` 只对比确切D063快照，避免将原D055–D063变动再次计入本轮。`tracked-only.diff`只是辅助，不能代替含未跟踪文件的源码ZIP。

## 9. 仍未证明与下步

- 真MX+首次自动连接/LIVE、采集中换设备、连接/初始化中Back、断链后点车图、系统睡眠唤醒、蓝牙音乐共存：实现具备保护，但尚无本轮真实适配器证据。模拟器无OBD，不能填成已通过。
- 实际3Hz不是硬保证。3Hz是降低目标；音乐问题也可能受车机蓝牙栈影响，不能承诺彻底消失。
- 10秒失联只表示当前车辆数据不可用，不确认熄火；事务正在等待回复时可能较阈值延后检查，链路错误也可能更早停采集。
- -256/+254扭矩边界语义未解决，不夸称物理准确性修复。
- 本轮不继续追5Hz、四档策略、自动保存/恢复、日志统计、固件实验、CAN监听、手机竖屏或新车通用化。

下一步只需自然用车时检查「开机自动连→播放音乐时仪表→切后台/返回→手动重连→返回退出」，异常时停车后拍照/描述。先以本地测试候选交付，不要求为验证专门长途驾驶，也不恢复已取消的日志模块。
