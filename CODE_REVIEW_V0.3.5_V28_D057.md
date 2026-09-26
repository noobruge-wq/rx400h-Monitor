# RX400h Monitor V0.3.5/v28 D-057 绘制性能审查

日期：2026-09-02  
范围：Presentation-only renderer headroom correction  
Repository：`noobruge-wq/rx400h-Monitor`  
Branch：`v0.3.0`  
Base HEAD：`8f1a3b9fb49359484c283ea6c3eaf69c631b4b45`  
状态：dirty worktree，本报告不代表已提交或已发布 baseline

## 1. 结论

D-057 已完成本地实现、构建和同一虚拟机 A/B 对照。它没有重做或移动用户确认过的 v28 界面，只减少重复绘制：不变的整页与辉光被缓存，10 fps 车轮作为独立小层绘制，扫描线由数百条逐条命令合并为一个小型重复图案，不变的数值目标不再反复重启过渡。

同一 API 26、1280×720、SwiftShader 虚拟机上，12 秒稳定窗口的中位帧时间从 40 ms 降到 20 ms，慢位图上传从 9 次降到 0。目标车机仍需复测，因为虚拟机数据只适合前后对照，不能替代车机绝对 CPU 结果。

## 2. 根因

五份 2026-09-01/02 目标车机记录显示 v28 UI 约占一个核心的 68.6–71.7%，而 v25 约为 16.6–18.6%。代码审查确认，车轮每 100 ms 更新一次时会让完整 1280×720 页面变脏，随后重建所有图层、发出八次全屏辉光绘制并逐条画扫描线。部分重复信号版本还会在显示结果未变化时重新开始 300 ms 过渡。

## 3. 本轮代码改动

唯一涉及的生产代码：

- `app/src/main/java/com/guanyu/rx400hprobe/PixelDashboardView.kt`

核心变化：

- 增加一个固定 1280×720 ARGB 页面缓存；只有可见文字、状态、控制、SOC/功率过渡或设置真正变化时才重建。
- 车轮不再让静态页面重新合成；只选择现有缓存轮帧并绘制局部辉光。
- 扫描线使用 1 像素宽、固定高度的小位图重复图案，一次覆盖输出画布；参数变化时才重建该小图案。
- SOC、HV 功率和车轮目标若与当前目标/显示值相同，不再重新创建过渡。
- 文本只在最终格式化字符串发生变化时才令页面缓存失效。

曾尝试仅失效车轮矩形，但 API 26 图形缓冲没有可靠保留未刷新区域，截图出现整页缺失。该实验已完整回退，最终源码和 APK不存在此路径。

## 4. 明确未改动

本轮没有修改：

- `DeadlineScheduler`
- request table / polling periods
- protocol whitelist / Bluetooth / ELM transport
- decoder formulas
- `SignalStore` semantics
- Idle Check 阈值、激活/退出判定
- logger、session、保存/恢复策略
- 三按钮业务行为
- v28 JSON 坐标、图像遮罩、字体大小、CRT 默认设置、动画时序和 10%/100% Idle Check 外观

## 5. 验证结果

| 项目 | 原始 v28 | D-057 最终候选 |
|---|---:|---:|
| API / 画布 | API 26 / 1280×720 | 同一 AVD |
| 稳定采样窗口 | 12 秒 | 12 秒 |
| 总帧数 | 103 | 104 |
| 卡顿帧 | 103（100%） | 59（56.73%） |
| 中位帧时间 | 40 ms | 20 ms |
| 90 分位 | 46 ms | 30 ms |
| 95 分位 | 48 ms | 32 ms |
| 慢位图上传 | 9 | 0 |
| App CPU 抽样 | 4–12% | 0–8%，8 次平均约 3.5% |
| 总 PSS | 约 33 MB | 35,894 KB |

内存增加来自一个有界的完整页面缓存，换取每个车轮帧不再重新构建整页。没有 steady-state bitmap/Paint/Path 分配。

构建与静态门禁：

- 生产及测试 Kotlin 编译：通过
- direct JUnit：111/111 通过
- `lintDebug`：0 errors / 9 既有非阻断 warnings
- `assembleDebug`：通过
- package：`com.guanyu.rx400hprobe.debug`
- versionCode：`28`
- versionName：`0.3.5-debug`
- APK Signature Scheme v2：通过
- 签名证书 SHA-256：`77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192`

标准 Gradle test worker 仍受本机 Windows 中文路径的 `GradleWorkerMain`/closed-pipe 启动故障影响；测试类已由 Gradle 编译，并使用项目既有 direct JUnit 方式实际执行，不把宿主启动故障伪装成标准任务通过。

## 6. 最终 APK

```text
../../deliverables/RX400h-Monitor-v0.3.5-v28-D057-renderer-debug.apk
../../outputs/v28-gui-retry/isolated-avd-home/artifacts/v28-d057-debug.apk
```

- 大小：2,656,023 bytes
- SHA-256：`b8cfe5d5497e13eb30a8eb4d02695ae2eed9de50a91bac265cd74af2cc28bbab`

## 7. 视觉证据

```text
../../outputs/v28-gui-retry/isolated-avd-home/artifacts/v28-d057-first.png
../../outputs/v28-gui-retry/isolated-avd-home/artifacts/v28-d057-steady.png
../../outputs/v28-gui-retry/isolated-avd-home/artifacts/v28-d057-settings.png
```

完整页面、用户既定坐标、字体、扫描线、辉光、车辆图、功率条、低亮度 Idle Check 和设置浮层均保留。车轮角度会随截图时刻变化，不属于布局差异。

## 8. 下一步 gate

在目标 API 27/1280×720 车机安装这一精确 APK 后，用相同预览/实时仪表条件记录 CPU。若长期 UI CPU 明显低于 v28 的 68.6–71.7% 且界面无新残影、闪烁或丢画，再继续调度器高速策略验证。当前候选未 push、未 commit、未建 PR、未发布。
