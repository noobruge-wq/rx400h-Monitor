# RX400h Monitor V0.3.5/v28 D-057 — 外部 AI 代码审查包

生成日期：2026-09-04  
用途：交给另一位 AI / 高级工程师进行只读代码级审查，并提出不改变既定 UI 的性能优化建议  
当前结论：D-057 停车时显著省 CPU，但行驶时出现明确性能回归，不能作为 HA 高速调度器的合格基础

---

## 0. 给审查者的直接任务

请先审查实际源码和证据，不要只依据本文结论，也不要在未经项目所有者授权时修改、提交、推送或发布任何内容。

请重点回答：

1. D-057 行驶时 CPU 从旧版约 72% 升到 89.1% 的主要原因是否确为全屏软件位图重新合成？请给出源码行级证据。
2. 在 Android 8.1 / API 27 弱车机上，如何保留当前精确像素外观，同时避免动态信号变化触发全屏 CPU 合成？
3. “静态底图缓存 + 小型动态图层直接绘制到硬件 Canvas”是否是最佳方案？如不是，请给出更小、更安全的替代方案。
4. 请至少比较两种可行架构的 CPU、GPU、内存、API 27 兼容性、视觉一致性和实现风险。
5. 请审查扫描线、八方向辉光、车轮、数值字形、SOC、HV 功率条、Idle Check、状态区和按钮各自应属于静态层还是动态层。
6. 请确认怎样避免 steady-state allocation、Bitmap 上传抖动、旧像素残留、扫描线相位变化以及设置浮层遮挡错误。
7. 请提出最小修改计划、测试计划和明确停手条件，不要顺便重构 Vehicle Core。
8. 请单独复核本文末尾的 Idle Check 防抖建议；不要把它与 UI 性能修改混成一个不可审查的大改动。

期望输出格式：

- 先列 Findings，按 P0/P1/P2/P3 严重度排序，并给出文件及行号。
- 再给根因判断、候选方案对比、推荐方案、最小补丁步骤、验证方法和剩余风险。
- 若认为现有证据不足，请明确指出还缺哪一种数据，以及它会改变什么判断。

---

## 1. 本地仓库与源码位置

Repository：`noobruge-wq/rx400h-Monitor`  
本地根目录：

```text
C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\rx400h-Monitor-crt
```

Git 状态：

```text
branch: v0.3.0
HEAD: 8f1a3b9fb49359484c283ea6c3eaf69c631b4b45
tracking: origin/v0.3.0
local commits: ahead 4
working tree: dirty
```

重要警告：`PixelDashboardView.kt`、`PixelDashboardModel.kt`、`PixelDashboardModelTest.kt` 和 `app/src/main/assets/rx400h_ui/` 当前仍是 **untracked**。普通 `git diff` 不会显示这些文件，必须直接读取实际文件并核对本文给出的哈希。不要因为 `git diff` 没有内容就误判 D-057 不存在。

### 1.1 D-057 最主要生产源码

```text
C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\rx400h-Monitor-crt\app\src\main\java\com\guanyu\rx400hprobe\PixelDashboardView.kt
```

SHA-256：

```text
71f09717ac8181ca1337e646f3ff52bcda31c24b6dabcbd554247717483b5063
```

关键位置：

```text
55-60     cachedFrameBitmap / cachedFrameCanvas
142-198   render()、可见字符串变化判断、transition target 更新
240-256   onDraw()：缓存整页、车轮、扫描线的最终绘制顺序
342-356   rebuildCachedFrame()：当前性能审查核心
500-546   drawWheelOnCanvas()：独立车轮与局部八方向辉光
653-676   单次 repeating BitmapShader 扫描线
679-755   updateSlotTexts()：格式化结果相同则不让整页变脏
757-813   SOC / power / wheel no-op transition suppression
815-856   动画推进
858-869   20 fps transition / 10 fps wheel frame scheduling
```

### 1.2 相关 presentation 源码

```text
app/src/main/java/com/guanyu/rx400hprobe/PixelDashboardModel.kt
app/src/main/java/com/guanyu/rx400hprobe/DashboardUi.kt
app/src/main/java/com/guanyu/rx400hprobe/MainActivity.kt
app/src/main/assets/rx400h_ui/layout_v035_pixel_v2.json
app/src/main/assets/rx400h_ui/*.png
app/src/test/java/com/guanyu/rx400hprobe/PixelDashboardModelTest.kt
```

`PixelDashboardModel.kt` SHA-256：

```text
22e5b16af0cd79edfb709b70fc7aef4e7f0adb990d88251b8fe7f0196454ee81
```

### 1.3 Idle Check 源码（独立审查项）

```text
app/src/main/java/com/guanyu/rx400hprobe/IdleCheckState.kt
```

当前条件位于约第 16-42 行：

```text
activation stability: 1000 ms
rpm: strict 900 < RPM < 1100
speed: <= 55 km/h
ICE mechanical power: abs(power) <= 0.05 kW
warmup_active: must be true
exit behavior: any single out-of-range update immediately deactivates
```

---

## 2. 必读项目文档

请按以下顺序读取：

```text
AGENTS.md
PROJECT_STATE.md
DECISIONS.md
ROADMAP.md
CHANGELOG.md
CODE_REVIEW_V0.3.5_V28.md
CODE_REVIEW_V0.3.5_V28_D057.md
```

其中 `CODE_REVIEW_V0.3.5_V28_D057.md` 记录的是虚拟机结果。其“可以继续目标车机确认”的结论已被 2026-09-03 实车日志部分推翻；虚拟机数据仍可用于静态画面前后对比，但不能代表连续动态数值下的车机性能。本文和最新实车 ZIP 是更新的证据。

---

## 3. 产品目标与当前真实目标

### 3.1 长期产品目标

- 保留 RX400h 车辆专用白名单协议和现有真实信号。
- 将动力、转速、高压电池功率、速度、SOC 等高价值动态信号提升到尽可能高的有效刷新率。
- 慢信号允许低频穿插，不与快信号争夺链路。
- 后续计划实现 HA 思路的高速调度策略，并以 5 Hz 快信号为第一阶段目标。

### 3.2 当前阻塞目标

在推进 HA 高速调度器前，先把 v28 像素 UI 的行驶 CPU 压到可接受范围：

```text
目标车机 / API 27 / 1280×720
行驶状态 process CPU：必须低于原始 v28 的约 72%，建议验收目标 < 55%
停车稳定状态 process CPU：保持 <= 20%
内存：无随会话时间持续增长
视觉：与用户确认的 v28 精确像素布局一致
调度：不得新增 deadline miss、late、expired 或 transport failure
```

只有满足上述 gate，才继续 HA 复刻高速调度器。

---

## 4. 冻结范围

本次优化只能修改 presentation / renderer。禁止为了优化 UI 改动：

```text
DeadlineScheduler
request table
七个 polling periods
protocol whitelist
Bluetooth / ELM transport
decoder formulas
SignalStore semantics
logger / evidence semantics
session ownership / recovery
三按钮业务流程
Idle Check eligibility（除非作为另一项单独批准的补丁）
```

不得改变：

- 用户最终 JSON 的坐标、尺寸、z-order、alpha 和字体大小。
- 640×360 逻辑像素到 1280×720 的最近邻 2× 放大。
- Noto Sans SC 物理像素中文。
- SOC 非线性范围映射与自检动画。
- HV 功率 `-50…0…50` 自检、填充与单游标语义。
- 车轮由新鲜速度驱动、方向中性、最高 10 fps。
- Idle Check 未激活 10%、激活 100% 的外观。
- 扫描线/辉光设置值及本地持久化语义。
- 纯黑背景；不加入噪声、颗粒、暗角、WebView、Compose、实时 blur 或重型 shader framework。

---

## 5. D-057 当前实现意图

D-057 原本要解决 v28 每个车轮帧都重建整页的问题：

1. 额外建立一个固定 1280×720 `cachedFrameBitmap`。
2. 非车轮页面只有 `contentDirty` 时才重新绘制。
3. 车轮从整页中分离，每 100 ms 单独选择已有 rotation frame 并绘制局部辉光。
4. 扫描线由逐条矩形改成一个很小的 repeating `BitmapShader` pattern。
5. 文本格式结果、SOC/HV power/wheel 目标不变时，不重复令页面变脏或重启 300 ms 过渡。

虚拟机静态预览结果很好，但当前 `rebuildCachedFrame()` 会在一个 Bitmap-backed software Canvas 上执行：

```text
cachedFrameCanvas.drawColor(Color.BLACK)
drawHalo(cachedFrameCanvas, sharpBitmap)  // 八次接近全屏的 bitmap copy
cachedFrameCanvas.drawBitmap(sharpBitmap, ...)
```

这条路径在数值频繁变化时可能把原本可由硬件 Canvas / RenderThread 处理的工作转成 CPU 位图内存搬运。请审查者验证该判断，不要直接把它当作既定事实。

---

## 6. 构建物与虚拟机证据

最终 D-057 APK：

```text
C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\deliverables\RX400h-Monitor-v0.3.5-v28-D057-renderer-debug.apk
```

```text
size: 2,656,023 bytes
SHA-256: b8cfe5d5497e13eb30a8eb4d02695ae2eed9de50a91bac265cd74af2cc28bbab
package: com.guanyu.rx400hprobe.debug
versionCode: 28
versionName: 0.3.5-debug
APK Signature Scheme v2: verified
certificate SHA-256: 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
```

虚拟机证据目录：

```text
C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\outputs\v28-gui-retry\isolated-avd-home\artifacts
```

关键文件：

```text
v28-debug.apk
v28-first.png
v28-steady.png
v28-d057-debug.apk
v28-d057-first.png
v28-d057-steady.png
v28-d057-settings.png
v28-d057-gfxinfo.txt
v28-d057-meminfo.txt
v28-d057-top.txt
```

同一 API 26 / 1280×720 / SwiftShader AVD 的 12 秒静态预览：

| 指标 | 原始 v28 | D-057 |
|---|---:|---:|
| janky frames | 103/103 | 59/104 |
| median frame time | 40 ms | 20 ms |
| slow bitmap uploads | 9 | 0 |

该结果只证明静态内容 + 车轮场景改善，不能代替真实行驶中的动态字段变化。

---

## 7. 最新目标车机实测证据

实车 ZIP：

```text
C:\Users\冠儒\Downloads\RX400h Monitor log 2026-09-03 19-48-25.zip
SHA-256: aae478739262519ae26ad711b87805f08efaba23c5ab4da4e3a9f54938c79f25
```

设备/会话：

```text
Android 8.1 / API 27
sprd sp7731e_1h10_native
1280×720 / density 1 / landscape
OBDLink MX+ 99905
session: RX400h_20260903_073219_764
LIVE duration: about 947.5 s
termination: USER_END
evidence_complete: true
errors: 0
manifest file hash mismatches: 0/13
```

ZIP 内 `manifest.json` 和 `session.json` 均绑定到 D-057 精确 APK SHA-256 `b8cfe5d5…28bbab`。

### 7.1 Process CPU 的正确分场景比较

CPU 数据来自 `PerformanceTracker` 的 `Process.getElapsedCpuTime()`，每约 5 秒记录一次。比例按 `cpu_delta_ms / wall_interval_ms` 计算，约等于占用一个 CPU core 的百分比。

原始 v28 APK `020a0d9f…d30f6` 的五份 2026-09-01/02 记录：

| 记录 | 整段 CPU | 行驶 CPU | 停车/ICE off CPU |
|---|---:|---:|---:|
| 09-01 17-31-01 | 67.59% | 71.94% | 51.48% |
| 09-02 15-08-18 | 68.60% | 72.94% | 56.81% |
| 09-02 16-47-57 | 69.98% | 71.83% | 样本不足 |
| 09-02 18-37-22 | 69.04% | 72.31% | 55.87% |
| 09-02 19-45-44 | 71.70% | 72.35% | 56.25% |

D-057 精确 APK `b8cfe5d5…28bbab`：

| 场景 | 时间 | weighted CPU | sample median | sample p90 |
|---|---:|---:|---:|---:|
| 全部 | 944.2 s | 49.79% | 26.82% | 95.20% |
| 行驶（speed > 1 km/h） | 428.2 s | **89.10%** | 90.89% | 96.41% |
| 停车/ICE off | 516.0 s | **17.17%** | 12.07% | 24.68% |

整段 49.79% 是被后半段 516 秒停车拉低，不能称为“行驶性能改善”。正确结论是：

- 停车稳定状态从旧版约 51–57% 降至 17.17%，no-op suppression / cache 有效。
- 行驶状态从旧版约 72% 升至 89.10%，主要目标失败。
- 当前候选不可直接作为 5 Hz 高速调度器的性能基础。

内存：

```text
PSS first: 35,342 KB
PSS median: 35,211 KB
PSS max: 37,676 KB
PSS last: 29,413 KB
```

没有持续上升趋势；额外完整页 Bitmap 的固定内存成本可见但不是本次失败主因。

### 7.2 Scheduler/transport 没有被 UI 改坏

```text
scheduler profile: v030_capacity_002
run mode: NORMAL（不是 HA 高速模式）
request utilization: 0.73395
projected utilization: 0.906533
average request rate: about 5.02 requests/s
average decoded signal updates: about 10.62/s
errors/no-data/timeout/bus-error: 0
deadline miss: 0
executed late: 0
expired: 0
transport unavailable: 0
```

Scheduler conservation：

```text
4771 releases = 4758 on-time + 11 capacity-rejected + 2 session-ended
capacity rejection rate = 0.2306%
```

11 次容量舍弃集中在两个周期碰撞窗口，发生时 process CPU 约 11–25%，没有证据表明它们由 UI 高 CPU 直接触发。它们证明当前 90.65% projected utilization 已接近容量边界，也说明不能简单缩短现有 periods 来实现 5 Hz。

---

## 8. 推荐审查的优化方向

以下不是授权实现，只是要求审查者评估。

### 方案 A：静态整页 + 小型动态图层（当前优先建议）

只在初始化或 CRT/控制/状态发生低频变化时生成静态底图。每个 UI frame：

1. 硬件 Canvas 绘制一次静态底图。
2. 动态数值、SOC fill、HV-power fill/cursor、Idle Check、车轮、状态文字分别作为小矩形图层绘制。
3. 动态辉光仅围绕对应小图层做八方向偏移，不在 Bitmap-backed Canvas 上重合成整页。
4. 扫描线仍保持最终 physical-output pass 和原相位。

需要审查：动态图层如何覆盖旧值、如何保留底部车身/刻度线、如何避免透明区域把底图变黑、设置浮层打开时如何冻结或遮挡车轮。

### 方案 B：静态时启用完整页缓存，动态时回退旧 GPU 路径

动态值持续变化时使用旧版的 hardware Canvas 路径；连续一段时间没有可见变化后才建立完整页缓存。这能保留停车收益并避免行驶时软件重合成回归，但可能只能回到旧版约 72%，未必达到 `<55%` 目标。

需要审查：稳定判定时间、缓存建立抖动、额外调度器 callback，以及是否值得作为安全的中间修复。

### 明确否决/谨慎项

- 不得再次直接依赖 `invalidate(left, top, right, bottom)`。API 26 实验出现未刷新区域丢失，最终截图只剩局部图层；该实验已经从源码删除。
- 不使用 RenderEffect、实时 blur、Compose、WebView、动态 shader framework 或新大型依赖。
- 不通过降低信号刷新率掩盖 UI 性能问题。
- 不改变用户像素坐标或减少扫描线/辉光默认强度来伪造优化结果。

---

## 9. Idle Check 最新实车证据（独立问题）

2026-09-03 会话记录三段 active：

```text
19:39:41.801 ACTIVE
19:39:44.804 INACTIVE

19:40:05.823 ACTIVE
19:40:09.944 INACTIVE
19:40:11.833 ACTIVE
19:40:14.817 INACTIVE
```

确认的闪烁窗口：

```text
19:40:09.257  rpm=914.00, warmup=true, icePower=0, active=true
19:40:10.266  rpm=889.75, warmup=true, icePower=0, active=false
19:40:11.549  rpm=907.75, warmup=true, icePower=0, active=false
19:40:12.740  rpm=905.25, warmup=true, icePower=0, active=true
19:40:14.854  rpm=911.50, warmup=false, active=false（正确最终退出）
```

这证明严格 `RPM > 900` 导致约 1.9 秒的暗灭后重亮。

建议审查的两级条件：

```text
进入仍严格：warmup=true, 900<RPM<1100, abs(ICE power)<=0.05kW, speed<=55, stable 1s
保持可放宽：warmup=true, 850<RPM<1200, abs(ICE power)<=0.30kW
warmup=false：立即退出
其他保持条件越界：可考虑持续约1s后退出
```

该方案仍只有 active/inactive 两种可见状态；“保持区间/退出防抖”不是新增第三种 UI 状态。请审查是否会造成错误延长、是否需要 power hysteresis，以及应增加哪些 replay unit tests。

---

## 10. 必须运行的验证

如后续获准修改，最低验证集：

```text
compile production/test Kotlin
111+ direct JUnit（新增 renderer policy / Idle replay 后数量应增加）
lintDebug
assembleDebug
manifest/version/package/signature/hash verification
API 26/1280×720 exact visual smoke
settings overlay smoke
Start self-test / SOC / HV-power cursor / wheel / Idle 10%-100% smoke
目标 API 27/1280×720 行驶 + 停车同条件 performance.csv
```

性能测试必须按车辆状态分组，禁止只报告整段平均值。至少报告：

```text
moving speed > 1 km/h weighted process CPU
stopped / ICE off weighted process CPU
sample p50 / p90
PSS first / median / max / last
request effective Hz
deadline miss / late / capacity reject / expired / transport unavailable
```

---

## 11. 成功定义

本轮真正成功不是“虚拟机截图看起来更流畅”，而是：

> 在不改变用户确认的 v28 像素布局、CRT效果、信号语义、协议和调度周期的前提下，让目标 API 27 车机在真实行驶、动态数值持续变化时的 process CPU 明显低于原始 v28 的约72%，建议达到55%以下；停车稳定状态保持20%以内，同时不新增任何调度截止期或链路错误。

在这条 gate 关闭前，不建议推进 HA 复刻或 5 Hz 策略。
