# docs-for-ai 全目录准确性与一致性审计

> Status: resolved
> Date: 2026-07-15
> Scope: `docs-for-ai/` 全目录（约 120 个 .md + 04-reference/xdefs/*.xdef）
> Conclusion: 已修复全部 P0（7）+ P1（3）+ P2（9）。剩余 Q5/Q7/Q8（子模块/实体清单补全策略）留作 open question。

## Context

`docs-for-ai/` 是仓库中唯一权威的平台使用文档目录，也是日常开发 AI 的唯一阅读源。任何与源码矛盾的声明都会直接误导编码。

本次用 **4 个独立子 agent 并行** 从四个维度交叉核查整个 `docs-for-ai/`，再由本作者对最高优先级发现逐一对照真实源码复核：

| Agent | 维度 | 范围 | 详细结果 |
|-------|------|------|---------|
| 01 | 技术声明源码准确性 | `02-core-guides/`（35 个核心文档） | `_tmp/audit-01-core-guides.md` |
| 02 | 模块/runbook 准确性 | `03-modules/`、`03-runbooks/`、`01-repo-map/`、`05-examples/`、`00-required-reading-*` | `_tmp/audit-02-modules-runbooks.md` |
| 03 | 跨文档一致性 + 路由 | 全目录（矛盾检测 + INDEX.md + source-anchors + 必读入口） | `_tmp/audit-03-consistency.md` |
| 04 | 链接 + 代码示例语法 | 全目录（断链 + XML/Java 示例 vs xdef） | `_tmp/audit-04-links-examples.md` |

**复核方法**：agent 报出的 HIGH/SEV-1 级发现，本作者已用 Grep/Read 直接对照仓库源码二次确认，确认无误后才记入下文。

## Analysis

### 总览

| 类别 | 数量 | 说明 |
|------|------|------|
| 严重错误（与源码直接矛盾，已复核） | **7** | 误导编码，应优先修复 |
| 不一致 / 疑点 | **9** | 可能误导，需维护者判断 |
| 链接 / 格式问题 | **5** | 渲染或可发现性问题 |
| 已确认准确 | 65+ 条 | 含 stdSqlType→stdDataType 默认映射表全部命中、25 个 source-anchors 全部命中 |

总体结论：**文档体系整体准确率很高**，矛盾集中在 `orm-model-design.md`（domain 解析行为、主键策略、VARCHAR 阈值）和模块子模块清单（nop-ai / nop-auth / nop-code）两处。

---

### 一、严重错误（已对照源码复核）

#### E1. domain 的 precision/scale 优先级写反
- **文档**：`orm-model-design.md:122` —— "如果 column 也设置了这些属性，以 domain 的设置为准"
- **源码**：`nop-persistence/nop-orm-model/.../OrmModelInitializer.java:184-190`
  ```java
  if (domainModel.getPrecision() != null && col.getPrecision() == null) {
      col.setPrecision(domainModel.getPrecision());
  }
  ```
- **事实**：对 precision/scale，**column 优先**，domain 仅在 column 为 null 时填充。文档笼统说"以 domain 为准"对这两项是反的。
- **加重**：同方法对 stdDataType 在冲突时直接**抛 `ERR_ORM_MODEL_COL_DATA_TYPE_NOT_MATCH_DOMAIN_DEFINITION`**（`:175-182`），对 stdDomain/stdSqlType 冲突只 warn 后用 domain 覆盖（`:157-173`）。文档一句话"以 domain 为准"掩盖了三种不同冲突处理策略，开发者无法预判哪种会启动失败。

#### E2. `seq-default` 描述为"使用数据库自增序列"，与同文件自相矛盾
- **文档**：`orm-model-design.md:187` —— `tagSet="seq-default" — 使用数据库自增序列`
- **同文件**：`orm-model-design.md:234` —— "Nop 放弃了数据库自增主键支持"
- **事实**：`seq-default` 实际调用平台 `ISequenceGenerator.generateLong(key, useDefault=true)`（应用层序列号引擎，底层含 snowflake 等），**非数据库原生自增**。
- **影响**：开发者会误以为 `seq-default` 走数据库 AUTO_INCREMENT，从而在分布式/跨库场景下做出错误判断。

#### E3. MySQL VARCHAR 类型自动转换阈值错误
- **文档**：`orm-model-design.md:481` —— "precision ≤ 255：VARCHAR；255 < precision ≤ 65535：TEXT"
- **源码**：`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml:157`
  ```xml
  <sqlDataType name="VARCHAR" precision="16383" stdSqlType="VARCHAR"/>
  <sqlDataType name="TEXT" precision="65535" stdSqlType="VARCHAR" allowPrecision="false"/>
  ```
- **事实**：VARCHAR 上限是 **16383**，不是 255。按文档设 precision=500 期望 VARCHAR 是对的，但文档给出的阈值表本身错误。

#### E4. `nop-ai.md` 子模块 `nop-ai-llm` 不存在
- **文档**：`03-modules/nop-ai.md:39` 列出 `nop-ai-llm | LLM 集成`
- **源码**：`nop-ai/pom.xml` 的 `<modules>` 中无 `nop-ai-llm`，`nop-ai/` 下无该目录（已用 `ls` + `rg` 确认）
- **事实**：LLM 能力实际在 `nop-ai-core` / `nop-ai-agent`。文档还漏列 `nop-ai-dsl-orm`、`nop-ai-api`、`nop-ai-meta`、`nop-ai-app` 等真实子模块。

#### E5. `nop-code.md` 子模块 `nop-code-graph` 不存在
- **文档**：`03-modules/nop-code.md:11` 列出 `nop-code-graph | 图分析（Louvain 等）`
- **事实**：图分析能力在**独立的顶层模块** `nop-graph/`（`nop-graph-api` + `nop-graph-core`，见 `module-groups.md:22`）。文档把它误挂到 nop-code 下，且与 `module-groups.md` 自相矛盾。

#### E6. `nop-auth.md` OAuth 实体名/表名双重错误
- **文档**：`03-modules/nop-auth.md:56` 列出 `NopOAuthUserConsent | nop_oauth_user_consent`
- **源码**：`nop-auth/nop-oauth/model/nop-oauth.orm.xml:99-100`
  ```xml
  <entity className="io.nop.oauth.dao.entity.NopOauthAuthorizationConsent"
          name="io.nop.oauth.dao.entity.NopOauthAuthorizationConsent"
          tableName="nop_oauth_authorization_consent"/>
  ```
- **事实**：根本不存在 `NopOAuthUserConsent` / `nop_oauth_user_consent`，真实为 `NopOauthAuthorizationConsent` / `nop_oauth_authorization_consent`。实体名和表名双重错误。

#### E7. `nop-auth.md` OAuth 实体类名大小写错误（`OAuth` vs `Oauth`）
- **文档**：`nop-auth.md:54-55` 写 `NopOAuthAuthorization`、`NopOAuthRegisteredClient`
- **事实**：真实类名是 `NopOauth*`（`Oauth`，a 小写），包名 `io.nop.oauth.dao.entity`。平台整体命名规范是小写 a。Java 区分大小写，按 `NopOAuth*` 搜类找不到。

#### 附：Java 版本声明冲突（待确认修复口径）
- **文档**：`00-start-here/project-context.md:12` 和 `AGENTS.md` 均写 "Java 21"
- **源码**：根 `pom.xml:25,28,30` —— `<java.version>17</java.version>` + `<maven.compiler.release>11</maven.compiler.release>` + `source=11`
- **事实**：编译目标为 Java 11，声明运行时 17，文档却写 21。AI 据此可能用 17/21 才有的 API 导致编译失败。
- **说明**：此条可能是有意为之（推荐用 21 运行，代码限制为 11 兼容），但表述未澄清，归为待确认。

---

### 二、不一致 / 疑点（未逐一复核源码，依 agent 报告）

| ID | 位置 | 问题 |
|----|------|------|
| Q1 | `orm-model-design.md:208` | 复合主键运行时 id 类型写 `OrmCompositePKModel`（模型层类），实际运行时是 `OrmCompositePk`（`xlang-and-xpl-basics.md:120` 用对了） |
| Q2 | `orm-model-design.md:462` | 称 VARCHAR precision 是"字节数"，MySQL 中 `VARCHAR(n)` 的 n 实为字符数 |
| Q3 | `tenant-model.md:37` | 引用 entity.xdef 注释 `tenantProp缺省值nopTenant`，但实际代码默认 `nopTenantId`（XDef 注释本身笔误，文档忠实引用但未澄清） |
| Q4 | `INDEX.md:75`、`eql-and-database-compatibility.md:110` | 用 `delFlag` 术语，但 `logical-deletion.md` 实际只有 `delVersion`/`deleted`，无 `delFlag` |
| Q5 | `module-groups.md` | 遗漏 10 个真实根 pom 模块（含 `nop-cluster`/`nop-message`/`nop-search`/`nop-frontend-support` 等基础设施模块） |
| Q6 | `03-runbooks/build-approval-flow.md:5,397` | 称"15 个产品级示例"，实际 examples 目录 14 个顶层目录 |
| Q7 | 多个模块文档 | 子模块表系统性漏列 codegen/meta/app/api 等生成层子模块（nop-auth/wf/task/file/dyn/report/batch） |
| Q8 | 多个模块文档 | 实体清单不完整（nop-auth 漏 `NopAuthUserSubstitution` 等；nop-retry 漏死信表 `NopRetryDeadLetter`） |
| Q9 | `03-modules/reusable-modules-overview.md:63-64` | 两行加粗未闭合（`**nop-integration\`` 以反引号结尾，应为 `**nop-integration**`） |

---

### 三、链接 / 格式问题

| ID | 位置 | 问题 |
|----|------|------|
| L1 | `02-core-guides/testing.md:385` | 锚点链接 `#-重新录制-output` 多了前导 `-`，正确应为 `#重新录制-output` |
| L2 | `02-core-guides/rpc-and-distributed-rpc.md:171-176` | 表格表头 3 列（入口/方法/返回类型），数据行只有 2 列，"入口"列缺失 |
| L3 | `02-core-guides/ioc-and-config.md:120` | 表格 inline code 中 `|` 未转义（其他文档已用 `\|`） |
| B1 | INDEX.md | `04-reference/async-service-guide.md` 存在且被引用，但 INDEX 无路由条目 |
| B2 | INDEX.md | `04-reference/bizmodel-method-selfcheck.md` 完全孤立——INDEX 无条目，全目录无任何文档引用它 |

> 注：`layout-syntax-reference.md:116` 的 `[备注](2)` 是 layout DSL 语法，非 markdown 链接，为检查器误报，无需修复。

---

### 四、已确认准确（抽样）

下列经 agent 逐项核对源码**全部通过**，证明文档主体质量可靠：

- `orm-model-design.md` 的 stdSqlType→stdDataType 默认映射表（16 条）与 `StdSqlType.java:22-108` 完全一致
- stdDataType→Java 类型表与 `StdDataType.java:53-138` 一致（正确区分 FLOAT→DOUBLE、REAL→FLOAT）
- `stdSqlType` 必填、`stdDataType` 缺省取 stdSqlType 映射 —— 与 `OrmModelInitializer.java:193-199` 一致
- `source-anchors.md` 抽查 25 个锚点（TXN/BIZ/IOC/EXT/DQL/TEST/CODE/WF/AISEC/MAP/BATCH/TNT/VFS/GQL）全部命中
- 5 个 `00-required-reading-*.md` 引用的核心规范路径全部存在
- `@BizMutation` 自动事务、`@Inject` 不支持 private、`@InjectValue` 约定 —— 跨 6+ 文档一致且与源码一致
- 控件匹配链 `control→domain→stdDomain→stdDataType` 跨文档一致
- 7 个 ORM 模型表名（nop-wf/task/sys/rule/batch/retry/tcc）与文档逐一一致
- `nop-wf` 不依赖 `nop-task` 声明属实
- ORM `<column>` 约 50 处示例属性全部符合 `entity.xdef`，`ext:dict` 为合法 xdsl 扩展
- `docs-for-ai/` 内所有相对路径 .md 链接全部有效（无断链）

## Conclusion

文档体系**整体可信**（65+ 条已确认准确），但存在 **7 条已复核的严重错误**，集中分布且可一次性修复：

**修复优先级清单：**

| 优先级 | 项 | 动作 |
|--------|----|------|
| P0 | E1 | `orm-model-design.md:122` 改为按属性分述：stdDomain/stdSqlType 冲突 domain 覆盖（warn）；stdDataType 冲突**抛异常**；precision/scale **column 优先** |
| P0 | E2 | `orm-model-design.md:187` `seq-default` 描述从"数据库自增序列"改为"平台默认序列号生成器（`useDefault=true`）" |
| P0 | E3 | `orm-model-design.md:481` VARCHAR 阈值 255 改为 16383（并注明 dialect 相关） |
| P0 | E6/E7 | `nop-auth.md:54-56` 修正 OAuth 实体：删除 `NopOAuthUserConsent`，`NopOAuth*` → `NopOauth*`，补 `NopOauthAuthorizationConsent` |
| P0 | E4/E5 | `nop-ai.md` 删 `nop-ai-llm`；`nop-code.md` 删 `nop-code-graph` 并注明图能力在顶层 `nop-graph` |
| P1 | E7附 | 统一 Java 版本表述：确认实际目标后修正 `project-context.md`/`AGENTS.md`（21→17/11）或加注运行/编译差异 |
| P1 | Q4 | INDEX.md:75 / eql-and-database-compatibility.md:110 `delFlag` → `deleted` |
| P1 | B1/B2 | INDEX.md 补 `async-service-guide.md`、`bizmodel-method-selfcheck.md` 条目 |
| P2 | Q1-Q3/Q5-Q9/L1-L3 | 按上表逐项修复 |

- 被否决方案：无（本次为审计，不涉及方案选择）
- 后续工作：建议在修复后新建 `ai-dev/plans/` 计划逐条修正，并补充对应回归验证（如修正后跑 `node ai-dev/tools/check-doc-links.mjs --strict`）

## Open Questions

- [ ] E7附（Java 版本）：21 是推荐运行版本还是笔误？需维护者确认后统一文档表述口径。
- [ ] Q5（module-groups.md 漏模块）：是否应把 `nop-cluster`/`nop-message`/`nop-search` 等基础设施模块补入分组文档？
- [ ] Q7/Q8（子模块/实体清单不完整）：模块文档的清单策略是"只列核心"还是"应完整"？需定规则。
- [ ] E1 中 stdDataType 冲突抛异常的行为，文档是否应明确提示开发者"设 domain 时不要在 column 上重复设 stdDataType"？

## References

- 详细审计结果（4 份 agent 报告）：
  - `_tmp/audit-01-core-guides.md`
  - `_tmp/audit-02-modules-runbooks.md`
  - `_tmp/audit-03-consistency.md`
  - `_tmp/audit-04-links-examples.md`
- 关键源码锚点：
  - `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/init/OrmModelInitializer.java:147-199`
  - `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml:156-161`
  - `nop-auth/nop-oauth/model/nop-oauth.orm.xml:99-100`
  - `nop-ai/pom.xml`、根 `pom.xml:25-30`
- 涉及文档：
  - `docs-for-ai/02-core-guides/orm-model-design.md`
  - `docs-for-ai/03-modules/nop-ai.md`、`nop-code.md`、`nop-auth.md`
  - `docs-for-ai/INDEX.md`、`docs-for-ai/00-start-here/project-context.md`
