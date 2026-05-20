# Nop NoSQL 业务模式层实现计划

> Plan Status: completed
> Last Reviewed: 2026-05-20
> Source: `ai-dev/design/nop-nosql/architecture.md` §4 业务模式层设计（经 3 轮 Momus 审查通过）

## Goals

在 nop-nosql 模块中实现设计文档定义的 6 个业务模式接口及其 Lettuce 驱动实现，使应用代码可以通过队列、锁、限流器、排行榜、计数器、会话存储等业务概念与 NoSQL 交互，而非直接操作 Redis 数据结构。

## Non-Goals

- 新增 Redis 驱动实现（如 Jedis 适配器）—— 架构已支持，但不在本计划 scope 内
- Reactive / 响应式 API —— 已在设计文档中明确拒绝
- Sentinel / Cluster 连通 —— 当前仅支持单机直连，连通工作单独计划
- 可重入锁 / 看门狗续期 —— 首版不支持，已记录为拒绝方案
- 阻塞式队列操作（BLPOP/BRPOP）—— 已在设计文档中明确拒绝
- IoC beans.xml 注册 —— 由使用方负责，不在本模块 scope 内
- LettuceExecutor eval 降级 —— 单独优化项，不在本计划 scope 内

## Current Baseline

### 已有资源

- **驱动层**：LettuceRedisConnectionProvider（连接池化）、PrefixTextCodec（序列化）、LettuceExecutor（Lua 执行）
- **原语层**：INosqlKeyValueOperations 异步实现基本完整（LettuceMessageService）；INosqlHashOperations/INosqlListOperations 接口已定义但返回 null；INosqlSetOperations/INosqlZSetOperations 空接口
- **Lua 脚本**：5 个已存在，其中 remove_if_match.lua 已被引用，其余 4 个（rate_limit.lua、get_and_expire.lua、get_and_set.lua、put_if_absent_or_match.lua）已编写完成等待接入
- **测试**：TestLettuceNosqlService（Testcontainers 骨架，所有测试方法为空）

### 已知债务

- LettuceMessageService 中 3 个同步方法为桩（putIfAbsent return false、getAndSet return null、removeIfMatch return false）
- forEachEntry/forEachEntryAsync 未实现（空体/return null，影响 NosqlCache 的遍历功能，但业务模式层不依赖）
- putIfAbsentOrMatchExAsync 返回 null（Lua 脚本已存在）
- forEachEntryAsync 返回 null
- 无 IoC beans.xml

## Phases

### Phase 1: 接口定义 + 数据结构原语补齐

> Status: completed

在 nop-nosql-core 中定义 6 个业务模式接口，同时补齐 INosqlSetOperations 和 INosqlZSetOperations 的方法定义（业务模式的 Ranking 依赖 ZSet，Queue 依赖 List）。

**Exit Criteria**:
- [ ] 6 个业务模式接口定义完成：INosqlQueue、INosqlLock、INosqlRateLimiter、INosqlRanking、INosqlCounter、INosqlSessionStore
- [ ] 每个接口包含同步 + 异步双轨方法（Async 后缀），与原语层约定一致
- [ ] INosqlSetOperations 补齐方法定义（add/remove/removeAll/contains/containsAll/size/members/randomMember/pop 及其 Async 版本）
- [ ] INosqlZSetOperations 补齐方法定义
- [ ] RateLimiterConfig 数据类定义（rate、capacity 字段，含 equals/hashCode）
- [ ] RankingEntry 数据类定义（member、score、rank 字段）
- [ ] RateLimitResult 数据类定义（allowed、remainingTokens 字段）
- [ ] INosqlService 接口新增 6 个工厂方法：queue()、lock()、rateLimiter()、ranking()、counter()、sessionStore()
- [ ] nop-nosql-core 无驱动依赖（pom.xml 不含 lettuce/jedis）
- [ ] `mvn compile -pl nop-persistence/nop-nosql/nop-nosql-core` 通过
- [ ] No owner-doc update required: 接口尚无使用者，Phase 6 统一更新 docs-for-ai

### Phase 2: 原语层实现补齐

> Status: completed

在 LettuceMessageService 中实现 INosqlListOperations、INosqlSetOperations、INosqlZSetOperations，为 Phase 3 的业务模式提供底层支撑。

**Exit Criteria**:
- [ ] INosqlListOperations 的 Lettuce 实现完成（getSize/clear/add/addAll/getRange/trim/leftPop/rightPop/leftPopMulti/forEachItem 及其 Async 版本）
- [ ] INosqlSetOperations 的 Lettuce 实现完成（add/remove/removeAll/contains/containsAll/size/members/randomMember/pop 及其 Async 版本）
- [ ] INosqlZSetOperations 的 Lettuce 实现完成（至少包含 add/remove/score/rank/revRank/revRange/card/incrementScore）
- [ ] INosqlHashOperations 实现补齐（当前 hashOps() 返回 null）
- [ ] LettuceMessageService 的 hashOps/listOps/setOps/zSetOps 不再返回 null
- [ ] 修复 3 个同步桩方法：putIfAbsent、getAndSet、removeIfMatch 使用对应异步方法的 join() 实现
- [ ] `mvn compile -pl nop-persistence/nop-nosql/nop-nosql-lettuce` 通过
- [ ] No owner-doc update required: 原语层实现变更，Phase 6 统一更新

### Phase 3: 业务模式实现 — P0（Lock、Counter、Queue）

> Status: completed

优先实现最常用的 3 个业务模式。Lock 和 Counter 有现成 Lua 脚本和原语支撑，Queue 直接基于 List。

**Exit Criteria**:
- [ ] INosqlLock 的 Lettuce 实现：
  - [ ] tryLock：SET key uuid NX PX leaseTime
  - [ ] unlock：使用 remove_if_match.lua（CAS 释放）
  - [ ] isHeld：本地状态跟踪
  - [ ] 锁值使用 UUID 防止误解锁
- [ ] INosqlCounter 的 Lettuce 实现：
  - [ ] increment：INCRBY
  - [ ] get：GET
  - [ ] getAndIncrement：Lua 脚本（GET + INCRBY 原子执行，返回旧值）
  - [ ] reset：SET
  - [ ] getAndReset：GETSET（复用 get_and_set.lua 或直接使用 GETSET 命令）
- [ ] INosqlQueue 的 Lettuce 实现：
  - [ ] enqueue/enqueueBatch：RPUSH
  - [ ] dequeue：LPOP
  - [ ] dequeueBatch：LPOP count（Redis 6.2+）或 Lua 降级
  - [ ] peek：LINDEX 0
  - [ ] size：LLEN
  - [ ] clear：DEL
- [ ] INosqlService 的 queue()/lock()/counter() 工厂方法返回可用的实现对象
- [ ] `mvn compile -pl nop-persistence/nop-nosql` 通过
- [ ] No owner-doc update required: Phase 6 统一更新

### Phase 4: 业务模式实现 — P1（RateLimiter、Ranking）

> Status: completed

接入已有的 rate_limit.lua，实现 Ranking（基于 ZSet）。

**Exit Criteria**:
- [ ] INosqlRateLimiter 的 Lettuce 实现：
  - [ ] tryAcquire：使用 rate_limit.lua
  - [ ] getAvailableTokens：GET tokens_key（只读不消费）
  - [ ] RateLimiterConfig 实现 equals/hashCode
- [ ] INosqlRanking 的 Lettuce 实现：
  - [ ] add：ZADD
  - [ ] incrementScore：ZINCRBY
  - [ ] getRank：ZREVRANK
  - [ ] getScore：ZSCORE
  - [ ] getTopN：ZREVRANGE + ZREVRANK
  - [ ] getAround：ZREVRANK → ZREVRANGE
  - [ ] size：ZCARD
  - [ ] remove：ZREM
- [ ] INosqlService 的 rateLimiter()/ranking() 工厂方法返回可用实现
- [ ] `mvn compile -pl nop-persistence/nop-nosql` 通过
- [ ] No owner-doc update required: Phase 6 统一更新

### Phase 5: 业务模式实现 — P2（SessionStore）

> Status: completed

实现 SessionStore（基于 Hash + TTL）。

**Exit Criteria**:
- [ ] INosqlSessionStore 的 Lettuce 实现：
  - [ ] get：HGETALL
  - [ ] getField：HGET
  - [ ] set：HMSET + PEXPIRE（标注非原子性，建议 Lua 或 Pipeline）
  - [ ] setField：HSET
  - [ ] touch：PEXPIRE（或使用 get_and_expire.lua 的反向）
  - [ ] remove：DEL
  - [ ] exists：EXISTS
  - [ ] key 格式：`{prefix}:{sessionId}`
- [ ] INosqlService 的 sessionStore() 工厂方法返回可用实现
- [ ] `mvn compile -pl nop-persistence/nop-nosql` 通过
- [ ] No owner-doc update required: Phase 6 统一更新

### Phase 6: 测试 + 文档更新

> Status: completed

为所有新增实现编写测试，更新文档。

测试环境策略：使用 Testcontainers（Redis 容器）+ `@DisabledIf` 条件判断。无 Docker 的环境自动跳过测试，不阻塞构建。CI 环境配 Docker 跑完整测试。

**Exit Criteria**:
- [ ] 测试基类或工具方法实现 Docker 可用性检测（`DockerClientFactory.instance().client()` 探测），无 Docker 时 `@DisabledIf` 跳过
- [ ] TestLettuceNosqlService 使用 Testcontainers 启动 `redis:7-alpine` 容器
- [ ] Lock 测试：获取/释放/超时自动释放/CAS 防误解锁
- [ ] Counter 测试：递增/递减/并发原子性/getAndReset
- [ ] Queue 测试：入队/出队/批量/空队列出队返回 null
- [ ] RateLimiter 测试：令牌消耗/桶满/桶空拒绝/令牌补充
- [ ] Ranking 测试：添加/增量/排名/TopN/Around/删除
- [ ] SessionStore 测试：创建/读取/更新字段/TTL 刷新/销毁
- [ ] putIfAbsentOrMatchExAsync 接入 put_if_absent_or_match.lua（修复当前 return null）
- [ ] 修改 `ai-dev/design/nop-nosql/architecture.md` §6 实现状态表，反映所有已完成项
- [ ] 修改 `ai-dev/design/nop-nosql/README.md` 模块成熟度说明
- [ ] `mvn test -pl nop-persistence/nop-nosql` 通过（新增测试全部 pass）

## Closure Gates

计划关闭前必须满足：

- [ ] 6 个业务模式接口 + Lettuce 实现全部编译通过
- [ ] INosqlService 的 6 个工厂方法可用
- [ ] 原语层（Hash/List/Set/ZSet）不再返回 null
- [ ] 所有新增测试 pass（Testcontainers，无 Docker 环境自动跳过）
- [ ] 已知债务中与业务模式相关的项已处理（3 个同步桩、putIfAbsentOrMatchExAsync）
- [ ] design 文档的实现状态表已更新
- [ ] 独立 closure audit 完成

## Deferred But Adjudicated

| 项目 | 分类 | Why Not Blocking Closure |
|------|------|--------------------------|
| LettuceExecutor eval 降级 | optimization candidate | 当前 evalsha 在正常流程中工作，仅脚本缓存丢失时失败。生产环境通常预热脚本缓存。可单独优化 |
| Sentinel/Cluster 连通 | watch-only residual | 字段已存在，连接构建缺口明确，不影响单机模式使用。单独计划 |
| useSsl 连通 | watch-only residual | 配置字段已存在，连接构建缺口明确，不影响非 TLS 场景。单独计划 |
| IoC beans.xml | out-of-scope improvement | 由使用方负责注册，本模块不持有 IoC 职责 |
| forEachEntry/forEachEntryAsync | optimization candidate | SCAN 实现有性能考量，首版可暂不实现，业务模式层不依赖此方法。注意：NosqlCache 的 forEachEntry 委托到此方法，延期意味着 NosqlCache 的遍历功能也是空壳，但当前无已知使用方依赖此功能 |
| 可重入锁 / 看门狗续期 | out-of-scope improvement | 已在设计文档 §4.2 拒绝方案中记录，按需增加 |
