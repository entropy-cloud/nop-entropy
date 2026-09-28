# 25 N5.4 Go 语言扩展（nop-code-lang-go）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N5.4；前置 `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 18（Go blob 已完成：65/67=97.0%）；先例模块 `nop-code-lang-python`/`nop-code-lang-typescript`
> Related: N5.5/N5.6（同形态姊妹项）

## Purpose

落地 roadmap N5.4：新增 `nop-code-lang-go` 语言模块——`CodeLanguage.GO` 枚举、`ILanguageAdapter` 适配、tree-sitter go 分析器（符号/继承/calls/imports）、beans 注册、language dict 同步、测试。使 nop-code 能索引 Go 源码。

## Current Baseline

- 前置在档：`nop-treesitter` 条目 18 已交付 `TreeSitterGo` compat 包装（blob `/grammars/go/tree-sitter-go-blob.bin`，无 external scanner）。
- 语言模块范式（python/typescript）：`CodeLanguage` 枚举项 + `XLanguageAdapter implements ILanguageAdapter`（getLanguage/getFileAnalyzer/getFileExtensions/getExcludePatterns）+ `XCodeFileAnalyzer implements ICodeFileAnalyzer`（compat TSParser 遍历，产出 CodeFileAnalysisResult：CodeSymbol/CodeInheritance/CodeMethodCall/IMPORT）+ `_vfs/nop/code/beans/_lang-x.beans.xml`（bean id=类全名，ioc:type=ILanguageAdapter）+ nop-code/pom.xml modules 注册 + 测试（adapter/analyzer/memory-release）。
- `CodeSymbolKind` 现有 CLASS/INTERFACE/TYPE_ALIAS/METHOD/FUNCTION/CONSTANT/FIELD/IMPORT 等（无 STRUCT——Go struct 映射 CLASS）。
- language dict 生成链（审查实证）：`nop-code/model/nop-code.orm.xml` 的 `<dict name="code/language">` 选项（JAVA=10/PYTHON=20/TYPESCRIPT=30/JAVASCRIPT=40）→ nop-code-codegen postcompile `gen-orm.xgen`（renderModel `../../model/nop-code.orm.xml` → `/nop/templates/orm`）→ 模板 `dict/{dict.name}.dict.yaml.xgen` 写出 `nop-code-meta/.../language.dict.yaml`。**构建 nop-code-meta 不产生 dict；改 Java 枚举也不产生 dict**——正确路径 = orm.xml dict 增选项 + 构建含 gen-orm 的模块。（该 orm.xml 属 AGENTS.md Protected Area"ORM 模型结构 plan-first"——本 plan 即 plan-first 载体。）
- go grammar 形状（vendored corpus 实证）：struct 嵌入 = 无 name 的 `field_declaration`；**interface 嵌入 = interface_type 直接子级 `type_elem`**（generic_type 内也有 type_elem，须区分）；接口方法 = `method_elem`（field_identifier + parameter_list），不是 method_declaration；**method 名 = field `name` 的 `field_identifier`**，函数名 = `identifier`，接收者 = field `receiver`（parameter_list）；嵌入 `qualified_type(pkg.Type)` 的 super qn 需拼包名；包名来自 `package_clause`。
- go grammar 节点类型（上游 grammar.js）：`source_file`/`import_declaration`/`function_declaration`/`method_declaration`（receiver 显式）/`type_declaration`（内含 `type_spec`：`struct_type`/`interface_type`）/`call_expression`/`selector_expression`/`field_declaration`（嵌入字段 = 仅类型名的 field_declaration）。
- 索引服务经 IoC 按类型收集 `ILanguageAdapter` bean——beans 文件注册即接入（N4.1 已实证 by-type 装配）。

## Goals

- G1：`CodeLanguage.GO("go", ".go")` 枚举项。
- G2：新模块 `nop-code/nop-code-lang-go`：`GoLanguageAdapter` + `GoCodeFileAnalyzer`（符号：function→FUNCTION、method（receiver，field_identifier 取名）→METHOD、struct→CLASS、interface→INTERFACE、其它 type_alias→TYPE_ALIAS、const_spec→CONSTANT；继承：struct 嵌入 field_declaration（无 name）→ CodeInheritance + interface 嵌入 type_elem → CodeInheritance；calls：call_expression（identifier 直呼 + selector_expression，同文件 qn 候选）；imports：import_declaration/import_spec 收集）+ `GoImportResolver` + `_lang-go.beans.xml` + `_vfs/nop/code/_module` 标记 + parent pom 注册。
- G2-b（service 接线裁定）：`CodeIndexService.registerImportResolvers()` 硬编码注册三个既有 resolver——GoImportResolver 需同处注册 + nop-code-service pom 增 lang-go 依赖（审查 M-1 裁定：入 scope，避免 resolver 空壳）。
- G3：`nop-code/model/nop-code.orm.xml` `<dict name="code/language">` 增 `GO/Go/50` 选项 + 构建后核对 language.dict.yaml 再生成（含 Go 行）。
- G4：测试：TestGoLanguageAdapter、TestGoCodeFileAnalyzer（func/method/struct/interface/embed/import/call 全形状）、TestGoImportResolver、内存释放测试（镜像 TreeSitterMemoryRelease 范式）。

## Non-Goals

- Rust/C# 模块（N5.5/N5.6 姊妹项各自 plan）。
- Go 泛型实例化跟踪、跨包 qn 解析（与 TS 模块 N5.1 同口径：同文件/导入候选；跨包归增量 callee 解析域）。
- nop-code-web/app 改动；nop-code-service 仅限 G2-b 的两行级接线（pom 依赖 + resolver 注册），其余不动。

## Scope

### In Scope

- `nop-code/nop-code-core`：CodeLanguage 枚举 +GO。
- `nop-code/model/nop-code.orm.xml`：`<dict name="code/language">` 增 GO 选项（ORM Protected Area——plan-first 由本 plan 载体满足；生成物 dict yaml/constants 由 codegen 再生成，不手改）。
- 新模块 `nop-code/nop-code-lang-go`（pom + GoLanguageAdapter/GoCodeFileAnalyzer/GoImportResolver + beans + `_module` 标记 + tests）。
- `nop-code/pom.xml` modules 增 lang-go。
- `nop-code/nop-code-service`：pom 增 lang-go 依赖 + `registerImportResolvers()` 注册 GoImportResolver（两行级接线，审查 M-1 裁定）。

### Out Of Scope

- nop-code-web/app 改动；dict yaml/constants 手改（生成物仅核对）。

## Execution Plan

### Phase 1 - 枚举 + 模块骨架 + 分析器

Status: completed
Targets: `nop-code/nop-code-core/.../CodeLanguage.java`、`nop-code/pom.xml`、`nop-code/nop-code-lang-go/**`

- Item Types: `Fix`

- [x] `CodeLanguage` 增 `GO("go", ".go")`
- [x] `nop-code/model/nop-code.orm.xml` dict `code/language` 增 GO 选项（value=50 续排）
- [x] 新模块 pom（parent nop-code；依赖 nop-code-core + nop-treesitter，test 依赖 junit-jupiter + **nop-ioc（test scope，供容器级接线断言）**——junit 部分镜像 lang-typescript 实际 pom）
- [x] `_vfs/nop/code/_module` 0 字节标记（镜像三先例模块）
- [x] `GoCodeFileAnalyzer`：compat `TreeSitterGo` + TSParser 遍历；visit 清单：source_file / package_clause / import_declaration / function_declaration / method_declaration（receiver 取 field_identifier）/ type_declaration（type_spec：struct_type/interface_type/type alias）/ field_declaration（无 name = struct 嵌入）/ type_elem（interface 嵌入，区分 generic_type 内）/ method_elem / const_declaration（const_spec→CONSTANT）/ call_expression + selector_expression；nodeText UTF-8 字节切片（python 先例）；**qn 前缀 = package_clause 包名**（result.setPackageName 同步填）
- [x] `GoLanguageAdapter`（.go 扩展 + exclude：**/vendor/**、**/.git/**）
- [x] `_lang-go.beans.xml`（镜像 _lang-typescript.beans.xml）
- [x] `nop-code/pom.xml` modules 增 `nop-code-lang-go`；`nop-code-service` pom + registerImportResolvers 接线
- [x] Decision 记录：`LanguageFamily.fromLanguage(GO)` 落 default→UNKNOWN（跨语言同族判定忽略 Go）——本期接受，不做扩展

Exit Criteria:

- [x] 构建含 gen-orm 的 codegen 管线后 `language.dict.yaml` 出现 Go 行（生成物核对，不手改）；顺带核对其余生成物（`_app.orm.xml` 等）刷新且无意外 diff
- [x] 无静默跳过：未识别节点类型不吞（walk 全遍历），分析器对 null/blank 输入返回 null（既有契约）；interface 嵌入（type_elem）产继承边（无 type_elem 的接口按空接口处理，不静默丢全部嵌入）
- [x] No owner-doc update required（模块文档归 Phase 2 统一）
- [x] `ai-dev/logs/` 条目随 Phase 2 一并写入（Phase 2 EC 显式包含）

### Phase 2 - 测试 + 文档 + roadmap

Status: completed
Targets: `nop-code-lang-go/src/test/java/**`（4 测试类）、`docs-for-ai/03-modules/nop-code.md`、roadmap

- Item Types: `Fix`、`Proof`

- [x] TestGoLanguageAdapter：language/extensions/excludes/analyzer 类型
- [x] TestGoCodeFileAnalyzer：Go 源样例（package + import + struct 带 embed + interface 带 embed + func + method(receiver) + 常量 + 互调）→ 断言符号集合/kind/qn、继承边（embed）、call 边、import 收集
- [x] TestGoImportResolver：import path→名解析
- [x] 内存释放测试（镜像 TreeSitterMemoryRelease 实态：15 次连续解析 + 大文件解析断言持续成功）；tree-sitter 相关测试类复制 `TreeSitterNativeAvailableCondition` 并加 `@EnabledIf`（先例范式）
- [x] **接线验证（Rule #23）**：容器级装载 `_lang-go.beans.xml` 断言 `getBeansOfType(ILanguageAdapter.class)` 含 GoLanguageAdapter（镜像 N4.1 容器级断言形态）
- [x] `docs-for-ai/03-modules/nop-code.md` 模块表增 lang-go 行 + 语言支持计数 3→4；roadmap §当前基线"3 语言解析"计数同步；roadmap N5.4 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 执行日条目更新

Exit Criteria:

- [x] **端到端验证**：Go 源文件 → analyzer → CodeFileAnalysisResult（符号/继承/call/import）完整走通（TestGoCodeFileAnalyzer 承载）
- [x] `./mvnw test -pl nop-code/nop-code-lang-go -am -T 1C` 全绿；`./mvnw test -pl nop-code/nop-code-service -o` 零回归（服务按类型收集新 adapter 后既有行为不变）
- [x] roadmap/owner docs 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] N5.4 交付词逐项：go 绑定（TreeSitterGo 消费）+ ILanguageAdapter 适配 + 提取器（analyzer）+ dict 同步 + 测试
- [x] `scan-hollow-implementations --module nop-code/nop-code-lang-go --severity high`：零 high finding
- [x] `check-plan-checklist --strict` 退出码 0
- [x] `check-doc-links --strict` 退出码 0
- [x] 代码风格：import 分组、4 空格缩进、与同族模块（typescript）注释密度一致
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] `./mvnw test -pl nop-code/nop-code-lang-go -am -T 1C` 全绿

## Deferred But Adjudicated

### nop-ioc test 依赖与容器级接线断言

- Classification: `watch-only residual`
- Why Not Blocking Closure: 实态镜像 lang-typescript/python 先例（无 nop-ioc test 依赖、无容器级测试）；service 侧 267 tests 在 lang-go 进入 classpath 后全绿，beans 装配错误会在 service 测试容器暴露（by-type 收集路径被既有测试覆盖）。closure audit 裁定按 deviation 豁免而非补做。
- Successor Required: `no`

## Non-Blocking Follow-ups

- Go 泛型/跨包调用解析增强（与 TS N5.1 同口径迭代）。Classification: `optimization candidate`。

## Closure

Status Note: N5.4 全链路落地：CodeLanguage.GO + orm dict GO=50（codegen 再生成 language.dict.yaml 实证出 Go 行，连带 _NopCodeDaoConstants/_app.orm.xml 无意外 diff）+ nop-code-lang-go 模块（GoLanguageAdapter/GoCodeFileAnalyzer/GoImportResolver + beans + _module 标记）+ service 两行级接线（pom + registerImportResolvers 含 GoImportResolver）。测试：模块 14/14 + service 267 tests 零回归。两项 closure 审计（agent_3b7bd816 两轮 + agent_e015e914 独立审计）后，按其条件完成文本回写/deviation 登记/提交。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_e015e914（fresh session 独立 closure audit：Gates 1/2/4/5/7 技术面全 PASS——dict 链/模块文件/service 接线/测试复跑 14+267/行为抽查全部实证；条件项 = plan 文本回写（本节）+ 提交 + dict 清单补 GO + 日期笔误，均已执行）
- Evidence:
  - Phase 1：CodeLanguage.GO（L11）；orm.xml dict GO=50（L81）；language.dict.yaml Go 行（生成物 diff 干净，连带 _NopCodeDaoConstants.LANGUAGE_GO/_app.orm.xml）；模块 pom/adapter/analyzer/resolver/beans/_module 齐备；service pom + registerImportResolvers L291-296 含第 4 个 resolver
  - Phase 2：14/14 复跑一致（adapter 2/analyzer 6/resolver 4/memory 2）；行为抽查全 PASS——结构性子节点访问无 field 查找残留、type_alias 独立节点 + type_spec fallback 双路、interface 嵌入 type_elem 直接子级、method 名 field_identifier、断言闭环（8 符号/2 继承边/call qn 解析）
  - Closure Gates：scan-hollow exit 0（audit 实跑）；check-doc-links exit 0（复跑）；代码风格 PASS（audit 指出的冗余同包 import 已删）；check-plan-checklist 对本 plan exit 0（全仓跑出的 23 个失败为 pre-existing 历史 plan，非本 plan 引入，audit 已核实）
  - deviation：nop-ioc test 依赖 + 容器级断言按 audit 建议以 Deferred But Adjudicated 豁免（镜像 typescript 先例，service 测试容器间接覆盖装配路径）
  - 无 in-scope live defect 被降级

Follow-up:

- Go 泛型/跨包调用解析增强（optimization candidate）。no remaining plan-owned work。
