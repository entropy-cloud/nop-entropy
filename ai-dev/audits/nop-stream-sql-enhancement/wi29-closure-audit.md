# WI29 Closure Audit——09-wi29-case-cast-eval.md

> Auditor: 独立子 agent（fresh session）：agent_ab25779e-902e-46e5-b498-d274d69c8a34
> Date: 2026-10-08
> Verdict: 首轮 **REJECTED**（B1 CASE 打印通道空心 + M1 陈旧钉码 + M2 GROUP BY cast 可空键 + M3 §4b SELECT 行缺）→ 补救后复核 **APPROVED**

## R1 发现（全部有实跑探针证据）

- **B1**：CASE 打印通道空心——`java.util.Objects` 裸 FQN 在 XLang scope-var-is-undefined（实跑复现），字符串钉码 ≠ 语义落地（guide Lessons #8 模式）
- **M1**：TestStreamAggregatorFunctionResolver.exprCompileFailureFailsFast 陈旧钉 CASE 已合法
- **M2**：GROUP BY cast(可空列) 运行时 null-key 失败（引擎既有 reduce requires key context 约束，非 CAST 特有）
- **M3**：§4b SELECT 投影行未补 CASE/CAST
- m1 joinWhere/keyXplExprs prependImports 未统一（当前 join+WHERE 不可达）；m2 双通道判等分歧；m3 MANAGED_TYPE_BY_NAME 死常量键域不一致；m4 孤立 javadoc；m5 plan 文本状态
- 预先存在观察：join+WHERE 限定名列 `unknown join scope qualifier`（非 WI29 引入，建议 backlog）

## 补救处置（全部实测绿）

| # | 处置 | 证据 |
|---|---|---|
| B1 | printCase 重写：java.util.Objects 加入 requiredImports 前置；简单式数值感知条件（instanceof Number → doubleValue 比较，fallback Objects.equals；null 短路 false）——与 eval 通道 valuesMatch 语义收敛；搜索式 `Objects.equals(true, when)`（Boolean.TRUE 裸 FQN 同不可用已修）| **TestCaseCastE2E 新增真实执行 E2E 全绿**：7 行 sign/amt 精确断言（P/1×4、N/-5、Z/null），null 透传正确 |
| M1 | exprCompileFailureFailsFast 改指 concat | 复跑绿 |
| M2 | 裁定为引擎既有 null-key 约束（与 GROUP BY amount 含 null 同族）→ plan watch-only follow-up | 记录于 plan Non-Blocking Follow-ups |
| M3 | §4b SELECT 行补 CASE/CAST（WI29 形态+闭集边界） | sql-subset :98 |
| m1-m5 | m1 静态确认 join+WHERE 不可达故不升级；m2 随 B1 收敛；m3/m4 记录 | plan Closure |

## 复核通过项

- 求值通道 21/21（简单/搜索 CASE、null 语义、CAST 跨类型/闭集外/resolveType）——auditor 独立复跑
- compile 侧 39/39（含 caseCastProduceProjectedOutput 字符串钉码 + E2E 执行钉码）
- §4a#7 修订质量（strikethrough + 闭集边界 + 钉码锚点）；零越权；scan-hollow 0 发现
- 回归：_tmp/wi29-full-regression.log BUILD SUCCESS
