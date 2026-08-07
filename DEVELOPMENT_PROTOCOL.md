# RX400h Monitor — DEVELOPMENT_PROTOCOL

## 1. Canonical memory rule

Project state lives in repository documents, **not in chat history**.

Before any code change, read/update:

1. `PROJECT_STATE.md`
2. `DECISIONS.md`
3. `ROADMAP.md`
4. `CHANGELOG.md`
5. `EVIDENCE_INDEX.md` when evidence changes

The latest source + these documents must be sufficient for a new developer/AI to resume the project.

---

## 2. Mandatory pre-development sequence

For every development session:

```text
1. Pull latest repository.
2. Read PROJECT_STATE / DECISIONS / ROADMAP / CHANGELOG.
3. State the target milestone and exact change boundary.
4. Update PROJECT_STATE "Current work" / next gate if needed.
5. Add or update a DECISIONS entry for any semantic/architectural choice.
6. Add an [Unreleased] CHANGELOG item.
7. Only then edit source.
```

Do not make source changes first and reconstruct intent later.

---

## 3. Mandatory post-development sequence

After implementation:

```text
1. Run relevant static/unit/replay/build checks.
2. Record evidence/results.
3. Update CHANGELOG with actual result (not intended result).
4. Update PROJECT_STATE current baseline/debt/next step.
5. Update EVIDENCE_INDEX with new logs/hashes/artifacts.
6. Update ROADMAP status if an exit gate was reached.
7. Add/supersede DECISIONS if implementation changed a prior assumption.
8. Commit docs and code together.
```

If a major milestone is complete, create a fresh baseline snapshot of all project-state documents in the same commit/tag.

---

## 4. Major-version completion rule

Each `0.x.0` completion must leave the repository able to answer:

- What version is valid?
- What versions are void?
- What protocol is currently allowed?
- What signals/semantics are frozen vs experimental?
- What was validated and how?
- What known debt remains?
- What is the next milestone?
- What must not be reintroduced?

If those questions cannot be answered from repo documents, the milestone is not complete.

---

## 5. Skip-ahead rule

If a future milestone's exit criteria are already fully satisfied:

- Mark the milestone as satisfied in `ROADMAP.md`.
- Record evidence in `CHANGELOG.md`.
- Add a decision note if the skip changes expected sequencing.
- Move directly to the next major version.

Do not implement redundant work merely to consume a version number.

---

## 6. Protocol safety gate

Any change that affects vehicle requests must document:

```text
request/header
purpose
expected response
source evidence
safety class
frequency/bus-load impact
rollback condition
```

No evidence → no new runtime request.

Do not silently reintroduce void/rejected items from old branches or chats.

---

## 7. Consumer traceability gate

Every typed Runtime signal/state must have at least one current consumer:

- product display,
- derived product signal,
- required state/Idle Check,
- connection/health correctness,
- required validation contract.

If no consumer exists:

- remove it from typed Runtime/decoded summaries,
- keep raw response evidence if the containing request is still needed.

---

## 8. Lean Core gate

Before closing each major milestone, perform:

```text
Dead-code audit
Consumer audit
Allocation/hot-path audit
Dependency audit
Duplicate-state/cache audit
```

Prefer deletion over speculative future abstractions.

Avoid adding generalized frameworks when a small fixed RX400h-specific implementation is clearer and lighter.

---

## 9. Performance gate

When performance-sensitive code changes:

Record at least the metrics available for that stage, such as:

- request/signal Hz,
- latency P50/P95/P99,
- deadline misses,
- CPU,
- PSS/heap,
- GC/allocation indicators,
- logger latency/bytes,
- UI publish/render latency.

Optimization claims without measurements remain provisional.

---

## 10. Test-cost gate

Use real vehicle testing only for questions that require real vehicle/adapter behavior.

Prefer:

- deterministic replay,
- virtual clock,
- accelerated/loop soak,
- unit/parser tests,
- stationary fault injection,
- ordinary daily driving.

Do not require costly dedicated long-distance driving as a normal release gate.

---

## 11. Evidence package discipline

For important real-vehicle artifacts record:

```text
filename
SHA-256
app/core version
protocol profile
scheduler profile
session duration
key pass/fail summary
known limitations
```

Never overwrite original evidence archives.

---

## 12. Handoff packet

When a conversation/project context is approaching its limit, the handoff packet is simply:

```text
PROJECT_STATE.md
DECISIONS.md
ROADMAP.md
CHANGELOG.md
DEVELOPMENT_PROTOCOL.md
EVIDENCE_INDEX.md
latest source/archive or repository commit
latest relevant real-vehicle/replay evidence if not already in repo
```

The receiving conversation should first summarize these files and explicitly state the current baseline/next gate before proposing changes.

---

## 13. Build/signing and repository-upload reproducibility

The handoff is incomplete if a new developer knows the architecture but cannot reproduce the signed GitHub build or update the baseline documents.

Canonical operational instructions live in:

```text
GITHUB_BUILD_AND_BASELINE_WORKFLOW.md
```

It must document and remain synchronized with:

- `.github/workflows/build-apk.yml`;
- the fixed development/test signing key path and certificate identity;
- JDK/Gradle/build task expectations;
- APK signature verification;
- Termux source push workflow;
- complete baseline overlay/update workflow;
- `BASELINE_MANIFEST.sha256` regeneration and verification.

If any of these change, update the workflow document in the same commit.

The major-version handoff packet therefore includes:

```text
PROJECT_STATE.md
DECISIONS.md
ROADMAP.md
CHANGELOG.md
DEVELOPMENT_PROTOCOL.md
GITHUB_BUILD_AND_BASELINE_WORKFLOW.md
EVIDENCE_INDEX.md
BASELINE_README.md
BASELINE_MANIFEST.sha256
latest source / repository commit
latest required evidence not already in repository
```


---

## 14. Codex / repository access gate

For Codex, startup order is expanded to:

```text
AGENTS.md
CODEX_HANDOFF.md
PROJECT_STATE.md
DECISIONS.md
ROADMAP.md
CHANGELOG.md
DEVELOPMENT_PROTOCOL.md
EVIDENCE_INDEX.md
GITHUB_BUILD_AND_BASELINE_WORKFLOW.md
REPO_ACCESS_AND_AUTH.md
latest source
```

Before editing source, Codex must verify repository access:

```text
git fetch = PASS
baseline read = PASS
write dry-run = PASS
working tree clean or explicitly understood
```

If authentication is missing, prefer `gh auth login --web` and system credential storage. Never request that a PAT be pasted into a project file.

A failed ChatGPT connector write is not a reason to block development when local GitHub credentials work.
