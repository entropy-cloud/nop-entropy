# Nop AI Tool Filesystem Design

## 1. Design Rationale

IToolFileSystem (`nop-ai-toolkit`) was introduced as a dedicated filesystem abstraction for AI tools, deliberately separate from Nop platform's IResourceStore. The rationale is documented in `nop-ai-shell-design.md §5.4`: IResourceStore was explicitly rejected for Agent tools because:

1. **Security is first-class, not incidental** — Path validation bubbles up to interface level (`isPathAllowed()`), not buried in implementation
2. **Content-oriented, not resource-oriented** — Tools need `readText()`, `grep()`, `glob()`, not `getResource()` with loading/unloading semantics
3. **Tool Layer separation** — IToolFileSystem belongs to nop-ai-toolkit (Tool Layer), IResourceStore to nop-core (Core Layer). The Architecture Baseline (`01-architecture-baseline.md §三`) enforces that Tool Layer has no upward dependency on Agent Engine Layer

IToolFileSystem is not a "missing integration" with IResourceStore; it is an intentional interface narrowing for a different caller and responsibility.

## 2. Method-Set Analysis

IToolFileSystem defines 16 methods in 6 categories:

### 2.1 Path Normalization & Security (2 methods)

```java
String normalizePath(String path);
boolean isPathAllowed(String path);
```

- `normalizePath`: delegates to `StringHelper.normalizePath()` — resolves `.`/`..`, normalizes separators
- `isPathAllowed`: resolves to canonical path and checks prefix containment within `workDir`

Design decision: security gate is a separate method, not embedded in every file operation. This allows future implementations to implement different authorization strategies (allow/deny lists, pattern matching) without touching file I/O logic.

### 2.2 File Metadata (3 methods)

```java
boolean exists(String path);
boolean isFile(String path);
boolean isDirectory(String path);
```

Minimal metadata — sufficient for tool decision-making (does a file exist? is it a file or directory?) without exposing full filesystem attributes.

Notable omission: no `lastModified()`, `length()`, `canRead()/canWrite()` as standalone methods. These are only available through `listDirectory()`'s returned `FileInfo` objects.

### 2.3 Read Operations (3 methods)

```java
TextResult readText(String path, int maxChars);
LineResult readLines(String path, int fromLine, int toLine, int maxLineLength);
int countLines(String path, int maxLines);
```

Key design decisions:
- **Truncation-aware**: every read operation signals truncation. `readText()` returns `TextResult.truncated`, `readLines()` truncates individual lines, `countLines()` stops at `maxLines`. This is critical for AI token budget control.
- **Line-range reading**: `readLines()` supports fromLine/toLine for partial file reads. `LineResult.totalLines` is set to the requested `toLine` boundary (not the actual file line count); it indicates the upper bound of the requested range.
- **SafeLineReader**: all reads go through `SafeLineReader` for memory safety (buffer limits, line length limits).

### 2.4 Write Operation (1 method)

```java
void writeText(String path, String content, boolean append);
```

Single atomic write operation. No streaming write, no random-access write. Design rationale: AI tools produce complete content (edited file, generated file). The `append` flag supports log-like accumulation.

**Atomic replace semantics (2026-09-14, plan 2026-09-14-1937-1)**: for `append=false`, `LocalToolFileSystem` writes to a temp file in the same directory and renames it over the target (`ATOMIC_MOVE` first, plain `REPLACE_EXISTING` move as the explicit fallback for filesystems without atomic rename support). Any failure before the final move leaves the previous target content untouched — a partial write or crash no longer truncates the old file. `append=true` keeps its in-place append semantics unchanged (appending is inherently in-place). Failure posture matches `move`/`copy`: failures throw instead of silently succeeding.

Notable omission: no `readBinary()/writeBinary()` — IToolFileSystem is text-first. Binary file handling is not a current requirement for LLM tool use.

### 2.5 Directory & File Operations (4 methods)

```java
List<FileInfo> listDirectory(String dirPath, int depth, int maxCount);
void mkdirs(String path);
void delete(String path, boolean recursive, boolean force);
void move(String fromPath, String toPath, boolean overwrite);
void copy(String fromPath, String toPath, boolean recursive, boolean overwrite);
```

All operations carry safety bounds:
- `listDirectory`: depth limit + max count limit
- `delete`: `recursive` for directories, `force` for read-only files
- `move`/`copy`: explicit `overwrite` parameter (no silent overwrite)

### 2.6 Search Operations (2 methods)

```java
List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults);
List<SearchMatch> grep(String pattern, String searchDir, boolean recursive, boolean ignoreCase,
                       int maxMatchesPerFile, int maxFiles, int maxDepth);
```

Both use AntPathMatcher for glob patterns and java.util.regex.Pattern for grep. Both carry explicit depth/max-result bounds.

Notable design choice: grep returns `List<SearchMatch>` (file + line + matched text), not just file list. This is Tool-oriented: the LLM needs matched content, not just file names.

## 3. Result Models

All in package `io.nop.ai.toolkit.fs`:

| Model | Fields | Purpose |
|-------|--------|---------|
| `FileInfo` | path, name, directory, size, lastModified | Directory listing / glob result |
| `TextResult` | path, content, truncated | File read with truncation signal |
| `LineResult` | path, totalLines, fromLine, toLine, lines | Range read with metadata |
| `Line` | lineNumber, content, truncated | Single line with truncation |
| `SearchMatch` | filePath, lineNumber, line, matchedText, truncated | Grep result |

All models are `final` with only getters — pure data carriers. `FileInfo` and `SearchMatch` have `toFormattedString()` for LLM-readable output; `LineResult` has `toLineNumberedContent()`.

## 4. Security Model

### 4.1 Current Implementation (LocalToolFileSystem)

```
isPathAllowed(path):
  1. resolve file relative to workDir
  2. get canonical path of resolved file
  3. get canonical path of workDir
  4. return pathStartsWith(canonicalPath, canonicalWorkDir)
```

This is a "prefix containment" model: any file under `workDir` (including subdirectories) is allowed. Symbolic links are resolved by `getCanonicalPath()`, so symlink-based escapes are prevented.

### 4.2 Enforcement (Current)

`isPathAllowed()` is enforced at the filesystem layer: every file operation goes through `resolveFile()`, which calls `isPathAllowed()` and throws `NopAiException("Path not allowed: ...")` for any path outside `workDir` (absolute-path bypass and `..` escapes are rejected; canonical resolution also defeats symlink escapes).

```java
private File resolveFile(String path) {
    if (!isPathAllowed(path)) {
        throw new NopAiException("Path not allowed: " + path);
    }
    return resolveFileInternal(path);
}
```

This closes the gap described in earlier revisions of this section (file operations relying on the caller checking `isPathAllowed()` first). The dispatch layer (`ReActAgentExecutor.checkPathAccess()`) additionally provides `IPathAccessChecker.checkAccess()` on tool-call arguments matching `ToolPathArgKeys.KEYS` — a two-layer defense: dispatch-layer argument screening + filesystem-layer path containment.

### 4.3 Future Security Enhancements

Design direction from `nop-ai-shell-design.md §5.4` and `nop-ai-agent-security-and-permissions.md`:
- Per-path pattern-based allow/deny lists (using AntPathMatcher)
- Staged through configuration (not baked into interface)
- Layer 1 (deny/allow) → Layer 2 (content screening) → Layer 3 (approval flow) progression

## 5. IResourceStore vs IToolFileSystem

| Dimension | IResourceStore | IToolFileSystem |
|-----------|---------------|-----------------|
| **Package** | nop-core (io.nop.core.resource) | nop-ai-toolkit (io.nop.ai.toolkit.fs) |
| **Layer** | Core Layer | Tool Layer |
| **Caller** | Platform internals | AI Tool implementations |
| **Orientation** | Resource management (load/unload/store) | File content operations |
| **Security** | Access control by module/grant | Path containment + pattern allow/deny |
| **Read pattern** | getResource() → IResource | readText/maxChars, readLines/range |
| **Search** | Built-in: find, findAllByAntPath, getAllResources (resource-oriented, returns IResource) | glob + grep with depth/max bounds (content-oriented, returns match details) |
| **Write** | Not primary concern | writeText + mkdirs + delete + move + copy |
| **Binary** | Yes (InputStream) | No (text-only) |
| **Delta** | Delta-based resource overlay | No layering yet (planned) |

They are complementary, not substitutes. An AI tool should never need IResourceStore; it should use IToolFileSystem. The platform's VFS layer (classpath, module resources, delta overlays) remains accessible through normal Nop APIs but is intentionally hidden from tool file operations.

## 6. VFS vs ToolFileSystem: Dual-Storage Model

The Nop AI Agent operates two distinct storage abstractions:

| | VFS (`.nop/`) | IToolFileSystem |
|---|---|---|
| **Purpose** | Agent-scope state: session, plan, checkpoint, message history | Tool work files: source code, data files, output artifacts |
| **Access scope** | Agent engine only (nop-ai-agent) | Tool implementations only (nop-ai-toolkit) |
| **Persistence** | Snapshot + compression + tree retention | Direct file I/O, no snapshot |
| **Security** | Module-level permissions | Path containment + pattern allow/deny |
| **Layering** | Delta overlay (classpath + user `.nop/`) | None yet (flat work dir) |
| **Documented in** | `nop-ai-agent-session-and-storage.md §4` | This document |

They share physical filesystem space but serve different logical concerns. A tool should never read/write VFS-managed files (`.nop/` directory) via IToolFileSystem — those are for the engine only.

## 7. Implementation Status

**Sole implementation**: `LocalToolFileSystem` (351 lines)

| Aspect | Status |
|--------|--------|
| Constructor | `LocalToolFileSystem(File workDir)` — single root directory |
| WorkDir resolution | `resolveFile()`: absolute → as-is, `/`-prefix → strip and join, relative → join |
| Path validation | `isPathAllowed()` via canonical prefix check |
| Truncation | All read paths use `SafeLineReader` |
| Error handling | `NopAiException` for path/precondition failures, `NopException` + `NopAiToolkitErrors` code for `mkdirs`/`delete` failures, `NopException.adapt()` for IO errors |

**No other implementations exist**:
- No `VfsToolFileSystem` (wrapping IResourceStore for VFS access)
- No `LayeredToolFileSystem` (for multiple overlay directories)
- No `RemoteToolFileSystem` (for SSH/NFS/docker volumes)
- No `CompositeToolFileSystem` (for multi-root workspaces)

## 8. Known Gaps and Future Directions

### 8.1 Security Enforcement (Closed)
`isPathAllowed()` is enforced inside `resolveFile()`, which every file operation uses. Absolute-path bypass and `..` escapes are rejected with `NopAiException`; canonical resolution defeats symlink escapes. See §4.2.

### 8.2 LayeredToolFileSystem (P1)
AI tools often need to read from multiple directories (project source, libraries, reference data) while writing only to a specific output directory. A `LayeredToolFileSystem` should overlay multiple directories, with read-through semantics and write-to-primary.

```
interface: IToolFileSystem
  ├─ LayerToolFileSystem (read from N dirs, write to primary)
  ├─ LocalToolFileSystem (single work dir)  ← current
  └─ ... future: VfsToolFileSystem, RemoteToolFileSystem, etc.
```

### 8.3 Pattern-Based Path Authorization (P1)
Current canonical-prefix containment is coarse. Tools should be configurable with AntPathMatcher-based allow/deny patterns, as outlined in `nop-ai-agent-security-and-permissions.md`.

### 8.4 VFS-Backed Implementation (P2)
An implementation that wraps IResourceStore would allow tools to access classpath resources and delta-overlay content. This must be carefully scoped to avoid exposing internal VFS paths.

### 8.5 Binary File Support (P2)
Currently text-only. Binary files (images, compiled artifacts) would require `readBinary()/writeBinary()` additions.

### 8.6 Multi-Root Workspaces (P2)
AI tools in large projects need access to multiple independent directories. A `CompositeToolFileSystem` could combine multiple roots with union semantics.

### 8.7 BashExecutor Filesystem Bypass (P0)
`BashExecutor` in `nop-ai-toolkit` does not use `IToolFileSystem` at all — it operates directly on the filesystem via `ProcessBuilder`. This means all path security measures (`isPathAllowed()`, `IPathAccessChecker`, workDir containment) are bypassed when a tool invokes a shell command. The shell inherits the JVM process's filesystem access.

The planned mitigation is `nop-ai-shell` (see `nop-ai-shell-design.md`): a virtual in-process shell that parses bash AST, executes commands via a whitelisted `ShellCommandRegistry` (currently echo/cd/ls), and uses `IToolFileSystem` internally for all file operations. The bridge class `ShellBashExecutor` (wrapping `ShellCommandExecutor` as an `IToolExecutor`) is designed but not yet implemented. Once wired via IoC Delta, it can replace `BashExecutor` at the bean level without agent-engine changes.

Short-term mitigation options:
- Enforce `cd workDir` in every shell command preamble (partially mitigates, does not prevent `../` escapes)
- Pre-process command arguments to detect/reject absolute paths outside workDir
- Run shell commands in a container/sandbox (Layer 4 scope)

### 8.8 Thread Safety (P2)
`LocalToolFileSystem` uses shared mutable state (`AntPathMatcher antMatcher` field) and delegates to java.io.File operations that are not thread-safe. Concurrent tool calls may interfere during glob/grep operations. Each method call should use a local `AntPathMatcher` instance instead of the shared field.

### 8.9 Concurrent Write Safety (Improved 2026-09-14)
`writeText` with `append=false` is now an atomic replace (same-directory temp file + rename, see §2.4/§9.1): concurrent writes to the same file can no longer interleave into corrupted content — each write lands as a complete file and the last rename wins. `append=true` remains in-place and is still not safe under concurrent writers. For AI tool workloads (single-agent, sequential tool calls) this is sufficient; multi-agent parallel appends would need coordination (file locking, write coordination via the multi-agent layer).

### 8.10 Encoding Assumption
`LocalToolFileSystem` hardcodes `StandardCharsets.UTF_8` for all read/write operations. This is sufficient for most AI tool workloads but should be documented as a constraint.

### 8.11 The `IToolExecuteContext` Evolution

`IToolFileSystem` is accessed through `IToolExecuteContext.getFileSystem()`. The context hierarchy has evolved organically without a formal interface contract between the toolkit base and the agent extension.

#### 8.11.1 Hierarchy (No Interface Extension)

There is no `IAgentToolExecuteContext` interface. The hierarchy is flat — all implementations directly implement `IToolExecuteContext`:

```
IToolExecuteContext (interface, nop-ai-toolkit)
  ├── ToolExecuteContext (class, nop-ai-toolkit)         — 6 base methods + Builder
  ├── SimpleToolExecuteContext (class, nop-ai-agent)      — 6 base methods only
  └── AgentToolExecuteContext (class, nop-ai-agent)       — 6 base + 12 extra fields
```

#### 8.11.2 Three Layers at Two Module Boundaries

| Layer | Module | Class | Methods | Callers |
|-------|--------|-------|---------|---------|
| Base (Toolkit) | nop-ai-toolkit | `IToolExecuteContext` | getWorkDir, getEnvs, getExpireAt, getCancelToken, getFileSystem, getExecutor | All tool executors in nop-ai-toolkit (18 tools) |
| Simple (Agent test) | nop-ai-agent | `SimpleToolExecuteContext` | Same 6 — no extra | Test setups in nop-ai-agent |
| Extended (Agent) | nop-ai-agent | `AgentToolExecuteContext` | + getEngine, getMessenger, getSessionId, getAgentName, getAllowedTools, getAllowedPathRoots, getAllowedPathRules, getMemoryStore, getTeamManager, getTeamTaskStore, getTeamAclChecker, getDelegationDepth/getSession (+ setters) | Engine-aware tools in nop-ai-agent (9 executors) |

#### 8.11.3 Constructor Evolution

The constructors grew from 6 to 17 parameters as new capabilities were added, each adding a new domain concept:

| Parameters | Added Field | Capability |
|-----------|-------------|------------|
| 6 | base fields | Tool-layer file/dir/env/fs/executor |
| 10 | engine, messenger, sessionId, agentName | Agent-scope: call-agent, send-message |
| 11 | allowedTools | Tool permission propagation (§4.4) |
| 12 | allowedPathRoots | Path confinement propagation |
| 13 | allowedPathRules | Path-rule chain propagation |
| 14 | memoryStore | Working memory (alone, before team fields) |
| 16 | memoryStore, teamManager, teamTaskStore | Working memory + team operations |
| 17 | teamAclChecker | Team ACL enforcement (Plan 228) |

Two mutable fields via setters (not constructor):
- `delegationDepth` — set by ReActAgentExecutor before dispatch loop (Plan 278)
- `session` — set by dispatch loop from sessionStore (Plan 296 WS2)

#### 8.11.4 The `instanceof` Pattern (Fragile Contract)

9 tool executors in `nop-ai-agent` access extended fields via `instanceof AgentToolExecuteContext`:

```
AbstractMemoryToolExecutor.resolveStore()     → context instanceof AgentToolExecuteContext
CallAgentExecutor.execute()                   → same pattern
SendMessageExecutor.execute()                 → same
SetActiveTagsExecutor.execute()               → same
TeamExecuteFlowExecutor.execute()             → same
TeamSendMessageExecutor.execute()             → same
TeamStatusExecutor.execute()                  → same
TeamTaskCreateExecutor.execute()              → same
TeamTaskUpdateExecutor.execute()              → same
```

The pattern is consistent: check `instanceof`, fail-fast with descriptive error if false, cast, access extended field, null-check the field, fail-fast if absent. No tool silently no-ops.

#### 8.11.5 Design Gap

- **No intermediate interface**: Tools that need engine/messenger access cannot declare their dependency via a type. `instanceof` is fragile and invisible to static analysis.
- **No capability marker**: A tool cannot declare "I need `IAgentMessenger`" at type level. The contract is implicit (check instanceof at runtime).
- **Constructor explosion**: 7 constructors for one class, with telescoping parameter lists.
- **Two modules, one class**: `AgentToolExecuteContext` is the only class crossing the toolkit↔agent boundary — it lives in nop-ai-agent but implements a nop-ai-toolkit interface, carrying engine types.

A future improvement would introduce `IAgentToolExecuteContext extends IToolExecuteContext` to declare the extended contract explicitly, allowing tools to declare typed dependencies and eliminating the `instanceof` pattern.

## 9. Design Rulings (2026-09-14, plan 2026-09-14-1937-1)

### 9.1 Atomic Replace for `writeText` (append=false)

**Ruling**: `writeText(..., append=false)` uses atomic replace — write a temp file in the same directory, then rename over the target. `ATOMIC_MOVE` is attempted first; plain `Files.move` with `REPLACE_EXISTING` is the explicit fallback for filesystems that do not support atomic rename. The temp file is always in the target's directory, so a cross-filesystem move cannot occur and the fallback is only about filesystem atomic-rename support, not cross-device semantics. Any failure before the final move leaves the previous target content untouched. `append=true` keeps its in-place append semantics (appending is inherently in-place; atomicity is not defined for it).

**Why**: previously all file-mutating tools (`write-file`, `patch-file`, `apply-delta`, `ThoughtStorage.saveSession`) truncated the target in place; a partial write / crash / ENOSPC destroyed the old content, while `move`/`copy` on the same abstraction failed loudly. The new semantics make the failure posture consistent within the abstraction and give every tool the same crash safety without interface changes.

**Rejected alternatives**: (a) `writeTextWithLock`-style lock file — adds a lock artifact and still truncates on crash; (b) always-copy-to-`.bak` — leaves residue and does not give a single atomic switch point; (c) `fsync` before/after rename — durability hardening orthogonal to atomicity, deferred (see 9.3).

### 9.2 Fail-Fast `mkdirs` / `delete`

**Ruling**: `mkdirs` and `delete` inspect the operation result and throw `NopException` with `NopAiToolkitErrors.ERR_AI_TOOLKIT_INVALID_STATE` (`nop.err.ai.toolkit.invalid-state`, `{detail}` param, English detail carrying the failing path) when the operation fails while the target still exists. `delete` of a non-existent path remains a no-op; `mkdirs` of an existing directory remains a no-op.

**Why**: previously `dir.mkdirs()` and `file.delete()` return values were ignored, so `create-dir`/`delete-file` executors reported "created successfully"/"deleted successfully" on failure. Their existing `try/catch` → `errorResult` boundary now receives the failure and reports an error result.

### 9.3 Deferred (Non-Blocking)

- `fsync`/directory-sync durability before rename: not required for atomicity (no torn content is observable), only for power-loss durability; nop-ai tools are conversation-scoped working files. Optimization candidate.
- Concurrent `append=true` writers remain uncoordinated (single-JVM tool semantics, see §8.9). Watch-only residual.
- `LocalToolFileSystem` shared `AntPathMatcher` / thread safety (§8.8) unchanged — out of this plan's scope.
