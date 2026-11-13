# Dispatch Log — CRA WI 6.2 nop-tools-parse 并发审计

- **Plan**: `ai-dev/plans/2489-cra-wi6.2-nop-tools-parse.md`（active；R1 agent_df5051e6 0 Blocker、1 Major[2026-09 G10 前审未清点]+3 Minor 全修）
- **基线**: commit 0d131797dd；基线文件 `_tmp/cra-baseline-nop-tools-parse.txt`（附录 C + 解析器生命周期专项 + G10 站点定位）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index（**C1 批 B 复核必须对照 2026-09 G10 原文给出 LanguageRegistry 三态裁定**）
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: 无 api 叶（R1 实测）；bench/deepwiki 面不在计数面
- **跨单元预登记（Phase 1 锚点核实确认）**: 2026-09 全仓审计 G10 已覆盖 lint/refactor/jq：G10-01-02（LanguageRegistry 裸 HashMap 已发布读，engine/LanguageRegistry.java:30，P3）、G10-01-03（rename/rewrite 每请求重复 ServiceLoader 发现）、G10-01-01（refactor 消费 lint cli 包，P3 非并发）——现状三态裁定归 Phase 4 复核对照原文；treesitter 为真首报

## Phase 1 锚点核实结论（主 agent，2026-10-22；R1 实测确认）

| 锚点 | 实测 | 结论 |
|------|------|------|
| jq 编译缓存 | JqEngine:16 static LocalCache「nop-jq-compile」 | C1/C6 候选确认 |
| treesitter native 面 | MemorySegment/Arena 9 文件、SubtreeArena、TSParser 静态 parse | C6 主战场确认 |
| lint 并发面 | main 零 Pool/ExecutorService；真实面=XScriptEngine/LintDeadlineExecutor/RuleResultCache/LanguageRegistry | 预注① 改写为实存站点 |
| G10 站点 | LanguageRegistry.java:30 裸 HashMap 定位吻合 | 预登记成立 |
| owner doc | nop-treesitter.md/nop-refactor.md 存在；nop-lint/nop-jq 无（占 58% 行数） | drift 登记 |
| Protected Area | 无 api 叶（graphql 子模块非 api 叶） | P0 即修无叶限制 |

## owner doc 漂移登记

- **nop-lint/nop-jq 无 owner doc**（占单元 58% 行数）——drift 登记，归治理轨。

## 历史审计对照口径

- cra-index 对四模块 0 提及；2026-09 G10 覆盖 lint/refactor/jq（预登记处置归本单元）——treesitter 真首报。

## 维度级派发记录

1. C1 批 A（agent_3a36b7cd，串行 1/5）：载体 18 项（static 非 final 0）→ 发现 1（C1-001 P2 DefaultTreeSitterLanguageProvider.cache get→load→put 竞态——并发首触双 Language 实例→parseIncremental fail-fast+双份解码表；修法一行 computeIfAbsent）+ CL-001（jq fork() 预算快照嵌套放大）；基线勘误：live 无 MemorySegment/FFM（SubtreeArena 为 int[] 并行列）。
2. C1 批 B（agent_ca2ba5c0，串行 2/5）：载体 20 项 → 发现 5（**C1-101 P1** LintDeadlineExecutor.install 全局槽丢更新→护栏静默失效、**C1-102 P1** NodeTscBridge 队列无纪元标记旧 reader 毒化、C1-103 P2 TscTypeResolver.bridge 非 volatile、C1-104 P2 LanguageRegistry check-then-act、C1-105 P2 RuleResultCache.save 跨进程无互斥）+ CL-101/102；编号 101 段隔离。
3. C6 批 A（agent_67c92c89，串行 3/5）：配对 16 项（14 ✓）→ 发现 6（C6-001 P2 EditPlanApplier 先写盘后守卫、C6-002 P2 javadoc「atomically」与部分落地矛盾、C6-004 P2 TSNode SCRATCH 池游标不缩容、P3×3：ScannerVM 池壳常驻/SubtreeArena.free 死代码/原子写无 fsync）+ CL-201~205。
4. C6 批 B（agent_604f1a72，串行 4/5）：配对 14 类（9 ✓）→ 发现 5（**C6-101 P1** NodeTscBridge 重启无栅栏——stale 帧击穿 restart 预算/孤儿进程、C6-102 P2 RuleResultCache.save 遗留 .tmp+固定名互踩、P3×3）+ CL-401/402；nop-jq 四重守卫齐全无立案。
5. CS 抽查批（agent_4a7b6347，串行 5/5）：七形态锚点先行（C3 零命中/C7 全有界/C9 无达标缺陷）→ 发现 3（**CS-001 P1** 旧 reader EOF×respawn 竞态永久击穿 bridge、CS-002 P2 JavaTypeResolver 双查询缓存无界、CS-003 P3 XmlTagKinds 并发 id 分配冲突）+ CL-501~505；显式终止。

**Phase 2 汇总**：20 立案 = P1×4 + P2×9 + P3×7；CL 线索 15（撞号未重编口径）。已知跨批重复族（去重归 Phase 4/6）：**NodeTscBridge 三方同站点**（C1-102/C6-101/CS-001）、RuleResultCache.save 双登记（C1-105/C6-102）、LanguageRegistry（C1-104 与 2026-09 G10-01-02 同站点）、LintDeadlineExecutor（C1-101 与 CL-401 同站点）。


## Phase 3 深挖派发记录

6. C1 R2（agent_6cac8d8b，串行）：**0 新增，显式终止**——P1 同族放大闭合（全局槽写者唯一=install 本体、进程桥唯一=NodeTscBridge，九源码根全扫）；干净面抽查 3 项重演维持；新增 CL-201（xscript 资源护栏哨兵可被 catch-all 吞——**注意与 C6 批 A CL-201~205 编号冲突**，Phase 6 归并时须重编）。
7. C6 R2（agent_9450037a，串行）：**0 新增，显式终止**——进程站点唯一性修正批 B glob 缺陷后确认（ProcessBuilder/new Thread 各唯一命中）；写盘家族闭环（全单元写点恰 4 处，C6-102 失效面完备）；新增 CL-403（treesitter 查询引擎确定性缺陷——非 C6 域移交）。
8. CS R2（agent_906c7d7d，串行）：新增 1（CS-R2-001 P2 NodeTscBridge.request() error 帧无 correlation 校验无回收+BAD_FRAME 不回收+无协议版本字段——CS-001 同根因同修复面）；C5 收口（31 cache 文件逐一判定无第二无界站点）；显式终止。

**Phase 3 汇总**：21 立案 = P1×4（C1-101/C1-102/C6-101/CS-001）+ P2×10 + P3×7；三维度均有显式终止信号；CL 16 条（含编号冲突 1 处待 Phase 6 重编）。

## Phase 4 复核派发记录

9. C1 复核（agent_4636e2fe，串行 1/3，配额中断后重派 CS 前完成）：6/6 保留 0 驳回 0 降级；C1-101 P1 全实证（_executor 非 volatile/register 无同步/第二写者 XLangDebuggerInitializer/main 无 uninstall）；**NodeTscBridge 三方裁定=同一根因同一缺陷，主登记建议 C6-101**（后果枚举最全），C1-102/CS-001 转引用；CL-001/101/102/201 确认（CL-201 撞号建议重编 CL-206）。
10. C6 复核（agent_acdbdc1a，串行 2/3）：11/11 保留 0 驳回、降级 2（C6-004 P2→P3 量化链修正 ~192KB 封顶、C6-102 P2→P3 面拆分——跨进程踩踏面归 C1-105 主登记 P2）；**C6-101 确认 P1 三方主登记**；**G10 三态裁定：G10-01-02 仍成立→并入 C1-104（P2 超集）；G10-01-03 仍成立→并入 CL-204**；回填注记文本已交付；CL 编号冲突重编 CL-206 确认。
11. CS 复核（agent_53b5f5e2，串行 3/3；首次派发配额中断零产物后重派）：4/4 保留 0 驳回、降级 1（CS-002 P2→P3 在编未接线校准）；CS-001 归属改判 C6-101 转引用确认；CS-R2-001 P2 独立确认（消费侧不同层）；CL-501~507 全确认（CL-502 归并 C1-103、CL-501 归并 C1-104）。

**Phase 4 汇总**：21 实体全保留 0 驳回；降级 3（C6-004/C6-102/CS-002）+ 归属改判 2（C1-102/CS-001→C6-101 转引用，P1 本体保留）；RuleResultCache 为面拆分姊妹案（C1-105 并发面 P2 主 + C6-102 生命周期面 P3 独立，修复同点）；G10-01-02→C1-104、G10-01-03→CL-204；**P1×2 独立工单**（C6-101 代际栅栏族/C1-101 install 全局槽）；CL 唯一编号 19 条全确认 0 驳回（勘误见 merge-adjudication §三.6）。

## Phase 5 C10 派发记录

12. C10（agent_f6c31e17）：21 行矩阵——**会红 0**（不会红 18/不会可靠红 3[C6-101 族]）；**反向保护 4 处**（修复将使现有测试红——C1-101 install 全局槽钉定/EditPlanApplier 部分落地/SCRATCH 池常驻/SubtreeArena free() 机制钉死）；hang-once-then-ok 定性=时序敏感假保护；缺保护 17 项初判。
13. C10 复核（agent_d25428c5）：抽样 15 行全成立（反向保护 4/4 重演）——**PASS with corrections**（2 处登记修正：C6-001 漏登补 M-P2-6、缺口数 17→18[P1×2/P2×7/P3×9]）。

**Phase 5 汇总**：会红 0/21；反向保护 4（campaign 新高）；缺保护 18 项。
