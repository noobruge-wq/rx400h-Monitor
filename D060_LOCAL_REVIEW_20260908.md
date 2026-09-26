# D-060 本地实施与验收报告

日期：2026-09-08。结论：九组需求的产品代码已分批落地；本地功能、像素一致性与两种资源配置对照已有证据。**不是九组验收全部关闭，更不是实车通过或正式发布。** 具体未完成项列于第6节。

## 1. 源码与候选身份

- 源码根目录：`C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\rx400h-Monitor-crt`。
- 分支 `v0.3.0`；HEAD `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45`，未提交工作区。原有 D-055…D-058 修改仍在，不得把整个 git diff 都当作本轮新增。
- 本地候选：`../../deliverables/D060-local-20260908/RX400h-Monitor-v0.3.5-v28-D060-local-debug.apk`。
- SHA-256：`db6b33fa2038ae6f9711fc87e0d83907d99fb09d42d1d3b8f156f51965811297`；9,997,565 bytes；`0.3.5-debug` / versionCode 28；未改版本号。
- 包：`com.guanyu.rx400hprobe.debug`；原项目固定 v2 调试签名，证书 SHA-256 `77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192`。
- 完整当前文件哈希（含未跟踪源码/资源）及 tracked-only diff：同交付目录的 `manifest.json`、`source.sha256`、`tracked-only.diff`。历史 `BASELINE_MANIFEST.sha256` 不覆盖。
- 未 commit、push、PR、发布、操作车辆。只安装于明确核实的模拟器。

## 2. 本轮功能改动

### 调度与真实刷新

设置只保留最后确认的两档：稳健、HA复刻，默认HA复刻。复刻对四个核心请求设置200ms目标，顺序为 `std_core → cd_f3 → c3 → c4`；慢请求在头部分组边界穿插，防止慢请求持续到期饿死核心。链路慢时仍保持该策略，记录实际执行、迟到、到期舍弃，不悄悄切档。25ms虚拟事务+5ms换头、含慢请求的60秒测试，四核心均达到至少299次；220ms慢事务测试检验顺序和计数守恒。**这不是实车5Hz证明。**

确认切换后复用单一会话线程自动End/保存/释放旧连接，再启动新策略会话；保存失败按授权丢弃旧未保存内容，并在新会话带入旧会话ID、请求/删除时间、原因、结果。设置在空闲时只影响下一次Start。UI仅提供当前授权的两个选项，没有自研或额外实验档。

真实更新完成后立即合并发布到主线程；500ms心跳只负责年龄/状态检查。新增 `dashboard_snapshot_hz`（performance.csv末列），与每请求实际Hz、窗口动画Hz分开。日志写入不再占据信号锁。

### 旧会话、失效与Idle Check

启动不进入旧记录恢复流程；活动写入者受进程租约保护。只删除应用私有、未发布的旧会话；已保存回执、公共ZIP、电脑原始日志均保护。目录边界和符号链接检查失败时拒绝危险删除，但不恢复旧内容或永久封锁新Start。丢弃回执最多64条，额外条数记摘要；回执写入失败会保留，不能把丢失记录冒称完整。

末轮发现：旧writer flush/close失败仍可阻止首次新Start。已用真实已关闭文件流复现，再修复为“记录失败、明确关闭底层流、放弃旧缓冲、允许新会话”；正常保存仍报告错误。测试只永久删除自行生成的模拟器隔离文件，**没有删除用户电脑或车辆日志**。

结束/断线/请求失败/样本过期使显示失效，保留固定格式占位，停止旧车速轮辐动画；不让自检缓存重新点亮失效数据。快信号上限5秒、电池温度12秒；RPM/扭矩来源时差限制800ms，公式不变。

Idle Check：warmup=true、900<RPM<1100、|引擎功率|≤0.05kW、速度≤55km/h，新的功率样本连续约1秒进入。保持范围850<RPM<1200、|功率|≤0.30kW、速度≤55；数值越界持续1秒退出，warmup关闭/必要值无效立即退出。重复样本不推进计时，记录退出理由和压住的短越界次数；没有用户可见中间态。界面遵循最终约定的未激活10%、激活100%。

### 精确UI与性能

原作者JSON及车图没有重排、重画。布局SHA-256仍为 `1f3914510943261f3c2d29f4d8eaf4abca7236b5676e0584948c2839b63518aa`。动力条位置从原JSON/遮罩推导，静止轮辐40%，中文动态文本采用Noto Sans SC静态衍生字体。设置几何/英文数字走逻辑像素栅格，中文保留此前约定的物理字号。新会话编号重置时，自检识别首次有效数据，不误用旧fresh样本。

选择缓存软件合成：保留原720p辉光的像素运算，只重算变化的小位图分区，最终仍绘制完整页面。不是依赖屏幕残留的局部invalidate。没有每帧新建Bitmap/Paint/Path，没有新增UI框架、WebView、Compose、blur/shader。

Noto动态字体为本地生成的10,373,024-byte静态字体，包含30,890个Unicode映射，OFL及来源说明在fonts目录；FontTools仅是电脑上的生成工具，不进入APK。完整中文覆盖增加安装包和字体内存成本；不得把约47MB回放PSS说成与旧约30MB一样。尚未进行长时间泄漏证明。

## 3. 修改文件与边界

产品路径均在 `app/src/main/java/com/guanyu/rx400hprobe/`：

| 文件 | 本轮职责 |
|---|---|
| SamplingStrategy.kt、CapacityAwareScheduler.kt | 两策略、HA顺序和200ms目标、无自动降级 |
| MainActivity.kt、MonitorSessionState.kt | 自动切换交接、取消启动恢复入口、真实发布、失效调用链 |
| ProbeLogger.kt、OwnedSessionFiles.kt | 安全丢弃、回执、租约与关闭故障、策略/发布频率元数据 |
| IdleCheckState.kt | 严入宽持、样本驱动计时、理由 |
| DashboardFreshness.kt、RequestSignalBindings.kt | 来源时效/配对、请求失败的准确归属 |
| PerformanceTracker.kt | 独立快照发布统计 |
| PixelDashboardView.kt、PixelDashboardModel.kt | 字体/浮窗/轮辐/自检、精确缓存合成 |
| BitmapDamage.kt、PowerBarGeometry.kt | 有界位图分区和作者布局驱动几何 |

另有 `assets/rx400h_ui/fonts/`字体/授权/来源、相应单测、debug Preview/Replay/SessionLifecycleSmoke及manifest、`tools/`生成/构建/捕获工具、工作区`work/capture_d060_*.py`、`work/summarize_d058_runtime.py`、本报告和状态/路线/决策/证据文档。

**确实修改了scheduler、会话/日志、派生Idle和presentation；不是UI-only换皮。** 白名单命令/响应、传输串行性、decoder公式、SignalStore语义未改。RequestTable的原稳健周期未改；HA使用明确授权的派生请求配置。原有DashboardUi/响应式/构建工作流等dirty差异属于之前工作，不据此冒领或回滚。

## 4. 已执行验证

证据根目录：`../../outputs/d060-validation-20260907/`。

- 最终direct JUnit 140/140，Python统计/校准18及回放导出3项通过。标准Gradle `testDebugUnitTest` 的worker曾失败，不记为通过；使用重建runtime jar和同一编译测试类的直接JUnit作为本机替代。
- `lintDebug`：0 errors / 8 warnings；`assembleDebug`、v2签名通过；日志`build-final.log`、`junit-final.log`、`signature-final.log`。
- `lifecycle-final`：7/7。涵盖未保存丢弃及回执、已保存保护、活动owner保护、ZIP别名失败后新Start、真实finalizer故障、嵌套软链接、旧writer关闭失败。最后一项用debug反射注入失败状态及真实已关闭FD，不是物理蓝牙测试。
- `lifecycle-close-red`为最初未成功注入目标故障的测试失败；不能当产品根因证据。`lifecycle-close-red-corrected`按自己的runId复现 `Cannot durably close failed session writers / Stream Closed`；修复后最终7项通过。日志可能包含旧run，必须按结果UUID识别。
- `gui-final`：1280×720 framebuffer / 1280×672应用View；设置打开/选择稳健/确认/重启持久化/恢复HA/关闭，原始PNG完全恢复。未按开始、不使用真实蓝牙。静态参考与缓存排除转动轮辐后903,582像素，差异0。
- `pixel-oracle-final`：40次输入变化、100帧完整720p离屏缓存，逐帧与完整软件合成 `Bitmap.sameAs` 全部通过，包括自检/过渡/失效/Idle切换。昂贵像素oracle仅显式debug测试启用，性能运行未启用。
- 普通diff不涵盖未跟踪代码，交付manifest补齐。Windows CRLF曾被临时关闭autocrlf的检查误判；按cr-at-eol正确复核通过，未为此重写旧文件。

## 5. 性能对照与弱配置边界

相同输入601帧/120秒，剔除覆盖前10秒和收尾的窗口，以单调时长加权；CPU100%约一个guest核心。WHPX/API26 x86_64/SwiftShader，固定1280×720和原CRT参数。每次清理本测试App进程而不清数据；来源与installed APK SHA均记录。

| 配置 / 同APK配对 | 完整软件参考CPU | 缓存CPU | 约相对降低 | 窗口Hz：参考 / 缓存 |
|---|---:|---:|---:|---:|
| 4核、2GB / 7abab51d | 19.28% | 15.02% | 22.1% | 19.33 / 19.02 |
| 1核、实际1.5GB / 7a41c3fc | 18.07% | 13.94% | 22.9% | 18.82 / 18.67 |

以上四组均601/601、零跳过；对应`stress5-tiles-*`和`stress5-final-1core-*`目录。不是旧D057原APK对新APK的完整车辆性能比较：参考保留D057软件合成算法，但使用同一当前字体/内容以隔离合成成本。最终db6b33fa只在7a41c3fc之后修复logger关闭及增加隔离测试；显示源码相同，另行进行最终精确APK回放，结果见runtime-summary。

最终db6b33fa单核复测：CPU13.89%、窗口18.87Hz、601/601输入、零跳过，输入最大迟到54ms；PSS48,082–49,358kB，稳态3帧总耗时超过50ms、FrameMetrics回调丢失0。不是60fps顺滑或零卡顿承诺，也不是内存不增长的长期证明。

直接HWUI的90a51e44虽测得更低CPU，但存在辉光采样差异，未作为最终方案；早期非fresh-process两组受Activity堆叠影响，也不进入主对照。

弱配置申请1024MB，被模拟器自动提升到1536MB；实际`online_cpus=0`、`MemTotal=1531004kB`，不得称“1GB车机同配”。减少核心数未使UI CPU接近实车旧D057完整程序的88.15%，说明仅改核心/内存不足以校准算力、图形、蓝牙和日志负载。现有21个有效会话、7个确切APK+1历史组、4636性能样本/395.8分钟的实车基准仍保留；**没有80%跨版本留出准确率，也没有车上5Hz结论。**

## 6. 未关闭项与下一步

1. 自动策略切换已有源代码/空闲GUI/真实文件交接验证；真实LIVE→End→保存失败分支→蓝牙重连→新LIVE还没有端到端证据。
2. 新Idle样本相干逻辑通过边界与早期自然激活片段，但最新原始事务全量重放尚未完成，不能承诺车上不再闪烁。800ms配对上限是当前工程边界，必须结合新增来源时间记录复核。
3. R9没有同一端到端工作负载的多版本模拟器留出集；单核压力只能说明资源敏感性。下一步应先补匹配工作负载和本地回放，而非调出一个好看的CPU百分比或要求专门长途。
4. 旧恢复入口已从Main移除，ProbeLogger中仍保留未调用的历史恢复/脱离辅助代码；完整死代码清理及长时内存检查未宣称完成。
5. 目标API27车机、真实协议/适配器吞吐、覆盖安装/驾驶可读性仍不是本次模拟器能验证的项目。手机竖屏、自研调度、CAN监听和删除整个log模块继续按已确认范围延期。

旧日志坏掉与新会话自身存储完全不可写是两种问题：本轮保证前者不再以恢复/旧flush失败阻碍新Start；尚未把日志系统改成全盘满时也能完全无日志运行的可选模块。

当前可用于继续本地审核和有限验证；不要直接把此候选升格为实车通过的正式基线。
