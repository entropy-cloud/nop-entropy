# 39 ORM 缓存装配与 ICache 契约缺陷收口

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/analysis/2026-10/2026-10-05-orm-query-optimizer-landscape-vs-nop-orm.md` §4.1.1
> Related: `ai-dev/backlog/orm-defect-audit-followups.md`（非缺陷关闭项与其余遗留）
> Autonomy: 三个目标均为已核实 live defect / contract violation，属 `Fix`，直接实施

## Purpose

收敛三处已核实的缓存缺陷：

1. `OrmSessionFactoryBean.refreshConfig()` 守卫读 bean 字段而转型读 impl 字段；IoC 装配下 `@Inject` setter 注入使 bean 字段恒非 null ⇒ 守卫恒假 ⇒ `nop.orm.entity-global-cache.size/timeout` 配置热刷新在**所有 IoC 部署**中静默失效
2. `ICache.putIfAbsent` 默认实现（`:70-76`）违反自身 javadoc（`:66-69`）明文的 check-then-act 原子契约
3. `ICache.computeIfAbsent` 默认实现同样非原子；`JavaxCache` 未覆写同步版

## Current Baseline（全部经一手核实）

- `SessionFactoryConfig.java:226-228`：`@Inject setGlobalCache(@Named("nopOrmGlobalCacheProvider"))`；`orm-defaults.beans.xml` 的 `nopOrmSessionFactory` bean 无 `globalCache` property ⇒ 注入总是发生
- `OrmSessionFactoryBean.init():82` 把 bean 字段拷入 impl；`:102-107` 仅在 bean 字段为 null 时新建 `LocalCacheProvider` 写入 impl（不回写）⇒ 两字段分叉
- 修复前 `refreshConfig():60` 守卫 `getGlobalCache() == null`（bean 字段）恒假，`:61-64` 永不执行
- `ICache.java:66-69` javadoc："实现方应保证check-then-act的原子性：并发对同一key调用时，只有一个调用返回true"；默认实现却是 `containsKey`+`put` 两步
- 覆写矩阵：`LocalCache`/`MapCache`/`NosqlCache`/`JavaxCache` 已覆写 `putIfAbsent`；`computeIfAbsent` 仅前三个覆写，`JavaxCache`（及继承它的 `HazelcastCache`）落回非原子默认
- `JavaxCache.getConfig()` 恒返回 null（外部 JCache 自管配置）

## Goals

- G1 `refreshConfig()` 对注入的 `LocalCacheProvider` 真实生效；非 `LocalCacheProvider` 时显式告警而非静默跳过
- G2 `ICache` 两个默认实现满足 javadoc 原子契约
- G3 `JavaxCache.computeIfAbsent` 具备单一胜者语义
- G4 缓存契约 owner doc 落盘

## Execution Plan

### Phase 1 - 修复与红绿证

Status: completed
Targets: `ICache.java`、`JavaxCache.java`、`OrmSessionFactoryBean.java`、两个测试类
Item Types: `Fix`

- [x] `ICache.putIfAbsent` 默认实现改 `synchronized`
- [x] `ICache.computeIfAbsent` 默认实现走原子 `putIfAbsent`（竞争失败回读先写入者值）；null mapping 不写入语义与 `LocalCache`/`MapCache` 一致
- [x] `JavaxCache.computeIfAbsent` 覆写：委托 JCache 原子 `getAndPut`
- [x] `JavaxCache.refreshConfig()` 空体补文档（有明确语义的能力声明，`getConfig()==null` 为判定信号）
- [x] `OrmSessionFactoryBean.refreshConfig()`：判据改为 `sessionFactory.getGlobalCache() instanceof LocalCacheProvider`；非该类型时 `LOG.warn("nop.orm.global-cache-config-refresh-not-supported")`；plan cache 路径（恒为 `LocalCache`）保持不变
- [x] 红证测试 `TestICacheDefaults`（nop-commons，6 个用例，含故意不加锁的 `NaiveCache` 使默认实现被真实执行）
- [x] 红证测试 `TestOrmSessionFactoryBeanRefreshConfig`（nop-orm，2 个用例，`setGlobalCache` 模拟 IoC 注入路径）

Exit Criteria:

- [x] 红证记录（对旧代码运行）：
  - `TestICacheDefaults.testPutIfAbsentConcurrentSingleWinner` 失败：`expected: <1> but was: <2>`（round 16）
  - `TestICacheDefaults.testComputeIfAbsentConcurrentSingleWinner` 失败：`expected: <1> but was: <2>`（round 37）
  - `TestOrmSessionFactoryBeanRefreshConfig.testRefreshConfigAppliesGlobalCacheSizeAndTimeout` 失败：`expected: <777> but was: <1000>`
- [x] 修复后 `TestICacheDefaults` 6/6 通过、`TestOrmSessionFactoryBeanRefreshConfig` 2/2 通过
- [x] `./mvnw test -pl nop-kernel/nop-commons` 全量：**364 tests, 0 failures**
- [x] `./mvnw test -pl nop-persistence/nop-orm -am` 全量：**773 tests, 0 failures**（+ 297 in reactor, 25 skipped 为既有豁免）
- [x] `./mvnw compile -pl nop-cluster/nop-hazelcast/nop-hazelcast-cache -am` 通过（`HazelcastCache` 继承链未破坏）
- [x] 接线验证：`refreshConfig()` 驱动链为 `DefaultBeanClassIntrospection`（按 `IConfigRefreshable` 登记）→ `BeanDefinition.refreshConfigMethod.call0(...)`；测试经 `AppConfig.getConfigProvider().updateConfigValue(...)` 真实触发该链
- [x] 无静默跳过：非 `LocalCacheProvider` 分支显式 `LOG.warn`（分布式 provider 的容量由其自管，抛错会破坏合法部署，告警是正确粒度）
- [x] `ai-dev/logs/2026/10-05.md` 已更新

### Phase 2 - owner doc

Status: completed
Targets: `docs-for-ai/03-modules/nop-commons-cache-contract.md`、`docs-for-ai/INDEX.md`

- [x] 新建 `docs-for-ai/03-modules/nop-commons-cache-contract.md`：ICache 原子契约、5 类实现矩阵、`JavaxCache.getConfig()==null` 判定、`LocalCacheProvider` 热刷新、`IConfigRefreshable` 驱动链与守卫判定坑
- [x] `docs-for-ai/INDEX.md` 补路由

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 文档只描述 live 事实（无 Proposed/Current 对比，Rule #14）

## Closure Gates

- [x] 三处 live defect 全部修复且各有红绿证
- [x] `nop-commons` / `nop-orm`（含 `-am` 依赖模块）全量回归 BUILD SUCCESS
- [x] `nop-hazelcast-cache` 编译通过（继承链完整）
- [x] Anti-Hollow：红证测试在旧代码上真实失败（非恒真断言）；修复后真实通过；无空方法体作为新实现（`JavaxCache.refreshConfig` 空体为文档化的能力声明且修复前已存在）
- [x] owner doc 落盘 + INDEX 路由 + doc-links 0 错误
- [x] 独立 closure audit（fresh session）完成并在下方记录 evidence
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/39-orm-cache-contract-remediation.md --strict` 退出码 0

## Deferred But Adjudicated

（全部非缺陷关闭项与其余遗留见 `ai-dev/backlog/orm-defect-audit-followups.md`，此处不重复登记）

## Closure

Status Note: 三个已核实缺陷已修复并有红绿证；独立 closure audit 7 项全 PASS。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: fresh-session 独立审计子代理（task ses_ef40d69e7ffeEWIGUcGtIsrB3l）
- Evidence:
  - 代码语义：ICache putIfAbsent synchronized / computeIfAbsent 原子路径；JavaxCache computeIfAbsent 委托 getAndPut；OrmSessionFactoryBean instanceof 判据 + LOG.warn；均 PASS
  - 测试实跑：TestICacheDefaults 6|0、TestOrmSessionFactoryBeanRefreshConfig 2|0（-am）BUILD SUCCESS
  - 红证机理：HEAD 版无锁两步实现 / bean 字段守卫恒假，与记录的失败形态一致
  - Anti-Hollow：并发测试与修复真实耦合；JavaxCache.refreshConfig 空体为 HEAD 既有（仅补文档）
  - owner doc 与 INDEX 一致；check-doc-links --strict exit 0；nit（8 类→5 类）已随收口更正
