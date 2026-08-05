# RX400h Protocol Probe V0.1.8

V0.1.8 是首个基于 **Hybrid Assistant 真实 Bluetooth HCI/RFCOMM 抓包**构建的 RX400h 专有数据版本。它不再扫描未知 Header 或 DID，而是只执行已经在本车 HA 会话中观察到的只读命令。

## 本版核心变化

### 1. 删除旧 0x22 拓扑探测路线

不再发送：

```text
221001
221002
221814
220103
221F07
```

不再扫描：

```text
7E1 / 7E3 / 7E4
```

### 2. 使用抓包确认的真实 RX400h 链路

```text
7E0 → 7E8
01040C0D0E10 2
01050607 1
21CDF3 3

7E2 → 7EA
21C3 6
21C4 5
21CF 4
```

### 3. 七项核心数据全部进入运行时

| 数据 | 来源 |
|---|---|
| SOC | `7E2 / 21C3 6` |
| HV 电池功率 | `21C3` 中电压×电流 |
| 冷却液温度 | `7E0 / 01050607 1` |
| 发动机 RPM | `7E0 / 01040C0D0E10 2` |
| HV 电池温度 | `7E2 / 21CF 4`，8 路传感器 |
| 12V | `ATRV` |
| 车速 | `7E0 / 01040C0D0E10 2` |

同时显示 MG1、MG2、后轴 MG、发动机喷油状态和功率方向。

### 4. 结构化 CAN / ISO-TP 解析

新增：

- 按 CAN ID 解析响应；
- ISO-TP Single/First/Consecutive Frame 重组；
- Consecutive Frame 序号检查；
- 对 `21CF 4` 的已知、受限部分响应进行专门处理；
- 不再通过拼接全部字符串寻找响应前缀。

### 5. Logger 状态化

新增：

```text
IDLE
ACTIVE
FINALIZING
FINALIZED
FINALIZE_FAILED
```

ZIP 先写入 `.tmp`，成功后再替换正式文件。日志 I/O 降级会写入 `evidence_complete=false`，避免把不完整日志误标为完整证据。

## 使用流程

1. 选择已配对的 OBDLink。
2. 点击“连接”。
3. 点击“链路确认”，确认 `7E0→7E8` 和 `7E2→7EA`。
4. 点击“HA链验证”，执行一次全部已知命令并检查七项数据。
5. 点击“开始实时仪表”，进行连续测试。
6. 测试结束后点击“结束并发送所有日志”。

## 推荐首次测试

首次 V0.1.8 测试只需：

```text
车辆 READY
P 挡静止 1–2 分钟
随后低速短途行驶 5–10 分钟
包含自然发动机启停、轻加速和回收减速
```

不要同时运行 Hybrid Assistant 或其他 OBD 应用。

重点观察：

- SOC 是否约在车辆仪表合理范围内缓慢变化；
- HV 电压是否约 288–400 V；
- 加速时 HV 功率是否趋向正值；
- 回收时是否趋向负值；
- 八路电池温度是否连续且变化缓慢；
- 发动机 RPM 大于零但 Injection 为零时，是否显示“ROTATING / NO INJECTION”；
- 长时间运行是否出现断流、STALE 或重连。

## 日志内容

每次会话包含：

```text
raw_io.jsonl
 decoded.jsonl
 frames.csv
 events.csv
 connection.log
 errors.log
 request_stats.csv
 protocol_matrix.csv
 session.json
 device.json
 manifest.json
```

`frames.csv` 已新增 SOC、HV V/A/kW、8 路电池温度、MG 数据和喷油量。

## 安全边界

- 严格单线程、单事务；
- 不提供任意命令输入；
- 不扫描 Header 或 DID；
- 不进入编程会话；
- 不执行控制、清故障或写入命令；
- 轮询频率低于 Hybrid Assistant 抓包中的实际频率；
- `21CF 4` 的部分 ISO-TP 解析仅适用于该抓包确认命令。

## 尚未完成

- APK 未在本交付环境中编译；
- 尚未经过 V0.1.8 实车日志复核；
- ForegroundService 尚未实施；
- 正式 CRT UI 尚未实施；
- 能量流显示仍是基础方向判断，不是最终动画状态机；
- `61C3` 四个辅助温度和 `61CF` 两个标量温度的具体身份仍未命名。

详细公式与证据边界见：

```text
RX400h_HA_Protocol_Mapping_v0.1.8.md
rx400h_ha_capture_profile_v0.1.8.json
```
