# EQL 命名参数与注释 Hint 支持调研

> Status: resolved
> Date: 2026-10-18
> Scope: `nop-persistence/nop-orm-eql` 参数文法/绑定、注释与装饰器机制、方言 hint 能力；结论用于 EQL 在线编辑器设计
> Conclusion: v1 不在 EQL 语言层增加命名参数与注释 hint；控制台用「命名参数映射 + 顺序」覆盖命名参数 UX，提示/控制信息一律走既有 `@decorator`（结构化、可校验、可门控），并对装饰器做白名单/未知拒绝；语言级命名参数与数据库 `/*+ */` hint 透传列为 v2 待裁定的独立设计（plan-first）。

## 1. 问题

1. EQL 是否应增加**命名参数**（如 `:name`）支持？
2. EQL 需要**注释解析**：应把注释作为传统 hint（如 `/*+ ... */`）透传给底层数据库，还是复用现有注解（`@decorator`）机制？

## 2. 现状证据

### 2.1 参数：只有位置参数 `?`

- EQL 文法的参数标记只有一个：`sqlParameterMarker: QUESTION_`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:22-24`）；`?` 之外没有 `:name`/`#{name}` 规则。
- 编译器按出现顺序编号：`EqlTransformVisitor` 中 `int paramIndex = 0; ... setParamIndex(paramIndex++)`（`.../compile/EqlTransformVisitor.java:264-274`）。
- 执行期按索引取值：`SimpleParamBuilder.buildParams` 从 `input.get(paramIndex)` 取值（`.../param/SimpleParamBuilder.java:22-27`）；`CompiledSql.buildParams(List<Object>)` 依次调用各 builder（`.../compile/CompiledSql.java:146-150`）。
- `ICompiledSql` 只暴露 `getColumnBinders()/getFieldMetas()` 等，**不暴露参数名表**（`.../ICompiledSql.java`）；执行入口 `IQueryExecutor.executeQuerySql(session, compiled, markerValues, range, callback)` 的 `markerValues` 是位置列表（`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/IQueryExecutor.java:34-38`）。
- 平台在 **SQL-lib/SQL Builder 层**已有另一套参数化：`${expr}` 自动转 `?` + JDBC 参数（`docs-for-ai/05-examples/sql-lib-and-mapper.java:21-32`），以及 `:param` marker 的计数/展开（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md:353`）。该机制不等于 EQL 文本内命名参数。

### 2.2 注释：被 lexer 丢弃，AST/输出均不保留

- `Comments.g4` 把块注释/行注释全部送到 HIDDEN channel：`BLOCK_COMMENT ... -> channel(HIDDEN)`、`INLINE_COMMENT ... -> channel(HIDDEN)`（`nop-persistence/nop-orm-eql/model/antlr/Comments.g4:23-24`）。
- 解析器/构建器不读取 hidden token：EQL AST 没有 comment 字段；`AstToSqlGenerator`/`AstToEqlGenerator` 也没有注释输出（generator 中无 comment/decorator 打印逻辑）。
- 结论：注释在 EQL 编译链路上**既不是语义，也不会进入生成 SQL**；解析器只是“容忍注释存在”，不能透传。

### 2.3 装饰器：已存在结构化 hint 机制

- 文法：`sqlDecorator: AT_ name=sqlIdentifier_ (LP_ args=decoratorArgs_? RP_)?`，参数为字面量列表（`.../BaseRule.g4:423-426`）。
- 装饰器可挂在语句/select/from/表源/projection 等多个 AST 节点（`DMLStatement.g4:24,38,47,78,88,102,115,140,153,176...`），AST 节点实现 `IWithDecorators`（`.../ast/IWithDecorators.java:13-19`）。
- 已消费的装饰器只有三个：`@dump`、`@querySpace`、`@enableFilter`（`.../OrmEqlConstants.java`；`EqlTransformVisitor.java:318-320,526-537`、`EqlCompiler.java:75-80`）。
- **未知装饰器既不报错也不输出**：生成器没有 decorator 访问器，装饰器在 EQL→SQL 阶段被静默丢弃；因此拼写错误（如 `@enablefilter`）会静默失效。`@dump` 更是在编译内部直接打印 AST（`EqlCompiler.java:75-78`），必须编译前拒绝。
- 方言层没有 hint 模型：`dialect.xdef` 与方言 XML 未发现 optimizer hint 能力位/模板（grep `hint` 零命中）。

## 3. 方案对比

### 3.1 命名参数

| 方案 | 做法 | 优点 | 代价/风险 |
|---|---|---|---|
| A 语言级命名参数 | 文法新增 `:name`/`#{name}`；AST 新节点；`ICompiledSql` 暴露 name→index；执行 API 接受 Map | EQL 文本自描述；外部调用方友好；控制台/代码共用 | 触碰生成管线（ANTLR 生成物、AST、printer、codegen）；`ICompiledSql`/`IOrmSession` 公共 API 扩展；与平台 `${}`/`:param` 两套既有参数化概念重叠；需 plan-first 与全量回归 |
| B 控制台层命名参数映射 | 请求仍为有序数组；页面配置 `params`（名→scope 值）+ `paramOrder`（顺序）；参数面板把 `?` 位置映射为名称 | 零语言/内核改动；与现有 `?` 语义一致；可立即落地 | 命名只存在于控制台配置/历史；EQL 文本本身仍不可自描述；需严格数量校验防错绑 |
| C 混合（v2） | 在 B 基础上，可选在控制台前端把 `:name` 重写为 `?`（lexer 感知，避开字符串/注释），仅作为编辑器语法糖 | 用户可写命名参数文本 | 形成“第二种 EQL 方言”；历史/复制到其他地方不可用；必须明确仅控制台有效 |

**推荐**：v1 采用 B；A 列入 v2 评估（先由 owner 裁定是否有 EQL 文本自描述的正式需求）；C 不采用（维护两套语法代价高）。

### 3.2 注释 vs 装饰器 hint

| 方案 | 做法 | 优点 | 代价/风险 |
|---|---|---|---|
| D 装饰器 hint | 用 `@hint(...)`/`@dbHint('...')` 表达控制/透传；console 白名单 + 未知拒绝 | 结构化、可校验、可 gate、可跨方言；无需保留注释；不担心注释被格式化/复制丢失；安全边界清晰（可复用编译前扫描） | 语法非传统 SQL；外部粘贴的 `/*+ */` 需改写；DB hint 需生成器按方言位置输出 |
| E 注释 hint 透传 | 保留指定注释（如 `/*+ ... */`）到 AST 并在生成 SQL 原位置输出 | 符合 DBA 传统习惯；可直接粘贴带 hint 的 SQL | 需要改 lexer/文法（HINT token 或 hidden token 收集）、AST 新字段、printer 位置规则；普通注释是否保留需产品定义；注释被误当语义会带来安全/可重复性问题；影响面大 |
| F 混合 | 以 D 为语义来源，可选把“限定形式的 `/*+ */`”解析为同一个 AST hint（语法糖） | 兼顾传统写法与结构校验 | 仍需 grammar/AST 支持；建议等 D 稳定后再评估 |

**推荐**：v1 采用 D；明确“注释不承载语义”写进设计文档；E/F 列入 v2 评估（若确有 Oracle/MySQL optimizer hint 透传需求，再按 plan-first 设计 grammar 级限定形式）。

## 4. 结论与落点

1. **命名参数**：v1 不改变 EQL 文法；控制台通过 `params`（命名映射）+ `paramOrder`（顺序）支持命名参数 UX；请求/后端仍用有序数组并强制数量校验。语言级命名参数（方案 A）列入 v2 评估。
2. **注释/hint**：注释继续只做语法容忍、不承载语义；控制信息用既有装饰器；console 必须对装饰器做白名单（允许 `@enableFilter`、`@querySpace` 一致性校验；拒绝 `@dump` 与未知装饰器），避免静默 no-op。DB optimizer hint 透传（方案 D 的 `@dbHint` 或 E 的注释形式）列入 v2 评估。
3. 工作项：`ai-dev/backlog/eql-online-editor-roadmap.md` 的 WI-29（参数面板/命名参数映射，v1.5）、WI-30（hint/decorator 白名单门控，v1/M2）、WI-31（语言级命名参数与 `@dbHint` 透传评估，v2/plan-first）。

## 5. Open Questions

- [ ] 是否存在“EQL 文本必须自描述参数”的外部调用方需求？若有，方案 A 的优先级需 owner 重排。
- [ ] 数据库 optimizer hint 的实际需求方（Oracle/MySQL 为主）与可接受语法（`@dbHint` vs `/*+ */`）需产品/owner 裁定。
- [ ] 若做注释透传，普通注释是否保留/如何格式化，需单独定义（v1 明确不保留）。
- [ ] `@querySpace` 之外是否还有需要开放给用户的控制装饰器白名单，需与权限模型一起评审。
