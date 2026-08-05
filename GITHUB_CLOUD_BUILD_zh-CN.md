# GitHub 云编译说明

项目已配置 `.github/workflows/build-apk.yml`。

触发方式：

- 向 `main` 或 `master` 分支推送代码时自动编译；
- 在 GitHub 仓库的 **Actions → Build Android APK → Run workflow** 手动编译。

构建环境：

- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3

## 签名与安装

项目包含专用于内部测试的固定 Debug Keystore：

`.github/signing/rx400h-debug.keystore`

Debug 构建会始终使用同一密钥签名，因此后续 GitHub Actions 生成的 APK 可以覆盖安装并保留应用数据。工作流会在上传前通过 Android SDK `apksigner` 验证签名；验证失败时任务会直接失败。

构建成功后，在运行详情底部下载 Artifact：

`RX400hProtocolProbe-v0.1.7-debug-signed`

解压后安装：

`RX400hProtocolProbe-v0.1.7-debug-signed.apk`

该密钥仅适合内部 Debug 测试，不应用于正式发布版本。
