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

## Canonical rules

- project memory lives in Git docs, not chat;
- V0.1.9 = VOID;
- current baseline = V0.2.0 Reactive Core (closed 2026-08-09; historical validated baseline = V0.1.10);
- next milestone = V0.3.0 High-Performance Scheduler;
- protocol is whitelist/evidence-driven;
- Lean Core + high useful refresh are first-class objectives;
- no expensive dedicated long-trip release gate;
- docs first, code second;
- local git/gh is the canonical Codex write path.
