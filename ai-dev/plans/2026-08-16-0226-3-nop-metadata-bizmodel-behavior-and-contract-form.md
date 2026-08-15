# nop-metadata BizModel 行为与契约形态清扫（P2-18 / P2-19 / P2-21 / P2-22 / P2-24 / P2-25）

> Plan Status: active
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit Follow-up Backlog — BizModel 行为/契约形态族（P2 批次清扫）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（BizModel 行为/契约形态族）；审计源 `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2-18/P2-19/P2-21/P2-22/P2-24/P2-25）
> Related: `2026-08-15-1913-2`（P1-4/P1-5 DataProduct 聚合根修复；其 Follow-up 显式将 P2-21 移交 backlog——本计划 P2-21 行号已随 1913-2 落地漂移至 :58/:99/:122）。执行顺序在 `2026-08-16-0226-1`、`2026-08-16-0226-2` 之后（本计划与计划 2 同碰 `NopMetaTagLabelBizModel` 不同方法，顺序执行避免冲突）。

## Purpose

把 follow-up backlog"BizModel 行为/契约形态族"6 项收口：DataProduct 手工 JSON 拼接换 JsonTool、6 个 save override 补 null-data 防护（让基类 `ERR_BIZ_EMPTY_DATA_FOR_SAVE` 语义统一）、computeQualityScore 恢复 defaultPrepareSave 定制点、testConnection 注解语义修正（@BizMutation→@BizQuery）、QualityResult.approve 语义诚实化、queryJoinData/queryAggregation 超参签名的文档裁定。收口后 BizModel 层的行为形态与模块自身惯例一致，无自相矛盾的双形态并存。

## Current Baseline

> 事实为 2026-08-16 live repo 实测（1913-2 落地后）。

- **P2-21 手工 JSON 拼接**：`NopMetaDataProductBizModel` :58（linkAsset）/:99（unlinkAsset）/:122（getLinkedAssets）三处 `"{\"dataProductId\":\"" + dataProductId + "\"}"` 手工拼接 metadata JSON——dataProductId 含 `"` / `\` / 控制字符时 JSON 损坏（写坏 metadata 字符串 + 后续 `FilterBeans.eq(metadata)` 整串精确匹配失真，live 匹配为 :63/:105/:126 的 eq 整串比较、非 contains）。模块内 `JsonTool` 已有使用先例（NopMetaQualityScoreBizModel :48-50）。已验证 `JsonTool.stringify`（紧凑输出）与手工拼接对正常 ID **逐字节一致**（存量行兼容）。
- **P2-19 save override 缺 null-data 防护**：基类 `CrudBizModel.doSave`（`nop-service-framework/nop-biz/.../CrudBizModel.java:534-535`）对 `CollectionHelper.isEmptyMap(data)`（null 与 empty 均为 true，CollectionHelper:125-128）抛 `ERR_BIZ_EMPTY_DATA_FOR_SAVE`。但先解引用 `data` 再调 `super.save` 的 override 会 NPE 抢先：`NopMetadataHelper.stringOf`（NopMetadataHelper.java:33-36）`data.get(key)` 对 null data 直接 NPE。已核实先解引用形态 6 处（独立重扫逐处吻合）：`NopMetaTableDimensionBizModel:57-58`、`NopMetaEntityFieldBizModel:29-30`、`NopMetaTableJoinBizModel:65-66`（validateJoin(data)）、`NopMetaTableMeasureBizModel:61-62`、`NopMetaTableFilterBizModel:57-58`、`NopMetaTagLabelBizModel:68`（:68 `data.containsKey("state")`——1913-2 后仍是先解引用形态）。已有防护先例：`NopMetaModuleBizModel:121`、`NopMetaTableBizModel:101`（`data == null ? null : stringOf(...)`，**只防 null 不防 empty**）、`NopMetaTagBizModel:31`（`data != null && ...`）。
- **P2-24 绕过 defaultPrepareSave**：`NopMetaQualityScoreBizModel.computeQualityScore`（:52）`doSave(data, null, (entityData, ctx) -> {}, context)`——空 lambda 显式覆盖 prepareSave，绕过 `invokeDefaultPrepareSave`（CrudBizModel:1911，protected，经 `getThisObj().invoke("defaultPrepareSave",...)` 分发 = xbiz 可覆盖定制点）。基类 `save()`（CrudBizModel:527）用 `this::invokeDefaultPrepareSave` 为正确形态。cron 自动评分链路因此绕过宿主 xbiz 定制。**分发机制事实**：`getThisObj()` 按 bizObjName 从 BizObjectManager 查**容器注册 bean**（CrudBizModel:225-227）——不是 this 动态分发；接线测试须经容器/xbiz 路径（见 Phase 3 设计）。
- **P2-18 testConnection 注解错位**：`NopMetaDataSourceBizModel:119-120` 实现与 `INopMetaDataSourceBiz`（dao 模块）:25-26 接口**双面** `@BizMutation`。无写操作是代码事实（只读探测）；同模块同类探测正确先例：judgeByRuleId / checkContractReadOnly 用 @BizQuery。`nop-metadata-web`/`nop-metadata-meta` 零引用（live 复核）；**已知引用面**（独立复核）：`TestNopMetaDataSourceBizModel` 5 处 `mutation { NopMetaDataSource__testConnection ... }`（:64/:79/:92/:102/:113）、`TestNopMetaDataSourceConnectionConfigWritePath`、owner doc :187/:269——Phase 4 全量清点后同步。注解同时决定 action 的授权类目（mutation 型 vs query 型 action-auth）——`testConnection` 是 SSRF P0 审计点名的攻击入口，授权面影响必须核对（见 Phase 4）。
- **P2-22 approve 无效果 updateEntity**：`NopMetaQualityResultBizModel.approve`（:28-32，方法体）`requireEntity` 后无字段变更直接 `dao().updateEntity(entity)`；javadoc 首句（:23）残留"重新执行规则判定"表述（:24-25 后半已自认 re-judge 在工作流侧、本方法为回调入口——矛盾在 javadoc 内部与行为之间）。真实 re-judge 在 `qualityBreachApproval` 工作流 agree 路径（`QualityAlertWorkflowProcessor.reJudge` :105，c:script 直调）；另有 disagree listener 直改实体不经 reject 方法的事实。live 证据链可获得（xwf + processor 均在库）。
- **P2-25 超参签名文档裁定缺失**：`NopMetaTableBizModel.queryJoinData`（:269-275，6 个 @Name 参数）、`queryAggregation`（:295-305，10 个 @Name 参数）超出 5 参数规则未用 @RequestBean——签名为 AR-09/F4 显式裁定契约（改动即破坏对外契约），缺的是把"裁定例外"落进 owner doc（`docs-for-ai/03-modules/nop-metadata.md` API 契约段 :142 起——该段 :161/:163/:165 已有"例外裁定"表述先例可复用）。

## Goals

- DataProduct 三方法 metadata JSON 经 `JsonTool` 生成（无转义缺陷，对正常 ID 逐字节等价——存量行兼容），对抗测试用含引号/反斜杠/控制字符的 ID 钉死 round-trip 与 eq 匹配。
- 6 个 save override 对 null/empty data 统一落到基类 `ERR_BIZ_EMPTY_DATA_FOR_SAVE`（统一采用"提前委托 super.save"防护形态），NPE 抢先路径消除，6 处 focused test 钉死。
- `computeQualityScore` 传 `this::invokeDefaultPrepareSave`（与基类 save 同形态），xbiz defaultPrepareSave 定制在 cron 自动评分链路生效，经容器/xbiz 路径的接线测试验证。
- `testConnection` 接口 + 实现 + 全部引用面改 `@BizQuery`（只读语义对齐模块惯例）；GraphQL operation 翻转与 action-auth 授权面影响清点并记录。
- `approve` 语义诚实化：javadoc/行为一致（re-judge 归属工作流侧的事实显式化；无效果 updateEntity 处置有据）。
- `queryJoinData`/`queryAggregation` 的 @RequestBean 例外裁定写入 owner doc API 契约段。

## Non-Goals

- **GraphQL 对外契约破坏性变更** —— P2-18 注解修正会把 GraphQL operation 类型从 mutation 翻转为 query；仓库内引用面同步更新，但**不承诺**对外部第三方调用方的迁移（模块 API 演进期，兼容性影响记录进 owner doc）。
- **P2-20（CheckpointExecutionResultDTO List<Map> 类型化）** —— 归后续批次（测试/DTO 卫生族）。
- **P2-25 的签名重构** —— 只做文档裁定，不改 queryJoinData/queryAggregation 签名（AR-09/F4 契约）。
- **审批流/工作流语义重设计**（P2-22 只做诚实化，不把 re-judge 搬进 approve——那是工作流侧既有职责）。

## Scope

### In Scope

- `NopMetaDataProductBizModel` 三处 JsonTool 替换 + 对抗测试（P2-21）。
- 6 个 save override null-data 防护 + focused tests（P2-19）。
- `NopMetaQualityScoreBizModel.computeQualityScore` prepareSave 修正 + 接线测试（P2-24）。
- `testConnection` @BizQuery 修正 + 引用面同步（P2-18）。
- `NopMetaQualityResultBizModel.approve` 语义诚实化（P2-22）。
- owner doc API 契约段 @RequestBean 例外裁定（P2-25）。

### Out Of Scope

- P2-20 及其余 P2 族（安全族 → 计划 1；错误处理族 → 计划 2；ORM/IoC/文档测试卫生族 → 后续批次）。
- `_gen/` 产物与 xbiz 生成层手工编辑。

## Execution Plan

### Phase 1 — DataProduct metadata JSON JsonTool 化（P2-21）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataProductBizModel.java`（:58/:99/:122）；测试 `TestNopMetaDataProduct*`（1913-2 落地的 linkAsset 测试族）

- Item Types: `Fix | Proof`

- [ ] 三处拼接（:58/:99/:122）替换为 `JsonTool.stringify(Map.of("dataProductId", dataProductId))`（或等价 JsonTool 入口；Map key 形态保持 `"dataProductId"` 与现契约一致）
- [ ] 核对三个方法对 metadata 的 eq 匹配消费面（:63/:105/:126，含 getLinkedAssets :126 的存量行查询）在正常 ID 下行为等价（JsonTool 紧凑输出与手工拼接逐字节一致——已预验证，执行时复测）
- [ ] 对抗测试：dataProductId 含 `"`、`\`、控制字符（如 `\n`）时——metadata 生成合法 JSON、可 parse 回、linkAsset 写入的行可被 unlinkAsset 的 eq 匹配删除（含 getLinkedAssets 查询形态）
- [ ] 存量坏行裁定：旧代码写入的对抗 ID 损坏 metadata 行升级后 unlink 不到——一行裁定记录（平台生成 ID 不含坏字符，影响面≈0，不做数据迁移）

Exit Criteria:

- [ ] `rg -n '\\"dataProductId' nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataProductBizModel.java` 零命中（三处手工拼接全替换）
- [ ] 对抗测试通过：坏字符 ID 不再产生损坏 JSON；正常 ID 输出逐字节等价（存量行兼容）
- [ ] 1913-2 落地的 linkAsset/unlinkAsset 测试族全绿（无回归）
- [ ] **接线验证**：linkAsset → metadata JSON 写入 → unlinkAsset 匹配删除链路在对抗 ID 下完整走通（端到端）
- [ ] No owner-doc update required（metadata 字段内部形态不变，仍为含 dataProductId 键的 JSON——执行时复核 owner doc DataProduct 段无拼接表述引用）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — save override null-data 防护（P2-19）

Status: planned
Targets: 6 个 override（前置复核定稿：audit 清单 + live 重扫；已核实 5 处 + `NopMetaTagLabelBizModel` save 面）

- Item Types: `Fix | Proof`

> 前置复核：`rg -n -A2 'public .* save\(' nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/` 重扫全部 override，按"是否在 super.save 前解引用 data"定界清单（基线 6 处，独立重扫已逐处吻合）。**防护形态统一裁定为"提前委托"**：`if (CollectionHelper.isEmptyMap(data)) return super.save(data, context);` 置于方法首行——null 与 empty 都直落基类 `ERR_BIZ_EMPTY_DATA_FOR_SAVE`。不采用既有 `data == null ? null : ...` 三元形态（只防 null 不防 empty，无法满足 empty-map 断言；Module/Table 两个既有防护 override 可在本批一并统一为提前委托形态——如不改，需在 daily log 记录"两种形态并存"的容忍理由）。

- [ ] 重扫清单写入 daily log（6 处定稿 + 各处解引用点行号）
- [ ] 逐处加提前委托防护（6 处形态统一）：null/empty data 不再 NPE，统一抛基类 `ERR_BIZ_EMPTY_DATA_FOR_SAVE`
- [ ] focused tests：每处 override 以 null data 与 empty map 各调 save → 断言 `ERR_BIZ_EMPTY_DATA_FOR_SAVE`（非 NPE、非各 override 前置业务校验错误码）；null 场景经 Java 直调或 `bizObject.invoke`（GraphQL 入口 data 必传 map 无法表达 null——提前委托分支不触 dao 依赖，直调可行）
- [ ] 对照断言：3 个已有防护 override 行为不变（同测试覆盖）

Exit Criteria:

- [ ] 6 处 override 的 null-data 测试全部通过（错误码精确断言，非仅异常类型）
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am -T 1C` 全绿（正常路径无回归）
- [ ] **无静默跳过**：防护分支显式委托基类错误语义，非吞掉返回
- [ ] No owner-doc update required（错误语义对齐模块既有基类契约——执行时复核）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — computeQualityScore 恢复 defaultPrepareSave 定制点（P2-24）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityScoreBizModel.java`（:52）

- Item Types: `Fix | Proof`

- [ ] `(entityData, ctx) -> {}` 替换为 `this::invokeDefaultPrepareSave`（CrudBizModel:1911，protected 可达；与基类 save:527 同形态）
- [ ] 接线测试（**必须经容器/xbiz 分发路径，禁止测试子类直调**——`getThisObj()` 按 bizObjName 查容器注册 bean，非 this 动态分发）：首选形态 = 测试 resources 经 **delta 路径** `_vfs/_delta/default/nop/metadata/model/NopMetaQualityScore/NopMetaQualityScore.xbiz` 覆盖（**用 `<action>` 标签**声明 defaultPrepareSave——xbiz.xdef 的 action 元素覆盖 @BizAction 级方法；main classpath 已有同路径空壳 xbiz，直放同路径会资源冲突，必须走 delta；模块内活先例 NopMetaTagLabel.xbiz + 测试 delta 先例 nop-auth NopAuthUser.xbiz）；副作用标记选既有断言不依赖的实体真实 DB 列（delta 对模块内全部测试全局生效，勿扰动 TestNopMetaQualityScoreBizModel 既有断言面）→ 经 GraphQL 入口调 computeQualityScore → 断言副作用生效；备选形态 = 容器 bean 覆盖注册测试子类。评分行保存结果与既有断言不变
- [ ] 核对 computeQualityScore 全部调用面（cron 调度链 + 可能的手动入口）无依赖"空 prepareSave"的隐含假设

Exit Criteria:

- [ ] `rg -n '\(entityData, ctx\) -> \{\}' nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityScoreBizModel.java` 零命中
- [ ] 接线测试通过：xbiz 覆盖的 defaultPrepareSave 在 computeQualityScore 链路经 getThisObj 分发被真实调用（副作用断言）
- [ ] 既有评分测试全绿（评分行为不变）
- [ ] **接线验证**：本 Phase 即接线验证本体（xbiz 副作用断言，经容器分发而非直调）
- [ ] No owner-doc update required（恢复基类定制点语义，非新契约——执行时复核 owner doc 评分段如提及管线语义则同步）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — testConnection 注解语义修正（P2-18）

Status: planned
Targets: `INopMetaDataSourceBiz.java`（dao，:25）、`NopMetaDataSourceBizModel.java`（:119）；引用面清点与同步

- Item Types: `Fix | Decision`

> 兼容性影响（Decision 记录）：(1) GraphQL operation 类型 mutation→query 翻转（ReflectionBizModelBuilder 按注解归类）——仓库内引用面同步更新，对外不做迁移承诺；(2) **action-auth 授权类目变化**——注解决定 action 的授权类目（mutation 型 vs query 型条目），`testConnection` 是 SSRF P0 审计点名的攻击入口（"可登录用户即可触发"语境）：翻转后需核对 query 类 action 的默认授权策略是否比 mutation 类更宽（若更宽 = SSRF 触发面扩大，须记录并评估是否需配套权限建议）、已部署环境的功能点授权条目失配影响。

- [ ] 前置清点：`rg -n 'testConnection' -g '!target'` 仓库级引用（Java 测试/xmeta/view/page/e2e/docs）清单写入 daily log（已知：`TestNopMetaDataSourceBizModel` 5 处 mutation 调用（:64/:79/:92/:102/:113）、`TestNopMetaDataSourceConnectionConfigWritePath:66` 第 6 处 mutation 调用、owner doc :187/:269）
- [ ] 授权面影响核对：query vs mutation 类 action-auth 的默认策略差异（读平台 action-auth 机制源码/文档）+ SSRF 触发面影响裁定记录（含是否需在 owner doc 提示部署侧权限条目同步）
- [ ] 接口 + 实现 `@BizMutation` → `@BizQuery`（双面一致）
- [ ] 引用面同步（`TestNopMetaDataSourceBizModel` 等测试的 mutation→query 调用形态、文档示例）
- [ ] 测试：testConnection 经 BizModel/GraphQL 入口可调（@BizQuery 形态），既有安全测试（TestMetaDataSourceConnectionSecurity）全绿
- [ ] 兼容性影响（operation 翻转 + 授权条目）写入 owner doc 数据源 API 段

Exit Criteria:

- [ ] `rg -n -A2 '@BizMutation' -g '*.java' nop-metadata/ | rg testConnection` 零命中（限定 Java 文件，避免 ai-dev 文档行恒命中）；@BizQuery 双面一致
- [ ] 仓库内无残留 mutation 形态的 testConnection 调用（清点清单核对）
- [ ] 授权面影响有书面核对结论与裁定记录（SSRF 触发面不因翻转而无人知情地扩大）
- [ ] 数据源相关测试全绿
- [ ] owner doc 已同步（注解语义 + 兼容性 + 授权面结论）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — approve 语义诚实化（P2-22）

Status: planned
Targets: `NopMetaQualityResultBizModel.java`（:19-31）；工作流侧事实核对（`qualityBreachApproval` xwf / `QualityAlertWorkflowProcessor`）

- Item Types: `Fix | Decision`

> 前置核对（Decision 依据）：追踪 `qualityBreachApproval` agree 路径确认 re-judge 时序（c:script reJudge 发生在 notifyResult 回调 approve **之前/之后/无条件**），据此定 approve 的诚实语义。两个允许终态：(a) javadoc 改为真值（"notifyResult 回调入口，无字段变更；re-judge 由工作流 agree 路径完成"）+ 若 updateEntity 无可论证目的（无乐观锁/时间戳诉求）则删除该无效果调用，方法返回 requireEntity 结果；(b) 若核对发现 approve 应承担 re-judge（工作流侧没做），则显式接线并测试——**以 live 工作流事实为准，禁止臆断**。

- [ ] 工作流路径核对结论写入 daily log（reJudge 调用时序证据：文件/行号）
- [ ] 按核对结论落地终态 (a) 或 (b)；javadoc 与行为一致
- [ ] 测试：approve 路径（mock 或集成）断言语义与 javadoc 声明一致（(a) 断言无副作用返回实体 / (b) 断言 re-judge 触发）
- [ ] reject 同族核对：其 `setIsFalsePositive(1)` 有真实字段变更，不在本项范围（确认即可）

Exit Criteria:

- [ ] javadoc 与 live 行为一致（无"声称重判实则 no-op"的自相矛盾）
- [ ] 终态有核对证据支撑（非猜测）；测试钉死所选语义
- [ ] 审批流相关测试全绿
- [ ] owner doc 质量结果审批段（如提及 approve 语义）同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 — queryJoinData/queryAggregation @RequestBean 例外裁定落档（P2-25）

Status: planned
Targets: `docs-for-ai/03-modules/nop-metadata.md`（API 契约段）

- Item Types: `Decision`

- [ ] owner doc API 契约段补裁定：两方法超参签名（6 参 / 10 参）为 AR-09/F4 显式裁定契约（limit/having/orderBy 拒绝点固定 BizModel 入口），不迁移 @RequestBean；理由 = 对外 GraphQL 契约稳定性
- [ ] 复核 `docs-for-ai` 内 5 参数规则的 owner 文档（api-and-graphql.md / service-layer.md）是否已有"裁定例外"表述机制；优先在其既有机制内登记，无机制则在 nop-metadata.md 局部登记并留链接

Exit Criteria:

- [ ] `docs-for-ai/03-modules/nop-metadata.md` 含两方法例外裁定表述（可定位章节 + 方法名）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：改动前后各跑一次，error 数不增（pre-existing 基线计数与 0 新增结论写入 daily log——`--strict` 对 pre-existing error 会 exit 1，以计数 diff 为判据）
- [ ] 纯文档 Phase：No new test required: documentation-only adjudication
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划含一项对外契约面变更（P2-18 注解翻转，演进期模块 + 引用面同步 + 影响记录）与多项行为形态统一。

- [ ] P2-18/P2-19/P2-21/P2-22/P2-24 五项落地；P2-25 裁定落档
- [ ] `./mvnw compile -pl nop-metadata -am -T 1C` 通过
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata` 0 新增命中
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：改动前后各跑一次，error 数不增（pre-existing 基线计数与 0 新增结论写入 daily log——`--strict` 对 pre-existing error 会 exit 1，以计数 diff 为判据）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] 不存在被静默降级到 deferred 的 in-scope 项
- [ ] 受影响 owner docs（数据源 API 段 / 审批语义段 / @RequestBean 例外）已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）P2-24 接线测试真实经过 getThisObj 分发（非直调私有方法）；（b）P2-21 端到端链路（linkAsset→匹配→unlinkAsset）在对抗 ID 下走通；（c）P2-22 终态与工作流 live 事实一致
- [ ] roadmap Follow-up Backlog 对应条目标注处置结果

## Deferred But Adjudicated

（本计划无 deferred 项。P2-20 为 Non-Goals 显式移出至后续批次，非 in-scope 降级。）

## Non-Blocking Follow-ups

- P2-20（CheckpointExecutionResultDTO List<Map> 类型化）—— 归后续测试/DTO 卫生族批次。
- ORM / IoC / 文档测试卫生族 P2 项 —— 归后续批次清扫计划。

## Closure

Status Note: （待收口时填写）
Completed: （待收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （待收口时填写）
- Evidence: （待收口时填写）

Follow-up:

- （待收口时填写）
