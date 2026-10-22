# 2494 WI6 结果文件缓存端到端（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI6；`ai-dev/design/nop-report/auth-wiring.md`（缓存命中不越权）
> Related: 2489（WI1 授权）、2490（WI2 参数规范化）

## Purpose

`NopReportResultFile` 接入引擎前置缓存：按「报表 + 参数 + 数据版本」键控的同步命中/未命中生成、TTL/手动失效、异步生成 + 轮询查询；缓存命中不越权（WI1 授权先行）。

## Current Baseline

- `NopReportResultFile` 实体齐备（sid/fileName/fileType/filePath/fileLength/bizDate/rptId/rptParams/status/…）+ CRUD BizModel；**引擎/服务链路无任何消费**（WI0 勘误已登记）。
- 渲染出口：`ITemplateOutput` 基类 default `generateToResource` 统一 html/xlsx/pdf 落盘（demo `ReportDemoBizModel.download` + runbook 同模式，`GlobalExecutors.globalTimer()` 延迟清理先例）；WI1 授权挂 `getXptModel`；WI2 参数经 `ReportParamResolver` 规范化注入 scope。
- 异步原语：`io.nop.commons.concurrent.executor.GlobalExecutors.globalWorker()`（nop-commons，既有惯用）。
- 实体字段映射裁定（**不新增 ORM 列**——避免 model/*.orm.xml protected area；`version` 列是乐观锁不可复用）：renderType→`fileType`；规范化参数 SHA-256 hex→`rptParams`；数据版本载体=`bizDate`（DATE 型；调用方以 `bizDate` 参数显式传数据版本，缺省当天）；TTL 以 `createTime` 判定；status 内部约定 0=生成中/1=完成/2=失败（不复用 core/active-status，落 design + 常量类）。并发唯一性：命中取 createTime 最新一条 + 生成前按键查重。
- 配置动态读取：ttl/enabled/dir 以 `IConfigReference` 运行时读取（`AppConfig.getConfigProvider().updateConfigValue` 可动态生效，TTL 过期测试依赖此）。
- service 有 AutoTest 基座（JunitAutoTestCase + h2）。

## Goals

- service `NopReportResultCache`（bean `nopReportResultCache`）：
  - 同步 `getOrGenerate(reportPath, renderType, params)`：**先过 `reportEngine.getXptModel(reportPath)`（WI1 授权）**→ 键控 `rptId + SHA-256(规范化参数 JSON) + renderType + 数据版本 bizDate` → 查 `NopReportResultFile`（status=1 完成、未过期，同键多条取 createTime 最新）→ 命中返回已存文件；未命中同步渲染（`generateToResource` 统一出口）落盘 + 落库。
  - 异步 `generateAsync(...)`：查重后落 status=0（生成中）记录，`GlobalExecutors.globalWorker()` 执行渲染，完成置 1/失败置 2（ORM 正常更新走乐观锁）；`getResultStatus(rptId, cacheKey)` 轮询查询。
  - 「命中不再渲染」观测：测试注入 spy `IReportEngine` 计数渲染次数；异步轮询设超时上限（5s 未达 status=1 判失败）。
  - 失效：`invalidate(rptId)`（按报表删缓存记录）；TTL 配置 `nop.report.result-cache.ttl`（默认 0=不过期）与开关 `nop.report.result-cache.enabled`（默认 true）。
- BizModel 暴露：`NopReportCacheBizModel`（download/getResultStatus/invalidate），全部经 getXptModel 授权先行；文件下载走 `WebContentBean`。
- 缓存键规范化约定与命中不越权语义落 owner doc + design；roadmap、logs 收口。

## Non-Goals

- 数据源级缓存细分（roadmap 允许并入本 WI 裁定：不做，登记 design）；分布式缓存协调（单节点文件缓存，集群由后续裁定）；nop-datav D5 调度消费（只保证接口可调用）。

## Scope

### In Scope

- `nop-report/nop-report-service`：NopReportResultCache、BizModel、配置、测试
- `docs-for-ai/03-modules/nop-report.md`（缓存勘误→已接线）、design 新建 `ai-dev/design/nop-report/result-cache.md`、roadmap、logs

### Out Of Scope

- 引擎内部改动（缓存为服务层前置）；nop-report-web 页面。

## Execution Plan

### Phase 1 - 缓存服务与 BizModel

Status: completed
Targets: `nop-report/nop-report-service`

- Item Types: `Feature`

- [x] `NopReportResultCache`：键控/命中判定（TTL）/同步生成落盘落库/异步生成（GlobalExecutors.globalWorker()）/失效（删记录 + best-effort 删 filePath 文件）；配置项 enabled/ttl/存储目录 `nop.report.result-cache.dir`（默认 `_report-cache/`）
- [x] `NopReportCacheBizModel`：`download(reportPath, renderType, params)`（WebContentBean）、`getResultStatus(rptId, cacheKey)`、`invalidate(rptId)`——均先 getXptModel 授权
- [x] beans 注册（手写文件）+ AutoTest 测试：首次未命中生成（文件+记录存在）、二次命中（不再渲染——spy engine 计数）、参数不同键不同、失效后重新生成、TTL 过期重新生成、**未授权 download 被拒**（auth 先于命中）、异步生成 + 轮询状态迁移

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] 命中不越权与异步状态迁移均有测试断言
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 文档收口

Status: completed
Targets: docs/design/roadmap/logs

- Item Types: `Decision + Proof`

- [x] design 新建 `ai-dev/design/nop-report/result-cache.md`：字段映射（不新增列裁定）、status 0/1/2 约定 + 常量类、键控规范（参数 JSON 规范化/bizDate 数据版本）、命中不越权（授权先于缓存查询）、异步原语裁定、数据源级缓存不做裁定；owner doc 缓存勘误（「未接线」→ 已接线 + 用法 + 已知限制）；roadmap WI6 勾选；logs 收口

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端（AutoTest 全链路：BizModel 入口→文件落盘→命中复用）：未命中生成→命中复用（spy engine 计数不变）→失效→再生成；未授权不命中（授权先于缓存查询）
- [x] 无静默跳过（生成失败置状态 2 并可查询，不静默）
- [x] owner doc + design 同步
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录

## Deferred But Adjudicated

### 数据源级缓存细分

- Classification: `watch-only residual`
- Why Not Blocking Closure: 行级过滤/WI3 语义下按数据集缓存一致性复杂度高，报表级结果缓存已覆盖 roadmap 主目标；roadmap 允许并入本 WI 裁定
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- TTL 过期记录与文件的定期清理（当前过期仅不命中，表与目录单调增长）
- 集群部署下的缓存目录共享/失效广播

## Closure

Status Note:
WI6 交付完成并经独立 closure audit 通过。audit 发现 1 项 Major（`nop.report.result-cache.enabled` 定义未消费，design/owner doc 宣称的开关无实际效果），已修复：`NopReportResultCache.getOrGenerate`/`generateAsync` 入口在授权之后读 `cacheEnabled()`，关闭时直接渲染、不读不写缓存；新增 testDisabledCacheSkipsCache 断言渲染成功且记录数为 0。修后测试 7/7 绿。

Completed:
2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（新会话，GLM-5.3-Flash / ZCode），2026-10-22
- Evidence:
  1. 实现核验 PASS：NopReportResultCache（键控 renderType+SHA-256(params JSON)+bizDate / TTL 以 createTime / 同步 getOrGenerate 授权先行 / 异步 globalWorker / invalidate 文件 best-effort 删除 + SQL 删记录）；NopReportCacheBizModel 三 action（download/getResultStatus/invalidate）均 getXptModel 授权先行，download 走 WebContentBean；NopReportConfigs 3 项 IConfigReference（enabled/ttl/dir，enabled 消费点已复核）；beans 手写注册 report-auth.beans.xml；ReportCacheErrors 错误码英文 + .param。
  2. 测试 PASS：自行运行 `./mvnw test -pl nop-report/nop-report-service -Dtest=TestReportResultCache`（修复复核后重跑）Tests run: 7, Failures: 0, Errors: 0（5.1s）——覆盖命中不越权（ERR_REPORT_AUTH_REPORT_DENIED）、spy 计数不再渲染、参数分键、失效重生成、TTL 过期、异步轮询、enabled=false 跳过缓存。
  3. 文档 PASS：design result-cache.md（映射/status 0-1-2/键控/已知限制 watch-only）与实现一致；owner doc 缓存段 + 实体表「已接线」勘误；roadmap WI6 勾选；logs 10-22 WI6 条目在；`node ai-dev/tools/check-doc-links.mjs --strict` 自行运行 0 errors 退 0。
  4. 契约一致 PASS：字段映射/status/授权先行三处（plan Goals、实现、design/owner doc）一致；auth-wiring.md「缓存协同」与实现相符。
  5. 文本一致 PASS：Phase Status/Exit Criteria 勾选与事实相符。
  6. Anti-Hollow PASS：授权→键→命中/生成→落盘→落库→下载链路有代码 + 测试断言双重证据；失败置 STATUS_FAILED 不静默。
  审计裁定：唯一 Major（enabled 未接线）修复后复核通过，准予 completed。
