# RX400h Monitor — CODEX_HANDOFF

## 2026-09-26 项目暂时结束 / 换电脑归档

先读 `PROJECT_ARCHIVE_20260926.md`。用户现已授权把D064源码、资源、测试/编辑工具和文档提交到既有GitHub `v0.3.0` 分支，不覆盖main，不扩展产品功能；旧的“未授权push”是历史检查点。原始日志、APK、R8mapping和输出证据不在源码提交里，卖电脑前另行备份。没有把本地通过冒称实车验收，保留D064报告的全部限制。

## 最新本地交接 — 2026-09-23 / D-064

先读 `D064_LOCAL_REVIEW_20260923.md` 与 `D064_REALTIME_CLOSURE.md`。V0.3.5/v29纯实时仪表本地候选，HA顺序约3Hz；日志/性能统计/保存恢复/稳健策略已经按用户要求删除，不要根据以下历史段落恢复。自动连接一次、侧视车图手动重连、后台采集/停画面、返回取消退出；设备选择在设置，原三个按钮彻底不命中。主源码3525行（−61%），用户assets未动。交付 `../../deliverables/D064-realtime-local-20260923/` 含精确源码、两APK、D063差分和R8mapping。92directJUnit两轮、41143回复、10Android检查、157像素帧、两包lint/build/签名/无适配器GUI通过；标准Gradle worker仍失败单列。真实LIVE/连接中退出/切换/睡眠/音乐及实际3Hz未验证，不是正式基线。电脑日志和D063恢复包保留，无推送/实体安装。旧不确定会话宁可保留，清理不阻挡仪表。下一步只做自然使用验收，不重新索取本版日志ZIP。

## 最新本地交接 — 2026-09-17 / D-063

先读 `D063_LOCAL_REVIEW_20260917.md`。本轮继续用户批准的精简+A/B运行减负，未要求立即上车。main32文件/9044行，D061累计−1757行/16.3%；benchmark R8包6446609字节，累计−22.2%，SHA `bcf47921…a507d`。150directJUnit、41143历史回复、48000调度对照、8Android日志/文件、146像素帧、两包lint/build/signature/nativeGUI通过；标准Gradle worker失败继续单列。29–35%只为稳健搜索局部主机微基准，不是默认HA/实车收益。交付 `../../deliverables/D063-lean-runtime-local-20260917/` 含源码、APK、D062专用diff、manifest及必须保留的R8mapping。UI/请求/周期/公式/Idle/保存策略未变；原D062可回退。无推送/实体安装/固件实验；MX+增强命令仍test-only，R8真LIVE保存、5Hz、扭矩/轮方向等旧gate未关闭。不要据下方旧段落复原已删wrapper或旧启动恢复链。

## 最新本地交接 — 2026-09-17 / D-062

先读 `D062_LOCAL_REVIEW_20260917.md`。用户批准加强精简，已完成本地代码、构建和验收；不要从下方旧检查点恢复旧 UI / 启动恢复链。仍为 `v0.3.0 / 8f1a3b9 + dirty`，保留原D055–D061。主源码净减1,631行/15.1%，上车测试APK7,130,205字节（−13.9%）；`../../deliverables/D062-lean-local-20260917/` 含测试包、debug对照、源码包、D061专用diff与身份清单。144directJUnit、41,143记录回复、148独立oracle帧、7Android文件故障、两种构建/lint/GUI/签名通过；标准Gradle worker仍宿主启动失败，单列。相同debug UI回放CPU9.96%→9.88%，基本持平，不能声称实车明显提速。两策略/default HA、白名单、周期、公式、Idle、用户资源、正常日志保存政策均保留；后台仅停UI发布。旧删减代码可由原D061源码ZIP恢复，原始日志未动。下一步是自然使用的exact-artifact数据，不要求专程测试；真实5Hz/MX+批次/扭矩边界/LIVE切换与长期内存仍未关闭。没有commit/push/CI/实体设备安装或发布。

## 最新本地交接 — 2026-09-12 / D-061

先读 `D061_LOCAL_REVIEW_20260912.md`，不要根据下面旧检查点复原旧源码。当前仍 `v0.3.0 / 8f1a3b9 + dirty`，D061叠在保留的D060之上；`../../deliverables/D061-AB-local-20260912/` 包含非调试测试APK、debug对照、源码快照、完整清单与D060→D061专用diff。165单元测试、13,465事务解析核对、两版独立像素/GUI/文件故障通过；标准Gradle worker失败单列。相同debug模式UI-only CPU约降低31%，不等于实车采集5Hz。实际运行了阻塞接收和共享解析，但STPX/STBC只在测试研究中，未启用。下一步是受控目标适配器取证，不改用户画面、不凭模拟器认定固件支持，不新增四档，不删除原始证据。完整未关闭项及WORK_FOLLOWUP见报告末尾。无Git提交/推送/远端CI/基线晋升。

## 最新本地交接 — 2026-09-08 / D-060

先读 `D060_REQUIREMENTS_CLOSURE.md` 和 `D060_LOCAL_REVIEW_20260908.md`，以其区分实现/本地通过/实车未验证。当前仍为 `v0.3.0 / 8f1a3b9 + dirty`，V0.3.5/v28候选APK `db6b33fa…811297`；没有提交/推送/发布。两策略默认HA、确认自动交接、丢弃未保存旧会话、Idle/新鲜度/像素UI均已实现并分层回归。140 direct JUnit、21 Python、7 Android文件故障及最终像素检查通过；不是全部九组gate关闭。待补真实LIVE连接交接、最新原始Idle回放、跨版本整负载弱机留出拟合及长时内存。不要让下一位代理根据下面历史段落恢复旧保留策略或重新添加四档。

## 1. 目的

这是从长 ChatGPT 项目对话迁移到 Codex 的正式交接入口。目标是：**Codex 读完本迁移包和仓库后即可完整接手，不需要重新询问几百条聊天历史。**

项目的 canonical memory 是 Git 仓库文档，不是聊天记录。

仓库：

```text
noobruge-wq/rx400h-Monitor
```

用户最近通过 Git 确认的基线提交：

```text
628da4b Add v0.1.10 project baseline
43a9e6b Update to v0.1.10 cleanup candidate
0121b66 Update to v0.1.8
```

Codex 启动后仍必须 `git fetch` 并以远端最新 HEAD 为准；上述 SHA 只是迁移时已知状态。

## 2. 当前真值

```text
Valid current baseline: V0.2.0 Reactive Core (closed 2026-08-09)
Historical real-vehicle-validated baseline: V0.1.10 cleanup
Valid protocol evidence baseline: V0.1.8
VOID: V0.1.9
Next milestone: V0.3.0 High-Performance Scheduler / Refresh Frontier
```

## 3. 产品为什么存在

项目不只是要“显示 RX400h 数据”。用户专门开发它的原因包括：

1. 想做比通用/现有软件更高的有效动态刷新率；
2. 想把代码精简到 RX400h 专用场景，减少通用框架的 CPU/RAM/GC/维护成本；
3. 最终允许未来皮肤/动画独立发展，而车辆 Core 保持稳定；
4. 不想为软件验证专门承担昂贵长途驾驶成本；
5. 希望研发过程可复现、可交接，不能再依赖超长聊天。

如果最后只是低频数据 + 换皮 HA，则项目核心价值没有实现。

## 4. 当前协议白名单

```text
7E0 -> 7E8
01040C0D0E10 2
01050607 1
21CDF3 3

7E2 -> 7EA
21C3 6
21C4 5
21CF 4

ATRV
```

禁止无证据扩展 `22 / 2C / 10 02 / 10 03 / 7E1/7E3/7E4` 或任意输入。

## 5. 当前产品 UI Contract

### BATTERY
- SOC — A
- HV 电池平均温度 — A
- MAX / MIN — B
- 不在这里重复 Battery Power

### VEHICLE STATUS
- Speed — A
- Coolant — A
- `12V OBD` — A（OBDLink/DLC 供电电压，不宣称电瓶端子电压）

### POWER
- ICE mechanical power — A
- Engine RPM — A
- `IDLE CHECK` — B，仅车辆**真实处于 Idle Check**时显示，否则该位置空白
- HV battery power — A

不在主 UI 显示 S0/S1/S2/S3/S4。MG1/MG2/MGR 不因“可以解码”就自动进入默认 UI。

## 6. 重要物理/状态语义

### ICE torque / power

```text
ICE torque = (d[3] - 128) * 2 Nm
ICE mechanical power =
    torqueNm * 2π * RPM / 60 / 1000 kW
```

### Injection

`u16be(d[12:14]) / 32` 是 HA 中恢复出的真实字段，但：

```text
Injection > 0 != combustion boolean
```

不得用它判断发动机是否燃烧。

### 21C4 warmup

```text
Warmup Active = (d[1] & 0x01) != 0
```

### Idle Check

恢复出的核心候选：

```text
Warmup Active == true
900 < RPM < 1100
ICE Power == 0.0 kW
speed <= 55 km/h
stable ~1 s
```

但 HA 的进入资格受 S3/S4 等历史状态影响，所以 V0.2.0 必须用 replay 证明简化 `IdleCheckEligibilityState` 与完整参考状态机等价后，才能删除完整 S0–S4 内部状态。

注意：HA 的 `IDLECHECK marker` 表示“需要/等待 Idle Check”，不是“当前正在 Idle Check”。产品 UI 绝不能复制这个语义。

## 7. 高刷新证据

Hybrid Assistant HCI 核心序列：

```text
ATSH7E0
01040C0D0E10 2
21CDF3 3
ATSH7E2
21C3 6
21C4 5
```

已知：

- 裸核心 cycle median ≈ 0.159 s ≈ 6.28 Hz；
- 有低频穿插后核心实际 ≈ 5.5 Hz；
- `01050607 1` median ≈ 3.106 s；
- `21CF 4` median ≈ 5.205 s。

结论：

**5.5 Hz 是 HA 的工作点，不是证明 ECU 最大只能 5.5 Hz。**

V0.3.0 需要：
- deadline/priority/backpressure；
- 真实 acquisition Hz；
- publish Hz；
- P50/P95/P99 latency；
- deadline miss；
- NO DATA/TIMEOUT/BUS/ISO-TP errors；
- CPU/GC；
- 分阶段探索 5→6→7…Hz，找到实用 knee point。

## 8. V0.1.10 最强实车基线

```text
RX400h_20260807_120303.zip
SHA-256:
e0f4756d1ec8fb712bfdb12d766c984209bbed385f7950e5fa728714ed987f0b
```

摘要：

- LIVE 1337.653s ≈ 22.294min；
- 3656 transactions；
- 479 frames；
- 0 errors；
- logger_degraded=false；
- evidence_complete=true；
- manifest hashes matched；
- active 车辆段无 vehicle request failure；
- 末尾 17 次 NO DATA 只在人工车辆关机后出现，ATRV/ELM 仍工作，应分类为 ECU OFF AFTER VEHICLE SHUTDOWN；
- scheduler 仍是 V0.1.8 慢节奏，frame median ≈ 2.85s。

日志债务：车辆关机 NO DATA 后 `frames.csv` 会继续保存 last-known-good 值，独立 CSV 缺 freshness/age/status，未来要修。

## 9. Lean Core / consumer traceability

每个 typed signal/state 都必须回答：

1. final UI consumer?
2. derived product calculation?
3. required state/IdleCheck logic?
4. connection/health/correctness?
5. formal validation contract?

全部 NO → 从产品 runtime typed model 删除。

Raw ECU data 保留在 raw log，因此“删 typed field”不等于丢证据。

优先审查删除：
- Engine Load（若 IdleCheck 简化后无消费者）；
- MAF；
- Injection typed runtime；
- MG1/MG2/MGR；
- Brake raw candidates（若 1.0 不做制动功能）；
- PowerBall/Glide/Pulse 等最终产品不消费的 HA 复刻状态。

请求本身不一定能删，因为 Toyota local block 是整块返回。

## 10. Reactive Core 目标

V0.2.0 要把：

```text
Transport / ELM
→ Decoder
→ SignalStore
→ Derived Physics / minimal state
→ Presentation Contract
→ Renderer/Skin
```

正式建立。

信号元数据至少概念上包括：

```text
value
sourceTimestamp
updateTimestamp
age
quality
version
source
```

UI 不再每 500ms 无条件 `String.format + setText` 全屏重画。

推荐：

```text
single-writer mutable state
+ version / dirty bitmask
+ main-thread minimal View updates
```

动画未来可以 30fps，但只能插值 `displayValue`；不得把插值值回灌 SignalStore、状态机或 logger。

## 11. 长期运行与测试成本

用户明确拒绝把“专门 8h/20h 长途”设成 gate，因为一趟成本可能上百刀。

正确策略：

- 日常驾驶自然累计实车 Session；
- 22min 等真实 log 循环 replay 成数小时/数百万 transaction；
- virtual clock 快进 stale/deadline/IdleCheck/logger rolling；
- stationary fault injection；
- 真正不能模拟的 Bluetooth/OBDLink 长期行为等待自然长途机会。

## 12. 未来大版本

```text
0.1.10  Probe/Cleanup baseline
0.2.0   Reactive Core
0.3.0   High-Performance Scheduler / Refresh Frontier
0.4.0   Persistent Runtime / ForegroundService
0.5.0   Durability / Recovery
0.6.0   Deterministic Replay Validation
0.7.0   Low-End Performance / headroom
0.8.0   Product Core Freeze Candidate
0.9.0   Daily-use RC
1.0.0   Core Freeze
```

如果某个大版本 exit gate 提前已经满足，直接跳过，不为了版本号重复造工作。

## 13. GitHub / 自动签名

当前 repo workflow：

```text
.github/workflows/build-apk.yml
```

构建：
- ubuntu-latest；
- JDK 17；
- Gradle 8.9；
- `:app:assembleDebug`；
- `apksigner verify`；
- 上传 APK + signature text + SHA256。

固定 debug 签名：
```text
.github/signing/rx400h-debug.keystore
SHA-256:
8e2ecdec24f9f628fd788cd4cb97e614172d3bb600e8d08d8fa8d4a87247bcb1

alias: rx400hdebug
store/key password: android
debug applicationId: com.guanyu.rx400hprobe.debug
```

证书 SHA-256：
```text
77:BA:84:B1:F4:F7:37:A5:D6:1B:91:0B:F4:38:6D:F1:
67:54:8B:9C:6C:E6:89:ED:25:E9:94:C3:7B:2B:C1:92
```

这是测试/开发签名连续性，不是商店 release key。

## 14. GitHub 读写现状与迁移策略

迁移时已确认：
- ChatGPT GitHub integration 能读取 repo；
- ChatGPT contents 写入尝试返回 403 `Resource not accessible by integration`。

这不影响 Codex。

Codex 应使用本地仓库的 `git` 写入；如果新 Windows 环境没有凭据，迁移包自动化脚本会：

1. 检查/安装 `gh`（可用 winget 时）；
2. 执行 `gh auth login --web`；
3. 用户在浏览器完成一次 GitHub 授权；
4. `gh auth setup-git`；
5. clone/pull；
6. 用 `git push --dry-run` 验证 write；
7. 后续 Codex 可直接 commit/push。

**第一次 OAuth 浏览器同意是 GitHub 安全边界，不能也不应该被迁移包静默绕过。**
不要创建或提交 PAT。

## 15. Codex 接手完成标准

Codex 必须先证明它已经能回答：

- current valid baseline?
- void branch?
- protocol whitelist?
- exact UI contract?
- high-refresh objective?
- lean-core objective?
- next milestone?
- source/doc mismatches?
- repo read/write works?
- GitHub Actions signing works?

回答完后按用户当前明确授权继续；若没有新的开发授权则停止。V0.2.0 已关闭，当前开发分支/里程碑是 `v0.3.0`。

## 16. 当前 `v0.3.0` 分支 / V0.3.1 App 候选 — 2026-08-11

- D-041 全尺寸响应式 UI、D-043 三按钮单-owner session 和 D-044 可恢复日志已作为 V0.3.1/v23 提交并推送到 `origin/v0.3.0`，代码提交为 `43a959b`。
- 稳定 Android View 树从实际 inset-safe 窗口测量并 reflow；卡片/控制组件有显式尺寸合同，整页可滚，冻结 POWER / active-only Idle Check contract 已恢复。
- Pre-commit local verification 为 62 tests、lint 0 errors / 9 warnings、assemble、固定 v2 签名和 API26 smoke；local APK `bd3b252e…14a3` 是诚实标记 dirty 的候选，不是 `43a959b` clean remote artifact。
- Clean exact-commit GitHub Actions artifact 仍待生成；下一位代理不得把 local hash 称为远端 baseline。protocol/scheduler/request table 保持不变。
- 用户已授权把下一可安装 App 候选推进为 `versionName = 0.3.1`, `versionCode = 23`，但 V0.3.0 Scheduler / Refresh Frontier 工程里程碑仍未关闭（D-042）。
- V0.3.1 新合同（D-043）：控制固定为 `设备` / `开始` / `结束`；Start 自动执行连接、初始化、runtime 配置并在成功后进入 LIVE；End 由同一 session task 停止、关闭并保存，不再有独立连接/停止/导出按钮，也不强制分享 chooser。
- V0.3.1 日志合同（D-044）：app-specific 工作目录持续流式 checkpoint；下次启动恢复 incomplete session；正常/恢复 ZIP 使用本地结束/最后持久化时间的人类可读名称，并发布到 `Download/RX400h Monitor`。不得申请 `MANAGE_EXTERNAL_STORAGE`。
- API 26（Android 8.0）模拟器已通过 clean install、冷启动、三按钮初始状态、DevicePicker 空状态、横竖屏 reflow/scroll 与保持同一 Activity 的旋转 smoke，未见 fatal crash。exact-commit GitHub artifact、API 27 以及带已配对 OBD 的连接→LIVE→结束→公共保存/异常恢复 smoke 尚未完成，不得把本地 hash 称为远端 baseline。Android 7 因 `minSdk=26` 不支持。候选 artifact 名为 `RX400hProtocolProbe-v0.3.1-resilient-logs-debug-signed`，protocol/request/scheduler profile 保持冻结。

## 17. 三角色协作入口 — 2026-08-12

- Chat 使用 `CHAT_ROLE.md`：澄清需求、分类证据、生成 TASK_PACKET、决定 Work/Codex 路由。
- Work 使用 `WORK_ROLE.md`：普通实施、本机操作、build/install/GUI/log 收集；复杂问题用 CODEX_ESCALATION_PACKET 升级。
- Codex 使用 `AGENTS.md`：作为高级架构/疑难问题专家，采用最小充分读取与修改，完成核心修复后输出 WORK_FOLLOWUP 交回 Work。
- 动态版本和 gate 只以 `PROJECT_STATE.md` 为准；角色卡不替代 current-state 文档。

## 18. V0.3.2 调度器重建本地候选 — 2026-08-12

- 用户依据 V0.3.0 E1 反馈授权重做调度器。D-046 取代 D-040：release 锚定 LIVE epoch，header 与数据请求每笔交易后重新规划，每个 release 只有一个守恒终态。
- Git 中的 HA/HCI 是 clean-room 时序与请求链证据，不是可移植源码。实现保留严格串行 prompt 边界与 HA 的 7E0/7E2 分组事实；正常 scheduled hot path 不再把旧 `120/80/80 ms` 等待当协议常数。
- 当前本地候选为 V0.3.2/v24，scheduler profile `v030_capacity_002`。七个请求、header、command、decoder 和 target periods 保持不变；成本种子不可信，因此 admission 为 `UNKNOWN`、运行模式为 diagnostic，rate ladder 继续封锁。
- 最终本地 72 JVM tests 全通过，lint 0 errors / 9 warnings，assemble、manifest 与固定 v2 证书验证通过。APK SHA-256 `a8bc90fb35a2c0f8e1c41b517b9016ef42444f1102d15e2ab2518de7343bb347`；它是 base `e58d9f9` 上的 dirty build，不是 exact-commit artifact。
- 实现已作为 `8e55c6a` 推送到 `origin/v0.3.0`；GitHub Actions run `31635798035` 在该 exact clean commit 上通过并产出 APK SHA-256 `841b1a4adb9f9e4a1834d2830dd6e94754a54cbcc3b2b3209023061da1969e9b`。尚未安装或执行车辆动作。API 27、paired-OBD connection → LIVE → End/public-save/recovery smoke 与同 periods E1 仍待完成；这些完成前不得 promotion，也不得提高频率。

## 19. V0.3.3/v25 本地实现候选 — 2026-08-21

- exact-clean V0.3.3/v25 已安装到目标车机并成为当前实车证据候选，但尚未 promotion。
- D-049：首屏显式 `RECOVERING`；已发布的稳定终态使用有界 metadata fast path，待恢复、缺失或不一致的记录仍走完整 ZIP/manifest/SHA/session 校验。
- D-050：End、`onDestroy`、worker finally 与 logger 共享 first-writer-wins 的 `finalize_intent.json`；USER_END 不再被后来的 Activity 销毁改写。终止后普通事件入口关闭，START_FAILED 保留已有字节并以 `evidence_complete=false` 可重试打包。
- D-051：只在 API27 `sprd/sp7731e_1h10_native/sp7731e_1h10` + 大小写匹配的 `OBDLink MX+` 家族上选择版本化方向成本模型；不适用或缺成本回到 `UNKNOWN`。冻结 60 秒 replay 为 `ADMITTED`、projected utilization `0.906533`、0 miss/0 reject，七个请求周期完全不变。
- D-052：前后校时只追加 `CLOCK_ADJUSTMENT` 证据，不参与 scheduler、freshness、Idle Check、checkpoint 或生命周期真值。D-047 固定 CRT Green 已整合，仍是轻量 Android View presentation skin。
- exact-clean 实现提交 `c9ad39759021fd8d4ca529b115ab7932aa2bf8d8` 的生产/测试源码编译通过；刚编译的 98 项 JUnit 直接运行全部通过；lint 0 errors / 9 warnings；V0.3.3-debug/v25 assemble、manifest 与固定 v2 证书验证通过。APK 为 2,589,038 bytes、SHA-256 `2d75bd7d1bc6be8a923495e901c05ce904d58966f8e10ff3e12b2e980a33d0a1`，内嵌 `GIT_DIRTY=false`。Gradle test worker 在当前 Windows/Unicode 路径上因 `GradleWorkerMain`/pipe 启动器故障无法运行，必须与测试失败区分记录。
- exact-artifact API 26 GUI 已覆盖 1280x720、360x800、800x360、滚动可达与 Idle Check active-only；exact 800x600 重抓因重置后的模拟器冷启动无法到达 ADB 而 host-blocked，不声称通过。两份 2026-08-26 API 27 exact-artifact 记录（ZIP SHA-256 `c917e183…5201`、`5364e69e…ecda`）均完成 `USER_END`、零错误、完整证据和 public receipt，已证明 normal connection/LIVE/End/public-save；第二份 `4006/4006` release 全部 on-time。下一 gate 是 remote exact-commit CI 与强制中断恢复。不得提高频率，不得把本地候选称为 baseline；除非用户另行授权，不 push/PR/release。

## 20. V0.3.4/v26 目标车机首帧与日光 CRT 修正 — 2026-08-27

- D-053 仅改 presentation：首帧窗口 key 覆盖真实 width/height/insets/density/fontScale；attach-scoped stabilizer 最多压住两次 pre-draw，第三次必定移除并放行；debug preview 的私有三轮 settle workaround 已删除。
- CRT 改为更亮的绿色层级、主数值/标题/按钮选择性粗体和静态交替间距扫描线；无动画、blur、shader、新依赖或 draw-time allocation。phone portrait 暂不作为 gate。
- Gradle/Workflow 身份推进为 V0.3.4/v26 和 `RX400hProtocolProbe-v0.3.4-target-crt-ui-debug-signed`。生产/测试源码编译、99/99 direct JUnit、lint 0 errors / 9 warnings、assemble 和固定 v2 证书通过；标准 Gradle test worker 仍受当前 Windows/Unicode `GradleWorkerMain`/pipe 故障影响。
- exact-clean 实现提交 `b60619d5c1f4e011508b3cf74de6fee7422ec720` 已重编译为内嵌 `GIT_DIRTY=false` 的 V0.3.4-debug/v26 APK；文件 `RX400hProtocolProbe-v0.3.4-b60619d-target-crt-ui-debug-signed.apk` 为 2,591,078 bytes、SHA-256 `fa88064be3450ccb8765217ed0f12e97e01793c66434b07b0901777b4072002b`，固定 v2 证书通过。本机没有 API 27 image，API 26 模拟器在 hardware/software 两种路径都停在 ADB 前，因此不声称新的 GUI pass；实际 API 27/1280x720 natural cold-start first-frame gate 仍待完成。未改 scheduler/protocol/decoder/SignalStore/logger/session，未 push/install/执行车辆动作。

## 21. V0.3.5/v27 信号图形、精确对齐与失败恢复解锁 — 2026-08-31

- 本地审查资料足够进行代码级复核：分支/HEAD、dirty diff、源代码、113 项 direct JUnit、lint/assemble、manifest、APK hash/signature 和项目状态文档齐全。完整报告保存为 `CODE_REVIEW_V0.3.5.md`。
- D-054 的手工布局坐标已被程序化归一：mdpi 1280×720 模型中三卡为 `x=20/440/860`、`400×556`，按钮为 `x=320/518/716`、宽 166、间距 32；窄窗口仍由响应式换行和滚动处理。
- 三张 PNG 只是构图参考，BAT/VEH/PWR 已改为由 `DashboardSnapshot` 驱动的原生 Canvas 绘图；SOC、温度、速度、冷却液、12V、RPM/ICE 和双向 HV 功率都遵循未知/新鲜语义，显示插值封顶 20 fps，不提高采集 Hz。
- D-055 让 `SAVE_FAILED` 同时提供 `设备`、`开始` 和 `结束`。只有旧 worker、writer、蓝牙连接和 session lease 安全释放后才允许新 session；旧失败证据不删除、不覆盖、不冒充成功。无法安全关闭 writer 时仍 fail-closed，防止双 owner。
- 本地结果：V0.3.5-debug/v27 APK 2,612,662 bytes，SHA-256 `0b803f3cf3759d96e350e6ea4f18146ae263c1250cfa557780d8011a84bd533e`，`GIT_DIRTY=true`，固定 v2 签名通过；113/113 direct JUnit、lint 0 errors/9 warnings、assemble 通过。目标 API 27 GUI、OBD 实时流和强制中断恢复仍待车机人工验收，未 push/install/执行车辆动作。

## 22. V0.3.5/v28 用户最终像素布局实现 — 2026-09-01

- D-056 取代 v27 被拒绝的抽象车辆重绘和自动对齐。`RX400h-UI-layout-v035-pixel-v2 (3).json`（SHA-256 `1f391451…518aa`）成为精确合同；640×360 逻辑图统一最近邻放大到 1280×720，用户的俯视/侧视车辆遮罩和独立轮辐直接进入 presentation 资产。
- 固定中文以 Noto Sans SC 按声明物理像素预栅格化，实时数字继续使用用户位图字形。SOC、轮辐和双向 HV 功率遵循确认过的自检/游标规则；Idle Check 保持 10%/100%。设置浮层只在本机持久化扫描线不透明度/宽度/间隔和辉光强度/半径。
- 自检只由按 Start 后的新 SOC/HV 功率样本触发，并在自检期间更新最新落点。绘图缓存所有遮罩、字形、轮帧和对象；普通过渡最高 20 fps、轮辐最高 10 fps，后台/静止不持续调度。
- 本地结果：V0.3.5-debug/v28 APK 2,656,023 bytes，SHA-256 `020a0d9fb05e718e0eef6e100edbdd306dcba244f7b4017b67e2967f048d30f6`；111/111 direct JUnit、lint 0 errors/9 warnings、assemble、manifest/资源哈希和固定 v2 签名通过。完整报告为 `CODE_REVIEW_V0.3.5_V28.md`。
- 2026-09-01 GUI 重试已清除旧 QEMU 锁，绕过原 userdata，并建立全新隔离的 API 26 / 1280×720 AVD；软件与有效硬件加速均仍在 Android 启动前停止，ADB 缺失或仅为 `offline`。剩余阻塞点已收窄为本机 Emulator/QEMU/WHPX 运行环境，因此不声称本轮 GUI pass。目标车机 UI、配对 OBD 和强制恢复仍待人工验收。未 push、commit、PR、安装或发布；重试证据见 `../../outputs/v28-gui-retry/README.md`。

## 23. V0.3.5/v28 D-057 绘制余量修正 — 2026-09-02

- 五份目标车机记录显示 v28 UI 约占单核 68.6–71.7%，明显高于 v25 的 16.6–18.6%。根因是 10 fps 车轮每步都重建整页、重画八份全屏辉光并逐条画扫描线；不变目标也会重启 300 ms 过渡。
- D-057 只改 `PixelDashboardView.kt` 的 presentation 绘制：缓存非车轮整页与辉光，车轮作为独立层绘制，扫描线改为单个有界重复位图图案，并抑制无变化的数值/目标。精确坐标、CRT 参数、动画规则、Idle Check、按钮与全部数据语义不变。
- Android Emulator 36.6.11 在中文用户路径下破坏自身 QEMU/BIOS 路径；通过 `C:\AndroidCodex` 英文 junction 与全新隔离 AVD 修复。原版和候选均在同一 API 26/1280×720/SwiftShader 环境测试。
- 原版 12 秒窗口为 103/103 卡顿帧、中位 40 ms、9 次慢位图上传；候选为 59/104、中位 20 ms、0 次慢上传，CPU 八次采样 0–8%、平均约 3.5%，PSS 35,894 KB。完整画面和设置浮层截图通过。局部 dirty-rectangle 实验因 API 26 未可靠保留未刷区域而被删除。
- 最终本地 111/111 direct JUnit、lint 0 errors/9 warnings、assemble、包身份和固定 v2 签名通过。APK 2,656,023 bytes，SHA-256 `b8cfe5d5497e13eb30a8eb4d02695ae2eed9de50a91bac265cd74af2cc28bbab`。完整报告为 `CODE_REVIEW_V0.3.5_V28_D057.md`。目标车机 CPU/配对 OBD 仍是后续 gate；未 push、commit、PR 或发布。
