# SOURCE VALIDATION — 2026-08-07

对象：RX400h Protocol Probe V0.1.10 UI/cleanup candidate

## 状态

**源码候选；未执行 Android `assembleDebug`；未生成 APK；未实车验证。**

## 已完成离线验证

1. `ProbeModels.kt + ObdParsers.kt` 使用 `kotlinc 1.9.0` 编译。
2. 真实响应向量 parser 测试：
   - `61C3`: SOC / HV voltage / HV current；
   - `61CF`: T1–T8 battery temperature；
   - 标准 `41 0C / 41 0D`；
   输出：`parser tests passed`。
3. 全部 Kotlin 源码使用最小 Android/AndroidX/JSON 类型桩统一编译，无项目 Kotlin 编译错误（桩自身仅有未使用参数 warning）。
4. 静态扫描确认 Runtime 没有重新出现：
   - `221001 / 221002 / 221814 / 220103 / 221F07`
   - `1002 / 1003`
   - `2C01F301 / 2C01F302`
   - `ATSH7E1 / ATSH7E3 / ATSH7E4`
5. 静态扫描确认源码不再含：
   - `rawText / appendRaw`
   - `ProtocolAttempt`
   - `protocol_matrix`
   - 旧手动 READY/ENGINE_STARTED/ENGINE_STOPPED/EV_MOVE/STOP 控件路径。
6. 用 2026-08-07 实车日志中的三个旧 Pending 假阳性样本验证新 Pending 判断：旧规则命中 3，新规则命中 0。
7. ZIP 打包前将执行 `unzip -t` 和 SHA-256。

## 尚未完成

- Android SDK/AGP `assembleDebug`；
- Android lint；
- APK 安装；
- 实车连接/日志回归；
- 横竖屏实体设备检查。

因此本候选不得当作已发布 V0.1.10，也不得替代 V0.1.8 的实车证据基线，直至正式 Android 构建和回归通过。
