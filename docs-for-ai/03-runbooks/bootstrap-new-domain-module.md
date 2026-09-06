# 新增业务域模块：从 ORM 模型到可用菜单与页面

> **用途：** 在一个已有的 Nop 应用工程（`nop-app-erp` / `nop-app-iot` 形态：`module-<domain>/` 多模块结构 + `app-*-all` 聚合）内，**从零新建一个业务域模块**的端到端流程。以 `nop-app-erp` 18 个业务域的实际开发史（含全部返工修正）为实证蓝本。
>
> **按角色限定阅读（不要通读全部文档）：**
>
> | 角色 | 必读 | 按需 |
> |---|---|---|
> | 后端实施 | 本篇第 1~4、6~8 步 + `../00-start-here/application-project-pitfalls-backend.md` | 第 5、9 步了解接口即可 |
> | 前端实施 | 本篇第 5~6、9 步 + `../00-start-here/application-project-pitfalls-frontend.md` | 第 1~4 步了解模型接口即可 |
>
> 项目级冷启动（从零创建整个应用工程）看 `bootstrap-new-application.md`；已有模块内加实体/字段看 `create-new-entity.md` / `add-field-and-validation.md`；功能切片总纲看 `feature-implementation-checklist.md`。

## 适用场景

- 应用需要接入一个新业务域（如 erp 加 `module-cs`、iot 加 `module-device`）
- 全新产品首批域模块的批量落地（历史做法：一次计划批量 8 域）

## 流程总览

```text
ORM 模型（唯一真相）
  → nop-cli gen 一次性生成七子模块骨架（codegen/dao/meta/service/web/app/api）
  → 手写保留层：action-auth 菜单 + xbiz(mutation auth) + Errors 错误码
  → mvn clean install 增量再生成（xmeta/view/page/i18n/api）
  → 页面定制（delta bounded-merge 覆盖 _gen）
  → i18n 合规
  → CRUD 冒烟测试（RECORDING → CHECKING）
  → app-all 聚合 + 菜单合并校验
  → E2E（可 deferred）
```

## 前置条件

- nop-entropy 已构建到本地仓库（`bootstrap-new-application.md` 第 0 步）
- 已确认该域的持久化模型设计（实体清单、关系、字典）——模型是唯一真相源，建模缺陷会在后续每一步放大

### 第 1 步：写 ORM 模型

位置：`module-<domain>/model/app-<app>-<domain>.orm.xml`（唯一真相源，其余全部是生成物）。

约定（以 nop-app-erp 定制化层为准，应用项目按自身 AGENTS.md 等价规则）：

| 项 | 规则 |
|---|---|
| 实体名 | `Erp<Domain><Entity>`（如 `ErpCsTicket`）；iot 项目用 `IoT<Domain><Entity>` 等自有前缀 |
| 表名 | `erp_<short>_*` 小写下划线 |
| 字典 | 真相定义在 ORM `<dict>` 节点，命名空间 `<app>-<short>/<dict-name>`；`_vfs/dict/` 下 yaml 是生成物 |
| 自动值标注 | 生成码/引用码列 `tagSet="var"`；业务时间戳（postedAt/approvedAt 等）`tagSet="clock"`；主键 `tagSet="seq-default"` |
| FK 显示名 | 被引用实体的显示列标 `tagSet="disp"`（可为 title/fullName 等非 name 列），下游自动获得显示名解析——**不要手写 @BizLoader FK-name 解析**（erp 曾因此产生 826 处冗余代码后全量拆除） |

`tagSet` 不是可选项：缺 `var`/`clock` 标注会让 autotest 快照回放对非确定值误报（erp 曾 18 域补标 144 处）。

### 第 2 步：一次性生成模块骨架（仅一次）

```bash
java -jar nop-cli.jar gen -t=/nop/templates/orm module-<domain>/model/app-<app>-<domain>.orm.xml
```

产出七子模块（`<app>-<short>-codegen/-dao/-meta/-service/-web/-app/-api`）+ `deploy/sql/` 建表脚本。工程化细节、路径 A/B 边界与常见卡点见 `bootstrap-new-application.md`。

> **关键边界**：`nop-cli gen` 只跑这一次。此后一切模型变更走第 4 步的 Maven 增量再生成，**不要重跑 gen**（会与手写层冲突）。

### 第 3 步：手写保留层（菜单 / 权限 / 错误码）

生成物一律带 `_` 前缀，**永不手改**（改了会被下次生成还原——erp 曾三轮返工才固化此纪律）。手写内容全部放无 `_` 前缀的保留层，以 `x:extends` 继承生成层。

**action-auth.xml 菜单**（`-web` 模块）：

```text
_vfs/<app>/<short>/auth/
├── _<app>-<short>.action-auth.xml   # 生成层：自动测试菜单根 + :query/:mutation 坍缩桶（禁改）
└── <app>-<short>.action-auth.xml    # 手写层：x:extends 生成层，删除测试根，定义正式菜单
```

菜单树三层结构：`TOPM`（域一级菜单）→ `SUBM`（菜单组/实体页入口，`url` 指向 `main.page.yaml`）→ `FNPT`（功能权限点，`<permissions>Entity:action</permissions>`）。

硬性规则（每条都是踩坑换来的）：

1. `roles="A,B,C"` **只认逗号分隔**——斜杠分隔会被解析成单个无效 roleId，菜单语义性失效且不报错（erp 7 域 35 行中招）。
2. SUBM 可见性依赖其下 FNPT cascade-up：**每实体至少配一个 FNPT 权限点**，否则整域菜单可能不可见。
3. 域聚合 `app.action-auth.xml`（`-app` 模块）`x:extends` 手写层；**全局聚合器 `app-*-all/.../auth/app.action-auth.xml` 必须追加新域的 `x:extends` 路径**——漏注册则菜单永不可达。
4. 静态角色种子 + `displayName`（中文）+ `i18n-en:displayName`（英文）+ `icon` + `orderNo` 一次配齐。

**xbiz 权限**：每个手写 `<mutation>` 的**首个子元素**必须是 `<auth permissions="Entity:action"/>`——缺 auth 的 mutation 权限检查形同虚设（erp 曾一次性补齐 117 处）。参考 `write-bizmodel-method.md` 与 `auth-and-permissions.md`。

**错误码**：`-service` 模块 `Erp<Domain>Errors.java` Java 接口（本模板体系不用 err.xml），前缀 `erp.err.<short>`（应用自有前缀），标 `@Locale("zh-CN")`。见 `error-codes-and-nop-exception.md`。

### 第 4 步：构建触发增量再生成

```bash
mvn clean install -DskipTests
```

生成链各司其职（明细见 `change-model-and-regenerate.md`）：`-meta` 产 xmeta/i18n yaml/dict yaml，`-web` 产 `<Entity>.view.xml` 的 `_gen/` 生成视图 + `main.page.yaml`，`-meta/postcompile/gen-crud-api.xgen` 产 api 模块 Java。验证：`xmllint --noout` 抽查 + 单域 `mvn test -pl module-<domain> -am`。

### 第 5 步：页面定制（delta 覆盖，不碰 _gen）

手写 `<Entity>.view.xml` 以 bounded-merge 覆盖 `_gen/_<Entity>.view.xml`。规范要点：

- **表单分组**用 `====>` 标记（如 `>baseInfo[基本信息]`、`^audit[审计信息]` 缺省折叠）；查询表单 ≥5 字段并配 `filterOp`；≥20 字段用 `size="lg"`。
- **格式化在 xmeta 层**按 ORM `domain` 一次配置全域生效（amount `#,##0.00`、百分比 `0.00%`、日期 `YYYY-MM-DD` 等），不要在页面散写。
- **页面数据访问强制 REST `/r/`**：`POST /r/<BizObj>__<method>` 或 `@query:`/`@mutation:` 前缀——禁止 `/graphql` + 手拼 query 字符串（erp 曾 74 文件迁移）。

详见 `admin-page-development-roadmap.md`、`amis-rendering.md`、`override-platform-page-with-delta.md`。

### 第 6 步：i18n 合规

- 页面用户可见文案必须有英文承载：手写页加 `i18nEn` 属性；codegen 产物改模型源 `i18n-en:` 属性后重新生成。
- 运行时日志统一英文（不走 i18n）；异常参数传状态码/枚举名/字典值，禁中文散文；`ErrorCode.define` 中文描述合规（zh-CN 为源语言）。
- `_` 前缀生成 i18n yaml 禁手改；web 菜单 i18n 需 `-web/precompile2/gen-i18n.xgen` 二次生成。

判定准绳以应用项目的 i18n 合规文档为准（erp：`docs/architecture/i18n-compliance.md`）。

### 第 7 步：CRUD 冒烟测试

测试放 `-service/src/test`（继承 `JunitAutoTestCase`），快照落模块根 `_cases/`：

1. `@NopTestConfig(localDb, initDatabaseSchema=true, enableActionAuth=false)`，先 RECORDING 录制（录完抛 `snapshot-finished` 是预期非失败）→ 人工审查 CSV → 切 CHECKING 回放。
2. 实体抽样规则：每域 1 主实体 + 1 头-行对。
3. 已知约定：纯插入用 `__update`；`seq-default` 主键非自动变量需测试代码内取 id；跨域强制 FK 需依赖前置域数据；`del_version`/decimal 列快照值置 `*`。

见 `write-tests.md`、`write-integration-test-with-noptestconfig.md`。

### 第 8 步：聚合与集成验证

1. `app-*-all/pom.xml` 追加新域 `-app` 依赖。
2. 全局聚合器 `app.action-auth.xml` 追加 `x:extends`（若第 3 步未做）。
3. 菜单合并校验：`mvn test -pl app-*-all -Dtest=TestAppActionAuthMerge`（TOPM id 零冲突）。
4. 全量 `mvn clean install -DskipTests` + `mvn test`，本地启动冒烟。

### 第 9 步：E2E（可 deferred）

基础设施未建立时标注 deferred，不阻塞域闭合。建立后：Playwright + 页面断言走 REST `/r/`（禁断言 GraphQL），flux 页面先过 `validate:flux` 门禁。见 `e2e-testing.md`。

## 常见坑

1. **手改 `_` 前缀生成文件**（action-auth/view/i18n yaml 都中招过）→ 下次生成被还原，且失败是静默的。
2. **roles 斜杠分隔** → 菜单语义性失效、无报错；只认逗号。
3. **mutation 缺 `<auth>`** → 权限检查形同虚设，事后补齐成本 ×117。
4. **聚合器漏注册 x:extends** → 菜单永不可达，排查成本极高。
5. **页面走 /graphql 手拼查询** → 与平台 REST 契约漂移，E2E 无法统一断言。
6. **缺 tagSet 标注** → 快照回放非确定值误报；缺 `disp` → FK 显示名退化成手写 loader 海。
7. **重跑 nop-cli gen** → 与手写保留层冲突；模型变更只走 Maven 增量。
8. **ORM 建模偷懒直接开工** → 模型是唯一真相源，缺陷沿生成链放大到每一层。

## 相关文档

- `../00-start-here/application-project-pitfalls-backend.md` / `-frontend.md` — 按角色分离的教训速查（本流程全部踩坑的根因与检测命令）
- `bootstrap-new-application.md` — 项目级冷启动（本流程的前置）
- `create-new-entity.md` / `add-field-and-validation.md` / `change-model-and-regenerate.md` — 模型与再生成
- `feature-implementation-checklist.md` — 已有模块内功能切片总纲
- `auth-and-permissions.md` — action-auth/data-auth 权威细节
- `write-bizmodel-method.md` / `choose-entity-bizmodel-processor.md` — 业务逻辑层
- `admin-page-development-roadmap.md` / `override-platform-page-with-delta.md` — 页面定制
- `write-tests.md` / `write-integration-test-with-noptestconfig.md` / `e2e-testing.md` — 测试
- `error-codes-and-nop-exception.md` / `error-handling.md` — 错误码与异常
