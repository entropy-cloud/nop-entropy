# EQL Console 后端 API 与安全设计

适用范围：`nop-entropy` 的 `nop-eql-console` 模块组与 `EqlConsole` BizModel。本文定义后端模块边界、API 契约、诊断与错误模型、安全门控与配置项；页面与交付见 `04-workbench-page-and-delivery.md`，前端字段语义见 `02-frontend-dialect-and-editor-contract.md`。

## 1. 模块分层

```text
nop-eql-console/
├── nop-eql-console-api      DTO 与常量；依赖 nop-api-core
├── nop-eql-console-service  BizModel + 编译/执行/安全 gate；依赖 nop-biz、nop-orm、nop-orm-eql
├── nop-eql-console-web      页面/action-auth/i18n/beans；依赖 nop-web
└── nop-eql-console-app      （可选）独立 Quarkus 宿主；v1 不交付
```

约束：

- 不建 `model/dao/meta/codegen`：v1 无持久化实体；查询历史由浏览器本地存储承担。
- 依赖方向：`api ← service ← web`；service 不依赖 web；不依赖 `nop-report`/`nop-metadata`/`nop-dev-tools`。
- 模块默认只被 dev/demo 应用依赖；是否进入生产应用由部署方裁定（见 `00-vision.md` §6）。
- BizModel 注册带 `nop.debug` 条件；`nop.debug=false` 时 Bean 不注册，API 不可达。

## 2. API 契约

BizModel 名：`EqlConsole`。REST 形态：`POST /r/EqlConsole__{method}`；GraphQL 同名操作。所有入参 `@Name` 显式声明。

| 方法 | 类型 | 入参 | 成功响应 | 权限 |
|---|---|---|---|---|
| `loadMeta` | `@BizQuery` | `querySpace?` | `EqlConsoleMeta` | `EqlConsole:read` |
| `validate` | `@BizQuery` | `eql` | `EqlValidateResult` | `EqlConsole:read` |
| `execute` | `@BizQuery` | `eql`, `params?`, `querySpace?`, `offset?`, `limit?` | `EqlExecuteResult` | `EqlConsole:read` |
| `executeUpdate` | `@BizMutation` | `eql`, `params?`, `querySpace?` | `EqlUpdateResult` | `EqlConsole:write` |

命名与语义约束：

- `eql` 为单条语句；多语句在编译期被拒绝，API 不承担语句切分。**分页由控制台统一负责**：预解析阶段拒绝 EQL 文本自带的 `LIMIT`/`OFFSET`（避免与 `LongRangeBean` 外层分页叠加成非法 SQL），需要分页时使用 API 的 `limit`/`offset`。
- `params` 为**有序数组**，按 EQL 文本中 `?` 出现的顺序绑定（EQL 文法只接受位置参数 `?`，v1 不支持语言级命名参数）；数量必须与 `?` 数量一致，不一致返回参数诊断。控制台的“命名参数”只是页面配置映射（`params`+`paramOrder`），不进入 EQL 文本。禁止把参数拼进 EQL 文本。
- `querySpace` 的执行值**始终取自编译产物**（`ICompiledSql.getQuerySpace()`）；入参 `querySpace` 只做一致性校验（与编译值不一致时报错），不得用它替换执行数据源。EQL 文本中的 `@querySpace` 装饰器仅在入参显式提供 `querySpace` 且两者一致时允许，否则拒绝（防止用装饰器静默切换数据源）。
- `limit` 缺省 1000、上限 10000；`offset` 缺省 0。逐查询 `timeoutSeconds` 属 v2 预留，v1 不暴露该入参；v1 超时来自配置 `nop.eql-console.timeout-seconds`（见 §3/§6）。
- `loadMeta` 的 `entities` 在 v1 按 querySpace 过滤，**不做**实体级权限裁剪：持有 `EqlConsole:read` 即可见全部实体/属性名（不含数据内容）。实体级可见性裁剪属 owner 决策点（见 `00-vision.md` §6 #3），实现前不得声称已按实体鉴权。
- `executeUpdate` 仅在 `nop.eql-console.allow-write=true` 时可用，仅接受单条 INSERT/UPSERT/UPDATE/DELETE，否则显式报错；不提供「关闭逻辑删除」或「跳过租户」参数。

### 2.1 DTO 字段契约

`EqlConsoleMeta`：

| 字段 | 语义 |
|---|---|
| `metaVersion` | 元数据版本：对 ORM 模型（实体/属性/关联）+ 方言配置 + 控制台配置计算稳定哈希生成（无现成模型版本 API，不得声称来自框架） |
| `querySpaces` | `[{ name, dialect, entityCount? }]` |
| `entities` | `EntitySchema[]`（结构见前端契约文档；白名单投影，不含物理表名/类名） |
| `profile` | 完整的 `SqlDialectProfile` 数据（字段见 `02-frontend-dialect-and-editor-contract.md` §1.2），**不含** `validation` / `execution` action；v1 不提供 `functions`（无权威函数注册表） |
| `capabilities` | `{ explain, cancel, multiStatement }`；v1 全为 false。写模式由 `config.allowWrite` 单一来源决定。前端只按这些能力位显示/隐藏入口 |
| `config` | `{ maxRows, maxRowsLimit, defaultTimeoutSeconds, allowWrite }`（键与 §6 配置项一一对应） |

`EqlValidateResult`：

| 字段 | 语义 |
|---|---|
| `ok` | 编译是否通过 |
| `diagnostics` | `SqlDiagnostic[]`；`ok=true` 时可为空数组 |
| `compiledSql` | 目标方言 SQL 文本（参数以占位符展示），供预览/复制 |
| `statementKind` | `SELECT` / `INSERT` / `UPSERT` / `UPDATE` / `DELETE` |
| `querySpace` | 编译期推导的数据源 |
| `readEntities` | 编译产物读取的实体名（用于辅助权限/影响面提示） |
| `writeEntity` | 写语句目标实体名（只读语句为空） |

`EqlExecuteResult`：

| 字段 | 语义 |
|---|---|
| `ok` | 是否执行成功 |
| `diagnostics` | 编译失败时的诊断；成功时为空或省略 |
| `columns` | `[{ name, label?, type?, stdSqlType? }]`，顺序即列顺序 |
| `rows` | 对象数组，键为列名；要求 `columns[].name` 唯一，重复列名在 validate/execute 阶段返回诊断拒绝 |
| `rowCount` | 本次返回行数（≤ limit） |
| `truncated` | 是否因 limit 截断（采用 `limit+1` 探测，不返回第 limit+1 行） |
| `statementKind` / `querySpace` / `elapsedMs` | 元信息 |
| `compiledSql` | 可选，执行所用 SQL 文本（不含数据权限展开） |

`EqlUpdateResult`（与只读执行共用错误信封）：`{ ok, diagnostics?, affectedRows?, elapsedMs?, statementKind?, querySpace? }`；用户 EQL 语法/语义错误同样返回 `ok=false + diagnostics`，系统/权限错误抛 `NopException`。

## 3. 编译与执行路径（契约级）

```text
validate(eql):
  1) EqlASTParser 预解析 → 扫描 decorator：拒绝 @dump，校验 @querySpace（若存在必须与请求一致）
  2) sessionFactory.compileSql(name, eql, /*disableLogicalDelete*/ false,
                               /*astTransformer*/ null, /*useCache*/ true,
                               /*allowUnderscoreName*/ false, /*enableFilter*/ true)
  → 成功: compiledSql/statementKind/querySpace/readEntities/writeEntity
  → 失败: NopException → SqlDiagnostic[]（ok=false）

execute(eql, params, ...):
  1) 与 validate 相同的预解析 + decorator 门控（必须在 compile 之前，因为 @dump 在编译内部就会打印 AST）
  compiled = 同上（enableFilter=true）
  安全 gate（statementKind/断言项/写权限/limit/timeout(配置)）
  → SELECT: queryExecutor(querySpace).executeQuerySql(session, compiled, 原始参数值, range, 读取行)
  → DML: 仅 executeUpdate 路径，@BizMutation 事务内执行
  → 结果列元数据来自 compiled.getDataSetMeta()
```

约束：

- 编译必须开启 `enableFilter`，否则行级数据权限不会生效；租户与逻辑删除过滤始终生效，不提供关闭开关。
- 参数值必须是**原始值**；执行的参数构造（含租户参数追加）由 ORM 执行器内部完成，禁止在入口二次构造。
- 编译预览 SQL 可能不含数据权限条件文本（以 marker 注入、执行期展开）；API 需在文档/前端提示「预览 SQL 与实际执行 SQL 可能不同」。
- **编译前 AST gate**：装饰器白名单——允许 `@enableFilter`（仅 true 语义）、`@querySpace`（一致性规则见 §2）；拒绝 `@dump` 与**未知装饰器**（避免静默 no-op）；对 `UPDATE`/`DELETE` 检查 AST 是否存在 `WHERE`，无 `WHERE` 直接拒绝（不依赖 `ICompiledSql`，它不暴露 WHERE）。
- **注释策略**：注释只做语法容忍（lexer HIDDEN），不承载任何语义；控制/Hint 不使用注释，统一用 decorator；v1 不做数据库 hint 透传。
- **超时**：v1 通过请求上下文设置 `IContext.setCallExpireTime(now + timeoutMillis)`（执行前保存、finally 恢复）；JDBC 层 `JdbcHelper.getQueryTimeout` 会取「SQL 超时」与「上下文剩余时间」的较小值并换算为秒。**不修改** QueryPlanCache 共享的编译产物。单位换算固定：配置 `nop.eql-console.timeout-seconds` 为**秒**，注入上下文前乘 1000；`nop.dao.max-query-timeout` 为**毫秒**。超时生效与并发不串超时为必过测试（不得以「显式降级」收口）。
- **取消**：`executeQuery` 无 `ICancelToken`。v1 `capabilities.cancel=false`：前端「取消」只停止等待并恢复 UI，服务端可能继续执行；v2 再评估 `executeStatement`/`ICancelToken` 路径。

## 4. 诊断与错误模型

### 4.1 SqlDiagnostic

| 字段 | 语义 | 约束 |
|---|---|---|
| `code` | 稳定错误码（`nop.err.eql.*` 或 `nop.err.eql-console.*`） | 必填 |
| `message` | 面向用户的错误描述（英文/中文由 i18n 资源决定） | 必填 |
| `severity` | `error` / `warning` / `info` | 必填 |
| `line` / `column` | 1-based 行、1-based 列（Unicode code point） | 必填；位置未知时填 1/1 并附标记 |
| `endLine` / `endColumn` | 可选结束位置，语义同上 | 可选 |

### 4.2 错误边界

| 错误类型 | 行为 |
|---|---|
| 用户 EQL 语法/语义错误（parse/compile） | HTTP 200，`ok=false` + `diagnostics[]`；`execute` 与 `executeUpdate` 同 |
| 参数缺失/多余/类型错误 | HTTP 200，`ok=false` + 一条精确诊断（参数错误定位到整条语句或 1/1） |
| 权限不足 | `NopException`（`nop.err.eql-console.permission-denied`），框架转标准错误响应 |
| 写操作未开启 / 语句类型被拒 | `NopException`（`nop.err.eql-console.write-not-allowed` / `statement-not-allowed`） |
| 数据库执行失败（超时、连接、SQL 错误） | `NopException`（`nop.err.eql-console.execute-failed`），`errors` 中带底层 code/SQLState（若有） |
| 服务端内部错误 | 框架统一 `ApiResponse` 错误 |

### 4.3 诊断样本矩阵（验收基线）

| 样本 | 期望 code（族） | 位置 |
|---|---|---|
| 语法错误 | `nop.err.antlr.common.parse-fail`（或 `nop.err.antlr.not-end-properly`） | token 行列；无则 1/1 + `no-location` |
| 未知实体 | `nop.err.eql.unknown-entity-name` | 有则精确，无则 1/1 + `no-location` |
| 未知属性/列 | `nop.err.eql.unknown-column-name` | 同上 |
| 未加别名引用 | `nop.err.eql.unknown-alias` | 同上 |
| 多语句 | `nop.err.eql.not-support-multiple-statement` | 当前实现可能无 loc，必须显式 `no-location` |
| `@dump` 装饰器 | `nop.err.eql-console.dump-decorator-not-allowed` | 预解析无 loc 时 1/1 + `no-location` |
| commit/rollback | `nop.err.eql.not-support-transaction-statement` | 有则精确，无则 1/1 + `no-location` |
| 重复列名（控制台约束） | `nop.err.eql-console.duplicate-column` | 1/1 + `no-location` |
| 自带 LIMIT/OFFSET（控制台约束） | `nop.err.eql-console.inline-pagination-not-allowed` | 1/1 + `no-location` |
| 未知/禁止装饰器 | `nop.err.eql-console.decorator-not-allowed` | 1/1 + `no-location` |
| 写操作未开启 | `nop.err.eql-console.write-not-allowed` | 整条语句 |
| 无 WHERE 写操作 | `nop.err.eql-console.unsafe-write-not-allowed` | 整条语句 |

- `no-location` 通过 `SqlDiagnostic` 的 `code` 前缀或附加字段表达（实现时二选一并在 §4.1 冻结），前端显示「(无位置)」。

约束：

- 语法/语义错误必须走 `ok=false + diagnostics`（因为 REST `ApiResponse` 会丢弃 `SourceLocation`；GraphQL 虽保留行列但单条错误无 errorCode，不能作为统一协议）。
- 错误码族 `nop.err.eql-console.*` 在 v1 冻结；新增错误码需同步使用文档。

### 4.4 错误响应与 HTTP 状态

- `/r/` 的错误响应状态码来自 `ApiResponse.httpStatus`，默认值为 0 → HTTP 200；**不要**按 HTTP 403/404 做验收断言。
- 验收一律断言响应体 `status`/`code`/`msg`（权限不足、Bean 未注册、参数错误均是如此）；如需真实 HTTP 状态，必须显式设置并单独验证。
- Bean 未注册时 `/r/EqlConsole__*` 的返回形态由路由层决定（可能是 404 或框架错误体），验收时先探测实际形态再固化断言。

## 5. 安全门控矩阵

| 层次 | 规则 | 违反时 |
|---|---|---|
| 部署开关 | Bean 仅 `nop.debug=true` 注册；模块默认不进入生产应用（装配层面的门控） | 框架错误响应（默认 HTTP 200 + body code；Bean 未注册形态先探测后固化）；页面显示「未启用」 |
| 操作权限 | `EqlConsole:read`（loadMeta/validate/execute）；`EqlConsole:write`（executeUpdate） | 框架错误响应（默认 HTTP 200）+ body code `permission-denied` |
| 权限链路前置 | **fail-closed**：控制台入口统一检查 `nop.auth.enable-action-auth=true`，为 false 时直接拒绝并返回 `nop.err.eql-console.action-auth-disabled`；同时断言 `IActionAuthChecker` bean 存在（框架在 enable=true 但无 checker 时会 SKIP 权限检查，属 fail-open）；启动时若 `allow-write=true` 且上述任一条件不满足，则 fail-fast（拒绝注册控制台 Bean）；dev/E2E profile 必须显式开启（见 04 §5.2） | 入口拒绝 + 启动失败 + 错误码 |
| 语句面 | `execute` 只允许 SELECT；DML 仅 `executeUpdate` 且 `allow-write=true` | `statement-not-allowed` / `write-not-allowed` |
| 数据权限 | 编译 `enableFilter=true`；租户/逻辑删除始终开启 | 行级越权（必须由测试钉死） |
| 危险构造 | 拒绝 `SELECT ... FOR UPDATE`、`@dump`、未知装饰器；`@querySpace` 必须与请求一致；不允许关闭租户/逻辑删除过滤；`executeUpdate` 的 UPDATE/DELETE 必须含 WHERE（无 WHERE 直接拒绝） | `statement-not-allowed` / `unsafe-write-not-allowed` |
| 结果规模 | `limit` 默认 1000、上限 10000；`truncated` 显式返回 | 超限截断 |
| 超时 | 默认 30s，上限 `nop.dao.max-query-timeout`（毫秒配置换算为秒）；慢查询必须可终止/超时 | 超时报错 |
| 元数据暴露 | 白名单 DTO：实体名/展示名/属性名/类型/关联/querySpace；不含物理表名、Java 类名、SQL 片段、内部路径 | 由 DTO 投影保证 |
| 凭据 | 不接收数据库连接信息；只使用平台已配置的 ORM 数据源 | 入参不含连接字段 |

## 6. 配置项与权限点

| 配置键 | 默认 | 语义 |
|---|---|---|
| `nop.debug` | false | 控制台 Bean 的部署开关 |
| `nop.eql-console.allow-write` | false | 是否允许 `executeUpdate`；为 true 要求 `nop.auth.enable-action-auth=true`，否则启动 fail-fast |
| `nop.eql-console.max-rows` | 1000 | 默认单次返回上限 |
| `nop.eql-console.max-rows-limit` | 10000 | 允许请求的最大上限 |
| `nop.eql-console.timeout-seconds` | 30 | 默认查询超时 |
| `nop.dao.max-query-timeout` | 平台既有配置（毫秒，默认 600000） | 控制台超时（秒）换算后不得突破该值 |

| 权限点 | 语义 |
|---|---|
| `EqlConsole:read` | 读取元数据、校验、执行只读查询 |
| `EqlConsole:write` | 执行受控写语句 |

## 7. 拒绝的方案

- `nop-sys` 内实现：系统管理域与「任意 EQL」安全边界混杂，且 `nop-sys` 常驻所有应用，暴露面过大。
- 扩展 `nop-metadata`：其外部数据源与 ORM querySpace 是两套并行机制，且 `queryData` 不返回列元数据；会让前端混淆数据源语义。
- 扩展 `nop-report`：`nop-report-core` 不依赖 ORM，报表数据集不是即席查询；会破坏报表分层。
- 在 `nop-biz/dev` 新增 BizModel：dev 目录定位为框架内省/调试库，没有 web 页面归属，ORM 依赖获取方式也不适合。
- v1 支持原生 SQL 模式：安全模型（DDL/多语句/连接白名单）与 EQL 完全不同，不能复用 EQL 的 DDL 排除保证。
- v1 引入异步任务/结果物化：EQL 查询规模与 `LongRangeBean` 分页已覆盖 v1 需求；异步会引入任务表、事件通道、取消协议等额外基础设施。
