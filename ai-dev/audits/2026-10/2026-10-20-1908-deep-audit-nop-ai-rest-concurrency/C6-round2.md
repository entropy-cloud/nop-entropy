# C6 深挖第 2 轮 — nop-ai-rest（WI 3.4）

- **审计日期**: 2026-10-20
- **审计员**: C6 深挖第 2 轮子代理（合并批 C1/C3[rag]/C6 之 C6 部分；盲审，仅读派单指定的第 1 轮三文件）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀（F1–F11 + 口径纪律）+ C6 正文 + 附录 A 13 字段
- **编号**: 续接第 1 轮 C6-001…C6-003，本轮从 C6-004 起；`PgVectorStore.schemaReady`（C1-002/C3-004/C6-001 三登记）不再重复

## 深挖范围与方法

按派单四个深挖面逐一核对（获取点以 live code + 平台封装实现复核）：

1. **code-analyzer 全部文件读取面（CL-011 之外的句柄泄漏面）**：8 根内唯一裸 `FileInputStream`（`JavaCodeFileInfoParser.java:74-103`）经 finally + `IoHelper.safeCloseObject` 配对，`javaParser.parse` 抛 `ParseProblemException`（RuntimeException）时 finally 仍关闭——**无泄漏**；`countLines`/`calculateMD5`（L71-72）经平台 `FileHelper`（内部流自管）；`Files.walkFileTree`（`FileTreeWalker.java:21`）自管理；`MavenDependencyTreeParser/MavenPomParser` 经 `FileHelper.readLines`/`XNodeParser`（平台封装）。深挖新发现的面不在句柄而在**外部进程与半写文件**：`MavenProject.generateDependencyTree` 的外部 mvn 进程无超时（CL-015）、`JavaCodeFileInfoGenerator` 的产物写非原子（C6-004）。
2. **coder 其余 20 类的输出流**：直接写点全清单经 grep 复核 = `JavaMethodReplacer.java:50`（C6-002 已立案）+ `AiCoderHelper.java:126`（`XNode.saveToResource`）/`:147`/`:151`（`ResourceHelper.writeText`，平台封装，生成物写、无 skip-if-exists 放大面）；`DictGenHelper` 仅内存 Appendable。**coder 面：零新增。**
3. **rag PgVectorStore 之外的资源**：`AiModelTextEmbedding`（构造注入 `IEmbeddingModel` 引用，无 OS 资源、无客户端生命周期所有权）；`EmbeddingModelBasedClassifier`（同上，仅持引用）；`InMemoryVectorStore`（纯堆数据面，CL-004/CL-012 已登记）；`PgVectorStore` JDBC 对象（Connection/PreparedStatement/ResultSet）全部 try-with-resources（L115-133/L152-163/L188-207 逐点复核）；连接池所有权 javadoc 已声明归集成方（已声明边界，第 1 轮口径维持）。**rag 面：零新增。**
4. **service 文件/附件面**：全根 grep `InputStream|OutputStream|Files\.|writeText|saveToResource|new File\(` 零命中（唯一命中为注释文本）——`NopAiGenFile` 为纯 DB 实体外壳，附件不落本地盘；`OrmModelSwitchedMessageWriter` 经 `IEntityDao` 写库。**service 面：零新增。**

## 新增发现

### [C6-004] JavaCodeFileInfoGenerator 的 exists()-skip × 非原子截断写组合——半写 .info.json 被幂等跳过永久保留

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/JavaCodeFileInfoGenerator.java:48-66`（skip 判定 :56-58）、`:82`（写点）；平台写实现 `nop-kernel/nop-commons/.../FileHelper.java:147-158`（`new FileOutputStream(file, append=false)` 截断写，无 temp+rename、无锁）
- **证据片段**:
  ```java
  File destFile = new File(file2.getParentFile(),
          StringHelper.replaceFileExt(file2.getName(), ".info.json"));
  if (destFile.exists())
      return FileVisitResult.CONTINUE;            // :57-58 存在即跳过，永不重生成
  ...
  FileHelper.writeText(destFile, JsonTool.serialize(fileInfo, true), null);  // :82 截断式写
  // FileHelper.writeText: out = IoHelper.toWriter(new FileOutputStream(file, false), ...)
  //                        out.write(text); out.flush();   —— 无原子替换
  ```
- **严重程度**: P2（工具面，当前零 main 消费者——grep 全仓仅 2 个测试类引用；一旦接入 codegen 管线即升 P1 面）
- **并发场景**: (1) 单线程失败路径即可触发，无需并发——:82 截断写中途 JVM 崩溃 / kill -9 / OOM / 磁盘满；(2) 并发场景为两个生成任务（不同线程/进程）对同一模块并行 `generate`——两者都见 `destFile` 不存在，同时进入 :82 对同一文件交错 truncate+write。
- **触发时序**: (a) T1: :82 `writeText` 已截断目标文件并写入部分 JSON → 进程被 kill -9 → 残缺 `.info.json` **存在** → 其后每次运行 :57-58 判定存在即跳过 → 半写产物永久保留，且无任何校验/告警点（F2 半个成功 + skip 放大为不可自愈损坏）。(b) T1 写线程 flush 到一半 → T2 写线程同时 truncate 重写 → 两个 JSON 字节流交错 → 解析失败或（更糟）解析成功但内容为两版本混合，下游 summary 注入读到错配的 class/function 条目。
- **现状**: 平台既有安全写设施（`FileHelper.writeTextWithLock` 文件锁重试、`writeTextIfNotMatch` 比对写）均未被使用；`destFile.exists()` 幂等跳过的设计意图是「已生成不重做」，但与截断写组合后把瞬时失败放大为永久损坏。**口径声明**：第 1 轮 C6-002 失效面曾将本站点判为「幂等可恢复，无破坏性覆写」——round 2 证据（:57-58 的存在即跳过使重生成永不发生 + `FileHelper.writeText` 为截断写实现）不支持该 clearing 结论，本条按新发现立案并提交合并裁定复核（同一 F2/F6 家族、不同站点不同根因：C6-002 是 read-modify-write 无原子替换，本条是 write-once + skip-if-exists 放大）。
- **风险**: 接入生成管线后，一次崩溃即产生静默的永久损坏分析产物（代码索引/summary 注入的下游输入），损坏不可被重跑自愈、无检测信号；并发双跑时产物交错损坏。
- **建议**: 写路径改「临时文件 + `Files.move(ATOMIC_MOVE)`」或先写 `.tmp` 再 rename（消除半写窗口）；或 skip 判定改为「存在 **且** JSON 可完整解析」；并发双跑场景加平台 `io.nop.commons.concurrent.lock.LocalFileLock` 或任务级单飞守卫。
- **失效面枚举**（8 根内「写点 × 幂等/覆盖语义」全清单，grep 复现）: `JavaCodeFileInfoGenerator:82`（缺陷本体，skip 放大）；`JavaMethodReplacer:50`（C6-002，同族不同根，已立案）；`AiCoderHelper:126/:147/:151`（平台封装、无 skip 组合、覆写即全量重写——watch-only，非本缺陷形态）；`JavaCodeFileInfoGenerator` 经 `FileHelper.walk2` 的目录遍历（自管理，无句柄面）；其余 6 根无直接写点（第 1 轮口径维持）。
- **信心水平**: 确定（机制与平台写实现均可证；影响面受限于当前零 main 消费者）
- **误报排除**: 非「平台封装即安全」默认假设——本条恰以 `FileHelper.writeText` 的截断写实现为证据；非已声明边界——类无 Reserved 裁定、无「崩溃后需手工清理」声明；不与 C6-002 重复计数（站点、根因、后果三者均不同）。
- **复核状态**: 未复核（含对第 1 轮 C6-002 失效面 clearing 结论的分歧声明，待合并裁定）
- 复核状态: 保留(P2·条件化)｜新发现·已知未修复（潜伏：全仓仅 2 个测试类引用复核属实；接入管线即升 P1 面的条件标注成立）——**分歧声明经复核裁定成立**：C6-002 对本站点的「幂等可恢复，无破坏性覆写」clearing 结论被推翻（:57-58 exists()-skip 使半写产物永不可由重跑自愈，`FileHelper.writeText` 截断写实现 L147-158 亲证）；两案（C6-002/C6-004）裁定为同族不同站点不同根因、双保留不合并，C6-002 失效面枚举按本条更正。裁定全文见 C6-resource-lifecycle.md 复核节

## 维度集外线索（overlay）

（编号续接全维度共享流水：CL-001…014 已被第 1 轮占用，本轮自 CL-015 起；CL-016 见 C3-round2。）

### [nop-ai-rest-CL-015] （归 C7，F5 挂死不可见）MavenProject.generateDependencyTree 经 ShellRunner.runCommand 以零超时执行外部 mvn 进程

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/maven/MavenProject.java:21-23`；平台设施 `nop-utils/nop-shell/.../ShellRunner.java:51-70`（便捷方法不设超时）、`ShellCommand.java:42`（`timeout` 默认 0）、`ShellRunner.java:107-112`（`timeout<=0 → process.waitFor()` 无限期）
- **证据**:
  ```java
  public void generateDependencyTree() {
      ShellRunner.runCommand("mvn -DoutputFile=" + DEPENDENCY_TREE_FILE_PATH
              + " dependency:tree", getProjectDir());   // ShellCommand.create 默认 timeout=0
  // ShellRunner.run: if (command.getTimeout() > 0) { waitFor(diff) } else { return onExit(process.waitFor()); }
  ```
- **线索**: `mvn dependency:tree` 涉及网络（缺失构件下载、仓库探测）与本地锁（`mvn` 进程对本地仓库 `.lock` 的互斥）；网络黑洞 / 仓库锁被另一个挂起的 mvn 持有时，`process.waitFor()` 永久阻塞调用线程——「不抛异常也不产出」的静默挂死，无超时、无取消入口、无看门狗可见性（F5 命中）。ShellRunner 自身具备完整超时设施（`setTimeout` + `scheduleTimeout` + 超时 safeDestroy），缺陷在便捷调用方未使用（「只审使用方式」口径）。进程句柄/输出收集线程在挂死期间持续持有（伴生资源占用）。移交 C7 定审；建议 `ShellCommand.create(...).timeout(...)` 或在调用方包 `CompletableFuture.orTimeout` + 进程销毁。
- **同族清单**: 8 根内外部进程执行点全仓 grep 仅此 1 处（`runCommand|ProcessBuilder` 零其他命中），无兄弟站点。

## 继续判定

- 新增发现：1 条（P2×1 = C6-004，含对第 1 轮 clearing 结论的分歧声明）；overlay 线索 1 条（CL-015，归 C7）；无 P0/P1；无扩展立案。
- 四个指定深挖面全部收敛：code-analyzer 读取面句柄配对复核零泄漏、coder 输出流零新增、rag PgVectorStore 之外零资源持有面、service 零文件 IO；第 1 轮基线 C6a/C6b 获取点配对表在本轮逐点复核下无新增未配对项。
- **判定：终止信号**——新增全为 P2 及以下且资源获取点清单已闭环（收敛证据：8 根 IO/进程/资源面 grep 穷举 + 配对表复核完毕），无需第 3 轮 C6。

---

## 复核结论（WI 3.4 独立复核，2026-10-20）

- **C6-004：保留(P2·条件化)**——机制证据链亲证成立（exists()-skip :57-58 + `FileHelper.writeText` 截断写实现 + 平台安全写设施存在未用）；**分歧声明裁定成立**：C6-002 第 1 轮对本站点的 clearing 结论（「幂等可恢复」）经复核推翻，两案维持独立工单、同批修复不合并（站点/根因/后果三者不同——「源文件破坏」与「产物永久损坏」是两个不同失效语义）。
- 本轮 CL-015（mvn 外部进程零超时挂死，F5）机制与「平台设施超时能力俱在未用」形态经复核成立，移交 C7 维持。
- 本轮终止信号复核成立：四个深挖面收敛结论与获取点配对表复核无误，无重启深挖事由。
- 全维度复核裁定与工单结构见 C6-resource-lifecycle.md「维度复核结论」（含 schemaReady 三登记主登记裁定：C6-001 主、C1-002/C3-004 吸收）。
