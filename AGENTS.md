# RX400h Monitor — AGENTS.md / Codex Repository Instructions

本文件是 Codex/自动化开发代理的最高层仓库工作规则。项目事实以 `PROJECT_STATE.md` 等基线文档为准；本文件规定“应该怎样工作”。

## 0. 启动纪律

任何新 Codex 会话在修改代码前必须按顺序读取：

1. `AGENTS.md`
2. `CODEX_HANDOFF.md`
3. `PROJECT_STATE.md`
4. `DECISIONS.md`
5. `ROADMAP.md`
6. `CHANGELOG.md`
7. `DEVELOPMENT_PROTOCOL.md`
8. `EVIDENCE_INDEX.md`
9. `GITHUB_BUILD_AND_BASELINE_WORKFLOW.md`
10. `REPO_ACCESS_AND_AUTH.md`
11. `.github/workflows/build-apk.yml`
12. `app/build.gradle.kts`
13. 当前 Kotlin 源码

然后先提交一份“恢复报告”，至少明确：

- 当前有效版本/commit；
- 哪些版本 VOID / rejected；
- 当前协议白名单；
- 当前 UI/Signal Contract；
- 当前里程碑与 Exit Gate；
- 当前技术债；
- 文档与源码是否存在不一致；
- GitHub 读/写和自动签名构建是否可用。

**第一次接手时不要立刻重构或提高轮询频率。恢复状态并检查一致性后，再等用户确认开发任务。**

## 1. 产品范围

RX400h Monitor 是 **Lexus RX400h 专用** Android 原生仪表/监控软件。

核心约束：

- RX400h-only；
- 离线；
- Kotlin + Android View；
- 弱 Android 车机友好；
- 低 CPU / 低 RAM / 低 GC；
- 高动态信号尽量取得高有效刷新率；
- 不做 Hybrid Assistant 换皮；
- 不做通用 Toyota 诊断平台；
- 不提供任意 CAN/ELM/PID/DID 输入；
- 协议变化必须由证据驱动。

## 2. 有效代码基线

- V0.1.8：有效的协议/实车证据基线。
- V0.1.10：历史实车验证 cleanup/runtime 基线。
- V0.2.0：当前已关闭 Reactive Core 基线。
- **V0.1.9：VOID，绝不可作为后续代码基线。**

## 3. 当前冻结车辆请求白名单

```text
7E0 -> 7E8
01040C0D0E10 2
01050607 1
21CDF3 3

7E2 -> 7EA
21C3 6
21C4 5
21CF 4

ATRV
```

无新的直接证据和 `DECISIONS.md` 记录时，不得重新引入：

```text
22xxxx
2C
10 02
10 03
7E1 / 7E3 / 7E4 scanning
arbitrary CAN/ELM command input
```

## 4. Lean Core 规则

本项目故意重造一个更轻的 RX400h 专用轮子。

优先：

- 固定 typed structures；
- 固定 request table；
- 单写者/简单状态；
- 小而明确的 parser；
- 有界缓冲；
- 磁盘流式历史；
- 少依赖；
- 少 allocation；
- 少重复缓存；
- 明确 consumer traceability。

避免：

- `Map<String, Any>` 信号总线；
- 通用车型插件系统；
- 大型 DI / reactive middleware；
- 为“未来也许用”保留无消费者字段；
- hot path 上 regex/split/substrings/临时 List；
- session 时长越长 RAM 越大的结构。

每个大版本收尾前执行：

```text
Dead-code audit
Consumer audit
Allocation/hot-path audit
Dependency audit
Duplicate-state/cache audit
```

## 5. 高刷新率是正式目标

HA HCI 已证明：

- 裸核心循环中位约 0.159 s ≈ 6.28 Hz；
- 低频任务穿插后核心有效约 5.5 Hz。

这只是已知工作点，不是 ECU 上限。

V0.3.0 应通过受控阶梯测试寻找 RX400h + OBDLink 的实际 latency/error/CPU knee point，可在稳定情况下探索 6 Hz 以上。

必须区分：

```text
acquisition rate
signal-change rate
UI publish rate
renderer / animation FPS
```

不能把它们混成一个“刷新率”。

## 6. 测试成本纪律

不以昂贵专门长途作为 Release Gate。

优先：

- 日常驾驶自然积累；
- deterministic replay；
- virtual monotonic clock；
- accelerated / loop soak；
- stationary fault injection；
- parser/unit tests。

只有真实 ECU / OBDLink / Bluetooth 行为必须用车时才消耗实车时间。

## 7. Docs-first

任何源码改动前：

1. pull/fetch 最新仓库；
2. 读完整基线；
3. 明确里程碑和改动边界；
4. 更新 `PROJECT_STATE.md` 当前工作；
5. 架构/语义决策写入 `DECISIONS.md`；
6. `CHANGELOG.md` 先写 `[Unreleased]`；
7. 才允许改源码。

改完后：

1. 跑静态/单元/replay/build；
2. review diff；
3. 更新实际结果；
4. 更新证据和路线图；
5. 更新基线 manifest；
6. 文档与代码一起 commit；
7. push；
8. 验证 GitHub Actions 自动签名 APK。

## 8. GitHub 写入

不要依赖 ChatGPT 的 GitHub connector 写权限。当前已验证该 connector 能读仓库，但 contents 写入曾返回 GitHub 403 `Resource not accessible by integration`。

Codex 的首选写路径：

```text
local git
+ GitHub CLI (gh) browser OAuth
+ HTTPS credential helper
```

迁移包的 `02_AUTOMATION/` 提供 Windows PowerShell 和 Unix/Termux bootstrap。第一次授权不可绕过用户的 GitHub 浏览器确认；授权后可以自动检测、clone/pull、dry-run 验证 write、提交和 push。

禁止把 PAT/token 写进仓库、基线或聊天。

## 9. 当前里程碑

当前工程基线：**V0.2.0 Reactive Core（已关闭 2026-08-09）**。

历史实车验证基线：**V0.1.10**。

下一大版本：**V0.3.0 High-Performance Scheduler / Refresh Frontier**。

V0.2.0 已由用户授权开始；按 `DEVELOPMENT_PROTOCOL.md` 的 docs-first 顺序执行，不得在文档/基线更新前改源码。
