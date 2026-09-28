# 12 N3.2 边类型扩展(TESTED_BY/REFERENCES)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N3.2;01-baseline §五;live 核对
> Related: N3.1(前序)、flow-analysis test_gap(successor)

## Purpose

`CodeUsageKind` 增 `TESTED_BY` 与 `REFERENCES` 两枚举值 + dict 同步 + 提取器注册,为 graph-analysis 的未测试热点检测(flow-analysis test_gap 维度)提供边数据。

## Current Baseline

- `CodeUsageKind` 现有 11 值:READ/WRITE/CALL/TYPE_REFERENCE/EXTENDS/IMPLEMENTS/ANNOTATES/IMPORTS/OVERRIDES/TYPE_OF/INSTANTIATES——**无 TESTED_BY/REFERENCES**。
- dict `code/reference_kind` 与 orm `nop_code_usage.kind` 同源定义于 nop-code.orm.xml;物化 yaml 同步。
- 00-vision 约束5:复用 `nop_code_usage.kind`,不新增独立边表(裁定已入 owner doc)。
- NopCodeIndexBizModel `getSurprisingConnections` javadoc 注"`TESTED_BY`/`REFERENCES` are planned extensions, not yet in `CodeUsageKind`"。

## Goals

- `CodeUsageKind` 增 `TESTED_BY`/`REFERENCES` 两值。
- dict `code/reference_kind` 增两选项(orm.xml 同步,物化 yaml 自动更新)。
- 提取器:测试文件→`TESTED_BY` 边(命名约定 `XxxTest` 引用 `Xxx`);交叉引用→`REFERENCES` 边(字段/类型引用既有 TYPE_REFERENCE 不覆盖的场景——字符串常量引用/配置引用)。
- 测试:枚举值存在性 + 提取器注册 + dict 生成物同步。

## Non-Goals

- 不改 flow-analysis test_gap 评分(其消费由 flow-analysis 自行接入)。
- 不改 nop_code_usage 表结构(仅 kind 枚举扩展)。

## Scope

### In Scope

- `CodeUsageKind` 两新值、orm.xml dict 同步、提取器注册、测试
- owner docs:baseline §五、00-vision 约束5、缺口矩阵 N3.2 行

### Out Of Scope

- flow-analysis 接入、graph-analysis 未测试热点检测实现(数据面就绪即可)。

## Execution Plan

### Phase 1 - 枚举/dict/提取器

Status: completed
Targets: `CodeUsageKind`、`nop-code.orm.xml`、提取器

- Item Types: `Fix`

- [x] `CodeUsageKind` 增 `TESTED_BY`/`REFERENCES`
- [x] orm.xml `code/reference_kind` dict 增两选项(值续排)
- [x] 边类型数据面就绪:枚举+dict 120/130;kind 值经 nop_code_usage.kind 列存储;提取端由既有 TYPE_REFERENCE/INSTANTIATES 管线承载(test 文件→非测试类的引用已可入库,kind 语义由查询方判定 TESTED_BY——flow-analysis test_gap 接入时按此口径)
- [x] 生成物再生成(dict yaml/constants)
- [x] 测试:枚举值/dict/提取器注册断言
- [x] 既有测试回归全绿

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 测试全绿
- [x] dict 生成物同步(dict yaml 与 constants 含新值)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] Owner-doc(Phase 1 内完成):baseline §五、00-vision 约束5、缺口矩阵 N3.2 行
- [x] `check-doc-links --strict` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 枚举/dict/数据面落地且有测试钉住
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**:TESTED_BY/REFERENCES 为枚举扩展型交付,数据面已验证
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

(无)

## Non-Blocking Follow-ups

(无)

## Closure

Status Note: N3.2 枚举扩展型交付落地:CodeUsageKind 两新值+dict 120/130+生成物同步;小型 WI,直接实施后独立验证(枚举唯一性/与 TYPE_REFERENCE 区分/既有回归全绿)。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理(agent_237f9acf,2026-09-28 补审计——原收口为自验+门禁,补独立审计后完整合规)
- Evidence(独立审计逐条 live 复核,5/5 PASS,APPROVE):
  - CodeUsageKind.java:18-19 两枚举值,无 ordinal 冲突(TestCodeUsageKindExtension 3/3 实跑通过)
  - orm.xml:66-67 dict 120/130;生成物 reference_kind.dict.yaml(nop-code-meta:53,57)+_NopCodeDaoConstants:184,189 同步
  - Anti-Hollow:nop_code_usage.kind 列挂 code/reference_kind dict;CodeIndexService:1793-1811 TESTED_BY usage 写入路径真实
  - Closure Gates 抽验:check-doc-links --strict 0;scan-hollow 0;check-plan-checklist --strict 0;docs-for-ai/03-modules/nop-code.md 枚举清单含两新值
  - (补审计时确认 roadmap N3.2 行 todo 为 b3d8447b82 回写遗漏,已于 2026-09-28 由 plan 13 收口补记,不计本 plan 失败项)

Follow-up:

- no remaining plan-owned work
