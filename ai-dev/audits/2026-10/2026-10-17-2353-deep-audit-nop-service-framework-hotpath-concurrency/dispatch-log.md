# Dispatch Log — nop-service-framework-hotpath concurrency audit (plan 2470)

- 主 agent：ZCode 会话 sess_21ba4274（分支 concurrency-audit-remediation，worktree nop-entropy-concurrency-audit）
- 审计对象：请求热路径五模块（8 源码根）406 files/42,748 行手写——nop-graphql（core 145f/17,097 + orm 11f/859 + message 3f/162 + grpc 37f/3,174）、nop-biz 89f/9,985、nop-gateway 44f/4,743、nop-biz-auth-core 60f/5,760、nop-biz-file-core 17f/968
- 方法论：ai-dev/skills/concurrency-resource-audit-prompt.md；维度集合 C1/C2/C3/C4/C8/C9 全维度 + C10 最后批次（roadmap 2.3 裁定）；C5/C6/C7 维度集外 overlay 三形态，CL 前缀 `nop-service-framework-hotpath-CL-0NN`
- 基线：_tmp/cra-baseline-nop-service-framework-hotpath.txt（13 节，8 源码根分别执行）；计数与 plan 2470 对账一致
- 预注锚点核实（Phase 1 回填门）：R2 已代实测一轮（6 真 3 幻），幻影名（IResourceResourceManager/file 去重表/@BizMethod）已在 plan 订正；锚点终核实测由 C1 批次执行（IUserContextCache/LocalUserContextCache/AuthHttpServerFilter/ChunkFileUploadHandler 等已确认存在）

## 盲审纪律（每份派发 prompt 均含）

> 【盲审纪律】禁止读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下的任何文件（含审计输出目录中其他维度的文件）。只审计当前 live code。例外：深挖轮次可读本维度前序轮次文件（必要输入）。

## 第 1 轮初审（串行 6 批次，2026-10-17/18）

| 批次 | 产出文件 | 派发 agent |
|------|---------|-----------|
| C1 共享可变状态 | C1-shared-mutable-state.md | （见下结果表） |
| C2 锁与同步 | C2-lock-sync.md | 同上 |
| C3 异步链路 | C3-async-chains.md | 同上 |
| C4 线程池治理 | C4-thread-pools.md | 同上 |
| C8 背压流控 | C8-backpressure.md | 同上 |
| C9 请求热路径 | C9-hot-path.md | 同上 |

## 第 1 轮初审结果（2026-10-17/18）

| 批次 | agent | 发现 |
|------|-------|------|
| C1 | agent_8a0409b9 | 8 条（**P1×1：UserContextImpl 缓存活引用共享原地变异+dirty 丢更新**——令牌刷新永不持久化；P2×5、P3×2）+ CL-001~003 + 2 条跨维度移交提示（C2：BufferedStreamingPublisher 锁内 fetch、BizObjHelper computeIfAbsent 内嵌套 invoke） |
| C2 | agent_2bb4f795 | 6 条（**P1×1：UserContextImpl 混合同步纪律**——与 C1-001 同设施互引；P2×5：dispatchAll 持锁同步执行 loader/BufferedStreamingPublisher 锁内 fetch/BizObjHelper bin 锁内完整 biz invoke/订阅双 map 复合/resultLock 全局锁）+ CL-004~006 + 25 锁站点矩阵与全模块锁序图（无可证死锁环） |
| C3 | agent_ffce36e9 | 12 条（**P1×5：DataLoader dispatch 泵断链异常吞没、invokeOperation 出口泵缺上下文、BufferedStreamingPublisher 背压断裂无界 ArrayDeque、订阅取消三入口不 complete 上下文、BizActionService 裸 execute 不传播上下文**；P2×7）+ CL-007~009 + 22 站点异步上下文传播矩阵 |
| C4 | agent_eb1b1a6b | 6 条（**P1×1：网关 URL 重试链挂 JDK 全局单线程 Delayer+双层重试 ×16 次/90s**；P2×4：callActionAsync 默认 bean 全仓无定义确定性报错、WS keepAlive 全局 timer 同步网络写、auth 下游链 executeBlocking 无隔离、DownloadHelper 清理任务静默吞；P3×1）+ CL-010/011 + 载体全景 9 站点（globalWorker 本五模块零直接使用） |
| C8 | agent_6f7078aa | 8 条（**P1×2：WS 订阅推送同步阻塞发送钉死 5 线程全局 timer、Gateway 429 重试放大 ×7 无准入上界**；P2×6）+ CL-012~014 + 15 项缓冲载体清单（CL-011 盲审判定不成立另立 C8-008） |
| C9 | agent_5cab5aa0 | 11 条（P2×6：RPC/REST 每请求重建全量选择树、Gateway 每请求真实 stat、JwtAuthTokenProvider synchronized 串行化、MAP/ANY 字段双次序列化、SSE 每元素双重转换、HTTP 入口每请求 INFO 全量 query；P3×5）+ CL-015 新发现（LocalDateTimeFieldMarshaller 线格式正确性疑似 P1——归属留裁定） |

### 初审汇总

**51 条发现（P0×0 / P1×10 / P2×33 / P3×8）+ 15 条 CL 线索（CL-001~015）**。跨维度同设施互引：C1-001↔C2-004（UserContextImpl 双镜头）、CL-008↔C8-001（深化）、CL-003↔C8-005（深化）。全部 6 维度含 P1 或 ≥3 条 P2/P3 且无终止信号 → Phase 3 全维度派第 2 轮深挖。

## 第 2~3 轮深挖（串行，2026-10-18）

| 批次 | 产出文件 | 发现 | 终止判定 |
|------|---------|------|---------|
| C1 r2 | C1-round2.md | +2（P2×2：attrs 域锁旁路（ExtensibleBean 公开可变器绕过 synchronized 覆写不置 dirty）、DaoUserContextCache 并发 flush DB 层丢失更新——**「令牌回退→再登录风暴」链成立**（Dao+SSO 双条件）；F3 修正第 1 轮两处枚举误差）+ CL-016/017 + CL-002 归并判定维持 | 终止（C1 合计 10） |
| C2 r2 | C2-round2.md | +0 立案（**C2-006 前提推翻建议撤案**——resultLock 实为每请求锁非引擎级；**C2-002 修复范围扩大**——terminate 三链实际总在锁内监视器重入）+ CL-018（ContextTaskQueue 承重原语移交 C3 判定） | 终止（C2 合计 6+撤案 1） |
| C3 r2 | C3-round2.md | +4（**P1：C3-013 ContextBinder 复用分支跳过 init**；P2×3：订阅三步非原子、fetcher 层缺 thenOnContext、DefaultFlowControlRunner 同步阻塞）+ CL-019/020；**C3-001 机制被证伪建议降档**（字节码实测 exceptionally 恒返回空列表） | 新增 P1 → 派 r3 |
| C3 r3（收口轮） | C3-round3.md | +1（C3-017 P2 条件化：HTTP filter 链 context 裸附着无 detach）+ **4 条改判建议：C3-013 P1→P3（决定性反证——ContextHttpServerFilter 无条件注册建 context，ContextBinder 恒 no-op 死代码）、C3-001 P1→P3（二次字节码反证——OrmBatchLoader 同步抛到不了递归泵）、C3-014/016 P2→P3（零生产调用方/dormant）** | 终止（C3 合计 17） |
| C4 r2 | C4-round2.md | +1（**P1：C4-007 SSO 半衰期刷新在载体线程无超时阻塞 IdP HTTP**——推翻第 1 轮 C4-004 降档理由；async SPI 闲置）+ CL-022 + C4-005/CL-007 关联不归并（新主失效面 File.delete boolean）+ 勘误 6 项（×16→×7 加法等） | 新增 P1 → 派 r3 |
| C4 r3（收口轮） | C4-round3.md | +1（C4-008 P2：JWKPublicKeyLocator synchronized 内无超时 JWKS 拉取每小时确定性阻塞）+ CL-023~025 + **C4-007 修复设计勘误**（refreshTokenAsync SPI 不可落地——正确修复面为 ILoginService 异步化） | 终止（C4 合计 8：P1×2/P2×5/P3×1） |
| C8 r2 | C8-round2.md | +2（P2：C8-009 WS 会话无死活检测僵尸滞留∞+并发发送无单写者、P3：C8-010 file 层零指标）+ 0 CL；C8-001 量化精化（ρ=N·L/50，1~5 慢客户端即钉死 5 线程池）、C8-002 ×7 加法复核 + 内存参数化收口、C8-005/008 判定为两个独立设施（共同根因=未复用平台 DefaultRateLimiter） | 终止（C8 合计 10） |
| C9 r2 | C9-round2.md | +2（**C9-012 P1：CL-015 终裁升格**——LocalDateTimeFieldMarshaller computeSize=19 vs 纳秒实写 23-29 字节越界，实测复现两种硬失效，**移交功能正确性维度**；C9-013 P3 auth 过滤链双扫）+ C9-002 与 WI 2.1 C9-010 同设施包含关系裁定 | 终止（C9 合计 13） |

### Phase 3 收敛汇总

- 发现总量：C1 10 + C2 6 + C3 17 + C4 8 + C8 10 + C9 13 = **64 条**（初审 51 + 深挖 13——closure 勘误 A1：原记 15 系将 C2-006 撤案与量化记录误计为立案；P1 初审 10 + 深挖 2（C3-013/C4-007）= 12 存量，另有 C3 四条降级建议待复核终裁）
- 维度集外线索累计 **25 条（CL-001~025）**；跨维度移交：CL-016/017→C4/C5、CL-023→认证/安全维度（CL-021 为空号——B2 勘误：原记安全线移交系与 WI 2.2 同号混写）、CL-024→单元外同步 fetch 族、CL-025→平台原语前置
- 全部 6 维度显式终止
