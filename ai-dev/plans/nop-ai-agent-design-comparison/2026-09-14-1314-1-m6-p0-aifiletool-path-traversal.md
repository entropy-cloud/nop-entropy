---
status: active
mission: nop-ai-agent-design-comparison
work-item: M6-P0
group: "2026-09-14-1314"
verify: [test]
---

# M6-P0 AiFileTool 任意文件读写修复（nop-ai-mcp-server 沙箱逃逸）

## Current Baseline

- 审计发现（deep-audit round 2，M6-P0）：`AiFileTool`（nop-ai-mcp-server）存在任意文件读写。`getResource`（AiFileTool.java:163-179）用 `new File(baseDir, path)` 拼接后仅做 `StringHelper.normalizePath` 的路径标注，**无任何 baseDir 包含性校验**：
  - 绝对路径（`loadNopFile("/etc/passwd")`）完全绕过 baseDir（Java `new File(baseDir, absPath)` 直接解析为绝对路径）；
  - `../` 段可直接逃逸 baseDir。
- 影响面：`loadNopFile`（:68，`@Auth(permissions = "AiFileTool:read")`）与 `saveNopFile`（:118，`AiFileTool:write`）均经 `getResource` 解析目标资源 → 读写双侧受影响；MCP 工具权限恰好是授予 LLM 的工具权限，prompt-injection 暴露面下即任意主机文件读取/覆盖。
- baseDir 来源：`@InjectValue("@cfg:ai.mcp.base-dir|.")`（:49-52），默认 `.`（进程工作目录），沙箱甚至不锚定在专用目录。
- 现有测试：`TestAiFileTool.java` 仅 3 例（xdef 抛错、merge 不支持抛错、merge 成功写文件），无路径逃逸用例。
- 错误码容器：`McpServerErrors`（ERR_MCP_FILE_NOT_FOUND 等 3 个码），无路径逃逸专用错误码。
- 对侧参照：nop-ai-tools 的 `AiToolsHelper.requireValidSessionId`（utils/AiToolsHelper.java）对同类输入 fail-closed（正则白名单）；nop-ai-core `LocalFileOperator` 沙箱模式 fail-closed——本缺陷是同一抽象家族里的安全姿态缺口。

## Goals

- `AiFileTool` 的资源解析改为 fail-closed：解析后的真实路径必须包含在 baseDir 内，绝对路径与 `..` 逃逸一律拒绝并抛带专用错误码的异常。
- 新增回归测试：绝对路径读/写被拒、`../` 逃逸被拒、沙箱内正常读/写不受影响；错误码/参数契约被断言。
- 若修改 `@cfg:ai.mcp.base-dir` 语义说明或错误码表，同步 owner doc。

## Non-Goals

- 不改 `loadNopFile`/`saveNopFile` 的业务语义（转换、过滤、merge 行为不变），只收口资源解析的路径包含性。
- 不处理 nop-ai-tools 侧 `FileToolBizModel.getProjectDir` 的同类问题（那是 M6-P1 另一个工作项，单独计划）。
- 不新增 MCP 协议层改动（`McpConstants` 等不动）。
- 不运行 mvn 全量构建；仅对受影响模块 nop-ai-mcp-server 做 `./mvnw test -pl nop-ai/nop-ai-mcp-server -am` 验证。

## Phase 1 — getResource 路径包含性校验（fail-closed）

Targets: `nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java`、`McpServerErrors.java`、`TestAiFileTool.java`

- Item Types: `Fix | Proof`

- [x] `Fix` 在 `getResource` 中改为：解析 `new File(baseDir, path)` 后取 canonical path（`File.getCanonicalFile()`），校验其以 baseDir 的 canonical path 为前缀（或 equals）；对解析异常（IO 异常/不存在的规范路径）与越界结果统一 fail-closed 抛 `NopException`，新增错误码（如 `ERR_MCP_PATH_ESCAPE`，ID 遵循本文件现有 `nop.err.mcp.*` 点号命名约定、英文描述）并带 `ARG_PATH` 参数。
- [x] `Fix` 保持现有正常语义：沙箱内的相对路径读/写、`saveNopFile` 新建文件（canonical 解析前 `file.exists()` 为 false 的路径须先 resolve 父目录再校验，避免新建路径被误判不存在）仍可用；`VirtualFileSystem` 回退分支（`path.startsWith("/")` 的 VFS 资源）语义不变或按现有契约保留（仅对 baseDir 落盘路径做包含性校验）。
- [x] `Proof` 复核 `loadNopFile`/`saveNopFile` 全部调用 `getResource` 的入口，确认无旁路（如直接 `new File` 或 `ResourceHelper.writeText` 直写 baseDir 外路径）。
- [x] `Fix` 为新增错误码在 `TestAiFileTool.java` 添加用例：绝对路径（`/etc/passwd` 形态）读/写被拒、`../` 逃逸（`../../etc/passwd` 形态与 `foo/../../bar` 形态）被拒、沙箱内相对路径读/写成功；断言异常 `getErrorCode()` 为新增错误码且 `ARG_PATH` 参数存在。
- [x] `Fix` 若 baseDir 默认值 `.` 的语义在文档中引发歧义（相对 CWD 的沙箱定位），在相关 owner doc 或 javadoc 补一行明确说明；否则显式写 `No owner-doc update required`。已处置：`setBaseDir` javadoc 补一行（默认 `.` 相对进程 CWD 的沙箱定位）；docs-for-ai 无受影响错误码表/配置文档。

Exit Criteria:

- [x] `getResource` 对绝对路径与 `..` 逃逸 fail-closed：带 `ARG_PATH` 的专用错误码异常，且日志无静默跳过路径。
- [x] 新增回归测试全绿：`./mvnw test -pl nop-ai/nop-ai-mcp-server -am` 通过（15 例，退出码 0），且测试断言 `getErrorCode()`（不只断言异常类型）。
- [x] **端到端验证**：从 MCP 工具入口（`loadNopFile`/`saveNopFile`）经 `getResource` 到文件系统的完整路径已覆盖——逃逸输入在入口即被拒，沙箱内输入可完整读写。
- [x] **接线验证**：`getResource` 的校验逻辑确实被 `loadNopFile`/`saveNopFile` 运行时调用（测试经由工具入口触发，而非直接调 `getResource`）。
- [x] **无静默跳过**：逃逸路径不返回空/原样资源，一律抛异常；无 `catch {}` 吞错。
- [x] owner doc 同步：若错误码表/`ai.mcp.base-dir` 语义文档存在且受影响则更新，否则写 `No owner-doc update required`。已处置：`setBaseDir` javadoc 补充默认 `.` 相对 CWD 语义；docs-for-ai 无受影响错误码表（`service-layer.md` 仅登记权限字符串，未变）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Draft Review Record

- dispatch review #review-2026-09-14-110620-2026-09-14-1314-1-m6-p0-aifiletool-path-traversal-1-41b8b18a to opencode-pid-31126
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-2026-09-14-1314-1-m6-p0-aifiletool-path-traversal-1-41b8b18a

## Verification

（空，由 BUILD_VERIFY 填写）
- pass test 2026-09-14-110620-mission-driver exit=0

## Closure

（空，由 CLOSURE_AUDIT 填写）
- dispatch audit #audit-2026-09-14-110620-mission-driver-2026-09-14-1314-1-m6-p0-aifiletool-path-traversal-1-a6739c2e to opencode-go/deepseek-v4-flash models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-mission-driver-2026-09-14-1314-1-m6-p0-aifiletool-path-traversal-1-a6739c2e：独立 closure audit 通过——AiFileTool 沙箱逃逸已 fail-closed 收口：`getResource`（AiFileTool.java:206-226）对 `/` 前缀路径仅放行 VFS 命中资源、落盘路径 canonical 后必须位于 baseDir canonical 内（`ensureWithinBaseDir`:172/`ensureNewFileWithinBaseDir`:187，新建文件先 canonical 父目录再拼名），绝对路径与 `..` 逃逸一律抛 `ERR_MCP_PATH_ESCAPE`（McpServerErrors.java 新增 `nop.err.mcp.path-escape`，英文描述 + ARG_PATH），IO 失败同 fail-closed；无旁路（loadNopFile:75/saveNopFile:126 单一入口经 getResource，类内无直接 new File 写）；接线验证：测试全部经 MCP 工具入口触发而非直调 getResource；`./mvnw test -pl nop-ai/nop-ai-mcp-server -am` 本 visit 实测 exit=0（TestAiFileTool 15 例 0 失败，断言 getErrorCode() + ARG_PATH）；`node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，9 warnings 均为其它计划既有存量）；Anti-Hollow 通过（无空壳/静默跳过/吞错）；docs-for-ai 无受影响错误码表（grep 零命中），setBaseDir javadoc 已补默认 `.` 语义；roadmap M6 P0 已勾选、ai-dev/logs/2026/09-14.md 已记录；无 in-scope defect 被降级；plan-check.mjs --strict exit=0，ledger 公式（all-checked ∧ pass:test ∧ audit-receipt）满足，frontmatter `status: active` 保持不变