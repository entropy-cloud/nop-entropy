# nop-rg 编码与字节保真 Roadmap

> Last updated: 2026-11-20
> Sources: `ai-dev/design/nop-rg/03-encoding-and-byte-fidelity-design.md`（设计决策）、`ai-dev/plans/2575-nop-rg-encoding-byte-fidelity.md`（执行计划）
> Status: **in progress — owner 已于 2026-11-20 指示执行（plan 2575）**

## Purpose

消除非 UTF-8 文件上的静默失真与静默错配：输出改为字节保真（rg `text`+`bytes` 双字段契约），并提供 `--encoding <charset>` 显式编码支持（模式编码 / regex 解码 / 输出三处一致）。

本 roadmap 只做执行分解；设计准入、验收方式与变异探针要求见设计文档第三节。

## Work Items

> **This is the only dynamic state block. Update status only here.**

### Wave E1: 输出字节保真（默认行为，无开关）

- E1.1 JSON 输出 `text` + `bytes`（base64，仅非法区间）双字段: `done`
- E1.2 非法 UTF-8 不再走 `CharsetDecoder` 回退，改基础扫描 + base64: `done`
- E1.3 合法 UTF-8 输出零变化对拍（JSON 逐字节 diff）: `done`
- ★ **Milestone E1**: `done`（非 UTF-8 输出可精确还原原始字节）

### Wave E2: `--encoding <charset>` 显式编码

- E2.1 `SearchCommand.patternBytes` 按指定编码编码（模式侧）: `todo`
- E2.2 `RegexSearcher` 整文件与候选行按指定 Charset 解码（regex 侧）: `todo`
- E2.3 输出 `text` 按指定编码解码（输出侧）+ `--encoding none` 别名: `todo`
- E2.4 非法 charset 值 exit 3 显式报错: `todo`
- ★ **Milestone E2**: `todo`（GBK 语料全链路正确）

### Wave E3: 验收与保护

- E3.1 GBK / Latin-1 / Shift-JIS 语料，与系统 rg `--json` 逐字段对拍: `todo`
- E3.2 变异探针（只改模式不改 regex / 只改输出不改模式 → 用例必红）: `todo`
- E3.3 性能确认（合法 UTF-8 无退化；非法场景较现状提升）: `todo`

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | 未开始（本 roadmap 当前全部处于此状态） |
| `planned` | 有执行计划且通过评审 |
| `in progress` | 正在执行 |
| `done` | 完成并通过 closure audit |

## Baseline（2026-11-20 代码核实）

| 事实 | 位置 |
| --- | --- |
| literal / 行定位 / count 已在字节域，无解码 | `SearchCommand.patternBytes`、`ByteScan`、`MatchAggregator.advanceLineStart` |
| 命中行文本 lossy 解码（`U+FFFD` 静默替换） | `LineCursor.text`、`MatchAggregator.decode` |
| regex 整文件 / 候选行假定 UTF-8 | `RegexSearcher.decode` / `processCandidateLine` |
| JSON 输出仅 `text`，无 `bytes` 字段 | `JsonOutput.writeMatch` |
| CLI 无 `--encoding` 参数 | `NopRgMain` 参数解析 |
| 二进制判定：头部 8KB 找 NUL | `SearchCoordinator.isBinary`（`BINARY_SNIFF_BYTES=8192`） |

## 明确不做

- 不做自动编码探测（charset 自动识别）——不可靠，rg 亦不做。
- 不引入"假定 UTF-8"性能开关——合法 UTF-8 下零收益，非法场景是正确性缺陷而非调优项（设计文档决策 1）。
- 不改变二进制判定策略（设计文档决策 4）。

## Rules

- ~~本 roadmap 当前**不进入执行队列**（owner 指示：先写文档，暂不实施）~~；owner 已于 2026-11-20 显式指示执行，锁定解除（plan 2575）。
- 三处一致性（模式编码 / regex 解码 / 输出编码）是硬约束：任一处遗漏即静默错配，验收以变异探针强制。
- 状态只在上方 Work Items 块更新；阶段间必须完成对拍验收再推进。
