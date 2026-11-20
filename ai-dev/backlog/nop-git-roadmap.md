# nop-git Roadmap

> Last updated: 2026-11-20
> Sources: `ai-dev/design/nop-git/00-vision.md` (Vision), `ai-dev/design/nop-git/01-architecture-baseline.md` (Architecture Baseline)

## Purpose

This roadmap tracks the implementation of nop-git: a pure-Java Git tool module for the Nop platform providing (1) a Java API facade (`IGitRepositoryService`) for direct integration, (2) a git-CLI-compatible command line (`nop-git-cli`) deployable via PATH override so AI coding tools treat it as system git, and (3) a config-driven AI Git operation policy engine (`allow/deny/review`) shared by both entry paths.

Terminal goal: AI 编程工具（Claude Code / Aider / Codex CLI）在无系统 Git 环境中可完整工作；P0/P1 子命令输出与原生 git 纯文本格式逐字节对齐（golden 对照）；enforce 模式下五维策略裁决 + 审计可用；所有子命令与裁决路径有测试覆盖。

Does not contain implementation details. Each `planned` stage is owned by its execution plan.

## Work Items

> **This is the only dynamic state block. Update status only here.**

### Wave 1: 骨架与 API 门面

- 1. nop-git module skeleton (parent POM + nop-git-bom + nop-git-core/cli 模块占位，接入 nop-tools modules)：`planned`
- 2. JGit 依赖引入与版本锁定（bom 唯一声明点；JDK 17 启动校验）：`planned`
- 3. 自有结果模型（NopGitStatus / NopGitCommit / NopGitDiffEntry / NopGitRef + Options 类型）：`planned`
- 4. `IGitRepositoryService` 门面 + `JGitRepositoryService` 适配层（open/init/status/add/commit/log/branch/resolveRevision）：`planned`
- 5. 空仓库 / 默认分支 / Repository 生命周期（AutoCloseable）语义处理：`planned`
- 6. 架构支柱测试（`io.nop.git` 外禁止 import org.eclipse.jgit）：`planned`
- ★ **Milestone: Java API 集成可用** (unlocks when 1 + 3 + 4 + 5 + 6 done): `planned`

### Wave 2: git 兼容 CLI（P0/P1）

- 7. CLI 骨架（GitCompatCli main、`-C`、退出码契约、fatal/error 消息格式、未实现子命令报错）：`planned`
- 8. P0 子命令：`--version`（配置伪装版本）、`status --porcelain`、`diff`（--cached/HEAD/--stat）、`add`、`commit -m/-a`：`planned`
- 9. P1 子命令：`log --oneline/-n`、`branch`（--show-current/-a）、`rev-parse`（HEAD/--abbrev-ref/--show-toplevel）、`config` 子集：`planned`
- 10. GitOutputFormatter + 原生 git golden 对照测试框架（本地有真实 git 时录制/对比，无 git 时 golden file）：`planned`
- 11. PATH 覆盖部署包装（sh 脚本 + Windows .cmd）+ AI 工具兼容冒烟清单（Claude Code 只读探测 / Aider add+commit 输出解析 / Codex doctor --version 解析）：`planned`
- ★ **Milestone: AI 工具可当系统 Git 使用（P0/P1）** (unlocks when 7..11 done): `planned`

### Wave 3: 权限策略引擎

- 12. `IGitPolicyEngine` 骨架（IGitOperationRequest 归一化模型 + 三态裁决枚举 + 分层配置加载/strict 合并）：`planned`
- 13. 五维规则评估：操作级、选项级（含同族 flag 归一化 -f/--force 等）、分支级（protected_branches + refspec 解析）、路径级（deny/review paths + status 路径集合）、远程级（remotes/protocols/url patterns）+ scopes 覆盖：`planned`
- 14. review 通道（交互确认 / NOP_GIT_REVIEW_APPROVED 环境变量 / 超时降级 deny）：`planned`
- 15. observe/enforce 两模式 + JSON Lines 审计日志（hypothetical_effect、命中规则 ID、outcome）：`planned`
- 16. CLI 拦截点 + API 门面拦截点接入（同一引擎实例）+ deny 输出 `fatal: policy denied: ...` 契约测试：`planned`
- 17. 策略裁决全覆盖测试（每维度 × allow/deny/review × observe/enforce、绕过尝试：flag 别名、路径遍历、refspec 变体）：`planned`
- ★ **Milestone: 策略防火墙可用** (unlocks when 12..17 done): `planned`

### Wave 4: CLI 扩展（P2/P3）

- 18. P2：`checkout`/`switch`、`reset`（soft/mixed/hard）、`clean`、`restore`/`rm`/`mv`、`stash` 子集：`planned`
- 19. P3（本地）：`init`（defaultBranch 配置）、`worktree` 子集、`remote -v/add/set-url`：`planned`
- 20. P3（网络）：`push`/`fetch`/`pull`/`clone` refspec 子集（受远程规则约束）：`planned`
- 21. P3（历史改写类，最后）：`merge`/`rebase`/`cherry-pick` 子集：`planned`
- 22. 扩展子命令逐个补 golden 对照 + AI 工具回归冒烟：`planned`
- ★ **Milestone: CLI 覆盖面达标** (unlocks when 18..22 done): `planned`

### Wave 5: 可选 HTTP Git 服务层

- 23. nop-git-server 模块（GitServlet + RepositoryResolver + incrementOpen 生命周期）：`planned`
- 24. PreReceiveHook 服务端分支保护（直推/强推/删除校验，与 CLI 层策略互补）：`planned`
- 25. 仓库注册表配置 + 部署形态（内嵌容器 / 挂入已有 Nop 服务）+ HTTP 协议集成测试：`planned`
- ★ **Milestone: HTTP 端点可用（可选）** (unlocks when 23..25 done): `planned`

### Wave 6: 平台集成与收尾

- 26. nop-git-core beans.xml（IGitPolicyEngine / service 工厂显式 bean 声明，@InjectValue 配置覆盖）：`planned`
- 27. nop-ai-shell AI Agent git 工具后端切换评估（拼 shell → IGitRepositoryService 注入）：`planned`
- 28. `docs-for-ai/` 使用文档 + INDEX 路由更新；架构支柱/策略绕过专项审计；全模块测试收口：`planned`
- ★ **Milestone: 平台集成完成** (unlocks when 26 + 27 + 28 done): `planned`

## Dependencies & Notes

- Wave 1 → 2 → 3 为严格前置（策略引擎需要归一化请求模型；CLI 对照测试框架是后续所有子命令的质量门）；Wave 4/5/6 可在 Wave 3 后并行立项。
- 每个 Wave 立项时在 `ai-dev/plans/` 建执行计划，遵守 plan guide； protected-area 不涉及（新模块），但涉及跨模块接入（Wave 6-27 触碰 nop-ai-shell）时需按对应模块规则评估。
- 性能非本模块首要目标（AI 工具为串行小命令调用），不设 JMH 基准 Wave；正确性/格式保真优先。
