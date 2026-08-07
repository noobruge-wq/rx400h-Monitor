# RX400h Protocol Probe V0.1.8 → UI/代码收敛审查

日期：2026-08-07

## 0. 审查边界

本次直接以已经实车验证的 V0.1.8 源码为基线。V0.1.9 仅作为历史候选对照，不作为代码基线。

目标不是重构协议架构，而是：

- 删除已经完成历史使命的 Probe UI/扫描遗留；
- 消除明确死代码、重复缓存和错误语义；
- 把必须保留的底层证据移出驾驶主界面、继续写入日志；
- 不改变 V0.1.8 已验证车辆请求集合；
- 本轮不引入快速调度、ForegroundService 或新协议请求，避免 UI/代码清理与总线时序变更混在一次回归中。

## 1. 已确认并删除的陈旧代码

| 项目 | V0.1.8 状态 | 裁决 |
|---|---|---|
| 右侧 raw HEX 实时面板 | 每个事务都 append 到 TextView，最多保留约 40k 字符 | 删除 UI 路径；原始数据继续写 `raw_io.jsonl` |
| `链路确认` 手动流程 | 为协议未闭环阶段设计 | 删除按钮及调用路径；当前 7E0/7E2 链路已有 HCI+实车证据 |
| `HA链验证` 手动流程 | 与已经确认的 Runtime 请求重复 | 删除按钮及重复验证路径 |
| READY / ENGINE_STARTED / ENGINE_STOPPED / EV_MOVE / REGEN / STOP 手动事件按钮 | 早期协议研究标记工具 | 从驾驶 UI 删除；自动 LIVE/RECONNECT/SESSION 事件日志保留 |
| `ProtocolAttempt` / `bestProtocol` | 已无真正协议扫描；bestProtocol 无读取者 | 删除 |
| `logProtocolAttempt()` / `logProtocolSummary()` | 仅定义、无调用 | 删除 |
| `protocol_matrix.csv` | 当前 Runtime 不再生成协议矩阵数据，但仍创建空文件 | 删除 |
| `SignalValue.rawResponse` | 每个信号重复保存 raw 字符串 | 删除；底稿由 `raw_io.jsonl` 统一承担 |
| `ObdParsers.rounded()` | 无调用 | 删除 |
| C3 `d[8:10]/100 = ICE Power` | 与 HCI 同步 ICE Torque×RPM 机械功率不一致 | 删除错误语义 |
| C4 未消费的 secondary ratio / brake accumulator 字段 | 无当前产品用途，且原始帧已保存 | 从 Runtime typed model 删除 |
| CF 未消费 scalar/status 字段 | 无当前产品用途，且原始帧已保存 | 从 Runtime typed model 删除 |

## 2. 保留但不在驾驶主界面展示的数据

下列数据对后续证据、状态机或离线研究仍有价值，因此不因“UI 不显示”而删除：

- 所有 `raw_io.jsonl` 原始 TX/RX；
- `decoded.jsonl`；
- `request_stats.csv`；
- CAN ID / ISO-TP 原始结构；
- T1–T8 电池温度；
- Engine Load / Ignition Timing / MAF；
- Injection µL；
- MG1/MG2/MGR RPM 与 Torque；
- `61C3` Brake Regen / Master Torque 候选字段。

原则：**驾驶 UI 只展示用户需要理解的车辆状态，研究证据继续完整落盘。**

## 3. 本候选实施的结构性收敛

### 3.1 UI 与控制器分离

新增 `DashboardUi.kt`。`MainActivity` 不再直接构造 raw/debug 双栏界面，而只提供 Dashboard snapshot/status。

这不是最终 Repository/Service 架构，只是先切开“驾驶呈现”和“协议事务”，防止下一轮正式 Dashboard 再次污染通信代码。

### 3.2 主界面控制收敛

仅保留：

- 设备；
- 连接/断开；
- 开始/停止实时；
- 结束并导出。

没有任意 CAN/ELM 输入框，也没有重新引入扫描入口。

### 3.3 正确语义进入 typed model

- `21CDF3`: `iceTorqueNm`, `injectionUl`；Injection 不用于“是否燃烧”的判定。
- `21C4`: 加入已由 HCI 重放确认的 `warmupActive = (d[1] & 0x01) != 0`。
- ICE mechanical power 仅由 `ICE Torque × 2π × RPM / 60 / 1000` 派生。
- `61C3` 两个 Brake 字段暂以 `Candidate` 命名，只记录，不在 UI 声称为已闭环 Brake 状态。

## 4. 额外发现并修正的明确缺陷

### P1 — Response Pending 假阳性

V0.1.8 在整段拼接 hex 中用 `7F??78` 搜索 Pending。2026-08-07 实车日志存在正常 Mode 01 数据：

```text
7E807410575067F0778
7E807410577067F0778
7E80741056A067F0778
```

旧逻辑会把数据内部的 `7F 07 78` 错记为 `response_pending_seen=true`。

本候选改为：只解析 CAN/ISO-TP payload 起点，并要求负响应严格为：

```text
7F <requested service> 78
```

用该实车日志回放后，旧逻辑 3 个假阳性，新逻辑 0 个。

### P1 — 合法 AT 文本统计误报

`ATI / STI / AT@1 / ATDP / ATDPN` 的合法文本输出在旧 request stats 中可能不是 OK。本候选明确按适配器文本命令分类。

### P1 — filesDir fallback 与 FileProvider 不一致

Logger 已支持 `getExternalFilesDir(null) ?: filesDir`，但旧 FileProvider 只公开 external-files-path。候选加入 internal files path，避免真的触发 fallback 后 ZIP 无法分享。

### Privacy — OBD MAC 进入分享日志

`device.json` 不再写明文 MAC，改为 `adapter_id_sha256`。设备名称仍保留用于诊断。

## 5. 本轮刻意没有做的修改

这些问题存在，但不应与本次 UI/死代码清理混成同一个变量集合：

| 项目 | 原因 |
|---|---|
| V0.1.9 的 450ms/20ms 快速调度 | V0.1.9 对 CDF3/C4 的降频已被后续 HSD/PSD 证据淘汰；需重新设计 |
| 直接复制 HA ~5.5Hz 核心循环 | HA HCI 给出可行性上界，不等于本项目必须使用相同负载 |
| ForegroundService | 最终 Monitor 必须做，但属于下一阶段生命周期架构，不应掩盖本轮 UI 回归 |
| Activity Result API 迁移 | 低收益兼容性清理，当前不是阻塞项 |
| 完整 HSD S0–S4 / PowerBall / Brake overlay 渲染 | Brake overlay 与 EV PowerBall preference 仍有覆盖缺口；不应在 Probe UI 先造默认值 |
| 大型能量流动画 | 目标弱车机；最终只考虑轻量 Canvas/Vector，不引入图片动画框架 |

## 6. 仍需后续处理的技术债

- Activity 当前仍持有 Bluetooth/worker/logger 生命周期；正式 Monitor 前迁入 ForegroundService。
- `onDestroy()` 对 active session 仍只是 close writer，不是可恢复的 crash/orphan session 机制。
- 当前 scheduler 仍为 V0.1.8 慢速时序，实际约 0.36Hz；清理版回归通过后单独改成保守的约 1.5–2Hz core。
- Brake Regen/Master Torque 的字节/公式还需 HA APK 直接调用链闭环后才能去掉 `Candidate`。
- Idle Check 已有强实车行为样本，但缺同步 HA UI/HCI 的最终覆盖。

## 7. 本轮安全边界

没有新增车辆请求。运行时仍仅允许当前 Profile：

```text
01040C0D0E10 2
01050607 1
21CDF3 3
21C3 6
21C4 5
21CF 4
ATRV
```

没有 `22xxxx / 2C / 10 02 / 10 03 / 7E1 / 7E3 / 7E4`，没有任意命令输入。
