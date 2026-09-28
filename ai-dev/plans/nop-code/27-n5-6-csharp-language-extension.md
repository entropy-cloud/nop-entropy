# 27 N5.6 C# 语言扩展（nop-code-lang-csharp）

> Plan Status: draft
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N5.6；前置 `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 20（C# blob 已完成：177/179=98.9%）；先例模块 `nop-code-lang-go`/`nop-code-lang-rust`（N5.4/N5.5）
> Related: N5.4/N5.5（同形态姊妹项）

## Purpose

落地 roadmap N5.6：新增 `nop-code-lang-csharp` 语言模块——`CodeLanguage.CSHARP` 枚举、orm dict 选项、`ILanguageAdapter` 适配、tree-sitter c-sharp 分析器、beans 注册、service resolver 接线、测试。使 nop-code 能索引 C# 源码。

## Current Baseline

- 前置在档：`TreeSitterCSharp` compat 包装（blob `/grammars/c-sharp/tree-sitter-c-sharp-blob.bin`，`CSharpScanner` 工厂已静态接线）；条目 20 交付 177/179=98.9%；条目 19/20 的 no-lookahead 修复在档。
- N5.4/N5.5 模块范式全在档：枚举 → orm dict 选项（Protected Area plan-first 载体）→ codegen 再生成（3 生成物须随提交入库）→ 模块 → service 接线 → 测试。
- C# grammar 节点形状（CSharpTreeProbe 实测 dump，2026-09-29）：
  - `namespace_declaration`（块式）与 **`file_scoped_namespace_declaration`**（C# 10+ 文件级，成员为兄弟节点）：名称节点 = identifier **或 qualified_name**（`namespace A.B`）——qn 前缀 = 拼接后的完整 namespace 路径；`using_directive` → identifier（导入名）→ IMPORT。
  - `class_declaration`/`interface_declaration`/`struct_declaration`/`record_declaration`（/`enum_declaration`）：名称 = identifier 子节点；`base_list` 子 = 基类/接口（各 identifier/type 子节点）→ 继承边按容器分派裁定（审查 M1）：class/record class = 首项 EXTENDS 其余 IMPLEMENTS；**interface = 全部 EXTENDS**；**struct/record struct = 全部 IMPLEMENTS**；enum base_list（`enum E : byte`）= 不产边；body = declaration_list。
  - `method_declaration`：名称 = identifier 子节点（类型后）；`property_declaration` → 名称 = identifier 子节点（**类型之后**——自定义类型属性 `public Animal Mother` 的 type 也是 identifier，不得取首 identifier）——CodeSymbolKind 无 PROPERTY，映射 FIELD（typescript 先例一致）；`field_declaration` → variable_declaration → variable_declarator → identifier，const modifier → CONSTANT 否则 FIELD；`enum_declaration` → 名称 + ENUM kind（enum_member 不提取）；constructor/delegate/event/indexer 不提取（Non-Goals）。
  - 方法体：`block` → `invocation_expression`（member_access_expression 或 identifier）→ calls；全局/命名空间内 `method_declaration` 直接可见。
  - 修饰：`modifier` 子（public/private/protected/internal）→ access modifier 映射。
  - C# 无 package 子句：qn 前缀裁定 = **namespace 路径**（嵌套 namespace_declaration 的 identifier 拼接，`Demo` → `Demo.Animal`）；`result.setPackageName` 同步。
  - Decision：`LanguageFamily.fromLanguage(CSHARP)` 落 default→UNKNOWN 本期接受（与 GO/RUST 同裁定）。

## Goals

- G1：`CodeLanguage.CSHARP("csharp", ".cs")` + orm dict `CSHARP/C#/70` 选项（codegen 再生成核对）。
- G2：新模块 `nop-code/nop-code-lang-csharp`：`CSharpLanguageAdapter` + `CSharpCodeFileAnalyzer` + `CSharpImportResolver`（using 名 → 项目 namespace 目录匹配，镜像 rust）+ beans + `_module` + parent pom 注册 + service 两行级接线（N5.4 M-1 同裁定）。
- G3：测试：adapter/analyzer（namespace/interface/class+base/property/field/const/method/call 全形状）/resolver/内存释放。

## Non-Goals

- 泛型类型参数跟踪、partial 类合并、preprocessor 指令符号；web/app 改动；service 仅两行级接线。

## Scope

### In Scope

- `nop-code-core`：CodeLanguage +CSHARP；orm dict CSHARP=70。
- 新模块 `nop-code/nop-code-lang-csharp`；`nop-code/pom.xml` modules；service pom + resolver 注册。

### Out Of Scope

- web/app；dict yaml/constants 手改；preprocessor。

## Execution Plan

### Phase 1 - 枚举 + dict + 模块

Status: planned
Targets: `CodeLanguage.java`、orm.xml、`nop-code/pom.xml`、`nop-code/nop-code-lang-csharp/**`、service 接线

- Item Types: `Fix`

- [ ] `CodeLanguage.CSHARP("csharp", ".cs")`；`nop-code/model/nop-code.orm.xml` dict CSHARP=70
- [ ] 模块 pom（镜像 lang-go）；`_module`；beans
- [ ] `CSharpCodeFileAnalyzer`（结构性子节点访问；**namespace 前缀栈：块式嵌套拼接 + file_scoped 变体 + qualified_name 拆段**；base_list 按容器分派继承边（M1）；method/property/field/const/enum 提取；invocation_expression calls）
- [ ] `CSharpLanguageAdapter`（.cs + exclude **/bin/**、**/obj/**、**/.git/**）；`CSharpImportResolver`
- [ ] parent pom modules + service 接线

Exit Criteria:

- [ ] 构建后 language.dict.yaml 出现 C# 行（生成物核对，随提交入库）；`./mvnw install -pl nop-code -o -DskipTests` 通过
- [ ] 无静默跳过：base_list 各项必产边（首项 EXTENDS 其余 IMPLEMENTS）；未识别节点全遍历
- [ ] No owner-doc update required（Phase 2 统一）；logs 随 Phase 2 写入

### Phase 2 - 测试 + 文档 + roadmap

Status: planned
Targets: tests 4 类、docs-for-ai 模块表、roadmap

- Item Types: `Fix`、`Proof`

- [ ] TestCSharpLanguageAdapter / TestCSharpCodeFileAnalyzer（样例含 file-scoped namespace（`namespace Demo.Animals;` qualified_name）与块式；INTERFACE IRunner；CLASS Animal/ Dog；PROPERTY Name（+自定义类型属性 Mother 的取名反例）；CONSTANT Max；METHOD Run×2（声明+实现，qn=Demo.Animal.Run）；EXTENDS Dog→Animal + interface 基全 EXTENDS；struct 基全 IMPLEMENTS；IMPLEMENTS Animal→IRunner；calls Console.WriteLine）/ TestCSharpImportResolver / 内存释放
- [ ] **接线验证（Rule #23）**：service 全量零回归 + registerImportResolvers 第 6 个 resolver 代码核验（容器级断言按 N5.4/N5.5 deviation 范式豁免，记录于 Deferred）
- [ ] 删除临时 probe（如有）；docs 模块表补 lang-csharp 行 + dict 清单 +CSHARP；roadmap N5.6 todo→done + 汇总 + 基线 5→6；`check-doc-links --strict` exit 0；logs 条目

Exit Criteria:

- [ ] **端到端验证**：C# 源 → analyzer → 全形状结果走通
- [ ] `./mvnw test -pl nop-code/nop-code-lang-csharp -am -T 1C` 全绿；service 全量零回归
- [ ] roadmap/owner docs 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] N5.6 交付词逐项：csharp 绑定 + adapter + 提取器 + dict 同步 + 测试 + service 接线
- [ ] `scan-hollow-implementations --module nop-code/nop-code-lang-csharp --severity high` 零 high finding
- [ ] `check-plan-checklist --strict` 对本 plan 退出码 0
- [ ] `check-doc-links --strict` 退出码 0
- [ ] 代码风格：import 分组、4 空格、注释密度对齐 lang-go/rust
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] `./mvnw test -pl nop-code/nop-code-lang-csharp -am -T 1C` 全绿

## Deferred But Adjudicated

### 容器级接线断言（Rule #23 容器形态）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 镜像 N5.4/N5.5 deviation；service 全量测试覆盖 by-type 装配路径。
- Successor Required: `no`

## Non-Blocking Follow-ups

- C# 泛型/partial 合并/preprocessor（optimization candidate）。

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
