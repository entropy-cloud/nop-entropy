# Dispatch Log — CRA WI 6.1 nop-tools-parallel 并发审计

- **Plan**: `ai-dev/plans/2488-cra-wi6.1-nop-rg-find-bytecode.md`（active；R1 agent_23c1e694 0 Blocker、1 Major[overlay 无独立抽查批口径]+4 Minor 全修）
- **基线**: commit 5e84b88a37；基线文件 `_tmp/cra-baseline-nop-tools-parallel.txt`（附录 C + FFM/Arena/SIMD 专项节）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: 无 api 叶（R1 实测确认）；nop-rg-benchmark 面按误报校准不立案（引源已录 plan）
- **计数面>立案面**: nop-rg-benchmark 8f/742 计入规模（43f/3,274），立案面 ≈35f/2,532

## Phase 1 锚点核实结论（主 agent，2026-10-22；R1 实测确认）

| 锚点 | 实测 | 结论 |
|------|------|------|
| FFM/Arena 落点 | MappedFileReader.java:31 `Arena.ofConfined`、SearchCoordinator.java:113、ParallelFileWalker.java:71 ForkJoinPool、FindCoordinator.java:145 fixed pool | C4/C9/C6 主战场确认（rg 侧） |
| SIMD 面 | nop-rg-vector 4f（Vector API）；find/bytecode 主代码 FFM 0 命中 | SIMD 仅 rg-vector |
| C4 原语 | ForkJoinPool + fixed pool + ExecutorService 少量命中（见基线 C4 节文件分布） | 有实质并发面 |
| owner doc | nop-bytecode.md 存在；nop-rg/nop-find 无（占 62% 行数） | drift 登记 |
| Protected Area | 无 api 叶 | P0 即修无叶限制 |

## owner doc 漂移登记

- **nop-rg/nop-find 无 owner doc**（占单元 62% 行数）——drift 登记，归治理轨。

## 历史审计对照口径

- cra-index 对三模块 0 提及——全单元首报。

## 维度级派发记录

1. C1 全单元（agent_ac020551，串行 1/4）：载体 5 类全覆盖（singleton 0/static final 16 全安全/ThreadLocal 0）→ 发现 2（C1-001 P2 SearchCommand.patternBytes 惰性缓存 check-then-act+无安全发布、C1-002 P3 WalkContext.recordError volatile 守卫非原子「首个错误胜出」退化）+ CL-001~003；FFM confined arena 线程封闭构造性安全、SIMD 封装 final 不可变。
2. C4 全单元（agent_b03debe1，串行 2/4）：池创建点 3 处盘点（每调用新建×3 但 finally shutdownNow 配对正确——CL-002 泄漏疑点证伪，成立面=线程无命名 P3）→ 发现 3（C4-002 P2 invokeAll 首错全弃——TOCTOU 瞬时错误→整次搜索 exit 2 与 rg「报错继续」背离、C4-001 P3 池无命名、C4-003 P3 patternBytes 竞态[C1-001 同站点异维度]）+ CL-101/102；FFM Arena 全部 confined+TWR 主动设计通过。
3. C6 全单元（agent_10876941，串行 3/4）：26 资源获取点盘点（24 ✓）→ 发现 5（C6-001 P2 ChunkedFileReader 全块视图累积单一 arena 驻留≈全文件+小 chunk 注入击穿 max_map_count、C6-002 P2 取消路径失效——扫描循环无中断检查点、P3×3）+ CL-201/202；CL-001 双映射共存证伪（严格先后+TWR）。
4. C9 全单元（agent_bb9809ec，串行 4/4）：热路径清单 24 行 → 发现 4（C9-001 P2 walker 每文件全局锁+双重 stat、C9-002 P2 bytecode 逐 artifact 重开 JarFile O(N×central-dir)、P3×2）+ CL-301/302；SIMD/FFM 专项全部通过；基准缺口 5 项登记。

**Phase 2 汇总**：14 立案 = P0×0/P1×0/**P2×6**/P3×8；CL 线索 9。（原 P2×7/P3×7、CL 12 系汇总行滑差，勘误见 merge-adjudication）已知跨维重复对（去重归 Phase 4/6）：patternBytes（C1-001/C4-003/CL-301）、walker 锁（C9-001/CL-101）、映射累积（C6-001/CL-001）。


## Phase 3 深挖派发记录

5. C1+C4 R2 合并批（agent_d196d98b，串行）：C1 新增 1（**C1-R2-001 P2** FFM 映射段存活期×外部截断 TOCTOU——Zulu JDK 26 本机实测可捕获 InternalError，复用 C4-002 聚合层，失效面 8 项）；C4 新增 2（C4-R2-001 P3 walk 不可取消[JDK 26 源码确证]、C4-R2-002 P3 --jfr SIGINT 无 dump）+ C4-002 失效面补充 2 项；扩大面核查全通过（CompiledGlob.CACHE 键不可变/CLI 参数冻结/FFM 别名不逃逸/commonPool 零污染）；两维度终止。
6. C6+C9 R2 合并批（agent_f8dcab8b，串行）：C6 新增 1（C6-R2-001 P3 CollectManifest.save 原地截断写非原子——崩溃自伤 manifest F6 族）；C9 新增 2（C9-R2-001 P3 LineCursor 快速路径行首重扫——纠正初审 C9-004 失效面误判，建议合并单一修复面、C9-R2-002 P3 CompiledGlob 回溯无 memoization）+ watch 3 项；SIMD 扩大全查维持通过；两维度终止。

**Phase 3 汇总**：20 立案 = P0×0/P1×0/P2×7/P3×13；全维度有显式终止信号；无必派触发（新增全 P2/P3 且 <3 或带终止）。（原 P2×8/P3×12 系汇总行滑差，勘误见 merge-adjudication）

## Phase 4 复核派发记录

7. C1+C4 复核（agent_7771fc41，串行 1/2）：8/8 保留 0 驳回——C1-R2-001 复核同机复现成功（_tmp/c1r2-verify/TruncFaultTest.java）限缩「穿透嵌入方屏障」子句；C4-003 并入 C1-001（P3 低于数据竞争下限）；patternBytes 三重登记收敛为 C1-001 单点主登记；C4-002 补实锤（CLI 只打顶层消息 cause 不可见）；CL-001~003/101~102/301 全确认。
8. C6+C9 复核（agent_62df3e9c，串行 2/2）：12/12 保留 0 驳回、**降级 3**（C6-001 P2→P3 chunkSize 不可注入恒默认 256MiB/C6-002 P2→P3 对齐取消族/C9-001 P2→P3 二阶量化）；**取消族裁定 C4-R2-001 主案**（caller 取消完全失效）建议并卷单一修复工单；C9-R2-001 纠正初审 C9-004 误判核实成立，与 C9-004 合并 LineCursor 单一修复面；CL-201/202 确认（后者闭环至 C9-002）。

**Phase 4 汇总**：20 实体全保留 0 驳回；降级 3；归并 3（C4-003→C1-001、C9-R2-001+C9-004 同面、C6-002+C4-R2-001 取消族并卷建议）；工单级 **P1×0**（本单元无 P1）/P2×4（C1-001/C1-R2-001/C4-002/C9-002）/P3×剩余；CL 9/9 确认（原「CL 12/12」系汇总行滑差，勘误见 merge-adjudication §三.6）。

## Phase 5 C10 派发记录

9. C10（agent_32833766）：20 行矩阵——会红 0/部分 7/不会红 13（测试全为功能语义断言）；缺保护 10 项（P2×4 逐条+P3 聚合 6 组）；3 项建议「先红后绿」。
10. C10 复核（agent_b324475b）：抽样 10 行全成立——通过（修正 2 处：truncat 命中数 11→14/P3 聚合漏 C6-R2-001 补记）。

**Phase 5 汇总**：会红 0/部分 7/不会红 13；缺保护 10 项。
