# RX400h Monitor — CODEX_HANDOFF

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
- 本次没有 commit、push、安装或车辆动作。clean commit/CI、API 27、paired-OBD connection → LIVE → End/public-save/recovery smoke 与同 periods E1 全部仍待完成；这些完成前不得 promotion，也不得提高频率。
