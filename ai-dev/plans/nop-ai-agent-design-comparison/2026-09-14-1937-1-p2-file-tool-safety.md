---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-FILES
group: "2026-09-14-1937"
verify: [test]
---

# P2 文件工具安全收口（ThoughtStorage 路径原语 + 原子写 + 目录/删除失败 fail-fast）

## Current Baseline

- 来源：deep-audit round 2 的 P2 Follow-up Backlog（roadmap `## Follow-up Backlog` 文档顺序第 19/20 项；M0–M6 全部 WI 已勾选，这是剩余未勾选集中的前两项），全部经 live 复核（HEAD `c00173afae`，2026-09-14）：
  1. **ThoughtStorage 任意路径读写原语**（`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java`）：`exportSession`（:133-145）对调用方传入的 `filePath` 直接 `FileHelper.writeText(new File(filePath), json, null)`（:141）；`importSession`（:147-158）直接 `FileHelper.readText(new File(filePath), null)`（:152）——无任何目录包含性校验。同文件 `getSessionFile`（:62-68）对 sessionId 有 `AiToolsHelper.requireValidSessionId` fail-closed 守卫（:66），filePath 面缺失同类守卫。当前唯一调用方是测试（`TestThoughtStorage.testExportImportRoundTrip` :128-141，导出到 storageDir 内文件）；owner doc `ai-dev/design/nop-ai/03-sequential-thinking-storage.md` 未提及这两个方法（无契约）。一旦接线到请求 bean 即成任意文件读写原语。
  2. **文件修改工具原地截断写无原子性**：`LocalToolFileSystem.writeText`（`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:161-164`）经 `FileHelper.writeText(file, content, UTF-8, append)` 直接覆盖目标文件（无 temp+rename、无 fsync、无 .bak）；上游调用点 `PatchFileExecutor.java:74`、`ApplyDeltaExecutor.java:68` 均以 `writeText(..., false)` 覆盖原文件；`ThoughtStorage.saveSession`（:82-87）同模式。部分写/崩溃/ENOSPC 时旧内容永久丢失；而 `move`/`copy`（:236-238、:255-259）失败会转异常，同一抽象内失败姿态不一致。
  3. **mkdirs/delete 返回值被忽略**：`LocalToolFileSystem.mkdirs`（:198-203）忽略 `dir.mkdirs()` 返回值、`delete`（:206-220）忽略 `file.delete()`/`FileHelper.deleteAll` 结果；`CreateDirectoryExecutor.java:35-37` 与 `DeleteFileExecutor.java:38-40` 无条件返回 "Directory created successfully"/"File deleted successfully"——失败仍报成功。
- 归属模块：nop-ai-tools（项 1）、nop-ai-toolkit（项 2/3）。
- 现有测试基建：`TestThoughtStorage`（nop-ai-tools，9 例）、`TestLocalToolFileSystemErrors`（nop-ai-toolkit）、`CreateDirectoryExecutorTest`/`DeleteFileExecutorTest`/`PatchFileExecutorTest`/`ApplyDeltaExecutorTest`/`WriteFileExecutorTest`。
- 错误码容器：nop-ai-toolkit 有 `NopAiToolkitErrors`（`nop.err.ai.toolkit.invalid-argument`/`invalid-state`）；nop-ai-tools 无模块错误码容器（项 1 沿用 `NopAiException` 或复用 `NopAiCoreErrors` 既有会话码，按 error-handling.md 两档策略）。

## Goals

- ThoughtStorage 的 export/import 路径原语收口：或保留并 fail-closed 约束在 storageDir 内，或按零生产调用/无 owner 契约裁定删除；两条路径二选一并记录理由。
- 文件修改工具（`LocalToolFileSystem.writeText` 及经其写盘的 `PatchFileExecutor`/`ApplyDeltaExecutor`、`ThoughtStorage.saveSession`）具备原子替换语义：写入失败/中断不破坏旧内容；`mkdirs`/`delete` 失败不再静默报成功。
- 回归测试覆盖：逃逸拒绝、原子写失败保留旧内容、mkdirs/delete 失败在 executor 层返回 error 而非 success。
- 涉及模块 `./mvnw test -pl nop-ai/nop-ai-tools,nop-ai/nop-ai-toolkit -am` 通过；check-doc-links 0 error。

## Non-Goals

- 不改 `IToolFileSystem` 接口签名与既有正常读写语义（append 模式、路径白名单 `isPathAllowed` 行为不变）。
- 不处理 roadmap P2 第 21-23 项（gateway MFA/getCapabilities/Feishu 凭证，由同批 `2026-09-14-1937-2` 覆盖）。
- 不引入跨进程文件锁、不处理多 JVM 并发写语义（保持单 JVM 工具语义）。
- 不运行 mvn 全量构建。

## Phase 1 — ThoughtStorage export/import 路径原语收口

Status: planned

Targets: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java`、`nop-ai/nop-ai-tools/src/test/java/io/nop/ai/tools/sequential_thinking/service/TestThoughtStorage.java`、`ai-dev/design/nop-ai/03-sequential-thinking-storage.md`（如需登记裁定）

- Item Types: `Decision | Fix | Proof`

- [ ] `Decision` 裁定 export/import 归宿：(A) 保留并约束——`filePath` 解析后的 canonical 路径必须位于 storageDir canonical 内（新建/不存在的路径先 canonical 父目录再拼名），逃逸一律 fail-closed；或 (B) 删除——两方法全仓零生产调用且无 owner 契约，删除并处置对应测试。记录选择理由与备选方案。
- [ ] `Fix` 按裁定落地：方案 A 新增模块级校验（错误码 ID 遵循 `nop.err.ai.*` 点号命名约定、英文描述、带路径参数）且 storageDir 内正常 round-trip 保持可用；方案 B 删除两方法及对应测试，全仓 grep 零残留。
- [ ] `Fix` 回归测试（方案 A）：绝对路径（storageDir 外）export/import 被拒、`../` 逃逸被拒、storageDir 内 round-trip 成功；断言异常 `getErrorCode()` 与参数（非仅异常类型）。方案 B：`No new test required`——删除由编译 + 全仓 grep 验证（Minimum Rules #25）。
- [ ] `Proof` 复核 export/import 全部调用点（main/test/xpl），确认无旁路（如其他类直接 `new File` 写 storageDir 外路径）。

Exit Criteria:

- [ ] export/import 对 storageDir 外路径 fail-closed（方案 A，测试为证）或两方法零残留（方案 B，grep 为证）；无静默 sanitize（不重写/截断路径）。
- [ ] **端到端验证**：从 `ThoughtStorage` 公开方法入口经路径解析到文件系统的完整路径覆盖——逃逸输入在入口即被拒，合法路径可完整读写。
- [ ] **接线验证**：校验逻辑在 export/import 运行时被调用（测试经公开方法入口触发，非直调私有）。
- [ ] **无静默跳过**：拒绝路径抛带错误码异常，不返回空/原样资源，无 `catch {}` 吞错。
- [ ] owner doc：`ai-dev/design/nop-ai/03-sequential-thinking-storage.md` 登记 export/import 的最终裁定（保留语义或删除）；否则显式写 `No owner-doc update required`。
- [ ] `./mvnw test -pl nop-ai/nop-ai-tools -am` 通过。
- [ ] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 2 — 文件写原子性与 mkdirs/delete 失败 fail-fast

Status: planned

Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java`、`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/CreateDirectoryExecutor.java`、`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/DeleteFileExecutor.java`、`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java`（saveSession）、对应测试

- Item Types: `Fix | Proof`

- [ ] `Fix` `LocalToolFileSystem.writeText` 改为原子替换语义：先写同目录临时文件、成功后原子 move 覆盖目标（同目录保证 rename 原子性；跨文件系统/不支持 ATOMIC_MOVE 的回退策略显式裁定），任何中间失败不改变目标文件旧内容；`append=true` 保持追加语义不变。
- [ ] `Fix` `ThoughtStorage.saveSession` 使用同等原子写路径（或复用统一 helper），不再直接 `FileHelper.writeText` 截断覆盖。
- [ ] `Fix` `LocalToolFileSystem.mkdirs`/`delete` 检查执行结果：失败抛带错误码的异常（复用 `NopAiToolkitErrors` 或新增码），不再静默忽略返回值。
- [ ] `Fix` `CreateDirectoryExecutor`/`DeleteFileExecutor` 失败路径返回 error 结果（经 fs 抛错传播），不再无条件报 success。
- [ ] `Fix` 回归测试：原子写成功后内容完整；写失败（目标为不可替换形态/可注入的失败路径）后旧内容保留；mkdirs/delete 失败时 executor 返回 error 而非 success；append 模式回归。
- [ ] `Proof` 复核 `writeText`/`mkdirs`/`delete` 全部调用点（含 `WriteFileExecutor`/`PatchFileExecutor`/`ApplyDeltaExecutor`），确认无绕过原子写路径直接写目标文件的旁路。

Exit Criteria:

- [ ] 原子写语义成立：失败场景旧内容保留（测试为证），正常路径内容正确；`append` 语义不变。
- [ ] mkdirs/delete 失败不再报成功（测试为证）；`move`/`copy` 既有失败转异常语义不变。
- [ ] **端到端验证**：从工具入口（write-file/patch-file/apply-delta/create-dir/delete-file executor）经 `IToolFileSystem` 到文件系统的完整路径覆盖。
- [ ] **接线验证**：原子写 helper 与返回值检查被上述运行时调用点消费（非仅新增方法）。
- [ ] **无静默跳过**：新增失败分支显式抛错/返回 error，不吞错、不静默忽略返回值。
- [ ] owner doc：`ai-dev/design/nop-ai-agent/nop-ai-tool-filesystem-design.md` 或 `04-tool-invocation.md` 登记原子写与失败语义；否则显式写 `No owner-doc update required`。
- [ ] `./mvnw test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-tools -am` 通过。
- [ ] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-14-1937-1-p2-file-tool-safety-1-895f6b97 to opencode-pid-23839
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-14-1937-1-p2-file-tool-safety-1-895f6b97

## Verification

（空，由 BUILD_VERIFY 填写）

## Closure

（空，由 CLOSURE_AUDIT 填写）
