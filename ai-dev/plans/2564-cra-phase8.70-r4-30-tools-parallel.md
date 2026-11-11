# 2564 CRA Phase 8.70 — R4-30 6.1 搜索/FFM/聚合束（4 工单）

> Plan Status: active
> Last Reviewed: 2026-11-12
> Source: roadmap 工作项 8.70；批次定义 = cra-index.md :470 P2-G85 行；成员发现 = `ai-dev/audits/2026-10/2026-10-22-1400-deep-audit-nop-tools-parallel-concurrency/`；设计文档 = `../design/concurrency/tools-parallel-batch.md`（裁定 A-D + 勘正 4）
> Related: plan 2488（审计立项 6.1）；CL-202 闭环至 W-14
> **PA**：无（四工单全 Java 层，nop-rg/nop-find/nop-bytecode）
> **特殊约束**：① 批内顺序 W-05 → W-03（错误项通道接缝，不并单）；② W-03 R27 双机复现存证已灭失（勘正 1）→ 复现义务转新载体测试（assumeTrue JDK 门控）；③ W-05 失效面 9 项逐面覆盖（面 6 walker fail-fast 冻结不覆盖）；④ 先红后绿：C10-01 结构钉确定性红 + C10-03 行为红 + P2-02 条件红（JDK 门控）——cra-index :470「P2-01/02/03」口径与审计 C10 :89 权威清单冲突，以审计清单为准（勘正 2）

## Purpose

关闭 R4-30 的 **4 个 P2 工单**：patternBytes 惰性双写族（W-01 急切编码）、FFM 映射×外部截断 TOCTOU 可见性（W-03 错误上下文+边界声明）、invokeAll 首错弃全（W-05 对齐 rg 部分成功语义+9 失效面）、read-back 逐 artifact 重开 JarFile（W-14 对齐 collectJar 形态）。C10 缺保护 P2-01~04 随本体验收前置。

## Current Baseline（live 亲读 @ 2026-11-12，取证 agent fresh session；勘正 4 已登记）

- W-01：`SearchCommand.java:19/:63-68` patternBytes 惰性 check-then-act 双写+JMM 未安全发布；调用点 :98/:186/:245；FindCommand 全 final 对照
- W-03：映射段存活期×外部截断 TOCTOU（双机实测曾复现，存证 `_tmp/c1r2-verify/` 已灭失——勘正 1）；SearchCoordinator:126-127 已包装 NopRgException；可见性缺陷=NopRgException 无 toString+NopException.getMessage(:383) 不折 cause+CLI 只打顶层（勘正 4）
- W-05：`SearchCoordinator.java:117-127` invokeAll 首错弃全；失效面 9 项终裁（8=searchFileChunked:217-223 IOOBE 变体、9=FindCoordinator.java:148-153 同型弃全，live 核实成立）
- W-14：`NopBytecodeMain.java:244` read-back 逐 artifact 重开 JarFile（O(entries×central-dir)）；对照 `ClassArtifactCollector.collectJar:118` 正确形态
- 基线命令（R2-M2 勘正：aggregator 不递归）：`./mvnw -o test -pl nop-tools/nop-rg/nop-rg-core,nop-tools/nop-rg/nop-rg-cli,nop-tools/nop-find/nop-find-core,nop-tools/nop-bytecode -am`；benchmark/find-cli/rg-vector 编译级归 E1

## Goals

- 4 工单全部落地，先红后绿（结构钉/行为红/条件红按 ④ 口径）+ 变异论证。
- W-05 九项失效面逐面兑现并留证；W-14 注入式 jarOpener seam（run 重载收 Function<Path,JarFile>，现有 run() 委托默认工厂——final+static 不可覆写[R1-B1 定形]）断言每 jar ≤1 open。
- 契约知识落 `source-anchors.md` 新锚点行（nop-rg/nop-find 无 owner doc→缺口声明沿用）。

## Non-Goals

- W-06 取消族（AG-P3-01 不在 G85）；窗口收窄型修法（W-03 只做可见性+声明）；jcstress/JMH smoke（BG-5 排期）；nop-rg-benchmark 基准代码立案面。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-D + fresh-session 取证 + 勘正 4）

- [x] 设计文档（`ai-dev/design/concurrency/tools-parallel-batch.md`）
- [x] 成员取证（锚点零漂移 + 勘正 4：TruncFaultTest 灭失/先红清单口径冲突/失效面 9 项终裁 live 核实/W-03 可见性本体补全；R1-m7 追勘 NopRgMain.search 实为 :145）
- [x] 独立 draft review R2（fresh session 换 task_id：0B+2M+3m 全修——M-R2-1 W-03 注入通道定死[包私有 taskDecorator seam]/M-R2-2 基线命令 aggregator 空转勘正[逐叶清单+编译级回归面声明]/m-R2-3 NopRgMainTest:203 匿名类覆写登记/m-R2-4 门控机制=JDK 版本门+POSIX/m-R2-5 task 级 record 留实现评审；R1 11 项落位核验全 PASS+四问想象性分析全可落地）
- [x] 独立 draft review（R1 must-fix-first：1B+2M+8m 全修——B1 W-14 seam 形态 final+static 不可覆写→定注入式 jarOpener run 重载；M2 W-03 载体竞态空转→两腿确定性形态+条件红红证定义；M1 既有测试语义翻转登记；m1 ExecutionException 兜底腿/m2 InternalError message null/m3 chmod root 前提/m4 javadoc 措辞/m5 close 点/m6 DIRECTORY 平移确认/m7 锚点勘正/m8 checkstyle 门禁无插件改 scan-hollow 口径）

### Phase 2 - 4 工单实现 + 回归测试

Status: in progress
Targets: nop-tools/nop-rg + nop-tools/nop-find + nop-tools/nop-bytecode
Item Types: `Fix`（4）

**实施顺序**（串行；W-05 先于 W-03）

1. [x] W-05+A：SearchOutcome(results, errors) 部分成功语义——任务边界单点 catch（NopRgException/InternalError/RuntimeException→FileError 项），首错不弃全；CLI 有 errors→stderr 逐条+exit 2；失效面 9 项逐面兑现（面 9 FindCoordinator=不弃已完成 shard+汇总抛错含 shard 上下文，不引入部分 top-K 保 index 确定性契约）；载体测试+行为红（C10-03；chmod 腿 assumeTrue 非 root+POSIX[R1-m3]）+既有测试语义迁移改写 testRegexOnFileAboveChunkedThresholdFailsExplicitly[R1-M1]
2. [x] W-03+B：任务 lambda InternalError 补 rel 路径包装喂 W-05 错误通道 + MappedFileReader javadoc「映射存活期×外部截断未定义」边界声明；载体 TestMappingTruncationFault 两腿确定性形态[R1-M2+R2-M1]（单元腿 map 后截断断言 InternalError 含 rel/通道腿经 SearchCoordinator 包私有 taskDecorator seam 注入 InternalError/RuntimeException 验 coordinator 包装与边界转换；门控=JDK 版本门+POSIX[R2-m4]；条件红红证=修前两腿断言红，门控未过 skip 日志留证）
3. [x] W-01+C：SearchCommand 主构造器急切编码，patternBytes private final（保留 clone 返回+改写惰性缓存 javadoc[R1-m4]）；C10-01 反射结构钉（字段 final 断言修前确定性红）+并发复用等价腿
4. [x] W-14+D：NopBytecodeMain per-input 外层循环 JAR 单开一次 JarFile 经包私有 ClassByteSource 下沉 readBytes；sha256 对照 :256-259 逐字保留；ClassByteSource per-input try-with-resources close[R1-m5]；C10-04 openJar 计数 seam

Exit Criteria:

- [x] 4 工单全部实施
- [x] 载体测试存在且断言目标行为（TestSearchPartialFailure 4 腿 / TestMappingTruncationFault 3 腿 / TestFindCoordinatorShardFailure 2 腿 / TestSearchCommandImmutability 3 腿 / NopBytecodeMainTest readBackOpensEachJarAtMostOnce + NopRgMainTest partial-failure 扩展 2 腿）
- [x] 先红后绿：C10-01 结构钉红证（实测 red "got modifiers=private"）+C10-03 行为红（链1 首错即抛还原 2 红；chmod 腿 assumeTrue 非 root+POSIX）+C10-02 条件红（通道腿修前红实测；单元腿 darwin 平台门 skip 留证——Linux 机重跑留证为 closure audit 复核项）；变异链 13 条全过（W-05×4 / W-03×3 / W-01×3 / W-14×3），逐链恢复 grep 断言命中——`_tmp/red-green-ledger-2564.md`
- [x] W-05 九项失效面逐面兑现清单留证（设计裁定 C 表 9 项逐面落地；面 6 冻结豁免由 walker 穿透腿钉住）
- [x] W-14 sha256 对照逐字保留（冻结钉 tampered/corruptManifest 双绿）+ openJar ≤1 断言（opens==1，修前形态 4）
- [x] E3：四叶绿（rg-core 457 / rg-cli 196 / find-core 20 / bytecode 77，`-am` 全量一轮 0 fail）[R2-M2]；勘误：上游 nop-core TestResourceCacheInvalidationChain 3 失败=既有 mtime 粒度 flake（单测复跑绿、本批零触碰）
- [x] E1 全量编译门禁 BUILD SUCCESS（`./mvnw clean install -DskipTests -T 1C`，benchmark/find-cli/rg-vector 编译级覆盖）
- [x] 契约知识落 `source-anchors.md` 新锚点行（R4-30-A/B/C；缺口声明沿用设计 §七）+ nop-bytecode.md read-back 条目
- [x] 工具门禁：check-doc-links --strict EXIT=0（1 既有 warning 非 error）；scan-hollow `--severity high` 四叶 EXIT=0
- [x] `ai-dev/logs/` 已更新（ai-dev/logs/2026/11-12.md）

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（4/4）
- [ ] 所有 in-scope confirmed contract drifts 已收敛
- [ ] 行为/契约结果已达成
- [ ] 必要 focused verification 已完成
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 受影响 owner docs 已同步（缺口声明+新锚点）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [ ] `./mvnw compile`（触达模块）
- [ ] `./mvnw test`（逐模块）
- [ ] 代码规范自查（imports 分组/无裸 RuntimeException）+ scan-hollow --severity high 通过（仓库无 checkstyle 插件——R1-m8 口径）

## Deferred But Adjudicated

### JMH smoke（BG-5）/ jcstress / W-06 取消族 / 窗口收窄型修法
- Classification: `watch-only residual`（裁定 D/审计明示移交边界）
- Why Not Blocking Closure: BG-5 随基准缺口排期；jcstress 沿 C10 建议不做；W-06 不在 G85；W-03 裁定只做可见性。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-12，must-fix-first）**：1B+2M+8m 全修落 plan/design。B1 W-14「包私有可覆写工厂」在 public final+全 static 的 NopBytecodeMain 上不可实现→定形注入式 jarOpener（run 重载收 Function<Path,JarFile>，现有 run() 委托默认工厂，不去 final 不做结构重构）；M2 W-03 载体原形态为竞态测试（truncate 大概率迟到→绿但零覆盖/SIGBUS 风险）→改两腿确定性形态（单元腿 map 后截断+通道腿注入 RuntimeException）并定义条件红红证形态；M1 testRegexOnFileAboveChunkedThresholdFailsExplicitly:243-256 修后必红→登记语义迁移改写（errors 含路径+其余结果照常，强化非弱化）；m1 收集循环补 ExecutionException 兜底腿；m2 InternalError 腿只断 relativePath；m3 chmod 腿 assumeTrue 非 root+POSIX；m4 :61 惰性缓存 javadoc 同步改写；m5 ClassByteSource per-input try-with-resources close；m6 DIRECTORY 腿平移+既有测试 static run() 兼容确认；m7 设计 :66 NopRgMain.search 锚点勘正（:145）；m8 checkstyle 无插件→改代码规范自查+scan-hollow 口径。调用点清单核实无遗漏（cli/benchmark/测试全改写面）。修正后 ready-to-execute（W-01 可直接执行）。

**Draft review R2（fresh session，2026-11-12）**：0B+2M+3m——M-R2-1 W-03 两腿缺注入通道机制化（单元腿停在 reader 层验不到 coordinator 包装、变异链失配）→ 定死包私有 taskDecorator seam（默认恒等，测试注入装饰器确定性送 InternalError/RuntimeException 进任务边界）；M-R2-2 基线命令 -pl aggregator 不递归空转 → 勘正为逐叶 rg-core/rg-cli/find-core/bytecode -am+benchmark/find-cli/rg-vector 编译级归 E1；m-R2-3 NopRgMainTest:203 匿名类覆写面登记；m-R2-4 截断腿门控=JDK 版本门+POSIX（探测判定有 SIGBUS 风险不可用/Windows 映射句柄锁）；m-R2-5 task 级联合 record 留实现评审一句登记。R1 全部落位核验 PASS。两轮收敛无 Blocker，进入实施。

## Closure

（待实施与 closure audit）
