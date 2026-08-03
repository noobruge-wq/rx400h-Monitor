# GitHub 云端自动编译说明

本项目已加入 `.github/workflows/build-apk.yml`。

## 自动触发

将完整项目上传到 GitHub 仓库的 `main` 或 `master` 分支后，GitHub Actions 会自动开始编译 Debug APK。

## 手机手动触发

1. 用手机浏览器打开项目仓库。
2. 进入 `Actions`。
3. 打开 `Build Android APK`。
4. 点击 `Run workflow`。
5. 再点击绿色的 `Run workflow`。

## 下载 APK

1. 在 `Actions` 中打开最新一次带绿色对勾的运行记录。
2. 滑到页面底部的 `Artifacts`。
3. 下载 `RX400hProtocolProbe-v0.1.6-debug`。
4. 解压下载的 ZIP。
5. 安装 `RX400hProtocolProbe-v0.1.6-debug.apk`。

压缩包内还包含 `SHA256SUMS.txt`，可用于核对 APK 文件是否完整。

## 说明

- 每次向 `main` 或 `master` 分支上传修改，都会自动重新编译。
- 连续快速上传时，旧的未完成任务会自动取消，只保留最新一次编译。
- APK 构建产物保留 30 天。
- 当前生成的是带独立包名后缀的 Debug 版：`com.guanyu.rx400hprobe.debug`。
- Debug 版适合协议研究和频繁测试，不建议作为最终长期发布版。
