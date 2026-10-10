# 2394 full-code-review R1.28 — nop-search 先修批（P1-4.4-S-02..06）

> Plan Status: active
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.28；findings `P1-4.4-S-02..06`（check/nop-search.md P1-1..P1-5，4.4 §2.2 标定维持），见 `ai-dev/audits/check/nop-search.md`
> Related: **⚠️ plan-first 载体**：S-05 触 nop-search-api `ISearchEngine.getDoc` 签名变更（Protected Area：跨模块公共 API plan-first）——本 plan 即载体，调用方迁移清单见 Execution Plan；S-06 注 6.4 #6a 纯引用裁定勿双立；执行顺序约束：先修 5 条 P1 再动 LuceneSearchEngine 拆分（4.4 裁定）
> Owner Doc: `docs-for-ai/03-modules/nop-network.md`（nop-search 面无独立 owner doc——实施时核对，network.md 为 search 相关语义既有落点）

## Purpose

收口 nop-search 五条 P1（LuceneSearchEngine 先修批）：①空 query 构造空 BooleanQuery 恒空（Match all 意图相反）；②visitNot 纯 MUST_NOT 匹配空集；③rollback 关闭 IndexWriter 但留缓存 map——topic 永久不可写（单条坏文档打死整个 topic）；④getDoc 忽略 topic 跨 topic 串读（API 签名缺 topic）；⑤indexDir 任意服务器路径+checkAllowAccess 空实现（任意文件读取面，安全面）。

## Current Baseline

- **S-02**：`LuceneSearchEngine.buildQuery:866-868`——空 query → `new BooleanQuery.Builder().build()`（零子句 BooleanQuery 匹配 0）以 MUST 加入 → 整体恒空。9.7.0 探针实证。
- **S-03**：`LuceneFilterBeanTransformer.visitNot:118-125`——纯 MUST_NOT 子句 BooleanQuery 匹配空集（NE 分支 :43-48 有 MUST MatchAllDocs 锚点，NOT 分支漏）。
- **S-04**：`addDocs:856`/`removeDocs:1111` 失败 → `rollback(writer):1151`（rollback 即 close，writer 不可再用）但 `indexWriters`/`searcherManagers` 条目不移除 → 后续 getIndexWriter 返回已关闭 writer → AlreadyClosedException 直到重启。单条坏文档（buildDocument Guard.notEmpty(id)）即触发。
- **S-05**：`ISearchEngine.getDoc(String docId):23`（api 签名无 topic）；`LuceneSearchEngine.getDoc:312` 遍历全部 searcherManagers 取第一个命中；`SearchEngineBizModel.getDoc:109-111` 声明 topic 但不传。调用方全量：SearchEngineBizModel:111 + 测试三处（TestBuildIndexTool:47 stub/TestSearchEngineBizModel:48 stub/TestLuceneSearchEngine:144）——**无其他生产调用方**（grep 实证）。
- **S-06**：`SearchEngineBizModel.indexDir:66-77`——`checkAllowAccess` 空方法:135-137；`getLocalFile:80-88` resourceLocator 恒 null（setter 无 @Inject 且 beans 未配）→ `FileHelper.resolveFile(绝对路径)` 无白名单；演示页以服务器绝对路径为输入。bean 注册 search-core.beans.xml:5。
- **测试基建**：nop-search-lucene 测试容器可用（TestLuceneSearchEngine extends JunitBaseTestCase + @Inject ISearchEngine）；nop-search-core 有 TestBuildIndexTool/TestSearchEngineBizModel（stub ISearchEngine 形态）。

## Goals

- S-02：空 query → `MatchAllDocsQuery`（仅 tag/filter 浏览场景可用）。
- S-03：visitNot 补 MUST MatchAllDocs 锚点（NOT 补集语义生效）。
- S-04：失败路径先从 indexWriters/searcherManagers 移除条目（SearcherManager close）再 rollback——下次访问重建，topic 自愈。
- S-05：`ISearchEngine.getDoc(String topic, String docId)`（api 签名变更）——实现限定单 topic 查找；调用方全量迁移（4 处）。
- S-06：checkAllowAccess fail-closed admin 校验（IUserContext null 或非 admin 拒绝，新错误码）+ indexDir path 白名单（配置 `nop.search.index-dir-roots` csv，非空时绝对路径须落在根内；空=仅相对路径可用）。
- 判别测试五腿 stash 实证；nop-search-lucene/core 测试全绿。

## Non-Goals

- LuceneSearchEngine 拆分（4.4 裁定先修后再动——独立计划）；pattern 灾难性回溯限制（checkAllowAccess admin 门后 admin 自伤面，follow-up）；resourceLocator 装配；nop-search 其他 P2/P3。

## Scope

### In Scope

- `LuceneSearchEngine`：buildQuery 空查询/rollback 条目清理/getDoc(topic,docId)
- `LuceneFilterBeanTransformer.visitNot` 补锚点
- `ISearchEngine` getDoc 签名（api 模块——plan-first 载体）+ `SearchEngineBizModel` 迁移 + 测试三处迁移
- `SearchEngineBizModel` checkAllowAccess fail-closed + indexDir path 白名单（新配置键）+ 新错误码
- 测试：五腿判别（TestLuceneSearchEngine 扩展 + TestSearchEngineBizModel 更新）
- owner doc：nop-network.md search 语义注记

### Out Of Scope

- Non-Goals 全部；SearchRequest.limit 归一化（P2 台账）；HYBRID 阈值语义（P2 台账）

## Execution Plan

### Phase 1 - 五修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] S-02：buildQuery 空 query → `new MatchAllDocsQuery()`
- [x] S-03：visitNot 补 `builder.add(new MatchAllDocsQuery(), Occur.MUST)` 锚点
- [x] S-04：`rollback(String topic, IndexWriter writer)`——searcherManagers.remove+close、indexWriters.remove 后 writer.rollback()；两调用点传 topic
- [x] S-05：ISearchEngine.getDoc(String topic, String docId)（@Name 注解）；LuceneSearchEngine 限定 topic 单 manager 查找；SearchEngineBizModel.getDoc 传 topic；测试三处迁移（TestBuildIndexTool/TestSearchEngineBizModel stub 签名、TestLuceneSearchEngine 调用点）
- [x] S-06：SearchEngineBizModel.checkAllowAccess fail-closed（IUserContext.get()==null 或非 admin → 新错误码 ERR_SEARCH_NO_PERMISSION `nop.err.search.no-permission`）；indexDir path 白名单——新配置 `nop.search.index-dir-roots`（csv，默认空），非空时 getLocalFile 结果 canonical path 必须落在某根内否则拒绝；GatewayConfigs 风格配置键落点核对
- [x] 测试：①S-02——空 query + topic 搜索返回索引文档；②S-03——not 过滤补集命中；③S-04——addDocs 含坏文档抛异常后同 topic 正常 addDocs 成功（修复前 AlreadyClosedException）；④S-05——两 topic 同 docId 各异文档，getDoc(topicA,id) 返回 A 的；⑤S-06——非 admin 上下文调 indexDir 拒绝（新错误码）+白名单外绝对路径拒绝；既有 TestSearchEngineBizModel/TestBuildIndexTool 补 admin 上下文迁移
- [x] **判别验证（五腿 stash）**：各修 stash 后对应测试 FAIL、恢复后绿（S-04 判别=修复前第二次 addDocs 抛 AlreadyClosedException）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 五修全落地
- [x] 判别测试五腿绿 + 既有测试零回归（迁移后）
- [x] nop-search-lucene/nop-search-core/nop-search-api 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-network.md`

- Item Types: `Fix`

- [x] nop-network.md：search 语义注记（空 query=match all/NOT 补集语义/rollback 自愈/getDoc topic 隔离〔API 签名变更记录〕/indexDir 授权+路径白名单）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.28 翻 done；index P1-4.4-S-02..06 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-4.4-S-02..06）
- [x] 行为/契约结果已达成（判别五腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-search 三模块测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `./mvnw test -pl nop-search/nop-search-api,nop-search/nop-search-core,nop-search/nop-search-lucene` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### pattern 灾难性回溯限制

- Classification: `watch-only residual`
- Why Not Blocking Closure: checkAllowAccess admin 门后 pattern 仅 admin 可控（自伤面）；正则超时无 JDK 原生支持，语法限制属独立设计
- Successor Required: no
- Successor Path: 随 nop-search 拆分计划一并处理

## Non-Blocking Follow-ups

- LuceneSearchEngine 拆分（4.4 裁定：先修 5 条 P1 后独立计划——本 plan 解锁）
- SearchRequest.limit 归一化 / HYBRID 阈值语义（P2 台账行）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-
