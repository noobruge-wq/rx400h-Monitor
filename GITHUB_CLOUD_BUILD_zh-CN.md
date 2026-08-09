# GitHub 云端自动编译说明（V0.2.1 UI 调整）

本包已整合与 V0.1.6、V0.1.7、V0.1.8 相同的固定 Debug 测试签名。

推送到 `main` 或 `master` 后，GitHub Actions 自动：

1. 使用 JDK 17 与 Gradle 8.9 编译 `assembleDebug`；
2. 使用项目内固定测试证书签名；
3. 使用 Android `apksigner` 验证 APK 签名；
4. 输出 APK、签名报告及 SHA-256。

Artifact：

`RX400hProtocolProbe-v0.2.1-ui-debug-signed`

APK：

`RX400hProtocolProbe-v0.2.1-ui-debug-signed.apk`

签名证书 SHA-256：

`77:BA:84:B1:F4:F7:37:A5:D6:1B:91:0B:F4:38:6D:F1:67:54:8B:9C:6C:E6:89:ED:25:E9:94:C3:7B:2B:C1:92`

`versionCode = 11`，高于 V0.2.0 的 `versionCode = 10`。在设备上已安装同一固定签名的 Debug 版时，应可直接覆盖升级。

> 注意：这是研究/测试 Debug 签名，不是发布商店使用的正式 release key。
