# 审核页面对比展示契约

**日期**：2026-09-22
**范围**：nop-sys（getReviewDetail/diffTree 输出）/ nop-sys-web（审核前端）
**状态**：active
**上游**：`01-architecture-baseline.md` §7；`02-snapshot-nested-data.md` §4/§5

---

## 一、设计结论

1. **前端不比较数据**：`getReviewDetail` 返回记录元信息 + 服务端计算的 `diffTree`；掩码、大小写、归一化差异全部在服务端消化。
2. **对比语义三组化**：字段级 UPDATED / 子表 ADDED / 子表 REMOVED（删除类仅来自显式删除动作）；create 型整树 ADDED，delete 型整树 REMOVED。
3. **危险信号前置**：头部信息条直接给出"基线是否漂移（staleness 预检）""是否高危动作（delete/批量）"，checker 无需从 diff 里找风险。

## 二、GraphQL 契约

- `findPendingItems(filter)`：Inbox 列表，行含 `changeSummary`（预存摘要，免算 diff）、maker、提交时间、expireTime、高危标记。
- `getReviewDetail(recordId)`：返回：

```
{
  record: { id, bizObjName, bizObjAction, makerName, requestTime,
            baseVersion, currentVersion, baseStale,        # 服务端预检 staleness
            status, expireTime, emergency },
  diffTree: DiffNode
}

DiffNode = {
  path: "order.items[3].price"        # 属性名路径，前端按 objMeta 翻译显示名
  label: "单价"                        # objMeta displayName，服务端填好
  change: "ADDED" | "REMOVED" | "UPDATED"
  kind: "FIELD" | "TO_ONE" | "TO_MANY"
  oldValue / newValue: 归一化标量 | 子节点
  masked: true?                        # 敏感字段，值已替换为定长掩码
  truncated: true?                     # 大文本仅存摘要
  children: DiffNode[]?
  group?: "added" | "removed" | "updated"   # 仅 TO_MANY 的直接子节点
}
```

## 三、审核页面布局（线框图）

```
+--------------------------------------------------------------------------------------+
| 待审 #1042   订单:ORDER-20260922-001   动作: save   maker: 张三   2026-09-22 14:20    |
| [基线一致 ✓]  [过期于 2026-09-23 14:20]            [高危: 含子表变更 ×3]              |
+--------------------------------------------------------------------------------------+
| 变更对比 (6 项变更)                                     [展开全部] [仅看变更]        |
+--------------------------------------------------------------------------------------+
| 字段                  | 修改前                    | 修改后                             |
|-----------------------+---------------------------+------------------------------------|
| 订单备注              | (空)                      | 加急件，周五前送达          [新增] |
| 总金额                | 1,280.00                  | 1,380.00                           |
| 收货手机号            | 138****1234      (掩码)   | 139****5678               (掩码)   |
| ▼ 订单明细 items (3)                                                                  |
|   ■ 新增行 [4]                                                                        |
|   | 商品 | (新增)  | 智能网关 ×1  金额 300.00                          [ADDED]  |    |
|   ■ 变更行 [2]                                                                        |
|   | 数量 | 2 → 3  | 单价 | 100.00 → 120.00                             [UPDATED] |   |
|   ■ 删除行 [1]                                                                        |
|   | 商品 | (删除)  | 传感器 ×2                                        [REMOVED] |    |
+--------------------------------------------------------------------------------------+
| 审批意见 (reject 必填)  [___________________________________________]                |
|                                          [ 驳回 ]   [ 批准 ]   [ 撤回(仅maker) ]     |
+--------------------------------------------------------------------------------------+
```

## 四、渲染约定

1. **默认"仅看变更"**：DiffTree 中 UNCHANGED 分支不下发（服务端裁剪），减少噪音；提供"展开全部"切换（含基线完整内容，用于 context 核对）。
2. **行高亮配色契约**：ADDED 绿、REMOVED 红、UPDATED 黄；delete 动作整树红底横幅提示不可逆。
3. **staleness 预检条**：`baseStale=true` 时头部红色横幅"对象在送审后被外部修改"，批准按钮禁用并引导 STALE 说明——避免 checker 批完才吃 EXECUTE_FAILED。
4. **掩码**：`masked` 节点显示定长掩码 + tooltip"敏感字段已掩码"；前端不申请原文（服务端也不提供）。
5. **大文本**：`truncated` 节点显示摘要 + "查看原文"按需拉取（同一接口的 detail 扩展）。
6. **create 型**：无双列，单列树形表单视图全绿；delete 型：单列只读基线视图全红。
7. **操作按钮与状态联动**：PENDING 才显示批准/驳回；withdraw 仅 maker 本人可见；EXECUTE_FAILED 显示 retry/force-cancel（super-user）；APPROVED/STALE 等终态页面转只读审计视图。

## 五、Inbox 列表约定

- 列：审批编号、业务对象 + 业务键、动作、maker、提交时间、剩余时限、changeSummary 摘要、高危标记。
- 排序缺省：剩余时限升序（快过期在前）。
- 可见范围：租户内 + `:approve` 权限；provider SPI 收窄规则生效后按其过滤（前端无感知）。
