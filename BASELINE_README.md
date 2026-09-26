# RX400h Monitor — Complete Codex Baseline / Read Order

2026-09-23 最新入口：`D064_LOCAL_REVIEW_20260923.md` 与 `../../deliverables/D064-realtime-local-20260923/manifest.json`。V0.3.5/v29本地纯实时仪表候选：约3Hz单HA策略、自动连接、点车图重连、后台采集、返回退出、只保留本机设置。用户已取消产品日志/性能统计/保存恢复/稳健策略；不能据旧文档复原。原D063源码ZIP和原始数据保留；本地检查通过与真实蓝牙尚未验证须分开。源码ZIP排除签名密钥，重建须在原Git/工具/签名环境，不是拿旧HEAD即可复现。

本包和仓库的目的：让 Codex 在没有旧聊天记录的情况下直接完整接手项目。

2026-09-12 最新入口：`D061_LOCAL_REVIEW_20260912.md` 和 `../../deliverables/D061-AB-local-20260912/manifest.json`。这是保留D060之上的A+B本地dirty候选，不是正式/实车基线；完整源码快照含未跟踪文件，D060→D061专用diff避免把历史修改算作本轮。非调试测试APK `ea2525dc…2d8835`、debug对照 `56779960…cf4db7`。STPX/STBC未启用、没有push/CI；以下旧“当前候选”按日期作为历史读取。

2026-09-06 最新补充：D-058 已完成六次本地动态运行和 GUI/同环境旧包对照，零输入跳过；但 R1 辉光像素不完全等价，暂不发布/晋升。详细结果见 `../../outputs/d058-runtime-20260906/D058_RUNTIME_REPORT.md` 和 `PROJECT_STATE.md` §29。验证包 `b388efed…e68b7` 不同于原 R1 包 `33021cb6…7fafa`；9 月 5 日 artifact.json 的未安装/受阻值是历史记录，不再代表当前验证状态。下方旧版本摘要不替代最新检查点。

## Codex 必须按顺序读

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
11. 最新源码

## 大文件不要先全部展开

`04_EVIDENCE/` 内包含 HCI、APK、实车日志和历史审计证据。它们用于需要追溯时核验，不要求 Codex 初次接手时一次性吞掉所有二进制内容。

## 三角色入口

- 新 Chat：先读 `CHAT_ROLE.md`，动态状态再读 `PROJECT_STATE.md`。
- 新 Work：先读 `WORK_ROLE.md`，修改前必须核对真实 remote / branch / HEAD / dirty state。
- Codex：按 `AGENTS.md`；收到完整 escalation packet 时采用最小充分读取，不为局部问题全面扫描。

## Canonical rules

- project memory lives in Git docs, not chat;
- V0.1.9 = VOID;
- current baseline = V0.2.0 Reactive Core (closed 2026-08-09; historical validated baseline = V0.1.10);
- active engineering milestone = V0.3.0 High-Performance Scheduler / Refresh Frontier;
- installed/vehicle-evidence app candidate = V0.3.3 / versionCode 25 (D-047…D-052; exact-clean local commit `c9ad397`; API 27 normal start/LIVE/End/public-save evidenced by the two hash-audited 2026-08-26 archives; forced interrupted recovery and remote CI pending);
- current local source/artifact candidate = V0.3.5 / versionCode 28 (D-056 exact user-authored 640×360→1280×720 pixel dashboard plus D-057 presentation-only renderer cache; retaining D-055 recovery behavior; base HEAD `8f1a3b9`, optimized dirty-worktree APK SHA-256 `b8cfe5d5…28bbab`, 111/111 direct JUnit, compile, lint, assemble, package identity and fixed-v2 signature pass; repaired API 26/1280×720 AVD confirms complete dashboard/settings UI and reduces steady median frame time 40→20 ms with slow uploads 9→0; target-head-unit CPU/paired-OBD follow-up pending, not promoted; reviews in `CODE_REVIEW_V0.3.5_V28.md` and `CODE_REVIEW_V0.3.5_V28_D057.md`);
- protocol is whitelist/evidence-driven;
- Lean Core + high useful refresh are first-class objectives;
- no expensive dedicated long-trip release gate;
- docs first, code second;
- local git/gh is the canonical Codex write path.
