# 报表结果文件缓存 设计

**范围**：`nop-report/nop-report-service`（NopReportResultCache / NopReportCacheBizModel）
**状态**：active（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 结果缓存为**服务层前置**（不进引擎）：`NopReportResultCache.getOrGenerate` 在渲染前查 `NopReportResultFile`，命中直接返回已存文件，未命中渲染落盘落库。
2. **命中不越权**：所有入口先经 `IReportEngine.getXptModel(reportPath)`（WI1 报表级授权），授权先于缓存查询；缓存键不含用户身份，授权在键之外独立判定。
3. **不新增 ORM 列**（`model/*.orm.xml` 为 protected area），字段映射：renderType→`fileType`；规范化参数 SHA-256→`rptParams` 键；数据版本→`bizDate`（DATE 型，调用方显式传数据版本、缺省当天——非日期型数据版本不支持，已知限制）；TTL 以 `createTime` 判定；`version` 列是乐观锁不可复用。
4. status 内部约定：**0=生成中 / 1=完成 / 2=失败**（常量类 `NopReportResultCache`；不复用 `core/active-status`——语义是生成生命周期而非业务有效态）。
5. 异步生成：`GlobalExecutors.globalWorker()` 执行，状态经 `updateEntityDirectly` 迁移（跨 session）；`getResultStatus(rptId, cacheKey)` 轮询。

## 二、背景与动机

平台报表被调度（nop-datav D5）与多用户重复访问时，同一「报表+参数+数据版本」反复渲染浪费且不可控；`NopReportResultFile` 实体早已建表但链路未接线。缓存必须保证：命中不越权（授权先行）、参数参与键（WI2 规范化值）、可失效可轮询。

## 三、核心设计

### 键与存储

- cacheKey = `renderType + ":" + SHA-256hex(规范化参数 JSON) + ":" + bizDate`；记录唯一性靠「生成前查重 + 命中取 createTime 最新」兜底（无唯一索引，并发下可能重复记录——已知限制，watch-only）。
- `filePath` 存**相对缓存目录**（`nop.report.result-cache.dir`，默认 `_report-cache/`）的相对路径（列 precision 100 限制），下载/删除时拼接目录；文件名用参数摘要 hex（防超长）。
- 渲染统一走 `ITemplateOutput.generateToResource`（html/xlsx/pdf 同出口）。

### 配置（IConfigReference 运行时读取，可动态生效）

| 配置 | 缺省 | 说明 |
|------|------|------|
| `nop.report.result-cache.enabled` | true | 缓存开关 |
| `nop.report.result-cache.ttl` | 0（不过期） | TTL 秒；过期仅不命中，不清理（已知限制） |
| `nop.report.result-cache.dir` | `_report-cache/` | 存储目录 |

### 失效与清理

- `invalidate(rptId)`：best-effort 删除缓存目录文件 + 直接 SQL 删除记录（ORM session 边界：跨 session 实体 delete 需 attach，SQL 最简）。
- TTL 过期记录/文件的定期清理：follow-up（当前过期仅不命中）。

### 已知限制

- 数据版本仅支持 DATE 型（bizDate）；并发 check-then-insert 有竞态窗口（正确性由「取最新」兜底）；innerHTML/集群共享目录见 roadmap follow-ups。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| 引擎内部缓存（IReportEngine 挂缓存） | 授权/实体/配置都在服务层；引擎应保持纯渲染 |
| 新增 ORM 列承载 renderType/数据版本 | ORM 模型 protected area；既有列语义可承载 |
| 内存缓存 | 结果文件本就落盘消费；内存双写复杂且重启丢失 |
| 键含用户身份 | 缓存应跨用户复用；越权由授权层把关（auth-wiring 缓存协同） |

## 五、与已有设计的关系

- `auth-wiring.md`「缓存协同」：命中前过渲染入口授权——本设计落实。
- `parameter-model.md`：参数以「解析后的规范化值」参与缓存键——本设计落实（调用方传参后 JSON 规范化摘要）。
- nop-datav D5 调度可经 `NopReportCache` 的异步接口消费，不反向依赖。
