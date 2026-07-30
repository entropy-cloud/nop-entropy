# 跨模块数据依赖审计提示

在审计多模块应用的跨工程数据依赖合理性、DAG 合规性、外部实体引用一致性时使用此提示。

在跨模块 ORM 关联建立后（机制 B notGenCode 落地）、模块拆分调整后、或 codegen 前最终核对时使用。不要将其用作单模块内部审计、需求综合或计划审计的替代品。

```text
您是高级架构师。以下是项目所有业务域的 `<domain>/model/*.orm.xml` 与数据依赖矩阵文档。审计跨模块数据依赖的合理性、DAG 合规性、外部实体引用完整性。

首先阅读这些文件：
- `AGENTS.md`
- 项目的模块边界文档（模块依赖方向 + 跨工程实体关系规则）
- 项目的数据依赖矩阵文档（R/S/P 三类依赖矩阵 + 外部实体引用清单）
- `docs-for-ai/02-core-guides/cross-module-entity-reference.md`（四种机制 A/B/C/D）
- `nop/schema/orm/entity.xdef`（`@notGenCode` 权威定义，位于 `_vfs/` 下）
- 所有模块的 `model/*.orm.xml`

审计维度：

1. DAG 合规性
   - 跨模块 refEntityName 引用方向单向，无循环。
   - 用脚本构建依赖图，拓扑排序检测循环。

2. 外部实体声明完整性（机制 B）
   - 每个生效的跨模块 `<to-one refEntityName="X.dao.entity.Y">` 都有对应 `<entity notGenCode="true">` 声明。
   - 声明的列只列关键列，不全量复制（运行时由被引用模块的 Entity 类提供完整列）。
   - 外部实体声明的 `name`（实体全限定名）与 `tableName` 与被引用模块一致。

3. 跨模块引用范式选择合理性
   - 高频多维关联查询 → 应用机制 B（notGenCode 外部实体 + to-one）。
   - 列表显示名 → 用冗余显示名字段（L1）。
   - 详情展开 → 用 @BizLoader + requireBiz（L3）。
   - 报表复杂查询 → 用 EQL 子查询（IN）。
   - 业务表之间反查源单 → 用弱指针字符串三元组（机制 P），不建 to-one。

4. 冗余字段策略
   - 高频列表显示场景应冗余显示名字段，与 to-one 并存。
   - 冗余字段需有维护机制（主数据改名时刷新，或 @BizLoader 实时带出）。

5. Maven 依赖与 orm 声明对齐
   - 引用方工程的 `pom.xml` 应依赖被引用方的 `-dao` 包（codegen 后核对）。
   - 本模块的 orm.xml 不重复生成外部模块的 Entity 类（靠 notGenCode="true" 跳过）。

6. 与数据依赖矩阵一致性
   - 矩阵中声明的依赖方向与 orm.xml 实际 refEntityName 一致。
   - 矩阵的 R/S/P 分类与实际引用方式一致（R=只读外键、S=同事务写、P=弱指针反查）。

自动化核查脚本建议：
- 扫描所有 orm.xml 的 refEntityName，按模块名提取跨模块引用边。
- 对照依赖方向验证方向合法性。
- 拓扑排序检测循环。
- 统计每模块"生效 to-one 数 / 注释残留数 / 外部实体声明数"，验证"引用 ≤ 声明"完整覆盖。

严重性指南：
- `blocker`：DAG 循环、refEntityName 无对应声明（codegen 必失败）。
- `major`：高频关联查询场景未用机制 B（性能差）、缺关键外部实体声明、弱指针与 to-one 范式混用。
- `minor`：冗余声明、命名小问题。

按严重性返回发现，附：引用边清单、DAG 验证结果（✅/❌）、外部实体声明完整性矩阵。最后给：
- 裁决：通过/失败
- 跨模块引用边总数、DAG 合规边数、循环数
- 各模块外部实体声明完整覆盖率
- 残留风险（如 Maven pom 未对齐——codegen 阶段处理）
```
