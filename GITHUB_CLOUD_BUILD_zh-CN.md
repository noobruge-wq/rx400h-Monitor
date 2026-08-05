# GitHub 云端自动编译说明（v0.1.8）

推送到 `main` 或 `master` 后，GitHub Actions 会自动：

1. 使用 JDK 17 与 Gradle 8.9 编译 Debug APK；
2. 使用项目内固定测试证书签名；
3. 用 `apksigner` 验证最终 APK；
4. 输出 APK、签名报告及 SHA-256 校验文件。

Artifact 名称：

`RX400hProtocolProbe-v0.1.8-debug-signed`

此版本沿用 v0.1.6/v0.1.7 的固定测试签名，可覆盖安装此前同签名的 Debug 版本。
