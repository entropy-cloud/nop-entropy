# 09: DDL 零 UK 发射 — uniqueKey.constraint 属性门导致唯一约束静默缺失

> Date: 2026-08-05
> Severity: High — nop-metadata 36 个 `<unique-key>` 全部零 `constraint` 属性，deploy/sql 三方言 DDL 不发射任何唯一约束，构建/测试全绿；数据完整性保护在部署层静默丢失

## 场景

nop-metadata 审计（MA7.3-01 + P2-MA6.6-001 根因纠正）发现：`nop-metadata/model/nop-metadata.orm.xml` 中 **36 个 `<unique-key>` 元素全部缺少 `constraint` 属性**，而 DDL 生成模板 `ddl.xlib:81-82` 以 `constraint` 属性为发射门——无该属性则 DDL 跳过该唯一约束。

后果链：

1. `deploy/sql/**` 三方言（mysql/oracle/postgresql）DDL 不包含任何 `UNIQUE` 约束语句
2. 应用层唯一性完全依赖代码侧去重逻辑（如 `NopMetaTagLabelBizModel` 的幂等去重）
3. 一旦代码侧去重遗漏（如并发、绕过路径、新入口），重复数据可自由写入——**数据库层没有任何兜底**
4. 全程 `./mvnw clean install` + 全部测试绿色——模型声明"存在唯一键"，部署产物"没有唯一约束"，**构建不报错、测试不发现**

对照：`nop-auth/model/nop-auth.orm.xml` 的 unique-key 显式声明 `constraint` 属性，其 DDL 正常发射。

## 根因

1. **ORM 模型属性即 DDL 发射门**：`<unique-key name="...">` 只声明逻辑名，`constraint="..."` 才触发 `ddl.xlib` 的约束发射；模型作者补 unique-key 时未补 constraint，语义不完整。
2. **静态 DDL 供给（checkin 的 deploy/sql 文件）没有生成期校验**：`mvn install` 只验证模型可解析、Java 可编译，从不断言"模型里的唯一键 == DDL 里的唯一约束"。模型与部署产物漂移零检测。
3. **错误假设**："模型声明了 unique-key，数据库就有唯一约束"——在生成管线里这个假设不成立，DDL 是独立模板产物。

## 正确做法

1. **补全 constraint 属性并重新生成**：`<unique-key name="UK_X" columns="a,b" constraint="UK_X"/>` 每个 unique-key 都带与 name 一致的 constraint 值；`deploy/sql/**` 三方言 DDL 再生成（model-first 闭环）。
2. **DDL 物化断言测试**：为 `DdlSqlCreator` 补断言测试——模型 36 个 UK → 三方言 DDL 各含 36 条 UNIQUE 约束语句，计数与列集逐项核对（本路线图 R3.19 已落地）。
3. **审计必查项（纳入 orm-model-audit-prompt.md）**：`grep -c "unique-key name="` 与 `grep -c "constraint="` 必须相等；任一 `unique-key` 无 `constraint` = P2 缺陷（数据完整性在部署层缺失）。

## 判定规则

> **"模型声明了唯一键"不是"数据库有唯一约束"。** 判定标准：`deploy/sql/**` 中该表的 `CREATE TABLE`/`ALTER TABLE` 语句必须含对应 `UNIQUE` 约束；模型 `<unique-key>` 元素必须带 `constraint` 属性（DDL 发射门）。
>
> 唯一性保护按"最弱环节"判定：应用层去重、ORM 校验、数据库约束三层缺一不可；数据库层缺失 = 并发/绕过路径的最终兜底缺失，即使当前无重复数据也是 live defect。

## 适用范围

- 所有 model-first 模块的 unique-key 声明与 DDL 生成核对
- 静态 DDL 供给（checkin deploy/sql）的审计
- 生成管线的"模型属性 → 部署产物"物化验证

## 参考

- `nop-metadata/model/nop-metadata.orm.xml`（36 个 unique-key，R3.19 补 constraint 后全部带属性）
- `nop-metadata/deploy/sql/mysql/oracle/postgresql/`（三方言 DDL）
- `nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/ddl.xlib`（constraint 发射门 :81-82）
- `ai-dev/audits/2026-08-05-0856-arm-MA7.3-nop-metadata-import-sync.md`（MA7.3-01）+ `ai-dev/audits/2026-08-04-1748-arm-MA6.6-nop-metadata-fix-verification.md`（P2-MA6.6-001）
- 修复：roadmap R3.19（plan-2026-08-05-0746-2 Phase 4，DdlSqlCreator 断言测试）
