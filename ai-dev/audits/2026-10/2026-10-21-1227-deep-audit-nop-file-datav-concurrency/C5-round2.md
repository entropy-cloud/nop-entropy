# C5 深挖第 2 轮 — 内存有界性与大数据量

- 挖掘面：第 1 轮未覆盖的快照历史查询、会话列表、LLM 循环内存曲线、Screen 序列化链
- 纪律：仅登记新发现；与 C5-001~005 不同路径
- CL 续编：`nop-file-datav-CL-021` 起

### [C5-R2-001] getScreenSnapshotHistory 全量加载快照含 SNAPSHOT_CONTENT：每请求内存随发布次数线性增长

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavScreenBizModel.java:220-232, 385-390`
- **证据片段**:
  ```java
  // findAllSnapshots :385-390 —— 无 LIMIT、整行加载（含 SNAPSHOT_CONTENT 大列）
  QueryBean query = new QueryBean();
  query.addFilter(FilterBeans.eq("screenId", screenId));
  query.addOrderField("snapshotVersion", true);
  return daoProvider().daoFor(NopDatavScreenSnapshot.class).findAllByQuery(query);
  // 调用方 :225-232 —— 仅取元数据三字段，content 加载后即弃
  List<NopDatavScreenSnapshot> snapshots = findAllSnapshots(screen.getScreenId());
  ```
- **严重程度**: P2
- **并发场景**: 非竞态；每次调用 getScreenSnapshotHistory 的请求线程确定性触发。
- **触发时序**: 大屏每发布一次追加一条快照（publishScreen 无上限）→ 第 N 次发布后查询历史 → ORM 整行加载 N 条快照（每条含完整屏幕 JSON content，大小随 widget 数增长）→ 仅使用 version/publishedBy/publishedTime 三字段 → content 占用的内存在请求期内全量驻留。单请求成本 O(ΣN×contentSize)。
- **现状**: 无投影列裁剪、无分页、无快照保留上限；对照 DashboardBizModel 同类查询全部 `setLimit(1)`（:924/:934/:942）——族内防护不对称。
- **风险**: 长生命周期大屏（数百次发布 × 数十 KB content）单请求物化数十 MB；高频刷新历史页可叠加。F7"一次载入全量"画像。
- **建议**: 查询列投影（排除 snapshotContent）或分页（limit/offset 参数）；快照保留数上界（保留最近 M 版）一并考虑。
- **失效面枚举**（F3）: `getScreenSnapshotHistory`（本条，唯一全量快照查询）；`findLatestSnapshot`/`findSnapshotByVersion`（Screen/Dashboard 两 BizModel 共 4 处，均 limit(1)，已修对照）；`rollbackScreen`（按版本单条，无涉）。
- **信心水平**: 确定
- **误报排除**: ORM 若按需懒加载大列？Nop ORM 实体全列加载（无 lazy column 证据），不能凭空假设豁免；即使懒加载，整行 findAllByQuery 的行集本身仍随版本数无界。
- **复核状态**: 已保留（回码验证：findAllSnapshots 无 LIMIT、调用方仅取三元数据字段属实；Dashboard 侧对照查询均 limit(1) 属实；现状：新发现，跨单元关联 cra-index WI1.5 C5-001 无 LIMIT 全量载入族）

### [C5-R2-002] listChatSessions 全量加载用户全部会话：无分页无上界

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/ChatBiSessionManager.java:96-101`
- **证据片段**:
  ```java
  public List<NopDatavChatSession> listSessions(String operator) {
      QueryBean query = new QueryBean();
      query.addFilter(FilterBeans.eq("userName", operator));
      query.addOrderField("updateTime", false);
      return sessionDao().findAllByQuery(query);     // 无 LIMIT
  }
  ```
- **严重程度**: P3
- **并发场景**: 非竞态；listChatSessions 每次调用全量加载。
- **触发时序**: 用户长期使用（会话只增不删，无 TTL）→ 数千会话后每次列表页加载全量行。
- **现状**: 单行较轻（会话元数据小），风险显著低于 C5-002（消息行含 resultJson）；与 C5-002 同根因（会话/消息无保留策略）不同路径，单独立项便于分级。
- **风险**: 响应体膨胀与慢查询；与 C5-005（F4 保留策略缺失）合并治理。
- **建议**: 分页参数（cursor=updateTime）；会话/消息保留期配置。
- **失效面枚举**: `listSessions`（本条）；`loadMessages`（C5-002 已立项，同族关联）；`requireSession`（单条，无涉）。
- **信心水平**: 确定
- **误报排除**: 非管理低频路径——会话列表为常规 UI 入口。
- **复核状态**: 已保留（回码验证：findAllByQuery 无 LIMIT 属实；现状：新发现）

## 第 2 轮核验通过项（正向证据）

1. **ChatBiToolCallingLoop 请求累积有界**：每轮追加 toolResponse ≤ maxIterations(5) × maxRows(1000) 行 JSON → 峰值 ~5k 行等效文本，有界。
2. **publishDashboard/publishScreen 快照内容有界**：面板/组件数受 CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS=50 与 layout/panelConfig JSON 长度校验（DashboardLayoutCodec JSON_MAX_LENGTH=4000）约束 → 单快照 ≤ ~百 KB。
3. **LLM 生成 executor 局部缓存有界**：`datasetCache`/`seenDatasetSidsCache` 均为 per-call HashMap，键空间受本次生成 payload 约束。
4. **文件上传链有界闭环**：saveFile 声明长度 >0 走 LimitedInputStream（超限抛 ERR，实测 raiseError 语义非静默截断）、长度 ≤0 走 maxLength 限制 + 临时文件实测长度回填——两条分支均有界（修正第 1 轮写作过程中的"截断"疑点，不构成发现）。

## 维度复核结论

- 2 条深挖发现逐条回码验证：**保留 2 / 降级 0 / 驳回 0**；现状均为新发现。
- C5-R2-001 复核补验：findAllByQuery 整行加载（含 SNAPSHOT_CONTENT 大列）+ 调用方仅消费元数据三字段；Dashboard 侧同类查询全部 limit(1) 对照成立。

## 最终保留项

| CL | 发现 | 严重度 | 现状 | 跨单元关联 |
|---|---|---|---|---|
| CL-021 | C5-R2-001 getScreenSnapshotHistory 全列全量加载 | P2 | 新发现 | WI1.5 C5-001（族） |
| CL-022 | C5-R2-002 listChatSessions 全量加载无分页 | P3 | 新发现 | — |
