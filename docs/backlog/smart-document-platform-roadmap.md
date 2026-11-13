# Smart Document Platform Roadmap — 智能单据中台

> Last Updated: 2026-10-28
> Source: Gitee GVP 调研 2026-10-28（OnnxOCR 及各 ERP 单据/发票/合同处理能力盘点）

## Purpose

本 roadmap 追踪"智能单据中台"在 nop 平台上的落地：覆盖 **发票/合同 OCR 识别 → 单据草稿生成 → 审批流 → 台账归档** 全链路。差异化定位：不做通用 OCR 引擎（对标 OnnxOCR 只做接入与封装），而是利用 nop 的**元模型优势**——用 nop-orm 元数据（实体/字段定义）约束识别结果的字段映射，用 nop-codegen 模型驱动生成单据界面与服务，用 nop-wf 编排审批，用 nop-report/nop-search 完成台账与归档，形成"识别即建模、建模即生成"的单据平台能力。

Does not contain implementation details. Each `todo`/`planned` work item is owned by its execution plan.

## Work Item Status

> **This is the only dynamic state block. Update status only here.**
> Humans set items and their order; AI takes the first `todo` item, drafts/executes
> plans, and writes the item back to `done` when closure audit passes.

### Milestone 1: 调研（OCR 方案与对标项目盘点）

- 1.1 OCR 方案与对标项目盘点（OnnxOCR、PaddleOCR、各 ERP 票据/合同识别能力矩阵）: `todo`
- 1.2 调研阶段深度审计（对照 live repo 核对 nop-ai / nop-ocr 相关现状与本文 contract drift）: `todo`
- 1.3 调研阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

### Milestone 2: 设计

- 2.1 识别服务 SPI + 字段映射模型设计（识别结果→ORM 实体、置信度、人工校正交互契约）: `todo`
- 2.2 单据生成 / 审批编排 / 台账归档总体设计文档: `todo`
- 2.3 设计阶段深度审计（设计文档 vs live repo 契约核对，实现与 contract drift 检查）: `todo`
- 2.4 设计阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

### Milestone 3: 开发

- 3.1 票据/合同识别服务抽象 SPI（`IDocRecognitionService` + OnnxOCR/PaddleOCR 适配器骨架）: `todo`
- 3.2 识别结果→ORM 实体字段映射模型（基于 nop-orm 元模型的字段映射 + 类型校验 + 映射准确率提升）: `todo`
- 3.3 置信度模型与人工校正工作台（低置信字段高亮、逐字段确认、校正样本回流）: `todo`
- 3.4 单据草稿生成（识别结果→单据实体草稿，复用 nop-codegen 模型驱动生成界面/服务）: `todo`
- 3.5 单据模板管理（模板定义/版本化/字段绑定，模型驱动的模板 DSL）: `todo`
- 3.6 单据审批流编排（复用 nop-wf：单据状态机 + 审批节点 + 回退/会签）: `todo`
- 3.7 审批 HITL 人工介入（复用 nop-ai-agent：AI 预审 + 人工复核/HITL 交接）: `todo`
- 3.8 台账归档与查询（归档实体 + nop-search 全文检索 + nop-duckdb 分析查询）: `todo`
- 3.9 报表导出与打印（复用 nop-report：台账报表、Excel 导出与打印模板）: `todo`
- 3.10 开发阶段深度审计（对照 live repo 逐工作项核对实现与 contract drift）: `todo`
- 3.11 开发阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

### Milestone 4: 验证

- 4.1 端到端验证（发票/合同样张→识别→草稿→审批→归档→报表全链路回归 + 字段映射准确率报告）: `todo`
- 4.2 验证阶段深度审计（对照 live repo 核对验证覆盖与 contract drift）: `todo`
- 4.3 验证阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

> Milestones carry no status of their own. A milestone is considered reached when
> all of its work items are `done`. Only work items carry todo/ready/done status.

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | Not started, no plan |
| `ready` | Has execution plan, passed draft review |
| `done` | Complete, passed closure audit |

## Framework / platform reuse

| Capability | Provider | Notes |
| --- | --- | --- |
| LLM/Chat 模型 SPI、Prompt、Embedding 接口 | nop-ai-core | 识别后处理、合同要素抽取、AI 预审复用 |
| 人工介入 / Agent 编排 | nop-ai-agent | 审批 HITL：AI 预审→人工复核交接 |
| MCP 工具暴露 | nop-ai-mcp-server | 识别/单据/台账能力以 MCP tool 暴露给 Agent |
| 向量检索 SPI | nop-ai-rag `IVectorStore`（PgVector/InMemory） | 合同类知识库、相似单据召回（可选增强） |
| 字段映射元模型 | nop-orm | 识别结果→实体的映射约束、类型校验 |
| 模型驱动生成 | nop-codegen | 单据界面/服务/API 低代码生成 |
| 审批流编排 | nop-wf（含 nop-wf-ai） | 单据审批工作流、状态机 |
| 报表与 Excel | nop-report（中国式报表引擎，已完成） | 台账报表、Excel 导出/打印模板 |
| 全文检索 | nop-search（Lucene，约 25% 完成度） | 归档单据全文查询；注意完成度风险 |
| 分析查询 | nop-duckdb | 台账统计分析、聚合查询 |
| 文件存储 | nop-file | 原始扫描件/图片归档 |
| 业务对象/CRUD 底座 | nop-biz / nop-dao | 单据实体持久化与业务服务 |

## Current baseline

**Already shipped（live repo 核对，2026-10-28）：**

- nop-ai 模块组整体完成度约 45%（README）：含 nop-ai-core（chat/prompt/embedding SPI 及 reliability/command/persist 等支撑包）、nop-ai-agent、nop-ai-gateway、nop-ai-mcp-server（含 nop-spring-mcp-server）、nop-ai-coder、nop-ai-codegen、nop-ai-rag
- nop-ai-rag：`IVectorStore` SPI 已落点本模块（`PgVectorStore` + `InMemoryVectorStore`），SPI 已随 plan 2421 迁入，包名保留 `io.nop.ai.core.*`
- nop-wf：完整工作流模块组（api/core/dao/app/codegen + nop-wf-ai），README 状态已完成
- nop-report：中国式报表引擎模块组（api/core/dao/app/codegen + nop-chart-report），README 状态已完成，Excel 导出/打印能力可用
- nop-search：api/core/lucene 三模块，README 状态 25%，仅可作受限依赖
- nop-codegen：数据驱动的代码生成器，已完成；nop-orm：下一代 ORM 引擎，已完成
- nop-duckdb：分析查询模块已存在；nop-file：文件存储已存在

**Main gaps:**

- 平台无任何 OCR / 票据 / 合同识别能力（无识别服务 SPI，无 OnnxOCR 等引擎适配）
- 无"识别结果→ORM 实体"的字段映射模型与置信度/人工校正机制
- 无单据模板管理与识别→草稿生成链路
- 审批流未与单据场景打通，nop-ai-agent 的 HITL 能力未沉淀为单据审批模式
- 无台账归档模型；nop-search 完成度低（25%），归档全文检索可能需要补齐或降级为 duckdb/SQL 检索

## Milestones

| # | Milestone | Work items | Deps | Reuse focus |
| --- | --- | --- | --- | --- |
| M1 | 调研 | 1.1-1.3 | — | OnnxOCR 等外部方案盘点 |
| M2 | 设计 | 2.1-2.4 | M1 done | nop-orm 元模型、nop-codegen、nop-wf、nop-report |
| M3 | 开发 | 3.1-3.11 | M2 done | nop-ai-core/agent/mcp-server、nop-orm、nop-codegen、nop-wf、nop-search、nop-duckdb、nop-report |
| M4 | 验证 | 4.1-4.3 | M3 done | nop-autotest / e2e |

> M3 里程碑内的两个审计工作项（3.10 深度审计、3.11 开放审计）依赖 3.1-3.9 全部 `done` 后执行。

## Work Item Details

### 1.1 OCR 方案与对标项目盘点

**Goal:** 盘点 OnnxOCR、PaddleOCR、各 ERP（金蝶/用友/SAP 等）的票据与合同识别能力，产出能力矩阵与选型结论；明确本平台"接入与封装、不自研 OCR 模型"的边界。

**Deliverables:** 调研报告（对标能力矩阵、引擎选型建议、许可证/部署形态结论）、SPI 需求输入清单。

**Owner Doc:** 调研报告（本工作项产出） | **依赖:** — | **复用:** 外部开源方案

### 1.2 调研阶段深度审计

**Goal:** 对照 live repo 深度审计：核对 nop-ai 各子模块、nop-wf、nop-report、nop-search 的实际能力与 1.1 调研报告中引用的基线结论是否 drift；发现 contract drift 须登记并回写文档。

**Owner Doc:** 审计报告 | **依赖:** 1.1 | **复用:** 本文 Current baseline 块

### 1.3 调研阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；仅输入"调研报告 + 基线摘要"，开放质疑选型结论与范围边界。未通过则 1.1 返工。

**Owner Doc:** 开放审计意见 | **依赖:** 1.1, 1.2 | **复用:** —（上下文隔离是强制要求）

### 2.1 识别服务 SPI + 字段映射模型设计

**Goal:** 设计票据/合同识别服务抽象（SPI：同步/异步识别、批量、回调），以及识别结果→ORM 实体的字段映射模型：以 nop-orm 实体元数据为映射目标 schema，定义字段绑定规则、类型校验、置信度数据结构与人工校正交互契约。

**Deliverables:** SPI 接口定义草案、字段映射元模型设计、置信度与校正流设计文档。

**Owner Doc:** `ai-dev/design/smart-doc/`（设计文档目录，本阶段产出） | **依赖:** M1 done | **复用:** nop-orm 元模型

### 2.2 单据生成 / 审批编排 / 台账归档总体设计文档

**Goal:** 总体设计：识别→单据草稿（nop-codegen 模型驱动）、模板管理 DSL、nop-wf 审批编排与 nop-ai-agent HITL 介入模式、台账归档与检索/报表导出链路。明确各环节复用面与新增面。

**Deliverables:** 总体设计文档 + 各子系统设计小节 + 非目标（Non-Goal）清单。

**Owner Doc:** 同上设计文档目录 | **依赖:** 2.1 | **复用:** nop-codegen, nop-wf, nop-ai-agent, nop-report, nop-search, nop-duckdb

### 2.3 设计阶段深度审计

**Goal:** 对照 live repo 核对设计文档中引用的每一处 SPI/接口/模块能力是否与实现一致（contract drift 检查），特别是 nop-search 25% 完成度下的检索依赖降级路径是否成立。

**Owner Doc:** 审计报告 | **依赖:** 2.1, 2.2 | **复用:** live repo

### 2.4 设计阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；仅输入"设计文档 + diff 摘要"。未通过则回到对应设计工作项修订。

**Owner Doc:** 开放审计意见 | **依赖:** 2.1-2.3 | **复用:** —

### 3.1 票据/合同识别服务抽象 SPI

**Goal:** 实现 `IDocRecognitionService` SPI 及 SPI 装配契约；提供 OnnxOCR（本地推理）与云端 OCR 两类适配器骨架；支持图片/PDF 输入、同步与异步识别、结构化结果输出（区域、字段、置信度）。

**Out of scope:** 具体字段映射（3.2）、OCR 模型训练。

**Owner Doc:** 2.1 SPI 设计 | **依赖:** M2 done | **复用:** nop-file（原件存储）, nop-ai-core（配置/错误体系风格）

### 3.2 识别结果→ORM 实体字段映射模型

**Goal:** 基于 nop-orm 元模型实现字段映射引擎：以目标实体元数据为 schema，完成 OCR 字段名→实体属性绑定、类型转换校验、数组/明细行映射（发票明细）、映射规则可配置与缓存；以元数据约束提升映射准确率。

**Owner Doc:** 2.1 设计 | **依赖:** 3.1 | **复用:** nop-orm, nop-core 元模型设施

### 3.3 置信度模型与人工校正工作台

**Goal:** 字段级置信度模型（阈值策略、低置信高亮）；人工校正界面（逐字段确认/修改、原图区域对照）；校正结果回流为映射规则/样本数据，用于后续准确率优化。

**Owner Doc:** 2.1 设计 | **依赖:** 3.2 | **复用:** nop-biz/nop-codegen（界面生成）, nop-orm

### 3.4 单据草稿生成

**Goal:** 识别+校正结果→单据实体草稿（含明细行），自动建立单据与原件的关联；利用 nop-codegen 按单据模型生成列表/详情/编辑界面与服务 API，做到"建模即生成"。

**Owner Doc:** 2.2 设计 | **依赖:** 3.3 | **复用:** nop-codegen, nop-biz, nop-orm

### 3.5 单据模板管理

**Goal:** 单据模板的定义、版本化与字段绑定管理：模板描述单据类型对应的识别 schema、映射规则、界面布局引用；支持模板 DSL 与导入导出。

**Owner Doc:** 2.2 设计 | **依赖:** 3.2 | **复用:** nop-codegen 模型驱动机制, nop-orm 元模型

### 3.6 单据审批流编排

**Goal:** 基于 nop-wf 实现单据审批编排：单据状态机（草稿/待审/通过/驳回/归档）、审批节点配置、回退与会签；审批动作与单据实体状态联动。

**Owner Doc:** 2.2 设计 | **依赖:** 3.4 | **复用:** nop-wf（nop-wf-core, nop-wf-api）

### 3.7 审批 HITL 人工介入

**Goal:** 复用 nop-ai-agent 实现审批 HITL：AI 预审（规则 + LLM 抽取要素复核）产出建议与风险标注，低置信/高风险单据自动转人工复核，人工结论回流 Agent 上下文；能力可选通过 nop-ai-mcp-server 以 MCP tool 暴露。

**Owner Doc:** 2.2 设计 | **依赖:** 3.6 | **复用:** nop-ai-agent, nop-ai-core, nop-ai-mcp-server

### 3.8 台账归档与查询

**Goal:** 归档实体模型与归档流程（审批通过后自动归档，原件入 nop-file）；归档查询：结构化 SQL 查询 + nop-duckdb 统计分析；全文检索优先接入 nop-search，若完成度不足（当前 25%）则降级为 duckdb/SQL 检索并在设计中登记。

**Owner Doc:** 2.2 设计 | **依赖:** 3.6 | **复用:** nop-duckdb, nop-search, nop-file, nop-orm

### 3.9 报表导出与打印

**Goal:** 基于 nop-report 实现台账报表与 Excel 导出：归档台账汇总报表、单据打印模板（发票/合同套打）、按模板导出。

**Owner Doc:** 2.2 设计 | **依赖:** 3.8 | **复用:** nop-report（含 nop-chart-report）, nop-duckdb

### 3.10 开发阶段深度审计

**Goal:** 对照 live repo 逐工作项核对实现与 contract drift：SPI 签名、映射元模型、审批状态机、归档/报表链路是否与 2.x 设计文档一致；drift 登记并回写设计文档。

**Owner Doc:** 审计报告 | **依赖:** 3.1-3.9 all done | **复用:** live repo

### 3.11 开发阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；输入"任务计划 + diff 摘要 + 验证输出"三件套，开放评审实现质量与设计一致性。未通过则定向返工。

**Owner Doc:** 开放审计意见 | **依赖:** 3.10 | **复用:** —

### 4.1 端到端验证

**Goal:** 全链路回归：发票与合同样张→识别→映射→人工校正→草稿→审批（含 HITL 分支）→归档→检索→报表导出；产出字段映射准确率报告与性能基线。Pass/fail 验证必须程序化断言。

**Owner Doc:** 2.2 设计（验收标准小节） | **依赖:** M3 done | **复用:** nop-autotest, e2e 测试设施

### 4.2 验证阶段深度审计

**Goal:** 对照 live repo 核对端到端验证的覆盖完整性（四阶段场景、异常分支、校正回流）与 contract drift；核对准确率报告数据来源可复现。

**Owner Doc:** 审计报告 | **依赖:** 4.1 | **复用:** live repo

### 4.3 验证阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；输入"验证报告 + 审计材料"。通过后整条 roadmap 收口，★ 智能单据中台 1.0 达成。

**Owner Doc:** 开放审计意见 | **依赖:** 4.1, 4.2 | **复用:** —

★ **Milestone: 智能单据中台 1.0**（unlocks when M1-M4 all done）

## Cross-cutting concerns

| Concern | Notes |
| --- | --- |
| OCR 边界 | 平台只做接入/封装/映射，不自研 OCR 模型；引擎可替换（SPI） |
| 映射准确率 | 以 nop-orm 元模型约束输出 schema；置信度 + 人工校正兜底；校正样本回流 |
| nop-search 风险 | 完成度约 25%，全文检索须有 duckdb/SQL 降级路径 |
| HITL 上下文隔离 | 开放审计工作项强制 fresh sub-agent，不复用执行者上下文（1.3/2.4/3.11/4.3） |
| 敏感数据 | 发票/合同含敏感信息：原件存储权限、日志脱敏、MCP 暴露面收敛 |
| 异步与重试 | 识别为长耗时操作：异步任务 + 回调 + 失败重试 |
| 测试策略 | Must automate: 字段映射引擎、审批状态机；Should have: 校正工作台、报表导出 |

## Rules

- This file is a state index and coarse decomposition, not an execution plan.
- Each `ready`/`done` work item is owned by its execution plan.
- Status changes happen only in the Work Item Status block at the top.
- Milestones carry no status; a milestone is reached when all its work items are `done`.
- Every stage milestone MUST contain exactly two audit work items: 阶段深度审计 (deep audit vs live repo, contract drift) and 阶段开放审计 (independent fresh sub-agent, no executor context reuse).
