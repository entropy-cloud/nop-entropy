# 审核页面对比展示契约

**日期**：2026-09-22（修订：渲染模式对齐业内实证）
**范围**：nop-sys（getReviewDetail 输出）/ nop-sys-web（审核前端）
**状态**：active
**上游**：`01-architecture-baseline.md` §7；`02-snapshot-nested-data.md` §4/§5

---

## 一、设计结论

1. **渲染分三模式，表单复用为缺省**：CRUD 实体对象复用其既有 xview 只读表单渲染 before/after 两份数据并高亮变更字段（Mode A，对齐 Syncope 的实证做法）；diff 树表格用于自定义动作与精确定位变更（Mode B）；原样 JSON 是兜底（Mode C）。
2. **前端不比较数据**：变更字段集合（changedPaths）与期望后状态（expectedAfter）均由服务端计算下发；高亮 = 前端把 changedPaths 映射到表单字段的样式类。
3. **displayName 统一从目标对象 xmeta 解析**（平台既有机制，含 i18n），审核页不另建标签体系。

## 二、业内实证（设计依据）

开源 maker-checker / 审批界面实际采用的显示形式，按普遍度：

| 形式 | 实证 | 说明 |
|------|------|------|
| 原样 JSON / key-value | Mifos community-app Checker Inbox 直接展示 pending command 的 JSON 原文；Fineract API 也只返回 `commandAsJson`，不做 diff | 开源实现里最普遍的最低保障 |
| **复用标准实体表单 + (修改前, 修改后) 双实体 + 字段级变更标注** | Syncope：`UserRequestFormDetails` 用 `AnyOperations.patch(previousUserTO, userUR)` 构造 after，与 before 一起传给**标准用户向导表单**的只读模式（`UserWizardBuilder(previousUserTO, userTO, ...)`）；各字段面板对比 previous 显示 changed 标注/旧值（如 `UserDetails` 对 username 显示旧值标注、`Resources` 面板显示变更标记） | 实体审批的成熟做法：**没有独立 diff 组件**，复用既有表单 |
| 行级 `old -> new` 文本 diff | Terraform CLI（`internal/command/jsonformat`）：`~ field = "old" -> "new"`，高危变更附 `# forces replacement` 标注 | IaC 领域标准；Atlantis 等 PR 审批工具同款 |
| 结构化字段级双列 diff 表格 | 产品化审批台（ServiceNow 类）常见；开源 maker-checker 实现中少见 | 适合自定义载荷与精确定位 |

结论：原 04 版把双列 diff 表格作为唯一形态不符合主流；**表单复用才是实体型审批的通行做法**，diff 表格降级为辅助模式。

## 三、渲染模式

### Mode A：表单复用（缺省，对象有 xview 表单时）

- 审核壳页面按 `record.bizObjName` 动态加载目标对象的 xview（页面本就是按 bizObj 生成的，如 `nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysCheckerRecord/main.page.yaml` 一行 `web:GenPage` 即由 view.xml + xmeta 生成页面），取其只读表单 schema（`<form id="view"/>`）。
- 用 `getReviewDetail` 下发的 beforeData / expectedAfter 两份数据各渲染一份只读表单；**不走原页面的数据查询**（数据由审批记录快照提供，而非实时拉取——保证审核的是快照）。
- 高亮：服务端下发的 `changedPaths`（属性名路径集合）映射到表单字段样式类（ADDED 绿 / REMOVED 红 / UPDATED 黄）；to-many 子表行按主键匹配分组标注。
- displayName/控件类型/枚举翻译天然来自同一 xmeta，**零额外标签工作**。

### Mode B：diff 树表格（自定义动作无表单 / 需精确定位时）

- 消费 `diffTree`（服务端计算，见 02 §五），双列（修改前/修改后）+ 变更高亮 + to-many 三组分组；线框图见 §六。
- 触发条件：动作声明的载荷无对应 xview 表单；或 checker 手动切换"变更明细"视图。

### Mode C：原样 JSON（兜底）

- 展示 requestData 原文（语法高亮折叠），对齐 Fineract/Mifos 最低保障；任何记录在任何模式都可切换到该视图。

## 四、displayName 解析（平台机制，非新建设计）

平台既有链路：orm `displayName` → 生成 xmeta（`<prop name="bizObjName" displayName="业务对象名" i18n-en:displayName="Biz Object Name"/>`，如 `nop-sys/nop-sys-meta/src/main/resources/_vfs/nop/sys/model/NopSysCheckerRecord/NopSysCheckerRecord.xmeta`）→ xview 字段只声明 prop 名（`<col id="bizObjName"/>`，无 label）→ 页面生成/渲染时从 `<objMeta>` 引用的 xmeta 解析标签与控件。

审核页规则：

- **Mode A**：标签来自复用表单自身，零工作。
- **Mode B**：DiffNode.label 由服务端在计算 diff 时按**请求 locale** 从目标对象 xmeta 解析（注意 xmeta 携带 `i18n-xx:displayName`，多语言部署必须走 locale 解析而非固定中文）；解析不到时回退属性名。
- 敏感掩码标注（`masked`）仍由服务端 diff 层施加，与标签机制正交。

## 五、GraphQL 契约（修订）

`getReviewDetail(recordId)` 返回：

```
{
  record: { id, bizObjName, bizObjAction, makerName, requestTime,
            baseVersion, currentVersion, baseStale, status, expireTime, emergency },
  beforeData:    {...}|null,      # 提交时基线投影（Mode A"修改前"数据源）
  expectedAfter: {...}|null,      # 服务端按保守合并语义推导（Mode A"修改后"数据源）
  changedPaths:  ["items[3].price", ...],   # 变更属性路径集合（Mode A 高亮依据）
  diffTree:      DiffNode|null,   # 查询参数请求时才计算（Mode B）
  rawRequest:    "..."            # Mode C
}
```

- `expectedAfter` 由服务端按 02 §四合并语义从 beforeData + requestData 推导；create 型 beforeData 为 null（表单单份数据全绿）；delete 型 expectedAfter 为 null（单份基线全红）。
- `diffTree` 改为按需计算（默认只回 changedPaths，省大载荷）。
- 列表页 `findPendingItems` 不变（changeSummary 预存摘要）。

## 六、Mode A 页面布局（线框图，主形态）

```
+--------------------------------------------------------------------------------------+
| 待审 #1042   订单:ORDER-20260922-001   动作: save   maker: 张三   2026-09-22 14:20    |
| [基线一致 ✓]  [过期于 2026-09-23 14:20]            [高危: 含子表变更 ×3]              |
+--------------------------------------------------------------------------------------+
| [修改前(只读表单，复用订单 view 表单)]     |  [修改后(只读表单，变更字段高亮)]          |
|  订单备注: (空)                            |   订单备注: 加急件，周五前送达  [新增]    |
|  总金额:   1,280.00                        |   总金额:   1,380.00            [变更]   |
|  收货手机号: 138****1234 (掩码)            |   收货手机号: 139****5678 (掩码) [变更]   |
|  ▼ 订单明细 (只读子表)                     |   ▼ 订单明细 (子表行标注 新增/变更/删除)  |
+--------------------------------------------------------------------------------------+
| 视图切换:  (●) 表单对比   ( ) 变更明细   ( ) 原始JSON                                 |
| 审批意见 (reject 必填)  [___________________________________________]                |
|                                          [ 驳回 ]   [ 批准 ]   [ 撤回(仅maker) ]     |
+--------------------------------------------------------------------------------------+
```

Mode B 线框（变更明细）保留原双列 diff 表格设计：字段/修改前/修改后三列 + 子表"新增行/变更行/删除行"三组折叠 + 仅看变更开关。

## 七、渲染约定（两模式共用）

1. **staleness 预检条**：`baseStale=true` 时头部红色横幅 + 禁用批准按钮。
2. **掩码**：表单模式与树模式均显示定长掩码 + tooltip；前端不申请原文。
3. **危险信号前置**：delete 动作红底横幅；批量操作标注影响行数。
4. **操作按钮与状态联动**：PENDING 才显示批准/驳回；withdraw 仅 maker 可见；EXECUTE_FAILED 显示 retry/force-cancel（super-user）；终态转只读审计视图。
5. **Inbox 列表**：列 = 审批编号、业务对象+业务键、动作、maker、提交时间、剩余时限、changeSummary、高危标记；缺省按剩余时限升序。
