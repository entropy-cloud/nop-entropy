# 26 N5.5 Rust 语言扩展（nop-code-lang-rust）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N5.5；前置 `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 19（Rust blob 已完成：145/151=96.0%）；先例模块 `nop-code-lang-go`（N5.4，2026-09-28 落地，结构性子节点访问范式）
> Related: N5.4/N5.6（同形态姊妹项）

## Purpose

落地 roadmap N5.5：新增 `nop-code-lang-rust` 语言模块——`CodeLanguage.RUST` 枚举、orm dict 选项、`ILanguageAdapter` 适配、tree-sitter rust 分析器、beans 注册、service resolver 接线、测试。使 nop-code 能索引 Rust 源码。

## Current Baseline

- 前置在档：`TreeSitterRust` compat 包装（blob `/grammars/rust/tree-sitter-rust-blob.bin`，`RustScanner` 工厂已静态接线）；条目 19 的 no-lookahead/mark_end 修复在档。
- N5.4 模块范式全在档（`nop-code-lang-go`）：枚举 → orm dict 选项（`nop-code/model/nop-code.orm.xml`，Protected Area plan-first 由本 plan 载体）→ codegen 再生成 dict yaml/constants → 模块（adapter/analyzer/resolver/beans/_module）→ service pom + `registerImportResolvers()` 接线 → 测试（纯 JUnit + `@EnabledIf` Condition + 内存释放实态范式）。
- rust grammar 节点形状（RustTreeProbe 实测 dump，2026-09-28）：
  - `struct_item`/`enum_item`/`trait_item`：名称 = 首个 `type_identifier` 子节点（grammar 有 field 'name' 但按 N5.4 经验采用结构性访问）；struct body = `field_declaration_list`（`field_declaration` 内 `field_identifier` 名 + 类型）；trait body = `declaration_list`（`function_signature_item` = 特征方法，名称 = `identifier` 子节点）。
  - `impl_item`：trait 与 self-type 定位裁定——**trait = `for`（匿名节点）之前最后一个命名子节点；self type = `for` 之后第一个命名子节点**（generic impl `impl<T: Clone> Super for Pair<T>` 首命名子是 type_parameters，该规则覆盖）；trait/self-type 可为 type_identifier/scoped_type_identifier/generic_type；body `declaration_list` 内 `function_item`。
  - `type_item` = type alias（名称 = `type_identifier` 子节点）；`const_item`/`static_item`（名称 = `identifier` 子节点）→ CONSTANT；`mod_item` → 声明；`use_declaration` → `scoped_identifier`（crate 路径）→ IMPORT。
  - `call_expression`：function 子 = `identifier`（直呼）或 `scoped_identifier`（路径调用）。
  - `visibility_modifier`（pub）→ PUBLIC。注意 pub 项**首命名子是 visibility_modifier**（不得当名称）。继承裁定（审查 M2）：EXTENDS 边**仅取 trait_item 的直接 `trait_bounds` 子**（supertrait 语义，`pub trait Super: Clone + Send`）；struct/enum 的泛型参数 bounds（藏在 type_parameters/where_clause 内）语义上非继承，**排除**；IMPLEMENTS 边仅来自 impl_item（trait→self-type）。
  - rust 无 package 子句：qn 前缀裁定 = **无包前缀**（qn = 裸名称/类型限定名），`result.setPackageName(null)`；mod 路径不参与 v1 qn；struct 元组体（ordered_field_declaration_list）fields 不在本期。
  - Decision：`LanguageFamily.fromLanguage(RUST)` 落 default→UNKNOWN 本期接受（与 N5.4 GO 同裁定）。

## Goals

- G1：`CodeLanguage.RUST("rust", ".rs")` + orm dict `RUST/Rust/60` 选项（codegen 再生成核对）。
- G2：新模块 `nop-code/nop-code-lang-rust`：`RustLanguageAdapter` + `RustCodeFileAnalyzer`（符号：顶层 function_item→FUNCTION、declaration_list 内 function_item 与 function_signature_item→METHOD（Rust 无独立 method 节点，按父链上下文切换）、struct_item/enum_item→CLASS、trait_item→INTERFACE、type_item→TYPE_ALIAS、const_item/static_item→CONSTANT、use_declaration→IMPORT；继承：impl_item trait→self-type = IMPLEMENTS 边；calls：identifier/scoped_identifier 函数子节点，同文件 qn 候选；impl 方法归属 impl self-type）+ `RustImportResolver`（crate 路径 → 项目内 mod 目录前缀匹配）+ beans + `_module` + parent pom 注册 + service 两行级接线（pom + registerImportResolvers，N5.4 M-1 同裁定）。
- G3：测试：TestRustLanguageAdapter、TestRustCodeFileAnalyzer（struct/enum/trait/impl（含 trait impl）/type alias/const/use/call 全形状）、TestRustImportResolver、内存释放（N5.4 实态范式）。

## Non-Goals

- C# 模块（N5.6）；泛型/宏展开/macro_rules 内部符号；跨 crate qn 解析；nop-code-web/app 改动；service 仅限两行级接线。

## Scope

### In Scope

- `nop-code-core`：CodeLanguage +RUST；`nop-code/model/nop-code.orm.xml` dict RUST=60。
- 新模块 `nop-code/nop-code-lang-rust`（pom + 3 主类 + beans + _module + tests）。
- `nop-code/pom.xml` modules；service pom + resolver 注册。

### Out Of Scope

- web/app；dict yaml/constants 手改；宏内部。

## Execution Plan

### Phase 1 - 枚举 + dict + 模块

Status: completed
Targets: `CodeLanguage.java`、`nop-code/model/nop-code.orm.xml`、`nop-code/pom.xml`、`nop-code/nop-code-lang-rust/**`、service pom + resolver 注册

- Item Types: `Fix`

- [x] `CodeLanguage.RUST("rust", ".rs")`；orm dict RUST=60
- [x] 模块 pom（镜像 lang-go：core + treesitter + junit-jupiter test）；`_module` 标记；beans（ioc:type=ILanguageAdapter）
- [x] `RustCodeFileAnalyzer`（结构性子节点访问，按 Current Baseline 实测形状；qn 无包前缀裁定落地）
- [x] `RustLanguageAdapter`（.rs + exclude **/target/**、**/.git/**）；`RustImportResolver`
- [x] parent pom modules + service 接线

Exit Criteria:

- [x] 构建后 language.dict.yaml 出现 Rust 行（生成物核对）；`./mvnw install -pl nop-code -o -DskipTests` 通过
- [x] 无静默跳过：impl 无 trait 时不产 IMPLEMENTS 边（正常）、有 trait 必产；未识别节点全遍历
- [x] No owner-doc update required（Phase 2 统一）；logs 随 Phase 2 写入

### Phase 2 - 测试 + 文档 + roadmap

Status: completed
Targets: tests 4 类、docs-for-ai 模块表、roadmap

- Item Types: `Fix`、`Proof`

- [x] TestRustLanguageAdapter / TestRustCodeFileAnalyzer（样例含 generic impl：`impl<T: Clone> Super for Pair<T>` → IMPLEMENTS subType=Pair superType=Super；trait supertrait：`pub trait Super: Clone + Send` → EXTENDS 边；符号 CLASS Animal/CLASS Kind/INTERFACE Runner/TYPE_ALIAS Alias/CONSTANT MAX；impl 内 function_item→METHOD speak 归属 Animal；trait 内 function_signature_item→METHOD run；IMPLEMENTS subType=Animal superType=Runner；use 导入 fmt + crate::utils::helper；calls `fmt::print` + `helper` 同文件解析）/ TestRustImportResolver（crate:: 前缀路径 → mod 目录匹配）/ 内存释放
- [x] **接线验证（Rule #23）**：service 侧全量测试在 lang-rust 进 classpath 后零回归 + `CodeIndexService.registerImportResolvers` 代码核验第 5 个 resolver；（容器级断言按 N5.4 deviation 范式豁免，已记录于 Deferred）
- [x] 删除 nop-treesitter 测试源中的临时 RustTreeProbe（未入库探针）；docs 模块表补 lang-rust 行；roadmap N5.5 todo→done + 汇总 + 基线计数 4→5；`check-doc-links --strict` exit 0；logs 条目

Exit Criteria:

- [x] **端到端验证**：Rust 源 → analyzer → 全形状结果走通（TestRustCodeFileAnalyzer 承载）
- [x] `./mvnw test -pl nop-code/nop-code-lang-rust -am -T 1C` 全绿；service 全量零回归
- [x] roadmap/owner docs 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] N5.5 交付词逐项：rust 绑定 + adapter + 提取器 + dict 同步 + 测试 + service 接线
- [x] `scan-hollow-implementations --module nop-code/nop-code-lang-rust --severity high` 零 high finding
- [x] `check-plan-checklist --strict` 对本 plan 退出码 0
- [x] `check-doc-links --strict` 退出码 0
- [x] 代码风格：import 分组、4 空格、注释密度对齐 lang-go
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] `./mvnw test -pl nop-code/nop-code-lang-rust -am -T 1C` 全绿

## Deferred But Adjudicated

### 容器级接线断言（Rule #23 容器形态）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 镜像 N5.4 deviation（lang 模块无 nop-ioc test 依赖）；service 全量测试在 lang-rust 进 classpath 后覆盖 by-type 装配路径，beans 写错即 service 测试暴露。
- Successor Required: `no`

## Non-Blocking Follow-ups

- Rust 宏内部符号/跨 crate 解析（optimization candidate）。

## Closure

Status Note: N5.5 全链路落地：CodeLanguage.RUST + orm dict RUST=60（codegen 再生成实证，3 生成物 language.dict.yaml/_NopCodeDaoConstants/_app.orm.xml 随提交入库）+ nop-code-lang-rust 模块 + service 接线（第 5 个 resolver）。实现中 4 个缺陷测试驱动修复（EXTENDS subTypeId 语义、函数体 calls 遍历、inherent impl 回退、trait 方法提取）。测试：模块 13/13 + service 267 tests 零回归。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_bfc5137a（fresh session 独立 closure audit：Gates 1-7 技术面全 PASS——dict 链/模块同构/接线第 5 resolver/六项行为读码/测试复跑 13+267；RustTreeProbe 无残留；附 3 项关闭条件已执行：3 生成物入库、本节回写、checklist 复跑 exit 0）
- Evidence:
  - Phase 1：CodeLanguage.RUST（L12）；orm.xml RUST=60；language.dict.yaml Rust 行（audit 构建再生成后确认）；模块文件与 lang-go 逐项同构；service pom + resolver 顺序 Java→Python→TypeScript→Go→Rust（audit 读码 ：292-299）
  - Phase 2：13/13 复跑一致（含 generic impl IMPLEMENTS subType=Pair(symbol id)、supertrait EXTENDS、struct 泛型 bounds 反例、fmt::print/helper calls）；六项行为读码核实（继承边方向、EXTENDS 限直接子、无 for 回退、declaration_list 排除、trait 方法、calls 递归）
  - Closure Gates：scan-hollow exit 0；check-doc-links exit 0；check-plan-checklist --strict exit 0（completed 后复跑）；风格对齐 lang-go（audit 抽查）
  - Deferred 项分类诚实（容器级断言 deviation 镜像 N5.4）；无 live defect 降级

Follow-up:

- Rust 宏内部符号/跨 crate 解析（optimization candidate）。no remaining plan-owned work。
