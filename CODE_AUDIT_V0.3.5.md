# RX400h Monitor V0.3.5/v27 逐行代码深审报告

审查日期：2026-08-31
审查对象：本地分支 `v0.3.0` 上的 V0.3.5/v27 工作树（`work/rx400h-Monitor-crt`）
审查边界：`git diff HEAD` 本轮全部改动（9 个源码文件 diff + 3 个新文件 + 4 个测试文件，约 +1000/-120 行）
审查方式：主审人逐行亲读 DashboardUi.kt（968 行）、DashboardSchematics.kt（888 行）、DashboardSchematicState.kt（129 行）、MonitorSessionState.kt（153 行）全量，以及 MainActivity.kt / ProbeLogger.kt / ResponsiveLayout.kt / ResponsiveViewGroups.kt 全部 diff；两个独立子代理分别深审会话/日志状态机 diff 与四份测试文件质量；所有高/中危发现均经主审人回读源码交叉验证后入报告。

---

## 结论总览

**无阻断项。** 核心逻辑（SAVE_FAILED 状态机、FINALIZE_FAILED 交接、1280×720 几何合同、NaN 未知值纪律、动画时钟）均正确且自洽；未越界（零协议/解码/调度/period 改动，D-055 文档归档完整）；不存在“AI 乱编的假测试”（113/113 通过、无数值错断言、无恒真断言）。

但确认存在两处“AI 乱编”实锤（死代码 / 近死代码）与一批应修建议项，建议在交付车机验收前清一轮。

---

## 一、应修项（建议本轮清除）

### A1. `needsTicker()` 是生产零调用的死代码

- 位置：`app/src/main/java/com/guanyu/rx400hprobe/DashboardSchematicState.kt:110-118`
- 证据：全 `app/src/main` 源码只有定义、无任何调用点（grep 验证）；仅 `DashboardSchematicStateTest.kt:86-95` 在测试它。三个示意图视图实际各自使用自己的 `animationNeeded()`，从未使用 `needsTicker()`。
- 判定：典型的“写了纯函数 + 写了测试、但没接进任何视图”的死代码。
- 建议：二选一——(a) 把它接入视图的 tick 判定逻辑；(b) 删除函数与对应测试。

### A2. `replacementSessionStarted` 是近死代码

- 位置：`app/src/main/java/com/guanyu/rx400hprobe/MainActivity.kt:551 / 561 / 663-668`
- 证据：`ProbeLogger.start()` 失败路径必然 `throw failure`（`ProbeLogger.kt:282`），因此该标志唯一能起区分作用的场景是“shutdown 中途 `start()` 正常返回”这一罕见边角；在全部主流路径上它恒为 false 或恒为 true，`!replacementSessionStarted` 退化为 `deferredFailedEvidence`。
- 判定：不死但近乎退化；其存在让 `else` 分支的可读性受损。
- 建议：重构为直接判断 `logger.state`（或记录 `loggerStarted`），并显式处理 shutdown 中途返回的边角；或者删除标志并加注释说明该边角的处理依据。

---

## 二、建议项

| # | 位置 | 问题与建议 |
| --- | --- | --- |
| B1 | `DashboardSchematics.kt` 7 处：174-181、195-215、217-224、536-543、549-555、790-798 | `if (x.isFinite()) { target=x; if (!displayed.isFinite()) displayed=x } else { 双双置 NaN }` 的 7 段复制粘贴。提取一个 `updateSlot(target, displayed)` 辅助函数，行为不变、代码减半。 |
| B2 | `DashboardSchematics.kt` 421/434/458、670-676、726-740、858-862 | Paint 的 `alpha` 在多处 onDraw 方法里“现场修改、事后复位”，依赖绘制顺序的隐式耦合。当前每处都先设后用、安全，但任何后来者忘记设 alpha 就会继承上一帧残值（205/125/60 等）。建议每个使用点显式设置 alpha，或在 onDraw 入口统一重置。 |
| B3 | `DashboardSchematics.kt:579` | `wheelPhase` 只做一次 `-2π` 回卷，正确性依赖“delta ≤ 0.1s × 频率 ≤ 2.7 圈/s < 2π”的连锁假设；改任一常量即相位溢出。建议 `% (2π)` 或 while 回卷。 |
| B4 | `MainActivity.kt:364-371`（worker 线程写）vs `358-362`（主线程锁内读） | 本轮把重试引用清除从“主线程+锁内”移到“worker 线程+无锁”。经推演，正确性靠 `setPhase` 的 AtomicReference volatile 写 → 主线程 CAS 读 phase 的 happens-before 链兜底，**当前无竞态**，但契约是隐式的。建议字段加 `@Volatile` 或统一锁保护，并加注释固化该依赖。 |
| B5 | `ProbeLogger.kt:86-90` | `mustDetachFailedSession` 与 `mustRetainFailedOwnerAfterStartFailure` 函数体完全相同（均为 `state == SessionState.FINALIZE_FAILED`）。合并为一个谓词；若保留双名，注释说明其语义等价及原因。 |
| B6 | `ResponsiveLayoutTest.kt:94-117` 等 | 金标准数值锁死实现细节：223/300/150/706 等复刻生产公式，改常量不改行为时测试会成片红；`8`/`3` 是跨层复制 `DashboardUi` 的 `dp(8)` margin；`960/1260` 断言是纯算术恒等式、不触被测代码。建议对“非产品规格”的中间量改断言不变量，只对真正的 1280×720 规格量保留金标准。 |
| B7 | 测试覆盖缺口 | 各 gauge 的上限钳位/中间线性（`batteryTemperatureNormalized`/`coolantNormalized`/`adapterVoltageNormalized` 全缺）；`iceMarkerState` 的 0.001 kW 阈值边界与负 rpm；`approach()` 非有限 target/current、fullScale≤0 回退、dt>MAX 分支；`needsTicker` 奇数个 vararg；`permissionFallbackPhase(WAITING_PERMISSION)→IDLE`；`phaseAfterPendingConnectionCloseFailure` 的 STOPPING+fallback；STACKED→INLINE 滞回；`inlineHeaderGeometry` 的 require/钳位边界；`safeInsetsAndCompactSpacingUseTheRuntimeUnitPath` 中 `gridPlan` 漏传 `CARD_GAP_DP`（潜在隐性不一致）。 |

---

## 三、信息项（供知晓，不强制）

| # | 位置 | 说明 |
| --- | --- | --- |
| C1 | `ProbeLogger.kt:80-84` | `state in setOf(...)` 每次调用分配一个 Set；`when(state)` 零分配且更清晰。 |
| C2 | `DashboardSchematics.kt` | 车形轮廓与轮圈频率映射（0.7 + speed/80，上限 2.7 圈/s）等比例魔法数无依据注释；`tickLabels` 数组与 `TICK_COUNT = 5` 相互独立（改一个会越界）；`dp()` 辅助函数在三个视图类中重复定义，可提到基类。 |
| C3 | `MainActivity.kt:1706-1708` | `batteryTempFresh` 改为三温度全 VALID 才 fresh。查证 `SignalStore`：三温度同源同写、同 12s stale 阈值，**当前数据流下等效**，属防御性收紧而非行为变化；若未来信号来源拆分需回看。 |
| C4 | `MainActivity.kt:1738-1741` | idleCheckActive 加 VALID 门槛是刻意的 UI 语义变化（stale 后不再显示激活），符合 D-054 意图，确认有意即可。 |
| C5 | `ProbeLogger.kt:304-308` | `closeWriters(failOnError)` 在 `writePendingErrors` 之前；close 失败时 pending errors 不落旧目录。因 FINALIZE_FAILED 状态与门闩保留，可自愈，非数据丢失。 |
| C6 | `MainActivity.kt:511-543` | `!ran`/settle 路径无条件 `setPhase` 可覆盖 requestEnd 已置的 STOPPING（预存模式，本轮未引入，今日无害）。 |
| C7 | `DashboardSchematics.kt` 各 `updateSignals` | 值未变化也无条件 `invalidate()`；目标车机 0.375 Hz 数据率下开销可忽略，不值得改。 |

---

## 四、良好实践确认（防误伤：这些是真做得好的）

1. **零每帧分配**：onDraw 全部使用字段级 Paint/Path/RectF/FloatArray，全文无 Bitmap 创建、无每帧对象分配。
2. **动画时钟有界**：50ms 下限即 20 fps；detach/不可见/失焦全部停回调；`lastFrameNanos` 复位防跳变；首帧 delta 固定 0.05s。
3. **NaN 未知值纪律**：未知值全链路传导，BAT/VEH 显示“—”，PWR 抑制游标+能量段，绝不伪装 0；`finiteFresh` 先判 fresh 再判有限。
4. **渲染按版本门控**：`render()` 逐字段判 version 才更新；`applyWindowLayout` 有 token 去重；`applyPhysicalMetrics` 仅 density/fontScale 变化时执行；`setTextIfDifferent` 防无谓重绘。
5. **状态机质量**：CAS 状态迁移；失败 owner 保留与 process gate 语义正确；`closeWriters` 异常聚合（addSuppressed）；只在 `logger.start()` 成功后清重试引用（修复“清了还能重试”的漏洞）；detach 期间持 gate 的注释与行为一致。
6. **ResponsiveLayout**：`require()` 守卫、Long 乘法防溢出、measureInline 的重复 bounded() 逻辑收敛为 `inlineHeaderGeometry()`。
7. **测试**：无恒真断言、无“只测 mock 本身”、无数值错断言（全部代入实现复算一致）、无 Thread.sleep/时间依赖。
8. 源码零 TODO/FIXME/XXX 遗留；改动范围与 AGENTS.md 冻结规则无冲突。

---

## 五、与 V0.3.5 代码审查报告（CODE_REVIEW_V0.3.5.md）的关系

本报告是同一工作树的“逐行深审”补充，不重复其“已通过”项验证。独立复核（含干净重建、113/113 测试、lint 0 errors/9 warnings、APK 哈希 `0b803f3c…533e` 字节级一致、v2 签名 `77ba84b1…c192`）已于同日完成并通过；其中两项附加结论与本报告相关：

- 重建 APK 与报告哈希字节级一致 → 构建可复现；
- Gradle test worker 的 `GradleWorkerMain`/closed-pipe 故障经实验确认与 Unicode 路径相关（纯 ASCII 路径下原生 `testDebugUnitTest` 113/113 通过）。

---

## 六、待决策事项

1. **A1/A2 死代码**：建议本轮清除后再上车。改动极小、纯删减；不影响现有 APK 行为语义，但会改变 APK 字节，需要重跑构建/测试/哈希/签名复核后产出新候选 APK。
2. B 类清单可按“只清 A1/A2”或“A+B 一起清”两种方案执行；改完重跑全量验证。
3. 若决定完全不动代码，本报告可作为已知技术债归档进 `DECISIONS.md` 供下一轮处理。

---

## 附：审查覆盖清单

| 文件 | 方式 | 覆盖 |
| --- | --- | --- |
| DashboardUi.kt（968 行） | 主审人逐行亲读 | 100% |
| DashboardSchematics.kt（888 行） | 主审人逐行亲读 | 100% |
| DashboardSchematicState.kt（129 行） | 主审人逐行亲读 | 100% |
| MonitorSessionState.kt（153 行） | 主审人逐行亲读 | 100% |
| MainActivity.kt（diff +110/-24） | 主审人逐行 + 子代理独立深审 | 100% |
| ProbeLogger.kt（diff +64/-4） | 主审人逐行 + 子代理独立深审 | 100% |
| ResponsiveLayout.kt / ResponsiveViewGroups.kt（diff） | 主审人逐行 | 100% |
| 四份测试文件（diff + 新文件 144 行） | 子代理深审 + 主审人亲读 | 100% |

本报告只读审查，工作树源码、APK 与 deliverables 均未做任何修改。
