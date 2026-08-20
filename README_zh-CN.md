# RX400h Monitor — V0.3.0 Scheduler / Refresh Frontier 开发分支

> **状态：V0.2.0 仍是已关闭的工程基线；V0.1.10 是历史实车验证基线。** 当前已安装/实车证据候选为 V0.3.2/v24，当前本地源码候选为 V0.3.3/v25（恢复、生命周期、可信成本、校时观察与 CRT Green 整合），尚未 promotion。车辆请求白名单、decoder、SignalStore 与七个轮询周期保持冻结，rate ladder 未授权。

## V0.2.0 做什么

- 轻量 typed `SignalStore`：每个信号带 value / source timestamp / update timestamp / age / quality / version / source，时间统一用 monotonic clock。
- 事件/脏标记驱动的 UI 发布：不再每 500 ms 无条件全量重绘，只有变化的字段才更新。
- 三域默认 UI：BATTERY、VEHICLE STATUS、POWER；`IDLE CHECK` 仅在判定为真实 Idle Check 时显示。
- 最小 `IdleCheckEligibilityState`（实验性）：warmup active、900<RPM<1100、ICE 功率≈0 kW、speed≤55 km/h、稳定约 1 s；状态转换写入日志等待 replay/实车验证。
- Consumer audit：删除无消费者的 typed 字段（Engine Load、MAF、Timing、Injection、MG1/MG2/MGR、rear MG、brake candidates），原始响应仍完整保留在 `raw_io.jsonl`。
- 性能/健康观测基线：`performance.csv` 记录 PSS、Java heap、CPU delta、GC/allocation 计数、调度周期、UI 渲染耗时、logger 写耗时。
- 固定 `RequestTable` 描述请求周期/优先级，为 V0.3.0 的 deadline/priority 调度准备接口；V0.2.0 实际调度时序与 V0.1.10 保持一致。

## 默认 UI

```text
BATTERY
  SOC (A)
  AVG TEMP (A)
  MAX / MIN (B)

VEHICLE STATUS
  SPEED (A)
  COOLANT (A)
  12V OBD (A)

POWER
  ICE POWER (A)
  ENGINE RPM (A)
    IDLE CHECK (B, 条件显示)
  HV BATTERY POWER (A)
```

不显示 S0–S4、Warmup 文本、MG1/MG2/MGR 功率行；所有原始响应仍完整写入 `raw_io.jsonl`。

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

**保持 V0.1.10 时序不变。** 高刷新率探索属于 V0.3.0，前提是 V0.2.0 的性能观测基线建立完成。
