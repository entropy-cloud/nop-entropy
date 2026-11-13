# Dispatch Log — CRA WI 6.3 nop-code-index 并发审计

- **Plan**: `ai-dev/plans/2490-cra-wi6.3-nop-code-index.md`（active；R1 agent_04f01b2d 1 Blocker[service 规模误加 flow→116f/16,898 订正]+2 Major[ProjectAnalyzer executor 预留未用/2026-09 G11 预登记]+3 Minor 全修）
- **基线**: commit 7b0ccc89d4；基线文件 `_tmp/cra-baseline-nop-code-index.txt`（附录 C + 长驻索引专项 + G11 站点 + 5.6 消费面）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index（**C1 复核必须对照 2026-09 G11 原文给出 G11-15-02 三态裁定；C7/C5 复核对照 5.6 消费面边界**）
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: 零 api 叶（nop-code-api 归 6.4）
- **跨单元预登记 1（2026-09 G11）**: `11-code-graph.md` 已深读本单元（含 TestConcurrentIndexing）——**G11-15-02（P2 保留）：SymbolTable 读未同步 × CodeCacheManager 共享缓存数据竞争**预登记，三态裁定归 Phase 4 复核对照原文；G11 其余发现 Phase 1 清点
- **跨单元预登记 2（WI 5.6 消费面）**: CS-001/W-04（LeidenDetector 幽灵线程 P1）消费方 CodeGraphService.java:753 与 GraphMetricMaterializer.java:122 位于本单元 nop-code-service——**缺陷本体归 5.6 W-04 不重复立项；消费侧规模守卫/触发面归本单元立案面**（内联摘要已入派单，边界终裁归 Phase 4/6）

## Phase 1 锚点核实结论（主 agent，2026-10-23；R1 实测确认）

| 锚点 | 实测 | 结论 |
|------|------|------|
| 规模 | core 51f/3,865 + flow 12f/1,840 + service 53f/11,193 = 116f/16,898 | R1 Blocker 订正落盘 |
| ProjectAnalyzer executor | :61 预留未用（注释自证），生产装配传 null | 非主战场；主战场=service 长驻站点 |
| 长驻站点 | CodeCacheManager/CodeIndexService/GraphMetricMaterializer/OrmFingerprintStore（service）+ InMemoryFingerprintStore（core/incremental） | C5/C6/C7 主战场清单 |
| G11-15-02 | SymbolTable 类定位确认 | 预登记成立 |
| 5.6 消费面 | GraphMetricMaterializer/CodeGraphService 的 LeidenDetector 调用行吻合 | 预登记成立 |
| owner doc | nop-code.md 存在（169 行） | 漂移实测归各维度初审 |

## owner doc 漂移登记

- nop-code.md 存在——单元级无预登记 drift；发现级漂移归各维度初审对照。

## 历史审计对照口径

- cra-index 对 nop-code 0 提及；2026-09 G11 覆盖本单元（预登记处置归本单元）。

## 维度级派发记录

1. C1 全单元（agent_8aaf3c09，串行 1/4）：载体 5 类全扫（singleton 20 组/static final 12 全不可变反向证实/ThreadLocal 0）→ 发现 5（C1-001 P2 SymbolTable 混合同步、C1-002 P2 缓存失效次序不一致[同文件注释自证正确次序]、P3×3：ShardBuildCoordinator 跨事务双发布[零接线]/getFingerprintStore 良性竞态/防抖双触发）+ CL-001~006。
2. C5 全单元（agent_6a5c1cda，串行 2/4）：清单 30 行（53 DB 查询点逐一核 setLimit）→ 发现 9（**C5-001 P1** 公开图查询三动作全量物化 4 关系表[守卫声称不存在]+GraphMetricStore 无 LIMIT、**C5-002 P1** ProjectAnalyzer 死守卫[MAX_FILE_SIZE/COUNT 零引用]+BatchQueue 全量累积、P2×4、P3×3）+ CL-101/102。
3. C6 全单元（agent_4557108b，串行 3/4）：配对 19 类（13 ✓）→ 发现 10（C6-001 P2 git 子进程读循环无超时 4 站点、C6-002 P2 clone 失败残留毒化、C6-003 P2 deleteIndex 漏删 shard_ledger→击穿 completeShard 守卫、C6-004 P2 单页截断孤儿行、C6-005 P2 双写非原子 warn-only、P3×5）+ CL-201~207。
4. C7 全单元（agent_5c6326e1，串行 4/4）：清单 27 行（G1-G14 增长源+H1-H5 挂死点+R1-R10 重试；wiring 双口径验证 3 站点未接线）→ 发现 11（**C7-001 P1** 读路径自愈物化无单飞→并发 Leiden 重算风暴[与 5.6 W-04 叠加面 6 触发点+4 守卫空洞已枚举]、P2×4：集群工作区零 retention/git 超时不覆盖管道读取/已删文件依赖边永久残留/搜索引擎事务内副作用发散、P3×6）+ CL-301~305。

**Phase 2 汇总**：35 立案 = P1×3 + P2×15 + P3×17；CL 线索 20。已知跨维重复族（去重归 Phase 4/6）：git 子进程超时（C6-001/CL-101/C7 P2 git 面）、GraphMetricStore 无 LIMIT（C5-001/CL-203 类）、指纹存储语义（C1-004/C7 P3 IFingerprintStore 相反语义）、5.6 W-04 叠加面（C7-001 消费侧单飞）。


## Phase 3 深挖派发记录

5. C1+C5 R2 合并批（agent_3acbd065，串行）：C1 收尾零新缺陷——SymbolTable 失效面 15+→36 站点×5 读类（全折叠 C1-001 单点修复面）、CodeCacheManager 三表锁外读点穷举（callGraph/dependencies 良性）；C5 新增 7（**C5-R2-001/002/003 P2×3**：物化读装配 7/8 finder 无 LIMIT+全量符号物化、getPublicSurface 全文件 17 查询唯一无界、fileTree sourceCode CLOB 物化；P3×4）+ C5-002 触发层收口（生产链仅 2 admin mutation，streaming 变体即生产路径）；两维度终止。
6. C6+C7 R2 合并批（agent_231a8460，串行）：C6 新增 1（C6-R2-001 P3 LlmSemanticEdgeExtractor chatService.call 无超时 cancelToken 恒 null——第 5 处外部调用面唯一网络 RPC，双重休眠接线门禁项）+ 超时面穷举闭合（4 活 git+1 dormant）/清理面第 3 处不存在；C7 新增 1（C7-R2-001 P3 CodeCacheManager 全局锁内 DB 分页重建——单飞粒度全局过粗，C2/C9 邻域）+ 读触发写面闭合（materialize 族唯一）；两维度终止。

**Phase 3 汇总**：44 立案 = P1×3 + P2×18 + P3×23；全维度显式终止；无必派触发（新增全 P2/P3）。（原 P2×22/P3×19 系汇总行滑差，勘误见 merge-adjudication）

## Phase 4 复核派发记录

7. C1+C5 复核（agent_48b71ab7，串行 1/2）：21/21 保留 0 驳回 0 改级（1 机制修正：C1-003 失败模式=发布丢失非双重发布；2 行号勘误）；**G11-15-02 三态裁定=仍成立**，C1-001 为其超集/扩面复活（36 站点×5 读类）——建议单一修复工单、C1-001 吸并防双计数；C5-001/002 P1 三环全过（C5-002 附 admin-only 下限注维持 P1）；CL 8/8 确认。
8. C6+C7 复核（agent_44833f73，串行 2/2）：23/23 保留 0 驳回 0 改级；**C7-001 vs 5.6 W-04 边界裁定=本单元独立缺陷+交叉引用**（单飞/退避 5.6 未立案，W-04 修复不消灭本条）；**git 超时族主登记=C6-001**（C7-003 别名，repoLock 级联并入）；C6-003 与 C1-003 同族不并单独立立案；别名 C6-005→C7-005 主、C7-011→C6-009 主；CL 12/12 确认（CL-201=CL-301、CL-202=CL-303 双登记）。

**Phase 4 汇总**：44 实体全保留 0 驳回 0 降级；别名归并 4 组（C7-003→C6-001/C6-005→C7-005/C7-011→C6-009/C7-006 拆解）；跨单元裁定 2 条（C7-001 独立、G11-15-02 仍成立吸并 C1-001）；CL 20/20 确认。

## Phase 5 C10 派发记录

9. C10（agent_46180e93）：44 行矩阵——**会红 0/44**（7 行完全无测试+37 行相邻覆盖零缺陷面断言）；TestConcurrentIndexing 定性=真并发执行形态+功能冒烟断言（非并发正确性测试）；P1×3 全无保护；缺保护 41 项（P1×3/P2×16/P3 聚合 22）；C7-009 断开的现成契约测试（Orm 子类接入一行即红）。
10. C10 复核（agent_d9e239d8）：抽样 13 行全成立（P1×3 逐一）——**PASS with corrections**（3 处非载荷修正：测试文件数口径/P3 聚合去重/措辞一致）。

**Phase 5 汇总**：会红 0/44 第 12 例；缺保护 41 项；优先补测 TP-001~003（P1 验收前置）+ Orm 契约子类接入（零成本）。
