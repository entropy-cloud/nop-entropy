# EQL WI3 Phase 0 裁定

**日期**：2026-10-04
**范围**：`nop-persistence/nop-dao`（dialect XML / retention 模型 / accessor）、`nop-kernel/nop-xdefs`（dialect.xdef）、`nop-persistence/nop-orm-eql`（生成器空值门）
**状态**：active
**来源**：`ai-dev/plans/20-eql-wi3-dialect-config-fixes.md` Phase 0（11 项方言配置纠错）；上游裁决 `2026-10-03-wi0-gate-adjudications.md`（G-1/G-4）
**授权**：G-4 已书面批准 11 项逐项纠错（`2026-10-03-wi0-gate-adjudications.md` 第五节）

---

## 一、设计结论

1. **`INTERSECT`/`EXCEPT` 版本门 = 形态③（补 `intersectKeyword` xdef 属性 + retention 手写 + 生成器空值门），但「显式不支持」的表达值由空串修正为显式哨兵 `none`**——Phase 0 必答项实测否决了空串保真假设（见 §二）。
2. **第 7 项（mysql5.7 窗口 mixin 移除）= 候选 B（内联 `x:override="remove"`）**，配动态守卫测试拒绝泄漏（四维对比见 §三）。
3. **第 12 项（db2 selector 断链）= 补 mandatory 子节点**（`<driverClassName>` + `<jdbcUrlPattern>`，形态照 dm），不下掉 selector（JCC 坐标见 §四）。
4. **mariadb `RETURNING` 门 = 显式 `supportReturningForUpdate="false"`**（与 mysql 翻转解耦、独立成声明），与 INTERSECT 门同构：「声明式保守值 + 编译期报错 + 部署级 delta 显式覆盖」。
5. WI1 并行状态已核验：**supportLateral / sqlIntervalLiteral / fullJoin 门均已落地**，交集文件锚点已按符号重核（行号漂移登记见 §五）。

## 二、必答项：空串经 xdef string 绑定的保真实测（原始证据）

**问题**：`intersectKeyword=""` 空串经 xdef `string` 属性绑定后，`DialectSqls` 模型上是保真 `""` 还是被规范化为 `null`？

**方法**：最小 fixture 方言 XML（extends h2，`<sqls exceptKeyword="">`）经 `DialectManager`（`ResourceComponentManager.loadComponentModel` 全链路，与生产同通路）加载，读模型原始值。用已声明的 `exceptKeyword` 属性实测——与拟新增的 `intersectKeyword` 同一 `<sqls>` 元素、同一 xdef `string` 类型、同一绑定机制，证据可迁移（`intersectKeyword` 现未声明，无法先行绑定，见下第二组证据）。

**原始输出证据**（临时测试 stdout 摘录，2026-10-04 实测）：

```text
[PHASE0-EVIDENCE] fixture exceptKeyword (xml "") repr = null          ← 空串不保真
[PHASE0-EVIDENCE] fixture escapeSlash (merge-inherited from h2) = false ← 属性级 merge 继承正常
[PHASE0-EVIDENCE] h2 exceptKeyword (unset) repr = null
[PHASE0-EVIDENCE] oracle exceptKeyword (xml "minus") repr = non-null (length=5 value=[minus])
[PHASE0-EVIDENCE] undeclared attr load threw: NopException
    errorCode=nop.err.xlang.xdsl.attr-not-allowed
    params={attrName=intersectKeyword, allowedNames=[escapeSlash, exceptKeyword, insertKeyword,
            updateKeyword, trueString, falseString]}                       ← 未声明属性响亮报错
```

**结论（两条）**：

1. **空串不保真**：`exceptKeyword=""` 在模型上为 `null`，与「未配置」不可区分。plan 原契约 (b)（空串 = 显式不支持）**无法表达**，按 plan 预案切换为**显式哨兵值**。
2. **补 xdef 属性是必需前提**：未声明属性在加载期响亮报错（`nop.err.xlang.xdsl.attr-not-allowed`），不存在「不声明也能配」的旁路；形态③的第一步（xdef 声明）不可省略。

**哨兵值裁定：`none`**

- 值面契约（替代 plan 原契约 a/b/c）：
  - (a) **未配置**（模型 null）⇒ 行为与今天逐字节一致（生成器回落基类默认 `intersect`/`except`），零 BC 破坏；
  - (b) **显式配置 `none`** ⇒ 该方言声明「无该集合算子」⇒ 编译含 `INTERSECT[ ALL]`/`EXCEPT[ ALL]` 的查询时抛 `nop.err.eql.dialect-not-support-feature`（复用既有错误码），`ARG_FEATURE` = 对应配置名（`intersectKeyword`/`exceptKeyword`）；
  - (c) **显式配置其他非空值**（如 oracle 的 `exceptKeyword="minus"`）⇒ 原样用作关键字。
- 选 `none` 的理由：非任何真实方言的集合算子拼写（现有真实值仅 `intersect`/`minus`），小写与关键字风格一致，`intersectKeyword="none"` 自文档化；空串语义（「无」）由它继承，xdef javadoc 与 owner doc 同步写明。
- EXCEPT 走**同一条哨兵门规则**、复用既有 `exceptKeyword` 属性，无需新属性。
- `mysql` 不配置（G-1 floor 8.0.31 已含 INTERSECT/EXCEPT）；`mysql5.7` 与 `mariadb` 显式配置 `intersectKeyword="none" exceptKeyword="none"`（mariadb floor=10.2，G-1 承诺下限上确实缺该算子）。
- 矩阵快照保持 18（无新方言文件）。

**升级路径（部署级 delta 覆盖，≥10.3/≥13.0 的部署）**：部署方在自有 delta 方言 XML 中显式配置 `intersectKeyword="intersect"`（或删除哨兵配置）与 `supportReturningForUpdate="true"`——「声明式保守值 + 编译期报错 + delta 显式覆盖」与 G-1「编译期报错为默认」一致。该文案 Phase 7 落 owner doc。

**mariadb `RETURNING` 门形态与之一致**：`mariadb.dialect.xml` features 行显式 `supportReturningForUpdate="false"`（现有行 `<features supportSequence="true" supportLateral="false" supportFullJoin="false"/>` 增加该属性），既有校验点（`EqlTransformVisitor.visitSqlUpdate` 对 `update … returning` 抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`）即编译期报错；≥MariaDB 13.0 经 delta 显式开启。

## 三、第 7 项机制裁定：候选 B（内联 remove），候选 A 被拒

| 维度 | 候选 A（新中间方言 `mysql-base`） | 候选 B（`mysql5.7` 内联 10 个 `x:override="remove"`） |
|---|---|---|
| ① 快照算术 | 18→19（新方言文件必须登记；mandatory 子节点随内容移入 base ⇒ base 可加载） | **保持 18**，快照零 diff |
| ② 可加载方言数 | 14→15（`mysql-base` 携带 mandatory 子节点才能让 mysql5.7 加载成功） | **不变（14）** |
| ③ 回滚面（G-4 回滚单位） | 3 文件（新增 base + `mysql.dialect.xml` extends 改动 + `mysql5.7.dialect.xml` extends 改动），且 mysql 现有非窗口内容（reservedKeywords ~110 行、errorCodes、sqlDataTypes、driver 信息）整体搬迁，抄写风险大 | **1 文件**（`mysql5.7.dialect.xml` 单文件加 `<functions>` remove 块） |
| ④ mixin 未来新增函数是否泄漏进 mysql5.7 | 结构性隔离，永不泄漏 | 列举式移除，mixin 新增函数会经继承泄漏——**以动态守卫测试显式拒绝**（见下） |

**选择：候选 B**。理由：

1. **仓库 house style 先例**：duckdb 对 geo-support（两跳祖先 mixin）贡献的 25 个 `st_*` 正是用内联 `x:override="remove"` 处理「祖先/mixin 贡献但本方言不要」的同一问题（`duckdb.dialect.xml` functions 块，实测）；`mysql.dialect.xml` 自身 remove `cosh`/`sinh`、postgresql remove `uuid` 同理。机制可行性已被现网证明。
2. **最小回滚面与快照稳定性**：与 roadmap 拒绝 INTERSECT 门形态①（拆方言 18→19、selector 连锁）的同一成本逻辑一致；G-4 回滚单位 = 单文件 `git revert`。
3. **④ 维泄漏风险不静默接受而是机制化拒绝**：守卫测试在运行时 live 解析 `window-expr-support.dialect.xml` 的 `<functions>` 声明集，断言 mysql5.7 加载后的函数表与该集合交集为空（同时保留 10 个函数名的显式断言作为可读载体）。未来任何人向 mixin 新增函数，该测试立即转红，强迫显式补 remove 或重新裁定——把候选 B 唯一的弱点变成测试保护的不变式，兼取 A 的隔离语义。
4. 候选 A 的额外代价（mysql 内容整体搬迁 + 18→19 + 14→15）在本问题规模（10 个函数、mixin 单文件、演进频率低）下不成比例。

## 四、第 12 项修法裁定：补 mandatory 子节点（不下掉 selector）

- **现状（WI0 快照三元组，`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`）**：`db2` 登记 `loadable:false` + `errorCode: nop.err.xlang.xdsl.missing-mandatory-child` + `missingMandatoryChild: driverClassName`；`db2.selector.yaml` 已注册（`productName: DB2 → dialectName: db2`）⇒ DB2 自动选择命中后实例化失败。
- **修法**：给 `db2.dialect.xml` 补 `<driverClassName>` 与 `<jdbcUrlPattern>`（`dbProductNames` 已有），形态照 `dm.dialect.xml` 的 mandatory 三件套。db2 现有 `errorCodes` 表（`-803` 等，`DialectSQLExceptionTranslator` 消费）不动。
- **JCC 驱动坐标候选**（已对 IBM 官方文档核对，2026-10-04）：
  - `driverClassName` = `com.ibm.db2.jcc.DB2Driver`（IBM Data Server Driver for JDBC/SQLJ，Type 4 纯 Java）
  - `jdbcUrlPattern` = `jdbc:db2://{host}:{port}/{db}`（默认端口 50000；占位符形态与 dm/mysql 的 `{host}:{port}/{db}` 惯例一致）
- **拒绝的备选（下掉 `db2.selector.yaml`）**：selector 的存在是产品意图（DB2 自动选择），errorCodes 表完整且被消费；下掉 selector 会把「配置断链」（可修）变成「能力回退」（删功能），且 WI0 快照的 selectors 登记面同步缩水。修复后 db2 转可加载，快照按实测重生成（新增其函数表 = default + window mixin 继承面）；`TestDialectWindowSqlSnapshot` 头部 db2 注释随裁定同步更新。
- **复现纪律**：先落一个「修复前红」的复现测试（引用快照三元组 + selector 命中路径），修复后转绿，红→绿留痕。

## 五、WI1 并行交集核验与锚点复核（执行时点 live 状态）

**WI1 已落地**（supportLateral/sqlIntervalLiteral/BUG-1 ESCAPE 门、WI2 的 fullJoin 门也已落地）。交集文件当期结构（符号定位，行号为 2026-10-04 实测）：

| 符号 | 实测位置 | plan 起草行号 | 状态 |
|---|---|---|---|
| `dialect.xdef` `<sqls>`（`exceptKeyword` 属性所在行） | `:75-76` | `:74` | 漂移 +1（WI1 加 `sqlIntervalLiteral` 元素） |
| `IDialect.getIntersectKeyword()` default | `:168-170` | `:151-153` | 漂移（WI1/WI2 accessor 插入） |
| `IDialect.isSupportReturningForUpdate()` | `:125` | `:108` | 漂移 |
| `DialectImpl.getExceptKeyword()` 覆写 | `:372-375` | `:356-359` | 漂移 |
| `DialectImpl.isSupportReturningForUpdate()` | `:342-345` | `:327-328` | 漂移 |
| `AstToSqlGenerator.getIntercectKeyword()` | `:567-574` | `:501-506` | 漂移（WI1 lateral/ESCAPE 门插入） |
| `AstToSqlGenerator.getExceptKeyword()` | `:576-582` | `:509-515` | 漂移 |
| `AstToSqlGenerator.visitSqlLikeExpr`（ilike 消费点/`ERR_EQL_NOT_SUPPORT_ILIKE`） | `:528-565`（`:563`） | `:478-497` | 漂移 |
| `AstToEqlGenerator.visitSqlUpdate`（returning 输出） | `:335-355` | `:346-352` | 漂移 |
| `AstToEqlGenerator.visitSqlUnionSelect` | `:542` | `:506-532` | 漂移 |
| `AstToEqlGenerator.visitSqlOrderByItem`（nullsFirst 打印） | `:618`（`:625-626`） | `:582-594` | 漂移 |
| `EqlTransformVisitor.visitSqlUpdate`（returning 校验点） | `:1562`（`:1581-1586`） | `:1559-1574` | 基本一致 |
| `OrmEqlConstants.FEATURE_SUPPORT_RETURNING_FOR_UPDATE` | `:25` | `:25` | 一致 |
| `TestDialectWindowSqlSnapshot` db2 注释 | `:37-38` | `:37-38` | 一致 |
| `mysql.dialect.xml` extends / features / functions-remove | `:3` / `:136-137` / `:143-146` | `:3` / `:136-137` / `:144-145` | 一致（WI2 已加 supportFullJoin="false"） |
| `oracle.dialect.xml` features / sqls / ilike 模板 | `:99-100` / `:105` / `:174-176` | `:100` / `:105` / `:174-176` | 一致 |
| `h2.dialect.xml` features / functions | `:17-18` / `:131+` | `:18` / `:131-190` | 一致 |
| `duckdb.dialect.xml` features / functions（25 remove 先例） | `:21` / `:81-129` | `:21` / `:108-129` | 一致 |
| `dm.dialect.xml` mandatory 三件套 | `:11-15` | `:11-15` | 一致 |
| `dialect.xdef` defaultNullsFirst 声明/javadoc | `:15` / `:7` | `:15` / `:7` | 一致 |
| `_DialectModel.java` defaultNullsFirst getter/setter | `:283`/`:291` | `:283/288` | 微漂（零消费方结论不变） |
| DMLStatement 文法（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`）SqlUnionSelect / `unionType_` | `:82` / `:91-92` | `:82` / `:91-92` | 一致 |
| BaseRule 文法（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`）`sqlOrderByItem`（无 NULLS 文法） | `:365-367` | `:365-367` | 一致 |

其他复核结论：

- 消费方面 grep 复核：`dialect.getIntersectKeyword()` 的唯一消费方是 `AstToSqlGenerator.getIntercectKeyword`；`dialect.getExceptKeyword()` 的唯一消费方是 `AstToSqlGenerator.getExceptKeyword` ⇒ `DialectImpl` 层区分 null/显式值只影响生成器门，无其他行为面。
- 三份必测语料（NopJobTask / nop-auth-service test / nop-orm test）grep `returning|intersect|except` 零命中 ⇒ 配置值翻转不触及语料回显。
- `default.dialect.xml:22 supportILike="false"`、`h2.dialect.xml:18 supportILike="true"` 现值与 plan 一致；`mariadb.dialect.xml` 的 reservedKeywords 含 `EXCEPT`/`INTERSECT`、features 行已带 `supportLateral="false" supportFullJoin="false"`（WI1/WI2 产物，本 WI 只加 `supportReturningForUpdate="false"` 与 sqls 哨兵，不动 reservedKeywords/窗口 mixin）。

## 六、与已有设计的关系

- 上游：`2026-10-03-wi0-gate-adjudications.md`（G-1 承诺基线 / G-4 逐项批准——本裁定严格在其批准范围内，未扩项）；`2026-10-03-wi1-class-a-adjudications.md` §5（ESCAPE 门先例：消费点显式拒绝而非静默丢弃）；`2026-10-03-wi2-class-b-adjudications.md` §三/§五（fullJoin 门挂载点与授权模式先例）。
- 下游：owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）Phase 7 落版本门契约与 delta 升级路径；WI7 承接 `defaultNullsFirst`/`supportNullsFirst` 消费点（移交链见 plan 20 专节）。
