# 11: 审计计数口径勘误 — grep 统计把容器标签计入元素，69 ≠ 36

> Date: 2026-08-05
> Severity: Medium — nop-metadata MA7.3-01 计数勘误：`rg -c '<unique-key'` 统计 69 个，实际 UK 元素只有 36 个；差 33 个全是 `<unique-keys>` 容器标签

## 场景

nop-metadata 审计 MA7.3 报告"全量 69 个 UK 零物化"，计划据此准备修复。R3.0 展开器核对发现计数勘误：

```bash
# MA7.3 报告口径
rg -c '<unique-key' nop-metadata/model/nop-metadata.orm.xml   # 69 ← 含 33 个 <unique-keys> 容器标签

# 实际口径
grep -o '<unique-key name=' nop-metadata/model/nop-metadata.orm.xml | wc -l   # 36 ← 真实 UK 元素
```

`<unique-keys>`（容器标签）与 `<unique-key>`（元素标签）都匹配 `rg '<unique-key'`，69 = 36 元素 + 33 容器。若直接按 69 修复会多改 33 处不存在的元素。

## 根因

1. **子串匹配没有锚定完整标签**：`rg '<unique-key'` 匹配了 `<unique-keys>` 和 `<unique-key` 两类；应使用精确模式（`<unique-key name=`）或属性感知解析。
2. **审计报告直接引用原始计数**：MA7.3 未对计数做二次验证，把含杂质的 grep 输出当事实写进报告；计划按错误计数展开。
3. **同类问题无防错机制**：验证阶段（MV V.1 surefire 汇总）同样出现过 `TestNopMetaDictI18n` 文件名含数字被误计入测试数的假象——`rg`/`wc` 类统计命令的输出需要口径复核（如 `--no-filename`、精确模式）。

## 正确做法

1. **计数先定口径**：统计元素个数时用锚定模式（`<unique-key name=`、`<column code=`），并把容器标签（`<unique-keys>`）与元素标签（`<unique-key>`）区分开；报告中注明 grep 模式。
2. **计数交叉验证**：两个独立口径（grep 模式 A + 模式 B，或 grep + 解析器）结果一致才算成立；对报告中的关键计数（如"36 个 UK"）在修复前用 live 复核。
3. **文件名/类名数字防污染**：汇总测试数用 `--no-filename` 或解析 surefire XML，避免类名中的数字被 `wc -l`/`rg -c` 计入（`TestNopMetaDictI18n` 案例）。

## 判定规则

> **凡是报告中出现的"数量"结论，必须能复现 grep/命令 + 说明口径**：什么模式、排除了什么、容器标签是否计入。复现不出相同数字的计数，不能作为修复依据。
>
> 计数用于驱动修复时（如"36 个 UK 补 constraint"），以修复执行的 live 口径为准，报告计数只作线索。

## 适用范围

- 审计报告中带数字的发现（UK 数、文件数、测试数、类数）
- `rg`/`grep`/`wc`/surefire 汇总等统计命令的输出使用
- 计划按审计计数展开修复工作项的环节

## 参考

- `nop-metadata/model/nop-metadata.orm.xml`（36 个 unique-key 元素）
- 审计：`ai-dev/audits/2026-08-05-0856-arm-MA7.3-nop-metadata-import-sync.md`（MA7.3-01 计数勘误）
- 修复：roadmap R3.19 展开器裁决（69→36 口径更正）；MV V.1 计数复核（--no-filename 防 I18n 文件名污染）
