# 17 N5.3 DSL 驱动框架适配器

> Plan Status: completed(R1 1B/1M/2m 修订 + R2 复审 APPROVE + 独立 closure audit APPROVE，agent_ea6049fb)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N5.3；plan 16（N5.2 装配与 seam 前序）；live 核对（2026-09-28）
> Related: plan 16（IFrameworkRouteConvention/SpringEntryPointPatternProvider 前序交付）

## Purpose

在 N5.2 交付的可插拔 seam 上提供**描述式 DSL**：框架的入口点/路由模式以 XML 声明，驱动通用实现（`ConfigDrivenEntryPointPatternProvider`/`ConfigDrivenRouteConvention`），新增框架适配**只需 DSL 文件、无需编码**。交付物 = DSL 模型 + 解析器 + 通用实现 + Spring 等价证明。

## Current Baseline（live 核对 2026-09-28）

- N5.2 后：`SpringEntryPointPatternProvider`（nop-code-flow 顶层类，13 注解 FQN + 8 类名后缀 + shortName 匹配）、`IFrameworkRouteConvention`/`SpringFrameworkRouteConvention`（nop-code-lang-java convention 包：mapping 注解 6 值 + classPrefix + httpMethod 映射）——均硬编码 Java 实现，新增框架需写代码。
- `XNode.parse(String)`（nop-kernel nop-core）为平台 XML 解析先例；`FrameworkPatternModel` 落 nop-code-core（flow 与 lang-java 均依赖 core）。
- 错误处理约定（AGENTS.md）：模块内部用模块异常/明确异常 + 英文消息；`NopCodeCoreErrors` 现有 4 个错误码（无 DSL 相关，新增需走错误码模型——本项用 IllegalArgumentException 承载解析失败，见 Scope 裁定）。
- 消费链就绪：`<ioc:collect-beans by-type>` 装配对任意 `IEntryPointPatternProvider` 实现生效；`JavaFileAnalyzer.setRouteConvention` setter 接受任意 convention。

## Goals

- **G1 DSL 模型与解析**：`FrameworkPatternModel`（nop-code-core `io.nop.code.core.framework`）：name、entryPoint（annotationFqns/namePatterns/classSuffixes）、route（mappingAnnotations 短名键/classPrefixAnnotations/httpMethod 映射——**注意双形态**：入口注解用 FQN，路由映射注解用短名键，与各自硬编码实现的匹配形态一致）。解析失败用 IllegalArgumentException（英文消息；nop-code-core 已有先例 CodeEdgeData，lint 的 bare-exception 规则不命中 IAE）——此裁定承载于本 Goal。
- **DSL 语法（R1 Q-c 权威定义，实现者据此实现与测试）**：

```xml
<framework-patterns>
  <framework name="spring">
    <entry-point>
      <annotation-fqn>org.springframework.web.bind.annotation.RequestMapping</annotation-fqn>
      <class-suffix>Controller</class-suffix>
      <name-pattern>main</name-pattern>
    </entry-point>
    <route-convention>
      <mapping annotation="GetMapping" method="GET"/>
      <mapping annotation="RequestMapping" method=""/>
      <class-prefix-annotation>RequestMapping</class-prefix-annotation>
    </route-convention>
  </framework>
</framework-patterns>
```

未知元素/缺 framework@name/缺属性 → IllegalArgumentException。`XNode` API 实名（R1 核对）：`parse(String)`/`childByTag`/`getChildren`/`attrText`/`contentText`。
- **G2 通用实现**：`ConfigDrivenEntryPointPatternProvider`（nop-code-flow）——**匹配语义裁定（R1 Blocker）**：与 SpringEntryPointPatternProvider 的 isEntryPoint 完全一致（classSuffix 后缀 + METHOD/CONSTRUCTOR + 注解 FQN/短名）；**namePatterns 为声明性元数据**（经 getNamePatterns() 暴露，不参与 isEntryPoint 匹配——硬编码实现亦不消费它，实现通配将破坏等价且属行为变更，明确不做）并加测试断言钉住该口径。`ConfigDrivenRouteConvention`（nop-code-lang-java）：mappingAnnotations/classPrefixAnnotations/httpMethodFor（未命中返回 ""）。
- **G3 Spring 等价证明**：内置 `spring.framework-patterns.xml`（内容与 SpringEntryPointPatternProvider/SpringFrameworkRouteConvention 完全一致）+ 等价测试——config 驱动实现 ≡ 硬编码实现（相同 fixture 逐项相同输出）。
- **G4 测试**：DSL 解析（正常/缺字段/空模型）+ 等价 + 通用实现行为单测。

## Non-Goals

- 不在生产 beans 中自动注册 config 驱动 provider（避免与硬编码 Spring provider 双重匹配；注册属部署方选择，等价测试证明其可用性）。
- 不写 xdef schema 校验（XNode 解析 + 显式结构检查；xdef DSL 规范化属后续演进）。
- 不改 N5.2 已交付接口与装配（`<ioc:collect-beans>` 机制原样复用）。
- 不做 DSL 热加载。

## Scope

### In Scope

- nop-code-core：`FrameworkPatternModel` + `FrameworkPatternDsl`（解析）。
- nop-code-flow：`ConfigDrivenEntryPointPatternProvider`；nop-code-lang-java：`ConfigDrivenRouteConvention`。
- `spring.framework-patterns.xml`（nop-code-flow resources）+ 等价/解析测试。
- owner docs：flow-analysis-design.md（DSL 机制一段）、缺口矩阵、roadmap。

### Out Of Scope

- 生产装配切换、DSL 编辑器/校验工具、其他框架（Quarkus 等）的 DSL 文件。

## Execution Plan

### Phase 1 - 模型/解析/通用实现 + 测试（G1/G2/G4）

Status: completed
Targets: `nop-code-core`（framework 包）、`nop-code-flow`、`nop-code-lang-java`、各模块测试

- Item Types: `Fix`（roadmap 登记的演进项）

- [x] `FrameworkPatternModel`（@DataBean 风格 POJO）：name/entryPoint（annotationFqns+namePatterns+classSuffixes）/route（mappingAnnotations+classPrefixAnnotations+httpMethodMappings）
- [x] `FrameworkPatternDsl.parse`：XNode 解析 + 显式结构校验（缺 name、空 entryPoint 与 route 并存、未知元素名 → IllegalArgumentException 英文消息）
- [x] `ConfigDrivenEntryPointPatternProvider`：isEntryPoint 对齐硬编码实现（classSuffix + kind + 注解 FQN/短名）；namePatterns 仅经 getNamePatterns() 暴露（声明性元数据裁定）
- [x] `ConfigDrivenRouteConvention`：mappingAnnotations/classPrefixAnnotations/httpMethodFor（未命中返回 ""）
- [x] 单测：解析正常（含未知元素抛错/缺 name 抛错/空映射 method="" 可表达）/通用实现行为（后缀命中/注解命中/未命中/namePatterns 不影响 isEntryPoint 断言）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 单测全绿：解析三类 + 通用实现行为断言
- [x] **无静默跳过**：非法 DSL 显式抛错（非静默忽略节点）
- [x] `./mvnw test -pl nop-code/nop-code-flow -pl nop-code/nop-code-lang-java`（或 -am 等价）回归绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Spring 等价证明 + docs/roadmap 同步（G3）

Status: completed
Targets: `spring.framework-patterns.xml`、等价测试、flow-analysis-design.md、缺口矩阵、roadmap

- Item Types: `Proof | Fix`

- [x] `spring.framework-patterns.xml`：13 个入口注解 FQN + 8 类后缀 + 4 namePatterns + 6 映射注解 + RequestMapping 前缀 + 5+空 httpMethod 映射——与硬编码实现逐项一致
- [x] 等价测试：`ConfigDriven*(parse(spring.xml))` vs 硬编码实现——入口点判定（后缀/注解 FQN/短名/未命中 fixture 符号逐项）与路由约定（annotation→httpMethod 全映射含 RequestMapping→空串 + 前缀注解）逐项相等
- [x] flow-analysis-design.md §框架模式注册补 DSL 机制段（最终态表述）
- [x] 缺口矩阵 N5.3 行 done；roadmap N5.3 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 等价测试全绿（逐项明细断言）
- [x] roadmap/缺口矩阵/design 三处与 live 一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] DSL 模型/解析/通用实现落地且有测试钉住
- [x] Spring 等价证明完成（config 驱动 ≡ 硬编码，逐项明细）
- [x] 必要 focused verification 完成（相关模块测试全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) DSL 解析→模型→通用实现的链路真实连通（等价测试即端到端），(b) 等价断言逐项明细，(c) 非法 DSL 显式抛错
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无——in-scope 无延期项）

## Non-Blocking Follow-ups

- 生产装配切换到 config 驱动 provider（部署方选择，硬编码实现保持 default）；xdef schema 化 DSL 校验；DSL 热加载。Classification: `optimization candidate`；Why Not Blocking Closure: 本项交付物为"DSL 模型+解析+测试"，机制完整且经等价证明，装配切换属部署策略。

## Closure

Status Note: DSL 模型/解析/通用实现/内置 Spring DSL 文件全部落地；R1 Blocker（namePatterns 语义矛盾）裁定为声明性元数据并有专测钉住；Spring 等价经逐项明细断言证明；独立 closure audit APPROVE。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_ea6049fb）
- Evidence:
  - Phase 1/2 Exit Criteria 与 Closure Gates 全 PASS（XML 与硬编码逐项核对：13 FQN/8 后缀/4 namePatterns/6 mapping 全一致）
  - Anti-Hollow 三项 PASS：(a) DSL 文件加载→parse→ConfigDriven*→与硬编码对比端到端连通；(b) 6 fixture 逐个断言 + httpMethod 全映射 + 显式 RequestMapping→""；(c) 4 个 assertThrows 与 parse 抛出点一一对应
  - isEntryPoint 语义逐行对齐核实（后缀 break/kind 限制/双匹配/namePatterns 不影响判定有专测）
  - 实跑 PASS：nop-code-service -am 247/0（TestFrameworkPatternDslEquivalence 5/5）；上游 core 139、flow 42、lang-java 39 全绿
  - Deferred 分类检查 PASS：config provider 零生产 beans 引用（grep 证实），硬编码 Spring provider 保持 default
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（收口后复跑）

Follow-up:

- 生产装配切换 config provider / xdef schema 化 / 热加载（optimization candidate，见 Non-Blocking Follow-ups）
