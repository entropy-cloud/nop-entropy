# C6 资源生命周期与泄漏 — 初审（盲审批次 1/2 · 批 A）

- **审计目录**: CRA WI 6.2 / C6 维度（`ai-dev/skills/concurrency-resource-audit-prompt.md` C6 正文 + `deep-audit-prompts.md` 共享前缀 L298-374）
- **批次范围（批 A）**: `nop-tools/nop-treesitter`（92f/18,213 行，C6 主战场）+ `nop-tools/nop-refactor`（33f/4,010 行）
- **基线**: `_tmp/cra-baseline-nop-tools-parse.txt`（@ 0d131797dd，C6 native/arena 10 文件、close 点 4、ThreadLocal 2）
- **审计日期**: 2026-10-22 · 初审第 1 轮（盲审，未读 ai-dev/audits/plans/bugs/lessons/backlog 任何既有记录）
- **CL 前缀**: `nop-tools-parse-CL-0NN`；维度外线索 CL-201 起
- **现状标注**: 初审阶段不标注（新发现/已知未修复判定留待复核阶段）

---

## 一、资源获取点配对表

盘点口径：C6 执行步骤 1-6 全部资源类型；`grep -rn "\.free(\|\.close(\|Closeable\|AutoCloseable\|try (\|ThreadLocal\|createTempFile\|ServiceLoader\|Files\." 覆盖两模块 src/main 全量。

| # | 资源 | 获取点（file:line） | 释放/配对点 | 路径覆盖（成功/异常/取消/超时） | 判定 |
|---|------|--------------------|------------|-------------------------------|------|
| P1 | Language blob InputStream | `language/Language.java:483` `fromClasspath` | try-with-resources L483-491 | 全路径（含 IOException → TreeSitterException） | 配对 ✅ |
| P2 | SubtreeArena（int[] 列×18 + cache[]） | `TSParser.java:60`（parse）、`TSParser.java:155`（parseIncremental） | 无显式释放；`TSTree.adopt/snapshot` 接管 → GC | parse 抛错（如操作上限 L144、无完整树 L165）→ arena 随 GLRParser 弃 | GC 依赖（声明设计，见 C6-005） |
| P3 | TSTree / compat TSTree.delete() | `TSParser.java:65,159`；`compat/TSTree.java:48-50` | GC（delete() 为声明 no-op） | — | GC 依赖 ✅，compat 契约自洽 |
| P4 | ScannerVM ThreadLocal 池（POOL） | `scanner/ScannerVM.java:93,96-100` | finally L111-118 置空 program/source/validSymbols 引用；VM 壳 + int[] 栈跨调用驻留 | 异常路径（execute 抛 TreeSitterException）也走 finally | **部分配对**（见 C6-003） |
| P5 | TSNode SCRATCH 游标 + SCRATCH_DEPTH | `TSNode.java:112-113`，borrowScratch L115-131 | releaseScratch L133-145，全部 8 个访问器 try/finally 成对（L151-260）；depth==0 时 `cursor.release()`（L142）置空 tree/nav | 嵌套访问器深度守卫（depth 计数）覆盖重入与异常 | 配对 ✅（引用面），但数组不缩容（见 C6-004） |
| P6 | TSTreeCursor.release() 契约 | `cursor/TSTreeCursor.java:89-93` | resetTo(L101) 前调用导航方法为 documented undefined（L86-88） | — | fail-fast 缺口（P3 级契约不一致，并入 C6-004 附注） |
| P7 | ExternalScanner（Python/Bash 实例） | `GLRParser.java:805` `language.newExternalScanner()`（每 parse 一个，懒创建） | 无 close；纯内存状态（ArrayDeque/byte[]），随 GLRParser GC | scanner.serialize/deserialize 状态经 versions[].externalScannerState 配对（L811-819） | GC 依赖 ✅（无系统资源） |
| P8 | ServiceLoader（语言 provider） | `provider/DefaultTreeSitterLanguageProvider.java:91`（synchronized 内一次） | 迭代完成即弃；self-skip 防递归（L93） | — | ✅ |
| P9 | Language 缓存 | `DefaultTreeSitterLanguageProvider.java:38` ConcurrentHashMap | 进程生命周期；键空间有界（7 builtin + custom provider 声明名） | 检查-然后-put 竞态为 last-write-wins，双实例均有效 | 有界 ✅ |
| P10 | Language.validatedScannerProgram 惰性校验 | `Language.java:777-784` | volatile 布尔，竞态下重复校验（幂等） | — | 良性竞态 ✅（C1 域，记 CL-205） |
| P11 | 临时文件 | `EditPlanApplier.java:163` `createTempFile`（目标目录内） | finally L183-193 `deleteIfExists`（!moved 时），清理失败仅 LOG.warn 不掩盖主异常 | 成功/失败/AtomicMoveNotSupported 全覆盖 | 配对 ✅ |
| P12 | 原子写（rename） | `EditPlanApplier.java:171-174` write + ATOMIC_MOVE | — | 失败 fail-closed（目标保旧内容） | ✅（无 fsync → C6-006） |
| P13 | Files.walk Stream | `NopRefactorBizModel.java:396` | try-with-resources | IOException → NopRefactorException | 配对 ✅ |
| P14 | readAllBytes（refactor 读面） | `NopRefactorCli.java:136`；`NopRefactorBizModel.java:150,255` | JDK 内部关闭；pre-read cap（Files.size）+ post-read backstop 双闸 | IOException → fail-closed abort | ✅ |
| P15 | 多文件落地批（refactor apply 面） | `RefactorOperationRunner.java:58-94` 逐文件 `EditPlanApplier.apply` | **无批级回滚**；失败报错列出已落地文件 | mid-apply 失败/verify 失败 → fail-loud 部分落地 | **F2 半成功**（见 C6-001/C6-002） |
| P16 | GLRParser 内部结构（GSS/Version/SubtreeList/childrenScratch） | `GLRParser.java:101-106,385,1501` | parse 实例私有，parse 结束整体 GC | 抛错路径（操作上限/表异常）同弃 | ✅ |

无「只有 add 没有 remove」的注册点：两模块 main 无 listener/register 类 API（`grep register/addListener` 仅命中 LanguageRegistry.register——per-request 实例内 HashMap，随 registry 实例弃）。

---

## 二、发现条目

### [C6-001] 守卫后置：re-parse 守卫在写盘之后执行，回滚窗口内破损内容在盘且崩溃后无法恢复

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/EditPlanApplier.java:88-101`（写点在 `:89`、`:96`；守卫在 `:92-94`；`atomicWrite` `:159-195`）
- **证据片段**:
  ```java
  byte[] next = spliceAll(source, merge.applied());      // L87 内存合成
  if (!dryRun) {
      atomicWrite(file, next);                            // L89 先落盘
  }
  int currentErrorNodes = countErrorNodes(language.parse(source));  // L92 守卫在后
  int nextErrorNodes = countErrorNodes(language.parse(next));
  if (nextErrorNodes > currentErrorNodes) {
      if (!dryRun) {
          atomicWrite(file, source);                      // L96 回滚写
      }
  ```
- **严重程度**: P2
- **并发场景**: 单写者顺序语义本身无数据竞争；两个执行面交错时成立——(a) GraphQL worker 线程执行 `Refactor__applyRewrite`（写盘）与另一请求/进程并发读取同一目标文件；(b) 写-回滚间隙进程被 kill -9 / OOM。
- **触发时序**: T1（apply 线程）执行到 L89 `atomicWrite(file, next)` 完成，磁盘上是守卫尚未放行的内容 → T2（读取方）L150 `Files.readAllBytes` 读到语法破损内容 → T1 执行到 L94 判定 `nextErrorNodes > currentErrorNodes` 后才在 L96 回写原内容。崩溃变体：T1 在 L89 与 L96 之间被 kill → 被守卫拒绝的内容永久在盘，且无任何标记/日志指示回滚未完成——与类 javadoc L71-72「`false` writes atomically and restores the input content when the re-parse guard detects a syntax break」的承诺不符。注意守卫输入 `source`/`next` 均已在内存，先守卫后写盘零成本可达。
- **现状**: 写盘先于守卫，回滚依赖第二次写盘成功；两次写之间的窗口无崩溃恢复语义。
- **风险**: 并发读取方拿到破损内容（即使随后被回滚）；崩溃路径违背声明的 restore 契约；FixApplier 多 pass 场景窗口按 pass 数叠加。
- **建议**: 调整顺序为「merge → splice → 内存守卫（L92-94 前移）→ 守卫通过才 `atomicWrite(next)`」；删除 L96 的回滚写（守卫前置后不再需要）。`EditPlanApplier` 位于 nop-lint（批 A 范围外的 owner），本条以 nop-refactor 调用面立案，修复时同步通知 nop-lint owner。
- **失效面枚举**（F3 同族清扫）: `EditPlanApplier.apply` 全部调用点——(1) `RefactorOperationRunner.java:61-62`（rewrite/rename 共用，本批范围，窗口 × 批内文件数）；(2) `FixApplier.java:129`（multipass，nop-lint，同一 apply 语义、同一窗口，状态登记为同族未修）。两处均无守卫前置。
- **信心水平**: 确定（顺序可从代码直接判读，无假设）
- **误报排除**: 不是「低频路径粗锁」类误报——这不是并发正确性问题而是失败路径资源/内容一致性；也不是已声明边界：`EditPlanApplier` L24-25 明确声明「rollback on a syntax break」，而该回滚自身不可崩溃原子，声明与实现有缝隙；守卫前置是零代价修复，非「看起来不优雅」。
- **复核状态**: 已复核（WI 6.2 C6 独立复核）——**保留，P2 维持**；修复归属确认为 nop-lint owner（见维度复核结论第四节）。

### [C6-002] RefactorOperationRunner javadoc「lands atomically」与部分落地无回滚实现相矛盾；批级崩溃恢复语义未声明

- **文件**: `nop-tools/nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/operation/RefactorOperationRunner.java:43-47,63-71,103-110`
- **证据片段**:
  ```java
  * Runs the operation through the four segments. {@code dryRun = true}
  * computes and verifies everything but writes nothing (the WI4 entry's
  * dry-run); {@code false} lands atomically.            // L43-45
  ...
  } catch (RuntimeException e) {
      if (!dryRun && !landed.isEmpty()) {
          throw new NopRefactorException("apply failed at '" + planned.path()
              + "' after " + landed.size() + " file(s) already landed ("  // L66
  ```
- **严重程度**: P2
- **并发场景**: 单线程批处理；「原子」承诺在两类路径上不成立——(1) mid-apply 异常：已落地文件保持新内容、未落地不尝试，仅错误消息列出（类注释 L27-32 自己声明了这一点）；(2) 进程崩溃（kill -9 / 断电）：任意子集文件已改、无 journal/marker、重启后不可检测。
- **触发时序**: T1 依次对 plan.files() 执行 apply，第 k 个文件抛 RuntimeException → L63-71 进入 catch，`landed` 已含 k-1 个文件 → 抛错结束，磁盘上 k-1 个文件是新内容、其余是旧内容，无整体回滚、无中间态标记。崩溃变体：T1 在第 j 个文件 `atomicWrite` 返回前被杀 → 子集落地且运行方无从知晓边界。
- **现状**: 实现为 fail-loud 部分落地（自身注释声明），但 `run()` javadoc L45 写「lands atomically」，两处契约互相矛盾；崩溃恢复语义（无恢复，依赖外部 VCS）完全未声明。
- **风险**: 调用方（GraphQL 客户端、CLI 脚本、未来 operation 实现）按「原子」语义编写依赖（如失败即假设无副作用）→ 中途失败后状态误判；崩溃后文件集处于不可审计的混合状态。
- **建议**: 二选一并保持单一事实源：(a) 实现真正的批级原子性（先写 intent journal——平台有 `io.nop.commons.concurrent.lock.LocalFileLock` 与临时文件设施——或统一收集 diff 后单点提交）；(b) 承认 fail-loud 部分落地为声明边界：改 javadoc 措辞、在 RefactorResult/错误负载中机器可读地携带 landed 列表、javadoc 显式声明崩溃语义「undo via VCS」。禁止保留两处矛盾表述。
- **失效面枚举**: 同一 runner 服务全部四个入口——`previewRewrite/applyRewrite`（NopRefactorBizModel L81-88）、`previewRename/applyRename`（L91-98）、CLI apply（NopRefactorCli L152）——语义完全相同，无一豁免；dryRun=true 各入口不受影响（不写盘）。
- **信心水平**: 确定（javadoc 与实现同文件可对照）
- **误报排除**: 不是对「文档措辞不优雅」的吹毛求疵——「原子」是行为承诺词，与 F2 半成功家族直接对应；类注释另一处（L27-32）已声明非原子，说明作者本人对契约的认知不一致，属契约漂移而非既定边界。
- **复核状态**: 已复核——**保留，P2 维持**（:45/:30/:65 契约矛盾三点复核实证）。

### [C6-003] ScannerVM ThreadLocal 池 set-无-remove：壳对象跨请求常驻 worker 线程，未用平台 NamedThreadLocal 惯例、无清理钩子

- **文件**: `nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/scanner/ScannerVM.java:93-118`
- **证据片段**:
  ```java
  private static final ThreadLocal<ScannerVM> POOL = new ThreadLocal<>();  // L93
  ...
  ScannerVM vm = POOL.get();
  if (vm == null) {
      vm = new ScannerVM(program, source, position, validSymbols);
      POOL.set(vm);                                        // L99 set 无 remove
  } else {
      vm.reset(program, source, position, validSymbols);
  }
  ...
  } finally {
      // Drop the input references so the pooled VM does not pin the last
      // scanned source bytes across calls ...
      vm.program = null; vm.source = null; vm.validSymbols = null;  // L115-117
  ```
- **严重程度**: P3
- **并发场景**: 池化 HTTP worker 线程经 `TreeSitterBizModel.parseTreeSitter`/shell 面（bash/python 外部扫描器路径）触发；每个触碰过解析的线程永久持有一个 ScannerVM。
- **触发时序**: 非交错型——常驻型：线程 T 执行一次含外部扫描器语言的 parse → L99 `POOL.set(vm)` → 请求结束无任何 remove → T 在线程池内存活期内（web 容器常为数天）持有 VM（2 个定长 int[]：operandStack 256×4B + callStack maxCallDepth×4B ≈ 1-2KB + 头部）。
- **现状**: 引用级清理已做（重引用置空，避免 pin source bytes——作者有明确意识，见 L112-114 注释），但按 F8 口径 set/remove 不对称；未用平台 `io.nop.commons.concurrent.thread.NamedThreadLocal`（可观测/清理惯例），池无容量或生命周期钩子。
- **风险**: 有界但永久的 per-thread 驻留（线程池规模 × ~2KB）；诊断不可见（无名 ThreadLocal，heap dump 难归属）；若未来 VM 增加新引用字段，易遗忘补充 L115-117 的清理清单（该清单是手工维护的）。
- **建议**: 换用 `NamedThreadLocal`（平台惯例）并在清理块保持重引用置空；或将池改为每 parse 实例局部复用（GLRParser 已是 per-parse 对象，VM 随其分配即可，跨 parse 复用收益仅省两次小数组分配）。清理建议与 C6-004 一并处理。
- **失效面枚举**: 本模块 main 内 ThreadLocal 全量 3 处——(1) `ScannerVM.POOL` L93（本条）；(2) `TSNode.SCRATCH` L112（C6-004）；(3) `TSNode.SCRATCH_DEPTH` L113（随 SCRATCH 生命周期，无独立泄漏面）。基线 C1 计数 2 与实际 3 的差异：基线 grep 口径未覆盖 `new ThreadLocal<>()` 无初值形式，登记口径勘误。
- **信心水平**: 确定（驻留事实确定；风险定级为有界放大）
- **误报排除**: 不是「synchronized/CHM 原语使用」类误报——F8 是注册/注销对称性问题，ThreadLocal.set 与 remove 的不对称正是该家族形态；也不是无界增长夸大——保留量有界已如实量化，P3 定级与量级匹配。
- **复核状态**: 已复核——**保留，P3 维持**（POOL get/set 无 remove、NamedThreadLocal 可达均实证）。

### [C6-004] TSNode SCRATCH 池化游标 release() 只清引用不缩容：深树一次遍历后 frame int[] 永久驻留 worker 线程

- **文件**: `nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/cursor/TSTreeCursor.java:89-93`（release）、`:552-572`（pushFrame/grow）；`nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/TSNode.java:112-145`（池）
- **证据片段**:
  ```java
  public void release() {        // TSTreeCursor.java:89
      tree = null;
      nav = null;                // 引用清了……
      size = 0;                  // ……但 fNode/fCtx/fIdx/fStruct/fAlias 保持峰值容量
  }
  ...
  private void pushFrame(int nodeId, int contextId, ...) {   // :552
      if (size == fNode.length) {
          fNode = grow(fNode); fCtx = grow(fCtx); fIdx = grow(fIdx);
          fStruct = grow(fStruct); fAlias = grow(fAlias);    // 五列同步倍增
      }
  ```
- **严重程度**: P2
- **并发场景**: 与 C6-003 同为池化线程常驻型：worker 线程处理一次 `parseTreeSitter`/shell 请求即入池；`releaseScratch`（TSNode L133-145）在 depth 归零时调用 `cursor.release()`，但五个 frame 列的峰值容量随线程存活保留。
- **触发时序**: 非交错型：T1 对一棵深嵌套源（如 1MB 深嵌套括号/JS 回调链，树深 D；`resetTo` 的 5000 祖先上限 L115-117 只约束「从深节点起锚」，从 root 导航下降无此上限）执行 `childCount()/child(i)` 等结构访问器 → pushFrame 逐层下推至树深 → `fNode` 等五列倍增至 ≥2^⌈log2 D⌉ 项（D=10^5 时每列 512KB，五列 ≈ 2.5MB）→ 请求结束 `release()` 置空引用，数组容量不减 → 线程池存活期内每触碰线程常驻该峰值。
- **现状**: 作者对「池化线程 pin 引用」有防御（release() javadoc L80-88 明确以「long-lived thread does not pin the last parsed tree's arena and source bytes」为目标），但只防御了引用面，漏了容量面——防御目标与实现不完全对称。
- **风险**: 对抗性/意外深输入一次即给每个 worker 线程留下百 KB~MB 级永久驻留；多实例部署 × 池规模放大；同类问题是 ScannerVM（C6-003）选择显式清引用的同一动机，说明该池设计目标就是抗驻留，容量面是遗漏而非取舍。
- **建议**: `release()` 中将五个 frame 列重置为初始容量 8（或置 null 由 resetTo 重建）；一行级修复，与 C6-003 的 NamedThreadLocal 改造同批落地。
- **失效面枚举**: 游标池仅 SCRATCH 一处（TSNode L112）；同族「倍增后不缩容」结构全模块核对——`TSTreeCursor.ancestors` L57（resetTo 内增长，随游标实例 GC，不入池 → 无常驻面，豁免）；`GLRParser.childrenScratch/stackScratch` L385/L1501（parse 实例私有，随 parse GC，豁免）；`ReuseCursor` idStack L63-87（per-parse，豁免）。入池且不缩容的只有 SCRATCH。
- **信心水平**: 确定（增长与驻留可从代码直接判读；触发需深树输入）
- **误报排除**: 不是「有界声明存在的缓存」误报——该池无任何容量声明，且模块自己的 javadoc 声明了反驻留目标；也不是理论风险——触发链（一次深输入 + 池化线程）具体可构造。
- **复核状态**: 已复核——**保留，降级 P2→P3**：机制实证成立，但量化链修正——池化路径受 `resetTo` 5000-hop 守卫（TSTreeCursor.java:115-117，经 TSNode.borrowScratch:124 每次借用触发）约束，可达峰值 ~192KB/线程（对抗性深锚点）、常态 KB 级，MB 级不可达（无上限下降发生在不入池的新建游标上）；详见维度复核结论第五节。

### [C6-005] SubtreeArena 显式释放机制（free/freeList）在 main 零调用方：释放面为死代码，长持 TSTree 完全依赖 GC，adopt 路径保留全部 parse 期分配

- **文件**: `nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/subtree/SubtreeArena.java:210-220`（free）；`nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/TSParser.java:59-66`（adopt）、`:155-159`（snapshot）
- **证据片段**:
  ```java
  // TSParser.parse —— adopt 直接接管整个 parse arena：
  SubtreeArena arena = new SubtreeArena(expectedNodes(language, source));
  int rootId = GLRParser.parse(language, arena, source, options);
  language.noteNodeCount(arena.size(), source.length);
  // The parse arena is freshly created ... ownership transfers to the
  // tree without a deep copy.
  return TSTree.adopt(language, arena, rootId, source);
  ```
  配套口径：`grep -rn "\.free(" nop-treesitter/src/main/java` → 仅 `SubtreeArena.java:213` 定义本身，零调用方（调用全部在 src/test：SubtreeArenaTest、ErrorNodeRenderingTest）。
- **严重程度**: P3
- **并发场景**: 无并发专属面；生命周期设计盘点结论。
- **触发时序**: 非交错型：parse 期间 GLR 的版本竞争淘汰、错误恢复轮次、链容器重建会产生从 rootId 不可达的节点；`adopt` 不做可达性裁剪 → TSTree 的保留堆 = 全部曾分配节点（Subtree 快照对象 + 18 列容量），而非活树大小。错误密集输入下保留/活树比放大；操作上限 4_000_000（GLRParser L143-146）构成单次 parse 的分配上界（有界但可观）。
- **现状**: 显式释放 API 存在（free + freeList + use-after-free fail-fast 守卫）但主路径零使用；TSTree/compat.delete() 均为 no-op 声明。GC 依赖本身是自洽选择（不可变快照、无 native），本条立案的是「释放机制死代码 + adopt 保留放大」的组合，不是「缺 close」。
- **风险**: 长持 TSTree 的调用方（增量解析会话、未来缓存层）在错误密集源上内存驻留显著高于活树；死代码 free() 误导后续开发者以为存在显式释放契约。
- **建议**: 三选一并写明 owner doc：(a) 删除 free/freeList 死代码，文档声明「arena 生命周期 = GC，adopt 保留全量分配」；(b) snapshot 化 adopt（可达性裁剪，代价是深拷贝）或增加 compact()；(c) 若保留 free()，在 owner doc 登记其测试专用定位。保留放大问题移交 C5 维度详算（本条只登记生命周期归属）。
- **失效面枚举**: adopt 唯一调用点 `TSParser.parse` L65（快路径，全部 parse 入口收敛于此）；snapshot 路径 `TSParser.parseIncremental` L159 已做可达拷贝（正确面）；`TSTree.copy` L65-81 为 snapshot 内部实现。同族无其他入口。
- **信心水平**: 确定（死代码事实确定；保留放大为结构推演，量化移交 C5）
- **误报排除**: 不是「无状态单例被并发调用」类误报，也不是把 GC 管理当缺陷——本条明确承认 GC 依赖为声明设计；立案点是死代码 + 保留语义未声明，属可核查的结构事实。
- **复核状态**: 已复核——**保留，P3 维持**（free() main 零调用方、adopt 无深拷贝注释 :62-64 实证）。

### [C6-006] 原子写无 fsync：rename 原子性已达成，持久性未设防（F6 家族）

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/EditPlanApplier.java:159-195`
- **证据片段**:
  ```java
  Files.write(temp, content, StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING);          // L171-172 无 SYNC/force
  Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);                // L173-174
  ```
- **严重程度**: P3
- **并发场景**: 断电/内核崩溃窗口（非线程交错）：rename 元数据可先于 temp 文件数据块落盘（ext4 data=ordered 等常见配置下），崩溃后 target 可能是旧内容（rename 未及）、零块/截断内容（rename 已持久、数据未及）或正确新内容。
- **触发时序**: T1（apply 线程）执行 L171 write（页缓存）→ L174 ATOMIC_MOVE 返回 → 掉电 → 恢复后 target 内容 ∈ {旧, 空/截断, 新}，无屏障保证为「新」。模块自建 VCS 之外无副本。
- **现状**: atomicWrite 的声明是「atomic」，未声明 durable——原子性承诺已兑现；缺的是可选的持久性（`FileChannel.force` / `Files.write` 加 SYNC 选项 + 父目录 fsync）。
- **风险**: 重构工具的用户预期「apply 成功 = 内容已安全落盘」；崩溃后文件损坏属数据损坏面。定 P3 因：模块无 durable 声明（不属声明违约）、运行环境通常有 VCS 兜底、触发需掉电级故障。
- **建议**: 若按 durable 语义收敛：write 改 `StandardOpenOption.SYNC`（或 FileChannel.force）+ move 后父目录 fsync；否则在 javadoc 显式声明「atomic but not durable，崩溃语义未定义」，消除 F6 检查项的悬置。
- **失效面枚举**: 全模块落盘点共 2 处——`EditPlanApplier.atomicWrite` L159-195（唯一写实现，C6-001 同点）；`Ts2Java.java:39`（`Files.write(outBlob, blob)`，build-time 生成器，产物可再生，豁免）。无第三写点。
- **信心水平**: 很可能（fsync 语义依赖具体文件系统配置，代码事实确定）
- **误报排除**: 不是把「flush ≠ fsync」泛化套用——本条先确认了模块只声明 atomic 未声明 durable，定级与声明缺口匹配；与 C6-001 区分：那是写序问题（同进程可见），这是屏障问题（掉电可见）。
- **复核状态**: 已复核——**保留，P3 维持**（无 SYNC/force grep 实证、javadoc 仅声明 atomic）。

---

## 三、维度外线索（CL 附记，`nop-tools-parse-CL-2NN`）

### [nop-tools-parse-CL-201] treesitter GraphQL 面无输入规模闸（对照 refactor 面双闸）
`biz/TreeSitterBizModel.java:36-40` `parseTreeSitter` 直接 parse 任意长度 source，无 max-source-size / 目标数闸（对照 `NopRefactorBizModel` L72-78 的 1MB/512 文件双闸与 `checkPreReadCap`）。无界输入 → arena 内存 + `TSTree.estimateRenderLength` L153-155 的 StringBuilder 预分配上限 256MB + 深树递归（CL-202）。C5 主责；建议补齐同款闸。

### [nop-tools-parse-CL-202] 深树递归 → StackOverflowError 族（同模块内迭代/递归实现不一致）
递归实现：`TSTree.copy` L65-81、`TSTree.writeFlat` L157、`TSTree.writeNode` L333、`TSNode.subtreeHasError` L264-279、`TreeNavigator.visibleChildCount` L228+；而 `GLRParser` 自己的 `subtreeNodeCount`/`compareTrees`/`visibleChildCount` 已特意改显式栈（L1501-1529、L706-739）。深树输入（CL-201 无闸放大）触发 SOE。C5/C7 域。

### [nop-tools-parse-CL-203] SubtreeArena javadoc「allocation-free」与实现矛盾 + adopt 保留放大
`SubtreeArena.java:11-12` 声明 "keeping the hot parser path allocation-free"，但 `allocateChildren` L142-145 每节点分配一个 Subtree 快照对象并存入 cache 列（随 arena 全量驻留，与 C6-005 保留放大叠加）。C9（分配放大）/C5。

### [nop-tools-parse-CL-204] refactor 面每请求 ServiceLoader 全扫描 + 引擎构造（无泄漏，分配热点）
`NopRefactorBizModel.java:122,210` 每请求 `LanguageRegistry.discoverDefaults()`（ServiceLoader 全类路径扫描 + 新 registry）+ L132/L271 `new LintEngine`；`DefaultTreeSitterLanguageProvider` 的 provider 发现已做进程级缓存，lint 侧 registry 未做。无资源泄漏（实例随请求弃），属 C9 串行化/分配热点线索。

### [nop-tools-parse-CL-205] Language.observedNodesPerByte volatile float 读-改-写竞态（C1 域移交）
`Language.java:762-769` `noteNodeCount` 的 max 更新在并发 parse 下丢更新（volatile 不保证 RMW 原子）；后果仅是 arena 预留估计偏低（几何增长兜底），性能级。C1 维度主责，本维仅登记线索。

---

## 四、统计

| 项 | 数 |
|---|---|
| 资源获取点配对盘点 | 16 项（P1-P16；14 项配对 ✅，2 项立案：P4→C6-003、P15→C6-001/002；P5 部分配对→C6-004） |
| 发现总数 | 6（P0×0，P1×0，P2×3：C6-001/002/004；P3×3：C6-003/005/006） |
| 维度外 CL 线索 | 5（CL-201~205） |
| F3 同族清扫覆盖 | C6-001（apply 调用点×2）、C6-002（入口×5）、C6-003/004（ThreadLocal×3）、C6-005（adopt/snapshot×2）、C6-006（写点×2）——均已逐一登记状态 |

复核移交要点：C6-001 需与 nop-lint owner 对齐修复归属；C6-002 需裁定 (a)/(b) 收敛方向；C6-005 的保留放大与 CL-201/202 建议并入 C5 维度详算。

---
 
# C6 初审追加（批 B）

- **批次**: 盲审批次 2/2（批 B = nop-tools/nop-lint + nop-tools/nop-jq）
- **审计人**: C6 维度初审子代理（盲审，未读取本维度既有产出）
- **基线**: `_tmp/cra-baseline-nop-tools-parse.txt`（@ 0d131797dd）
- **范围**: nop-lint 160f/22,543 行（nop-lint-core 126f 为主）、nop-jq 72f/7,982 行
- **口径**: live code；`_` 前缀生成物不作为对象；本模块经核实**无任何** JDBC/连接池/文件锁/信号量/listener 注册类资源（grep 证据见各条目），资源面收敛为：外部进程、临时文件、目录遍历流、reader 线程+队列、ThreadLocal、全局 executor 槽、堆 arena 解析树、jq 编译缓存。

## 资源获取点配对表（批 B 全量盘点）

| # | 资源 | 获取点（file:line） | 释放路径 | 异常/超时/取消路径核对 | 判定 |
|---|---|---|---|---|---|
| 1 | 外部 Node 进程 + stdin writer | `NodeTscBridge.spawn` L195-210（全模块唯一 `ProcessBuilder`） | `destroyProcess` L277-283（destroyForcibly） | 查询超时/崩溃/写失败/id 失配 ✓ 均回收；**握手 EOF 分支 ✗**（C6-103）；**重启无栅栏 ✗**（C6-101） | **立案** |
| 2 | reader 线程 + `inbound` 队列 | `spawn` L208-210 | daemon 线程随流 EOF 自然退出 L231-243 | 旧 reader 无 join/世代栅栏，stale 帧可越过 `inbound.clear()`（C6-101） | **立案**（随 C6-101） |
| 3 | cache 临时文件 | `RuleResultCache.save` L199-203 | `ATOMIC_MOVE` | write/move 失败不删 temp → **C6-102**；固定名 `.tmp` 并发 run 互踩 | **立案** |
| 4 | fix 临时文件 | `EditPlanApplier.atomicWrite` L163-175 | `finally` + `moved` 标记 + `deleteIfExists` L184-193，清理失败单独 warn | ✓ 全路径（含 AtomicMoveNotSupported） | 正确（家族对照组） |
| 5 | baseline 文件 | `BaselineFile.writeTo` L187-193 | 直接 `writeString` 覆写 | 非原子：中断留截断文件；load 侧 fail-closed 可恢复 | C6-102 失效面登记 |
| 6 | 目录遍历 Stream | `TargetScanner.scanDirectory` L147-149 | try-with-resources | ✓ | 正确 |
| 7 | 解析树（SubtreeArena） | `TreeSitterLanguageAdapter.parse` L84-91 / `parseIncremental` L104-124 | GC | 已核实 `SubtreeArena` 纯堆分配（无 ByteBuffer/Unsafe/direct），`TSTree` 无 native 句柄、无 close 契约；`LintTree`/`TreeCache` 随树同亡（TreeCache L1-36 javadoc 与实现一致） | 正确，无需 close |
| 8 | LSP documents 表 | `didOpen` L159 / `didChange` L181 put；`didClose` L187 remove | 协议配对 | 客户端不发 didClose 则驻留（LSP 契约内，非模块缺陷） | 正确 |
| 9 | ThreadLocal `int[1]` ×2 | `LintDeadlineExecutor` L49、`ReferentMatcher` L25-26 | `++`/`finally --` 平衡（L92-107 / L52-68） | 池线程残留固定 24B/线程，值归零无语义滞留，无可量化风险 | 无需立案（误报校准第 3 条克制） |
| 10 | 全局 XLang executor 槽 | `LintDeadlineExecutor.install` L70-76 → `EvalExprProvider._executor`（nop-core 静态，L19） | **无卸载路径** | 一次 install 永久改道全 JVM XLang 求值 | → **CL-401**（C1/C9 域） |
| 11 | jq 编译缓存 | `JqEngine` L16-17 `LocalCache("nop-jq-compile", newConfig(1000), ...)` | 平台 LocalCache 容量淘汰 | 有界（1000）；条目 = AST + `JqExecutor`，均纯堆、execute 无实例可变状态（仅 `debugSink` setter，测试缝）、无 closeable | 正确（上限/淘汰/条目持有三问均有答案） |
| 12 | jq 运行时栈/环境 | `JqEnvironment` per-apply（`JqDirectQuery` L28/40/68） | 无 OS 资源；调用深 2000/输出 1e6/中断/超时四重守卫 L104-132 | `fork` 共享 scopes/defs 为 copy-on-write（L86-92、L183-187），单线程执行模型内安全 | 正确 |
| 13 | jq 正则 Pattern | `JqRegexSupport.compile` L23-44 每次编译 | GC | 无缓存无泄漏（每调用重编译是 C9 性能候选，非 C6） | 无需立案 |
| 14 | 报告 Writer 桥 | `NopLintCli.writerOf` L169-183、`CheckMojo.logWriter` L328-350 | `close()` 为 no-op 包装 System/Maven 流，不关闭属主流 | ✓ | 正确 |
| 15 | xscript eval scope | `XScriptEngine.executeMatch` L149-150 | `IEvalScope extends IVariableScope`（非 AutoCloseable，已核实 nop-api-core L10） | 堆对象，无释放契约 | 正确 |

---

## 发现条目（编号 C6-101 起，批 B 保留段）

### [C6-101] NodeTscBridge 重启路径无栅栏：一次慢查询可永久击穿 restart 预算或遗留孤儿 Node 进程

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/NodeTscBridge.java:195-243`（辅证 L245-283）
- **证据片段**:
  ```java
  private void spawn() {
      destroyProcess();            // L196: 旧进程 destroyForcibly —— 异步生效，不 join 旧 reader
      inbound.clear();             // L197: 清队 —— 但旧 reader 之后仍会 put
      ...
      Thread reader = new Thread(this::drainPeerOutput, "nop-lint-tsc-bridge-reader"); // L208: 无世代标识
      ...
      Object answer = pollWithDeadline(config.handshakeTimeout(), "handshake");        // L212
      if (answer == EOF) {
          state = State.EXHAUSTED;  // L214: 且此分支不 destroyProcess（对照 L224 有效路径）
          ...
  private void drainPeerOutput() {
      try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
              inbound.put(line);    // L236: 旧 reader 无 "process 还是当前代" 校验
  ```
- **严重程度**: P1
- **并发场景**: 桥的唯一调用线程（全部方法 synchronized 串行）与**每代进程各一个**的 `nop-lint-tsc-bridge-reader` daemon 线程；竞态发生在"第 N 代 reader 线程"与"第 N+1 代 handshake 等待"之间。
- **触发时序**: 分支 A（stale 数据帧）——T1 调用线程在 `request` L157 `pollWithDeadline` 超时（tsc 慢，响应帧**已 flush 进 pipe buffer**）；L254 `handleProcessFailure` → `destroyForcibly`（SIGKILL 异步生效）→ restartsLeft 2→1、state=NEW → 抛 TIMEOUT。T1 继续下一文件的 L2 查询 → `ensureReady` → `spawn`：L196 destroy（no-op）、L197 `inbound.clear()`、L201 启动新进程。T2（旧 reader 线程）从旧进程 pipe 读出滞留的响应行，L236 `inbound.put` —— **发生在 clear 之后**。交叠后果：新进程 ready 帧之前，handshake L212 poll 到旧响应帧 → L219 `validateReady` 失败（type≠"ready"）→ catch L224 `destroyProcess` **杀死健康新进程** + L225 state=EXHAUSTED——**一次慢查询永久打空桥，剩余 restart 预算整体作废**。分支 B（stale EOF）——旧进程被杀时 pipe 为空，旧 reader 的 `finally` L241 `offer(EOF)` 晚于 L197 的 clear → handshake 读到 stale EOF → L213-217 置 EXHAUSTED，且该分支不 destroyProcess——**全新健康 node 进程成为孤儿常驻至 JVM 退出**。stale id 与新请求 id 必然不同（`nextId` 单调递增不重置），故不存在"错答被接受"的更深后果，损害止步于可用性与进程泄漏。
- **现状**: 超时回收机制按 javadoc 契约（L25-29 "recycles the process through the restart budget"）设计，但重启时对上一代 reader 线程和 pipe 残留流量无任何栅栏；`destroyForcibly` 后旧 pipe 中**已写入未消费**的字节必然被旧 reader 排空进队列，而这恰是超时场景的常态（超时=响应迟到）。
- **风险**: restart 预算（默认 maxRestarts=2）在真实慢查询下几乎必然第一次就跳到终态 EXHAUSTED，L2 类型查询对本次 lint 全量降级（fail-visible，不产生错答）；分支 B 额外遗留一个常驻孤儿 node 进程（生产环境长期运行的 LSP/GraphQL 宿主内按次累积）。两者都使"有界重启自愈"契约在它最该生效的场景（偶发慢查询）失效。
- **建议**: `drainPeerOutput` 在线程启动时捕获 `Process` 局部引用，`put` 前比对 `this.process == captured`（一行级栅栏，同时封死分支 A/B）；或引入递增 generation 计数入队校验；`spawn` 在 clear 前 await 旧 reader 退出（带短超时）。握手 EOF 分支（L213-217）补 `destroyProcess()` 与 L224 对齐（见 C6-103）。
- **失效面枚举**: `request`/`spawn` 全部失败回收路径逐一核对——写失败 L153-156 ✓ 回收；EOF L159-163 ✓ 回收；超时 L254-256 ✓ 回收；correlation id 失配 L169-171 ✓ 回收；validateReady 失败 L220-227 ✓ 销毁+终态（设计声明）；**握手 EOF L213-217 ✗ 不销毁（拆出 C6-103）**；**decode BAD_FRAME L164 ✗ 不回收（拆出 C6-104）**。同族持有者清扫：全 nop-tools main 代码 `ProcessBuilder` 仅此一处（grep 证据），无兄弟进程持有类。
- **信心水平**: 很可能
- **误报排除**: 非原语误用报（不是"synchronized/queue 不安全"）；有具体 T1/T2 交错与两条可复现分支；现有测试 `hang-once-then-ok`（`NodeTscBridgeTest` L109-129）通过恰因挂起 peer **未写出任何字节**且本地 SIGKILL→EOF 快于下一次 spawn 的 clear——stale-data 与 late-EOF 两分支无测试覆盖，属"时序敏感测试假保护"而非已被验证的健壮行为。
- **复核状态**: 已复核——**保留，P1 维持；确认为 NodeTscBridge 三方（C1-102/C6-101/CS-001）唯一主登记**（全要素实证，C1-102 独有「诊断误导」侧写并入验收标准；见维度复核结论第二节）。

### [C6-102] 文件写家族原子性/清理不一致：RuleResultCache.save 失败遗留 .tmp；BaselineFile 非原子写

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/RuleResultCache.java:197-208`
- **证据片段**:
  ```java
  public void save() {
      try {
          Path temp = file.resolveSibling(file.getFileName() + ".tmp");
          Files.writeString(temp, JsonTool.serialize(artifact, false),
                  StandardCharsets.UTF_8);
          Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                  StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
          throw new NopLintException("cannot write lint cache file '" + file
                  + "': " + e.getMessage(), e);     // temp 不删除、无 moved 标记、无清理
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 单线程 CLI 写出；次生并发面——固定 temp 名 `X.tmp`（非 `createTempFile` 唯一名）使共享同一 `--cache` 路径的两个并发 run 互踩同一 temp 文件（A 的 move 可能搬走 B 刚写入的内容）。
- **触发时序**: T1 writeString 成功 → move 失败（目标被其他进程占用/权限/文件系统不支持 ATOMIC_MOVE 抛 `AtomicMoveNotSupportedException`，是 IOException 子句被同一 catch 吞入）→ 异常抛出，`X.tmp` 遗留。下次运行同名 temp 被覆盖不累积，但遗留物持续存在于 CI 工作区。
- **现状**: 同模块 `EditPlanApplier.atomicWrite`（L159-195）对同构风险有教科书式实现——`moved` 标记 + `finally deleteIfExists` + 清理失败单独 `LOG.warn` 不掩盖主异常；本类未复用该模式，注释声明的 "atomic write (temp file + atomic move)" 只覆盖成功路径。
- **风险**: 失败路径遗留 `.tmp` 垃圾（每次失败至多一个，有界）；并发 run 场景下固定 temp 名造成内容互踩（cache 产物损坏——虽然 load 侧 fail-closed 会拒绝，后果是 CI 报错而非静默错答）。与 fix 写路径的质量标准不一致是真实的维护成本。
- **建议**: 最小修复——`save()` 加 `boolean moved` + `finally` 清理（照抄 EditPlanApplier L169-194）；根因修复——把 atomicWrite 上提为 cli/fix 共享的模块内写原语，三处复用（见失效面）。
- **失效面枚举**（grep `\ createTempFile|Files.move|writeString` 全 nop-tools main，命中仅 3 处）: `EditPlanApplier.atomicWrite` L159-195 ✓ 原子+清理；`RuleResultCache.save` L197-208 ✗ 原子但无清理（本条主证）；`BaselineFile.writeTo` L187-193 ✗ **直接非原子覆写**——run 中途被 kill 留截断 baseline，下次 `load` fail-closed 报错可恢复（亚 P3，随本条同修）。
- **信心水平**: 确定
- **误报排除**: 不是"风格不一致"立案——move 失败路径的资源（temp 文件）确实无释放配对，且家族内正确实现已在，属 F8 清理不对称；后果有界故 P2 而非 P1。
- **复核状态**: 已复核——**保留，降级 P2→P3（面拆分）**：与 C1-105 为同站点姊妹案——跨进程踩踏面（固定 .tmp 名）归 C1-105 主登记（P2），本案转资源生命周期面（失败路径 temp 清理缺失 + BaselineFile 非原子写，均有界）定 P3；修复同点合并落地。见维度复核结论第三节。

### [C6-103] 握手 EOF 分支不销毁进程：dead Process 句柄与 pipe fd 滞留

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/NodeTscBridge.java:212-217`
- **证据片段**:
  ```java
  Object answer = pollWithDeadline(config.handshakeTimeout(), "handshake");
  if (answer == EOF) {
      state = State.EXHAUSTED;                       // 直接终态
      throw new TscBridgeUnavailableException("tsc bridge peer exited before the ready frame "
      ...
  try {
      TscProtocol.validateReady(TscProtocol.decode((String) answer));
  } catch (RuntimeException e) {
      ...
      destroyProcess();                              // L224: 对照组——有效路径失败会销毁
      state = State.EXHAUSTED;
  ```
- **严重程度**: P3
- **并发场景**: 单调用线程；peer 进程在 ready 帧前自行退出（helper script 不可导入等已声明的环境故障）。
- **触发时序**: T1 spawn 成功（process/peerInput 已赋值）→ peer 退出 → reader 读到 EOF 入队 → handshake 消费 EOF → L214 仅置 EXHAUSTED 即抛出，`destroyProcess` 未调用——`process`/`peerInput` 引用滞留，stdin pipe 写端 fd 在 Process 对象 GC 前不释放；EXHAUSTED 是终态，之后无人再调 destroy，仅 `close()` 能兜底而生产无强制 close 点（见 C6-105）。
- **现状**: 与 L218-227 的 validateReady 失败路径（先 destroy 再终态）不对称；EOF 语义上同样意味着"此进程不可用"。
- **风险**: 每桥实例至多一次、量级为单个死进程对象 + 两个 pipe fd，GC 后回收——低危，但属同一回收策略族的不一致面。
- **建议**: EOF 分支补 `destroyProcess()`（对已死进程是安全幂等操作，`destroyProcess` 有 null 检查）。
- **失效面枚举**: 见 C6-101 失效面 7 条清单——本条为其中唯一"回收策略缺失"的 EOF 面；C6-101 修复若采用统一失败出口则本条一并收敛。
- **信心水平**: 确定
- **误报排除**: 非"进程已死无需销毁"的过度设计指控——销毁的是 **JVM 侧句柄**（Process 对象/pipe fd/peerInput writer），不是为尸体补刀；与同文件 L224 的既有约定不一致才构成发现。
- **复核状态**: 已复核——**保留，P3 维持**（:213-217 vs :224 对照实证；随 C6-101 修复收敛）。

### [C6-104] decode BAD_FRAME 不回收进程：协议失步恢复延迟一轮并多烧 restart 预算

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/NodeTscBridge.java:164`
- **证据片段**:
  ```java
  Map<String, Object> frame = TscProtocol.decode((String) answer);   // 可抛 BAD_FRAME，不回收
  if (TscProtocol.isOk(frame)) {
      ...
      if (!Integer.valueOf(id).equals(answerId)) {
          handleProcessFailure("correlation id mismatch");           // 对照：id 失配立即回收
          throw new TscQueryException("BAD_FRAME", ...);
  ```
- **严重程度**: P3
- **并发场景**: 调用线程与 peer 进程 stdout 流；peer 输出一行非 JSON 垃圾（crash 前的半截缓冲、stderr 混流等）。
- **触发时序**: T1 request 读到垃圾行 → L164 `decode` 抛 `TscQueryException(BAD_FRAME)` 直接出 `request`，进程保持 READY → peer 对在途请求的真实响应随后到达 → 下一次 request 消费到**上一请求的迟到响应** → id 失配 → 此时才 `handleProcessFailure` 回收。恢复晚一轮，且多消耗一个 restart 预算槽。
- **现状**: 回收策略不一致——结构性失配（id 失配）立即回收，解析失配（decode 抛错）不回收；`garbage-response` 测试（`NodeTscBridgeTest` L95-106）只钉定"抛 BAD_FRAME"，未钉定回收语义。
- **风险**: 垃圾行后紧跟正常响应时，一次 peer 抖动消耗两个预算槽（回收两代进程）；预算仅 2，连续抖动提前进入永久 EXHAUSTED。
- **建议**: decode 失败同样走 `handleProcessFailure`（流完整性已不可信，与 id 失配同级）；或在测试中钉定两种失败面的回收一致性。
- **失效面枚举**: 同 C6-101 七条清单；本条与 C6-103 同为"回收策略不一致"面，修复应统一失败出口一次收敛三案（101 栅栏除外）。
- **信心水平**: 很可能
- **误报排除**: 非"异常不该抛"的口径——结构化 BAD_FRAME 抛出正确；立案点是**失败分类与回收动作的映射不完整**，有 id 失配路径作同文件对照，非理论洁癖。
- **复核状态**: 已复核——**保留，P3 维持**（:164 decode 在回收映射外、TscProtocol.decode:41-45 抛 BAD_FRAME、garbage-response 测试 :95-106 只钉抛错均实证）。

### [C6-105] TscTypeResolver 生命周期所有权无生产接线：AutoCloseable 契约零执行者

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/TscTypeResolver.java:20-23, 99-105`（辅证 `CheckRunner.java:196`、`RuleTestRunner.java:69,95-131,290`）
- **证据片段**:
  ```java
  // TscTypeResolver javadoc L20-21:
  //  * callers that lint in bulk should {@link #close()} when done.
  @Override
  public void close() {
      synchronized (lock) {
          if (bridge != null) {
              bridge.close();
          }
      }
  }
  // CheckRunner L196（CLI 生产装配）:
  LintEngine engine = new LintEngine(registry, profile, new DeepResolvers(...));  // typeResolver = null
  ```
- **严重程度**: P3
- **并发场景**: 不适用（所有权缺口，非运行时竞态）。
- **触发时序**: 生产 main 代码 grep 证据——`TscTypeResolver|NodeTscBridge` 构造点为零（唯一引用方是测试）；`CheckRunner` 用 DeepResolvers-only 构造器（L2 恒 null）；`NopLintBizModel` L80 与 `NopLintLanguageServer` L93 均为 FAST profile 无 L2。当 L2 装配落地时，模块内唯一批量 lint runner `RuleTestRunner` 持有 `TypeResolver` 字段（L69）却无任何 close/生命周期出口——按现有契约照抄接线即成进程泄漏模板。
- **现状**: 桥的关闭契约（"bulk 调用方负责 close"）在模块内没有任何执行者，也没有保护力测试钉定"注入了 resolver 的 runner 必须关闭它"。
- **风险**: 今日无泄漏（无构造点）；L2 接线落地时，每个 runner/每次 run 遗留一个常驻 node 进程（`NodeTscBridge.close` 后才销毁），属"接线即泄漏"的预埋缺陷。
- **建议**: 在 L2 装配落地的同一变更内：`RuleTestRunner` 增加 `AutoCloseable` 传播（持有 resolver 时 close 传播到 bridge）；CheckRunner/服务端装配点明确 resolver 所有权（run 结束或 bean destroy 钩子关闭）；补一条"runner 关闭后 bridge 进程销毁"的保护力测试。
- **失效面枚举**: `TypeResolver` 持有者全清单——`RuleTestRunner`（字段持有，无 close ✗）；`LintEngine`（构造注入，纯引用不拥有 ✓ 合理）；`CheckRunner`（传 null ✓）；`TypeQuerySupport`（run 级引用 ✓）；`NopLintBizModel`/`NopLintLanguageServer`（无 L2 ✓）。唯一缺口是 RuleTestRunner 的所有权出口。
- **信心水平**: 确定（缺口本身）；触发后果为前瞻性推断
- **误报排除**: 非"要求无消费者的代码补测试"的过度审计——`AutoCloseable` + javadoc 契约是该类自己声明的公开面，全模块零执行者意味着契约不可验证，属 F8 注册/注销不对称在 API 层的实例；P3 因今日不可触发。
- **复核状态**: 已复核——**保留，P3 维持**；现状标注补强：「在编未接线」实证——main 源码 0 个 `new TscTypeResolver` 构造点（grep 实证，与 C1-103 同标注）；CheckRunner:195-197 DeepResolvers-only 与 RuleTestRunner 无 close（class :57）实证。

---

## 维度集外强信号线索（CL-401 起）

### [CL-401] LintDeadlineExecutor.install 永久改写全 JVM 全局 XLang executor，无卸载路径（C1/C9 域）

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/xscript/LintDeadlineExecutor.java:70-76`；被写方 `nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/EvalExprProvider.java:19`（`private static IExpressionExecutor _executor`）
- **信号**: install 幂等（instanceof 检查）但**不可逆**：任何嵌入方跑一次 lint（如 `NopLintBizModel` L80 的 GraphQL 面）即把包装器永久写入平台静态槽。后果三连：(a) 全 JVM 所有非 lint XLang 求值从此多一次 wrapper 转发（无 deadline 时 passthrough，C9 热路径税）；(b) install 捕获 delegate 的时点与宿主安装其他全局 executor（如调试器）存在**顺序竞态**——先 install 后装调试器会把 wrapper 顶掉，下次 lint 再 install 时 wrapper 套调试器，嵌套语义未定义（C1 域复合操作）；(c) wrapper 永久持有 install 时 delegate 的强引用。C6 判定：注册无注销，登记线索移交 C1/C9 维度复核。
- **复核状态**: 已复核——**确认，归并 C1-101（同站点）**：C1 复核已保留 C1-101 P1，本条 (a)(b)(c) 三后果并入其验收面。

### [CL-402] CheckRunner 全量 findings+LineIndex 驻留内存直至渲染结束（C5 域）

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/CheckRunner.java:200-254`
- **信号**: `findings`（含每文件 `LineIndex` + 全部诊断）在整个 run 内累积，报告在循环后一次性渲染——大仓（万级文件 × 全量诊断）报告面全量物化，无流式/分批出口。规模包络未在 owner doc 声明，移交 C5 判定是否立案。
- **复核状态**: 已复核——**确认（→C5）**：CheckRunner.java:200 findings 全 run 累积 + :253 循环后一次性组装 CheckOutcome 实证。

---

## 批 B 计数结论（口径见共享前缀第 10 条）

- 配对盘点：14 类资源获取点（上表），其中正确配对 9、立案 4（#1/#2/#3/#5 归并为 4 条发现）、线索移交 1（#10）。
- 发现：P1×1（C6-101）、P2×1（C6-102）、P3×3（C6-103/104/105）。
- CL 线索：2（CL-401、CL-402）。
- 复现口径：`grep -rn 'ProcessBuilder' nop-tools/*/src/main` 命中 1（NodeTscBridge）；`grep -rn 'createTempFile|Files\.move' nop-tools/nop-lint/*/src/main nop-tools/nop-jq/src/main` 命中 2 文件 3 行；`grep -rn 'ThreadLocal' .../nop-lint main` 命中 2（均 int[1] 平衡型）。


---

# C6 深挖第 2 轮

- **轮次**: 第 2 轮（因批 B 新增 P1 C6-101 触发的必派轮）
- **审计人**: C6 维度深挖子代理（盲审例外：读本文件前轮产物；未读其他维度文件与 ai-dev/plans/bugs/lessons/backlog）
- **范围**: 四模块全单元（nop-treesitter / nop-refactor / nop-lint / nop-jq），四个指定扩面方向
- **扩面动机**: C6-101 同族放大 / C6-001·002·102 修复归属族闭环 / treesitter query 包异常路径配对（批 A 未深挖角落）/ jq 输出流失败路径

## 一、扩面裁定（四方向逐一闭合）

### 方向 1：外部进程生命周期第二站点 —— 无第二站点，家族唯一性确认（C6-101 同族清扫收口）

复现口径（修正批 B 的 glob 缺陷：nop-refactor / nop-lint 为嵌套多级模块，`nop-tools/*/src/main` 单层 glob 未覆盖 `nop-refactor/*/src/main`、`nop-lint/*/src/main`；本轮以下列口径全量重扫）：

```bash
grep -rn --include="*.java" -E 'ProcessBuilder|Runtime\.getRuntime|ProcessHandle'
  nop-tools/nop-treesitter/src/main nop-tools/nop-jq/src/main \
  nop-tools/nop-refactor/*/src/main nop-tools/nop-lint/*/src/main
# 命中唯一：nop-lint-js/.../tsc/NodeTscBridge.java:198
```

同族执行载体清扫（进程之外的第二类"代际栅栏"敏感结构）：

```bash
grep -rn --include="*.java" -E 'new Thread\(|Executors\.|ExecutorService|CompletableFuture'
  （同上四模块 main 路径）
# 命中唯一：NodeTscBridge.java:208（reader daemon 线程）
```

六个外围子模块残留清扫（nop-lint-graphql 4f / nop-lint-java 18f / nop-lint-nop 1f / nop-lint-maven-plugin 1f / nop-refactor-graphql 3f / nop-refactor-java 1f，共 28 文件）：无 Process/Stream/Closeable/ThreadLocal/临时文件获取点。两处表面命中均豁免——`CheckMojo.java:174` `new PrintStream(OutputStream.nullOutputStream(), true)` 为无资源汇（不持有 OS 句柄，不关无泄漏）；`NopRefactorBizModel.java:396` `Files.walk` 即批 A P13（try-with-resources ✓）。

**裁定**：外部进程 + 专用 reader 线程在四模块 main 各恰有一处，且同点共存于 NodeTscBridge——C6-101 的世代栅栏缺陷与 C6-103/104 的回收策略不一致无任何同族兄弟站点，单点修复即家族收敛。批 B "ProcessBuilder 唯一"结论在修正 glob 后复演成立。

### 方向 2：写盘家族（C6-001/002/102 修复归属族）闭环核对 —— 无漏点，前轮失效面枚举完备

全单元文件写点复现口径：

```bash
grep -rn --include="*.java" -E 'Files\.write\(|writeString|Files\.move|createTempFile|FileWriter|FileOutputStream|Files\.copy'
  （四模块 main 路径，含嵌套 glob）
# 命中恰好 4 处（7 行）：
#   Ts2Java.java:39                      build-time 生成器，产物可再生（前轮已豁免）
#   EditPlanApplier.java:163,171,173     atomicWrite：唯一 temp+move+finally 清理实现 ✓
#   RuleResultCache.java:200,202         C6-102 主证（本轮逐行重读核实与发现描述一致）
#   BaselineFile.java:189                C6-102 失效面（本轮逐行重读核实：直接 writeString 非原子）
```

配套核对：
- `FixApplier.run`（L105-144）多 pass 循环本体无新资源面（byte[] + 计数 record）；每 pass 经 `EditPlanApplier.apply`（L128-129）继承 C6-001 写-守卫窗口，窗口随 pass 数叠加——已在 C6-001 失效面 (2) 登记，无新增面。其 javadoc L33-37 对写失败的描述（"temp file is cleaned up, the target keeps its previous content"）与 atomicWrite 实现一致，无契约矛盾。
- nop-refactor 无直接写点（apply 面经 EditPlanApplier 收敛），与 C6-002 失效面登记一致。
- `RuleResultCache.load` fail-closed 面（L73-111：corrupt/version/fingerprint 三分支均显式抛错或换新 artifact）本轮复核无缺口。

**裁定**：写盘家族全单元恰 3 个运行时写点 + 1 个豁免生成器，C6-102 的失效面枚举即完备清单，修复时按该三点一次收敛即可，无补漏项。

### 方向 3：treesitter query 包（TSQuery/TSQueryCursor/树释放）异常路径资源配对 —— C6 闭合，无发现

- 游标来源核实：`TSNode.cursor()`（TSNode.java:101-103）与 `TSTree.cursor()`（TSTree.java:121-122）**恒返回新建 TSTreeCursor**；SCRATCH 池（TSNode.java:112-145）仅服务 8 个结构访问器，深度守卫下嵌套获取回退新建实例——查询路径（TSQueryCursor 构造器 L53 `tree.cursor()`、matchChildren L169 `parent.cursor()`）全部不入池，与池的 release/容量问题（C6-003/004）无交集。
- 资源定性：TSTreeCursor 为纯堆对象（五列 int[] frame + tree/nav 引用，无 native 句柄、非 AutoCloseable——本维度批 B #7 已核实同一结论于 SubtreeArena/TSTree），GC 即正确释放机制；异常路径（matchMatcher L161 unknown matcher 抛出、predicate 失败 L113-123 抑制、walk 中任何 RuntimeException）不持有任何需显式释放的资源。capture 持 TSNode → TSTree（arena）的驻留为不可变快照声明设计（C6-005 已登记保留语义）。
- 附带核实：matchChildren 每模式-每节点-每步新建 cursor 为分配热点，但 query 包今日**无 main 消费方**（`grep -rl 'treesitter.query'` 四模块 main 仅 query 包内部互引，全部消费在 src/test），C9 立案暂缓至接线落地。
- 家族补记：CL-202（深递归 SOE 族）补登记兄弟面——nop-lint-java `MetricsEvaluator.java:54` 的 `method.walk` 走 `com.github.javaparser` 库遍历（非本仓库递归实现），深度行为由第三方库决定，不按本仓库缺陷立案（豁免记录，与批 A CL-202 的"本仓库内递归/迭代不一致"口径区分）。

### 方向 4：jq 输出流失败路径清理 —— 闭合，无发现

- jq 模块 main 全量 `grep -E 'InputStream|OutputStream|Closeable|\.close\('` **零命中**：无 OS 流、无 closeable。输出面为纯内存 `List<Object>`（JqDirectQuery.java:30-34/46-50/77-81），JqPrinter 走 StringBuilder（JqPrinter.java:16-22）。失败路径无流资源可清理。
- deadline 为协作式轮询：`JqEnvironment` L124-127 在输出预算检查点轮询 `Thread.interrupted` + 墙钟，**无看门狗线程/Timer/schedule**（grep 证据）——不存在"超时后未取消的调度任务"泄漏面。
- 批 B #12（四重守卫）与 #11（编译缓存有界 1000）结论维持。`applyPartial`（JqDirectQuery.java:66-82）的 partial-output + errored 标记为测试 harness 声明语义，非生产路径缺陷。

## 二、维度外强信号线索（续批 B 编号）

### [nop-tools-parse-CL-403] TSQueryCursor.matchSteps 共享游标递归背离其声明的 split-state 回溯语义：可构造确定性误匹配（假阳性 + 假阴性两族）

- **文件**: `nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/query/TSQueryCursor.java:176-203`（辅证 `TSTreeCursor.gotoNextSibling` TSTreeCursor.java:172-193、javadoc 契约 TSQueryCursor.java:22-24）
- **信号**: `matchSteps` 各步进层共用**同一个** TSTreeCursor（L173 形参逐层透传，L192/L200 原位 `gotoNextSibling`，L193 递归下层继续用它下潜）。子类 javadoc L22-24 声称 "a failed deeper step backtracks to the next candidate, mirroring the C runtime's split-state search"，实现不具备该语义。关键佐证：`gotoNextSibling` 失败时恢复原位（TSTreeCursor.java:191 `size = initialSize`，"cursor unchanged" 契约成立），使失败后的"原位重启"成为系统性偏移而非偶发。两条确定性偏差链：
  - **假阳性（重复消费）**：模式 `(a (b) (b))` 对恰有一个 b 子节点的 a 节点——step1 匹配 b1 → L192 `gotoNextSibling` 失败（末子节点，游标原位停在 b1）→ L193 `matchSteps(stepIdx+1)` 从原位重启，L184 `currentNode()` 仍取到 b1 → step2 再次匹配同一 b1 → 返回 match，两个 capture 指向同一节点。上游 split-state 要求相邻步进消费不同子节点，此处误报。
  - **假阴性（候选跳过）**：深层步进失败耗尽后游标停在最后子节点，浅层 L197 `continue` 从原位重启——中间子节点只被深层（不同 matcher）扫描过，永不被浅层作为匹配候选重试，声明中的"回溯到下一候选"不成立；若深层经更深下潜后失败，L185 的 parent 校验 break 会沿递归链向上传播，整条分支提前终止。
- **定性**: 单线程、确定性、纯代码可判读的正确性缺陷——**非 C6 资源域**，移交查询引擎/功能正确性 owner 裁定。与 C6-105 同型："契约在、消费者为零"的预埋缺陷——query 包今日无 main 消费方，接线落地日即缺陷显形日。
- **建议**: matchSteps 递归改为每层传递位置快照（进入下层前记录游标 depth，下层失败后显式回溯重定位），或每层独立 cursor + 失败后按 checkpoint 重建；修复以官方 query 差分测试（`(a (b) (b))` 单 b 树、中间候选重试两类用例）钉定。
- **信心水平**: 确定（无并发假设、无时序窗口，代码路径直接判读）
- **复核状态**: 已复核——**确认移交（查询引擎正确性 owner）**：matchSteps 共享游标递归（:176-203）代码事实复核通过；非 C6 域定性维持。

## 三、第 2 轮计数结论

- 新 C6 发现：**0**（四方向全部闭合：进程站点唯一性复演成立、写盘家族无漏点、query 包资源配对闭合、jq 无流资源）
- 新 CL 线索：1（CL-403，维度外强信号——查询引擎正确性，非 C6 立案）
- 家族补记：CL-202 增补一条豁免记录（javaparser 库遍历）
- 复现口径均已内联（grep 模式 + 路径 glob + 命中行号），数量结论可复现（共享前缀第 10 条）

## 本轮终止

经四个指定扩面方向全量核查，未发现新的 C6 资源生命周期问题。深挖结束。

---

# C6 维度复核结论（WI 6.2 独立复核，2026-10-22）

> 复核人：C6 维度独立复核子代理（与初审批 A/B、R2 不同 task）。范围：C6-001~006/101~105 共 11 案逐条重读源码 file:line 实证 + G10 三态裁定 + 批次覆盖核验 + CL-201~205/401~403 裁定。live code @ 0d131797dd（git HEAD 实测一致）。Phase 4 复核豁免盲审：已读 2026-09 `10-lint-refactor-jq.md` G10 原文、cra-index、dispatch-log、C1 复核结论；除本文件追加与复核状态行回填外未改任何文件。

## 一、逐条判定

| 编号 | 判定 | 理由（复核实证锚点） |
|---|---|---|
| C6-001 | **保留，P2 维持** | 写序实证：`EditPlanApplier.java:87`（splice 内存合成）→ `:88-89`（atomicWrite 先落盘）→ `:92-94`（守卫在后）→ `:96`（回滚写）；守卫输入 `source`/`next` 均在内存，前置零成本成立；javadoc `:71-72` 的 restore 承诺与实现缝隙属实。失效面两点核实：`RefactorOperationRunner.java:61`（唯一 refactor 调用点）+ `FixApplier.java:129`（multipass 窗口叠加）。 |
| C6-002 | **保留，P2 维持** | 契约矛盾实证：`RefactorOperationRunner.java:45`「lands atomically」vs `:30`「already-landed files in the error (never a faked success)」（同文件自相矛盾）+ `:65-71` 部分落地实现；崩溃恢复语义（无 journal/marker）未声明属实。失效面入口（rewrite/rename × preview/apply + CLI）与 `:122/:210` 装配点核实。 |
| C6-003 | **保留，P3 维持** | `ScannerVM.java:93`（POOL）`:99`（set）实证，全文件无 `POOL.remove`；finally `:115-117` 重引用置空证实作者有反 pin 意识；平台 `NamedThreadLocal`（nop-commons `io.nop.commons.concurrent.thread.NamedThreadLocal`）存在，建议可达。量级 ~2KB/线程（operandStack `int[MAX_STACK]` + callStack `int[maxCallDepth()]`，`:45,:48`）与 P3 匹配。 |
| C6-004 | **保留，降级 P2→P3（量化链修正）** | 机制实证：`TSTreeCursor.release()`（:89-93）只清 `tree/nav/size`，五列 `fNode..fAlias`（`:52-56`，初始 8）与 `ancestors`（`:57`）保持峰值容量；`release()` javadoc `:80-88` 反 pin 目标与实现不对称属实。**量化修正见下节**——池化路径受 `resetTo` 5000-hop 守卫（`:115-117`）约束，MB 级常驻不可达。 |
| C6-005 | **保留，P3 维持** | `grep -rn "\.free(" src/main` 零命中实证（调用全部在 SubtreeArenaTest）；`TSParser.java:58-65`（adopt 无深拷贝，注释 `:62-64` 自证）、`:155-159`（snapshot 可达拷贝）核实；`free`/`freeList` + use-after-free 守卫死代码事实成立。 |
| C6-006 | **保留，P3 维持** | `EditPlanApplier.java:171-174` write+ATOMIC_MOVE 无 SYNC/force（grep 实证）；javadoc `:154-157` 只声明 atomic 未声明 durable，定级与声明缺口匹配；与 C6-001 的写序/屏障区分成立。 |
| C6-101 | **保留，P1 维持；确认为 NodeTscBridge 三方主登记** | 全要素实证：`spawn():196-197`（destroy/clear 无栅栏）、reader `:208`（无世代标识）、`drainPeerOutput:231-242`（读共享字段 `this.process`、`:236` put 无归属校验、`:241` finally 无条件 offer(EOF)）、握手 EOF 分支 `:213-217` 不 destroy（对照组 `:224`）、`nextId` 单调不重置（`:148`）排除错答。分支 A（stale 数据帧 → `:219` validateReady 失败 → 杀健康新进程 + EXHAUSTED）与分支 B（stale EOF → EXHAUSTED + 孤儿进程）均可从代码直接构造；`hang-once-then-ok` 测试（NodeTscBridgeTest `:109-129`）假保护判定属实。主登记裁定见下节。 |
| C6-102 | **保留，降级 P2→P3（面拆分，与 C1-105 姊妹案）** | 实证：`RuleResultCache.save():197-208` 固定名 `.tmp`（`:199`）+ move 失败无 `moved` 标记/finally 清理（`:204-207`）+ 对照组 `EditPlanApplier.atomicWrite:169-194`（moved + finally deleteIfExists）——清理不对称属实；`BaselineFile.writeTo:187-189` 直接 writeString 非原子属实。判级与归属裁定见下节。 |
| C6-103 | **保留，P3 维持** | `:213-217` EOF 分支仅置 EXHAUSTED 即抛，无 `destroyProcess`；对照组 `:224` 实证；EXHAUSTED 终态后无回收点、生产无强制 close（C6-105）链核实。滞留面 = 死 Process 句柄 + pipe fd 至 GC，P3 匹配。 |
| C6-104 | **保留，P3 维持** | `:164` decode 在 try 外、抛 `TscQueryException(BAD_FRAME)` 不回收（`TscProtocol.decode:41-45` 对非 JSON 抛 BAD_FRAME 实证）；对照组 id 失配 `:169` 立即 `handleProcessFailure` 实证；`garbage-response` 测试（`:95-106`）只钉抛错不钉回收语义属实。恢复晚一轮 + 多耗一预算槽成立。 |
| C6-105 | **保留，P3 维持** | 实证：javadoc close 契约（TscTypeResolver `:20-21`）、`close():99-105`；main 源码 `new TscTypeResolver`/`new NodeTscBridge` 构造点为零（唯一 `new NodeTscBridge` 在 TscTypeResolver.java:110 内部；其余引用全部在 src/test——grep 实证）；`CheckRunner.java:195-197` DeepResolvers-only（LintEngine(registry, profile, deep) 三参构造器 LintEngine.java:85，typeResolver=null）；`RuleTestRunner`（class 声明 `:57`，无 AutoCloseable/close）持有 `typeResolver` 字段。「接线即泄漏」预埋缺陷成立，P3 因今日不可触发正确。 |

## 二、C6-101 主登记裁定（确认 C1 复核建议）

**确认：C6-101 为 NodeTscBridge 重启无栅栏缺陷的唯一主登记**，C1-102、CS-001 转同站点引用登记。理由维持 C1 复核三点并补本维实证：(1) 后果枚举最全——唯一覆盖 stale 数据帧杀健康进程（分支 A）、stale EOF（分支 B）、孤儿进程（挂 C6-103）、预算击穿四后果；(2) 一行级修复（`drainPeerOutput` 捕获 `Process` 局部引用、put 前 `this.process == captured` 比对）同时封死分支 A/B；(3) C6-103/104 挂其失效面七条清单下，以它为主保持失效面锚完整。C1-102 独有侧写「失败原因误记/诊断误导」并入 C6-101 验收标准；CS-R2-001（消费侧 error 帧校验）维持独立立案（互补守卫）。

## 三、C6-102 / C1-105 双登记裁定

**裁定：同站点双面并存，按缺陷域拆分主登记，两案互注姊妹案；均保留不归并。**
- **C1-105 = 并发/互斥面主登记（P2 维持）**：跨进程 load-modify-save 无互斥 + 固定名 `.tmp` 踩踏使能（A move 搬空 B 的 tmp → NoSuchFileException 虚假失败 / 整件 last-writer-wins）。固定名唯一化修复归 C1-105 主责。
- **C6-102 = 资源生命周期面（降级 P2→P3）**：剥离与 C1-105 重叠的踩踏面后，本案残余 = 失败路径 temp 无清理（每次失败至多遗留一个、同名覆盖不累积，有界）+ 失效面 BaselineFile 非原子写（撕裂写，load 侧 fail-closed 可恢复）。两残余后果均有界且无并发专属面，按本 campaign 校准（对照 C6-006 P3）定 P3。修复两案同点落地（save() 加 moved+finally + temp 名唯一化 + BaselineFile 原子化），工单可合并、台账分域。

## 四、C6-001 修复归属核实

**确认：修复 owner = nop-lint**（`io.nop.lint.core.fix.EditPlanApplier` 属 nop-lint-core；nop-lint 无 owner doc，dispatch-log 已登记 drift）。初审「以 nop-refactor 调用面立案、修复时通知 nop-lint owner」的定位正确；实施按「nop-lint owner 主修 + nop-refactor 消费面回归」执行，守卫前置后删除 `:96` 回滚写的方案经核与 FixApplier multipass 语义等价（守卫失败返回 rolledBack=true 的 EditPlanResult 不变）。

## 五、C6-004 量化链修正（降级依据）

原判「深树一次遍历后百 KB~MB 级常驻」重演不成立，修正如下：
1. **池化路径有上游守卫**：SCRATCH 池游标的每次借用都经 `TSNode.borrowScratch` → `cursor.resetTo(this)`（TSNode.java:124），而 `resetTo` 的祖先攀爬有 5000-hop 上限——超过即抛 `IllegalStateException`（TSTreeCursor.java:115-117）。初审引用了该守卫但误判其射程（「只约束从深节点起锚，从 root 导航下降无此上限」——下降无上限属实，但**无上限的下降发生在不入池的新建游标**上：`TSNode.cursor()`/`TSTree.cursor()` 恒新建（TSNode.java:101-103，R2 方向 3 已核实查询路径全部不入池），用完即 GC，无常驻面）。
2. **可达峰值**：池化游标 frame 容量由「锚点祖先链深度（≤5000，否则抛错）+ 单次访问器内隐形容器链下潜」共同上限——五列倍增至 8192 项封顶 ≈ 160KB + ancestors ≈ 32KB，**绝对最坏 ~192KB/线程**（需对抗性 ~5000 深锚点输入；深度 >5000 的节点访问直接抛错，容量反而不再增长）；常规深度（数百层源码树）驻留为 KB 级。
3. **裁定**：机制缺陷保留（容量不缩容 + 模块自declared 反驻留目标不对称），但「MB 级」不可达、「百 KB」仅对抗性边界可达、常态 KB 级——判级 P2→P3（与 C6-003 同族同批修复：`release()` 中五列 + ancestors 重置初始容量）。修复建议本身（一行重置）不变。

## 六、G10 三态裁定（2026-09 G10-01-02 / G10-01-03）

| 原条目 | 三态 | 裁定依据（live code 对照） | 处置 |
|---|---|---|---|
| G10-01-02 LanguageRegistry 裸 HashMap（2026-09 P3，原文 :204-222） | **仍成立（未修复）** | live code 与原文描述逐点一致且未改：HashMap 字段、register check-then-act（`languages.get` → 冲突判 → `put`）、javadoc 宣传 embedders/tests 手动注册、resolve/registeredIds 读面。2026-09 的「GraphQL 逐请求 resolve」语境已被 C1-104 精化：仓内全部调用方为「build-then-freeze 安全发布」（含 C1 复核补的 NopRefactorCli.java:70），缺陷活在嵌入契约面。 | **并入 C1-104 主登记（P2）**——C1-104 为严格超集（同站点 + 守卫非原子 + registeredIds 迭代 CME 面 + 全调用方清单 + fails-closed 契约违反分析），P2>P3。G10-01-02 原条目标注「已被 WI 6.2 C1-104 吸收升级」，不再独立跟踪。 |
| G10-01-03 rename/rewrite 每请求 ServiceLoader 发现（2026-09 P3，原文 :224-238） | **仍成立（未修复）** | live code 行号未变：`NopRefactorBizModel.java:122,210` 每请求 `discoverDefaults()` + `:132,:271` `new LintEngine(registry, STANDARD)`；对照面 NopLintBizModel volatile-DCL 单例（runtime() :72-88）成立。 | **并入 nop-tools-parse CL-204（C9 线索主责）**——同站点同内容，G10-01-03 的「与 Lint 面一次装配契约不一致」侧写已含于 CL-204。cra-index 回填注记文本见下节。 |

**cra-index 2026-09 审计回填注记文本**（供 Phase 6 施用，本复核未改 cra-index）：

> 2026-09 `10-lint-refactor-jq.md` G10-01-02（LanguageRegistry 裸 HashMap，P3）、G10-01-03（refactor 每请求 ServiceLoader 发现，P3）：经 WI 6.2（2026-10-22，live @ 0d131797dd）三态复核**均仍成立（未修复）**；G10-01-02 并入 C1-104（P2，超集立案），G10-01-03 并入 nop-tools-parse CL-204（C9 线索）。两原条目自此不再独立跟踪，修复跟踪以 WI 6.2 台账为准。

## 七、批次覆盖核验

- **四源码根规模实测**（`find -path "*/src/main/*" -name "*.java" | wc -l`）：nop-treesitter 92 / nop-refactor 33 / nop-lint 160 / nop-jq 72——与批 A/批 B 头部口径逐一吻合；nop-lint 六子根（core 126/js 10/java 18/graphql 4/nop 1/maven-plugin 1）与 nop-refactor 三子根（core 29/graphql 3/java 1）构成 R2 九源码根清扫口径，成立。
- **批 A 痕迹抽核**：C6-003/004/005 的 treesitter 锚点（ScannerVM:93/99/115-117、TSNode:112-145、TSTreeCursor:52-57/89-93/115-117/552-566、SubtreeArena:210-220、TSParser:58-65/155-159）全复核；C6-002 的 refactor 锚点（RefactorOperationRunner:30/45/65）复核。批 A 范围声明勘误确认：C6-001/006 主文件在 nop-lint-core（批 B 域），初审已自declared以 refactor 调用面立案——跨批文件归属无隐瞒。
- **批 B 痕迹抽核**：NodeTscBridge 全部行号（:148/:159-163/:164/:169-171/:195-243/:254-256/:267-275）、RuleResultCache:197-208、BaselineFile:187-189、TscTypeResolver:20-23/99-105 全复核；grep 反演（ProcessBuilder/new Thread 四模块 main 唯一命中 NodeTscBridge:198/:208）重演通过（nop-tools 全域 grep 另命中 nop-rg-benchmark，不在审计单元，豁免）。
- **R2 终态认可**：四方向闭合结论（进程站点唯一、写盘家族 4 处、query 包不入池、jq 无流资源）与本复核独立 grep 一致。

## 八、CL 裁定

- **CL-201 确认（→C5）**：`TreeSitterBizModel.parseTreeSitter:31-41` 直接 parse 无任何规模闸实证；对照面 `NopRefactorBizModel` 双闸实证（CFG_MAX_SOURCE_SIZE 1MB `:72-74`、CFG_MAX_TARGET_FILES `:76,:136,:214`、checkPreReadCap `:149`）。
- **CL-202 确认（→C5/C7）**：`TSTree.copy` 递归实现实证（private static int copy 自递归 `:64-82`）；GLRParser 显式栈对照维持；R2 的 javaparser 豁免补记维持。补注：本复核发现的 `resetTo` 5000-hop 抛错（TSTreeCursor.java:115-117）为深锚点路径的 fail-loud 上界，与递归 SOE 族并行不悖（递归实现无对应守卫）。
- **CL-203 确认（→C9/C5）**：SubtreeArena javadoc「keeping the hot parser path allocation-free」（`:11` 附近）与 `allocateChildren` 内 `cache[id] = new Subtree(...)` 每节点快照分配矛盾实证。
- **CL-204 确认（→C9），并承接 G10-01-03**：`:122/:210` discoverDefaults + `:132/:271` new LintEngine 全实证。
- **CL-205 确认（→C1 注记级）**：与 C1 复核对载体表 #7 的「良性（设计取此）」判定一致，维持线索不立案。
- **CL-401 确认，归并 C1-101（同站点）**：C1 复核已保留 C1-101 P1（install 无卸载 + 跨 monitor 丢更新），CL-401 的 (a)(b)(c) 三后果并入其验收面。
- **CL-402 确认（→C5）**：`CheckRunner.java:200` findings 单一 ArrayList 全 run 累积 + `:253` 循环后一次性组装 CheckOutcome 实证。
- **CL-403 确认移交（查询引擎正确性 owner）**：matchSteps 共享游标递归（`:176-203`：L192 下层递归复用同游标、L184 失败后 currentNode 原位重启）代码事实复核通过；非 C6 域定性维持。
- **编号冲突重编**：确认 C1 R2 的 CL-201（xscript 哨兵可吞）→ **重编为 CL-206**；C6 批 A CL-201~205 保留原号。

## 九、最终保留项

- **C6 立案保留 11/11，驳回 0**；判级调整 2（C6-004 P2→P3 量化链修正、C6-102 P2→P3 面拆分）；登记归属裁定 2（C6-101 三方主登记确认；C6-102/C1-105 姊妹案拆分）。
- **判级终态**：P1×1（C6-101）、P2×2（C6-001、C6-002）、P3×8（C6-003/004/005/006/102/103/104/105）。
- **CL 落位 8/8**：CL-201→C5、CL-202→C5/C7、CL-203→C9/C5、CL-204→C9（承接 G10-01-03）、CL-205→C1 注记、CL-401→归并 C1-101、CL-402→C5、CL-403→查询引擎 owner；CL-206 重编确认。
- **G10 三态**：G10-01-02 仍成立→并入 C1-104；G10-01-03 仍成立→并入 CL-204；cra-index 回填注记文本已交付（第六节）。
- **现状标注汇总**：11 案全部「未修复」（live @ 0d131797dd）；C6-105/C1-103 同标注「在编未接线」；C6-001 修复 owner = nop-lint（owner doc 缺位 drift 在案）。
- **Phase 6 归并待办（本维视角）**：① NodeTscBridge 三方 → C6-101 主登记；② RuleResultCache.save 双登记 → C1-105（并发面 P2）+ C6-102（生命周期面 P3）姊妹案；③ LintDeadlineExecutor → C1-101 吸收 CL-401；④ CL-201 撞号 → CL-206；⑤ 2026-09 G10-01-02/03 回填注记施用。
