# 大数据集渲染防线与性能基线 设计

**范围**：`nop-report/nop-report-core`（行数上限防线、迭代导出）、`nop-report/nop-report-service`（游标分页 e2e 口径）
**状态**：active（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 全局展开行数上限：`nop.report.max-expand-rows`（默认 100_000，0=不限制），计数检查在 `ExpandedTable.insertEmptyRow`（行增长唯一 choke point；仅计新增插入行，`expandInplaceCount` 复用行不计）。超限 fail-fast 抛 `nop.err.xpt.max-expand-rows-exceeded` + warn 日志——**截断不可接受**（静默少行比失败更危险）。模板级 `expandMaxCount` 语义不变，两道防线并存。
2. 大数据集导出走**迭代式链路**：游标分页（SQL limit/offset）→ `ExcelSheetData` 迭代 → `getRendererForExcelData` → xlsx 落盘；粒度语义为**每页=一个 xlsx sheet**（sheet 内部非流式）。懒消费验证口径：**构造 renderer 零消费迭代器 + 全部拉取发生在 generateToFile 执行期间**（`ExcelTemplate.generateToDir` 循环逐 sheet 消费，代码结构保证 + 测试断言；逐 sheet 写出事件未对外暴露，交错时序断言不作要求）。批次数不构成证明——eager 预载同样满足批次数。
3. 性能基线：长表渲染基线测试（宽松时延预算），以系统属性 + `Assumptions.assumeTrue` 控制跳过（仓库 surefire 无 excludedGroups 配置，仅 @Tag 不生效）。
4. **完整惰性展开（expand-on-write）裁定为不做**：展开语义依赖全表坐标（SUM 公式/父子层级/合并单元格），惰性化是架构级重构；防线 + 分页迭代导出已覆盖大数据集主场景。残留 watch-only。

## 二、背景与动机

恶意/失误模板（展开表达式返回超大集合）可致渲染 OOM；模板级 expandMaxCount 需作者逐格声明，平台必须有无默认的全局防线。大数据集导出在防线内通过分页游标 + 既有迭代导出能力承载，不引入第二渲染架构。

## 三、核心设计

### 防线

- 计数语义：每 `ExpandedTable`（=每 sheet）独立计数；模板初始化基线行同经该检查（远低于上限，无碍）。
- 超限行为：中止渲染、错误消息含上限与配置指引（`nop.report.max-expand-rows` / 模板 `expandMaxCount`）。

### 迭代导出口径

- 消费方（应用 BizModel）负责游标分页；平台提供 `getRendererForExcelData(Iterator, template)` 出口。
- 懒消费验证口径：构造期零消费 + generate 期间拉取（见设计结论 2）。

### 已知限制

- 宽表（列拆分）无性能基线（residual，与 pdf roadmap 列拆分语义耦合）。
- 行数上限为 sheet 级统一值，不支持按 sheet 差异化配置。
- TTL/内存水位背压不做（上限即背压：fail-fast）。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| 超限截断（保留前 N 行继续渲染） | 静默少行比失败更危险（报表数字错误） |
| 展开引擎惰性化（expand-on-write） | 展开语义依赖全表坐标；架构级重构与收益不成比（见设计结论 4） |
| 仅模板级 expandMaxCount | 依赖作者逐格声明，平台无默认防线 |

## 五、与已有设计的关系

- `html-pagination.md`：长表分页渲染按行高装箱，与本防线正交（先过防线再装箱）。
- `result-cache.md`：结果缓存复用渲染产物，防线在渲染前生效。
