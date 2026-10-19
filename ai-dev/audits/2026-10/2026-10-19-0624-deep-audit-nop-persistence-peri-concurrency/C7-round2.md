# C7 深挖第 2 轮 — nop-persistence-peri（WI 2.5）

## 深挖范围与方法

第 1 轮 12 条（C7-001..012）集中在编排状态机、lag 门禁与 lettuce 生命周期。本轮按任务书四条未扫面深挖：

| 深挖线 | 触达文件 | 结论 |
|---|---|---|
| ① 状态存储的长期演化（状态行无限增长？历史任务清理面） | `MigrationJobStateStore.java`（全文）、`MigrationHistoryManager.java` | **阴性**：`nop_orm_migration_job` 仅 insert/update（无 delete API），但增长界 = 迁移作业次数（每作业 1 行，手工触发），非运行期自动增长；`loadAll():129-135` 全量列表 API 在生产代码零调用方（grep 复现，仅测试可达），无运行期无界读取面；`nop_db_migration_history` 由 UPDATE 覆盖式记账 + rollback 删除，第 1 轮 G6"有界健康"裁定维持 |
| ② 写门长期开启/关闭漂移 | `OrmMigrationOrchestrator.java:285,306-308`、`OrmMigrationWriteGate.java`（证据锚点） | **已覆盖确认**：开门/关门全调用点清单（completeSwitch:285、abort:307-308、runVerify:215 检查、confirmSwitch:263 检查）与集群不对称/无保持机制两形态均已被第 1 轮 C7-004/C1-010 立案，无第三种漂移形态 |
| ③ 时钟依赖（超时/静默期判定的时钟源） | 六根 `currentTimeMillis/nanoTime` 全量 grep + `LettuceRateLimiter.java`、`nop/nosql-core/src/main/resources/nop/redis/rate_limit.lua` | 除第 1 轮 C7-008 已立案的 lag 静默窗墙钟外，发现**限流令牌桶以客户端墙钟跨节点共享**（C7-013）；其余命中（MigrationExecutor/Engine/HistoryManager 的 executionTime 与 installed_on、MigrationJobStateStore 的 update_time）均为簿记时间戳，不进入任何判定，排除 |
| ④ Lettuce 重连退避与订阅恢复的长期行为 | `LettuceRedisConnectionProvider.java:245-285`、`LettucePubSubService.java`（全文）+ lettuce-core 7.5.2 字节码核对（`PubSubEndpoint`/`ConnectionWatchdog`/`PubSubCommandHandler` javap 反编译） | **发现订阅恢复缺失**（C7-014）：ClientOptions `autoReconnect(true)`（`:247,261`）只恢复 TCP 传输；`PubSubEndpoint.notifyChannelActive` 字节码仅复位 `subscribeWritten` 标志，`ConnectionWatchdog` 无任何 pub/sub 重订阅引用——Redis 侧订阅状态在重连后丢失且模块无补偿。另发现 LettuceLock 租约语义缺口（C7-015） |

## 新增发现

### [C7-013] 限流令牌桶以客户端墙钟计算跨节点共享的补桶量：时钟回拨冻结全体节点的令牌补给，时钟超前节点使其余节点饥饿

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRateLimiter.java:53-64`（`CoreMetrics.currentTimeMillis()` 注入 Lua）；脚本 `nop-persistence/nop-nosql/nop-nosql-core/src/main/resources/nop/redis/rate_limit.lua`（`local delta = math.max(0, now-last_refreshed)` 钳位 + `setex timestamp_key ... now`）
- **证据片段**:
  ```java
  // LettuceRateLimiter.java:56 —— 客户端墙钟秒级截断后作为补桶时钟
  long now = CoreMetrics.currentTimeMillis() / 1000;
  return LettuceExecutor.evalScript(async(), RedisScripts.RATE_LIMIT,
                  ScriptOutputType.MULTI,
                  new String[]{tokensKey, timestampKey},
                  new Object[]{String.valueOf(config.getRate()),
                          String.valueOf(config.getCapacity()), String.valueOf(now), ...})
  // rate_limit.lua —— delta 负值钳 0：now < last_refreshed 期间零补桶
  local delta = math.max(0, now-last_refreshed)
  local filled_tokens = math.min(capacity, last_tokens+(delta*rate))
  ...
  redis.call("setex", timestamp_key, ttl, now)   -- 任意节点把自身墙钟写入共享基准
  ```
- **严重程度**: P2（有明确触发链的条件必现：NTP 步进校时/多节点时钟偏斜为常态运维事件；后果为限流面可用性，不损数据）
- **并发场景**: 多应用节点（共享同一 Redis 的令牌键 `{key}:tokens`/`{key}:timestamp`）的请求线程并发 tryAcquire；每一节点的**本地墙钟**都被写入共享 timestamp 基准——时钟即分布式状态，但无任何同步机制
- **触发时序**: 链 A（回拨冻结）：T1 节点 A 在 now=T 写入 timestamp_key=T；T2 NTP 校时使全节点时钟步进回拨 Δ 分钟 → 后续所有 tryAcquire 计算 `delta = max(0, T'<T) = 0` → 令牌零补桶，容量耗尽后全部节点持续拒绝（429）直至墙钟重新越过 T（冻结窗 = 回拨幅度）；链 B（超前饥饿）：T1' 节点 A 时钟超前 +120s，其 tryAcquire 写入 timestamp=now+120；T2' 正常时钟的节点 B/C 计算 delta = 自身now − 超前ts < 0 → 钳 0 → B/C 零补桶最多饿 120s，而 A 自身照常补桶——限流配额向时钟最快节点系统性倾斜
- **现状**: 脚本作者已对负 delta 做防御钳位（回拨不产生负补桶），但对"冻结窗"与"多节点时钟即共享状态"未设防；令牌/时间戳键的 TTL = fill_time×2（脚本 `:7`），冻结期超过该 TTL 后键过期重建为满桶——这是唯一的隐性自愈路径，但其生效阈值取决于 rate/capacity 配置而非回拨幅度，无契约保证
- **风险**: 限流器（auth 短信/邮件发送限流等消费者场景）在时钟事件下产生分钟级整体误拒或配额倾斜；运维侧表现为"限流莫名全拒"，与 C7-008 的 VERIFY 冻结延长同属墙钟依赖族，但作用面为常驻请求路径而非迁移窗口
- **建议**: 改用 Redis 服务器时间作补桶时钟（脚本内 `redis.call('TIME')`，注意 script 内 TIME 后不能执行写命令需 `redis.replicate_commands()` 前缀，Redis 7 起默认 effect replication 已解除该限制）；或退化窗口算法（固定窗口 + INCR+PEXPIRE，服务端 TTL 天然单调）。同类兄弟 `LettuceCounter`/`LettuceRanking` 无时间参数，排除
- **失效面枚举**（F3 同族清扫）: 模块内进入判定的时钟源全清单——① `IncrementalLagProbe.sleep:60-61`（墙钟，C7-008 已立案）；② `LettuceRateLimiter.tryAcquireAsync:56`（本条）；③ `LettuceLock` 租约经 `SET PX`（服务端 TTL 时钟，服务端单调，合格）；④ `MigrationExecutor:39,50`/`MigrationEngine:243,254,337,378`（executionTime 簿记，回拨仅产生负耗时记录，不进判定，排除）；⑤ `MigrationHistoryManager:108,133`/`MigrationRecord:35`/`MigrationJobStateStore:102`（时间戳列，排除）；⑥ session touch/expiry（PEXPIRE 服务端时钟，排除）——进入行为判定的客户端墙钟共 2 处，均立案
- **信心水平**: 确定（脚本钳位与 `setex timestamp_key now` 为静态事实；触发依赖时钟事件，属条件必现）
- **误报排除**: 非"墙钟做时间戳"泛化告警——本条的 now 是**跨节点共享的补桶判定输入**，非展示字段；非"Redis 单命令原子性"误报——竞态不在命令层而在时钟源选择层；非已声明边界——`RateLimiterConfig` 仅声明 rate/capacity（类 javadoc `:24-26` 还要求"调用方对同一 key 使用一致配置"），未声明时钟语义与集群部署约束
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（`CoreMetrics.currentTimeMillis()/1000` 入共享补桶判定、lua `delta` 钳位与 `setex timestamp_key`、TTL=fill_time×2 隐性自愈路径均回码核实。跨单元同族：WI 2.3 C8-005（网关自研 TokenBucket 墙钟惰性回填，P2 复核保留）——不同设施不并案，修复批次对齐「服务端 TIME/固定窗口」方向；2.1 CL-034 墙钟 API 预警为 watch 级关联；round3 复扫确认本单元判定性墙钟恰 2 处（本条 + C7-008），收敛口径成立。lua ttl 行号笔误（实为 :9 附近）不影响结论）

### [C7-014] PubSub 共享连接断线自动重连后 Redis 侧订阅全部丢失且无重订阅/无检测：订阅面静默失联直至进程重启

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:96-105`（getOrCreateConnection 无重连监听）、`:112-117`（doStop 亦无恢复钩子）、`:119-162`（SubscriptionEntry 生命周期）；lettuce-core 7.5.2 字节码证据：`io.lettuce.core.pubsub.PubSubEndpoint.notifyChannelActive`（仅 `subscribeWritten=false`，无 SUBSCRIBE 重发）、`io.lettuce.core.protocol.ConnectionWatchdog`（无任何 pub/sub/subscribe 引用）
- **证据片段**:
  ```java
  // LettucePubSubService.java:96-105 —— 建连即用，未注册任何 ReconnectListener/重订阅钩子
  private StatefulRedisPubSubConnection<String, Object> getOrCreateConnection() {
      if (pubSubConnection == null) {
          synchronized (this) {
              if (pubSubConnection == null) {
                  pubSubConnection = connectionProvider.createPubSubConnection();
              }
          }
      }
      return pubSubConnection;
  }
  // LettuceRedisConnectionProvider.java:247 —— 传输层自动重连开启（仅恢复 TCP，不恢复订阅状态）
  builder.autoReconnect(true);
  ```
- **严重程度**: P1（长时运行必然触发的不可自愈失效：网络闪断/Redis failover/服务端重启是 pub/sub 部署的常态事件；失效全静默）
- **并发场景**: lettuce netty event loop 线程（ConnectionWatchdog 重连传输）与订阅管理线程（subscriptions 表状态不变）、发布方线程（publish 照常成功——fire-and-forget 无回执）。重连后 `pubSubConnection` 为同一实例（lettuce 原地重连），`subscriptions` map 中全部 `SubscriptionEntry` 仍显示在订，但新连接上 Redis 侧无任何 SUBSCRIBE 记录
- **触发时序**: T1：Redis 网络闪断 2s，watchdog 自动重连成功（客户端无任何异常暴露）；T2：发布方 `sendAsync` 返回成功——Redis 对无订阅者的 publish 直接丢弃；T3：订阅方进程存活、`subscriptions` 状态正常、`isCancelled/isSuspended` 均为 false，但永不再收到任何消息；交叠后果：失效在（a） lettuce 传输层（重连成功）、（b） 客户端状态层（订阅表原样）、（c） 发布路径（publish 永远成功）三处均不可见，唯一下游症状是"消息不再到达"——F5"不产出也不报错"的完整形态，无看门狗可检
- **现状**: lettuce 的 autoReconnect 契约只覆盖传输恢复（字节码核对：`notifyChannelActive` 仅复位 `subscribeWritten` 写标志；`ConnectionWatchdog` 反编译无重订阅路径；`PubSubCommandHandler` 的 replay 机制仅重放 RESP2 响应头而非 SUBSCRIBE 命令），订阅状态重发是应用层责任；本模块在 `getOrCreateConnection`/`doStart`/`doStop` 三处均未注册 `ReconnectListener` 或连接事件回调，`LettuceRedisConnectionProvider.createPubSubConnection:140-146` 亦无
- **风险**: 作为平台级 `IMessageService` 实现，宿主全部 pub/sub 通信（失效通知/推送类业务）在任意一次网络抖动后整体静默断流；因 fire-and-forget 语义无重放补偿，断流期间消息永久丢失且无法从客户端状态探测——第 1 轮 C7-009 覆盖的是 stop→start 生命周期面，本条是**运行期重连面**，触发概率高一个数量级
- **建议**: 注册重连恢复：lettuce `ConnectionEvents.Reconnect` 监听（`connection.addListener`）或 `ReconnectListener`，在重连成功事件中按 `subscriptions` 表逐 topic 重发 `sync().subscribe(topic)`（幂等）；辅以最小活性探测（如订阅后周期性 PUBSUB NUMNUM 对账，失配告警）；短期最小修：在 `SubscriptionEntry.message` 之外增加 stale 检测兜底（如超时无消息且 NUMSUB 为 0 时重订阅 + warn 日志）
- **失效面枚举**（F3 同族清扫）: 订阅恢复责任点全清单——① `getOrCreateConnection:96-105`（本条主对象，建连无恢复钩子）；② `doStart:108-109`（空实现，restart 路径同缺，C7-009 已立案其 stop 侧）；③ `doStop:112-117`（关闭无状态复位，同 C7-009 面）；④ `SubscriptionEntry.addListener:127-132`（仅注册投递监听器，不涉及 Redis 侧订阅重发）；⑤ `resume:221-226`（客户端监听器重挂，同样不重发 SUBSCRIBE——suspend 期间发生断线重连，resume 后依旧无 Redis 订阅，为本条的隐藏复合面）；⑥ 集群形态 `connectPubSub`（`LettuceRedisConnectionProvider:140-146`）同缺；⑦ 对照合格面：KV/Hash 等 `getAsyncCommands()` 池化连接不依赖服务端会话状态，重连后自愈，排除——pub/sub 是该 provider 唯一会话状态面
- **信心水平**: 很可能（客户端三处无恢复钩子为代码级确定；lettuce 侧不重订阅经 notifyChannelActive/watchdog 双点字节码核对，未遍历 lettuce 全部内部路径故不标"确定"）
- **误报排除**: 非"Redis pub/sub 本就 fire-and-forget"的语义豁免——类 javadoc（`:30-38`）声明的边界是"无持久化、无消费组"，未声明"断线后订阅永久丢失且不可恢复"；非第 1 轮重复——C7-009/CL-013/CL-016 的触发面是生命周期 stop→start（需宿主显式 stop），本条触发面是运行期网络事件（无需任何人为动作），且 `LifeCycleSupport` restart 是低频路径、网络闪断是常态路径；非平台设施重审——恢复责任属于使用 autoReconnect 的本模块（lettuce 官方契约将订阅重发留给应用层）
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（PubSub 生命周期族第四面，族裁定见 C7-long-running.md 复核节：本条为运行期断线重连面，与 C1-011/C3-004/C6-004 订阅竞态面、C7-009 stop→start 面触发路径/修复点互异，非同案；`check/nosql-cdc.md` 无重连条目，历史前案排除独立复核成立。模块侧三处无恢复钩子回码核实；`autoReconnect(true)`（provider :247/:261）与依赖版本 lettuce-core 7.5.2 一致，采信初审字节码核对。round3 回归面 5 项（重订阅锁面/取消复活/双修复状态源/cluster 双事件路径/恢复窗口）为修复设计输入）

### [C7-015] LettuceLock 无租约续期与 fencing，isHeld 以本地 volatile 回答跨进程锁状态：租约过期/他方抢锁后仍返回 true，临界区双写不可感知

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceLock.java:25-26`（`volatile String lockValue`）、`:34-44`（tryLock SET NX PX）、`:52-60`（unlock Lua CAS）、`:67-70`（isHeld 本地判定）
- **证据片段**:
  ```java
  // LettuceLock.java:34-44 —— 一次性租约，无续期；:67-70 —— isHeld 纯本地
  public CompletableFuture<Boolean> tryLockAsync(long leaseTimeMs) {
      String uuid = UUID.randomUUID().toString();
      SetArgs args = new SetArgs().nx().px(leaseTimeMs);
      return async().set(key, uuid, args).thenApply(s -> {
          if ("OK".equals(s)) { lockValue = uuid; return true; }
          return false;
      }).toCompletableFuture();
  }
  ...
  public boolean isHeld() {
      return lockValue != null;   // 租约过期/他方抢锁后仍为 true
  }
  ```
- **严重程度**: P2（可具体化触发链：持有方暂停超过租约即触发；当前仓库内 `INosqlLock` 生产调用方为 0，条件化降档——按 C1-012 同款口径）
- **并发场景**: 同 key 锁的多个持有者 JVM：持有线程（tryLock 成功后执行临界区）与新申请线程（lease 到期后 SET NX 成功）；触发对手方是"暂停 + 租约到期"这一时序事件而非代码竞态
- **触发时序**: T1：节点 A `tryLock(30s)` 成功进入临界区；T2：A 的 JVM 长时间 GC/VM 冻结 60s → 服务端租约到期删除键；T3：节点 B `tryLock(30s)` 成功（返回 true 是真实语义）开始执行同一临界区；T4：A 恢复继续执行——`isHeld()` 读本地 `lockValue != null` 返回 **true**（本地标志在租约失效时无任何失效路径），A 无从感知锁已易主，也无服务端校验原语可用（接口未提供 GET 比对/fencing token）；交叠后果：A、B 并发执行临界区；A 后续 `unlockAsync` 的 Lua CAS（REMOVE_IF_MATCH）因 uuid 不匹配安全拒绝（unlock 面合格），但临界区内的写副作用已双写
- **现状**: unlock 的 CAS 收口正确（`RedisScripts.REMOVE_IF_MATCH`），锁值 volatile 单标志可见性合格（第 1 轮批次 4 已核）；缺口在 (a) 无续期看门狗、(b) `isHeld()` 把"本地曾获取"当作"当前持有"回答，租约过期后该 API 给出的恰恰是调用方最需要的反向答案，(c) 无 fencing token 使临界区资源无法拒绝旧持有者
- **风险**: 以该锁做互斥的宿主功能在长 GC/网络分区下静默失去互斥；`isHeld()` 的误导性使其比"无此 API"更危险——守卫代码会基于假阳性放行。因零生产调用方暂为防御性缺口，一旦接入即升 P1 面
- **建议**: (1) `isHeld()` 改语义或改实现：要么 javadoc 明示"仅本地记忆、租约过期不感知"并改名 `isAcquiredLocally`，要么实现为服务端 `GET == lockValue` 校验；(2) 提供可选续期（后台定时 PEXPIRE only-if-match Lua，仿 REMOVE_IF_MATCH 模式新增 TOUCH_IF_MATCH 脚本）；(3) `tryLockAsync` 返回 fencing token（uuid 即可作为资源版本号由调用方下传），为临界区资源提供单调性校验入口
- **失效面枚举**（F3 同族清扫）: 锁生命周期全方法——① `tryLockAsync:34-44`（获取面，服务端原子，合格）；② `unlockAsync:52-60`（Lua CAS + 本地清 flag，合格）；③ `isHeld:67-70`（本条主对象，本地假阳性）；④ `lock(key)` 工厂（`LettuceMessageService.java:362-364`）每次新实例 + javadoc 已声明 per-instance 单线程（`LettuceLock.java:21-22`），线程面已声明边界，排除；同族对照：无其他 INosqlLock 实现
- **信心水平**: 确定（本地标志无失效路径为代码级事实；触发序列为标准租约锁失效模型；降档仅针对"零生产调用方"的触发面）
- **误报排除**: 非"Redis 锁无 fencing 是通用局限"的模式豁免——本条锚定的是 `isHeld()` 这一**接口承诺的守卫原语返回系统性假阳性**，属可指认的契约缺陷而非模式代价；非已声明边界——类 javadoc 仅声明单线程使用约束，未声明租约过期语义
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（`volatile lockValue`/SET NX PX 一次性租约/Lua CAS unlock/`isHeld` 纯本地判定逐行核实；零生产调用方复核成立——全仓仅接口定义 + `LettuceMessageService.lock` 工厂 + 测试 fake，无生产消费点，按本单元 C1-012 同款降档口径 P2 恰当。WI 1.5 C7-001/WI 1.6 C3-003 租约/fencing 族为家族关联不并案，fencing token/续期设施选型平台级对齐）

## 扩展

### 扩展 C7-001（链作业死亡不可见）

新增结构性证据：C3-008（见 C3-round2）证明 rescale 主场景（M>1）下 `runVerify` 探测的 connector 名（裸 base）与链注册名（`base-i`）错位，offset 指纹恒空——第 1 轮 C7-001 失效面 (a)-(e) 追加 (f)"M>1 时 lag 门禁对一切链状态（含健康滞后链）恒真"。修复顺序：先修键派生（C3-008），再加链存活轮询（C7-001），顺序颠倒会使存活轮询建立在错位观测之上。

## 维度集外线索（overlay）

（本轮无新增 CL；CL-025 已登记于 C3-round2 文件。）

## 继续判定

**需第 3 轮**。依据：本轮新增含 1 条 P1（C7-014，运行期重连后订阅静默失联——长时运行必然触发面）+ 2 条 P2（C7-013、C7-015），按"新增含 P0/P1 → 需第 3 轮"触发。第 3 轮建议范围：lettuce 重连恢复修复方案落地后的行为复核（resume×reconnect 复合面、集群 pub/sub 形态），以及限流时钟改造（服务端 TIME）在 script 语义下的回归面。

## 复核结论（WI 2.5 C7 独立复核）

C7-013/014/015 三条逐项回码复核：**全部保留，零驳回、零待定；严重度维持 P2/P1/P2**（逐条状态行见上文各条目末尾）。要点：C7-014 的 P1 经族裁定确认为 PubSub 独立失效面（与订阅竞态面、stop→start 面互异），历史无前案；C7-013 与 WI 2.3 C8-005 同族对齐不并案；C7-015 零生产调用方降档口径与本单元 C1-012 一致。扩展节（C3-008 修复顺序约束）经独立复核确认并已并入主文件 C7-001 的同案指针。判定表与最终保留项集中见 C7-long-running.md「维度复核结论」。
