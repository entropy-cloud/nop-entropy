# ORM 模型审计提示

在将模块 `<domain>/model/*.orm.xml` 作为持久化模型真相源进行规范与完整性审计时使用此提示。

在 ORM 模型首次建立后、跨模块引用变更后、字段批量补齐后或 codegen 前最终核对时使用。不要将其用作需求综合、设计文档审计或计划审计的替代品。

```text
您是高级数据建模师和 Nop Platform 专家。以下是项目所有 `<domain>/model/*.orm.xml` 文件。按维度审计其规范合规性与业务字段完整性。

首先阅读这些文件：
- `AGENTS.md`
- `docs-for-ai/02-core-guides/orm-model-design.md`（平台 ORM 规范）
- `nop/schema/orm/entity.xdef`（属性权威定义，位于 `_vfs/` 下）
- 平台参考实现：`nop-auth/model/nop-auth.orm.xml`
- 所有模块的 `model/*.orm.xml`

审计维度：

1. 类型规范
   - 每列显式 `code`（UPPER_SNAKE_CASE）+ `propId`（从 1 连续）+ `stdSqlType`（仅 StdSqlType 枚举值）+ `stdDataType`。
   - StdSqlType 与 stdDataType 配套：BOOLEAN↔boolean、INTEGER↔int、BIGINT↔long、DECIMAL↔decimal、VARCHAR↔string、DATE↔date、TIMESTAMP↔timestamp。
   - 残留废弃用法：`stdSqlType="INT"`（应 INTEGER）、`dictName=`（应 ext:dict=）。

2. 长度与精度
   - VARCHAR 字段带 `precision`；金额类 DECIMAL 用 `precision="20" scale="4"`（本位币）/`scale="8"`（汇率）。
   - 主键 BIGINT 不带 precision。
   - 文本类长字段用 CLOB 而非超长 VARCHAR。

3. 字典设计
   - `<dict name="域简称/kebab-name">` 格式。
   - option `code` UPPER_SNAKE、`value` 10/20/30 递增、`label` 中文。
   - 跨域不重复定义同名字典；通用状态应复用平台已有的字典。
   - 每个字段引用的 `ext:dict=` 都有对应 `<dict>` 定义。

4. 标准字段完整性（每个实体）
   - 必有：`id`（BIGINT primary + stdDataType=long + tagSet="seq-default"）、`delVersion`、`version`、`createdBy`、`createTime`、`updatedBy`、`updateTime`、`remark`。
   - 实体配 `useLogicalDelete="true" deleteFlagProp="delVersion"`。
   - 审计四件套用 domain 复用，不重复定义列。

5. 关系设计
   - 本模块关系用 `<to-one>` + `<join><on leftProp="..." rightProp="id"/></join>` + `tagSet="pub"`。
   - 跨模块引用：要么机制 B（`<entity notGenCode="true">` 外部实体引用 + to-one），要么机制 D（纯外键列 + I*Biz）。不允许"无声明的跨模块 refEntityName"。
   - 头-行关系用 `tagSet="pub,cascade-delete,insertable,updatable"`。

6. 跨模块引用一致性（机制 B 落地）
   - 每个 `<to-one refEntityName="Xxx.dao.entity.Yyy">` 的 Yyy 在本模块 orm.xml 有对应 `<entity notGenCode="true">` 声明（防 codegen 找不到 refEntityName）。
   - 外部实体声明的列只列本模块会用到的关键列（不全量复制）。
   - DAG 合规：跨模块引用单向，无循环（见 cross-module-dependency-audit-prompt）。

7. 命名一致性
   - 实体 className 与 orm 文件包名一致。
   - tableName 与域前缀一致。
   - 列 name camelCase、code UPPER_SNAKE_CASE。

严重性指南：
- `blocker`：类型非法、关系断裂（无声明）、标准字段缺失影响业财一体、DAG 循环。
- `major`：字典规范违规、跨域命名冲突。
- `minor`：冗余声明、列顺序优化、注释缺失。

按严重性返回发现，附：受影响文件与行、问题、修复建议。最后给：
- 裁决：通过/失败
- 各维度通过率
- 残留风险
```
