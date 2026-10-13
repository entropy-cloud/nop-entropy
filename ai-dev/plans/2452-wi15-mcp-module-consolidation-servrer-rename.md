# 2452 WI15 MCP 三模块整合与 servrer 更名

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI15；MCP 模块普查 = 2026-10-13 live HEAD（plan 2446 后）
> Related: WI2（completed，`04-tool-invocation.md` §WI2 裁定 BizModel 外部面维持 + Simplifier 依赖随 WI15 解除；tools 归属 = roadmap "toolkit/tools" 菜单 + FileToolBizModel 先例推定）；WI9/plan 2438——NopAiToolsErrors 先例；WI14/plan 2446——consumer-census 方法先例
> Draft Review: 2026-10-13 独立子代理对抗审查 round 1（agent_5f15d327）——1 Blocker（VFS bean 注册 2 资源不在 plan：bean FQN 迁移 + autoconfig 随迁 + Rule #23 注册验证缺失，按原稿执行 = 全 gate 绿但 MCP 出口运行时断裂的 hollow）+ 4 Major（simplifier 测试去向与零残留 EC 互斥 / spring-ai 依赖缺项 / owner-doc 面不全违 Rule #17 / servrer grep 未限范围）+ 5 Minor，全部修入正文（A1 扩常量与测试同迁、B1 注册资源随迁 + 断言、C3-C5 修正）后转 active
> Deviation: roadmap 验证命令含 `nop-ai-toolkit`——实测 toolkit 与迁移面零依赖（plan 执行不含 toolkit 改动），保留命令但登记零关联；模块数 before/after 登记见 Phase 2 收口项

## Purpose

MCP 三模块整合：①`AiFileTool` 按 WI2 裁定迁入 nop-ai-tools（解除 nop-ai-mcp-server 对 coder 的 compile 依赖——其唯一 coder 用面 = 2 个 Simplifier 类，随迁 nop-ai-core 共享层）；②`nop-spring-mcp-server-support`（spring-ai adapter 2 文件）并入 `nop-spring-mcp-server`（SpringBoot 启动器）；③修正 `io/nop/spring/mcp/servrer/` 拼写错误 → `server`。模块数 3→1（mcp-server 解散 + support 并入）。

## Current Baseline

> 2026-10-13 live HEAD 实测。

- **nop-ai-mcp-server**（3 java + 2 VFS 资源）：`AiFileTool.java`（234 行，`@BizModel(McpConstants.BIZ_OBJ_AI_TOOL)` BizModel 工具——WI2 裁定的 BizModel 适配器归属 = nop-ai-tools）+ `McpServerErrors.java`（22 行，`nop.err.mcp.*` 错误码）+ test `TestAiFileTool.java`（20 @Test，直 new 不依赖 beans.xml）+ **2 个 VFS 注册资源**——`_vfs/nop/ai/beans/ai-mcp-server-defaults.beans.xml`（`<bean id="nopAiFileTool" class="io.nop.ai.mcp.server.AiFileTool"/>`）+ `_vfs/nop/autoconfig/ai-mcp-server.beans`（指向前者）：**运行时接线 = McpServerConfiguration → GraphQLToolCallbackProvider.getBizObject("AiTool") 依赖该 bean 存在**，迁移必须连资源一起走并做注册验证，否则 spring-mcp-server 的 MCP 工具出口静默断裂（hollow）。coder 依赖 = 仅 simplifier 两处 import。模块无其他消费者（spring-mcp-server 依赖它即为此 bean + AiFileTool）。
- **Simplifier 迁移对象**：`io.nop.ai.coder.simplifier.JsonSimplifier`（88 行）+ `XNodeSimplifier`（86 行）——通用 JSON/XNode 字段过滤工具，coder 包引用仅 `AiCoderConstants.DEFAULT_POSITIONING_KEYS` 单常量（:15 `Set.of("id","name","type")`，全仓消费者仅此两类——**整体迁走，AiCoderConstants 删除该常量**）；**main 消费者 = 仅 AiFileTool**（coder main 0 消费者，与 2026-09-14 日志互证）；test 消费者 = 同包 2 测试类（JsonSimplifierTest/XNodeSimplifierTest，随类同迁 core test）。
- **nop-spring-mcp-server**：`SpringMCPServerMain.java`（13 行 SpringBoot 启动器，包 `io.nop.spring.mcp.servrer`——拼写错误）+ pom 依赖 mcp-server/support/spring-web-starter；**零测试**。
- **nop-spring-mcp-server-support**：`GraphQLToolCallbackProvider.java` + `McpServerConfiguration.java`（spring-ai adapter，包 `io.nop.spring.mcp.server`）；**零测试、零外部 java 消费者**（spring-mcp-server 之外）。
- **消费者**：`nop-spring-mcp-server` pom 依赖 mcp-server + support；`nop-bom` 管理两 artifact（:933 support、:1426 mcp-server）；nop-ai-web 的 mcp 字样仅为 test-scope 注释非依赖。
- module-groups/docs 提及：nop-spring-mcp-server* 独立发布周期（module-groups :17）。

## Goals

- `AiFileTool` + `McpServerErrors` + `TestAiFileTool` 迁入 nop-ai-tools（包 `io.nop.ai.tools.mcp`）；`JsonSimplifier`/`XNodeSimplifier`/`DEFAULT_POSITIONING_KEYS` 迁入 nop-ai-core（新子包 `io.nop.ai.core.simplifier`）——coder/tools 双向可见，coder 依赖解除。
- `nop-ai-mcp-server` 模块解散（nop-ai/pom.xml 模块表 + nop-bom 条目移除）。
- support 2 文件并入 spring-mcp-server（同包 `io.nop.spring.mcp.server`）；support 模块解散（pom + bom 条目移除）。
- `servrer` → `server` 包更名（SpringMCPServerMain 与并入文件同包落位）。
- 模块计数 nop-ai 3→1（MCP 相关）；全仓旧包名/artifact 零残留（src + bom + docs 活性引用）。

## Non-Goals

- 不改 MCP 工具语义、spring-ai 集成语义、错误码 ID（`nop.err.mcp.*` 保留）。
- 不动 nop-spring-mcp-server 的 SpringBoot 应用配置（application.yaml 等）。
- 不处理 roadmap 之外的其他 nop-ai 模块（WI16/17 scope）。

## Adjudications

### A1 Simplifier 落点 = nop-ai-core（`io.nop.ai.core.simplifier` 新子包）

- AiFileTool（BizModel）的家 = nop-ai-tools（WI2 裁定）；tools 不依赖 coder（M5-P1 裁定解除过的分层倒置不可回加）。Simplifier 为通用字段过滤工具（无 LLM/coder 业务语义），唯一共享锚点常量 `DEFAULT_POSITIONING_KEYS` 随迁。core 是 coder 与 tools 的公共祖先（二者均依赖 core），单次迁移双向可见。coder 侧 4 处引用同步 import。
- 常量处置：`DEFAULT_POSITIONING_KEYS` 整体从 AiCoderConstants 迁至 core 新位置（全仓消费者仅两类 simplifier，coder 侧零残留）。
- 测试随迁：2 个 simplifier 测试类同迁 core test 同包（coder 零 simplifier 残留）。
- 备选否决：迁 toolkit（tools 不依赖 toolkit）；留在 coder（依赖倒置复现）。

### A2 mcp-server 解散而非留壳

- 迁出 3 文件后模块空壳（零 main 零 test）；留壳即死模块（WI16 同类对象）。nop-bom 条目同步移除（artifact 不再存在）。spring-mcp-server 的依赖切换为 nop-ai-tools（AiFileTool 新家）。

## Execution Plan

### Phase 1 - Simplifier 迁移 + AiFileTool 迁入 tools + 模块解散

Status: completed
Targets: nop-ai-core（新 simplifier 包）、nop-ai-coder（引用更新）、nop-ai-tools（mcp 包新建）、nop-ai-mcp-server（解散）、nop-spring-mcp-server（依赖切换）、nop-ai/pom.xml、nop-bom/pom.xml

- Item Types: `Fix`

- [x] `git mv` JsonSimplifier/XNodeSimplifier + 2 测试类 → `nop-ai-core/src/{main,test}/java/io/nop/ai/core/simplifier/`，包声明更新；`DEFAULT_POSITIONING_KEYS` 自 AiCoderConstants 整体迁至 simplifier 包内常量（coder 删除原定义）；coder 全部 import 更新
- [x] `git mv` AiFileTool/McpServerErrors → `nop-ai-tools/src/main/java/io/nop/ai/tools/mcp/`，包声明更新；`TestAiFileTool` → tools test 同包；imports 更新（simplifier 新坐标）
- [x] **VFS 注册资源随迁（B1）**：`ai-mcp-server-defaults.beans.xml` 的 bean FQN 改 `io.nop.ai.tools.mcp.AiFileTool` 后迁入 tools `_vfs/nop/ai/beans/`（并入或同名共存，以 NopIoC 装载路径为准）；`autoconfig/ai-mcp-server.beans` 迁入 tools `_vfs/nop/autoconfig/`；**注册验证（Rule #23）**：TestAiFileTool 增加容器装配断言（仿 tools TestFileToolBizRegistration——bean `nopAiFileTool` 可解析且 biz 对象 `AiTool` 注册成功）
- [x] 解散 nop-ai-mcp-server：nop-ai/pom.xml 模块表移除、nop-bom :1426 条目移除、目录删除；spring-mcp-server pom 依赖 `nop-ai-mcp-server` → `nop-ai-tools`
- [x] `./mvnw test-compile -pl nop-ai/nop-ai-core,nop-ai/nop-ai-coder,nop-ai/nop-ai-tools -o -q` 编译门

Exit Criteria:

- [x] grep `io.nop.ai.coder.simplifier` 在 nop-ai 全源零残留（类与测试同迁 core）；grep `io.nop.ai.mcp.server` 零残留
- [x] 编译门通过；core simplifier 测试绿（行为基线随迁）+ tools TestAiFileTool 含 bean 注册断言绿
- [x] No new test required：纯迁移 + 注册断言增强；coder simplifier 测试随迁 core、TestAiFileTool 19 用例为行为基线（guide Rule #25 注记）
- [x] owner-doc 更新落 Phase 2（module-groups MCP 行 / nop-ai.md 模块表 / error-handling.md McpServerErrors 归属），Phase 1 为纯迁移 + VFS 接线
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - support 并入 + servrer 更名 + 收口

Status: completed
Targets: nop-spring-mcp-server、roadmap、log、plan closure

- Item Types: `Fix`

- [x] `git mv` support 2 文件 → `nop-spring-mcp-server/src/main/java/io/nop/spring/mcp/server/`；`SpringMCPServerMain` 同包落位（servrer 目录消除）；support 模块解散（nop-ai/pom.xml + nop-bom :933 + spring-mcp-server pom 的 support 依赖移除）；spring-mcp-server pom **新增 `spring-ai-starter-mcp-server-webmvc`**（spring-ai-bom 2.0.1 管理，免版本号——C3）
- [x] `servrer` 拼写零残留（口径：nop-ai/**/src + 活性 docs——ai-dev 历史记录与 plan 自身文件名按 Rule #20 豁免；`.settings` 实测无引用）
- [x] `./mvnw test -pl nop-ai/nop-spring-mcp-server -am` 全绿
- [x] roadmap `## Work Item Status` WI15 → `done`；状态块解析核对
- [x] 模块计数 before/after 登记（MCP 相关 3→1）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2452-wi15-mcp-module-consolidation-servrer-rename.md --strict` 0
- [x] pathspec 限定单一原子提交（Phase 1+2 同 commit——模块解散的中间态不可编译）

Exit Criteria:

- [x] grep `servrer` 零残留；`nop-spring-mcp-server-support` artifact 零引用
- [x] roadmap 更新后解析核对通过（21 行 WI 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：迁移摘要、验证、A1/A2、commit hash、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] MCP 模块 3→1（mcp-server 解散、support 并入）；nop-bom/pom 条目零残留
- [x] AiFileTool 在 nop-ai-tools 且 coder 依赖解除（mcp/tools 无 coder import）
- [x] Simplifier 在 nop-ai-core 且 coder/tools 双向引用编译通过
- [x] servrer 拼写零残留
- [x] 行为零变化：coder + tools + spring-mcp-server 测试绿
- [x] 不存在被静默降级的 in-scope live defect（A1/A2 为显式裁定）
- [x] owner docs 已裁定：module-groups MCP 模块行更新（3→1）
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `./mvnw test -pl nop-ai/nop-spring-mcp-server -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2452-wi15-mcp-module-consolidation-servrer-rename.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- ~~module-groups `nop-spring-mcp-server*` 独立发布周期表述随合并更新~~（已完成——Phase 2 随 owner doc 同步落地）。

## Closure

Status Note: MCP 三模块整合落地：nop-ai-mcp-server 解散（AiFileTool/McpServerErrors/TestAiFileTool + VFS bean 注册资源迁 nop-ai-tools，coder 依赖解除），support 并入 nop-spring-mcp-server（spring-ai starter 显式加入），servrer→server 正名；Simplifier 2 类+常量+2 测试迁 nop-ai-core simplifier 包（coder 零残留）；MCP 模块 3→1；Rule #23 注册断言（TestAiFileToolBizRegistration 2/2）+ 全量 3536 级绿；owner docs 三处同步。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（agent_6be72154，fresh session，2026-10-13）
- Evidence:
  - 10/10 项 PASS：模块解散（tracked 源 100% 解除、pom/bom 零残留）；AiFileTool 在 tools 且 coder 零 import；Simplifier 在 core（SimplifierConstants 唯一定义点）；VFS 注册资源 FQN 正确 + IocConstants.VFS_PATH_AUTOCONFIG 装载链确认；spring-mcp-server pom（tools + spring-ai starter 免版本）3 java 全落 io.nop.spring.mcp.server；servrer 按口径零残留；现场重跑注册测试 22/22 绿；文本一致；owner docs 三处在位；Deferred 无静默降级
  - Anti-hollow 运行时链实证：autoconfig → bean nopAiFileTool → @BizModel("AiTool") → GraphQLToolCallbackProvider.getBizObject("AiTool")
  - Minor housekeeping（已顺手处理）：nop-ai.md 模块表第一列回填、@Test 计数 20、follow-up 标记完成
  - 裁定：**APPROVE closure**
- `node ai-dev/tools/check-plan-checklist.mjs 2452-wi15-mcp-module-consolidation-servrer-rename.md --strict` 退出码 0（closure 前末次确认）
