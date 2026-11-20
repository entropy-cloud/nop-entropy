# nop-ai-shell Git 工具后端切换评估（roadmap item 30）

Status: resolved（结论已定，实施另立项）
Date: 2026-11-21
Scope: nop-ai/nop-ai-shell（现状盘点）、nop-tools/nop-git（新能力面）、legacy nop-utils/nop-git 处置
Conclusion: 三段式结论见文末——Phase 1 已随 nop-git-cli 交付零改动生效；Phase 2/3 不改 nop-ai-shell，另立项。

## 1. 现状盘点（live 证据）

- `nop-ai/nop-ai-shell/src/main/java` 全量 grep 无任何 `git` 引用（0 命中）：nop-ai-shell 是**通用 shell 执行器**（parser/executor/checker/adapter），git 类操作由 AI 编程工具经系统 PATH 自行调用 `git`，nop-ai-shell 不感知。
- 因此「拼 shell 的 git 调用点」在 nop-ai-shell 内**不存在**——design 04 §三「AI Agent 工具层推荐将 git 类工具后端从拼 shell 切换为注入 IGitRepositoryService」的实际落点是 **PATH 覆盖**（AI 工具在无系统 Git 环境中把 nop-git-cli 当系统 git 用），该能力已随 Wave 2（plan 2577 bin/git 包装 + shade jar）交付，nop-ai-shell 零改动即受益。

## 2. 可选深化面（不改 nop-ai-shell 的前提下）

- 平台内 Java 服务（非 AI 工具）需要 git 能力时，直接注入 `IGitRepositoryService`（Wave 6 新增 `nop-git-ioc` beans 面，`gitIocFactory` 工厂 + 配置覆盖），自动获得策略守卫与审计。
- 策略引擎与审计在 CLI 与 API 两条路径同引擎实例，两条后端不产生裁决分歧。

## 3. legacy nop-utils/nop-git 处置裁定

- `nop-utils/nop-git`（`IGitRepository`/`GitRepositoryImpl`，单分支模型文件管理，依赖 nop-core + JGit 7.4）：**原地保留，不迁移不删除**。理由：其调用方在平台内部（模型文件管理场景），与 nop-git 的 AI 工具面（全命令 CLI + 策略 + worktree）职责不同；迁移收益低、回归面大。
- 后续若其维护成本上升，可另立项将其 `GitRepositoryImpl` 底座切换到 `IGitRepositoryService`（successor：无强制）。

## 4. 结论

1. **Phase 1（已交付）**：nop-git-cli PATH 覆盖 + AI 工具冒烟契约（plan 2577）——nop-ai-shell 零改动。
2. **Phase 2（可选，另立项）**：平台 Java 服务经 `nop-git-ioc` 注入 `IGitRepositoryService`。
3. **Phase 3（可选，另立项）**：nop-ai-shell 增加感知 git 的白名单/策略联动（当前无需求信号）。

4. legacy `nop-utils/nop-git`：保留不动（本节 §3）。
