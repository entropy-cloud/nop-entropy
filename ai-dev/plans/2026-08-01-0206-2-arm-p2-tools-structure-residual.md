# 2 arm-p2-tools-structure-residual — nop-ai-tools / nop-ai-rag 结构残余（GraphQL bean 条件注册 + ThoughtStorage 裁定 + rag 空模块裁定）

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: `ai-dev/audits/2026-07-31-2200-arm-MA1.3-nop-ai-toolkit.md`（P3-MA1-013/016）+ `ai-dev/audits/2026-07-31-0753-arm-MA3.1-nop-ai-cross-module-deps.md`（P3-MA3-003）+ `ai-dev/audits/arm-index.md`
> Mission: audit-remediation
> Work Item: MA1.3-P3-013/016 + MA3.1-P3-003（第九批 deferred successor）
> Related: `2026-08-01-0206-1-arm-p2-skills-code-analyzer-structure.md`（顺序执行，本计划独立）

## Purpose

收口 nop-ai-tools / nop-ai-rag 剩余的结构类 P3 项：GraphQLToolSetFactoryBean 无条件 bean 注册（P3-MA1-016，非 GraphQL 环境装配风险）、ThoughtStorage 文件持久化方案裁定（P3-MA1-013）、nop-ai-rag 空模块裁定（P3-MA3-003）。这些项在 `2026-07-31-1834-3` Non-Blocking Follow-ups / `2026-07-31-2248-1` 登记为"后续批次"，本计划承接。

## Current Baseline

- **P3-MA1-016 live（防御性硬化项，非仓库内可复现 defect）**：`nop-ai/nop-ai-tools/src/main/resources/_vfs/nop/ai/beans/ai-tools-defaults.beans.xml:11` 注册 `nopGraphQLToolSet`（GraphQLToolSetFactoryBean），无任何条件。该 bean `@Inject IGraphQLEngine`（setter 注入 :27-30）。仓库内 nop-ai-tools 对 nop-biz 是 compile 依赖，nop-biz `biz-defaults.beans.xml:6` 无条件注册 `nopGraphQLEngine`，故仓库内部署不缺该 bean——本项是防御性硬化：保护排除 nop-biz / 自定义 beans 集的消费方（如 deepwiki task XML `ai:toolSet="nopGraphQLToolSet"` 的装配面）。
- **Nop IoC 条件注册语法（live 核实）**：`ioc:condition` 是 `<bean>` 的**子元素**（`beans.xdef:155` BeanConditionModel），不是属性；条件子元素为 `<on-bean>` / `<missing-bean>`（`missing-bean` 值为 bean 名集合，语义为"该 bean 存在时禁用本 bean"）。仓库实例：`rpc-cluster-defaults.beans.xml:79-83`、`biz-defaults.beans.xml:42-44`。GraphQLEngine 的注册 id 是 **`nopGraphQLEngine`**（非接口名 `IGraphQLEngine`）。**正确写法**：`<ioc:condition><on-bean>nopGraphQLEngine</on-bean></ioc:condition>`（引擎存在才注册本 bean）。audit MA1.3:163 建议的 `ioc:condition="!missing-bean:IGraphQLEngine"` 语法在 Nop IoC 中不存在（xdef 校验会失败），不可引用为基线。
- **P3-MA1-013 live**：`nop-ai/nop-ai-tools/.../sequential_thinking/service/ThoughtStorage.java` 使用 JSON 文件持久化（默认 `~/.mcp_sequential_thinking/`；beans.xml 配置 `nop.ai.sequential-thinking-tool.storage-dir-path` 默认 `/nop/ai/sequential-thinking/store`——该值是**文件系统绝对路径**（`FileHelper.resolveFile` 对 `/` 开头路径直接 `new File(path)`），非 VFS 路径，普通用户无写权限，默认配置实际不可写）。每次操作全量读/写（loadSession/saveSession）。
- **P3-MA3-003 live**：`nop-ai/nop-ai-rag/pom.xml` 仅有 parent 与 artifactId，0 个 Java 文件、0 依赖、0 资源。`nop-ai/pom.xml:35` modules 引用它；全仓库无其他模块依赖 nop-ai-rag。MA5.1/MA5.2 audit 将其排除在扫描外（"IVectorStore/IEmbeddingModel 预期实现模块"）。P1-MA5-003 已裁定 IVectorStore/IEmbeddingModel 为 SPI 扩展点契约（`arm-index.md:199`）。
- 既有测试：nop-ai-tools/toolkit 测试全绿（SearchEngineExecutorTest 8 例、SkillExecutorTest/VfsTest 等）。

## Goals

- GraphQLToolSetFactoryBean 在无 `nopGraphQLEngine` bean 的装配上下文中不再无条件实例化（Nop IoC 语法正确的条件注册）。
- ThoughtStorage 持久化方案裁定落盘（保持文件持久化 + 默认路径修正 + 会话级定位文档化，或迁移 ORM——附理由与证据）。
- nop-ai-rag 空模块裁定落盘（保留 + 文档化 / 从父 pom 移除 / 补最小实现——附理由，与 P1-MA5-003 SPI 裁定兼容）。
- 全量测试保持绿色。

## Non-Goals

- 不实现 nop-ai-rag 的 IVectorStore/IEmbeddingModel 生产实现（P1-MA5-003 已裁定为 SPI 扩展点契约，不重开）。
- 不迁移 SequentialThinkingBizModel 到 ORM（除非裁定结论为必须，此时仅评估建模路径，不落实现）。
- 不处理 nop-ai-skills 结构项（计划 1 承接）。

## Scope

### In Scope

- `nop-ai/nop-ai-tools/src/main/resources/_vfs/nop/ai/beans/ai-tools-defaults.beans.xml`：nopGraphQLToolSet 条件注册。
- `nop-ai/nop-ai-tools/.../sequential_thinking/`：ThoughtStorage 裁定 + 默认路径修正（若裁定保持）。
- `nop-ai-rag/`：空模块裁定 + 文档。
- 相关测试（条件注册的模型级验证 + 接线验证；ThoughtStorage 若改动）。

### Out Of Scope

- IVectorStore/IEmbeddingModel 实现（SPI 裁定不重开）。
- nop-ai-skills 结构治理（计划 1）。
- 测试质量 P3 残余（计划 3）。

## Execution Plan

### Phase 1 - GraphQLToolSetFactoryBean 条件注册（P3-MA1-016）

Status: completed
Targets: `nop-ai/nop-ai-tools/src/main/resources/_vfs/nop/ai/beans/ai-tools-defaults.beans.xml` + `nop-ai/nop-ai-tools/src/test/`

- Item Types: `Fix | Proof`

- [x] 为 nopGraphQLToolSet bean 增加子元素 `<ioc:condition><on-bean>nopGraphQLEngine</on-bean></ioc:condition>`（bean 名匹配，非接口名）
- [x] 语法验证：beans 模型解析测试（BeanModel 解析含条件子元素不报 xdef 错误）
- [x] 接线验证：nop-ai-tools 新增轻量容器测试（pom 加 `nop-autotest-junit` test 依赖；nop-biz 已是 compile 依赖 → classpath 含 `nopGraphQLEngine`），断言容器解析 `ai-tools-defaults.beans.xml` 且 `nopGraphQLToolSet` bean 可解析/注入；deepwiki task XML `ai:toolSet="nopGraphQLToolSet"` 消费面不受影响（`TestDeepWikiPrompts` 已 `@Inject @Named("nopGraphQLToolSet")`，为既有注入验证点，@Disabled 状态不改变——不作为唯一载体，仅作消费面证据）
- [x] 条件语义验证：BeanConditionEvaluator 对 `<on-bean>` 的匹配（engine 存在 → 条件满足 → bean 注册）与仓库既有实例（rpc-cluster-defaults/biz-defaults）一致性核对——通过模型级测试断言
- [x] 移除条件后行为验证（防御性）：构建自定义无 `nopGraphQLEngine` 的 bean 集（BeansDefinition 显式资源）验证容器装配不失败——若自定义装配在测试框架内不可行，改用模型级验证 + 接线验证组合，并落盘说明

Exit Criteria:

- [x] 条件注册语法与 Nop IoC 语义一致（`beans.xdef:155` BeanConditionModel + 仓库实例核对）
- [x] **接线验证**：有 `nopGraphQLEngine` 场景下 nopGraphQLToolSet 正常注册——载体为 nop-ai-tools 新增轻量容器测试（autotest 容器，断言 bean 可解析/注入），非 NopAiWebPagesTest（其依赖面不含 nop-ai-tools）
- [x] 无 `nopGraphQLEngine` 场景（若可构造）装配不失败；不可构造则模型级验证 + 理由落盘
- [x] `./mvnw test -pl nop-ai/nop-ai-tools -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ThoughtStorage 持久化方案裁定（P3-MA1-013）

Status: completed
Targets: `nop-ai/nop-ai-tools/.../sequential_thinking/` + `ai-dev/design/nop-ai/`

- Item Types: `Decision | Proof`

- [x] 评估 ThoughtStorage 使用面：调用方（SequentialThinkingBizModel）、数据量级、并发/事务需求、部署形态（框架模块 vs 应用）
- [x] 评估默认存储路径可用性：`/nop/ai/sequential-thinking/store` 为绝对路径且默认不可写（live 证据：`FileHelper.resolveFile` 对绝对路径直接 new File）——作为裁定输入
- [x] 裁定：a) 保持文件持久化（会话级工具，设计意图文档化 + 默认路径改为可写相对路径或 `./_tmp`）；b) 迁移 ORM 实体——附理由与拒绝替代方案
- [x] 裁定落盘（`ai-dev/design/nop-ai/` 或类 javadoc + `docs-for-ai/03-modules/nop-ai.md` 如涉及）；若 a 且改默认路径：改 beans.xml 默认值 + 相关测试

Exit Criteria:

- [x] 裁定记录落盘，含 Why（评估证据：使用面/量级/并发/默认路径不可用事实）
- [x] 若裁定为保持：默认路径已修正为可写（或显式裁定保持 + 不可用理由），并新增 `ThoughtStorage` 直接测试（当前零测试——如 `TestThoughtStorage`：addThought 持久化到配置目录 / 路径解析）；若裁定为迁移：建模评估落盘 + 迁移列入 successor（不落实现）
- [x] `./mvnw test -pl nop-ai/nop-ai-tools -am` 通过（如改动）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - nop-ai-rag 空模块裁定（P3-MA3-003）

Status: completed
Targets: `nop-ai/nop-ai-rag/` + `nop-ai/pom.xml` + `docs-for-ai/`

- Item Types: `Decision | Proof`

- [x] 核对 nop-ai-rag 引用面（`nop-ai/pom.xml:35` modules；无其他依赖方）与 SPI 定位（P1-MA5-003：IVectorStore/IEmbeddingModel 为 SPI 扩展点契约）
- [x] 裁定：a) 保留空模块 + 文档化为 SPI 预期实现落点（README/design doc）；b) 从父 pom 移除该模块（减少空模块噪音）；c) 补最小 InMemory 实现——附理由
- [x] 裁定落盘（`ai-dev/design/nop-ai/` 或模块 README + `docs-for-ai/` 同步）
- [x] 更新 arm-index：新增"第九批"P3 追踪小节并登记 P3-MA1-013/016、P3-MA3-003 三行（当前不存在对应行）

Exit Criteria:

- [x] 裁定记录落盘（含 Why 与对 P1-MA5-003 SPI 裁定的兼容性说明）
- [x] 若选 b：父 pom modules 更新 + `./mvnw clean install -DskipTests -pl nop-ai -am` 通过；若选 a/c：文档或最小实现 + 构建通过
- [x] **接线验证**（若选 c）：nop-ai-rag 自身容器测试断言 InMemory bean 注册 + SPI 契约（`ioc:type` 或 `IVectorStore`/`IEmbeddingModel` 契约别名装配）——因 nop-ai-rag 无外部依赖方，载体为模块内自测；若选 a/b：无代码变更，文档同步
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选后，才能将 Plan Status 改为 completed。

- [x] P3-MA1-016 条件注册落地（Nop IoC 正确语法）并有模型级 + 接线验证
- [x] P3-MA1-013 裁定落盘（保持或迁移，附证据含默认路径可用性）
- [x] P3-MA3-003 裁定落盘（保留/移除/实现，附证据，与 SPI 裁定兼容）
- [x] 所有裁定项无 in-scope live defect 被静默降级
- [x] 受影响的 owner docs 已同步（`ai-dev/design/nop-ai/` / `docs-for-ai/03-modules/nop-ai.md`），或明确 No owner-doc update required
- [x] `ai-dev/audits/arm-index.md` 新增第九批追踪小节（P3-MA1-013/016、P3-MA3-003 三行）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证无空壳/静默跳过（尤其若新增任何实现代码或条件注册失效）
- [x] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [x] `./mvnw test -pl nop-ai -am -T 1C`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0

## Deferred But Adjudicated

### nop-ai-rag 若裁定为"保留空模块 + InMemory 实现属未来"

- Classification: `watch-only residual`
- Why Not Blocking Closure: P1-MA5-003 已裁定 IVectorStore/IEmbeddingModel 为 SPI 扩展点契约，无生产实现属设计意图；空模块不阻塞任何消费方（无依赖方）。
- Successor Required: `no`

### "无 IGraphQLEngine 上下文"端到端测试若不可构造

- Classification: `watch-only residual`
- Why Not Blocking Closure: nop-ai-tools 测试 classpath 必然含 nop-biz（compile 依赖）→ `nopGraphQLEngine` 恒存在，无-引擎容器测试在既有测试框架内不可构造；以模型级验证 + 有引擎接线验证组合替代，理由落盘。
- Successor Required: `no`

## Non-Blocking Follow-ups

- P3-MA1-014（code-analyzer 模块职责）由计划 1 Phase 3 承接。
- P3-MA1-023~030（MA1.4 infra P3）已裁定 watch-only，不入本计划。

## Closure

Status Note: 三个 P3 finding 全部收口——P3-MA1-016 条件注册落地（Nop IoC 正确语法 + 模型级/接线/防御性三重验证）；P3-MA1-013 裁定保持文件持久化 + 默认路径修正，测试暴露 2 个 live defect（ThoughtSession 缺 @DataBean、Instant 无 JsonTool 支持）就地修复；P3-MA3-003 裁定保留空模块 + SPI 落点文档化。全量构建与测试通过，独立 closure audit APPROVE。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_046407277ffeeVbIcKXFv5LSoc`，general）
- Audit Session: `ses_046407277ffeeVbIcKXFv5LSoc`
- Evidence:
  - Phase 1（P3-MA1-016）：beans.xml `:18-22` `<ioc:condition><on-bean>nopGraphQLEngine</on-bean></ioc:condition>` + `xmlns:ioc`（PASS，bean id 非接口名）；pom `nop-http-client-jdk`/`nop-autotest-junit` test 依赖（PASS）；`TestGraphQLToolSetConditionalRegistration` 3 组断言：xdef 模型解析（getOnBean 含 nopGraphQLEngine）、有引擎容器注入 `@Named("nopGraphQLToolSet")`、无引擎 BeanContainerBuilder 自定义容器跳过且其他 bean 注册（PASS）
  - Phase 2（P3-MA1-013）：design doc `03-sequential-thinking-storage.md` 裁定完整（保持文件持久化 + 拒绝 ORM 理由 + 迁移触发条件，PASS）；`ThoughtSession` @DataBean + epoch-millis long、`ThoughtData` long timestamp（无 Instant）、`ThoughtAnalyzer:189` `Instant.ofEpochMilli`（PASS）；`TestThoughtStorage` 8 例（持久化 round-trip/路径解析/隔离/阶段+清空/导出导入/未知会话/null 拒绝，PASS）+ `TestSequentialThinkingBizModel` 成功路径（PASS）
  - Phase 3（P3-MA3-003）：`nop-ai-rag/README.md` + design doc `04-rag-module-position.md`（P1-MA5-003 兼容，PASS）；`nop-ai/pom.xml:35` 模块保留（PASS）；`docs-for-ai/03-modules/nop-ai.md:39` SPI 落点描述（PASS）
  - 跨模块：`arm-index.md:430-438` 第九批 P3 追踪小节 3 行（PASS）；plan 全部 checklist [x] + Phase Status completed（PASS）；无 in-scope live defect 静默降级（P3-MA1-013 暴露的 2 个 defect 就地修复并记录，PASS）
  - Anti-Hollow：`beans.xdef:155/182` BeanConditionModel/`<on-bean>` + `BeanConditionEvaluator.java:108-113` on-bean 不满足即禁用（条件注册真实生效，非空壳）；ThoughtStorage 真实 JsonTool 文件 round-trip（PASS）；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0（PASS）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（确认无未勾选项 + Closure Evidence 已写入）
  - Deferred 项分类检查：两个 watch-only residual（rag InMemory 属未来、无-引擎端到端不可构造）均未阻塞 closure 且有明确理由；无 in-scope live defect 被降级（PASS）

Follow-up:

- no remaining plan-owned work
- 非阻塞观察（audit 提及）：`TestThoughtStorage.testDefaultPathResolution` 的相对路径分支为字符串语义断言而非真实写入——绝对路径与回退分支已真实覆盖，非 defect
