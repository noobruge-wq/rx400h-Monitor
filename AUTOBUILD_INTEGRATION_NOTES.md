# V0.1.10 UI/cleanup candidate 自动构建整合记录

基础源码：`RX400hProtocolProbe_v0.1.10_cleanup_candidate_source.zip`

本整合包只额外加入自动构建/固定签名所需内容，以及保持与历史自动构建包一致的显式 SHA-256 Byte 无符号格式化：

- 恢复 `.github/workflows/build-apk.yml`；
- 复用 V0.1.6–V0.1.8 的 `.github/signing/rx400h-debug.keystore`；
- `debug` buildType 使用 `githubDebug` 固定签名；
- 保持 `applicationIdSuffix = ".debug"`；
- 保持 `versionCode = 9` / `versionName = "0.1.10"`；
- SHA-256 格式化显式使用 `it.toInt() and 0xFF`；
- 不改变当前车辆请求白名单或 Scheduler。

固定 keystore SHA-256：

`8e2ecdec24f9f628fd788cd4cb97e614172d3bb600e8d08d8fa8d4a87247bcb1`

签名证书 SHA-256：

`77:BA:84:B1:F4:F7:37:A5:D6:1B:91:0B:F4:38:6D:F1:67:54:8B:9C:6C:E6:89:ED:25:E9:94:C3:7B:2B:C1:92`
