# V0.3.5/v28 D-058：动态界面本地验证补强

创建：2026-09-05；续跑：2026-09-06。初始受阻记录保留在下文，不代表续跑后的最新状态。

## 2026-09-06 运行检查点

用户已手动重启并授权继续。WHPX 返回 0 / usable，API 26 模拟器实际启动成功，验证包 `b388efed…e68b7` 已仅安装到 `emulator-5560`。parked/wheel/recorded 各一轮、stress5 三轮已完成，3,868 输入全部交付、零跳过；三轮压力 CPU 11.23% / 11.25% / 11.54%。完整记录的显示行驶段 4.54%、停车段 0.42%。设置/Idle/自检采样和同环境旧包 GUI 对照已完成，但发现 R1 辉光边缘像素不等价，不能标记完全视觉一致或发布。完整结果：`../../outputs/d058-runtime-20260906/D058_RUNTIME_REPORT.md`。

本次不改产品或 debug Kotlin、布局资源、效果参数、协议、调度器、解码、SignalStore、Idle Check、logger 或会话代码；只执行本地验证并整理证据。不能把本机结果当作弱车机已拟合、D-057 同输入 A/B 或实车 5 Hz 达标。

## 2026-09-05 初始构建与阻碍记录

**后续环境修复记录：** 用户另行授权后完成管理员实查：HypervisorPlatform 原本已开启，但 Hyper-V 核心关闭且当前未运行。现已启用核心与平台服务，管理工具保持原来的关闭状态；Windows 明确要求重启，尚未重启或验证加速可用。没有修改本报告对应 APK 或产品源码。恢复点及完整前后状态在 `../../outputs/emulator-acceleration-repair/README.md`。以下“未安装驱动/未开启功能”的描述保留的是最初动态验证阶段；以本段后续记录区分时间，勿再重复安装。

## 结论先说

之前的固定数值预览不能证明行驶时的界面性能。这次补齐了持续变化的输入和观测手段，先在本地排查，不要求用户现在再上车提供试错样本。

但不能把“测试代码写好、编译通过”当成“D-058 已经更快”。本机模拟器加速检查仍返回 6：`Android Emulator hypervisor driver is not installed on this machine`。本轮没有安装驱动、开启 Windows 功能或重启，因此没有新的截图、帧率、GPU 或 D-058 CPU 成绩。弱车机拟合也尚未达成，不能承诺已有 80% 准确率。

## 1. 基线与边界

- 源码目录：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\rx400h-Monitor-crt`。
- 分支 `v0.3.0`，HEAD `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45`，工作树原本已 dirty。
- `git fetch origin v0.3.0` 成功；当前 `origin` 实际指向本机镜像 `C:\Users\冠儒\Documents\Codex\rx400h-Monitor`，其分支为 `3b48d2d`。这不是本轮直接核验 GitHub 云端最新状态。
- 未提交、推送、安装 APK 或进行实车操作；旧文件、日志 ZIP 和旧 APK 保留。
- 本轮没有修改任何 `app/src/main` 产品源码或资源。协议、调度器、请求频率、解码、SignalStore、Idle Check、保存恢复及三个产品按钮均未变。

## 2. 动态测试入口

新增 `src/debug` 专用 `DashboardReplayActivity`，直接使用当前产品的 `PixelDashboardView`，不另造简化画面替代它。

| 模式 | 输入 | 能回答什么 |
|---|---|---|
| `parked` | 120 秒，5 Hz 重复发布静止值 | 无可见变化时是否仍反复重画 |
| `wheel` | 120 秒，速度固定 42，其余值固定 | 独立车轮成本，与此前固定预览对照 |
| `recorded` | 9 月 3 日记录中 863 个显示帧，946308 ms | 实际数值轨迹下的变化、停车与过渡 |
| `stress5` | 120 秒，200 ms 一次，共 601 个端点样本 | SOC、功率、速度、转速、机械功率持续变化时的 UI 压力 |

这些模式不是新增调度策略，也不是 5 Hz OBD 采集。记录中的 `frames.csv` 约 1 Hz、只有墙钟、没有 freshness，不能完整重现原始异步信号发布。导出保留显示值、缺失值和时间间隔，不插值补造原始数据；在调试输入中非空值被假设为 fresh。这一限制写入了伴随 JSON 和本文件。

回放全部预加载、最多 1801 帧/30 分钟，运行时没有不断增长的历史队列。若 UI 来不及处理，只交付最新到期样本，并累计跳过数，不用追赶循环假装保持频率。离开 Activity 会终止这次测量，不把后台停顿混入前台成绩。开始会触发既有自检；正常结束另留 2 秒收尾。

`RX400hReplay` 每 5 秒记录过程 CPU、PSS、实际发布数、来源帧索引、跳过数、最大交付迟到、窗口帧数、FrameMetrics 耗时及丢失回调数。`RX400hRenderer` 保留 R1 的重建次数、软件重建墙钟/UI 线程 CPU、车轮绘制和硬件 Canvas 观测。

注意：FrameMetrics 的总时长不是独立 GPU 耗时，也不是信号更新率。120 秒压力场景的预加载和计数自身有小额、受限开销，不能把其整个进程 CPU 直接等同车上完整 App 的 CPU。

## 3. 修正性能统计口径

校准结果目录：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\outputs\emulator-calibration-v2`。

- 21 个目标车机会话、5 个版本名、7 个可核实 APK，加 1 个缺失 APK 哈希的历史组。
- 4636 个相邻单调时间区间，共约 395.8 分钟，含有质量标记的区间。
- CPU 分母用相邻 `elapsed_ms`，不再用会受手机校时影响的日期时间。
- 每会话首个 CPU 样本不采用；异常间隔标记保留；不把缺失 CPU 样本前后的间隔拼接。
- 工况看整个区间内已观测帧，包含停车/行驶转换的区间单列，不再用终点代表整段。
- 不借用未来帧；最多允许 2.5 秒帧间隔。预热、校时差超过 250 ms、缺帧和逆序都不进入干净工况表。
- 同会话冲突副本排除并报告；来源 ZIP 有 SHA-256。按实际 APK 分组，不把 D-057 和原 v28 混合平均。

| 干净工况加权 CPU（100% 约一个核心） | 原 v28 `020a0d9f…` | D-057 `b8cfe5d5…` |
|---|---:|---:|
| 行驶 | 72.25%，910 区间/4635.9 秒 | 88.15%，78 区间/397.6 秒 |
| 停车且发动机停机代理 | 52.12%，121 区间/616.3 秒 | 15.99%，98 区间/500.5 秒 |

结论仍是 D-057 静止省资源、动态退步。这是不同会话的观测证据，不是控制实验；“发动机停机代理”不是车辆熄火识别。原日志 `render_ms` 不覆盖异步 `View.onDraw`，总 `request_hz` 也不是每个信号的 Hz。

## 4. 实际测试结果

| 检查 | 本轮结果 |
|---|---|
| 产品及 debug/test Kotlin 编译 | PASS |
| 标准 `testDebugUnitTest` | 宿主子进程故障：GradleWorkerMain / closed pipe，未声称 PASS |
| 同一份编译产物直接 JUnit | **120/120 PASS**，包含全部既有测试和 8 个回放测试 |
| 统计分析 Python 测试 | **8/8 PASS** |
| 回放导出 Python 测试 | **3/3 PASS** |
| `lintDebug` | **0 errors / 9 existing warnings** |
| `assembleDebug` | PASS |
| v2 签名及包版本 | PASS |
| 真实 863 帧完整解析及 SHA 固定 | PASS |
| 原 UI 资产对照 R1 APK | 19 项逐项内容哈希相同 |
| 源码 whitespace 检查 | PASS |
| API 26/27 GUI、动态 CPU、帧率、视觉对照 | **未执行：模拟器加速环境阻碍** |
| 采集脚本端到端 | **未执行**；CLI 可加载，不伪装成运行验证 |

原 R1 renderer、model、model tests 的 SHA-256 仍分别为：

```text
88e04f38b997ea5f82a2d613c1f5ca0d1039b00e90da1b345049927e9d9e2a21
6876fabed17cc4964d22228e4d13f6de2627ac8dbb5f4c0b172b8307c67c219c
978ded08b29634684b72ea08c313dc4616dc65cdb3e17ea0f8c6437f9339dcb5
```

证据输出：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\outputs\d058-dynamic-validation`。

## 5. 修改/新增文件

仓库内（路径相对上述源码目录）：

- `app/src/debug/AndroidManifest.xml`：仅注册调试 Activity。
- `app/src/debug/java/com/guanyu/rx400hprobe/DashboardReplay.kt`：有界时间轴、记录解析、合成压力输入。
- `app/src/debug/java/com/guanyu/rx400hprobe/DashboardReplayActivity.kt`：真实 View、生命周期停止、窗口统计。
- `app/src/debug/assets/renderer_replay/recorded.csv`、`recorded.json`：白名单显示数据和来源校验。
- `app/src/testDebug/java/com/guanyu/rx400hprobe/DashboardReplayTest.kt`。
- `tools/export_dashboard_replay.py`、`test_export_dashboard_replay.py`、`capture_renderer_replay.py`。
- `tools/debug-test-classpath.init.gradle`、`run-debug-junit.ps1`：导出 Gradle 精确编译 classpath 后的显式宿主绕行工具。
- `PROJECT_STATE.md`、`DECISIONS.md`、`CHANGELOG.md`、`ROADMAP.md`、`EVIDENCE_INDEX.md`、`BASELINE_README.md`、本文。

工作区仓库外：`work/analyze_emulator_calibration.py`、`work/test_emulator_calibration.py`；新增统计输出、验证日志、APK。旧统计输出保留，没有覆盖。

## 6. APK 身份（本地验证用，不要求上车安装）

```text
位置：C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\deliverables\RX400h-Monitor-v0.3.5-v28-D058-dynamic-validation-debug.apk
长度：2962833 bytes
SHA-256：b388efed7e3a09153d8b2c2428d130ea07b963e0e14d01ced65b0193220e68b7
package：com.guanyu.rx400hprobe.debug
version：0.3.5-debug / 28
minSdk：26
v2 certificate SHA-256：77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
```

它使用同一 R1 产品 renderer，但包含额外 debug 测试入口和回放资产，因此 APK 身份不同，不能冒称旧 `33021cb6…`。原包未覆盖。调试 Activity 不进入 release source set；本轮未要求 release 构建。

## 7. 运行环境恢复后的顺序

1. 先确认实际模拟器能启动，再固定 API、1280×720、CPU/RAM、渲染后端和 CRT 设置。不得把“核数/RAM 相同”叫做算力相同。
2. 经授权把上述 APK 安装到模拟器。采集脚本不会安装 APK 或启动模拟器。
3. 从仓库目录调用工具，例如（替换为真实 emulator 序号；每次使用新输出目录）：

```text
python tools/capture_renderer_replay.py --serial emulator-5560 --mode stress5 --output ../../outputs/d058-run-stress5-01
```

4. 脚本拒绝实体设备序号，只接受已启动且 `ro.kernel.qemu=1` 的模拟器。记录包哈希/分辨率/密度/API/已有 CRT 偏好；不改偏好，不清空日志。输出截图、专用 logcat、gfxinfo、meminfo；缺少本次唯一 runId 的 complete 事件就失败。
5. 四场景分别运行；丢掉最初 10 秒自检/采样准备窗口。固定条件至少重复三轮，观察窗口分布，而非只选最好一轮。
6. 查看截图、自检轨迹、功率单游标、过零、Idle 10%/100% 外观；设置打开/关闭仍需专门 GUI 操作。时间轴/数值测试不能替代这些视觉检查。
7. 比较重建率×单次成本、实际交付/跳过、窗口帧数、内存和真实 HWUI 数据。掉数据/减少动画不能被当作优化成功。历史旧 APK 没有本次回放入口，不能把其静态预览当成相同动态 A/B；要做旧管线 A/B，需可信旧源码及同一输入入口。
8. 本地动态验证后再决定是否需要 R2。最终目标车机只保留必要短验证；真实 OBD/蓝牙的 5 Hz 能力仍需单独链路证据，不能由 UI 合成输入证明。

以上“下一步涉及系统加速环境”属于 2026-09-05 初始记录，后续授权、重启和续跑见本文顶部。不要据此重复开启 Windows 组件。
