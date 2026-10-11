# 2403 full-code-review R1.37 — nop-runner/cli 修复批（P1-4.5-R1/R2 + P1-4.5-D1）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.37；findings `ai-dev/audits/check/runner-cli.md`（P1 #1/#2、P2 #7 命令未注册）+ `ai-dev/audits/check/demo-migration.md`（P1 #1）；跨模块复制关系见 `4.5-frontend-devtools-migration-runner-utils-audit.md:262/508`；P2/P3 路由见 `6.9-check-p2p3-watchonly-archive.md:45`
> Related: `nop-runner/nop-cli-core`、`nop-migration/nop-ofbiz-migration`
> Owner Doc: `docs-for-ai/01-repo-map/module-groups.md`（CLI/迁移归属）
> Plan Review: agent_fa6f002d 首轮 NOT READY（0 Blocker + 4 Major：M1 addCatalog 测试未指明表头行/4 列且宿主类 @Disabled、M2 watch 回退生命周期/接线未定、M3 未注册命令被误归类为 out-of-scope improvement、M4 roadmap 措辞漂移 + 7 Minor）→ 本轮全修：**CliDiffCommand 注册进 MainCommand 纳入 in-scope**（消除 P2 可达性缺陷并让 P1 真实可达）、watch 回退明确 local/injected 所有权 + try/finally + 接线判据、迁移测试定死表头行 index 1 且落在新建启用测试类、roadmap 措辞在 Phase 3 修正、复用 `ERR_CLI_FILE_NOT_EXISTS`、补 #23/#24 显式条目与 lint/scan 门禁；agent_8aa2b2a7 二轮 NOT READY（1 新 Major：OrmModel 以 name 为键，两实体须 setName；6 Minor）→ 全修（MAJ-1 setName、WatcherHandle 契约、测试包/错误码断言、真实入口 e2e、import 清理边界、Deferred 措辞、watch 判别腿改为代码读数）→ READY（评审明确「fix MAJ-1 后即 ready」）。

## Purpose

收口 nop-runner/cli 与迁移工具三条 P1 及一条可达性 P2：①`CliDiffCommand.loadModel` 忽略参数恒用 `newModelPath`（diff 恒为空）+ `-N/--new-model` 未 required + 不校验资源存在；②`CliDiffCommand` 未注册进 `MainCommand`（命令不可达，P2 #7，随 ① 一并修复）；③`CliWatchCommand` 依赖容器 `@Inject FileWatcherFactory`，在无 DI 的 jdk11/回退启动器下 `watch` 必现 NPE；④`EntityDefDirTransformer.addCatalog` 序号列 off-by-one（首行写 2），并删除 runner 侧同构死副本方法 `GenOrmHelper.addCatalog`。

## Current Baseline

- **P1-4.5-R1**（live）：`nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliDiffCommand.java:78-84` `loadModel(String modelPath)` 体内 `ResourceHelper.resolveRelativePathResource(newModelPath)` 用字段而非参数——`call():62-63` 的 `loadModel(oldModelPath)`/`loadModel(newModelPath)` 解析同一资源，diff 恒 `{}`；`StringHelper.fileExt(modelPath)` 与资源不一致。`-O` 已 `required=true`（:34），`-N`（:38）未声明。`ResourceHelper.resolveRelativePathResource(null)` 对空路径返回当前目录资源。`CliErrors.ERR_CLI_FILE_NOT_EXISTS`（"文件不存在:{path}"，ARG_PATH）已存在，直接复用。
- **P1-4.5-R1b（P2 #7，同批）**（live）：`MainCommand.java:17-35` 恰 17 个 subcommands，无 `CliDiffCommand.class`；`nop-cli diff ...` 报 picocli "Unmatched argument"。
- **P1-4.5-R2**（live）：`CliWatchCommand.java:62-67` `@Inject FileWatcherFactory fileWatcherFactory;`，`call()` 首行 `fileWatcherFactory.newFileWatcher()` 无 null 防护。`nop-cli-jdk11` pom exclude `quarkus-picocli`/`nop-quarkus-core-starter` 且 mainClass=`NopCliMainForJdk11`（`new CommandLine(new MainCommand())` 默认反射工厂）；`NopCliApplication.run` 的 `factory == null` 回退分支（:40-47）同样无注入 → 字段 null → NPE。`FileWatcherFactory` 未在 beans.xml 定义（NopIoC 无 bean）。全仓命令仅此一处 `@Inject`（除入口类自身）。同模块先例：`CliWatchZipCommand.java:47-60/78/90` 在 call() 内自建 `NioFileWatchService` + `start()`，finally `stop()`。
- **P1-4.5-D1**（live）：`nop-migration/nop-ofbiz-migration/.../EntityDefDirTransformer.java:129-131` `ExcelRow row = table.makeRow(index++); row.makeCell(0).setValue(index);`——post-increment 后写「下一行」行号：首行写 2，第 n 行写 n+1。方法在循环前读取 `table.getCell(1,0..3).getStyleId()`（:124-127），故测试必须先在**第 1 行（header 行 index=1）创建 0..3 四列**。
- **跨模块复制**：`nop-runner/nop-cli-core/.../GenOrmHelper.java:66-94 addCatalog` 与上述同构（仅 sheet 名 "Catalog"/"目录" 不同），**无任何调用方**（全仓 grep 仅定义处）；同文件 `saveOrmXml`/`saveOrmToExcel` 有活调用方（`CliReverseDbCommand:82/84`、`CliGenOrmExcelCommand:90`、`TestOrmCodeGen:98`），故**只删 `addCatalog` 方法，不删类**。
- **测试基建**：`nop-runner/nop-cli-core` 有 `TestNopCli`（整体 `@Disabled`）、`TestCliWatchZipCommand`、`TestCliCallToolsCommand`、`TestOrmCodeGen`；`nop-migration/nop-ofbiz-migration` 的 `TestEntityDefDirTransformer` 为**类级 `@Disabled`**（不得作为新用例宿主）。`ExcelWorkbook.addSheet`/`ExcelSheet.getTable`/`AbstractTable.makeCell(row,col)` 可编程构建表头。
- **残留记录**：所编辑的 `CliWatchCommand.call()` 中 `catch (IOException e) {}` 吞异常为 runner-cli P3 既有项，本批不改，显式登记 out-of-scope residual。

## Goals

- `CliDiffCommand`：`loadModel` 使用传入参数解析资源；`-O`/`-N` 均 `required=true`；加载前 `resource.exists()` 校验，缺失抛 `ERR_CLI_FILE_NOT_EXISTS`（不静默/不解析当前目录）；**注册进 `MainCommand`** 使命令可达。
- `CliWatchCommand`：无容器注入时命令自身构造并**拥有** `FileWatcher`（`NioFileWatchService.start()`+`GlobalExecutors.globalTimer()`），结束 `stop()`；有注入时沿用注入实例且**不** stop（所有权归容器）——jdk11 与 factory==null 回退路径不再 NPE。
- `EntityDefDirTransformer.addCatalog` 序号列与行位置一致（首行 1）；`GenOrmHelper.addCatalog` 死副本方法删除（类与活方法保留）。
- 判别测试：三修各配 focused 回归；受影响模块测试全绿。

## Non-Goals

- 抽 nop-cli-core 共享引导方法统一装配（4.5 报告结构性建议覆盖 mapper/destroy/args==0 三漂移，范围大于本批；本批只做 watch NPE 的正确回退 + diff 命令注册）。
- `CliRunCommand` listFiles null（P2 #1）、`loadFromDir` listFiles（P2）——见 Deferred（显式 successor + why-not-blocking）。
- 入口 main/destroy 装配漂移（P3-4.5-004 等）；`catch(IOException){}` 静默残留。

## Scope

### In Scope

- `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliDiffCommand.java`
- `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/MainCommand.java`（注册 `CliDiffCommand.class`）
- `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java`
- `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/GenOrmHelper.java`（删 `addCatalog` 方法 + 清理不再使用 import）
- `nop-migration/nop-ofbiz-migration/src/main/java/io/nop/ofbiz/migration/transform/EntityDefDirTransformer.java`
- 测试：`nop-runner/nop-cli-core/src/test/**`（新建启用测试类）、`nop-migration/nop-ofbiz-migration/src/test/**`（新建启用测试类）
- `ai-dev/backlog/full-code-review-roadmap.md` R1.37 措辞勘正（Phase 3）

### Out Of Scope

- 共享引导方法重构；jdk11 mapper/destroy；CliRunCommand/loadFromDir 的 P2；其他 P2/P3

## Execution Plan

### Phase 1 - CliDiffCommand 正确性 + 注册；CliWatchCommand 无 DI 回退

Status: completed
Targets: `CliDiffCommand.java`、`MainCommand.java`、`CliWatchCommand.java`、cli-core 测试

- Item Types: `Fix`

- [x] `CliDiffCommand.loadModel`：`resolveRelativePathResource(modelPath)`（用参数）；`-N/--new-model` 增 `required = true`；加载前 `resource.exists()` 校验，缺失抛 `new NopException(CliErrors.ERR_CLI_FILE_NOT_EXISTS).param(ARG_PATH, resource.getPath())`（复用既有错误码，不新增）
- [x] `MainCommand`：`subcommands` 数组加入 `CliDiffCommand.class`（P2 #7 可达性修复；17→18）
- [x] `CliWatchCommand`：抽 package-private `newFileWatcher()`，返回 package-private `WatcherHandle { final FileWatcher watcher; final boolean locallyOwned; }`；`fileWatcherFactory != null` → `new WatcherHandle(factory.newFileWatcher(), false)`；否则本地 `NioFileWatchService service = new NioFileWatchService(); service.start();` → `new WatcherHandle(new FileWatcher(service, GlobalExecutors.globalTimer()), true)`。`call()` try/finally：仅当 `locallyOwned` 时 `watcher.stop()`（`FileWatcher.stop()` 委托 `watchService.stop()`；注入实例所有权归容器，不 stop）——镜像 `CliWatchZipCommand:47-60/78/90` 先例
- [x] 测试（**新建启用测试类，置于 `io.nop.cli.commands` 以访问 package-private `loadModel`/`newFileWatcher`**，不用 `@Disabled` 宿主）：①`CliDiffCommand` 以 picocli 直接执行 `-O old.json -N new.json -o target/.../diff.json`（两个不同 JSON fixture）断言输出 diff 非空且含差异键（修复前恒 `{}`）；②缺 `-N` → picocli 参数校验失败（required）；③**直接调 package-private `loadModel(<不存在路径>)` 并断言 `NopException.getErrorCode()==ERR_CLI_FILE_NOT_EXISTS`（picocli 默认 handler 只暴露退出码）**；④`MainCommand` 注解 subcommands 含 `CliDiffCommand`（可达性）；⑤**端到端（#22/#23）**：`new CommandLine(new MainCommand()).execute("diff", "-O", old, "-N", new, "-o", out)` 断言 exit 0 且输出非空（真实入口可达）；⑥`CliWatchCommand` 字段 null 时 `newFileWatcher()` 返回非 null，且**测试内 `watcher.stop()`**（NioFileWatchService 为非 daemon 线程，防泄漏）
- [x] **判别验证（stash 腿）**：临时还原 `loadModel` 用 `newModelPath`（保持注册）→ ①/⑤FAIL（diff 空）；临时删除 `MainCommand` 中 `CliDiffCommand.class` → ④/⑤FAIL；①⑤为真实 FAIL→PASS。watch 回退腿为**代码读数判别**（helper 直测不因 `call()` 是否解引用字段而失败，故不宣称红绿 FAIL→PASS，按 #23 代码追踪记录）
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`（内部行为/可达性修复，无契约文档面）
- [x] `./mvnw test -pl nop-runner/nop-cli-core -am -T 1C`

Exit Criteria:

- [x] `loadModel(param)` 使用参数解析资源（①断言旧/新模型不同 → diff 非空）
- [x] `-N` 缺失被 picocli 拒绝；资源缺失抛 `ERR_CLI_FILE_NOT_EXISTS`（#24 fail-loud）
- [x] `MainCommand` 注册 `CliDiffCommand`（④注解断言 + ⑤真实入口 `MainCommand` 执行 diff 断言）；命令真实可达（#22/#23）
- [x] `CliWatchCommand` 无注入时 watcher 非 null 且本地拥有者被 stop（⑤；#23 接线：`call()` 经 `newFileWatcher()` 获取，非直接解引用字段）
- [x] 判别 stash 腿 FAIL→PASS 记录在 plan
- [x] nop-cli-core 全模块全绿

执行证据（Phase 1）：
- `TestCliDiffAndWatchFixes` 6/6 绿（新建启用类，`io.nop.cli.commands`）。
- **判别 stash 腿**：①临时还原 `loadModel` 用 `newModelPath` → 恰 3 FAIL（diff 空 / 主入口 diff 空 / 资源缺失未 fail-loud，`_tmp/r137-stash-1.log`）；②临时移除 `MainCommand` 的 `CliDiffCommand.class` → 恰 2 FAIL（注册断言 + 主入口执行返回 2，`_tmp/r137-stash-2.log`）；恢复后 6/6 绿。
- watch 回退腿按 #23 代码读数记录（helper 直测不随 `call()` 解引用字段而红绿）。

### Phase 2 - 迁移 addCatalog off-by-one + 删 runner 死副本方法

Status: completed
Targets: `EntityDefDirTransformer.java`、`GenOrmHelper.java`、migration 测试

- Item Types: `Fix`

- [x] `EntityDefDirTransformer.addCatalog:129-131`：`makeRow(index++)` 后写 `setValue(index - 1)`，首行=1、逐行递增
- [x] `GenOrmHelper`：删除死副本方法 `addCatalog`（:66-94）并清理随之不再使用的 import（`ExcelCell`/`ExcelRow`/`ExcelSheet`/`ExcelTable`/`IEntityModel`/`java.util.List`；**`ExcelWorkbook` 保留**——`saveOrmToExcel` 仍用）；保留 `saveOrmXml`/`saveOrmToExcel`/`newReportEngine`（有活调用方）；全仓 grep `GenOrmHelper.addCatalog` 零残留
- [x] 测试（**新建启用类** `io.nop.cli` 无关——迁移测试包 `io.nop.ofbiz.transform`/`io.nop.ofbiz.migration.transform`，不用 `@Disabled` 的 `TestEntityDefDirTransformer`；`addCatalog` 为 static 且无 DB 依赖，普通 JUnit 即可）：构建 `ExcelWorkbook`，`new ExcelSheet()` + `setName("目录")` + `workbook.addSheet(sheet)`；**在第 1 行（index=1）为列 0..3 创建 cell**（`table.makeCell(1, c)` 返回 `ICell`，需 cast 为 `ExcelCell` 后 `setStyleId`）；OrmModel **两个实体必须 `setName("A")`/`setName("B")`（OrmModel 以 `OrmEntityModel::getName` 为 KeyedList 键，null 名会折叠为一条）** 且 tableName 不同 → 调 `addCatalog` → 断言 `table.getCell(1,0)` 值=1、`table.getCell(2,0)` 值=2（修复前=2/3）
- [x] **判别验证（stash 腿）**：还原 `setValue(index)` → 单测 FAIL（首行 2），恢复后 PASS
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`（迁移产物序号列修复，无契约文档面）
- [x] `./mvnw test -pl nop-migration/nop-ofbiz-migration -am -T 1C`
- [x] checkstyle/lint：`./mvnw -q checkstyle:check -pl nop-runner/nop-cli-core,nop-migration/nop-ofbiz-migration` 或按仓配置；不可用时显式记录 `No lint gate configured for these modules`

Exit Criteria:

- [x] 序号列首行=1、逐行递增（单测在案）
- [x] `GenOrmHelper.addCatalog` 已删除且编译通过（无调用方断裂；import 清理）
- [x] 判别 stash 腿 FAIL→PASS 记录在 plan
- [x] nop-ofbiz-migration 测试全绿；lint 门禁处置记录

执行证据（Phase 2）：
- `GenOrmHelper.addCatalog` 已删除（保留 `saveOrmXml`/`saveOrmToExcel`/`newReportEngine`），import 清理（`ExcelCell`/`ExcelRow`/`ExcelSheet`/`ExcelTable`/`IEntityModel`/`java.util.List`；`ExcelWorkbook` 保留）。
- `TestEntityDefCatalogAddCatalog` 1/1 绿（新建启用类）。
- **判别 stash 腿**：临时还原 `setValue(index)` → 恰 1 FAIL `first catalog row must be sequence 1 ... expected: <1> but was: <2>`（`_tmp/r137-stash-3.log`）；恢复后绿。
- 全量回归：`./mvnw test -pl nop-runner/nop-cli-core,nop-migration/nop-ofbiz-migration -am -T 1C` BUILD SUCCESS（cli-core 42/0/0 skipped 15；ofbiz-migration 2/0/0 skipped 1）。

### Phase 3 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`（如涉归属）、roadmap、index、daily log

- Item Types: `Fix | Proof`

- [x] 评估 owner doc：CLI 命令行为/可达性修复不改变模块归属（module-groups.md 无 CLI 命令级表述）→ 显式记录 `No owner-doc update required`
- [x] **roadmap R1.37 措辞勘正**：`同步删 runner 死副本 GenOrmHelper` → `删除死副本方法 GenOrmHelper.addCatalog`（类与活方法保留），并把 `P1-4.5-R1` 补注含 MainCommand 注册（已落盘）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.37 翻 done；index P1-4.5-R1/R2/D1 → `fixed`；daily log 条目（Lead 2026-10-10 同步完成）
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `./mvnw -q compile -pl nop-runner/nop-cli-core,nop-migration/nop-ofbiz-migration -am` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前以 `active` 状态运行 exit 0；翻 `completed` 后仅因上面的 Lead 台账项 L122/L132 报未勾选）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-runner --severity high` 与 `--module nop-migration --severity high` 均退出码 0

Exit Criteria:

- [x] owner doc 处置裁定记录；roadmap 措辞与代码一致
- [x] doc-links 检查退出码 0
- [x] roadmap/index/log 三方一致（roadmap R1.37=done、index P1-4.5-R1/R2/D1=fixed + R1.37 行=done、daily log 10-10 条目在位）
- [x] 独立 closure audit 完成并落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-4.5-R1/R1b/R2/D1）
- [x] 行为/契约结果已达成（判别测试在案）
- [x] 必要 focused verification 已完成（cli-core + ofbiz-migration 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 diff 命令真实加载两个不同模型、`MainCommand` 真实注册、watch 无注入路径真实构造并 stop 本地 watcher、addCatalog 真实写入序号
- [x] `./mvnw -q compile -pl nop-runner/nop-cli-core,nop-migration/nop-ofbiz-migration -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前 `active` 状态下 exit 0；翻 `completed` 后仅 L122/L132 两条 Lead 台账项未勾选）

## Deferred But Adjudicated

### CliRunCommand listFiles null（P2 #1）与 loadFromDir listFiles（P2）

- Classification: `watch-only residual`（确认 live 但**不在本批 R1.37 scope**；归 P2/R1.0 通道）
- Why Not Blocking Closure: 与本批三条 P1 无因果关系、缺陷面不同（`CliRunCommand` 为独立命令；`EntityDefDirTransformer.loadFromDir` 虽在 Phase 2 同文件但属另一 P2 缺陷面）；本批 Phase 2 只改 `addCatalog`，不改 `loadFromDir`，故不构成 in-scope 缺陷降级。
- Successor Required: `yes`
- Successor Path: runner-cli 的 P2/P3 经 `6.9-check-p2p3-watchonly-archive.md:45` 的「still-live 且无 finding-ID 承载」通道汇入 R1.0 裁决；在 roadmap R1.x 展开中具名承载

### nop-cli-core 共享引导方法统一装配（4.5 报告结构性建议）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 覆盖 mapper/destroy/args==0 三装配漂移，范围大于本批 P1；本批以命令内 null 回退 + 命令注册消除可观察缺陷，不引入新装配歧义。
- Successor Required: `no`

### ~~CliWatchCommand `catch (IOException) {}` 静默残留（runner-cli P3）~~（已在本批关闭）

- Classification: 无（已修复，非 deferred）
- 说明：本批编辑该文件后，pre-commit 的 ast-grep `java-lint-empty-catch` 硬门禁拒绝提交——按 Minimum Rule #13 不可降级，故本批一并处理：catch 体内加 `LOG.debug("...input read ended, stopping watch", e)`（EOF 为正常停止路径，仍可观测）。

## Non-Blocking Follow-ups

（无；P2 项已给显式 successor）

## Closure

Status Note: R1.37 三条 P1（P1-4.5-R1/R1b/R2）+ P1-4.5-D1 已闭环。独立 closure audit（本 subagent，独立于实现者、未改产品代码）逐条回看 live 代码 + focused 测试后 PASS：`CliDiffCommand.loadModel(param)` 用参数解析资源且缺失时抛 `ERR_CLI_FILE_NOT_EXISTS`（fail-loud），`-N/--new-model` 为 `required=true`，`MainCommand` 注册 `CliDiffCommand.class`（18 subcommands）使 `nop-cli diff` 真实可达；`CliWatchCommand.call()` 经 `newFileWatcher()` 取 `WatcherHandle`，仅 `locallyOwned` 时 `stop()`，注入实例不 stop；`EntityDefDirTransformer.addCatalog` 序号 `setValue(index - 1)`（首行 1），`GenOrmHelper.addCatalog` 死副本已删（零调用方，类与 `saveOrmXml`/`saveOrmToExcel` 保留）。判别 stash 三腿（3 FAIL / 2 FAIL / 1 FAIL）live 日志复核一致。唯一未勾选项 L122/L132 为 Lead 负责的 roadmap R1.37 翻 done + audit index P1-4.5-R1/R2/D1 → `fixed` 台账动作（本审计按指令未编辑 roadmap/index），不改变代码/契约成立性。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent)
- Audit Session: session-9601d130-1bfd-4608-86de-471049fd6c0e（独立于实现者；read-only 审计 + 本 plan Closure/勾选回填，未修改产品代码）
- Evidence:
  - **Phase 1 Exit Criteria PASS**：
    - `loadModel(param)` 用参：`CliDiffCommand.java:79-89`（`resolveRelativePathResource(modelPath)` + `resource.exists()` 缺失抛 `ERR_CLI_FILE_NOT_EXISTS`，`CliErrors.java:30-31` 复用既有码）；`TestCliDiffAndWatchFixes.diffLoadsBothModelsAndProducesNonEmptyDiff` 断言输出含差异键 `"b"`。
    - `-N` required：`CliDiffCommand.java:39`；`diffRequiresNewModelOption` 断言缺 `-N` 时 picocli exit≠0。
    - `MainCommand` 注册（17→18）：`MainCommand.java:35` `CliDiffCommand.class`；`mainCommandRegistersDiffSubcommand`（注解断言）+ `diffReachableThroughMainCommandEntryPoint`（`new CommandLine(new MainCommand()).execute("diff",...)` exit 0 且输出含 `"y"`，#22/#23 真实入口）。
    - watch 回退接线（#23）：`CliWatchCommand.java:74-91` `WatcherHandle{watcher, locallyOwned}` + `newFileWatcher()`（factory==null → `NioFileWatchService.start()` + `new FileWatcher(service, GlobalExecutors.globalTimer())`，`locallyOwned=true`）；`:93-105` `call()` 首行 `newFileWatcher()`，try/finally 仅 `locallyOwned` 时 `watcher.stop()`（注入实例 `locallyOwned=false` 不 stop，所有权归容器）；`FileWatcher.java:45-47` `stop()` 委托 `watchService.stop()`。`watchCommandBuildsWatcherWithoutContainerInjection` 断言非 null + `locallyOwned=true` + 测试内 stop（非 daemon 线程不泄漏）。
    - 判别 stash 腿（live 复核 `_tmp/r137-stash-1.log` / `-2.log`）：还原 `newModelPath` → 恰 3 FAIL（`diffLoadsBothModels...` 空 diff / `loadModelFailsLoudly...` 错码 / `diffReachableThroughMainCommand...`）；移除注册 → 恰 2 FAIL（注册断言 + 主入口返回 2）。watch 回退按 #23 代码读数判别（helper 直测不随 `call()` 解引用字段红绿），诚实记录。
  - **Phase 2 Exit Criteria PASS**：
    - 序号首行=1：`EntityDefDirTransformer.java:130-131` `makeRow(index++)` 后 `setValue(index - 1)`；`TestEntityDefCatalogAddCatalog.catalogSequenceMatchesRowPosition` 真实构建 workbook（`new ExcelWorkbook()` + sheet `"目录"` + `table.makeCell(1,0..3)` 表头样式，`src/test/.../TestEntityDefCatalogAddCatalog.java:21-33`）+ 两个 `setName("A")/("B")` 实体（:35-51，避 KeyedList 折叠），断言 `getCell(1,0)=1`、`getCell(2,0)=2`——非 tautology。
    - 死副本删除：全仓 grep `GenOrmHelper.addCatalog` 零残留；`GenOrmHelper.java` import 清理（`ExcelCell/ExcelRow/ExcelSheet/ExcelTable/IEntityModel/java.util.List` 已去，`ExcelWorkbook` 保留）；活调用方仍在（`CliReverseDbCommand:82/84`、`CliGenOrmExcelCommand:90`、`TestOrmCodeGen:98`）；`./mvnw -q compile` 通过（无调用方断裂）。
    - 判别 stash 腿：`_tmp/r137-stash-3.log` `expected: <1> but was: <2>` 恰 1 FAIL，恢复后绿。
  - **focused verification**：`./mvnw test -pl nop-runner/nop-cli-core,nop-migration/nop-ofbiz-migration -am -T 1C` BUILD SUCCESS；surefire 报告 cli-core 42/0/0 skipped 15（含 `TestCliDiffAndWatchFixes` 6/6）、ofbiz-migration 2/0/0 skipped 1（含 `TestEntityDefCatalogAddCatalog` 1/1）——与作者报告一致。
  - **Closure Gates PASS**：L136-L145 全绿（owner doc：`docs-for-ai/` 无 nop-cli 命令级文档，`module-groups.md` 不涉命令行为 → `No owner-doc update required` 诚实；Deferred 分类：`CliRunCommand`/`loadFromDir` P2 有显式 successor `6.9-check-p2p3-watchonly-archive.md:45`→R1.0，`catch(IOException){}` 为既有 P3 显式登记，无 in-scope live defect 降级）。
  - **Anti-Hollow**：`scan-hollow-implementations.mjs --module nop-runner --severity high` 与 `--module nop-migration --severity high` 均 exit 0（0 high/critical）；`check-doc-links.mjs --strict` exit 0（0 errors，6 条与本次无关的既有 warning）；端到端：`diff` 经 `MainCommand` 真实入口加载两个不同 JSON 并产出非空 diff（非仅类型存在）。
  - **check-plan-checklist**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2403-full-code-review-R1.37-runner-cli-fixes.md --strict` closure 前（`active`）exit 0；翻 `completed` 后 exit 1，**仅** L122 + L132 两条 Lead 台账项（roadmap R1.37 翻 done / index P1-4.5-R1/R2/D1 → fixed）未勾选，无其他未勾选项、Closure Evidence 已写入。

Follow-up:

- Lead-owned：roadmap R1.37 翻 `done`；audit index `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md:189-191` P1-4.5-R1/R2/D1 → `fixed`（完成时重跑 `check-plan-checklist --strict` 应 exit 0）。
- 无其他 plan-owned 剩余工作；`CliRunCommand`/`loadFromDir` P2 已给显式 successor，`catch(IOException){}` 登记为 P3 residual。
