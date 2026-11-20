# 2580 nop-git Wave 5: 可选 HTTP Git 服务层

> Plan Status: completed
> Last Reviewed: 2026-11-21
> Source: `ai-dev/backlog/nop-git-roadmap.md` Wave 5 (items 26-28)；`ai-dev/design/nop-git/04-api-integration-design.md` §四
> Related: plan 2576-2579 已完成

## Purpose

落地可选 HTTP Git 服务层（roadmap items 26–28）：`nop-git-server` 模块提供 GitServlet 智能协议端点 + 仓库注册表 + PreReceiveHook 服务端分支保护，嵌入 Jetty 可独立运行；HTTP 协议集成测试（本地起服 → git clone/push 经 HTTP 往返）钉死可用性。

## Current Baseline

- Wave 1-4 完成（142 tests）。`org.eclipse.jgit.http.server:7.7.1.202607240634-r` 已确认可从远端拉取（本地 m2 现已缓存）并基于 jakarta.servlet-api；Jetty ee10（jakarta servlet）与 javax jetty 9.4 均在本地 m2。
- design 04 §四：GitServlet + RepositoryResolver + incrementOpen 生命周期；服务端完整性由 PreReceiveHook 保护（保护分支直推/强推/删除校验，与 CLI/API 层策略互补）；部署形态=独立可执行（内嵌容器）或挂入已有 Nop 服务（后者 Wave 6+）；仓库注册表配置声明仓库根目录映射。
- gap：nop-git-server 模块不存在。

## Goals

- `nop-git-server` 模块（parent=nop-git-parent）：`GitServerConfig`（port、registry：仓库根目录 + 名称映射）、`NopGitHttpServer`（Jetty ee10 嵌入式启动/停止）、`GitServlet` 接线（RepositoryResolver 解析 `<name>.git`、incrementOpen）、`ProtectedBranchPreReceiveHook`（main/master/release/* 直推/强推/删除拒绝）。
- CLI 可执行入口（main + 端口/根目录参数）与编程式 API（start/stop）。
- HTTP 协议集成测试：本地起服（随机端口）→ `git clone http://...` → commit → `git push` → 断言服务端仓库收到；保护分支强推被拒（非零退出 + 服务端拒绝消息）。

## Non-Goals

- 挂入已有 Nop 服务的 beans.xml 部署形态（Wave 6 一并评估；本 Wave 仅嵌入式 Jetty + main）。
- 鉴权/HTTPS（design 未要求；拒绝面只有分支保护）。
- 仓库自动创建（只暴露已有仓库）。

## Scope

### In Scope

- `nop-git-server` 模块 + bom 补声明（jgit http.server、jetty-ee10、jakarta.servlet-api 版本唯一声明点）。
- 集成测试（真实 git 客户端 + 嵌入 Jetty 随机端口）。

### Out Of Scope

- Nop IoC beans.xml（Wave 6）。

## Execution Plan

### Phase 1 - 模块与端点

Status: completed
Targets: `nop-tools/nop-git/nop-git-server`

- Item Types: `Feature`

- [x] 模块骨架 + bom 声明（审查 Blocker-1 修正：**jetty-ee11-servlet/-server 12.1.x（Servlet 6.1，对齐 jgit-parent 管理值）+ jakarta.servlet-api 6.1.0 provided**——ee10/servlet 6.0 与 jgit 7.7 编译依赖 6.1 存在 API 面错位；bom 注释记录版本来源；解压 jetty jar 核验 class version ≤ 61 与 release=21 无冲突）
- [x] `GitServerConfig`（port、root、repo 名称→路径解析规则）+ `NopGitHttpServer`（start/stop，ServletContextHandler + GitServlet，**显式设 ReceivePackFactory 使 push 可用——GitServlet 默认只服务 fetch，审查 Major-1**）+ main 入口
- [x] `RepositoryResolver`：按注册表解析仓库、`incrementOpen` 计数生命周期
- [x] `ProtectedBranchPreReceiveHook`：保护分支（main/master/release/*）reject 直推/force/删除

Exit Criteria:

- [x] HTTP 集成测试绿：clone→commit→push→服务端 sha 到达（push 走 receive-pack POST 非零 reject 面）；浏览器式 GET 智能端点 404 属正常（设计明示）
- [x] 保护分支三类拒绝测试：非 force 直推 main 拒、force push 拒、删除拒（push 非零退出，receive-pack reject 消息可见）
- [x] `ib test` 绿；`ai-dev/logs/` 已更新

### Phase 2 - 收口

Status: completed
Targets: roadmap、独立 audit

- Item Types: `Proof`

- [x] roadmap items 26-28 + Milestone 置 done；独立 closure audit；变异探针（改 hook 拒绝分支名 → 保护测试红 → 恢复）

Exit Criteria:

- [x] 全模块测试回归绿；独立 audit 证据入 Closure 段
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] items 26-28 落地且 HTTP 集成测试覆盖（端到端：git 客户端入口 → HTTP → 服务端仓库出口）
- [x] PreReceiveHook 保护面有拒绝测试
- [x] roadmap 状态块更新；design 04 §四若落地修订 reconcile
- [x] 独立 closure audit 证据写入
- [x] `ib test` 全绿；check-doc-links / check-plan-checklist / scan-hollow 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- HTTPS/鉴权、仓库自动创建、挂入 Nop 服务部署形态（Wave 6 评估项）。

## Closure

Status Note: Wave 5（roadmap items 26-28）全部落地：nop-git-server 模块（Jetty ee11 嵌入 + GitServlet + RegistryResolver + incrementOpen + 显式 ReceivePackFactory 启用 push）+ ProtectedBranchPreReceiveHook 三类拒绝；HTTP 集成测试（真实 git clone/commit/push 往返 + 三类保护拒绝）绿；变异探针红→绿。
Completed: 2026-11-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_ec09beca-7e6d-425f-a0b3-4fa93af19fde）
- Audit Session: agent_ec09beca-7e6d-425f-a0b3-4fa93af19fde
- Evidence:
  - 独立复跑 server 模块测试：2 run / 0 fail（真实 git 客户端集成）
  - 独立端到端复验（Main 入口 + 随机端口 61586 + 真实 git）：clone 成功；dev push 成功且双端 sha 一致；master 直推/force/删除均 remote rejected 且消息逐类正确
  - Anti-Hollow：显式 ReceivePackFactory、traversal 拒绝 + HEAD 校验 + incrementOpen、Hook fail-closed，无空壳
  - 关键事实复核：core pom 已为 dependencyManagement import；dependency:list 实际解析 jgit 7.7.1；bom 唯一声明点成立（jetty 12.1.9 / servlet 6.1.0）
  - 一致性：roadmap items 26-28 + Milestone done；daily log；check-plan-checklist --strict exit 0；check-doc-links exit 0
  - 实现期变异探针：protected branch→guarded branch → 集成测试红（1F）→ 恢复绿
  - Deferred 检查：无鉴权/HTTPS/自动创建均为 Non-Goals 明示，无降级
