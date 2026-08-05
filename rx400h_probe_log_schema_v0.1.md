# RX400h Protocol Probe V0.1.8 日志结构

## raw_io.jsonl

每个 ELM 事务一行，保留：

- 发送 Header；
- ELM 完整命令，包括响应行数尾数；
- 原始响应行；
- 延迟；
- Prompt；
- 状态；
- 单调时钟和墙上时间。

## decoded.jsonl

每个已解析信号一行：

```text
signal
value
unit
source_command
raw_bytes
formula_version
```

当前 decoder version：

```text
rx400h-ha-static-20260805-001
```

## frames.csv

每秒快照字段包括：

- RPM、车速、冷却液、12V；
- SOC、HV 电压、电流、功率；
- 电池温度 min/max/avg 和 T1–T8；
- MG1/MG2/Rear MG 转速与扭矩；
- Injection 和 ICE torque raw。

## session.json

新增：

```text
protocol_profile_version
 decoder_version
 logger_degraded
 evidence_complete
```

若任一关键日志写入失败，`evidence_complete` 必须为 false。

## manifest.json

包含每个会话文件的：

```text
文件名
大小
SHA-256
```

并记录 profile 和 decoder 版本。
