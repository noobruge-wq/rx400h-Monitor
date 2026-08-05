# RX400h Hybrid Assistant 协议映射 — V0.1.8

## 证据基线

本版本不再基于 Header/DID 猜测。请求链来自 Galaxy Z Fold5 上对 Hybrid Assistant 3.320.0 与 OBDLink 的 Bluetooth HCI/RFCOMM 抓包；字段公式来自同一 HA APK 的 RX400h 解码分支静态追踪，并用抓包中的实际驾驶数据做范围和符号交叉验证。

## 运行链

```text
ATSP6 / ATH1 / ATCAF1

ATSH7E0
01040C0D0E10 2   → 7E8 / 41
01050607 1       → 7E8 / 41
21CDF3 3         → 7E8 / 61CD

ATSH7E2
21C3 6           → 7EA / 61C3
21C4 5           → 7EA / 61C4
21CF 4           → 7EA / 61CF
```

尾数 `1/2/3/4/5/6` 是 ELM 最大响应行数，不进入车辆 CAN 载荷。

## 61C3 精确映射

以下 `d[0]` 表示 `61 C3` 之后的第一个数据字节：

| 字段 | 公式 |
|---|---|
| MG2 RPM | `u16be(d[0:2]) - 16383` |
| MG2 torque | `(u16be(d[2:4]) - 4000) / 8` Nm |
| MG1 RPM | `u16be(d[4:6]) - 16383` |
| MG1 torque | `(u16be(d[6:8]) - 4000) / 8` Nm |
| ICE power | `u16be(d[8:10]) / 100` kW |
| SOC | `d[14] / 2.55` % |
| Four auxiliary temperatures | `d[20..23] - 50` °C；身份暂不命名 |
| HV voltage | `d[24] * 2` V |
| HV current | `d[26] * 2 - 256` A |
| HV power | `HV voltage × HV current / 1000` kW |
| Regen brake raw | `d[30] * 4` |
| Master brake raw | `(d[32] - 255) * 8` |

抓包范围：SOC 56.47–61.57%；HV 298–376 V；电流 -58–84 A；功率 -21.692–26.712 kW。与 MG2 工况相关性确认：正功率为电池放电/牵引，负功率为充电/回收。

## 61CF 电池温度

RX400h 车型初始化在 HA 中明确配置 **8 个电池温度传感器**。不是此前猜测的“6 个电池温度 + 2 个进气/室内温度”。

```text
indices = [8,10,12,14,16,18,20,22]
T = (u16be(d[i:i+2]) - 32768) / 100 °C
```

本次抓包八路温度总范围约 14.38–19.37 °C。

`21CF 4` 的 ELM 行数限制会在 ISO-TP 声明长度完成前停止返回，但前四行已经包含 HA 所使用的全部八路温度字节。因此 V0.1.8 允许对该命令进行有边界的“已知部分载荷”解析；其他命令仍要求完整 ISO-TP。

## 61CDF3

`d[0]` 位于 `61 CD` 之后：

| 字段 | 公式 |
|---|---|
| ICE torque raw | `(d[3] - 128) * 2` |
| Injection | `u16be(d[12:14]) / 32` µL |

Injection 大于零可用于区分发动机正在喷油燃烧与仅被电机带转。

## 61C4

| 字段 | 公式 |
|---|---|
| Rear MG RPM | `u16be(d[2:4]) - 16383` |
| Rear MG torque | `(u16be(d[4:6]) - 4000) / 8` Nm |
| Unnamed ratio | `d[10] / 2.55`；不得标为 SOC |
| Regen accumulator raw | `d[24] * 4` |

## 已否决路线

V0.1.8 不再执行：

- `7E1/7E3/7E4` Header 扫描；
- `22xxxx` DID 扫描；
- Header `700` 下失败的 `10/2C/F301/F302` 兼容分支；
- 未经证据的 `10 02` 或 `10 03` 会话切换。

## 当前边界

解码公式已经闭环，但 V0.1.8 仍是研究型实车验证版本，不是正式长期车机仪表。前台服务、完整生命周期隔离、最终能量流状态机和 CRT UI 留到协议链经新版本实车复核后实施。
