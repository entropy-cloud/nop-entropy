# 2434 WI7 mock 设施收敛下沉（core main → core test scope + 死能力删除）

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-ai-structure-refactor-roadmap.md（WI7）
> Related: 2414（WI1）；deps: WI1（已完成）

## Purpose

把 test-only 的 core/mock 设施（714 行）从 main artifact 下沉到 core test scope：main 零消费零装配已实证，无「必须留 main」约束；顺带删除零使用的 FileSystemResponseProvider 死能力，并处置唯一跨模块消费者（coder 的 1 个测试）。

## Current Baseline

（2026-10-11 执行时 HEAD 实测，Explore 子代理普查。）

- **core/mock/ 6 文件 714 行**：MockChatService 170（implements IChatService，无参 ctor + @Inject setter，无 bean 定义）、FileSystemResponseProvider 217（implements IResponseProvider+IRequestStore，record/replay 文件轮询 + 2 小时超时）、InMemoryResponseProvider 214、IRequestStore 33（**唯一实现者与唯一消费者都是 FileSystemResponseProvider**）、IResponseProvider 30、MockChatConfigs 50（6 键）。
- **消费者普查**：main 全仓 0 引用、0 装配（ai-defaults.beans.xml grep mock 零命中）；test 3 文件——core `TestNopAiCoreErrorsContract.java`（MockChatService 触发 INVALID_STATE 契约 + :37-39 行号锚点注释）、core `TestAiCommandExecute.java`（MockChatService+InMemoryResponseProvider，WI1 新增）、**跨模块** coder `AiTaskStepExecuteWiringTest.java`（L7-8/L57-58，MockChatService+InMemoryResponseProvider——**方案 A 的唯一阻断点**）。
- **死能力实证**：FileSystemResponseProvider 的 record/replay 全仓 0 实例化；其 4 个配置键（mock.dir/eof-marker/poll-interval-ms/timeout-hours）全仓仅自身定义、无任何测试/配置设置。MockChatConfigs 实际被读到的只有 enable-stream/stream-delay-ms（MockChatService.callStream，core 测试走 callAsync 连这个也没触发）。
- **名字撞车甄别**：agent **8 个测试文件**触及该名字（1 个私有类 TestLlmCompletionJudge:71 + 7 个 createMockChatService 匿名 stub helper），零 import core mock；datav 4 个私有类（TestNopDatavChatBi* 系列）同样私有、零 import——均不构成消费。
- **coder 阻断点处置基础**：AiTaskStepExecuteWiringTest 只需 callAsync 返回固定响应（`ChatResponse.success(new ChatAssistantMessage("echo: hi"))`）——可改为内联匿名 IChatService stub（**匿名内部类**先例——IChatService 有 callAsync+callStream 两抽象方法非函数式接口，参照 agent TestSingleTurnExecutor:69 createMockChatService），删除 MockChatService/InMemoryResponseProvider 依赖。
- **test scope 可行性**：mock 包依赖（nop-ai-api/nop-api-core/nop-commons）均为 core main 依赖，移入 test 无新增依赖；core test 消费者同 FQN（io.nop.ai.core.mock）原地编译。

## Goals

- core main artifact 减约 714 行 test-only 代码：mock 全族（**4 移 2 删**，裁剪后 4 文件约 440 行）移入 `core/src/test/java/io/nop/ai/core/mock/`（FQN 不变，core 内 2 测试零 import 改动）。
- 删除 FileSystemResponseProvider（record/replay 死能力，0 使用实证）+ IRequestStore（唯一实现者随删）+ MockChatConfigs 的 4 个死配置键（保留 enable-stream/stream-delay-ms）。**roadmap delta 登记**：roadmap 原列 FileSystemResponseProvider 为「下沉 test scope 或 testkit」对象，按 0 实例化/0 装配/0 配置三重实证改判删除。
- coder AiTaskStepExecuteWiringTest 改内联 IChatService stub（去 mock 依赖）。
- TestNopAiCoreErrorsContract 的 mock 行号锚点注释同步。
- datav/agent 私有 mock 收敛登记为 follow-up（datav 需引 core test-scope 依赖不可见；agent 私有 stub 自包含——非本 WI 阻断面）。

## Non-Goals

- 不建独立 nop-ai-testkit 模块（消费面仅 3 文件，建模块收益不抵维护成本——登记重启条件：datav/agent 私有 mock 实际收敛时重开）。
- 不收敛 datav 4 个私有 MockChatService 与 agent 本地 stub（自包含、跨模块 test 不可见）。
- 不给 MockChatService 补 List<ChatResponse> ctor（datav 收敛时再议）。

## Scope

### In Scope

- nop-ai-core：6 文件移动（5 移 + 1 删）+ MockChatConfigs 裁剪 + 测试注释同步。
- nop-ai-coder：AiTaskStepExecuteWiringTest 去 mock 依赖改内联 stub。

### Out Of Scope

- nop-datav/nop-ai-agent 私有 mock 收敛、nop-ai-testkit 模块、MockChatService API 扩展。

## Execution Plan

### Phase 1 - 下沉与死能力删除

Status: completed
Targets: `nop-ai/nop-ai-core/src/{main,test}/java/io/nop/ai/core/mock/`、`nop-ai/nop-ai-coder/src/test/java/io/nop/ai/coder/AiTaskStepExecuteWiringTest.java`

- Item Types: `Fix`

- [x] 删除 FileSystemResponseProvider.java + IRequestStore.java（record/replay 死能力，0 使用实证留档于本 plan Baseline）
- [x] git mv MockChatService/InMemoryResponseProvider/IResponseProvider/MockChatConfigs 至 `core/src/test/java/io/nop/ai/core/mock/`（FQN 不变）；MockChatConfigs 裁剪为 enable-stream/stream-delay-ms 2 键
- [x] coder `AiTaskStepExecuteWiringTest`：MockChatService+InMemoryResponseProvider 替换为内联 IChatService stub（callAsync 返回 `ChatResponse.success(new ChatAssistantMessage("echo: hi"))`，断言 provider 不变），删 2 个 import
- [x] TestNopAiCoreErrorsContract :37-39 mock 清单注释同步：**删除** `mock/FileSystemResponseProvider.java:91` 条目（文件被删；git mv 不改变保留文件行号——round 1 审查勘误）
- [x] 编译 + 测试：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-coder -am`

Exit Criteria:

- [x] core main 的 mock/ 目录不存在；core test 的 mock/ 含 4 文件（MockChatService/InMemoryResponseProvider/IResponseProvider/MockChatConfigs）
- [x] MockChatConfigs 仅 2 键；FileSystemResponseProvider/IRequestStore 全仓零命中（历史 docs 除外）
- [x] core/coder 测试全绿（TestAiCommandExecute 3 例与 AiTaskStepExecuteWiringTest 2 例为行为钉）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 文档登记

Status: completed
Targets: `docs-for-ai/03-modules/nop-ai.md`

- Item Types: `Proof`

- [x] mock 设施定位登记（如该文档有 mock 表述则同步：mock 族现位于 core test scope，无 main 装配；FileSystemResponseProvider 死能力删除；testkit 重启条件）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] doc 更新在位（或明确 no-change 登记）
- [x] check-doc-links --strict 0 error

## Closure Gates

- [x] core main mock/ 目录清空（**4 移 2 删**完成）；MockChatConfigs 2 键；FileSystemResponseProvider/IRequestStore 零命中
- [x] coder 测试改内联 stub 且 2 例通过
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-coder -am` 全绿
- [x] 不存在被静默降级的 in-scope live defect；FileSystemResponseProvider 删除为死能力裁定（0 使用实证）非静默降级
- [x] owner doc 登记完成（或 no-change 登记）
- [x] 独立子 agent closure-audit 已完成并记录证据（11/11 PASS APPROVE）
- [x] Anti-Hollow Check：TestAiCommandExecute 与 AiTaskStepExecuteWiringTest 的行为断言不变（**stub 返回同内容 + coder 测试的请求 provider 断言保留**——round 1 审查：该断言是 xlib chatOptions→ChatOptions 绑定的唯一验证点，不得静默消失）
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-coder -am`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-core --severity high` 无本次新增 high 发现
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2434-wi7-mock-sink-to-test.md --strict` 退出码 0（自指验证）

## Deferred But Adjudicated

### nop-ai-testkit 独立模块

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前消费面仅 3 文件（core 2 + coder 1），coder 已改内联 stub 后仅剩 core 内部——建模块维护成本大于收益。重启条件：datav/agent 私有 mock 实际启动收敛时。
- Successor Required: `no`
- Successor Path: 无

### datav 4 个私有 MockChatService 与 agent 本地 stub 收敛

- Classification: `watch-only residual`
- Why Not Blocking Closure: 自包含（零 import core mock），跨模块 test scope 不可见；收敛需 datav 引 core test 依赖（不可见）或 testkit 落地。
- Successor Required: `no`
- Successor Path: 随 testkit 重启条件一并评估

## Non-Blocking Follow-ups

- MockChatService 流式分支（callStream）在 core 测试中零覆盖——如需覆盖随 datav/agent 收敛一并考虑。

## Closure

Status Note: WI7 两 Phase 完成：mock 族 4 移 2 删（FileSystemResponseProvider record/replay 死能力 + IRequestStore 随删，三重实证 0 实例化/0 装配/0 配置）；MockChatConfigs 裁剪 2 键；coder 跨模块消费者改内联 IChatService stub（行为断言等价 + provider 绑定断言保留）；core main mock/ 清空。closure audit 11/11 PASS。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit，2026-10-11；HEAD 7c90b5b904）
- Evidence:
  - 11/11 项 PASS：main mock/ ABSENT、test mock/ 恰 4 文件、FileSystemResponseProvider/IRequestStore src 零命中、MockChatConfigs 2 键、coder 内联 stub（provider 断言保留 + callStream fail-loud）、Contract 清单同步、callStream 2 键未破坏、测试绿留档（core 399/coder 26）、roadmap delta 登记、doc-links 0、log 条目
  - scan-hollow（nop-ai-core, severity high）exit 1 = 既存 NoOpProviderFailoverQueue:34（2026-08-15，WI12 范围）非本次新增
  - 2 条非阻塞流程观察（scoped commit / roadmap 状态翻转）已随签核执行

Follow-up:

- datav/agent 私有 mock 收敛随 testkit 重启条件评估（Deferred 已登记）
- MockChatService.callStream 零测试覆盖（Non-Blocking Follow-ups 已登记）
