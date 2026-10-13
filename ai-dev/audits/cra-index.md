# CRA 审计-修复报告索引（concurrency-resource-audit）

> Campaign：全模块并发·异步·资源专项审计与修复（C1–C10 口径首次全模块滚动）
> Roadmap：`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md`
> 启动时间：2026-10-14
> 绿色基线 commit：b3a9c76bed（`./mvnw clean install -T 1C` BUILD SUCCESS，2026-10-14）

本索引是 CRA campaign 全部审计报告与发现台账的唯一入口，是 roadmap 9.1 对账的输入。纪律：报告产出即更新本索引；修复完成即回填；P0 走即时通道（发现即修，不入批量修复）；P2 逐项裁决后回填。

## 报告清单

| 报告目录 | 工作项 | 审计单元 | 维度 | P0 数 | P1 数 | P2 数 | P3 数 | 状态 |
|---------|--------|---------|------|-------|-------|-------|-------|------|
| （待审计产出后逐行登记） | | | | | | | | |

## P0 发现追踪（即时通道）

> P0 永不进入 Phase 8 批量修复：发现即就地修复（审计 plan 内加修复 Phase）或异步注入独立修复 plan。

| Finding ID | 报告 | 描述 | 修复路径 | 修复状态 |
|-----------|------|------|---------|---------|
| （无） | | | | |

## P1 发现汇总（待 Phase 8 批量修复）

> 7.1 合并裁定后按批次展开；每条 P1 必须在 9.1 对账前变为「已修 / deferred+successor」。

| Finding ID | 报告 | 单元 | 一句话描述 | 裁定批次 | 修复状态 |
|-----------|------|------|-----------|---------|---------|
| （无） | | | | | |

## P2 裁决台账（7.1 逐项裁决）

> P2 默认不在修复范围；7.1 逐项裁决为「修（in-scope）/ deferred（必须带 Why Not Blocking + successor 指针）」；已确认 live 的 must-fix P2 不得降级 deferred。

| Finding ID | 报告 | 单元 | 一句话描述 | 裁决 | Why Not Blocking / 批次 |
|-----------|------|------|-----------|------|----------------------|
| （无） | | | | | |

## 使用说明

- Finding ID 全局唯一，格式 `{单元标识}-C{N}-{序号}`，如 `nop-stream-core-runtime-C1-001`；单元标识取 roadmap 工作项中的单元名（归档目录名一致）。
- 报告目录命名：`ai-dev/audits/{YYYY}-{MM}/{YYYY-MM-DD}-{HHMM}-deep-audit-{单元标识}-concurrency/`，每维度一个文件（`{C编号}-{英文短名}.md`）+ `summary.md`。
- 审计工作项的 done 门控 = 审计目录完整 + summary.md 归档 + 本索引已更新 + 独立 closure audit PASS。
- 与其他 campaign（security / full-code-review / nop-stream-independent-audit）发现重叠时，先完成修复方为准，另一方在此登记 cross-reference 后关闭，不重复修复。
