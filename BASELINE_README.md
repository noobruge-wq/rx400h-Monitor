# RX400h Monitor — Complete Codex Baseline / Read Order

本包和仓库的目的：让 Codex 在没有旧聊天记录的情况下直接完整接手项目。

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
- current installable candidate = V0.3.1 / versionCode 23 (implementation pushed on `v0.3.0`; local API 26/UI smoke passed; clean exact-commit CI, API 27 and real-OBD save/recovery smoke pending);
- protocol is whitelist/evidence-driven;
- Lean Core + high useful refresh are first-class objectives;
- no expensive dedicated long-trip release gate;
- docs first, code second;
- local git/gh is the canonical Codex write path.
