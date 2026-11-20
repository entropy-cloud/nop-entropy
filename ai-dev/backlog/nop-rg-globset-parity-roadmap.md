# nop-rg Glob 性能对齐 globset Roadmap

> Last updated: 2026-11-20
> Sources: `ai-dev/design/nop-rg/02-globset-parity-design.md`（设计决策）、`~/sources/globset-src/crates/globset/src/`（参照实现）

## Purpose

把 nop-rg 的 Glob 匹配从当前实测水平（singleGlob ≈23.3k 次/s、globSet ≈1.65k 次/s）提升到消除**可消除的**数量级差距：先消除热路径分配（阶段一），再扩展分类覆盖（阶段二），再做小额优化（阶段三）。语义不得变更（rg 兼容契约 + 差分对拍守护）。

设计准入、瓶颈证据、不移植清单见设计文档，本文件只做执行分解与状态跟踪。

## Work Items

> **This is the only dynamic state block. Update status only here.**

### Wave G1: 分配消除（零语义风险）

- G1.1 Candidate 式路径上下文（basename 起点 + 段偏移，零 substring 零 split）: `todo`
- G1.2 `segmentMatches(String, from, to)` 区间化，消除中间 String: `todo`
- G1.3 `CompiledGlob` 编译期 `String[]` + `boolean[] isDoubleStar` 元数据: `todo`
- G1.4 基线重测（JMH 前后配对）: `todo`
- ★ **Milestone G1**: `todo`（singleGlob/globSet 均 ≥4x）

### Wave G2: 分类覆盖扩展

- G2.1 `**/REST` 分解为 basename/扩展名策略: `todo`
- G2.2 扩展名集改为候选 ext 单次哈希查找: `todo`
- G2.3 SLOW 模式必需扩展名预滤: `todo`
- G2.4 差分对拍扩面（新分类形态 vs 全 SLOW 参考）+ 分类钉子: `todo`
- ★ **Milestone G2**: `todo`（基准规则集 globSet ≥10x）

### Wave G3: 小额优化

- G3.1 字面量长度位掩码预滤: `todo`
- G3.2 `dir/**` 前缀策略与组件后缀策略评估（含语义可行性）: `todo`
- G3.3 最终重测 + 设计文档数值回填: `todo`

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | 未开始，无执行计划 |
| `planned` | 有执行计划且通过评审 |
| `done` | 完成并通过 closure audit |

## Baseline

2026-11-20 JMH（`GlobBenchmark`，`-wi 2 -i 5`，macOS arm64 / Zulu 26）：

| 基准 | 吞吐 |
| --- | --- |
| `GlobBenchmark.singleGlob` | 23.344 ± 3.188 ops/ms |
| `GlobBenchmark.globSet` | 1.646 ± 0.029 ops/ms |

复核命令（cwd = `nop-tools/nop-rg/nop-rg-benchmark`）：

```bash
java -cp "target/classes:$(cat target/cp.txt)" org.openjdk.jmh.Main ".*GlobBenchmark.*" -wi 2 -i 5 -foe true
```

## Rules

- 每个 `planned` work item 由执行计划 owner；状态只在上方 Work Items 块更新。
- 语义变更一律禁止：所有优化必须与全 SLOW 参考实现逐路径一致（差分 fuzz）。
- 阶段之间必须重测后再定下一步；数据落本文件 Baseline 段与 `ai-dev/logs/`。
- 不移植清单（设计文档第三节）是裁定结果，不得在执行中以"顺便也做了"的名义扩张。
