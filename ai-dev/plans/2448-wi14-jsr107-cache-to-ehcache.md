# 2448 WI14 JSR-107 cache replacement → Ehcache 3 jakarta

> Plan Status: completed
> Last Reviewed: 2026-10-12
> Source: ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md WI14 + Decisions（"JCache replacement = Ehcache 3 with the jakarta classifier"，human-owned 已裁决项）
> Related: 2444（BOM 政策）、2445-2447（同 roadmap 前序 WI）

## Purpose

缓存层脱离已死 JSR-107（javax.cache:cache-api 1.1.1，无 jakarta.cache 后续）：nop-commons 以 Ehcache 3（SB4 管理 3.12.0 的 jakarta classifier）适配器替代 JavaxCache 适配器，nop-hazelcast-cache 的 HazelcastCache 从 Hazelcast JCache（javax.cache 面）迁到 Hazelcast 原生 IMap；Nop 的 ICache/ISyncCache 抽象保持公共面不变。

## Current Baseline

- javax.cache 引用面（全仓 grep 实证）：nop-commons 的 `JavaxCache.java`（ISyncCache 适配器，wrap javax.cache.Cache）+ `TestICacheDefaults.java`（testJavaxCacheComputeIfAbsentSingleWinner 节 + AtomicFakeJavaxCache fake）+ nop-commons/pom.xml（cache-api versionless，SB4 管理 1.1.1）+ nop-hazelcast-cache/pom.xml（cache-api）。生产侧唯一 JavaxCache 消费方是 HazelcastCache。
- `HazelcastCache extends JavaxCache`，wrap `com.hazelcast.cache.ICache`（Hazelcast 的 JCache 实现，其类型层次依赖 javax.cache），含 stats()（ICache local stats）与 async 覆写（ICache 的 CompletionStage 异步面）；hazelcast 由 SB4 管理 5.5.0。
- ISyncCache 异步方法全部有 sync 包装 default（**13 个** async default）；ICache.computeIfAbsent 有锁化 default（注释明文：性能敏感实现应覆写）。ISyncCache 的 sync 抽象面 13 个方法无 default，须逐个实现。
- CacheStats 可用 setter：cacheGets/cachePuts/evictionCount/hitCount/missCount 等。Hazelcast 5.5.0 LocalMapStats 实测（javap）：getGetOperationCount/getPutOperationCount/getEvictionCount/getHits **无 misses getter**——迁 IMap 后 CacheStats.missCount 无法取数（见 Ruling 5）。
- ehcache 不在本地 repo（首次解析需网络）；SB4 管理 org.ehcache:ehcache @3.12.0（classifier 正交于版本管理）。
- nop-hazelcast-cache 无测试目录；ehcache/hazelcast 两面的行为测试面 = nop-commons 的 TestICacheDefaults。

## Goals

- nop-commons：新增 `EhcacheCache<K,V> implements ISyncCache<K,V>`（wrap org.ehcache.Cache，名字 ctor 传入；close 语义 = manager 持有者职责，no-op + 注释）；删除 `JavaxCache.java`；pom cache-api → `org.ehcache:ehcache` `<classifier>jakarta</classifier>`（无版本，SB4 管理）。
- nop-hazelcast-cache：HazelcastCache 重写为 wrap `com.hazelcast.map.IMap`（原生 API），stats() 经 localMapStats，async 覆写映射 IMap 异步面；pom 删 cache-api。
- TestICacheDefaults：JavaxCache 单胜者节替换为 EhcacheCache 真实行为测试（真实 CacheManager + on-heap cache），接口 default 语义节（NaiveCache）保留。
- 全仓 javax.cache / com.hazelcast.cache 活引用归零。

## Non-Goals

- 不动 ICache/ISyncCache/LocalCache/CaffeineCache/MapCache 等其余缓存面（Nop 抽象是公共面，roadmap 明文保留）。
- 不引入 ehcache 到 nop-hazelcast（Hazelcast 用原生 IMap，不混第二实现）。
- 不做 GraalVM native-image 配置（ehcache/hazelcast 的 native 面归 native 治理另案）。

## Rulings

1. **EhcacheCache 语义面（审查 jar 级实证修正）**：org.ehcache.Cache 3.12.0 方法集 = get/put/containsKey/remove/remove(k,v)/getAll/putAll/removeAll/clear/putIfAbsent(**返回前值 V 非 boolean**)/replace×2/Iterable——**无 getAndPut、无原子 computeIfAbsent**。故：(a) putIfAbsent 映射 `cache.putIfAbsent(k,v)==null`；(b) getAndSet 继承 ICache 竞态 default（get+put 非原子）——**已裁定语义损失**：现生产代码 ICache.getAndSet 零调用方，风险可控；(c) computeIfAbsent 继承 ICache 锁化 default；(d) refresh 按先例映射 remove。原 JavaxCache 的 getAndPut 单胜者覆写随 JSR-107 消亡。
1b. **HazelcastCache 语义面（IMap 实测映射）**：13 个 sync 抽象逐个实现——getIfPresent(get)、getAllPresent(getAll)、put、putAll、remove、removeAll(keys)（IMap 无 removeAll(Collection)，逐 key remove）、clear、refresh(remove)、forEachEntry(IMap Iterable/entrySet)、getName(ctor cacheName)、estimatedSize(size)、getConfig(null)、removeIfMatch(remove(k,v))、refreshConfig(no-op)；default 覆写 3 个 = containsKey（IMap 廉价实现）、getAndSet（**IMap.put 返回旧值**，原子）、computeIfAbsent（**IMap.computeIfAbsent 服务端原子**）。async 真异步覆写仅 5 个：getAsync、putAsync(+thenApply→null)、putAllAsync、getAndSetAsync(**→putAsync**，同返回旧值)、removeAsync(+thenApply→null)；putIfAbsentAsync/removeIfMatchAsync/clearAsync **走 sync default（显式裁决的异步能力降级**——IMap 无对应异步方法）；computeIfAbsentAsync 不覆写（ISyncCache sync 包装 default——IMap 无该方法，经覆写后的 sync computeIfAbsent 路由服务端原子）。
2. **HazelcastCache 目标 API 选 IMap 而非 Ehcache**：hazelcast 模块的分布式缓存本就由 Hazelcast 集群承载，IMap 是其原生标准面（JCache 是非标准包装）；roadmap Decisions 的 Ehcache 3 指的是替换 nop-commons 的 JCache 适配层（供非 hazelcast 场景使用），不是要求 hazelcast 换后端。
3. **classifier jakarta + 依赖收敛（审查 dependency:tree 实证）**：ehcache jakarta classifier 与主构件共享主 pom，传递 javax.cache:cache-api（compile）+ jaxb-runtime 家族（runtime）——与"脱离 JSR-107"目标相悖。处置：nop-commons 的 ehcache 声明 `<optional>true</optional>`（EhcacheCache 现零生产调用方，不向下游铺开 +8 jar；本模块 test classpath 仍可用）+ `<exclusions>` 掉 javax.cache:cache-api（已实证安全：ehcache 核心 1423 个非 jsr107 类对 javax/jakarta.cache 零引用，编程式路径不触碰 jsr107 包）。使用者需 EhcacheCache 时自行声明 ehcache 依赖（写入 owner doc）。
4. **close 语义**：EhcacheCache.close() no-op（org.ehcache.Cache 无 close；生命周期归 CacheManager 持有者），注释声明；JavaxCache 的 close 语义不保留。
5. **HazelcastCache stats 契约损失显式裁决**：LocalMapStats 无 misses getter——CacheStats.missCount 迁移后不填（保持 0），eviction 用 getEvictionCount、hits 用 getHits、cacheGets 用 getGetOperationCount、cachePuts 用 getPutOperationCount；NearCacheStats 合并不做（引入第二统计面，超出本 WI）。

## Execution Plan

### Phase 1 - 适配器迁移

Status: completed
Targets: nop-kernel/nop-commons、nop-cluster/nop-hazelcast/nop-hazelcast-cache

- Item Types: `Fix`（死标准迁移：替换 JSR-107 依赖面即本 WI defect 面，按 guide Rule 15 归 Fix）

- [x] nop-commons：新增 EhcacheCache（按 Ruling 1 映射全实现：13 sync 抽象 + computeIfAbsent/getAndSet 走 default + close no-op + getName(ctor)）；删除 JavaxCache.java
- [x] nop-commons pom：javax.cache:cache-api → org.ehcache:ehcache（无版本 + classifier jakarta + optional=true + exclusions 掉 javax.cache:cache-api，Ruling 3）
- [x] nop-hazelcast-cache：HazelcastCache 重写 wrap IMap（按 Ruling 1b：13 个 sync 抽象逐个实现、真异步覆写 5 个（computeIfAbsentAsync 不覆写走 default）、putIfAbsentAsync/removeIfMatchAsync/clearAsync 显式降级 sync default、stats 按 Ruling 5）；pom 删 cache-api
- [x] TestICacheDefaults：JavaxCache 节 → EhcacheCache 真实 CacheManager 行为测试（CacheManagerBuilder on-heap，验证 get/put/putIfAbsent(返回前值语义)/remove/getAndSet 顺序语义/forEachEntry/close；manager.close() 线程卫生），AtomicFakeJavaxCache 相应替换

Exit Criteria:

- [x] 全仓 `javax.cache` / `com.hazelcast.cache` / `JavaxCache` 活引用零（java+pom；nop-commons 自身 test classpath 经 ehcache 传递的残留不在违规面，但 exclusions 后 dependency:tree 亦应零 cache-api——抽查留痕）
- [x] `./mvnw test -pl nop-kernel/nop-commons -am -Dtest=TestICache*` 6/6 全绿 + `./mvnw test-compile -pl nop-cluster/nop-hazelcast/nop-hazelcast-cache -am` BUILD SUCCESS（`_tmp/wi14-gateb.log`/`_tmp/wi14-hazelcast-tc.log`；执行期修复：HazelcastCache 显式 implements AutoCloseable——旧 JavaxCache 声明而 ISyncCache 无）
- [x] owner doc 更新：docs-for-ai/03-modules/nop-commons-cache-contract.md 实现矩阵 JavaxCache/HazelcastCache 两行改写（EhcacheCache optional 使用方式 + HazelcastCache IMap 面 + 原子性语义：computeIfAbsent default（Ehcache）/IMap 服务端原子（Hazelcast）、getAndSet 竞态（Ehcache）/原子（IMap.put））；核对 docs-for-ai/INDEX.md:135 路由行
- [x] **新功能测试规则（Rule 25）**：EhcacheCache 新类由 TestICacheDefaults 的 EhcacheCache 节覆盖（get/put/putIfAbsent/remove/getAndSet 顺序语义/forEachEntry 六行为点）
- [x] `ai-dev/logs/2026/10-12.md` WI14 条目

### Phase 2 - 验证门与收口

Status: completed
Targets: 全仓构建、roadmap、plan

- Item Types: `Proof`

- [x] Gate A：`./mvnw clean install -DskipTests -T 1C -fae`（容忍面 = chronic gen-aop 家族签名归因）；ehcache jakarta classifier 首次解析需网络
- [x] Gate B：`./mvnw test -pl nop-kernel/nop-commons -am -Dtest='TestICache*'`（缓存面回归）
- [x] roadmap WI14 `todo` → `done`
- [x] 白名单提交（nop-commons pom+2 java+1 test、nop-hazelcast-cache pom+1 java、docs-for-ai/03-modules/nop-commons-cache-contract.md、docs-for-ai/INDEX.md、plan 2448、log、roadmap）
- [x] 独立子 agent closure audit
- [x] 三门：check-plan-checklist --strict / scan-hollow-implementations --module nop-kernel/nop-commons --severity high / check-doc-links --strict
- [x] 文本一致性核对后置 `Plan Status: completed`

Exit Criteria:

- [x] Gate A/B 通过且证据可追溯；commit hash 记录 Closure；审计证据写入；三门留痕

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（死 JSR-107 依赖 = 本 WI defect 面，已移除）
- [x] 所有 in-scope confirmed contract drifts 已收敛（ICache 公共面不变、实现面无 javax.cache）
- [x] 行为/契约结果已达成：EhcacheCache 行为测试绿 + HazelcastCache IMap 面编译通过
- [x] 必要 focused verification 已完成（Gate B）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（docs-for-ai cache-contract 实现矩阵 + INDEX.md 路由行已更新，见 Phase 1 Exit Criteria）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：EhcacheCache 真实构造并跑通行为测试（非空壳），HazelcastCache 非 stub
- [x] `./mvnw clean install -DskipTests -T 1C -fae` 全绿（chronic 容忍面内）
- [x] Gate B focused tests 全绿
- [x] 代码规范：import 分组、注释密度与仓内惯例一致

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- nop-hazelcast-cache 无测试目录（本次仅编译面验证）：out-of-scope improvement，Hazelcast 集群行为测试需真实实例，归 cluster 治理另案。

## Closure

Status Note: WI14 交付面全部落地并经独立 closure audit APPROVE（9/9 实质面 PASS，0 Blocker）。EhcacheCache（optional + exclusion 死标准）替代删除 JavaxCache、HazelcastCache 迁原生 IMap（javap 5.5.0 实证 5 真异步 + 3 显式降级）、行为测试 6/6 绿、owner doc（cache-contract + INDEX）同步。执行期一处接口面修复（AutoCloseable 显式声明）与两处 Ruling 文本修正（R2 虚构 API 清洗）均经审计确认。
Completed: 2026-10-12
Commit: e82c9c4817

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_31cc8490-6a89-49d3-b057-e7819861674d（fresh session）
- Evidence:
  - 9 项验证全 PASS（源码活引用零 + EhcacheCache 逐点一致 / HazelcastCache javap 复证 5+3+AutoCloseable+Ruling 5 stats / pom 三项 / 审计独立复跑 TestICache 6 绿 + dependency:tree 恰一行 ehcache optional 且 cache-api 零 / Gate 证据（hazelcast-tc 截断已由审计补证 _tmp/wi14-audit-hazelcast-tc.log）/ owner doc 抽查与 live 代码一致 / 文本一致性 / Deferred 诚实性（getAndSet 竞态裁决生产零调用方 grep 实证）/ 提交恰 10 文件零混入）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2448-wi14-jsr107-cache-to-ehcache.md --strict` 退出码 0（回填后复跑）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-commons --severity high` 退出码 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - Anti-Hollow：真实 CacheManager 行为测试 6/6 绿（非空壳）
  - Deferred 项分类检查：hazelcast-cache 无测试目录（follow-up 显式）、getAndSet 竞态（显式裁决）——无 in-scope live defect 被降级

Follow-up:

- 见 Non-Blocking Follow-ups
