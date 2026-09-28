# 16 N5.2 框架适配迁出核心（SPI 装配）

> Plan Status: completed(R1 1B/2M/4m 修订 + R2 复审 APPROVE + 独立 closure audit APPROVE，agent_0e48512f)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N5.2；`ai-dev/design/nop-code/flow-analysis-design.md` §框架模式注册（设计权威）；`00-vision.md` 约束 9（框架适配不入核心）；live 核对（2026-09-28）
> Related: N5.3（DSL 驱动适配器，远期演进，本 plan 不触及）

## Purpose

`IEntryPointPatternProvider` SPI 已存在，缺口是**装配**：`FlowDetector` 无参构造经 `List.of(new DefaultSpringEntryPointPatternProvider())` 硬编码私有内部类，无 IoC 注册。本项将框架入口点模式知识迁出为可插拔实现并经 IoC 装配，同时把 `DeadCodeDetector`/`JavaFileAnalyzer` 的硬编码框架知识按同一 SPI/约定外置——**行为等价**（默认装配下检测结果逐字节不变）。

## Current Baseline（live 核对 2026-09-28）

- **FlowDetector**（nop-code-flow）：私有静态内部类 `DefaultSpringEntryPointPatternProvider` + 静态 `SPRING_ENTRY_ANNOTATIONS`（13 个 Spring 注解 FQN，L58-72）；无参构造与 null 守卫分支两处 `List.of(...)` 硬编码（L82/L90）；`FlowDetector(List)` 构造已支持外部注入。IoC 面：`io.nop.code.flow.FlowDetector` 定义于 nop-code-service `app-service.beans.xml`（无参构造，无属性注入）。
- **DeadCodeDetector**（nop-code-flow）：`DEFAULT_FRAMEWORK_ANNOTATIONS` 集合硬编码（L24-46，成员 `frameworkAnnotations`），`isFrameworkEntryPoint` 以 extData 注解**短名精确 contains** 消费。**注意（R1 Q1）**：默认集为多框架混合（Spring + JMX/CDI/Guava/Nop BizModel 等）——本项"外置"语义 = provider 可插拔**增强**（union 注入），默认混合集原地保留（行为等价）。
- **SPI 消费错配（R1 Blocker 实证）**：`getAnnotationPatterns()` 返回 **FQN**（FlowDetector L558-560），而 DeadCodeDetector 短名匹配——直接 union 永不命中（注入空转）；全仓 `getAnnotationPatterns()` 现零消费者。注入必须做 **FQN→短名归一化**。
- **JavaFileAnalyzer**（nop-code-lang-java）：`SPRING_MAPPING_ANNOTATIONS` 集合 + `extractSpringHttpMethod` 映射 + `extractSpringRoutes` 提取逻辑内聚在内部类（L835-920）；分析器实例经 `JavaLanguageAdapter.getFileAnalyzer()` 每次 new，非 IoC bean（`_lang-java.beans.xml` 为 codegen 生成文件，仅注册 adapter）。
- **IoC 机制**：`app-*.beans.xml` 自动装载需模块 `_vfs/nop/code/_module` 标记（`_module` 允许多模块重复）；nop-code-flow 目前无任何 resources。
- **测试基建**：`TestFlowAnalysisE2E`/`TestGraphAnalysisE2E`（service 容器级）、`TestProvenanceAndSpringRoutes`（lang-java，路由提取行为）。service 测试容器自动装载 `app-service.beans.xml`——装配语法错误会令全部 service 测试失败（天然护栏）。

## Goals

- **G1 FlowDetector 装配**：`DefaultSpringEntryPointPatternProvider` 迁出为顶层公开类 `SpringEntryPointPatternProvider`（保留原 `DefaultSpring...` 为废弃委托或直接改名+全量引用修正，执行期按最小 diff 定）；`FlowDetector` 增 `setPatternProviders(List)`；IoC 注册 provider bean（nop-code-flow 新增 `_module` + `app-flow.beans.xml`）；service `app-service.beans.xml` 将 provider 注入 FlowDetector。
- **G2 DeadCodeDetector 按同一 SPI 增强**：增 `setPatternProviders(List<IEntryPointPatternProvider>)`——将各 provider 的 `getAnnotationPatterns()` 做 **FQN→短名归一化**后并入 `frameworkAnnotations`（默认混合集原地保留 = 现行为）；bean 装配经 `<ioc:collect-beans by-type>` 注入。setter 语义：**替换**（非累积，容器单次调用）；空 list = 合法空增强集，不回退默认（构造器 null→默认的语义仅保留在构造器）。
- **G3 JavaFileAnalyzer 知识外置**：Spring 路由知识外置为顶层 `SpringFrameworkRouteConvention`（实现新接口 `IFrameworkRouteConvention`：**mapping 注解集合 + class-prefix 注解名（RequestMapping 字面量也要外置，R1 Q3）+ httpMethod 映射**）；`extractSpringRoutes` 的 AST 遍历/extractRoutePath/extData JSON 合并属分析器基建留在 analyzer；分析器持默认实例（= 现行为）+ setter 可插拔。**装配边界裁定**：分析器实例非 IoC 管理（经 adapter 每次 new；adapter 注册于 codegen 生成文件），IoC 装配归后续语言适配器架构演进（Non-Blocking Follow-up）——本项交付"知识外置 + 可插拔 seam"。
- **G4 行为等价证明**：新增等价测试——默认构造 vs 显式装配（provider 注入）在相同 fixture 上产出完全一致（FlowDetector 入口点/流、DeadCodeDetector 报告、JavaFileAnalyzer 路由）；既有 E2E 全量回归。

## Non-Goals

- 不改 SPI 接口契约（`IEntryPointPatternProvider` 方法集不变）。
- 不引入新框架实现（Quarkus/JAX-RS 等 provider 属使用方扩展）。
- 不改语言分析器的 IoC 管理架构（codegen 生成管线不动）。
- 不做 N5.3 的 DSL 驱动适配器。

## Scope

### In Scope

- nop-code-flow：provider 顶层化 + FlowDetector/DeadCodeDetector setter + 模块 `_module`/`app-flow.beans.xml`。
- nop-code-lang-java：`IFrameworkRouteConvention` + `SpringFrameworkRouteConvention` + 分析器 seam。
- nop-code-service：`app-service.beans.xml` 装配注入。
- 等价测试 + 既有回归 + owner docs（flow-analysis-design.md §框架模式注册状态更新、缺口矩阵、roadmap）。

### Out Of Scope

- 语言分析器 IoC 管理化、codegen 生成管线变更。
- 新框架 provider 实现。

## Execution Plan

### Phase 1 - 迁出与装配（G1/G2/G3）

Status: completed
Targets: `nop-code-flow`、`nop-code-lang-java`、`nop-code-service` app-service.beans.xml

- Item Types: `Fix`（roadmap 登记的装配缺口）

- [x] `SpringEntryPointPatternProvider` 顶层化（Spring 注解知识随迁）；FlowDetector `setPatternProviders`（替换+priority 排序）；无参构造默认行为保持
- [x] DeadCodeDetector `setPatternProviders`：provider 的 getAnnotationPatterns 做 **FQN→短名归一化**后并入 frameworkAnnotations（setter=替换语义；默认混合集不变）
- [x] `IFrameworkRouteConvention`（mapping 注解集合 + classPrefix 注解名 + httpMethod 映射）+ `SpringFrameworkRouteConvention`；JavaFileAnalyzer 委托 convention + setter（默认实例 = 现行为）
- [x] nop-code-flow 新增 `_vfs/nop/code/_module`（多模块重复持有，无害已核）+ `_vfs/nop/code/beans/app-flow.beans.xml`（provider bean 注册，ioc:default=true + ioc:sort-order）
- [x] service `app-service.beans.xml`：FlowDetector/DeadCodeDetector 以 **`<ioc:collect-beans by-type="io.nop.code.flow.IEntryPointPatternProvider"/>`** 注入 provider（owner doc 权威机制，天然接纳未来 provider；service 测试容器自动装载该文件，装配错误即刻全量暴露）
- [x] 全量回归 `./mvnw test -pl nop-code/nop-code-service -am` 绿（既有 E2E 即行为等价第一证）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 装配生效且既有测试全绿（容器装配链无语法/类型错误）
- [x] **接线验证**（规则 #23）：等价测试断言容器装配的 FlowDetector 实际持有注入的 provider（非默认构造），DeadCodeDetector 同理
- [x] **无静默跳过**：setter 对 null/空 list 的语义显式（空 list = 无 provider，不静默回退默认——装配空集是合法意图）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 等价证明 + docs/roadmap 同步（G4）

Status: completed
Targets: `nop-code/nop-code-service/src/test/java/`、flow-analysis-design.md、缺口矩阵、roadmap

- Item Types: `Proof | Fix`

- [x] 等价测试：**自建 fixture**（现 test-project 无 Spring 注解/Controller 命名类不可复用——R1 Q6；含 Controller 命名类 + 带注解 extData 短名符号 + RequestMapping 路由类），JunitAutoTestCase 内 @Inject 容器装配 bean 与默认构造实例对比——FlowDetector flows/DeadCodeDetector report/JavaFileAnalyzer routes 逐项明细相等
- [x] flow-analysis-design.md §框架模式注册状态更新（IoC 装配已落地，内置实现为 default bean）
- [x] 缺口矩阵 N5.2 行 done；roadmap N5.2 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 等价测试全绿且断言逐项明细（非仅计数）
- [x] roadmap/缺口矩阵/design 三处与 live 一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Spring 模式知识三处外置：FlowDetector provider 顶层化并经 IoC 装配；DeadCodeDetector 经同一 SPI 可插拔增强（默认混合集原地保留）；JavaFileAnalyzer 知识外置为 convention（IoC 装配边界见 Follow-up）
- [x] 行为等价证明完成（等价测试逐项明细 + 既有 E2E 全量回归）
- [x] 必要 focused verification 完成（`./mvnw test -pl nop-code/nop-code-service -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap（JavaFileAnalyzer IoC 装配边界为显式裁定，见 Non-Blocking Follow-ups）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) 装配链真实连通（容器 bean 持有注入 provider，非默认构造），(b) 等价测试断言逐项明细，(c) 无静默跳过
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无——in-scope 无延期项）

## Non-Blocking Follow-ups

- JavaFileAnalyzer 的 IoC 装配（经 adapter/codegen 管线注入 convention）：分析器实例非 IoC 管理，装配需语言适配器架构演进（codegen 生成文件变更）。Classification: `out-of-scope improvement`；Why Not Blocking Closure: Spring 知识已外置为可插拔类（seam 就绪），默认行为等价；roadmap N5.3（DSL 驱动适配器）为该方向的后续演进载体。

## Closure

Status Note: Spring 入口点 provider 顶层化并经 `<ioc:collect-beans by-type>` 装配进 FlowDetector/DeadCodeDetector（DeadCodeDetector 侧 FQN→短名归一化）；JavaFileAnalyzer 路由知识外置为 IFrameworkRouteConvention（seam 就绪，IoC 装配边界显式裁定为 follow-up）；行为等价经 TestFrameworkAdapterEquivalence 5 项断言 + 242 测试全量回归钉住。独立 closure audit APPROVE 后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_0e48512f）
- Evidence:
  - 六项验证全 PASS：Phase 1/2 Exit Criteria + Closure Gates 逐条 live 核实；三工具（doc-links/hollow/checklist）exit 0 复跑
  - Anti-Hollow 三项 PASS：(a) 装配链真实连通（@Inject 容器 bean 反射断言含 SpringEntryPointPatternProvider；collect-beans 为 beans.xdef 真实构造）；(b) FQN→短名归一化真实（含短名/不含 FQN 双向断言）；(c) 等价断言逐项明细（entryPoint 有序列表/sorted qualifiedName 集合/route path 精确值 /demo/list→/list + httpMethod）
  - 行为等价抽查 PASS：原内部类逐行迁入顶层类（13 FQN 同集、shortName 匹配一致）；extractSpringHttpMethod 原样入 convention；DEFAULT_FRAMEWORK_ANNOTATIONS（21 项混合集）未动
  - 实跑 PASS：nop-code-service -am 242/0；nop-code-flow 42/0；TestFrameworkAdapterEquivalence 5/5
  - Deferred 分类检查 PASS：JavaLanguageAdapter 每次 new 分析器（非 IoC bean）+ _lang-java.beans.xml 为 codegen 生成——out-of-scope 裁定事实成立
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（收口后复跑）

Follow-up:

- JavaFileAnalyzer 的 IoC 装配（out-of-scope improvement，见 Non-Blocking Follow-ups）
