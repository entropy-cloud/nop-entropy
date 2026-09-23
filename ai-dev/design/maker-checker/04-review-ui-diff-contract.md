# 审核页面对比展示契约

**日期**：2026-09-22（R3 修订于 2026-09-23：渲染目标锁定 Flux；双栏对照改为单表单 + 控件级标注/变换）
**范围**：nop-sys（getReviewDetail 输出）/ nop-web（flux-web review 页型）
**状态**：active
**上游**：`01-architecture-baseline.md` §7；`02-snapshot-nested-data.md` §4/§5

---

## 一、设计结论

1. **渲染目标只有 Flux 渲染器**（nop-chaos-flux），不考虑 AMIS 模式。
2. **单表单对比，不做双栏对照**：一份只读表单（数据 = expectedAfter），变更字段在**控件级**呈现差异。两种呈现（同一页型，字段级可混用）：
   - **标注模式（缺省）**：控件本身不变，label 旁挂 `labelRemark` 图标，悬浮显示"原值: xxx"，字段高亮（`inputClassName`）——即"注释中显示对比旧值"。
   - **行内变换模式（增强）**：变更字段的控件渲染为"新值 + 旧值删除线"行内双值——即"自动变换为对比显示"。
3. **标注以表达式静态声明、运行时求值**：per-bizObj 的静态审核页（路径缓存）中，变更字段的 className/description 写为引用页面作用域的表达式；数据经 Flux 内置的表单 `loadAction` 运行时拉取（`getReviewDetail`）。**不新增 schema 生成端点**。
4. diff 树表格保留为"变更明细"切换视图（自定义动作无表单时的主视图）；原样 JSON 为兜底视图。

## 二、业内实证（设计依据）

| 形式 | 实证 |
|------|------|
| 原样 JSON / key-value | Mifos community-app Checker Inbox 直接展示 pending command JSON；Fineract API 只返回 `commandAsJson` |
| 复用标准实体表单 + 字段级变更标注 | Syncope：`UserRequestFormDetails` 构造 (before, after) 传给标准用户向导表单只读模式，字段面板对比 previous 显示 changed 标注/旧值——**单表单 + 逐字段标注，无独立 diff 组件、无双栏** |
| 行级 `old -> new` 文本 diff | Terraform CLI（`internal/command/jsonformat`）：`~ field = "old" -> "new"` |
| 双栏并排表单对照 | 无开源 maker-checker 实现采用——布局成本高、窄屏不可用、双眼往返对照负担大 |

结论：单表单 + 控件级标注与 Syncope 一致，是复杂度与表现力的最优交点；双栏对照被否决。

## 三、Flux 挂载点（源码核验）

Flux 表单字段基接口 `BoundFieldSchemaBase`（nop-chaos-flux 仓库 flux-core 包的 `schema.ts`）原生携带对比渲染所需 hook，**标注模式零渲染器改动**：

- `labelRemark?: FieldRemarkSchema { icon, content, placement, trigger }` —— label 旁图标 + 悬浮内容（注释机制的现成载体）；
- `description?: string` / `hint?: string` —— 字段下方说明文字（旧值的行内展示位）；
- `inputClassName` / `className` / `labelClassName` —— 高亮样式钩子；
- `readOnly`、`when/visible`（表达式）—— 只读与条件渲染。

行内变换模式（增强）需 `flux-renderers-form` 字段渲染器新增 compare 变体（读取静态注入的 `oldValue` 渲染双值），属渲染器增强项，二期实施；一期标注模式已覆盖全部信息量。

## 四、渲染模式

### Mode A：表单 + 控件级标注（缺省，对象有 xview 表单时）

- 复用目标对象 xview 的只读表单 schema（`<form id="view"/>`），经 flux-web 生成器输出 Flux form；表单数据 = expectedAfter。
- 生成器遍历字段：`changedPaths` 命中的字段输出——`inputClassName: "mk-changed"` + `labelRemark { icon: 提示图标, content: "原值: <旧值文本>" }`；未命中字段原样输出。
- 子表（to-many）：行级标注——新增行 `mk-added`、删除行 `mk-removed`（只读展示旧值）、变更行 `mk-changed` + 行内字段 remark；行分组头显示计数。
- create 型：整表 `mk-added` 无 remark；delete 型：整表 `mk-removed`，控件值即旧值。

### Mode B：变更明细（切换视图 / 自定义动作缺省）

- 消费 `diffTree`（服务端计算，02 §五），树形"修改前/修改后"对照 + to-many 三组分组；无表单可复用时为主视图。

### Mode C：原始 JSON（兜底）

- `rawRequest` 语法高亮折叠展示；任何记录可切换。

## 五、GraphQL 契约

`getReviewDetail(recordId)` 返回：

```
{
  record: { id, bizObjName, bizObjAction, makerName, requestTime,
            baseVersion, currentVersion, baseStale, status, expireTime, emergency },
  beforeData:    {...}|null,      # 基线投影（旧值权威来源，生成器与 Mode B 消费）
  expectedAfter: {...}|null,      # 保守合并推导（Mode A 表单数据源）
  changedPaths:  ["items[id=123].price", ...],   # 主键定位路径集合（生成期判定 + Mode B）
  diffTree:      DiffNode|null,   # 按需计算（Mode B）
  rawRequest:    "..."            # Mode C
}
```

- **changedPaths 路径规则**：子集合成员以主键定位（`items[id=123].price`），禁止位置索引（同一行在 before/after 数组中位置不同）；新增行仅存在于 after，删除行仅存在于 before。
- **消费方式**：`getReviewDetail` 由审核页表单的内置 `loadAction` 调用；审核页本体为 per-bizObj 静态页（路径缓存），标注表达式在渲染期对页面作用域求值（机制见 §六）。
- displayName：Mode A 复用表单自带（同一 xmeta）；Mode B 的 DiffNode.label 由服务端按请求 locale 从目标 xmeta 解析（xmeta 携带 `i18n-*:displayName`，禁止固定中文）。
- 敏感掩码：掩码替换发生在快照/ diff 服务端层；生成器输出的 remark 旧值文本同样使用掩码后文本。
- 列表页 `findPendingItems` 不变（changeSummary 预存摘要）。

## 六、落地机制（契约级）

> R4 修订（2026-09-23）：否决"新增 getPageSchema(recordId) schema 生成端点"——改用 Flux 内置动态加载 + 表达式标注，schema 端点不必要。

1. **审核页 = per-bizObj 的静态 review 页**（与 `main.page.yaml` 同族的一行 GenPage 调用，可由代码生成模板统一产出或 Delta 注入；经 PageProvider 按路径缓存）。页内 schema 含目标对象 view 表单与标注表达式，**不含任何记录数据**——路径缓存语义安全。**不新增 schema 生成端点**。
2. **表单数据运行时加载（Flux 内置）**：`FormSchema.loadAction/autoLoad`（flux-renderers-form `form-load-action.ts`）调 `getReviewDetail(recordId)`——expectedAfter 进表单数据域（控件按 `name` 绑定），beforeData/changedPaths/record 元信息进页面作用域。
3. **标注 = 表达式静态声明 + 运行时求值**：变更字段的 `inputClassName`/`description` 写成引用作用域的表达式（如 `"${includes(changedPaths,'items[id=123].price') ? '原值: ' + beforeData.items[123].price : ''}"`）。机制依据：flux-compiler `runtime-value-compilation.ts` 把 schema 属性值编译为 static/dynamic 两类 runtime value（嵌套对象递归编译），dynamic 值在渲染期对数据作用域重求值。
4. **Spike 前置（两式二选一收敛）**：① labelRemark/description 表达式端到端求值验证（field-frame 的 `title={labelRemark.content}` 须收到求值后字符串）；② 子表行级表达式路径解析。未达标的 fallback：回到 schema 内联端点方案（`getPageSchema(recordId)`，本修订前的 R3 设计，保留为备选）。
5. **后台回调时机 = `getReviewDetail`**（数据加工管线：加载记录 → 掩码 → merge/changedPaths 计算 → 可选 per-bizObj 定制 SPI `IMakerCheckerReviewDecorator`）。schema 侧无端点、无生成期回调。
6. **只读保证**：表单无 submit 绑定；审批按钮挂审核壳页面，调用审批记录动作。
7. **二期增强**：`flux-renderers-form` 字段渲染器 compare 变体（行内"新值 + 旧值删除线"），由 schema 静态注入 `oldValue` 驱动。

## 七、页面布局（线框图，单表单标注模式）

```
+--------------------------------------------------------------------------------------+
| 待审 #1042  订单:ORDER-20260922-001  动作:save  maker:张三                            |
| [基线一致 ✓] [过期于 09-23 14:20]                        [高危: 子表变更 ×3]          |
+--------------------------------------------------------------------------------------+
| 订单表单（复用 view 表单，只读；未变更字段正常展示，无任何标注）                        |
|  订单编号   ORDER-20260922-001                                                        |
|  订单备注   加急件，周五前送达      ⓘ原值: (空)                  ← 高亮+悬浮标注       |
|  总金额     1,380.00               ⓘ原值: 1,280.00                                  |
|  收货手机号 139****5678            ⓘ原值: 138****1234（掩码）                        |
|  ▼ 订单明细  [1 新增 / 2 变更 / 1 删除]                                               |
|   1234 智能网关 ×1  300.00                                    [新增行·绿]            |
|   1235 传感器A ×3  360.00   ⓘ数量原值 2 · 单价原值 120.00      [变更行·黄]            |
|   1236 传感器B ×2                                             [删除行·红·显示旧值]   |
+--------------------------------------------------------------------------------------+
| 视图: (●) 表单+标注   ( ) 变更明细   ( ) 原始JSON                                     |
| 审批意见 (reject 必填) [____________________]   [ 驳回 ]  [ 批准 ]  [ 撤回(仅maker) ] |
+--------------------------------------------------------------------------------------+
```

## 八、渲染约定（各模式共用）

1. **staleness 预检条**：`baseStale=true` 红色横幅 + 禁用批准按钮。
2. **掩码**：标注/明细/原始 JSON 三个视图的敏感字段均为掩码文本，前端不申请原文。
3. **危险信号前置**：delete 动作红底横幅；批量操作标注影响行数。
4. **操作按钮状态联动**：PENDING 才显示批准/驳回；withdraw 仅 maker；EXECUTE_FAILED 显示 retry/force-cancel（super-user）；终态只读审计视图。
5. **Inbox 列表**：审批编号、业务对象+业务键、动作、maker、提交时间、剩余时限、changeSummary、高危标记；按剩余时限升序。
