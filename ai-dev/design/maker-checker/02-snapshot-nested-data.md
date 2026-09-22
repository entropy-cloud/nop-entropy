# 快照与复杂嵌套数据设计

**日期**：2026-09-22
**范围**：nop-sys（快照/归一化/diff 契约实现）
**状态**：active
**上游**：`01-architecture-baseline.md` §1/§3/§6

---

## 一、设计结论

1. **复杂嵌套数据靠"整树快照"天然覆盖**：GraphQL mutation 输入本就是树（对象字段 + to-one 引用 + to-many 子集合），机制捕获整棵输入树为 requestData，不做任何拍平。
2. **快照三元组**：requestData（归一化输入）+ beforeData（按输入形状投影的当前数据基线）+ baseVersion（实体 version）；expected-after **不落库**，由 beforeData + requestData 在查询时推导。
3. **diff 服务端唯一出口**：`IEntityTreeDiffCalculator` 在审核详情查询时计算 DiffTree，掩码在计算层施加。
4. 子表集合合并语义取**保守合并**（按 id 更新 + 新增，不隐式删除）；删除类变更走显式删除动作单独送审。

## 二、归一化（IInputCanonicalizer）

requestData 存储的是**归一化后**的输入，规则：

- 剔除客户端控制字段（`__typename` 等）与机制自身字段（`idempotencyKey`）。
- **absent 与 explicit null 区分保留**：未提供的字段不出现在 JSON；显式传 null 保留为 null——二者在合并语义中不同（覆盖为空 vs 保持原值），diff 展示必须可区分。
- 标量标准化：日期时间 ISO-8601；decimal 以**字符串**形式存储，杜绝浮点精度失真导致的假 diff。
- 字段序：Crud 输入按 objMeta 属性序输出；自定义动作的未知字段保留原序（diff 与 hash 均基于归一化结果，保证 requestHash 稳定）。
- 大小上限：默认 1MB（列宽），超限**显式拒绝**并提示拆分操作；阈值可配。

## 三、beforeData 投影（IEntitySnapshotProjector）

投影原则：**基线形状 = 输入形状**。只投影输入涉及的字段与子集合，不全量 Dump 实体：

```
projectBefore(bizObjName, id, input):
    entity = load(bizObjName, id)
    if entity 不存在: return null          # create 型，无基线
    for field in input:
        if field 是 to-many 集合:
            children = 当前子集合，按输入子行的字段集投影，按主键排序
            输出 children 投影列表
        else:
            输出 entity[field] 当前值（标量/to-one 引用 id）
    return { data: 投影树, baseVersion: entity.version }
```

- 投影在提交事务内执行（与 guard、try 校验同事务），保证基线与加锁时刻一致。
- baseVersion 取实体乐观锁 version（平台实体普遍带 `versionProp`，如 `nop-sys/model/nop-sys.orm.xml` 各实体）。
- 无实体语义的自定义动作（纯服务调用）：beforeData = null，审核页仅展示输入内容；如动作能提供"受影响对象"语义，可由 tryMethod 返回投影上下文（可选扩展，一期不做）。

## 四、expected-after 合并语义（查询时推导）

审核 diff 的"修改后"列由 `projectAfter(before, input)` 推导，语义契约：

```
projectAfter(before, input):
    if before 为 null: after = input            # create 型：全部为新增
    else:
        for field in input:
            标量/to-one：显式提供（含 null）→ 覆盖；absent → 保留基线值
            to-many：按子行主键合并
                输入子行有 id 且基线存在 → 用输入子行字段覆盖该子行投影
                输入子行无 id → 新增子行
                基线子行未被输入提及 → 保留（不隐式删除）
```

**拒绝了 to-many 全量替换语义**（输入列表即全集、缺失即删除）：全量替换在嵌套 UI 下误删风险高、与"重要数据保守复核"取向冲突；删除子行属高危变更，要求走显式删除动作（独立记录、独立审批）。该语义与 CrudBizModel.save 的既有子表处理一致性在实现期核对，若存在偏差以"保守合并 + 显式删除动作"为准。

## 五、DiffTree 计算（IEntityTreeDiffCalculator）

```
diff(before, after, objMeta) -> DiffNode:
    对象节点: 逐字段递归，变更字段生成子节点
    to-many 节点: 按主键三方匹配
        added   = after 有 id/无基线
        removed = 基线有、after 无（仅显式删除动作产生）
        updated = 双方存在且归一化值不等 → 递归
    叶子: 归一化字符串不等 → UPDATED
    摘要: 顶层变更字段名列表 + 总计数 → changeSummary（提交时预存）
```

- 展示标签取 objMeta displayName；路径以属性名表达（前端经 objMeta 元数据可翻译为显示路径）。
- **敏感掩码**：字段声明 `sys:maskInChecker`（objMeta 扩展属性约定，如手机号/证件/密钥类）→ DiffTree 中该字段 oldValue/newValue 替换为定长掩码并置 `masked: true`；requestData 原文仍加密等价存储于库中（重放需要真实值），**含掩码字段的提交在 try 阶段显式拒绝**（缺省），开启字段级加密扩展后放开——两种策略均可见，不静默。
- 大文本（>200 字符）叶子只存前缀 + 长度 + sha256 前 8 位，详情展开按需取原文。
- create 型：整树为 ADDED；delete 型：整树为 REMOVED（beforeData 为全量投影，输入形状退化为 id）。

## 六、存储与一致性

| 问题 | 决策 |
|------|------|
| diff 预存还是查询时算 | 列表用预存 `changeSummary`；详情树查询时算（一次记录只算一次，计算成本与输入树同阶；避免存两份可展示格式、算法可演进） |
| 基线与锁的一致性 | 投影 + lockKey 插入 + 唯一索引在同一事务，事务提交即"基线可信" |
| 快照防篡改 | requestHash 在 approve 重放前重算比对；不一致 → 拒绝并告警（哈希为完整性校验，不替代权限控制） |
| 历史保留 | 终态记录保留全部快照列（审计第一载体）；清理策略仅针对 PROCESSED 等价态（APPROVED）按部署 TTL 归档，REJECTED/CANCELLED/STALE/EXECUTE_FAILED 不自动清理 |
