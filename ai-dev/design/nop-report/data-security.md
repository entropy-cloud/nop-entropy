# 报表数据级安全（行级过滤 + 列级脱敏） 设计

**范围**：`nop-report/nop-report-core`（SPI、行条件包装、展示层脱敏挂点、xpt-rt 标签）、`nop-report/nop-report-service`（规则提供者）
**状态**：active（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 数据级安全 = **行级过滤**（取数前 SQL 子查询包装）+ **列级脱敏**（展示层掩码），与 WI1 授权（能否取数）正交：授权先于取数，行过滤决定取多少行，脱敏决定展示什么值。
2. core 定义 SPI `IReportDataSecurityProvider`（bean `nopReportDataSecurityProvider`，`tryGetBean` 可选查找；未注册 = 引擎作嵌入式库使用，不启用数据级安全）。
3. 规则声明式：VFS 配置 `/nop/report/data-security.yaml`，按 resource lastModified 缓存（首用加载，变更后下次检查重读；classpath 打包资源的 lastModified 恒定，测试经 delta 目录覆盖验证）。
4. 行过滤采用**子查询包装**：`select * from ( 原SQL ) _rpt_sec where <条件>`——不做 where 文本解析；包装保留原 SQL 语句级属性（name/querySpace/timeout/fetchSize/cacheRef/readOnly 等），并裁剪原 SQL 尾部分号（参数 marker 均在裁剪区之前，偏移不变）；条件中的用户上下文值经 `?` 参数绑定，不拼接字面量。

## 二、背景与动机

WI1 回答「能不能取数」；平台还需要「按人/按部门只取该取的行、敏感列只展示掩码」。行/列规则必须对模板作者透明（无需在每个模板里写过滤条件），且不能因模板改用流式标签而绕过。

## 三、核心设计

### SPI 契约

| 方法 | 契约 |
|------|------|
| `getRowCondition(reportPath, dsName)` | 返回行条件（条件 SQL 片段 + `?` 绑定参数）或 null；dsName 为标签内归一化键 `dsName \|\| 'ds1'`，`JdbcDataSet` 流式标签（无 dsName attr）固定按默认键 |
| `maskValue(dsName, field, value)` | 返回脱敏值或原值；**dsName=null（`=field`/层级展开取值路径）仅匹配无数据集约束的字段级规则**——保守侧，防漏脱敏 |

### 挂接点

- 行过滤：`xpt-rt.xlib` 三个取数标签（`UseJdbcDataSet`/`UseOrmDataSet`/`JdbcDataSet`）在 sql 构造后调 `xptRt.applyRowCondition(sql, dsName)`——流式标签同样接线，无绕过路径。
- 脱敏：`ExpandedSheetEvaluator.evaluateCell` 的三条字段取值路径（setValue 前）——单点对所有渲染器（xlsx/html/pdf）生效。

### 规则 schema（VFS `/nop/report/data-security.yaml`）

```yaml
rules:                       # 行级过滤
  - reportPathPrefix: /report/sec/   # 可选，报表路径前缀
    dsName: ds1                      # 可选，缺省匹配所有数据集
    rowCondition: "dept_id = {deptId}"   # {userId}/{tenantId}/{deptId} -> ? 参数
masks:                       # 列级脱敏
  - dsName: ds1              # 可选；缺省=字段级规则（dsName=null 路径仅匹配此类）
    field: phone
    type: phone              # phone / name / idCard / full
```

- 行规则命中但无用户上下文且条件引用占位符 → fail-closed 抛 `nop.err.report.data-sec.no-user-context`（调度等系统调用方须显式建立用户上下文）。
- 条件列名可用 `_rpt_sec.` 前缀限定（派生表外层引用，规避原 SQL 列名歧义）。

### 聚合与脱敏的约束

脱敏发生在展示层（单元格取值时），数据集本身不被修改；但**被公式（SUM 等）引用的字段不得配置 mask**——公式对掩码文本求值行为未定义（xlsx 导出公式在 Excel 端还会重算）。不做静态检测，属模板作者约束（owner doc 同步声明）。

### 裁定

- `UseQueryDataSet`（query bean 路径）不做行过滤包装：走平台 ORM 层，可复用既有 `IDataAuthChecker` 数据权限体系；如需报表内联行规则由后续 WI 增强。
- yaml 打包资源的 lastModified 不变化，生产热更新经 VFS delta 目录覆盖实现；管理界面归产品化后续。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| where 文本解析追加条件 | SQL 文本结构解析脆弱（嵌套/注释/分号）；子查询包装方言安全（H2/MySQL 均要求派生表别名，`_rpt_sec` 合法） |
| 数据集构造时脱敏 | 掩码值进入 SUM/分组等聚合运算破坏正确性；展示层脱敏保持聚合语义 |
| ORM 实体承载规则 | ORM 模型改动属 protected area 且规则本质是部署配置；VFS yaml 与平台既有配置先例一致 |
| 条件拼接字面量 | 上下文值含引号即注入；`?` 参数绑定是唯一安全路径 |

## 五、与已有设计的关系

- `auth-wiring.md`：授权（能否取数）先于行过滤（取多少行）生效；数据源授权（querySpace）独立于行规则。
- `parameter-model.md`：参数解析在展开前注入 scope；行条件包装在取数标签执行时，二者互不影响。
