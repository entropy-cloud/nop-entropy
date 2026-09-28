# 30 N8.1 评测框架（搜索质量 MRR + 影响分析 F1 + 基线报告）

> Plan Status: draft
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N8.1；deps N1.3 done（查询路径物化）、N4.3 done（混合搜索）
> Related: M9（任务级验收——本项测工具自身指标，非 AI 代理有效性）

## Purpose

落地 N8.1：nop-code 工具自身指标的评测框架——搜索质量 MRR（Mean Reciprocal Rank）、影响分析准确性 F1、token 效率基线。产出评测脚本（JUnit 测试）+ ground-truth 集 + 基线报告。

## Current Baseline

- `CodeSearchService`：混合搜索已贯通（N4.3），InMemoryVectorStore + Stub 嵌入可确定性测试。
- `DependencyPropagator`：增量依赖传播 2-hop（N3.1），有单测。
- `LlmSemanticEdgeExtractor`：LLM 语义边（N7.1），有单测。
- nop-code-service 测试基线：274 tests 0 failures。

## Goals

- G1：`CodeSearchQualityTest`：ground-truth 查询集（10 个查询+预期文件名）→ MRR 评分 → 断言 MRR ≥ 0.5。
- G2：`ImpactAnalysisF1Test`：已知变更→预期受影响文件集 → DependencyPropagator 传播 → F1 评分 → 断言 F1 ≥ 0.5。
- G3：基线报告写入测试注释（token 效率以 Stub 嵌入 dim=16 为基线，非生产 API 维度）。

## Non-Goals

- JMH 微基准（构建性能已有 perf-tuning.md 基线）；AI 代理有效性（M9）；生产维度嵌入质量评估。

## Scope

### In Scope

- `nop-code/nop-code-service/src/test/java/io/nop/code/service/eval/`：两个评测测试类。

### Out Of Scope

- nop-code-core/main 改动；JMH 模块；AI agent。

## Execution Plan

### Phase 1 - 评测测试 + 基线

Status: planned
Targets: `nop-code-service/src/test/java/io/nop/code/service/eval/`

- Item Types: `Proof`

- [ ] `CodeSearchQualityTest`：构建 nop-code 多文件索引（InMemoryVectorStore + KeywordEmbedding）→ 10 个 ground-truth 查询 → MRR 计算 → 断言 ≥ 0.5
- [ ] `ImpactAnalysisF1Test`：构建多文件依赖图 → DependencyPropagator 传播 → 与已知受影响集对比 → F1 计算 → 断言 ≥ 0.5

Exit Criteria:

- [ ] 两个评测测试全绿
- [ ] `./mvnw test -pl nop-code/nop-code-service -o` 零回归
- [ ] `ai-dev/logs/` 条目已更新

## Closure Gates

- [ ] 两个评测测试全绿（MRR ≥ 0.5, F1 ≥ 0.5）
- [ ] `./mvnw test -pl nop-code/nop-code-service -o` 零回归
- [ ] check-plan-checklist --strict exit 0
- [ ] closure audit

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
