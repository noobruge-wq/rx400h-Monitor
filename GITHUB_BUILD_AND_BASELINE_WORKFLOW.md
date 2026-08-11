# RX400h Monitor — GitHub 自动编译、固定签名与基线文档上传

本文件是项目交接的一部分。新对话/新开发者不应依赖聊天历史来恢复“怎么编译、怎么保持 APK 可覆盖安装、怎么把项目基线文档推回 GitHub”。

---

## 1. 当前仓库与工作方式

Repository:

```text
noobruge-wq/rx400h-Monitor
```

推荐日常开发路径：

```text
手机 / Termux
    ↓
git pull / 覆盖新源码与基线文档
    ↓
git add / commit / push
    ↓
GitHub Actions
    ↓
Android Debug APK
    ↓
固定测试签名
    ↓
Artifact 下载、安装、实车验证
```

项目以手机端 Termux + GitHub Actions 为优先工作流，不要求本地 Android Studio 才能完成常规构建。

---

## 2. GitHub Actions 自动编译基线

当前自动构建基线采用：

```text
JDK: 17
Gradle: 8.9
Android compile/target SDK: 35（除非后续版本文档明确修改）
Build task: :app:assembleDebug
Unit tests: :app:testDebugUnitTest (before assembleDebug)
Static analysis: :app:lintDebug
APK verification: apksigner verify
```

Workflow 位于：

```text
.github/workflows/build-apk.yml
```

签名材料位于：

```text
.github/signing/rx400h-debug.keystore
```

### 2.1 固定测试签名

该 key 是项目专用的固定 **debug/test key**，目的是让连续开发版本能够覆盖安装，避免每次 GitHub 构建产生不同签名。

当前约定：

```text
alias: rx400hdebug
keystore password: android
key password: android
```

证书信息：

```text
Owner/Issuer:
CN=RX400h Protocol Probe Debug, O=Guanyu, C=NZ

RSA: 2048 bit

Certificate SHA-1:
2B:2B:AA:41:BD:D1:C9:D6:C7:97:B6:40:2C:19:C7:DC:B7:CE:D3:74

Certificate SHA-256:
77:BA:84:B1:F4:F7:37:A5:D6:1B:91:0B:F4:38:6D:F1:67:54:8B:9C:6C:E6:89:ED:25:E9:94:C3:7B:2B:C1:92
```

Keystore file historical SHA-256:

```text
8e2ecdec24f9f628fd788cd4cb97e614172d3bb600e8d08d8fa8d4a87247bcb1
```

### 2.2 签名约束

除非明确决定做签名迁移，否则不得：

- 随意生成新的 debug keystore；
- 改 package/applicationId 后仍声称旧 APK 可覆盖安装；
- 改 alias/password/workflow 而不更新本文件和 `DECISIONS.md`；
- 把这个测试 key 误称为正式商店/release key。

该 key 的作用仅是当前开发/实车测试链的安装连续性。

---

## 3. GitHub 自动构建的验收条件

每次大版本/需要安装测试的候选提交，在 GitHub Actions 中至少应满足：

```text
Gradle build PASS
:app:testDebugUnitTest PASS
:app:lintDebug PASS
:app:assembleDebug PASS
APK exists
apksigner verify PASS
Artifact uploaded
```

Artifact 名称应包含版本，避免多个候选混淆。例如 V0.1.10 历史约定为：

```text
RX400hProtocolProbe-v0.1.10-cleanup-debug-signed
```

V0.2.0 起使用：

```text
RX400hProtocolProbe-v0.2.0-reactive-debug-signed
```

V0.2.1 UI patch：

```text
RX400hProtocolProbe-v0.2.1-ui-debug-signed
```

V0.3.0 开发分支候选：

```text
RX400hProtocolProbe-v0.3.0-bottom-align-debug-signed
```

当前 V0.3.1 三按钮/可恢复日志候选：

```text
RX400hProtocolProbe-v0.3.1-resilient-logs-debug-signed
```

V0.3.1 是仍处于 V0.3.0 Scheduler / Refresh Frontier 工程里程碑内的 App 版本；Artifact、Gradle `versionName/versionCode` 与 session build provenance 必须指向同一 exact commit。

Gradle 的 Git provenance 采集必须 fail closed：Git 不可执行、命令失败或 commit ID 非 40 位十六进制时，构建直接失败。GitHub Actions 还必须在上传前检查生成的 `BuildConfig`：`GIT_COMMIT == GITHUB_SHA` 且 `GIT_DIRTY == false`。

版本进入正式 Monitor 阶段后可以调整命名，但必须保持“从 Artifact 名就能识别 app version / candidate”的原则。

### 构建失败时

不得把“源码已 push”写成“APK 已验证”。应在 `PROJECT_STATE.md` / `CHANGELOG.md` 中区分：

```text
source committed
GitHub build passed/failed
APK signed/verified
APK installed
real-vehicle validated
```

---

## 4. 手机 Termux：普通源码更新并触发自动编译

仓库路径：

```bash
cd ~/rx400h-Monitor
```

开始前：

```bash
git pull --ff-only
git status
```

将新的源码包解压到临时目录，再覆盖仓库。例如：

```bash
rm -rf ~/rx400h-update
mkdir -p ~/rx400h-update

unzip ~/storage/downloads/<SOURCE_PACKAGE>.zip \
  -d ~/rx400h-update
```

然后把**源码包内部真正的项目根目录**覆盖到 Git 仓库根目录：

```bash
cp -a ~/rx400h-update/<PROJECT_ROOT>/. ~/rx400h-Monitor/
```

检查：

```bash
cd ~/rx400h-Monitor
git status
git diff --stat
```

提交：

```bash
git add .
git commit -m "<version>: <summary>"
git push
```

验证：

```bash
git log --oneline -5
git status
```

成功标准：

```text
HEAD 与 origin/main 指向同一新 commit
working tree clean
```

Push 后 GitHub Actions 自动开始构建。

---

## 5. 每个大版本的“完整基线文档”组成

每个 `0.x.0` 大版本关闭时，仓库根目录必须至少存在并更新：

```text
BASELINE_README.md
PROJECT_STATE.md
CHANGELOG.md
DECISIONS.md
ROADMAP.md
DEVELOPMENT_PROTOCOL.md
EVIDENCE_INDEX.md
GITHUB_BUILD_AND_BASELINE_WORKFLOW.md
BASELINE_MANIFEST.sha256
```

如项目后续增加稳定的新基线文件，必须同步写进：

- `BASELINE_README.md` 的读取顺序；
- `DEVELOPMENT_PROTOCOL.md` 的 handoff packet；
- `BASELINE_MANIFEST.sha256`。

---

## 6. 基线文档更新顺序

开发任何大版本时：

```text
1. 先更新 PROJECT_STATE / CHANGELOG / DECISIONS / ROADMAP
2. 再改源码
3. 实现和验证结束后，再把“实际结果”写回上述文档
4. 更新 EVIDENCE_INDEX
5. 核对 GitHub build/signing 状态
6. 更新 GITHUB_BUILD_AND_BASELINE_WORKFLOW（仅当流程有变化）
7. 最后重新生成 BASELINE_MANIFEST.sha256
8. 文档与源码一起 commit/push
```

不要先改代码，几周后再靠聊天记录补文档。

---

## 7. 生成/更新 `BASELINE_MANIFEST.sha256`

在 Termux 仓库根目录执行：

```bash
cd ~/rx400h-Monitor

sha256sum \
  AGENTS.md \
  BASELINE_README.md \
  CHANGELOG.md \
  CODEX_HANDOFF.md \
  DECISIONS.md \
  DEVELOPMENT_PROTOCOL.md \
  EVIDENCE_INDEX.md \
  FULL_PROJECT_CONTEXT.md \
  GITHUB_BUILD_AND_BASELINE_WORKFLOW.md \
  PROJECT_STATE.md \
  REPO_ACCESS_AND_AUTH.md \
  ROADMAP.md \
  > BASELINE_MANIFEST.sha256
```

然后立即校验：

```bash
sha256sum -c BASELINE_MANIFEST.sha256
```

要求全部：

```text
OK
```

注意：`BASELINE_MANIFEST.sha256` 不应包含它自己的哈希，否则会形成自引用。

---

## 8. 从 ChatGPT 生成的 repo overlay 上传完整基线

如果 ChatGPT 交付的是类似：

```text
RX400h_vX.Y.Z_baseline_repo_overlay.zip
```

先把 ZIP 下载到手机 `Downloads`，然后：

```bash
cd ~/rx400h-Monitor

git pull --ff-only

unzip -o \
  ~/storage/downloads/RX400h_vX.Y.Z_baseline_repo_overlay.zip \
  -d .
```

先检查，不要直接盲推：

```bash
git status
git diff --stat
git diff -- PROJECT_STATE.md CHANGELOG.md DECISIONS.md ROADMAP.md
```

然后校验基线 manifest：

```bash
sha256sum -c BASELINE_MANIFEST.sha256
```

确认后：

```bash
git add \
  BASELINE_README.md \
  PROJECT_STATE.md \
  CHANGELOG.md \
  DECISIONS.md \
  ROADMAP.md \
  DEVELOPMENT_PROTOCOL.md \
  EVIDENCE_INDEX.md \
  GITHUB_BUILD_AND_BASELINE_WORKFLOW.md \
  BASELINE_MANIFEST.sha256

git commit -m "Add/update vX.Y.Z project baseline"
git push
```

最后验证：

```bash
git log --oneline -5
git status
git rev-parse HEAD
git rev-parse origin/main
```

`HEAD` 与 `origin/main` 必须相同，且 working tree clean。

---

## 9. 如果“源码更新包”和“基线 overlay”同时存在

顺序统一为：

```text
1. git pull --ff-only
2. 覆盖源码更新包
3. 覆盖 baseline repo overlay
4. git status / diff
5. 验证 BASELINE_MANIFEST.sha256
6. git add .
7. 一个 commit（优先）或按需要拆成 code/docs 两个连续 commit
8. git push
9. 等 GitHub Actions
10. 验证 signed APK artifact
11. 把 build/实车结果回写 PROJECT_STATE / CHANGELOG / EVIDENCE_INDEX
```

如果 APK 实车结果是在 push 之后才得到，可以用第二个 documentation-only commit 回写证据，避免事先声称测试通过。

---

## 10. 新对话的最短恢复路径

未来对话达到长度上限时，新对话只需要：

```text
GitHub repository + latest commit
PROJECT_STATE.md
DECISIONS.md
ROADMAP.md
CHANGELOG.md
DEVELOPMENT_PROTOCOL.md
EVIDENCE_INDEX.md
GITHUB_BUILD_AND_BASELINE_WORKFLOW.md
最新必要 evidence（如果未入库）
```

新对话第一件事应确认：

```text
当前有效版本 / commit
当前 GitHub build/signing 方法
当前 protocol profile
当前大版本 Gate
当前已验证与未验证内容
下一项允许实施的工作
```

不得从旧聊天历史猜测这些内容。


---

## 11. Codex local Git write path

ChatGPT connector write permission is not a required dependency.

For a new Codex Windows machine:

```powershell
gh auth login --hostname github.com --git-protocol https --web
gh auth setup-git
gh repo clone noobruge-wq/rx400h-Monitor
```

For an existing clone:

```powershell
git fetch origin
git pull --ff-only
git push --dry-run origin HEAD:refs/heads/__codex_write_check__
```

The transfer package includes `MIGRATE_TO_CODEX.ps1` / `.sh` to automate detection and setup.

The browser OAuth confirmation on first login must remain user-controlled and is intentionally not embedded/bypassed.
