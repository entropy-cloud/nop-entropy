# nop-git 权限策略引擎设计

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 策略引擎（`IGitPolicyEngine`）在**每次 Git 操作执行前**，根据声明式配置对归一化的 `IGitOperationRequest` 做三态裁决：`allow`（直接放行）/ `deny`（拒绝，非零退出码 + 明确错误）/ `review`（暂停，等待人工确认）。**deny 永远优先于 allow**，任一层命中 deny 即最终 deny。
2. 五个规则维度：**操作级**（命令名）、**选项级**（同命令不同 flag 风险不同）、**分支级**（保护分支的直推/强推/删除）、**路径级**（敏感文件）、**远程级**（推送目标白名单），外加 **scope 覆盖**（按仓库路径匹配的局部 deny 提升）。
3. 两阶段落地：`observe` 模式只记录不阻断（策略调优期），`enforce` 模式真实拦截；所有裁决写 JSON Lines 审计日志。
4. CLI 与 Java API 两条路径共用同一 `IGitPolicyEngine` 实例/配置源，裁决逻辑一致，不可绕过。

## 二、配置模型

分层加载（优先级高→低）：项目根 `.nop-git/policy.yaml` → 用户级 `~/.nop-git/policy.yaml` → 内置默认。合并为 strict 语义：各层规则合并，deny 永远不被低优先级 allow 覆盖。

```yaml
version: "1.0"
mode: observe                 # observe | enforce
default_effect: deny          # 未命中任何规则时：deny / allow / review
audit_log: ".nop-git/audit.log"
git_version_string: "2.45.0"  # --version 伪装的版本号（必须为真实存在的 git 版本）
review_timeout_seconds: 30    # review 超时降级 deny

operations:
  allow: [status, diff, log, add, commit, rev-parse, branch, config]
  deny: [gc, reflog]
  review: [push, reset, clean, checkout]

option_rules:                  # 只写规范 flag（归一化在评估前，见 §三表格）
  - operation: reset
    deny_flags: ["--hard", "--keep"]
  - operation: clean
    deny_flags: ["--force", "--directories", "--force-x"]
  - operation: push
    deny_flags: ["--force", "--force-with-lease", "--mirror", "--delete"]
  - operation: branch
    deny_flags: ["--delete --force", "--delete"]   # 规范形为组合串；-D 归一为 --delete --force

protected_branches:
  patterns: ["main", "master", "release/*"]
  rules:
    allow_direct_push: false
    allow_force_push: false
    allow_delete: false

path_rules:
  - operation: [add, rm, checkout, restore]
    deny_paths: ["**/.env*", "**/secrets/**", "**/*.pem", "**/*.key", ".github/workflows/**"]
    review_paths: ["pom.xml", "package.json"]

remote_rules:
  allow_remotes: ["origin"]
  deny_protocols: ["http"]
  allow_url_patterns:
    - "https://git.company.com/**"
    - "ssh://git@git.company.com/**"

scopes:
  - match: "gitdir:/work/project-a/"
    operations:
      deny: [reset, clean, push]
```

字段语义要点：

- `default_effect: deny` 是 AI 场景的保守默认；只读命令靠 `operations.allow` 显式放行，保证高频操作不被打断。
- deny/review/allow 的 `operations` 集合互斥；同一命令出现在多层时 deny 胜出。
- `option_rules` 与 `operations` 正交：`reset` 整体可 review，但 `--hard` 无条件 deny。
- `protected_branches.patterns` 用 glob；`push` 解析 refspec 目标、`branch -d/-D` 解析目标分支后匹配。
- `path_rules` 中 `deny_paths` 对 `add`/`rm` 生效需结合 status 求实际涉及路径集合；`review_paths` 命中转 review（如 CI 配置适合 review 而非一刀切 deny）。
- `scopes.match` 按仓库工作目录（含 `gitdir:` 前缀语义）匹配，命中的 scope 内 deny 以最高优先级介入。

## 三、评估算法（语义契约，伪代码）

```
evaluate(request):
    merge_layers(project, user, builtin)          # deny 优先合并
    normalize(request)                            # 选项归一化、路径规范化、refspec/分支提取

    if scope_matched(request.repoPath):           # scope 覆盖：最高优先级
        if request.operation in scope.operations.deny: return DENY(scope_rule_id)

    if request.operation in operations.deny:      # 操作级
        return DENY(op_rule_id)

    if any_flag(request.flags, option_rules.deny_flags):   # 选项级
        return DENY(flag_rule_id)
    if any_flag(request.flags, option_rules.review_flags):
        verdict = REVIEW(flag_rule_id)

    if target_matches_protected(request):         # 分支级
        if forbidden_by(request.operation, protected.rules): return DENY(branch_rule_id)

    if any_path(request.paths, path_rules.deny_paths):     # 路径级
        return DENY(path_rule_id)
    if any_path(request.paths, path_rules.review_paths):
        verdict = max(verdict, REVIEW(path_rule_id))

    if request.remote violates remote_rules:      # 远程级
        return DENY(remote_rule_id)

    if verdict == REVIEW: return REVIEW           # review 累积但低于任何 deny
    if request.operation in operations.allow: return ALLOW
    return default_effect
```

评估语义钉死（落地裁定，plan 2578）：

- verdict 偏序：DENY > REVIEW > ALLOW；`max(verdict, REVIEW)` 按此偏序。
- scope 命中但未命中其 operations.deny 时 fall-through 继续后续维度（不短路）。
- review 累积发生在 allow 白名单判定**之前**：操作在 operations.allow 且已命中 review_flags/ review_paths → 最终 REVIEW（有意语义：高危 flag 不因操作白名单放行）。
- **引擎非激活**：项目与用户层均无 policy.yaml 时引擎 inactive——不拦截、不写审计文件（保证无策略部署的输出与行为零变化，parity/golden 回归不红）；存在任一层 policy.yaml 时按其 mode 激活（缺省 observe）。
- **审计 outcome 回填协议**：单行一次写入（不 pending、不改写）；入口 wrapper 在操作完成后同步写入含 outcome 的完整行（CLI=命令分发处、API=门面方法公共包装）；进程被杀可留无 outcome 字段的行（可接受）。E2E 行数断言只数含 outcome 的完整行。

选项归一化规则（防绕过的核心；`-d` 语义按命令定表）：

| 原始写法 | 归一化为 |
|---|---|
| `-f`（push/clean 上下文） | `--force` |
| `--force-with-lease[=<ref>]` | `--force-with-lease` |
| `--hard` / `--mixed` / `--soft` / `--keep` | 各自保留（reset mode 族，互不代换） |
| `-fd` / `-f -d` / `--force --directories` | `--force` + `--directories` 两个规范 flag |
| `-D` | `--delete --force`（branch 上下文） |
| `-d` | branch 上下文 → `--delete`；clean 上下文 → `--directories`（按命令查表） |

归一化发生在策略评估**之前**，策略配置只写规范 flag。

## 四、review 的实现方式

- **交互式终端**：输出待执行命令、命中规则 ID、风险原因，读取标准输入确认（`yes` 继续，其余视为拒绝）。
- **无人值守**：检查环境变量 `NOP_GIT_REVIEW_APPROVED=1`（由外部审批流程设置）；或 `review_timeout_seconds`（默认 30s）超时后降级为 deny，错误信息说明 `review timed out, operation denied`。
- **不提供自动批准全部 review 的开关**（vision §四 non-goals）：放宽只能把具体规则显式改为 allow。
- review 降级策略可配置：`review_unattended: deny`（默认）。

## 五、observe 模式与审计

- `mode: observe` 时所有操作放行，审计记录额外携带 `hypothetical_effect`（enforce 模式下会是什么裁决）与命中规则 ID。
- 审计记录（JSON Lines，一行一条）：`timestamp`、`repoPath`、`rawCommand`、`normalizedOperation`、`flags`、`paths`、`remote`、`matchedRuleIds`、`verdict`、`hypotheticalEffect`（仅 observe）、`outcome`（exit code / 成败）。
- 用途：策略上线前先 observe 运行一段 AI 工作流，统计实际操作分布，修正过严/遗漏的规则，再切 `enforce`。

## 六、拦截点

两个拦截点，同一引擎实例：

1. **CLI 层**：`GitCompatCli` 解析完 argv → 构造 `IGitOperationRequest` → 裁决 → 通过才调门面。AI 工具拼任意 shell 字符串（如 `git push --force origin main`）都会在此被解析并评估——这是不可绕过的强制拦截点。
2. **API 门面层**：`IGitRepositoryService` 每个公开方法内部，调用 JGit 前再次裁决，防止 Java 调用方注入绕过 CLI。

## 七、拒绝了什么

- **代码内硬编码策略**：策略必须随仓库版本化、跨分支可不同，硬编码不可审计不可分发。
- **正则匹配原始命令行字符串**：字符串级匹配易被引号/顺序/缩写绕过；必须先结构化解析为归一化请求再匹配。
- **二态（allow/deny only）模型**：无 review 通道会把灰色地带操作逼成要么全放要么全禁，AI 无人值守工作流无法渐进收紧。
- **auto_approve_review 全局开关**：等于 review 降级 allow，见 §四。
