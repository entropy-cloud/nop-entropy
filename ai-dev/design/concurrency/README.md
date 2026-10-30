# concurrency 设计文档（平台并发设施）

> Status: active
> Created: 2026-10-24

本目录承载 nop 平台**并发基础设施**（`io.nop.commons.concurrent` 及其 executor 家族）的架构决策与使用契约。本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 层级

### Vision 层

- 无独立 vision 文档：平台并发设施的定位与不可违反约束由源码包结构 + 各分项设计的「设计结论」节承载；当本目录文档数超过 3 篇时再拆分独立 Vision 层。

### Architecture Baseline 层

- `periodic-task-insulation.md` — 周期任务异常绝缘层（重臂 vs kill+alarm 选型、返回 future 终态 × isDone 守卫 × 重臂三相容、失败治理四件套[计数/退避/日志抑制/升级]、拒绝路径、API source-compat 形态与 C3-016 边界、MA-06 看门狗前置裁定、30 点表载体覆盖矩阵）
- `pool-facility-hardening.md` — 平台池设施加固（调度槽/执行体契约与 globalTimer 28 消费方文件全处置矩阵（终表 27 + 终表外新增 D5）、globalTimer 容量语义与工厂副作用移除、池工厂 fail-fast 校验契约、cached 共享池上界与熔断、收缩语义与热更面处置、与绝缘层共享代码位边界）
- `bindcancel-context-unification.md` — api-core 取消通道与上下文传播统一（bindCancel 完成原因判别与调用方兼容矩阵、C3-004 接入顺序约束与 R1-01/R1-02 边界衔接、出口泵 thenOnContext/入口 newRpcContext 内置 binder/平台根因 isClosed 守卫、syncGet 端窗修复选型、SPI 零签名变更与 ×11 回归面全表、ICancelToken 契约与 toolkit 取消链接线）
- `cache-invalidation-single-point.md` — 模型缓存失效链单点修复（check-changed 判据统一与唯一载体落点[DefaultResourceChangeChecker：mtime 相等 + 宽限期承诺]、从属编辑点枚举表与 closure audit 机械判定、窗口二 load 原子化/窗口一宽限裁决、ResourceTimestampCache 移除终裁、TTL 兜底双路径自愈覆盖矩阵、ResourceCacheEntry 内建门[verdict 记忆化 + maxAge + deps-空豁免]覆盖 6 直连使用方零改码、TenantAware/WithLoader 委托穿透断言、R2-01/R4-32/5.5-W-03 三边界）
- `ledger-retention-policy.md` — R01 F4 无界账本族级保留策略（统一参数集七项一处定稿[保留期/触发周期/批删上限/行数上限裁定/fail-fast 包络/配置键命名/清理谓词时间锚列]、触发形态四态菜单判据[机器速率写入面必须自动触发]、R01 六成员逐单传导表、C7-101 第七面三选一裁定与 trusted-device 边界注记、W-01 生命周期配对与 G2 retention×reJudge 两条差异条款、sys_change_log 留存语义、家族 SSOT 与参数修订纪律）
- `registry-lifecycle-contract.md` — REGISTRY 生命周期契约（NopStreamOffsetBackingStore；R4-24 实现契约：生命周期三选一终裁[stop 侧配对释放 + 绑定代条件移除，同名重启续传唯一合法路径 = 显式 checkpoint 恢复]、软钉 #5 setOffsets REPLACE 契约维持与恢复声明窗、WI 2.5 消费面互斥分层表态、C3-014 幽灵引擎双层防御一致性声明与 C3-004 恢复语义衔接）
- `kernel-retirement-generation-and-model-load-locking.md` — kernel 退役代际与模型加载锁序（退役代际平台机制单点[发布新代 + 旧代退役登记 + 宽限延迟关闭：RoundRobinSupplier 收缩窗/DefaultScheduledExecutor 置空窗/DefaultVirtualFileSystem zip 句柄窗三站同机制 + 七读点快照化 + close 同族面]、三层监视器链统一不变式[监视器内只做同 key 去重+占位、装载/编译在锁外；等待图环检测 + 有界等待兜底 + 循环依赖类型化收敛；C2-001/C2-017 同变更集]、失效销毁时序[标记失效 + 延迟销毁]、2.5-C6-005 双轨联动与 createConnectionSupplier 测试缝、N2 钉改写契约、R1-01/R1-02/R1-05 零回改边界与 F4 时间机制复用 CoreMetrics、从属编辑点枚举表与死锁测试纪律）
- `plugin-lifecycle-and-config-source-hardening.md` — plugin 生命周期锁面与配置源加固（双锁单向序 reconcileLock → lifecycleLock 与 lifecycleLock 持锁禁触发 reconcile 硬规则、方法×持锁矩阵[VfsPluginDefinition/AbstractPlugin/PluginManagerImpl 三载体全生命周期方法]与锁内外呼站点逐一裁定、复合读双检 7 站点统一 ERR_PLUGIN_INACTIVE、中间态 try/finally 失败落定 FAILED 与 forceUnload 恢复 API 契约[实现类公共方法 + PluginManagerErrors 类型化拒绝]、loadPluginFromJar 映射前失败 try 收窄与 newPluginClassLoader package-private 断言缝[N2 两类失败终态消解]、JdbcConfigSource close 互斥 + closed 守卫[与 C1-021 隔离体/R1-02 卸载超时正交]、配置源构造器先加载成功再调度[executeOn 卸载形态保留]、ShellRunner 外层 finally 兜底 + destroyForcibly 有界升级[时序 A–F 全覆盖]、R2-04/R4-15 协同边界与 R1-01/R1-02 零回退承诺）
- `ioc-startup-graph-and-config-publish-protocol.md` — IoC 启动图收敛与配置发布协议（concurrent-start 框架级 join + 失败终态 + 有界超时与 C4-008 三形态收敛[NopApplication 零改动自然传播链]、BD/PBI 双域等待登记环检出复用 LoadWaitGraph 机制单点 + 有界兜底[补收集边仅入等待登记不进拓扑序与 order 校验、allow-cycle 维持 true]、waitQuietly 有界化与 `nop.ioc.bean-init.wait-timeout` 两档语义、api-core 配置发布单一不可变 holder + 单 volatile 引用[7 写点×8 读站对照、SPI 回归面 5+2+AppConfig 全列、零接口签名默认]、shardSelector volatile + 安全发布、写门租约 fail-open 终裁与窗口复核保持机制、R4-15/R2-21/R2-15 边界与 2506 零改动承诺解除）
- `hotpath-sso-refresh-async-and-gateway-retry-admission.md` — 服务热路径 SSO 刷新异步化与网关重试准入上界（C4-007 契约 default 方法兼容回退型异步化[SPI 回归面 8 面全列]、OAuthLoginServiceImpl 剥 syncGet 纯异步链与超时键 `nop.auth.sso.refresh-token-timeout` 两档语义[beans.xml `@cfg:` 行接线]、过滤器 thenCompose 严格领先 next.get 链序与 :354 挂死面闭合、stale-serve 降级零认证语义变更；C8-002 平台 ISemaphore 准入上界[url 路由腿双落点共享池 + 重试挂起全程持有 + 全出口释放]、类型化快速拒绝新码不可重试钉、钳制后值 equal jitter 单一可注入源、`nop.gateway.upstream-max-inflight|1000`/`upstream-max-retries|3` 配置化、在途 ≤ P 与 λ/H 解耦 OOM 轨迹消除；测试缝 = fake IHttpClient + 新建独立可控桩 + seeded jitter；R2-16/G36/C3-003 三向边界与零回退清单）
- `stream-buffer-unification-and-failover-flow-concurrency.md` — 流式缓冲统一与 FailoverStreamFlow 并发修复（共享有界窗口缓冲组件物理共享落 nop-gateway[元素类型泛型化、容量越窗前后同界、demand 唯一投递介质、pendingComplete 终态挂起基准、fetch/重试编排不上移]与 PA 核验[pom :17 单向依赖零 PA]、C2-001 onSubscribe 取消分支漏斗行 releaseAttempt + 4 进入点交错矩阵、C1-001 clear 入锁 + attempt-identity 纵深守卫、C3-001 终态挂起先例对齐与 error 路径豁免成文、C8-001 越窗上界 fail-loud + request 策略分侧判定规则[阻塞 submit 保 MAX + 兜底 vs 自驱动映射链 demand 门控]与 2505/2508 零回退核验、C2-002/C2-009 R03 出锁投递 + drain 串行信号序、C3-007 降级终态显式化 + C7-005 allowCall 代理落 CandidateHealthProvider + RuleBased healthView 第 5 标量零 PA 核验 + C3-005 attempted 请求级生命周期、MA-05 三回调体护栏 + C3-003 取消短路）
- `stream-core-concurrency-and-resource-envelope.md` — stream-core 并发正确性与资源包络契约（enumerator 三态锁收敛与叶锁零嵌套契约[锁序审计结论入宿主条款]、LOCAL 池双守卫[Σsubtask>poolSize typed fail-fast 误杀面取舍 + runLocal 单点有界等待 abort 语义]、刷新派发有界+in-flight 去重+丢弃计数、OpsJobManager 提交窗口 try/finally 四面补偿与孤儿窗口防护、standby 降级第四层防线[主动 abort + 扇出入口 active/fencing 复核]、poller 控制面/数据面分面与单 poll 批量上界、分配账本终态定界清理接线[锚 `assigned_at`+终态 join 零 DDL]与 msg_queue 消费位点定界保留[消费后可删裁决]+ChannelBacklogProbe 门控补齐、setLimit+分批读路径 R02 选型、FAM-1 许可保留溢出语义[acquire 恒 put/store 独立上界]与假保护改写契约、测试缝与零回退钉清单；R01/R02 对齐成员行以 ledger-retention-policy.md §3.4 为准）
- `stream-connectors-concurrency-and-envelope.md` — stream-connectors 并发正确性与资源包络契约（RocksDB gauge 锁内取值与 close 竞速语义[零新锁]、CEP copyForSubtask 序列化 round-trip 三面封闭[条件图私有/ctx 槽位/udf 生命周期]、Avro registry 请求级超时+CHM bin 锁外建连[R03 成员投影]、2PC epochledger 双载体保留[JDBC 形态 A `committed_at` 锚 + manifest 形态 B epoch 窗口顺带裁剪，R01 成员传导]、RocksDB 增量 checkpoint 缺省化+全量阈值 typed 拒绝、Debezium per-record last-emitted 位点协议、Kafka 事务超时缺省 900s+构造期耦合 fail-fast 拒配、七测试缝与八零回退钉清单及六处假保护/缺陷载体改写契约；保留成员行以 ledger-retention-policy.md §3.4 为准）
- `stream-orchestration-thin-concurrency.md` — stream 编排薄层并发契约（UDF 注册表 CHM 容器化 + 运行期换绑 WARN 契约[typed fail-fast 不采判据]与 sql.md §3a 双路径 docs bug 修正文案、目录源游标恢复 REPLACE 改 per-file 位点 max 增量合并[位点=已发射前缀单调边界]、ProcessOperator per-subtask RuntimeContext 封闭收敛深拷贝单分支[RichFunction 契约亲读结论写死 + 形态 B holder 拒绝 + 失效面外溢矩阵]、cancelled volatile run 入口 CAS 成功后复位[基类复位契约对齐 + C10-006 反向钉零回退 + C1-003 联调边界]、五测试缝与十零回退钉清单及 C10 集合落名[C10-007 搭车/C10-008 不搭车]）

## 阅读顺序

1. `periodic-task-insulation.md`（周期任务绝缘语义；新增分项设计按编号或主题追加于此表）
2. `pool-facility-hardening.md`（池设施容量/校验/卸载契约；与 1 共享 executor 代码位，边界见其 §3.6）
3. `bindcancel-context-unification.md`（bindCancel 原语契约与 context 传播统一口径；消费 1/2 的共享代码位边界，见其 §3.2/§五）
4. `cache-invalidation-single-point.md`（check-changed 失效判据与模型缓存门语义；与 1/2/3 零共享代码位，边界归 R2-01/R4-32/R3-05 successor，见其裁定⑥）
5. `ledger-retention-policy.md`（R01 族保留策略参数与逐单传导；形态 A 周期清理链消费 1/2 的设施契约，见其 §3.6）
6. `registry-lifecycle-contract.md`（Debezium CDC offset 基质的桶生命周期与恢复声明窗契约；与 1–5 零共享代码位，本体修复归 R4-24，见其裁定⑥）
7. `kernel-retirement-generation-and-model-load-locking.md`（退役代际机制与模型装载锁纪律；消费 1/2 的调度槽契约与 4 的门语义零回改边界，见其裁定⑥/§3.6；plan 2504 Phase 1a 产出）
8. `plugin-lifecycle-and-config-source-hardening.md`（插件生命周期锁矩阵与状态机恢复契约、配置源构造序与 close 互斥契约、Shell 进程兜底契约；消费 1/2 的 executeOn 卸载与隔离体零回改边界，见其裁定④⑤⑦/§3.7；plan 2506 Phase 1 产出）
9. `ioc-startup-graph-and-config-publish-protocol.md`（IoC 启动图 join/失败终态契约与 BD/PBI 等待环收敛、配置发布 holder 单发布协议、写门租约/复核契约；复用 7 的 LoadWaitGraph 机制单点与其两档超时语义零回改，见其裁定②/§3.2.1；消费 8 的 close 互斥/构造序零回退边界，见其裁定④⑤；plan 2507 Phase 1 产出）
10. `hotpath-sso-refresh-async-and-gateway-retry-admission.md`（SSO 刷新异步化契约与网关重试准入上界契约；消费 `CTX-001` 的上下文漂移对照框架与 plan 2504 LOCK-001 两档配置语义零回改边界，见其裁定①/§3.2.3；与 1–9 零共享代码位；plan 2508 Phase 1 产出）
11. `stream-buffer-unification-and-failover-flow-concurrency.md`（流式缓冲窗口/需求/终态统一契约与 FailoverStreamFlow 并发修复契约；消费 10 的准入闸正交边界与 plan 2505 `sse-backpressure-and-body-phase-lifecycle.md` 的 4.3 传输层零回退边界，见其 §3.5(c)/§五；plan 2509 Phase 1 产出）
12. `stream-core-concurrency-and-resource-envelope.md`（stream-core 并发正确性与资源包络契约；⑦⑧保留成员行消费 5 的 R01 族级 SSOT 与 1 的周期链绝缘契约，见其 §3.12；与 1–11 零共享代码位；plan 2510 Phase 1 产出）
13. `stream-connectors-concurrency-and-envelope.md`（stream-connectors 并发正确性与资源包络契约；④2PC 保留成员行消费 5 的 R01 族级 SSOT[face token=`epochledger`]、③锁内 HTTP 外移消费 11 的 R03 范式、⑥与 6 的 REGISTRY 契约域作对齐注记零触碰，见其 §3.4/§3.6/§3.3/§3.8；与 1–12 零共享代码位；plan 2511 Phase 1 产出）
14. `stream-orchestration-thin-concurrency.md`（stream 编排薄层并发契约；③深拷贝消费 13 的 round-trip 范式与 ShareRef 豁免判据引用不重开、②与 6 的 REGISTRY 契约域作零触碰注记，见其 §3.2/§五；与 1–13 零共享代码位；plan 2512 Phase 1 产出）
15. 实现锚：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`（`IScheduledExecutor` javadoc 为周期调度成文契约的代码侧锚点）+ `nop-kernel/nop-core/src/main/java/io/nop/core/resource/`（失效链设施与判据载体代码侧锚点）+ `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/` 与 `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/`（双轨生命周期锁契约与状态机恢复 API 代码侧锚点）+ `nop-core-framework/nop-config/src/main/java/io/nop/config/source/`（配置源构造序与 close 互斥代码侧锚点）+ `nop-utils/nop-shell/src/main/java/io/nop/shell/`（进程兜底契约代码侧锚点）+ `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/`（impl/initialize 启动图与等待环收敛代码侧锚点）+ `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/`（配置发布 holder 单发布协议代码侧锚点）+ `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/`（写门租约/复核与 shardSelector 热替换代码侧锚点）+ `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/` 与 `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/`（SSO 刷新异步化契约与超时键接线代码侧锚点）+ `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/`（url 路由腿准入与时间整形契约代码侧锚点）+ `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/`（共享有界窗口缓冲组件与映射链门控代码侧锚点）+ `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/` 与 `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/`（流式 failover 并发契约与 allowCall 消费面代码侧锚点）+ `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/` 与 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/`（enumerator 锁纪律/LOCAL 守卫/提交补偿/降级防线/poller 分面/账本清理/门控探测/读路径分批/溢出语义代码侧锚点）

## 职责边界

- 本目录：并发设施的**决策与契约**（为什么这样设计、语义是什么、拒绝了什么）
- `docs-for-ai/`：并发设施的**使用知识**（怎么用）；两者冲突时按 `ai-dev/design/README.md` 的 Precedence Model 处理
- 过程性审计证据（30 点表原始判定、单元审计详情）留在 `ai-dev/audits/`，本目录只保留最终裁定
