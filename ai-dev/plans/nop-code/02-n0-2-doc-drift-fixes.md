# 02 N0.2 文档-代码 drift 修正(7 处)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N0.2(7 处逐项定位);live repo 核对(2026-09-27,commit 6d21a67 之后)
> Related: `ai-dev/audits/nop-code/nop-code-feature-gap-matrix.md` N0.2 行
> Draft Review: R1(agent_3cab4f73,3 Major + 2 Minor)全修订 → R2 delta PASS(2026-09-27)

## Purpose

将 nop-code 相关 owner 文档中 7 处与 live 代码不一致的陈述修正为与代码一致。全部为文档修订,无代码变更。

## Current Baseline

> 以下 7 处均已于 2026-09-27 在 live repo 逐项复核定位(文件/行号/真实状态),与 roadmap N0.2 条目一致(roadmap R2 审查曾独立重定位 7/7 全中)。

1. **`docs-for-ai/03-modules/nop-code.md` L48**:写"Admin-only mutations, `code-query` permission for reads"。live 无 `code-query` 权限(全仓 grep 零命中);真实模型:`nop-code/nop-code-web/src/main/resources/_vfs/nop/code/auth/nop-code.action-auth.xml` 按 action-auth 资源授权(`NopCodeIndex:query`/`NopCodeIndex:mutation`/`NopCodeSymbol:query` 等),`NopCodeIndexBizModel` 的 mutation 方法标 `@Auth(roles = "admin")`(`nop-code-service/.../entity/NopCodeIndexBizModel.java` L56-141 多处)。
2. **`ai-dev/design/nop-code/query-api-design.md` §二(L29-41)**:描述 `CodeIndexApi` 接口(5 个 `ApiRequest<Map>/ApiResponse` 泛型方法,称位于 nop-code-api)。live 无此类(`find nop-code -name CodeIndexApi.java` 零命中);真实集成面是 `ICodeIndexService`(`nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`,具体类型签名)与 GraphQL BizModel。
3. **`ai-dev/design/nop-code/01-architecture-baseline.md` §4.5(L243-255)**:4 个幻影签名。真实签名(live 接口文件):
   - `IFlowDetector.detectFlows(String indexId, SymbolTable, CallGraph) → List<ExecutionFlow>`(另有 `getFlow(String indexId, String flowId)`);非 `detect(SymbolTable, CallGraph)`。
   - `IEntryPointPatternProvider`:`int priority()` / `boolean isEntryPoint(CodeSymbol)` / `List<String> getAnnotationPatterns()` / `List<String> getNamePatterns()`(`nop-code-flow/.../IEntryPointPatternProvider.java` L6-14);非 `getPatterns() → List<EntryPointPattern>`(该类型不存在)。
   - `IChangeAnalyzer.analyzeChanges(String indexId, String baselineCommitish, String targetCommitish, SymbolTable, CallGraph, String workingDirectory) → ChangeAnalysisResult`;非 `analyze(indexId, baseCommitish, targetCommitish)`。
   - `IDeadCodeDetector.detectDeadCode(String indexId, SymbolTable, CallGraph) → DeadCodeReport`;非 `detect(CallGraph, SymbolTable, config)`。
   - 同段 L257 注:内置 Spring provider 实际在 `nop-code-flow`(`FlowDetector` 私有内部类 `DefaultSpringEntryPointPatternProvider`),非"移出 `nop-code-core`"。
4. **`ai-dev/design/nop-code/graph-analysis-design.md` L151**:`CallGraph + CommunityDetector`。live 无 `CommunityDetector` 类;真实类为 `nop-graph-core` 的 `LeidenDetector`(结果类型 `nop-graph-api` 的 `CommunityResult`,该文档 L20 校正注已确认此映射)。
5. **`ai-dev/design/nop-code/semantic-edge-design.md` L135**:`nop_code_semantic_edge` 字段列表写 "SID / ... + 通用字段(CREATED_BY, CREATE_TIME, DEL_FLAG)"。live ORM(`nop-code/nop-code-dao/src/main/resources/_vfs/nop/code/orm/_app.orm.xml` 与源模型 `nop-code/model/nop-code.orm.xml` 一致)该表 16 列:ID/INDEX_ID/SOURCE_SYMBOL_ID/TARGET_SYMBOL_ID/DIRECTED/RELATION_TYPE/CONFIDENCE/CONFIDENCE_SCORE/RATIONALE/EXTRACTOR_ID/EXT_DATA/PROVENANCE/CREATED_BY/CREATED_TIME/UPDATED_BY/UPDATE_TIME——即:主键列名是 **ID 非 SID**;**无 DEL_FLAG**;是 `CREATED_TIME` 非 `CREATE_TIME`;核心列含 **PROVENANCE**。
6. **`docs-for-ai/03-modules/nop-code.md` L123-131**:"All dicts defined in `nop-code/model/nop-code.orm.xml`"。live:orm.xml 只**定义** 8 个 dict(symbol_kind/access_modifier/reference_kind/index_status/language/call_type/relation_type/semantic_relation_type,经 codegen 物化为 yaml);`call_direction`/`hierarchy_direction`/`provenance` 3 个 dict **仅以独立 yaml 存在于** `nop-code-meta/src/main/resources/_vfs/dict/code/`(orm 中 PROVENANCE 列无 dict 属性引用,无 `<dict>` 定义);`_vfs/dict/code/` 下共 11 个 yaml。
7. **`ai-dev/design/nop-code/01-architecture-baseline.md` §3.1 表(L106-124)**:缺 `ROUTE` 行。live 三处一致含 ROUTE:`CodeSymbolKind.java` L24 `ROUTE(100, "路由")`、orm dict `nop-code.orm.xml` L42 `<option code="ROUTE" value="100"/>`、物化 yaml `symbol_kind.dict.yaml`(label 路由/value 100)。ROUTE 符号由框架路由提取(`CodeRouteInfo`)在 `CodeIndexService`(L1349-1360)合成,name 形如 `GET /path`。

## Goals

- 7 处 owner 文档陈述与 live 代码完全一致(逐处可 grep 验证)。
- 修正不引入新的编号断裂或链接失效(check-doc-links --strict 保持 0 errors)。

## Non-Goals

- 不修改任何代码/生成物。
- 不重构文档章节编号(query-api-design §二以"校正"形态改写而非物理删除章节,避免全文重编号;这与仓内其它设计文档的校正节惯例一致)。
- 不评估或修订 roadmap 范围。

## Scope

### In Scope

- `docs-for-ai/03-modules/nop-code.md`(①⑥两处)
- `ai-dev/design/nop-code/query-api-design.md`(②)
- `ai-dev/design/nop-code/01-architecture-baseline.md`(③⑦两处)
- `ai-dev/design/nop-code/graph-analysis-design.md`(④)
- `ai-dev/design/nop-code/semantic-edge-design.md`(⑤)

### Out Of Scope

- 其它文档中同类措辞的顺带清理(超出 roadmap N0.2 登记的 7 处)。
- N0.2 之后的任何功能 WI。

## Execution Plan

### Phase 1 - 7 处 drift 修正

Status: completed
Targets: 上列 5 个文件

- Item Types: `Fix`

- [x] ① `docs-for-ai/03-modules/nop-code.md` L48:`code-query` 权限表述改为 action-auth 资源权限(`NopCodeIndex:query` 等)+ mutation `@Auth(roles = "admin")`
- [x] ② `query-api-design.md` §二:删除幻影 `CodeIndexApi` 接口描述,改为校正注——真实集成面为 `ICodeIndexService`(nop-code-service,具体类型签名)与 GraphQL BizModel
- [x] ③ `01-architecture-baseline.md` §4.5:4 个签名改为 live 真实签名(L243-255);L257 注的 provider 归属改为 nop-code-flow(`FlowDetector` 内部类)
- [x] ④ `graph-analysis-design.md` L151:`CommunityDetector` → `LeidenDetector`(结果类型 `CommunityResult`)
- [x] ⑤ `semantic-edge-design.md` L135:主键 `SID` → `ID`;删 `DEL_FLAG`;`CREATE_TIME` → `CREATED_TIME`;核心列补 `PROVENANCE`(通用字段如实列举 CREATED_BY/CREATED_TIME/UPDATED_BY/UPDATE_TIME)
- [x] ⑥ `docs-for-ai/03-modules/nop-code.md` L123-133:"All dicts defined in orm.xml" 改为如实列举:8 个 dict 定义于 orm.xml 并物化为 yaml;`call_direction`/`hierarchy_direction`/`provenance` 3 个为 `_vfs/dict/code/` 独立 yaml;字典列表补全 11 项
- [x] ⑦ `01-architecture-baseline.md` §3.1 表补 ROUTE 行(值 100,框架路由,`CodeRouteInfo` 提取经 `CodeIndexService` 合成)

Exit Criteria:

> 行号为 2026-09-27 起草快照,执行以章节标题 + 原文引文锚定,不依赖行号。

- [x] `docs-for-ai/` 下 grep `code-query` 零命中(ai-dev 历史审计/日志/roadmap/archived 中的命中视为历史记录,按 guide 规则 20 不改写);`03-modules/nop-code.md` 中权限描述与 `nop-code.action-auth.xml` + `@Auth(roles="admin")` 一致
- [x] `CodeIndexApi` 在 docs 中不再作为存在性陈述出现——仅允许在 query-api-design §二校正注中被否定引用(如"初版描述的 `CodeIndexApi` 不存在");与真实类 `NopCodeIndexApi` 严格区分不误伤
- [x] `01-architecture-baseline.md` §4.5 的 4 个签名与 `IFlowDetector`/`IEntryPointPatternProvider`/`IChangeAnalyzer`/`IDeadCodeDetector` live 文件逐 token 一致
- [x] `ai-dev/design/nop-code/` 下 grep `CommunityDetector` 仅允许命中既有的否定性校正注(`01-architecture-baseline.md` "不存在 ICommunityDetector"),不得残留作为存在性陈述的引用;`DEL_FLAG` 在 `semantic-edge-design.md` 零残留
- [x] `docs-for-ai/03-modules/nop-code.md` dict 节列举 11 个 dict 且归属正确(8 orm 定义 + 3 独立 yaml),与 `_vfs/dict/code/` 实际文件清单一致
- [x] `01-architecture-baseline.md` §3.1 表含 ROUTE(100) 行
- [x] **No new test required**:纯文档修订,无代码行为变更
- [x] No owner-doc update required beyond the 7 项本身(修正对象即 owner doc 自身)
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档计划**:不涉及代码变更,构建验证门禁按 guide 豁免,以链接/清单工具门禁替代。

- [x] 7 处修正全部落地且逐项通过 Phase 1 Exit Criteria 的 grep 验证
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope drift(audit 发现的超范围残留 "logical delete" 表述已当场修复,见 Closure)
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_b6a6101e,2026-09-27,APPROVE)
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

(无)

## Non-Blocking Follow-ups

(无;audit Minor-2/3 已在 closure 时顺手修复——plan 内 3 处路径引用补全 `nop-code/` 前缀、baseline 第 6 项括注更正)

## Closure

Status Note: roadmap N0.2 登记的 7 处 drift 全部修复并经独立审计逐 token/逐列核实与 live 一致;审计顺带发现的同类残留(nop-code.md "logical delete" 表述无 live 依据)已当场修复;顺带修复 nop-lint 两份历史 plan 的 3 处模块分组迁移坏链(guide 规则 20 豁免)。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_b6a6101e(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_b6a6101e-b506-49cf-b819-55235a71c350
- Evidence:
  - 7/7 修正 PASS:①code-query docs 零命中 + 权限表述与 action-auth/@Auth 一致;②CodeIndexApi 仅否定性校正注 + ICodeIndexService/BizModel 真实存在;③四签名逐 token 一致 + provider 归属与 FlowDetector L499/L82/L90 一致;④LeidenDetector+CommunityResult 真实存在、L203 否定注未被误改;⑤16 列逐一核对(orm 源模型 L930- 与 _app.orm.xml L907- 一致);⑥11 dict 与目录/orm grep 一一对应;⑦ROUTE(100) 三处 live 一致 + CodeIndexService L1340-1362 合成路径属实
  - Closure Gates:`check-doc-links --strict` exit 0(0 errors,23 warnings);`check-plan-checklist --strict` exit 0;daily log 一致;roadmap N0.2 审计时仍 todo(未提前翻转),审计放行后翻转
  - Deferred 项分类检查:无 in-scope 残留;3 个 audit Minor 中 Minor-1(超范围 "logical delete" drift)当场修复,Minor-2/3(plan 内路径 warning 与陈旧括注)已顺手修复
  - 审计裁定:APPROVE(2026-09-27)

Follow-up:

- no remaining plan-owned work
