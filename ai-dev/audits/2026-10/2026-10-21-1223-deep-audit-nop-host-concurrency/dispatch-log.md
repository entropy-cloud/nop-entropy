# Dispatch Log — CRA WI 4.4 nop-host 并发审计

- **Plan**: `ai-dev/plans/2480-cra-wi4.4-nop-host.md`（active；R1 agent_8ff962ce 2 Major[计数 13→11/形态预注失真]+4 Minor 全修；R2 agent_9a7695de 1 Major[pom-only 11→5/源码根 13→11 残留]+m1/m2 全修）
- **基线**: commit 2a2d546fc0，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-host.txt`（14 命令组 × 11 源码根 = 308 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发

## 源码根实测（Phase 1 回填，src/main 手写口径）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-spring/nop-spring-core-starter | 5f/464 | NopSpringBeanContainer/NopSpringCoreAutoConfig（@EventListener onStart:27/onClose:36）/NopSpringTransactionFactory |
| nop-spring/nop-spring-delta | 4f/205 | NopBeansRegistrar + mybatis 三类（MapperProxyFactoryEx :28 代理生成） |
| nop-spring/nop-spring-file | 2f/174 | |
| nop-spring/nop-spring-proxy | 5f/298 | SpringWebProxy（webClients CHM:34）/WebClientHolder |
| nop-spring/nop-spring-web-starter | 13f/1,213 | SpringHttpServerFilterConfiguration（getFilters :47）+ZipContentEncodingFilter +ServletHttpServerContext（CF/AtomicBoolean :238-239/:314-315） |
| nop-ai/nop-spring-mcp-server | 3f/85 | McpServerConfiguration @Bean toolCallbackProvider（入圈=roadmap 4.4 括注点名） |
| nop-quarkus/nop-quarkus-core-starter | 6f/484 | QuarkusIntegration.start（caller main 线程）/NopQuarkusBeanContainer/QuarkusExecutorHelper（ThreadLocal :28/Recorder :77） |
| nop-quarkus/nop-quarkus-file | 3f/244 | |
| nop-quarkus/nop-quarkus-web | 10f/1,120 | QuarkusGraphQLWebService/QuarkusJsonRpcWebService + 两 Registrar |
| nop-quarkus/nop-quarkus-web-orm-starter | 1f/11 | |
| nop-quarkus/nop-quarkus-web-starter | 1f/46 | |
| **合计** | **53f/4,344**（11 有源/5 pom-only） | `_gen/` 残留 0 |

## 预注锚点核实（Phase 1，R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| StartupEvent/ShutdownEvent | 零命中（唯一在 grpc 测试树出圈） | 预注订正 |
| WebClassLoader | 零定义 | 类加载面近乎为零 |
| BeanPostProcessor | 零命中（IoC 桥实为双 @EventListener） | 预注订正 |
| 并发原语 | 6 文件 26 行（VertxHttpServerContext 10/ServletHttpServerContext 6/SpringWebProxy 4/QuarkusExecutorHelper 4/FilterConfig 1/Registrar 1） | 原始并发面极薄 |
| getFilters 同构孪生 | Spring:47/Quarkus Registrar:46 | 跨维合并对照 |
| CL-021 feishu 点名 | 不含宿主 | Phase 4 漂移复查 |

## owner doc 漂移登记

- module-groups.md L19 组级覆盖 nop-spring/nop-quarkus、L17 覆盖 mcp-server——覆盖成立（薄，drift 登记条款兜底）。

## 历史审计对照口径

- `ai-dev/audits/` 下宿主模块 **并发主题**直接审计 0 命中。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-21，C1+C4+C6 合并批单次派发）

| 维度 | 编号 | 结果 |
|------|------|------|
| C1 | C1-001…006；CL-001…004 | **P1×2**（SpringWebProxy 凭据对跨线程撕裂/QuarkusExecutorHelper ThreadLocal 不传播）+P2×4 |
| C4 | C4-001…003；CL-005 | P2×3（过滤链 syncGet 阻塞 servlet 线程/WS keepalive globalTimer 滞留/启动窗口无就绪门禁） |
| C6 | C6-001…006；CL-006/007 | **P1×1**（上传流校验失败路径永不关闭——外部可触发 fd 泄漏）+P2×4+P3×1 |
| **Phase 2 合计** | 15 条 + CL×7 | **P0×0/P1×3/P2×11/P3×1** |

- 继续条件判定：2 维度含 P1 + C4（P2×3）→ 3 维度全派第 2 轮（合并批）

### Phase 3+4 深挖与复核（2026-10-21，深挖+复核合并任务单次派发）

| 阶段 | 范围 | 结果 |
|------|------|------|
| R2 深挖 | C1/C4/C6 | +4（P2×3/P3×1）：C1 零新增终止、C4-004、C6-007 **P1**/C6-008/C6-009；CL-008 | 3 维度全部显式终止 |
| 复核 | 19 条 | 19/19 全保留（降档 1 C1-002 P1→P2、**升格 1 C1-006 P2→P1**——复核补全变体 B 机制）；同案 2 组单列零重复；CL-021 漂移复查闭合 |

- **复核后最终保留 19 条：P0×0/P1×4/P2×13/P3×2**；新发现×19
- 主登记建议网：C6-007（future 族第 5 例——平台根因 ContextTaskQueue→WI 2.3 CL-018+WI 2.1 C3-001 绝缘层）、C6-008（错误交付族传输层成员）、C1-006（双宿主空链固化）、C1-001（SpringWebProxy 凭据撕裂）

### Phase 5 C10 + Phase 6 合并裁定（2026-10-21，C10+裁定合并任务单次派发）

- C10：矩阵 19 行 = 会红 0/假保护 1/部分 2/无测试 16（P1×4 全部无保护）；登记 C10-001…019（P1 本体强制 4 条）；反向钉死 4 处（1 钉死型+3 掩蔽型）
- 合并裁定：19 → 16 工单 + watch 1（5 族吸收 7 + 11 独立）；P0×0 显式空执行/P1×4/P2×10/P3×2；跨单元 8 处零重复；冲突裁定 2 留 7.1；C6-009 触碰 nop-http-api 申报 plan-first
