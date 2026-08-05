# V0.1.8 源码离线校验记录

## 已完成

1. `ProbeModels.kt` 与 `ObdParsers.kt` 使用本机 Kotlin/JVM 编译器成功编译。
2. 使用 HCI 抓包中的真实响应测试：
   - `61C3` 成功解码 SOC、HV V/A/kW、MG1/MG2；
   - `61CF` 成功解码 8 路电池温度；
   - `41` 组合响应成功解码 RPM 和车速；
   - `61CDF3` 成功解码喷油量与 ICE torque raw。
3. 全部 Kotlin 文件使用最小 Android API 类型桩完成静态类型编译，用于排除 Kotlin 语法、函数签名和项目内部类型错误。
4. 两份 Profile JSON 均通过 JSON 解析。
5. 运行时代码中未发现旧版 `221001/221002/221814/220103/221F07` 扫描请求。
6. 运行时 Toyota 命令限定为 HCI 已观察白名单。

## 关键测试向量

### 21C3

```text
7EA102761C33FFF0FA0
7EA213FFF0FA0000000
7EA22000000914C3800
7EA23800042423F3EA0
7EA2400800180810000
7EA25FF000000000000
```

结果：SOC 56.8627%；HV 320 V；0 A；0 kW。

### 21CF

```text
7EA101E61CF80BA41B0
7EA210000000086C986
7EA2296859E8753863B
7EA2385D286BD863B80
```

结果：

```text
17.37, 16.86, 14.38, 18.75, 15.95, 14.90, 17.25, 15.95 °C
```

此响应由 ELM 的四行限制截断于 ISO-TP 声明长度之前；解析器只对 `21CF 4` 允许已知部分载荷，且要求至少 27 个 payload 字节。

## 未完成

- 当前环境没有 Android SDK/Gradle 构建链，未生成或安装 APK；
- 未在真实 Android Framework 和 Android Gradle Plugin 下执行正式 assemble；
- 未进行 V0.1.8 实车测试。

因此本交付是**通过离线类型和解码测试的源码候选**，不是已验证 APK。
