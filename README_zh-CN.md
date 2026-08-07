# RX400h Monitor / Protocol Probe V0.1.10 清理候选版

> **状态：源码候选，未实车验证，不是协议新版本。**
>
> 基线：V0.1.8 已验证请求链。V0.1.9 已作废，本候选没有从 V0.1.9 继续开发。

## 本候选的目标

这次只做 UI / 代码收敛和已确认的解析语义修正，不增加车辆请求，也不改变 V0.1.8 的调度周期与请求频率。快速调度优化留给独立测试版本，避免把 UI 回归和总线时序变化混在一次实车测试里。

## 默认 UI

默认界面改为驾驶可读布局：

- 大号车速、SOC、HV 电池功率、RPM；
- 显示冷却液、电池最高温、12V OBD、ICE 状态、Warmup 状态；
- 仅保留设备、连接/断开、开始/停止实时、结束并导出四个控制；
- 不显示十六进制响应、CAN ID、原始请求、事务滚动文本；
- 删除旧的链路确认、HA 链验证和 READY/REGEN 等手动事件按钮；
- 所有原始响应仍完整写入 `raw_io.jsonl`，没有减少证据数据。

## 代码清理

- 删除已无调用的 `ProtocolAttempt`、`bestProtocol`、`protocol_matrix.csv`、`logProtocolAttempt()`、`logProtocolSummary()`；
- 删除 `SignalValue.rawResponse`，避免每个 Signal 在内存中重复保存原始字符串；
- 删除 V0.1.8 中错误的 `61C3 d[8:10]/100 = ICE power` 字段；
- `21CDF3` 字段按已恢复 HA 语义命名为 `ice_torque_nm` 和 `injection_ul`，但不再用 Injection 判断燃烧状态；
- `21C4` 加入已由 HCI 重放确认的 `warmup_active = (d[1] & 1) != 0`；
- 标准组合请求已有的 Engine Load / Ignition Timing / MAF 正式写入日志，但不占主 UI；
- `Response Pending` 改为按 CAN/ISO-TP payload 起始位置和请求服务号判断，避免普通数据中的 `7F xx 78` 假阳性；
- AT 文本响应 `ATI/STI/AT@1/ATDP/ATDPN` 可正确分类为 OK；
- BluetoothAdapter 获取改为 BluetoothManager；
- FileProvider 同时允许 external files 与 filesDir fallback 的日志 ZIP 分享；
- 日志中的 OBD 适配器 MAC 改为 SHA-256 标识，不再直接写出地址。

## 请求安全边界

运行时车辆请求仍严格限定为：

```text
01040C0D0E10 2
01050607 1
21CDF3 3
21C3 6
21C4 5
21CF 4
ATRV
```

没有加入 `22xxxx`、`2C`、`10 02`、`10 03`、7E1/7E3/7E4 扫描或任意命令输入。

## 调度

**保持 V0.1.8 时序不变。** 这是刻意的：先验证 UI/清理版没有回归，再单独推进基于 HA HCI 的 1.5–2 Hz 核心调度。
