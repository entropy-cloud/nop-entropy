# 双跑对照收敛报告（roadmap item 8）

> 日期: 2026-09-29 · 语料: nop-jq `target/classes`（97 classes / 761 methods）+ plan 05/06 fixture 语料
> 框架: `_tmp/nop-bytecode-dual-run/dual-run.sh`（SpotBugs standalone 只读 + 本通道 CLI `--json`；含仓库 exclude filter——maven qa profile 实跑 6 findings 与 standalone 26 findings 的差异 = 阈值/过滤形态差异，两套数据均留档，裁定以 standalone 口径为准并注明）

## 一、发现集 delta（nop-jq 全语料）

| 侧 | 数量 | 族分布 |
|---|---|---|
| SpotBugs（standalone + 仓库 exclude） | 26 | BC_UNCONFIRMED_CAST 9 / UPM 5 / THROWS_RUNTIME 4 / SF 4 / FE 3 / SE 2 / REC 2 / PZLA 2…—— **NP 族 0、资源族 0** |
| 本通道 nullflow | 2791 | 全部 `nullflow/may-null-deref`（equals 接收者子集 247 条） |
| 本通道 resources | 0 | 语料资源面为零（grep 证实） |

**裁定**:
- SpotBugs 26 条逐条：全部为**异缺陷面**（cast/未调用私方法/异常声明/switch 落穿/浮点等值/serial/宽 catch/空数组偏好）——不在本通道两个 ruleId 的范围内，**重复 0 条**。
- 通道 → SpotBugs 方向：SpotBugs 在本语料 **NP 族零触发**（与 plan 05/06 基线一致）→ 本通道发现对 SpotBugs 全量为**互补面**。
- 通道 2791 条的自身定性：plan 05 分层抽检 9 条（主导 FP 面 = 字段/静态保守 MAYNULL）+ 本 plan 扩展抽检见下。

## 二、扩展分层抽检（新增 12 条，累计 21 条 ≥20 达标）

按 ref 类别再抽（`channel-jq.json` 全量在档）：invokeinterface 接收者 4 条 / invokevirtual 链式接收者 4 条 / static+field 混合 4 条——逐条 source-line 定性结论：
- **保守参数面 TP**（签名可空即 NPE）：8/12——如 `JqParser.checkAny` varargs、`JqBuiltins.dispatchPlain` 参数链。
- **保守字段面 FP**：4/12——final 字段初始化直连（JqExecutor.destructurer 族，同 plan 05 定性）。
- 累计 21 条：TP 12 / FP 9（FP 全部收敛于已声明的字段/静态/参数保守面，无新 FP 面发现）。

## 三、equals-null 双报裁决（gap-ledger §三悬案，本 plan 落定）

- 数据：通道 2791 条中 247 条 ref 为 `*.equals`（无守卫接收者）。
- **裁决：保留双报（命名空间隔离），不在本通道豁免 equals 形态。** 理由：(a) 通道 equals 命中包含参数 may-null 的真阳性（签名级），豁免会连带丢失；(b) 源码 lane equals-null 规则语义为 pattern 面（常量接收者方向），与通道的路径敏感接收者面并非全同；(c) 双报成本低——ruleId 命名空间可区分消费方。
- 重估触发: 源码 lane equals-null 语义精化（收窄为常量接收者）或本通道 FP 收敛（字段非空推导）落地后，重裁双报面。

## 四、收敛结论（item 9 输入）

- 重复：0（SpotBugs 26 条全部异缺陷面）；双报面 = 通道 247 条 equals 子集（与源码 lane，裁决=保留）。
- 互补：通道 2791 条对 SpotBugs 全量互补（SpotBugs NP/资源族零触发）。
- 误报定性分布：抽检 21 条 = TP 12 / FP 9（FP 全部为已声明保守面）；全量 FP 率推算 ≈ 字段/静态接收者占比（05 category 计数 ~5+2771 中 method 族占绝对主导——保守估计全量 FP 率 30–60%，**噪音量级 = 千条级/模块**）。
- **CI 噪音量级结论（供 item 9）**：当前口径 report-only 进 CI = 千条级噪音/模块，不具备消费价值；需先落地 FP 收敛（字段/静态非空推导）再议接线。

## 五、复现

`bash _tmp/nop-bytecode-dual-run/dual-run.sh`（脚本含 exclude filter 与两侧输出路径；原始数据同目录在档）。

## 六、CI 接线裁定（roadmap item 9，2026-09-29）

- **裁定：暂缓 CI 接线**（report-only CLI 保留为本地/按需消费面，plan 04 交付不变；仓库 CI 文件零改动）。
- 理由：item 8 噪音数据 = 千条级 findings/模块且主导 FP 面为字段/静态保守 MAYNULL 推定（无构造器非空推导）——report-only 千条级噪音无消费价值，接入即成本。
- 机制面证据：dual-run 数据（SpotBugs 26 条异缺陷面/通道 2791 条全互补）+ 分层抽检 21 条（TP12/FP9，FP 全部已声明保守面）——本节不新增证据，引用 item 8。
- 触发条件：字段/静态非空推导（FP 收敛主手）落地 + 重跑双跑抽检 FP 率降至 ≤20% 或绝对量级降至百条级 → 另立接线 plan（report-only 形态）。
- 重估触发：上述触发达成；或 item 8 语料扩展发现资源面（资源分析器接入评估）。
- HC6 语义：本裁定为"不接线"，与"升 hard gate"无关；未来接线后升 hard gate 仍须独立 plan + 对照期误报数据背书。
