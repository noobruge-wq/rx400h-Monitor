# 项目暂时结束 / 换电脑恢复入口

日期：2026-09-26。用户授权把本地工程上传既有GitHub仓库；本轮只归档，不开发新功能，不宣布实车验收完成。

## 当前有效工程

- 仓库：`https://github.com/noobruge-wq/rx400h-Monitor`
- **取 `v0.3.0` 分支，不是旧的默认 `main`。** 本轮不合并/覆盖主分支，不强推、不删除历史。
- 最新产品源码：V0.3.5 / versionCode29 / D064-realtime-3hz。
- 只保留实时仪表和本机设置；HA顺序约3Hz，自动连接一次，车图手动重连，后台采集、返回退出；没有行车日志/性能统计/保存恢复/稳健策略。
- 本轮应用源码、资源和构建配置与9月23日D064交付逐文件核对一致。只增加归档说明、忽略规则、原目录外的工具副本。
- D064已有本地回归/构建/模拟器结果见 `D064_LOCAL_REVIEW_20260923.md`；真实蓝牙、音乐共存、睡眠唤醒和实际3Hz仍未证明。项目暂停，不将这些标记为已通过。
- 本地原来的4条未推送提交会随当前分支历史保存；D055–D064未提交工作以本轮归档提交保存，不伪造各中间版本的提交历史。

## 这次源码归档包含

1. `app/`：正式源码、debug验证入口、全部单元测试、回放样本、用户定稿布局/字体/图片及字体许可证。
2. 根目录工程文档、审查报告、Gradle配置、`.github/workflows/build-apk.yml`。
3. `tools/`：构建辅助、回放/生成/验收/导出脚本。
4. `tools/legacy_workspace/`：原来放在项目旁边的分析和验证脚本，按原字节归档。**历史脚本不保证迁移后直接可运行**；使用前调整旧workspace、SDK、模拟器和输入文件路径，旧相对目录结构见该目录README。
5. `design/editors/`：浏览器编辑器、模板和历史编辑辅助脚本。不代表编辑器自带示例是最终UI；正式布局以 `app/src/main/assets/rx400h_ui/layout_v035_pixel_v2.json` 为准，打开编辑器后导入它。

## 这次没有上传

- `deliverables/`中的历史APK、旧版本精确源码ZIP、对应R8mapping；尤其D063和D064整套交付目录应另备份。
- `outputs/`中的历史截图、构建输出、日志回放语料、模拟器测试证据。
- 下载目录和微信目录里的原始行车ZIP、原始设计图、外部审查资料；工程里只有明确用于测试的字段回放样本，不是原始整份行车日志。
- Gradle/SDK/JDK/AVD缓存、`local.properties`、账号凭据、个人SSH/API密钥。

**源码上传成功不等于整台旧电脑的数据都已备份。卖掉/清空电脑前，还要把上述需要保留的内容复制到另一台设备、移动硬盘或自己的私有云盘。仅放在本机另一个目录不算完成迁移。**

### 固定测试签名的例外说明

`.github/signing/rx400h-debug.keystore`是仓库历史上已经公开的项目测试签名，本轮核对本地与远端blob相同，不新增、不替换、不删除。它不是生产密钥；不要用于其他应用或正式商业签名。本轮不会新增个人私钥或账户令牌。

## 在新电脑恢复

```text
git clone --branch v0.3.0 https://github.com/noobruge-wq/rx400h-Monitor.git
cd rx400h-Monitor
```

先读本文件和D064报告，再读PROJECT_STATE/CODEX_HANDOFF。旧文档里5Hz、记录/恢复、三按钮等是历史政策，不要据此复原已取消功能。

构建环境沿用现有配置：JDK17、Gradle8.9、Android SDK Platform35，minSdk26。仓库目前**没有Gradle wrapper**；须本机安装Gradle8.9，或使用仓库已配置的GitHub Actions工作流。不要把旧机器绝对路径写进新机器的配置；设置JAVA_HOME、ANDROID_HOME或本机local.properties。

```text
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintBenchmark :app:assembleDebug :app:assembleBenchmark
```

日常候选为 `app/build/outputs/apk/benchmark/app-benchmark.apk`，不是含测试活动的debug对照包。新构建会嵌入新Git提交身份，哈希不必与旧dirty APK相同；需要原9月23日APK及其R8mapping时，必须用另行备份的原交付目录。

若复现旧Windows Gradle测试worker启动故障，可参考 `tools/debug-test-classpath.init.gradle` 与 `tools/run-debug-junit.ps1` 的独立运行方式，标准任务失败和直接运行通过必须分别报告。完整历史语料回归依赖未上传的原数据；缺少语料时不能复述41,143条的原验收结论为新机器已重跑。

此分支的push不会自动触发当前仅监听main/master的APK构建。本次以Git提交/远端树/重新clone逐文件核验作为源码归档验收，不额外发布APK、创建Release/PR或改变仓库默认分支。

`BASELINE_MANIFEST.sha256`自本次归档起校验Git保存的标准文本字节，而不是可能被Windows转换为CRLF的工作目录字节。克隆后可运行 `python tools/verify_archive_manifest.py` 校验文档；源码全树身份由提交的Git tree标识。D064原交付目录的manifest仍按其原始快照字节解释，未覆盖旧证据。

## 后续恢复开发的边界

- 先保留原用户布局和车辆协议白名单，不擅自扩展车型/调度档位/CAN实验。
- 本次没有修改应用行为，旧APK测试结果只对应报告中的确切制品。
- 因产品已取消日志，不再要求此版本导出行车ZIP。需要新的采集/诊断能力时重新征求用户授权。
- 销售电脑的安全擦除、账号注销和其他项目备份不属于本轮操作；本轮不删除电脑上的任何工程或原始数据。
