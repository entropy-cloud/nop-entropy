# 05: Overclaimed Closure — fix-status 与 live repo 漂移

> Date: 2026-07-31
> Severity: High — nop-ai 审计-修复闭环（MR1-MR4/MV）中 3 次出现"声称已修复/已落地，live repo 中不存在对应证据"

## 场景

nop-ai 审计-修复闭环中，MR 计划文本与 arm-index 修复状态表声称的落地情况与 live repo 不一致，先后出现 3 例：

1. **MR2 声称 MA4.3 P1 已展开进 arm-index**（Exit Criteria 勾选），但 live `arm-index.md` 的 P1 汇总表中不存在任何 MA4.3 行 — MR4 才补入 6 条 MA4.3 P1 行。
2. **MR1 声称 `_dao.beans.xml` 已添加解释性注释**，但 `git log` 显示该文件自初始提交从未被修改 — 该文件是 codegen 生成物（`_dao.beans.xml.xgen` 每次构建重新生成），注释根本不可能持久化。
3. **MR3 声称 `DefaultAiChatExchangePersister` 已加可选 AES 加密**，但 `git log` 显示 MR3 无任何提交触及该文件，live 代码纯明文落盘 — MR4 才真正实现加密。

另：MV closure audit 发现 P1-MA5-003 行声称"生产实现存在（ChatOptionsHelper/TokenEstimators）"，实际 `TokenEstimators.defaultEstimator()` 返回的 `CalibratedTokenEstimator implements ITokenEstimator`（agent 层接口），三个 core SPI 接口（`IVectorStore`/`IEmbeddingModel`/`ITokenCountEstimator`）自初始提交起无生产实现 — 裁定为 SPI 扩展点契约。

## 根因

1. **以计划文本/自查结论代替 live 证据**：勾选 Exit Criteria 时只确认"我做过这件事"的意图，没有回到 live repo 用 `git log`、文件内容、测试文件确认落地。
2. **修复状态表是文档不是事实源**：`arm-index.md` 的 `fixed` 行在修复时更新，但 live repo 才是唯一事实源；两者漂移后没有任何自动校验。
3. **对生成文件/生成管线认知不足**：`_dao.beans.xml` 属 `/nop/templates/orm` 的 xgen 生成产物，手改必被覆盖 — 在生成文件上做修复本身就是错误位置。

## 正确做法

1. **claim 与证据成对出现**：任何 `fixed`/`done` 声明必须附带可核查证据 — `git log --oneline -- <file>`、测试文件路径+方法名、代码路径行号。证据不可写"已核实"，要写"在哪里核实"。
2. **closure audit 必须重跑证据，不信任前置声明**：MV/MR4 的做法（逐行 `git log` 核验 + 独立子 agent 复核）应固化为标准流程。
3. **涉及生成文件的问题先判断生成管线归属**：`_` 前缀文件全部由 codegen 产出，修复必须上移到源模型（`model/*.orm.xml`）/生成模板/Delta 层，不能直接改生成物。
4. **overclaim 一旦发现即纠正记录**：在 arm-index 或 plan 中显式记录"声称 vs 实际"差异及裁定，避免后续基于错误状态继续工作。

## 判定规则

> **"声称已修复"不是证据，"能找到修复产物"才是。** 判定 fixed 的唯一标准：live repo 中可定位到对应代码路径、测试文件或裁定记录（含 commit hash 或文件行号）。
>
> 若一条 finding 的修复声明在 live repo 中无法定位到任何产物，按 overclaim 处理：立即修复或裁定，并在追踪表记录纠正。

## 适用范围

- 所有 MR/修复计划的 Exit Criteria 勾选
- 审计-修复追踪表（arm-index 类）的状态更新
- 计划 closure audit 的证据复现

## 参考

- `ai-dev/audits/arm-index.md` §MR4 P1 表逐行核验（3 例 overclaim 的完整记录）
- `ai-dev/plans/2026-07-31-1024-1-arm-mr4-adjudication.md`（MR2/MR3 overclaim 纠正）
- `ai-dev/plans/2026-07-31-1024-2-arm-mv-validation.md`（P1-MA5-003 纠正 + SPI 裁定）
