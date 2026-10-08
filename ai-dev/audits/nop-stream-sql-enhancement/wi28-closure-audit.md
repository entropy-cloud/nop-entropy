# WI28 Closure Audit——08-wi28-global-aggregation.md

> Auditor: 独立子 agent（fresh session）：agent_fce90ef0-4a17-41ef-ac0c-d69db675c692
> Date: 2026-10-08
> Verdict: 首轮 **REJECTED**（B1 NPE 回归 + M1 docs 未同步）→ 补救后复核通过 → **APPROVED**

## 验证矩阵

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | E2E | PASS | TestGlobalAggregationE2E 真实执行（compile→build→execute→sink），运行总和 [1,3,3,6,10,5,6]（null 跳过）终值 6；auditor 复跑 1/1 |
| 2 | 接线 | PASS | 分派 :348-356 → 合成键 :914-915 → keyBy event[0][0]（:555-558）→ reduce；钉码+E2E 双证 |
| 3 | 无静默跳过 | PASS（B1 修复后） | HAVING typed 守卫 + **B1 修复**：窗口伪表源+全局聚合分派 typed 拒绝（三型钉码 windowedGlobalAggregationFailsFast 实测绿） |
| 4 | 新测试覆盖 | PASS | shape + HAVING 守卫 + 三型 TVF + E2E；旧 fail-fast 钉删除（diff 证实）；TestStreamSqlCompiler 38/38 |
| 5 | 回归 | PASS | _tmp/wi28-full-regression.log BUILD SUCCESS（sql 123）；B1 修复后 focused 39/39 |
| 6 | owner docs | PASS（M1 补齐后） | nop-stream-sql.md :36 措辞、sql-subset §4b :101/:103 两行、log 条目 |
| 7 | 零越权 | PASS | git 变更恰限 nop-stream-sql 两文件 + E2E 新文件 + docs/plan/log |
| 8 | Anti-Hollow | PASS | 合成键→keyBy→单 reduce 静态 trace + E2E 真跑；scan-hollow exit 0 |

## R1 问题与处置

- **B1**（Blocker）：窗口 TVF+全局聚合由 typed 拒绝退化为裸 NPE（probe 实证）→ 分派 typed 拒绝修复 + 探针转正三型钉码，focused 复绿。
- **M1**：owner docs 与日志未更新 → 补齐（nop-stream-sql.md :34/:36/:46、sql-subset :101/:103、log WI28 条目）。
- m1：HAVING 双层守卫 defense-in-depth 保留；m2：plan 实现后拟制（实现先行偏差已声明），Phase 2 按实况翻转；m3：双 HAVING 钉意图不同接受。
- plan 状态裁定：Phase 2 可直接 draft → completed（guide 规则 8/12 以独立 audit 为据）。
