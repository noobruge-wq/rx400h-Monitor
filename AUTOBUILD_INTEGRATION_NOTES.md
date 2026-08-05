# v0.1.8 自动构建整合记录

在原始 v0.1.8 源码基础上仅加入/修正：

- GitHub Actions 自动编译与 `apksigner` 验证；
- 沿用既有固定 Debug 测试签名；
- `versionCode` 从 7 修正为 8；
- SHA-256 输出按无符号字节格式化：`it.toInt() and 0xFF`。

原始源码 ZIP SHA-256：

`174d5de7e8a295860f5e5577ab4d2e48e247ab674f4fbbc0b88a97d52a3d7cef`
