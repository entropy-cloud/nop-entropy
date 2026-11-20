# nop-git Vision

**范围**：nop-tools 下的 nop-git 模块组（新模块，尚未创建）
**状态**：目标架构（未落地，逐 Wave 立项实施）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. nop-git 提供两个面向使用者的入口：**Java API 门面**（`IGitRepositoryService`，供平台内服务/AI Agent 工具直接集成）与 **git 兼容 CLI**（`nop-git-cli`，子命令与输出格式对齐原生 git，可经 PATH 覆盖供 Claude Code / Aider / Codex CLI 等 AI 编程工具当作系统 Git 使用）。两个入口共用同一执行路径与同一策略引擎。
2. Git 对象模型与底层能力**不自建**，统一使用 JGit（`org.eclipse.jgit`），收敛在 `JGitRepositoryService` 适配层之内，全模块不向外部暴露 JGit 类型。
3. **AI Git 操作权限策略引擎（`IGitPolicyEngine`）是本模块与"JGit 简单封装"的本质差异**：在每次 Git 操作执行前，按声明式 YAML 配置从操作、选项、分支、路径、远程五个维度做 `allow / deny / review` 三态裁决，deny 永远优先；CLI 与 API 两条路径共用同一策略引擎实例，不可绕过。
4. 配置放 `.nop-git/policy.yaml`（项目级 → 用户级 → 内置默认三层合并），支持 `observe`（只记录不阻断）与 `enforce` 两阶段渐进落地，审计日志 JSON Lines。
5. HTTP Git 服务端（`GitServlet` + `PreReceiveHook`）是可选扩展面，排期靠后，仅在客户需要标准 Git 客户端经 HTTP 访问仓库时启用。
6. **linked worktree 的创建与删除是一等能力**（API `addWorktree`/`removeWorktree`/`listWorktrees` + CLI `worktree add/list/remove`）：JGit 7.x 只能读不能写 worktree，add/remove 在适配层按 git 官方元数据格式自建（详见 [05-worktree-design.md](05-worktree-design.md)）。

## 二、背景与动机

- 客户生产环境**不允许安装系统级 Git 二进制**，但 AI 编程工具（Claude Code、Aider、Codex CLI 等）依赖 `git` 命令感知仓库状态、暂存、提交、回滚；无 Git 则 AI 工作流不可用。
- 平台自身（nop-ib、nop-git-master 类 skill、AI Agent 的 git 工具调用）需要一套可在 Java 进程内直接调用的 Git API，避免各自拼 shell。
- AI 的高危 Git 操作（`reset --hard`、`clean -f`、`push --force`、直推保护分支）需要统一、可审计、可配置的拦截层，而不是各工具各自为政。

优先级排序：第一，AI 工具在无 Git 环境"能跑起来"；第二，高频安全操作（`status` / `diff` / `add` / `commit`）流畅不被打断；第三，精确阻断不可逆高危操作，灰色地带提供人工确认通道。

## 三、成功标准

- AI 兼容性：Claude Code 的只读命令探测、Aider 的 `add`+`commit -m` 自动提交、Codex `doctor` 的 `git --version` 解析均正常工作；`status --porcelain`、`diff`、`log --oneline` 输出与原生 git 纯文本格式一致（无 ANSI 颜色码）。
- 集成可用性：平台内 Java 代码通过 `IGitRepositoryService` 完成开仓/状态/暂存/提交/分支/回滚/worktree 增删全流程，不接触 JGit 类型。
- 安全性：enforce 模式下，策略 deny 的操作返回非零退出码与 `fatal: policy denied: ...` 风格错误；review 提供确认通道并在无人值守超时后降级 deny；observe 模式审计日志可完整还原每条裁决。
- 质量：所有子命令与策略裁决有 JUnit 5 测试覆盖；CLI 输出格式有与原生 git 的对照测试（本地有真实 git 时跑 parity 测试，无 git 时用 golden file）。

## 四、Non-goals

- **不做完整 git 的 100% 逐 flag 复刻**：只覆盖 AI 编程工具与平台集成实际调用的子命令集合（见 02-cli-compat-design 优先级表）；未实现子命令输出明确的 `git: 'xxx' is not a nop-git command` 并退出码 1，不静默吞掉。
- **不自建 Git 对象模型/传输协议**：见下节自完备裁定。
- **不做 GUI、不做 git 仓库托管服务**：HTTP 服务层只暴露已有本地仓库的智能协议端点。
- **不做策略规则的运行时编辑 UI**：策略即配置文件，随仓库版本化，变更走 git。
- **不提供 `auto_approve_review` 全局放行开关**：那等于把 review 降级为 allow，失去设计意义；放宽只能通过把具体规则从 review 显式改为 allow。

## 五、自完备裁定（JGit 引入）

平台级 `self-contained-design.md` 禁止引入外部大型库作为能力源或架构支柱，但允许**适配层内收敛的单一职责工具库**。裁定如下：

- Git 的对象存储、packfile、智能传输协议是数十人年级别的工程，从零自建不具备性价比且无法保证与真实 git 仓库的比特级兼容；因此**拒绝"从零实现 git"**。
- JGit 属于单一职责工具库，引用方式满足允许项：全部 JGit 调用收敛在 `JGitRepositoryService` 一个适配类内，模块其余部分（CLI 解析、策略引擎、输出格式化、审计）全部自建；对外 API（`IGitRepositoryService`）与 CLI 契约均为自有类型，替换底层实现（如未来换 native git 进程桥）不动使用方。
- 架构支柱测试：禁止 `io.nop.git` 包以外的类 import `org.eclipse.jgit`（ArchUnit/包结构测试保证）。
- 版本选型：JGit 7.x 稳定版（要求 JDK 17+）；**模块运行基线为 JDK 21**，启动时校验 `Runtime.version().feature() >= 21`，不满足时输出明确错误而非让 JGit 抛 `UnsupportedClassVersionError`。
- **`nop-git-core` 零仓库内部依赖**（落地裁定，plan 2576）：平台模块（nop-core/nop-api-core 等）为 class v69（Java 25）编译，依赖它们会隐式把运行基线抬到 25，破坏 JDK 21 目标；因此 core 只依赖 JGit，错误面用模块级 `NopGitException`。编译级别由 nop-git 父 pom 覆写 `maven.compiler.release=21`（根 pom 的 release=25 property 会被 maven-compiler-plugin 的 release 参数直接消费，子 pom 必须覆写同名 property）。Wave 6 IoC 集成若引入 nop-core 依赖，需重新裁定基线并显式记录。

## 六、与已有设计的关系

- `ai-dev/design/self-contained-design.md` —— 本模块的自完备裁定依据。
- `ai-dev/design/nop-rg/` —— 同为 nop-tools 下"对标原生 CLI 工具"的先行范例，CLI/parity 测试方法论可复用。
- nop-ai-shell / AI Agent 工具体系 —— `IGitRepositoryService` 是 AI Agent git 类工具的推荐后端，取代各工具自行拼 `git` shell。
