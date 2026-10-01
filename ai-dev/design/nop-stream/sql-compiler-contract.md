# sql-compiler-contract——D7/D8/D13 三条编译契约裁定落档

> Status: active
> 裁定日期：2026-10-02
> 负责人：仓库 owner（委托链：2026-10-02 执行指令「执行 nop-stream-sql-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」委托 ZCode 代理执行；D8/D13 采纳 roadmap 建议项，D7 的 schema 来源与解析入口在 roadmap 中无建议标注，属受托作出的 owner 级选择，理由见各节）
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI0c 行、前置裁定表 D7/D8/D13 行、§3.5、Cross-Cuting 4）
> 承载 plan: ai-dev/plans/nop-stream-sql/03-wi0c-compiler-contract-decisions.md
> 与 D14 的边界：D14 已裁定 (a) 参数化算子面（产物形态，落档归 sql-landing-decision.md）；本档 D13 定放哪个模块，D7/D8 定输入与入口，三者不互相预设。

## 1. D7 表列绑定来源与解析入口

### 1.1 选项集（五项，roadmap :82 全集）

| 选项 | 裁定 | 理由 |
|---|---|---|
| ORM 实体元数据 | 拒绝为主来源 | 流源不是 ORM 实体；编译器若依赖 ORM 元数据会把 nop-dao 拖进流编译路径（与 D13 (a) 的独立性目标冲突）；双目标场景下 RDBMS 侧执行真表，流侧无实体可言 |
| 连接器 schema | 拒绝 | 引擎当前无连接器 schema 声明面（roadmap §3.5：`<schemas>` build 期 fail-fast，无消费者）；先建连接器体系超出窄范围 |
| `.sql` DDL 文件 | 拒绝 | 引入 DDL 解析与文件管理负担；schema 应内聚在查询声明处 |
| xdef `schemas` 声明面 | 拒绝作编译期主来源，保留为模型级补充 | WI8b 将使其落地为模型级 typed schema（受管类型名→BasicTypeInfo）；编译期列绑定以 SQL 面自带 schema 为主，双来源并存时的优先级细节归 WI8b/WI17 plan 裁定 |
| **SQL 面自带 schema** | **胜出** | 与 D8 `<sql>` 元素天然配对（roadmap 明示该档必须参选）；schema 内聚在查询声明处，构造期经 WI8b 同一解析通路落为 BasicTypeInfo，无新机制 |

### 1.2 解析入口

**`EqlASTParser`**（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTParser.java`，完整 SqlProgram；ORM 外复用先例 `nop-metadata/.../SqlSourceEntityExtractor.java:40`）。`EqlExprASTParser` 仅表达式级场景使用，不作为 SQL 编译器入口。

### 1.3 SQL 类型 → BasicTypeInfo 映射表

受管类型名集合（schema 声明处使用）与 `BasicTypeInfo` 九个内置实例（`BasicTypeInfo.java:20-28` 实测）一一对应：

| 受管类型名 | SQL 对应 | BasicTypeInfo |
|---|---|---|
| string | VARCHAR/CHAR/TEXT | STRING |
| int | INT/INTEGER | INT |
| bigint | BIGINT | LONG |
| smallint | SMALLINT | SHORT |
| tinyint | TINYINT | BYTE |
| float | FLOAT/REAL | FLOAT |
| double | DOUBLE | DOUBLE |
| boolean | BOOLEAN | BOOLEAN |
| bytes | VARBINARY/BLOB | BYTE_ARRAY |

集合外类型（DECIMAL/NUMERIC、DATE、TIMESTAMP 等）**编译期显式报错**（错误码归 WI16 错误码表），不允许静默近似（如 DECIMAL→DOUBLE），进不支持清单。

### 1.4 时间列绑定立场

- 受管集合**不含** timestamp/date 类型（BasicTypeInfo 无时间类型实例，不引入新 TypeInfo）。
- **事件时间不经列类型绑定获得**：`TUMBLE(t, INTERVAL)` 等伪表函数的时间列 t 在 v1 用 **bigint epoch-millis** 受管类型承载；分析窗口 ORDER BY 时间列同理。
- DATE/TIMESTAMP/DECIMAL 列绑定进不支持清单，WI16 定稿复核；WI17 实现前不得擅自放宽（如需放宽须先改本档）。

### 1.5 裁定要素

负责人：仓库 owner（受托 owner 级选择，技术理由如上）；日期 2026-10-02；受影响 WI：WI8b（schemas 消费通路）、WI16（不支持清单与错误码）、WI17（编译器绑定实现）。

## 2. D8 用户可见接口面

### 2.1 选项集（五项，roadmap :83 全集）

| 选项 | 裁定 | 理由 |
|---|---|---|
| **新增 `<sql>` xdef 元素** | **胜出**（roadmap 建议项） | 与 D7 SQL 面自带 schema 配对；Nop 平台声明式模型优先原则（vision §三 #2 模型优先）；可经 Delta 定制（§八 10） |
| `.sql` 文件加 bean | 拒绝 | bean 面需显式 `<bean>` 定义（平台无注解扫描），且 bean 无 SQL 消费者；文件面与模型面割裂 |
| Java API | 拒绝（首版范围外） | 编程入口已有 DataStream API；SQL 用户面的价值在声明式，Java API 归后续按需 |
| CLI | 拒绝 | 无提交/运维场景支撑 |
| GraphQL | 拒绝 | 与 GraphQL 主 API 职能重叠（vision §四 修订行：GraphQL 继续承担主 API 职能） |

### 2.2 结论

`<sql>` xdef 元素：属性含 SQL 文本（内嵌或 .sql 文件引用）与 schema 声明面（D7 §1.3 受管类型名）；**编译落点已由 D14=(a) 裁定（WI8a 落档），本裁定不另设落点**；产出经 WI17 编译为既有 xdef 校验与 builder 消费的模型。首版不含 Java API / CLI / GraphQL 入口。

负责人：仓库 owner（建议项采纳）；日期 2026-10-02；受影响 WI：WI17（编译为该形态）、WI18（入口具名可调用 + E2E）。

## 3. D13 编译器宿主模块与依赖方向

### 3.1 选项集（三项，roadmap :88 全集）

| 选项 | 裁定 | 理由 |
|---|---|---|
| **(a) 新模块 `nop-stream-sql` 同时依赖 `nop-stream-flow` 与 `nop-orm-eql`** | **胜出**（roadmap 建议项） | nop-stream/nop-stream-flow/pom.xml 零 nop-orm-eql 依赖（实测；codegen/ioc 为 test scope，不影响结论）；引擎独立性保住 |
| (b) 给 `nop-stream-flow` 加 `nop-orm-eql` 依赖 | 拒绝 | `nop-orm-eql` 传递依赖 nop-dao + nop-orm-model + nop-core + nop-codegen + nop-antlr4-common，会把持久层拖进流引擎 |
| (c) 复制最小 EQL parser 子集 | 拒绝 | 违反单一口径原则（两套 parser 必然漂移） |

### 3.2 结论与后果（方向 + 机制风险）

**裁定 (a)**：新 Maven 模块 nop-stream/nop-stream-sql，pom 依赖 `nop-stream-flow` + `nop-orm-eql`；`<sql>` 元素 xdef 声明面落新模块 schema 资源目录。

**后果记录（方向）**：
1. 模块骨架（pom + `_vfs` schema 资源目录）创建义务归 **WI8b**（其 plan 范围须显式增补该项）。
2. WI8b/WI8c/WI8d 三处声明面落新模块 schema（roadmap Cross-Cuting 4 新模块分支）；D13 roadmap 行结论短句同步注明。

**机制风险（显式，不作为已决事实）**：stream 模型类型化 Java 类（`_StreamAggregateModel` 等 30 个）由 `nop-stream/nop-stream-flow/precompile/gen-stream-xdsl.xgen` 在 nop-stream-flow 构建期从 stream.xdef 生成；stream.xdef 全仓仅存于 `nop-kernel/nop-xdefs`；跨模块 delta 扩展 xdef 仅 test/demo 先例（`x:extends` / `x:extends="super"`），无生产模块先例；依赖方向 sql→flow 时 flow 构建看不到下游新模块内的 delta xdef。因此「声明面落新模块」的**具体机制**——delta 路径、flow 侧 `_gen` 类型化模型类生成落点、builder 消费方式——**归 WI8b 的 plan 实测裁定；若证实不可行（如必须改走直改 nop-xdefs 通路、或通用 XNode/SPI 扩展通路），须回改本裁定与 roadmap 相应行**（回改本身按 plan-first 处理）。

负责人：仓库 owner（建议项采纳）；日期 2026-10-02；受影响 WI：WI8a（落档衔接）、WI8b（骨架创建 + 机制裁定）、WI8c/WI8d（声明面落点）、WI17（编译器实现宿主）、WI21（注册接线核对）。
