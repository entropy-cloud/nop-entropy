# amis `$xxx` 简写 vs `${xxx}` 表达式语法分析

> Status: resolved
> Date: 2026-07-15
> Scope: amis-formula 表达式引擎、nop 平台 view 模型、flux 渲染管线
> Conclusion: nop 平台统一使用 `${xxx}` 表达式语法，不引入 `$xxx` 简写。flux 已正确使用 `${xxx}`，无需补充。

## Context

- nop 平台所有 `xxOn` 属性（`visibleOn`、`disabledOn`、`requiredOn` 等）需要使用 amis-formula 表达式语法
- amis 存在两种变量引用形式：`$xxx` 简写和 `${xxx}` 表达式
- 需要确认 nop 平台和 flux 渲染管线应采用哪种语法，以及 flux 是否需要补充 `$xxx` 支持

## Analysis

### 1. amis `$xxx` 简写机制

#### 词法解析

`$xxx` 在 amis-formula 词法分析器（`lexer.ts:292-321`）的 `raw()` 函数中处理：

```js
// 支持旧的 $varName 的取值方法
const match = /^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*/.exec(input.substring(i + 1));
```

- 只匹配 `[a-zA-Z0-9_]` 字符和 `.` 点号路径（如 `$user.name`）
- **不支持**：空格、`|`（过滤器）、`(`（函数调用）、运算符、`[`（索引访问）、`:`（命名空间）
- 解析器函数名为 `oldVariable()`（`parser.ts:848-874`），源码注释明确标注为"旧的"

#### 能力限制

| 能力 | `$xxx` | `${xxx}` |
|------|--------|----------|
| 简单变量引用 `$id` / `${id}` | ✅ | ✅ |
| 点号路径 `$user.name` | ✅ | ✅ |
| 过滤器 `$xxx\|html` | ❌ 静默失败 | ✅ `${xxx\|html}` |
| 函数调用 | ❌ | ✅ `${AVG(1,2)}` |
| 算术/逻辑运算 | ❌ | ✅ `${a + 1 && b}` |
| 三元表达式 | ❌ | ✅ `${a ? b : c}` |
| 命名空间 | ❌ | ✅ `${window:title}` |
| 索引访问 | ❌ | ✅ `${arr[0]}` |
| 在 `visibleOn` 等条件属性中 | ❌ 不可靠 | ✅ 推荐 |

`$xxx` 是 `${xxx}` 的**严格子集**——仅等效于 `${xxx}` 的纯变量查找场景。

#### amis 官方推荐

`docs/zh-CN/concepts/expression.md:56-69`：

> 第一种是早期的版本...灵活性虽高，但是安全性欠佳。**建议使用新版本规则**（`${xxx}`），新规则跟 tpl 模板取值规则完全一样，不用来回切换语法。

`data-mapping.md:382`（1.5.0+）：

> 更推荐用函数调用语法，如 `${xxx | html}` 改用 `${html(xxx)}`。

### 2. amis `${xxx}` 表达式机制

#### 词法解析

`${` 触发 `openScript()`（`lexer.ts:340-363`），进入 `SCRIPT` 模式，启用完整表达式语法：

```
rawScript() → complexExpression() → 完整 AST
```

支持：过滤器管道、函数调用、算术/逻辑/位运算、三元、数组/对象字面量、箭头函数、模板字符串、命名空间。

#### 类型保持

`resolveMapping()`（`dataMapping.ts:8-26`）的关键逻辑：

- **纯变量**（`"$id"` 或 `"${id}"`）→ `isPureVariable()` 返回 true → **保留原始类型**（数字、对象不被字符串化）
- **嵌入变量**（`"id-${id}"`）→ 走 `tokenize()` → **始终返回字符串**

两种语法在纯变量场景下行为一致。

#### 适用范围

`$xxx` 和 `${xxx}` 在以下场景都能工作：

- `tpl` 文本渲染
- `api.url` 路径插值
- `api.data` / `source` 数据绑定
- `name` / `id` 等属性

但 `visibleOn`/`disabledOn`/`requiredOn` 等条件属性使用 `evalExpression()`，**只可靠地支持 `${xxx}` 或旧版 JS 表达式**，`$xxx` 在此场景不可靠。

### 3. nop 平台当前实践

#### 标准 nop-web 管线（`web.xlib`）

- `xxOn` 属性值从 view 模型原样传递到 AMIS JSON
- nop view XML 中使用 `<visibleOn>${expr}</visibleOn>` 形式
- 不使用 `$xxx` 简写

#### Flux 管线（`flux-web.xlib`）

- `xxOn` 属性重命名后传递：`visibleOn → visible`、`disabledOn → disabled`、`requiredOn → required`、`readonlyOn → readOnly`
- 表达式文本**原样透传**，不做转换
- Flux 前端运行时使用与 AMIS 相同的 `${xxx}` 语法
- **不使用也不需要 `$xxx` 简写**

#### Flux 中 `$` 前缀的真实含义

flux-web.xlib 源码中出现的 `$xxx` 标记（`$i18n`、`$out`、`$config`、`$renderTemplateForScope` 等）是 **XLang 扩展方法调用**，不是变量引用。例如 `value.$i18n(default)` 等价于方法调用。

`${'$'}{xxx}` 模式（如 `flux-web.xlib:694`）是 XLang 的**字面量转义**——`${'$'}` 产出字符 `$`，最终在输出 JSON 中形成 `${xxx}` 字符串供前端运行时解析。

### 4. `$xxx` 简写的已知陷阱

1. **`$xxx | filter` 静默失败**：词法器匹配 `$xxx` 后立即跳出，`| filter` 变成纯文本输出。这是最常见 bug。
2. **`visibleOn` 中不可靠**：`evalExpression()` 不走 tokenize 管道，裸 `$xxx` 行为不确定。
3. **不支持命名空间**：`$window:title` 中 `:` 不匹配 `[a-zA-Z0-9_]`，解析失败。
4. **不支持索引访问**：`$arr[0]` 中 `[` 不匹配，解析失败。
5. **混用困惑**：同一项目中混用 `$xxx` 和 `${xxx}` 增加认知负担。

## Conclusion

- **nop 平台统一使用 `${xxx}` 表达式语法**，不引入 `$xxx` 简写
- **flux 无需补充 `$xxx` 语法**：flux 已正确使用 `${xxx}`，与 AMIS 共享同一运行时表达式语法
- `$xxx` 是 amis 的遗留语法（源码标注 `oldVariable()`），是 `${xxx}` 的严格子集，无优势
- nop view XML 中的 `xxOn` 属性必须使用 `${expr}` 形式，不能使用 `this.xxx` 或裸 `$xxx`

### 被否决的方案

- **在 flux 中支持 `$xxx` 简写**：否决。`$xxx` 能力受限（无过滤器、无函数、无运算），且与 `${xxx}` 混用会导致陷阱。统一用 `${xxx}` 更安全、更一致。

## References

- `~/app/amis-react19/packages/amis-formula/src/lexer.ts:292-321` — `$xxx` 词法处理
- `~/app/amis-react19/packages/amis-formula/src/parser.ts:848-874` — `oldVariable()` 解析
- `~/app/amis-react19/packages/amis-core/src/utils/tpl.ts:83-132` — `evalExpression()` 条件求值
- `~/app/amis-react19/packages/amis-core/src/utils/dataMapping.ts:8-26` — `resolveMapping()` 类型保持
- `~/app/amis-react19/docs/zh-CN/concepts/expression.md:56-69` — 官方推荐
- `~/app/amis-react19/docs/zh-CN/concepts/data-mapping.md:11,382` — 数据映射文档
- `docs-for-ai/02-core-guides/flux-rendering.md` — flux 渲染管线规范
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` — flux 生成器
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib` — AMIS 生成器
- `ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md` — AMIS vs Flux schema 对比
- `ai-dev/plans/290-flux-web-xlib-attribute-mapping-fixes.md` — flux xxOn 映射修正
