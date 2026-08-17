# 2026-08-17 nop-auth-service Suite missing-tenant-id Flake（租户配置跨测试类泄漏）

## Problem

- `./mvnw test -pl :nop-auth,:nop-ai-gateway,:nop-nosql -am -T 1C`（mission verify）中 `TestMdxQuery` 5 error、`TestManyToManyProp` 1 error，均为 `nop.err.orm.missing-tenant-id`（`NopAuthGroup`/`NopAuthRole` 实体要求 `nopTenantId`）。
- 单独运行 `TestMdxQuery` 5/5 绿；故障仅在与其他测试类同 JVM 共存时出现，且依赖类组合/执行顺序（forkCount=1C 多 fork 分派、APFS 目录扫描顺序、甚至重编译都会翻转结果）。
- 此 flake 此前被登记为 "pre-existing missing-tenant-id 顺序 flake"（见 08-17 日志 W12-impl 收口条），本轮 mission verify 再次复现后彻底定位修复。

## Diagnostic Method

- 难点：多 fork（root pom `forkCount=1C`，16 核 = 最多 16 JVM）导致"哪个类与受害者同 JVM"不稳定；重编译测试类会改变 surefire 类扫描顺序，使 bisect 结果自身不稳定（加一条 try/catch 诊断代码即令故障消失）。
- 先排除：单类运行绿 → 非测试自身问题；surefire 报告时间戳排序 + `-DforkCount=1` 固定单 JVM 复现（TestMdxQuery + TestManyToManyProp 稳定失败）。
- 假设排除：ORM 模型文件带 useTenant（否——仅 NopAuthUser，且经 `feature:on`）；`TestTenant` 单独+受害者（过）；三个 plain E2E 测试两两+受害者（过）；静态代码走查 `TenantOrmSessionEntityCache`/`OrmModelLoader`/`XModelInclude` feature 求值链（无静态泄漏点）。
- 决定性证据 1：失败 run 的 DDL 日志显示 `NOP_TENANT_ID` 列出现在 **TestTenant 运行之前** 的类（TestBizObject）的建表语句中 → 泄漏源不是 TestTenant，而是更早的 plain 测试的 `assignConfigValue("nop.orm.enable-tenant-by-default", true)`。
- 决定性证据 2：对照组实验——把 pre-mission 测试类全集（46 类，单 fork）跑绿；逐一加回 mission 新增类虽不单独触发，但把三个 plain E2E 测试的 tenant toggle 移除后，单 fork全量套件转绿（TestMdxQuery 修复），且三个 E2E 自身 10/4/15 用例全过（toggle 对自身并非必需）。
- 根因链定位（代码证据）：`AbstractConfigProvider.reset()`（`NopJunitExtension.beforeAll` 调用）只对 `staticValues`（System property 来源）非空的 ref 恢复值；`assignConfigValue` 写入的 ref（如 `nop.orm.enable-tenant-by-default`）在 reset 后**保留最后赋值**，跨测试类泄漏。plain 测试（无 NopJunitExtension 生命周期）在共享 JVM 中先行将 flag 置 true 后，后续某个 ORM 模型解析（`feature:on` 在解析期求值，经 `orm-gen.xlib` `TenantSupport` 给所有实体加 `nopTenantId`）读到 true，污染该会话工厂的模型。

## Root Cause

- `IConfigProvider.assignConfigValue` 产生的 config ref 值可跨测试类存活（`reset()` 恢复不了无 System property 的 ref），共享 surefire JVM 内 plain JUnit 测试的全局 flag 切换会泄漏给后续测试。
- mission 新增的三个 plain E2E（`TestMfaLoginE2E`/`TestScanLoginMfa`/`TestMfaUserSelfService`）为对齐 TestTenant 而全局置 `nop.orm.enable-tenant-by-default=true`，成为泄漏源；`TestTenant` 自身（`@NopTestProperty`）同样在类结束后把 true 留在 provider 中。
- 次生问题：`TestOperationMfaE2E` 继承 `JunitBaseTestCase` 但缺 `@NopTestConfig`，此前一直靠其他测试泄漏的 datasource 配置才能初始化容器（多 fork 分派变化后暴露 `nop.err.ioc.empty-config-var`）。

## Fix

- 三个 plain E2E：移除 `nop.orm.enable-tenant-by-default` 的全局 toggle（保留 original 值捕获与恢复，注释说明不可全局切换的原因）。其自建 H2+ORM 栈不依赖该 flag（实测用例全绿）。
- `TestTenant`：新增 `@AfterAll` 显式 `assignConfigValue("nop.orm.enable-tenant-by-default", false)`，在 `NopJunitExtension.afterAll` destroy 之前把泄漏源归零（`@NopTestProperty` 机制本身不负责恢复）。
- `TestOperationMfaE2E`：补 `@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)`，使其自持 datasource 配置而非依赖泄漏。

## Tests

- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestMdxQuery.java`（受害者，未改动）——单 fork 与 1C 多 fork 全量套件下均保持 5/5 绿。
- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestManyToManyProp.java`（受害者，未改动）——同上。
- 三个 plain E2E + `TestTenant` + `TestOperationMfaE2E` 自身用例在去除泄漏后全绿（10/4/15/2/N）。

## Affected Files

- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestMfaLoginE2E.java`
- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestScanLoginMfa.java`
- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestMfaUserSelfService.java`
- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestTenant.java`
- `nop-auth/nop-auth-service/src/test/java/io/nop/auth/service/TestOperationMfaE2E.java`

## Notes For Future Refactors

- 在共享 surefire JVM 中写 plain JUnit 测试时，**禁止**用 `assignConfigValue` 切换影响 ORM 模型解析期的全局 flag（`feature:on` 在解析期求值且 ref 值可跨类存活）；需要 per-class 配置隔离时用 `@NopTestProperty` + `NopJunitExtension` 生命周期，并在 `@AfterAll` 显式恢复。
- 若未来框架侧修复 `AbstractConfigProvider.reset()` 对非 System-property ref 的恢复语义（恢复到 static 缺省值），`TestTenant` 的显式恢复可移除，但 plain 测试的"勿全局切 flag"约束仍然成立。
- `forkCount=1C` 下类-JVM 分派随类数量与编译产物变化，bisect 类组合时务必固定 `-DforkCount=1` 并警惕重编译改变扫描顺序。
