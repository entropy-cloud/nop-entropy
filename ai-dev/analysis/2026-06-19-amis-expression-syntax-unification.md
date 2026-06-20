# AMIS 表达式语法统一分析

> Status: open
> Date: 2026-06-19
> Scope: nop-entropy 前端页面表达式语法（`$id` vs `${id}`）、代码生成器 XPL 转义机制、amis 引擎兼容性
> Conclusion: （待评审后填写，见文末"建议结论"）

## Context

- AMIS 历史上存在多种表达式/取值语法，不同版本推荐写法不同。
- 当前 nop-entropy 项目中 `$id`/`$ids` 简写与 `${id}` 标准写法混用；代码生成器（`.xgen`/`.xpl`/`.xlib`）内部还存在两种 `$` 转义写法（`$$` 与 `${'$'}`）不统一。
- XPL 模板语法（`${expr}`）与 amis 表达式（`${xxx}`）冲突：代码生成器要输出 amis 字面量 `${xxx}` 时必须转义，否则会被 XPL 当作求值。
- 本分析调研 amis 官方文档（amis-react19）、`nop-chaos-next-master/amis-guide` 以及当前仓库实现，给出统一方案与改动点评估。

## 一、AMIS 表达式语法历史形态

来源：`amis-react19/docs/zh-CN/concepts/expression.md`、`template.md`、`data-mapping.md`，以及 `nop-chaos-next-master/amis-guide/02-reference.md`。

| 语法 | 出现场景 | 官方定位 | 关键限制 |
|------|---------|---------|---------|
| `${xxx}` | SchemaTpl（title/label/tpl/placeholder/confirmText）、SchemaExpression（visibleOn/hiddenOn/disabledOn/sendOn）、api url、api data | **推荐标准**，新旧规则统一于此 | 无 |
| `$xxx`（简写） | **仅数据映射场景**：模板字符串、api url、api 请求体 | 支持，但不强调（`data-mapping.md` 明确"通过 `${xxx}` 或 `$xxx` 获取"） | **SchemaExpression（visibleOn 等）无效**，那里必须 `${xxx}` 或纯 JS |
| 纯 JS `data.xxx === 1` | SchemaExpression（早期） | 已弃用，安全性差（直接 eval） | 与 `${}` 规则不一致，需来回切换 |
| lodash `<%- data.xxx %>` | tpl 字段的 JS 模板引擎模式 | 可选高级用法 | **不能与 `${xxx}` 交叉使用**；此模式内取值用 `data.xxx` 而非 `${xxx}` |

### 关键事实（来自 amis 官方文档）

1. `expression.md:56-69`：amis 表达式有两种语法——纯 JS（旧）和 `${ }` 包裹（新）。文档明确建议用新规则，"跟 tpl 模板取值规则完全一样，不用来回切换语法"。
2. `data-mapping.md:11`：数据映射"支持通过 `${xxx}` 或 `$xxx` 获取变量"——**确认 `$xxx` 简写在数据映射场景被引擎兼容**。
3. `data-mapping.md:30`：amis 遇到 `$` 会尝试解析变量；输出字面 `$` 需用反斜杠 `\$` 转义。
4. `template.md:129-144`：模板字符串 `${xxx}` 与 JS 模板引擎 `<% %>` 不可交叉使用。

### 适用字段对照（来自 `amis-guide/02-reference.md:46-50`）

- **SchemaTpl 字段**（接受 `$xxx` 简写）：`title`、`label`、`tpl`、`html`、`placeholder`、`tooltip`、`description`、`confirmText`
- **SchemaExpression 字段**（**不接受** `$xxx`，必须 `${xxx}`）：`visibleOn`、`hiddenOn`、`disabledOn`、`staticOn`、`sendOn`、`initFetchOn`

## 二、NOP 项目现状调研

### 2.1 `$id`/`$ids` 简写大量使用

- **保留层 view.xml**（非 `_gen`）：在 `nop-auth`/`nop-wf`/`nop-rule`/`nop-task` 中有 **39 个文件**使用 `$id`/`$ids`。
- **代码生成模板** `orm-web/.../_{objName}.view.xml.xgen`：**7 处**（源头，影响所有模块的 `_gen/_*.view.xml`）。
- 典型用法（`nop-auth/.../NopAuthUser/NopAuthUser.view.xml`）：
  - api url：`<api url="@mutation:NopAuthUser__delete?id=$id">`
  - data 映射：`<userId>$id</userId>`、`<userIds>$ids</userIds>`
- 生成模板源头（`_{objName}.view.xml.xgen`）：`?ids=$ids`、`?id=$id`、`<_ j:key="${parentProp}">$id</_>`。

### 2.2 Java 层无任何 `$xxx → ${xxx}` 转换

- `WebPageHelper.fixPage`（`nop-frontend-support/nop-web/.../page/WebPageHelper.java:94`）只处理：GraphQL url 空格转义、i18n、dialog/drawer className、group body 数组化。**不涉及 `$` 表达式**。
- `NormalizeApi`（`nop-web/.../xlib/web.xlib:865`）只处理 NOP 特有的 `{@xxx}` 模板（`{@pageSelection}`/`{@listSelection}`/`{@formSelection}`），通过 `$renderTemplateForScope("{@","}",genScope)` 替换。**不处理 `$id`**。
- 结论：`$id` 原样透传给 amis，**完全依赖 amis 引擎对 `$xxx` 数据映射简写的兼容**。

### 2.3 NOP 特有的 `{@xxx}` 模板（与本次统一无冲突，仅记录）

- `{@pageSelection}`、`{@listSelection}`、`{@formSelection}` 是 NOP 在 view 模型里用的占位符，由 `NormalizeApi` 在代码生成阶段替换为实际 GraphQL selection。
- 这是 NOP 自有机制，与 amis `$xxx`/`${xxx}` 是不同层面，本次统一不涉及。

## 三、XPL 模板语法与 amis 表达式的冲突（核心问题）

### 3.1 冲突根源

- XPL（nop-xlang 模板语言）用 `${expr}` 进行求值。
- amis 用 `${xxx}` 取值。
- 当代码生成器（`.xpl`/`.xlib`/`.xgen`）要生成 amis 字面量 `${xxx}` 时，若直接写 `${xxx}`，XPL 会把它当成表达式求值（变量 `xxx` 未定义则报错或输出空）。

### 3.2 项目内现有两种转义写法（已不统一）

| 写法 | 出处 | 输出结果 | 含义 |
|------|------|---------|------|
| `${'$'}{${labelProp}}` | `nop-web/.../xlib/web.xlib:904`（`HiddenAndLabel` 标签） | amis `${labelProp的值}`，如 `${userName}` | 表达式形式输出字面 `$` |
| `$${objMeta.displayProp}` | `nop-web/.../xlib/web/grid_crud.xpl:75`（`labelTpl`） | amis `$displayProp的值`，如 `$userName`（**简写形式**） | `$$` 转义输出字面 `$` |

- `${'$'}`：XPL 表达式，求值为字符串 `"$"`。
- `$$`：XPL 字符转义，在文本输出模式下也产出字面 `$`。
- 二者都能输出 `$`，但 `grid_crud.xpl:75` 输出的是 amis `$xxx` **简写**，而 `web.xlib:904` 输出的是 amis `${xxx}` **标准**——即便都叫"转义"，产出的 amis 语法形态也不一致。

### 3.3 全局转义使用规模

- `${'$'}` 转义：全仓库 `.xpl`/`.xlib` 中仅 `web.xlib` **1 处**。
- `$$` 转义：`grid_crud.xpl` **1 处**。
- 说明绝大多数代码生成器通过 `xpl:attrs` 属性赋值和 CDATA 脚本构造对象的方式绕开了字符串模板冲突，只有需要生成纯文本节点（`<tpl tpl="...">`、`<labelTpl>`）时才触发转义。

## 四、统一方案评估

### 4.1 目标

1. view.xml / 生成产物中统一使用 amis 标准写法 `${id}`，淘汰 `$id` 简写。
2. 代码生成器（XPL 模板）内部统一 `$` 转义方式。

### 4.2 分场景改动方案

**必须严格区分两类文件**，因为 `${}` 在两者中语义完全不同：

| 文件类型 | `${xxx}` 的含义 | 直接写 `${id}` 是否安全 |
|---------|----------------|----------------------|
| `.xgen` / `.xpl` / `.xlib`（XPL 模板） | **XPL 求值** | ❌ 危险，会被当变量 `id` 求值 |
| `.view.xml`（XDef 解析的模型） | 普通字符串属性值 | ✅ 安全，原样保留给 amis |
| `.page.yaml` 的 `x:gen-extends:` 段 | XPL 求值 | ❌ 段内需转义 |

#### 改动点 A：代码生成模板 `_{objName}.view.xml.xgen`（源头，优先级最高）

路径：`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/src/main/resources/_vfs/{moduleId}/pages/.../_{objName}.view.xml.xgen`

7 处 `$id`/`$ids`，行号 153/178/196/218/222/223/230。

- ⚠️ **不能**直接改成 `${id}`——因为 `.xgen` 是 XPL 模板，`${id}` 会被 XPL 求值（`id` 未定义）。
- 必须改成 **`${'$'}{id}`**（输出字面 `${id}` 给 amis）。
- 例：`?ids=$ids` → `?ids=${'$'}{ids}`，`<userId>$id</userId>` → `<userId>${'$'}{id}</userId>`。
- 改完后重新跑 `precompile/gen-page.xgen`，所有 `_gen/_*.view.xml` 自动更新。

#### 改动点 B：保留层 view.xml（39 个文件）

- view.xml 经 XDef 解析，**不是 XPL 求值**，`${id}` 作为字符串属性安全保留。
- 可直接把 `$id` → `${id}`、`$ids` → `${ids}`，语义不变（amis 数据映射场景两者等价）。
- 注意：view.xml 里 `visibleOn` 等 SchemaExpression 已经用 `${}`（如 `${status == 1}`），本次改动只影响 data 映射和 api url 段，不涉及表达式段。

#### 改动点 C：统一代码生成器的 `$` 转义方式

- `grid_crud.xpl:75`：`<labelTpl>$${objMeta.displayProp}</labelTpl>` → `<labelTpl>${'$'}{${objMeta.displayProp}}</labelTpl>`
  - 这样从输出 amis `$xxx` 简写改为输出 amis `${xxx}` 标准，与统一目标一致。
- `web.xlib:904`：已是 `${'$'}{${labelProp}}`，保持。
- 统一原则：**XPL 模板中输出 amis 字面 `${}` 一律用 `${'$'}{...}`**，淘汰 `$$`（`$$` 只产出 `$`，无法表达 `${}` 标准形态）。

#### 改动点 D：Java 层（无需改动）

- `WebPageHelper` 和 `NormalizeApi` 均无需改动——`$id`/`${id}` 都原样透传，amis 引擎自行解析。

### 4.3 风险评估

| 风险 | 等级 | 说明 |
|------|------|------|
| `.xgen` 里误用 `${id}` 导致 XPL 求值失败 | **高** | 必须用 `${'$'}{id}`；建议改完逐个模板执行 `gen-page.xgen` 验证 |
| amis 老版本不认 `${id}` | 低 | amis 1.5+ 全面支持 `${}`，nop-chaos-next 用的 amis 版本远高于此 |
| data 映射场景 `$id`→`${id}` 语义漂移 | 极低 | amis 官方明确两者在数据映射场景等价（`data-mapping.md:11`） |
| view.xml 中 `${}` 与文本拼接 | 低 | `${id}` 在 `?id=${id}` 这类 url 里 amis 正常解析；注意原 `$id` 后跟非空白字符可能截断，`${id}` 有明确边界反而更稳 |
| SchemaExpression 误改 | 无 | 本次不触及 `visibleOn` 等字段，它们本就用 `${}` |

## 五、建议结论

### 推荐方案：全面统一为 `${id}`，代码生成器内部统一 `${'$'}` 转义

1. **改源头（改动点 A）**：`_{objName}.view.xml.xgen` 7 处 `$id`/`$ids` → `${'$'}{id}`/`${'$'}{ids}`，重新生成所有 `_gen/_*.view.xml`。
2. **改保留层（改动点 B）**：39 个 view.xml 的 `$id`/`$ids` → `${id}`/`${ids}`（可直接字符串替换）。
3. **统一转义（改动点 C）**：`grid_crud.xpl:75` 的 `$$` → `${'$'}`，使代码生成器输出 amis `${}` 标准形态。
4. **Java 层不动（改动点 D）**。

### 被否决的方案

- **保留 `$id` 简写、仅统一 XPL 转义**：否决。理由：amis 官方主推 `${}`，`$xxx` 简写在 SchemaExpression 无效，长期混用增加心智负担；且 `$id` 在 url 中无明确结束边界，遇到 `$ids` 这类相邻场景易歧义。
- **在 Java 层加全局 `$xxx→${xxx}` 转换**：否决。理由：会掩盖源头问题，且 amis 的 `$xxx` 在数据映射合法、在表达式非法，无法用单一正则安全区分场景；正本清源应改在模型层（view.xml）和生成层（.xgen）。

### 执行前置条件

- 本分析为 `open` 状态，方案需评审确认后再进入 `ai-dev/plans/` 编写执行计划。
- 执行时建议分模块验证：先改一个模块（如 `nop-auth`）的 .xgen 模板 → 重新生成 → 启动验证页面渲染正常 → 再推广。

## Open Questions

- [ ] nop-chaos-next 前端使用的 amis 具体版本号？需确认 `$xxx` 简写与 `${xxx}` 在该版本的完全等价性（虽然官方文档支持，但保险起见需核对前端 amis 版本）。
- [ ] 是否存在 view.xml 之外的 `.page.json` / 手写 amis JSON 文件使用了 `$id` 简写？本次调研聚焦 view.xml 与代码生成器，未全量扫描 page.json。
- [ ] `@i18n:` 前缀字符串与 `$` 表达式是否会出现在同一字符串中导致转义交互？需在执行阶段抽样验证。

## References

- amis 官方文档（amis-react19）：
  - `concepts/expression.md` — 表达式语法（新旧规则、`${}` 推荐）
  - `concepts/template.md` — 模板字符串 vs JS 模板引擎，不可交叉
  - `concepts/data-mapping.md` — `${xxx}` 或 `$xxx` 取值、`\$` 转义
- `nop-chaos-next-master/amis-guide/02-reference.md` — SchemaTpl / SchemaExpression 适用字段对照、过滤器
- `nop-chaos-next-master/amis-guide/01-quickstart.md` — 常用代码段中的 `${xxx}` 用法
- 仓库实现锚点：
  - `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java:94` — `fixPage`（无 `$` 转换）
  - `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib:865` — `NormalizeApi`（仅处理 `{@xxx}`）
  - `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib:904` — `${'$'}` 转义用例
  - `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web/grid_crud.xpl:75` — `$$` 转义用例（待统一）
  - `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/.../_{objName}.view.xml.xgen` — 生成模板源头（7 处 `$id`/`$ids`）
- 相关文档：`docs-for-ai/02-core-guides/view-and-page-customization.md`（页面生成机制）
