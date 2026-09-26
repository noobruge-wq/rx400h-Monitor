# D-063 精简与运行减负：本地审查报告

日期：2026-09-17。用户明确要求“都要做，最好大幅精简代码的基础上优化程序”，并确认本地实施及验证。本轮承接 D062；不是新 UI、采样周期调整或实车通信实验。

## 结论

本轮已完成可在本地证明的精简、运行路径减负及安装包缩减。没有以删功能、降频或减少日志换取数字。

| 项目 | D061（本次连续精简前） | D062 | D063 |
|---|---:|---:|---:|
| main Kotlin 文件数 | 36 | 33 | 32 |
| main Kotlin 行数，含注释/空行 | 10,801 | 9,170 | 9,044 |
| 非调试候选 APK 字节数 | 8,284,266 | 7,130,205 | 6,446,609 |

- 本轮主程序再减 126 行；连续两轮累计净减 **1,757 行 / 16.3%**。没有把测试或历史文档删除计入。
- APK 本轮减少 683,596 字节 / 9.6%；累计减少 **22.2%**。主要新增收益来自 Android 代码裁剪，不等于又删除了相同比例的手写源码。
- 稳健策略的单段搜索微基准中，两次最终运行的中位耗时下降约 **29%–35%**。这是电脑上的局部算法测量，**不是整车 CPU 降幅、不是采集提速，更不代表默认 HA 策略已经达到 5Hz**。
- 日志对象复用和 HA 热循环确实少做了临时分配；本轮没有新实车证据量化它们的整机收益。
- 原 UI、两种策略及默认 HA、七条请求、周期、数值公式、Idle、保存/舍弃规则均保持；MX+ 增强命令没有启用。

## 身份、回退及交付

工作目录：`C:/Users/冠儒/Documents/Codex/2026-08-12/files-mentioned-by-the-user-rx400h/work/rx400h-Monitor-crt`。

分支 `v0.3.0`，HEAD `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45` + 原有 D055–D062 dirty 内容 + D063。本地 origin mirror 已 fetch，无 merge；不声称 GitHub 最新已同步。没有 commit、push、远端 CI、实体设备安装或基线晋升。

开工前，D062 清单的 147 个源码条目全部匹配。比较基准是完整 D062 交付 ZIP，不是旧 HEAD：
`../../deliverables/D062-lean-local-20260917/D062-source-review.zip`，SHA256 `2d27479768bb5e52755c1e60fb160b573b2f33a39d1193620a5bccb932f0cd50`。旧源码、旧 APK 和用户行车日志未动；删除的 DashboardUi 包装层可从该 ZIP 恢复。

交付目录：`../../deliverables/D063-lean-runtime-local-20260917/`。

| 用途 | 文件 | SHA256 |
|---|---|---|
| 非调试、R8 裁剪候选 | RX400h-Monitor-v0.3.5-v28-D063-lean-runtime-test.apk | bcf479211dc29f01d45ff17ec6efabd0f1bb165d3e80a914791c4e4d5f3a507d |
| 未裁剪、本地验收对照 | RX400h-Monitor-v0.3.5-v28-D063-lean-runtime-debug-control.apk | 10da73a8b7353d93419436dd0f9f42a0b70650e36ce98cb0372718ba4c0b70f5 |

debug 包 9,674,208 字节，包含验收专用代码，不能用其大小代表车上候选。两个包都使用原包名 `com.guanyu.rx400hprobe.debug`，versionCode 28；候选 versionName `0.3.5-D063-lean-runtime`。原固定测试证书、v2 签名验证通过。

交付另含源码 ZIP、逐文件 SHA256 清单、D062→D063 app 专用 diff、原始 tracked-only diff 和 R8 的 mapping/usage/configuration/seeds。**R8 mapping 必须与 APK 一起保留，用于还原崩溃堆栈。** 人工审查 diff 统一换行以减少 CRLF/LF 噪声；源码 ZIP 和 manifest 保留原始字节。源码包不含签名 key、构建缓存、.git 或用户行车 ZIP；固定签名重建仍需本地原测试 key。

## 修改文件与理由

### 生产代码及构建

| 文件 | 实际变化 |
|---|---|
| CapacityAwareScheduler.kt | pending 索引容器复用；空闲唤醒时间直接取最小；HA 不再为一个选择创建数组；稳健搜索每次入口排序一次，不在每个递归节点重新排序；保留第一个同成本最优解；拒绝选择直接取最小而非全排序 |
| ProbeLogger.kt | 三种固定字段 JSON 复用现有锁下的对象，逐字段覆盖后立即序列化；内联 safeWrite 消除捕获闭包；统计接口直接接收已有 snapshot，去掉几十个参数的重复转发；删除无消费者 stop 别名 |
| MainActivity.kt | 直接持有既有 PixelDashboardView；直接传已有 scheduler snapshot；去除 wrapper.root 强转。没有把绘制或 Vehicle Core 迁入 Activity |
| DashboardUi.kt | 删除 49 行仅转发调用的包装层；没有独立业务、布局或数据模型被删 |
| app/build.gradle.kts | benchmark 开启已有 Android R8 优化/裁剪，标记 D063 版本后缀；debug 不裁剪，保留本地验收入口 |

本轮 main Kotlin 只有上述四个路径发生变化（其中一个删除），其余生产 Kotlin 保持。R8 总 DEX 未压缩体积从 2,503,656 降到 253,488 字节，包含无用库和仅供 debug 调用的绘制校验辅助路径的消除；不是运行时内存或 CPU 测量。

复用不是“零分配”：每条日志仍有必要的序列化字符串、数值包装和部分数组。只固定复用三个有限对象，不新增无界队列，不改变锁和检查点周期，不删除原始回复。

### 验收代码/工具

- debug：DashboardPreviewActivity 直接绑定原 View；SessionLifecycleSmokeActivity 新增日志字段校验；新增 LoggerRecordParityChecks。
- test：新增 D062SchedulerReference（只在 JVM 测试中冻结旧算法，不打入任何 APK）、SchedulerParityTest、SchedulerSearchBenchmarkTest；补强 MxPlusWireExperimentTest 的格式/错误边界。
- tools：新增 capture_d063_smoke.py、capture_d063_main_gui.py、export_d063_manifest.py。GUI 工具读真实 R8 mapping 识别 View，不为了测试给生产包添加保留类名规则。
- 文档：D063_LEAN_RUNTIME_PLAN、D063_MXPLUS_RESEARCH、本报告及项目状态/决策/路线图/证据/交接/变更记录。

## 保持边界的核验

与 D062 逐字节相同：RequestTable、SamplingStrategy、Elm327Client、SignalStore、ObdParsers、IdleCheckState、DashboardFreshness、PublicLogExporter、PixelDashboardView、PixelDashboardModel，及所有 main assets/resources/manifest。

因此不能说“没碰 scheduler/core”——确实改了调度实现和日志/Activity 调用；但调度的决定、截止规则、成本公式、迟到/舍弃记账不变。48,000 步新旧轨迹比较检验了这点。没有缩减请求、关闭某个信号或调整用户字体、按钮、位置、扫描线、动画参数。

## 最终验证

证据根目录：`../../outputs/d063-lean-runtime-20260917/`。前几次检查仍保留，不混作最终产物结果。

1. **构建/静态检查**：`build-final-02.log`，assembleDebug、assembleBenchmark、lintDebug、lintBenchmark 成功；两种 lint 各 0 errors / 15 warnings。没有屏蔽原有警告。
2. **单元测试**：27 个实际编译的测试类，直接 JUnit 两次各 150/150。相对 D062 的 144 项新增新旧调度对照 1、局部微基准 1、MX+ 拒绝测试 4；微基准环境开关已启用，不是 skip 冒充通过。最终日志 `junit-final-02-sep14.log`、`junit-final-02-historical.log`。
3. **历史回复**：上述两轮分别核对 27,678 和 13,465 条，共 41,143 条。它们验证解析/帧/状态/证据输出，不是新的实车吞吐测试。
4. **新旧调度**：两策略 × 40 种随机序列 × 600 步，共 48,000 决策，另比对完成结果、失败、断连重连、统计和结束清账；全部一致。旧实现仅放 test source，防止生产包恢复双份调度器。
5. **Android 文件/日志**：最终 debug 包 `file-json-smoke-final/`，8/8。含原 7 项隔离文件故障和新增真实 Android JSON 校验：两会话、空值/非空值交替、中文/转义字符、负零、递增序号、所有可空 scheduler 字段，以及用不同标记核对统计 CSV 每个有效载荷列，避免全零输入掩盖错位。不是单靠 JVM 的 mock JSON，也不是整个 LIVE 蓝牙会话集成测试。
6. **像素**：最终 debug `pixel-oracle-final/`，40 个变化输入、146 个实际绘制帧通过独立全画面对照；覆盖自检/过渡/过期/恢复/Idle。这是验证同内容绘制一致，不是 acquisition Hz。
7. **GUI**：API26、1 个 guest CPU、MemTotal 1,531,004kB、1280×720、SwiftShader。最终 debug `native-gui-debug-final/`、裁剪候选 `native-gui-benchmark/` 均通过主页/设备页/返回/设置/前后台；6 张截图的文件散列彼此一致，也与 D062 相同。策略选择/重开持久化及恢复 HA 在 `strategy-gui-debug/` 通过（debug 增强测试前的同生产源码 APK，身份另存）。没有实车/蓝牙访问。
8. **签名**：`signature-debug-final.txt`、`signature-benchmark.txt`，同原证书 SHA256 `77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192`，v2 通过。
9. **标准 Gradle test 未通过**：`gradle-unit-attempt.log` 仍在启动测试 worker 时出现 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`。这与此前宿主限制一致，测试未正常启动；上述直接 JUnit 是同批编译代码的替代执行，不把标准入口写成全绿。

R8 候选不含 debug 验收 Activity，因此文件/日志专用夹具在未裁剪 debug 执行；裁剪候选本体只做了原生页面/设置及启动恢复前台验收，**尚未验证其真实 LIVE→结束→导出全流程**。GUI 检查不证明 API27 首帧、API28+ 缺口或长时间运行均无问题。

## 性能数字应怎样理解

局部搜索测试每轮 100 次、6 轮交替新旧顺序，另有预热，两次最终进程：

| 进程 | D062 中位耗时 | D063 中位耗时 |
|---|---:|---:|
| sep14 单测进程 | 28.12405 ms | 18.15265 ms |
| historical 单测进程 | 26.76290 ms | 18.99985 ms |

输入是七条请求全部 pending、宽 deadline，隔离搜索成本，包含相同反射和结果检查开销。它不是默认 HA 热路径，也不是全调度 CPU 占比；不能据此说“车机快了三成”。没有本轮新 UI CPU 回放收益声明，既有 D061→D062 的 UI-only CPU 9.96%→9.88% 仍是基本持平。弱配置模拟器只做退化检查，没有 80% 等价实机的校准证明。

## MX+ 与下一步

参阅 `D063_MXPLUS_RESEARCH.md`：核对了官方文档并补充 test-only 格式边界，但目标固件的支持、回复计数和多帧格式没有本地实测证据。不能为了写“已优化”而把 STPX/STBC 打开，更不能把批量发送说成 ECU 并行。

本轮停止点是一个有身份、可回退的本地候选，不是整个 V0.3.5 结束。仍未关闭：
- 实际 MX+ enhanced wire 能力、取消/断连/迟到输出及整会话验证；
- HA 的真实有效采样频率和 5Hz 目标、长期内存/总 CPU；
- 前序单列的扭矩边界、轮动画方向等未在本次冻结像素的精简里悄悄处理。

### WORK_FOLLOWUP

不要重新加旧 wrapper/旧恢复入口，不要为省行数删掉在用的锁、超时、取消或数据有效性检查。继续局部精简须先列出消费者和测量对象；适配器路线先补准确能力/格式依据，再提出有限、可回退的验证范围。现阶段不要求用户专程上车，不自动发布或安装。若将来比较自然行车日志，必须用本报告 exact APK SHA 与 D062 对照，分别报告 acquisition、signal change、UI publish 和动画帧率。

