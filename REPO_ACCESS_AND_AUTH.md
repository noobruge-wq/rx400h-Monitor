# RX400h Monitor — Repository Access & Authentication

## 1. 目标

Codex 应能：

```text
read repository
fetch/pull
create local changes
commit
push
inspect GitHub Actions
```

项目不依赖 ChatGPT connector 的写权限。

## 2. 当前已知连接状态

迁移时：

- ChatGPT GitHub connector：**read OK**
- ChatGPT GitHub contents API write：**403 Resource not accessible by integration**

因此不要花时间试图把 ChatGPT connector 变成项目的写入通道。

Codex 首选：

```text
local Git repository
+ git
+ GitHub CLI (gh)
+ browser OAuth / credential helper
```

## 3. 为什么无法“完全零交互自动授权”

GitHub 首次登录属于用户授权动作。迁移包不会也不能内置用户 token/PAT。

自动化脚本可以做到：

```text
检测未登录
→ 启动 gh web login
→ 用户浏览器确认一次
→ 自动配置 git credential
→ 自动验证 read/write
```

这已经是安全前提下可以自动化的最大范围。

## 4. 推荐 Windows/Codex 路径

运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\02_AUTOMATION\MIGRATE_TO_CODEX.ps1
```

脚本会：

1. 检查 Git；
2. 若缺少 `gh` 且存在 winget，则安装 GitHub CLI；
3. 检查 GitHub 登录；
4. 未登录则发起 `gh auth login --web`；
5. `gh auth setup-git`；
6. clone 或 pull `noobruge-wq/rx400h-Monitor`；
7. 校验 origin；
8. `git push --dry-run` 到临时 ref 验证写权限；
9. 安装 Codex handoff 文档；
10. 生成 baseline manifest；
11. 可选 commit/push。

## 5. 已有 SSH 环境

如果原有：

```text
git@github.com:noobruge-wq/rx400h-Monitor.git
```

而且：

```bash
git fetch
git push --dry-run origin HEAD:refs/heads/__codex_write_check__
```

成功，则无需改 remote。

如果 Windows 新环境没有原 SSH key，优先使用 `gh` + HTTPS，而不是复制手机私钥。

## 6. 安全规则

禁止：

- 把 GitHub PAT 写入 `.md`；
- 把 token 放进脚本；
- 提交 `~/.ssh` 私钥；
- 把 GitHub CLI credential store 打进迁移包；
- 为“自动”绕过 OAuth 用户确认。

允许：
- GitHub CLI 安全 credential storage；
- 系统 credential helper；
- 用户主动授权 Codex/GitHub 应用。

## 7. 验证标准

### Read

```bash
git fetch origin
git ls-remote origin HEAD
```

### Write permission without modifying remote

```bash
git push --dry-run origin HEAD:refs/heads/__codex_write_check__
```

### Main synchronized

```bash
git rev-parse HEAD
git rev-parse origin/main
git status
```

开发完成后两 SHA 应一致，working tree 应 clean。

## 8. GitHub Actions

有 `gh` 时：

```bash
gh run list --repo noobruge-wq/rx400h-Monitor --workflow build-apk.yml --limit 5
```

必要时：

```bash
gh run watch <RUN_ID> --repo noobruge-wq/rx400h-Monitor
```

构建成功不等于实车验证；项目文档必须区分 source/build/install/E1。
