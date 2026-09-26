# 原workspace工具副本

这些脚本原来位于工程目录的上一层 `work/`，为避免换电脑时丢失，按原字节保存。不是本轮新增/重写的测试，也不进入APK。

原结构：

```text
workspace/
  outputs/
  deliverables/
  work/
    rx400h-Monitor-crt/  ← 当前Git仓库
    capture_d060_pixels.py
    analyze_emulator_calibration.py
    ...其他本目录脚本
```

如要复用原命令，可在新workspace中恢复相同结构，或先审查并调整脚本内路径。包含硬编码ADB路径/模拟器编号的旧工具必须先核对，不直接指向实体车机。输入行车ZIP、输出证据、SDK/AVD不随这些脚本上传。
