# C7 深挖第 3 轮（收敛轮）— nop-persistence-peri（WI 2.5）

## 深挖范围与方法

本轮为收敛轮，触发面 = C7-014（pubsub 断线重连丢订阅）与 C7-013（墙钟令牌桶）的修复回归面 + C7-015（LettuceLock 无 fencing）同族锁面。按任务书三条线定向收敛：

| 复核线 | 触达文件 | 结论 |
|---|---|---|
| ① 全模块「隐式依赖连接永不重连/会话状态」站点过筛 | `LettuceRedisConnectionProvider.java`（全文）、`AbstractLettuceOperations.java`、`LettuceMessageService.java`（全文）、`LettuceQueue.java`（全文）、`LettuceCounter.java`（全文）、`LettuceHelper.java`、`LettuceExecutor.java`、grep 全部 impl 类实例字段 | **除 pub/sub 连接（C7-014 已立案）外零站点**：其余全部 lettuce 操作类仅持 `final String key/prefix`（LettuceSessionStore:22、LettuceListOperations:21、LettuceSetOperations:20、LettuceZSetOperations:21、LettuceRanking:20、LettuceHashOperations:25），经 `RoundRobinSupplier` 共享连接做无状态单命令，重连后由 lettuce 连接初始化重放 AUTH/SELECT/CLIENT NAME 握手自愈；grep `multi(|exec(|watch(|blpop|brpop` 全模块零命中——无事务/阻塞命令跨重连面 |
| ② 墙钟单调依赖复扫（quiesce 同族） | grep `currentTimeMillis|nanoTime` nosql + orm-migration 两模块全集 | 进入行为判定的客户端墙钟**全模块恰 2 处**：`LettuceRateLimiter.java:56`（C7-013 已立案）、`IncrementalLagProbe.java:60-63`（C7-008 已立案）；其余命中（MigrationJobStateStore:102、OrmMigrationOrchestrator:142 的状态行时间戳）为簿记字段不进判定（第 2 轮裁定维持）。无第三处，收敛 |
| ③ C7-015 同族分布式原语面 | `LettuceLock.java`（全文）、`LettuceQueue.java`、`LettuceCounter.java`、`LettuceRateLimiter.java`（全文）、`rate_limit.lua`、`RedisScripts` 六个 Lua | 模块内**唯一声明跨进程持有态的原语就是 LettuceLock**（本地 `volatile lockValue` 回答 held）：Queue 为裸 LPUSH/LPOP/LINDEX/LLEN 无租约/可见性语义；Counter 为服务端 INCRBY 原子族；hash/string CAS 族（REMOVE_IF_MATCH/PUT_IF_ABSENT_OR_MATCH/hash 变体）为服务端 Lua 原子、无客户端持有态；session 族 TTL 为服务端时钟。同族面闭合，无漏网兄弟 |
| ④ C7-013/C7-014 修复方案行为面预演 | `LettuceRateLimiter.java`（全文）、`rate_limit.lua`（全文）、`LettuceExecutor.java:21-46`（NoScript 回退）、`LettucePubSubService.java` 生命周期路径 | 修复回归面登记于「回归面提示」节（脚本换 TIME 的复制语义、getAvailableTokens 读路径语义、重订阅与 C3-004 锁面的竞次、cluster 拓扑事件面） |

## 新增发现

（本轮零新增。三条触发线复扫均无新 P0/P1 实证：重连依赖面唯一站点 pub/sub 已由 C7-014 立案；判定性墙钟恰 2 处均已立案；同族锁面唯一站点已立案。）

## 回归面提示

（修复语义会改变的站点清单——供 C7-013/C7-014 修复方案设计，不立案为缺陷）

### C7-014 修复（重连后按 subscriptions 表重发 SUBSCRIBE）

| # | 站点 | 回归面 |
|---|---|---|
| 1 | 重订阅执行线程 | 重连事件在 lettuce IO 线程回调；重发 `sync().subscribe` 若从订阅管理线程发起，与 `subscribe/cancel`（C3-004 的非对称锁面）竞次会再生孤儿 entry——C3-004 的锁收敛必须与重订阅同批落地，且重订阅应复用同一 monitor |
| 2 | `PubSubSubscription.cancel:189-200` × 重订阅 | 重连恢复循环遍历 subscriptions 时若恰逢 cancel 的 isEmpty→unsubscribe→remove 序列，需以 entry 级互斥保证「恢复时不复活已取消的 topic」（否则取消语义被重连破坏） |
| 3 | `doStop`（C7-009 修复后清 subscriptions） | stop 清空订阅表后，重连监听器的恢复循环必须以表内容为准——两修复的清理/恢复责任需在同一状态源（subscriptions map）上收敛，避免「stop 已清、重连又按旧快照重订」 |
| 4 | cluster 形态 `connectPubSub`（LettuceRedisConnectionProvider:140-146） | cluster 拓扑变化（master failover）与单机重连是两条事件路径（TopologyRefreshEvent vs ReconnectEvent），恢复钩子需两点注册；仅挂 ReconnectListener 会漏 failover 面 |
| 5 | 恢复窗口语义 | 重连成功到 SUBSCRIBE ack 之间到达的 publish 仍丢失（Redis 不重放）——建议中的 NUMSUB 对账兜底是该窗口的唯一检测手段，修复说明应显式声明此残余窗口而非声称"恢复订阅即无损" |

### C7-013 修复（令牌桶时钟改服务端 TIME / 退化固定窗口）

| # | 站点 | 回归面 |
|---|---|---|
| 1 | `rate_limit.lua` 脚本复制语义 | 脚本内 `redis.call('TIME')` 后执行写命令：Redis ≥7 默认 effect replication 已解除限制；Redis <7 需 `redis.replicate_commands()` 前缀——宿主 Redis 版本下限需在脚本或文档声明 |
| 2 | 脚本 digest 滚动 | 改脚本 → 新 digest；evalsha 未命中走 `LettuceExecutor` 的 NoScript→eval 回退（LettuceExecutor.java:28-30）自愈，**混合舰队**（部分节点缓存旧脚本）亦安全（新文本 eval 后按新 digest 缓存）——无需迁移步骤，但 `getAvailableTokens` 期间读到的 tokens 键值可能来自新旧两版脚本，格式需兼容（见 #3） |
| 3 | `getAvailableTokensAsync:100-117` 读路径 | 现状即不补桶（裸读 tokens 键，上次脚本写入的余量）——换 TIME 后该语义不变；若改为「跑一遍脚本」则会因脚本内 setex 产生写副作用（观测操作改变状态），禁止；改固定窗口 INCR 方案则 tokens 值恒整数，`parseTokenCount:119-125` 的浮点解析分支变死代码（可留作兼容） |
| 4 | key 拓扑 | `{key}` hash tag 双键同槽约束（LettuceRateLimiter:40-48 注释 R1.8/P1-2.4-001）在任何重写中必须保留，否则 cluster 下 CROSSSLOT 回归 |
| 5 | TTL 派生 | 现状 tokens/timestamp 键 TTL = `fill_time×2`（脚本 :7），是回拨冻结后的唯一隐性自愈路径；改固定窗口后 TTL 语义换为窗口长度，C7-013「现状」节描述的自愈路径描述需随修更新 |
| 6 | `permits > capacity` 边界 | 现状：requested>capacity 恒拒绝且不扣token（脚本 allowed=false 分支）；构造期 Guard 只校验 rate/capacity 正数，permits 无校验——两种修复方案均保持同语义即可，属契约声明项非并发缺陷 |

## 维度集外线索（overlay）

（编号续接 nop-persistence-peri-CL-026）

### [nop-persistence-peri-CL-026] LettuceRedisConnectionProvider.doStop 不关闭 connectionSupplier，重启路径依赖 event-loop 关闭兜底（C6/C9）

- **位置**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:127-138`（doStop）；`nop-kernel .../commons/concurrent/RoundRobinSupplier.java:95-103`（AutoCloseable.close 契约未被调用方使用）
- **线索**: `doStop` 依次 `clusterClient/standaloneClient.shutdown()` + `clientResources.shutdown().awaitUninterruptibly(10, TimeUnit.SECONDS)`，但从不 `connectionSupplier.close()`。池内全部共享连接的显式关闭契约（`RoundRobinSupplier implements AutoCloseable`，close() 逐个 safeCloseObject）全模块零调用——连接消亡完全依赖 clientResources 关闭时 event loop group 收缩连带断开传输。stop→start 循环（LifeCycleSupport 声明能力）中 doStart 以新 RoundRobinSupplier 覆盖旧字段，旧 supplier 及其连接对象未经 close 即被替换；`clientResources.shutdown` 限时 10s 放弃后残余 socket 的收敛无显式保证。同族对照：resize 缩容路径（RoundRobinSupplier.resize:83-86）有显式 safeClose，stop 路径独缺——F8「清理不对称」的轻症形态。运行期无实证泄漏（event-loop 关闭兜底断 fd），故登记线索不立案。建议 doStop 在 shutdown client 前先 `connectionSupplier.close()` 并置空字段，与 `clientResources = null`（L136）的清理对称性对齐。
- **归属维度**: C6（资源生命周期）为主，关联 C9（生命周期重启语义）。

## 继续判定

**输出终止信号**。依据：本轮零新增发现（无新 P0/P1），三条触发线——重连依赖站点、判定性墙钟、同族锁面——经全量 grep + 逐文件精读确认唯一站点均已在前两轮立案；C7-013/C7-014 的修复回归面（上表 11 项）已登记，修复落地后的复核属修复验证任务而非继续深挖。C7 维度三轮累计 15 条发现（P1×6：C7-001/002/003/004/005/014；P2×8：C7-006/007/008/009/010/011/013/015；P3×1：C7-012）+ CL-020/021/022/026，本维度收敛。

## 复核结论（WI 2.5 C7 独立复核）

本卷为零新增收敛轮，复核确认：① 三条收敛线（重连依赖唯一站点=pub/sub、判定性墙钟恰 2 处、跨进程持有态原语唯一=LettuceLock）的阴性结论经独立抽查成立，无漏网新面；② 回归面提示 11 项（C7-014 修复 5 项、C7-013 修复 6 项）与同单元 C3-004 锁面、C7-009 doStop 修复的联动关系登记充分，已并入 C7-014 复核状态行作为修复设计输入；③ CL-026（doStop 不关 connectionSupplier）登记为线索不立案的口径复核成立（event-loop 关闭兜底，运行期无实证泄漏），与 C7-009 的 doStop 状态复位修复同文件不同面，随 C7-009 工单顺带收口。C7 维度 15 条发现的逐条判定与最终保留项集中见 C7-long-running.md「维度复核结论」（复核后 P1×5/P2×9/P3×1，C7-005 降档 P2，C7-011/012 标已知未修复）。
