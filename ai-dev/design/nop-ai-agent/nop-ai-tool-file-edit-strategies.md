# Nop AI Tool 文件编辑策略设计

**日期**：2026-09-14（更新于 2026-09-20）
**范围**：nop-ai-toolkit（IToolFileSystem、文件编辑工具）、nop-ai-agent（工具配置、prompt 组装）
**状态**：active
**灵感来源**：OpenAI Codex (`apply_patch`)、Pi Agent (`edit`)、DeepSeek Harness (`edit` / `write`)

---

## 一、设计结论

1. Nop 平台提供 **4 种独立的文件编辑工具**，各自保留原始系统的设计和算法，作为可插拔策略供 Agent 配置选择。
2. 每种编辑工具是独立的 `IToolExecutor` 实现，互不依赖、互不融合，由 Agent 配置的 `toolNames` 列表决定实际暴露给模型的工具集。
3. 不同 Agent 可根据模型能力、任务场景选择不同策略组合，同一 Agent 不同 session 也可切换策略。

---

## 二、背景与动机

当前 Nop AI Agent 的文件编辑能力仅依赖 `IToolFileSystem.writeText()`（全量覆盖）。模型只能通过全量写入来修改文件，缺少精确编辑能力。

调研了三个主流 AI 编程助手的文件编辑机制（源码级），提炼为 4 种独立策略实现。每种策略保留其原始设计的完整行为，不做算法融合。

### 策略来源映射

| 策略 | 来源 Agent | 源码仓库 | 核心文件 |
|------|-----------|---------|---------|
| `write` | 所有 Agent 的通用能力 | — | — |
| `line-patch` | OpenAI Codex | openai/codex | codex-rs/apply-patch/src/ (seek_sequence.rs 匹配算法, parser.rs 格式解析, file_update.rs 文件更新) |
| `fuzzy-replace` | Pi Agent | earendil-works/pi | packages/coding-agent/src/core/tools/edit.ts (工具定义) + edit-diff.ts (匹配算法 fuzzyFindText) |
| `exact-replace` | DeepSeek Harness | deepseek-ai/deepseek-harness | packages/fs/fs-local/src/fsio.ts (applyLiteralEdit 匹配算法) + packages/fs/tool-fs/src/edit.ts (工具注册) |

---

## 三、四种独立编辑策略

### 策略 1：`write` — 全量写入（已有）

**来源**：所有系统的通用能力。

- **行为**：模型提供完整文件内容，系统直接覆盖写入（`append=false` 时原子替换）
- **参数**：`file_path`, `content`
- **匹配算法**：无
- **适用场景**：新建文件、文件整体重写、小文件编辑
- **实现**：已有 `IToolFileSystem.writeText()`

#### Prompt 指引

```
Use `write` to create new files or completely overwrite existing files. For
targeted changes to specific parts of an existing file, use an edit tool
instead — write replaces the entire file content.
```

---

### 策略 2：`line-patch` — 行级 Patch 编辑

**来源**：OpenAI Codex (`codex-rs/apply-patch/`)

#### Patch 格式

```
*** Begin Patch
*** Add File: new-file.txt
+line content
*** Delete File: old-file.txt
*** Update File: src/foo.py
*** Move to: src/bar.py
@@ def main():
 import os
-old_value = "hello"
+new_value = "world"
@@
+new_line_at_eof
*** End of File
*** End Patch
```

#### 操作类型

- `*** Add File:` — 创建新文件，`+` 前缀行组成文件内容
- `*** Delete File:` — 删除文件
- `*** Update File:` — 修改文件，可包含多个 chunk
- `*** Move to:`（可选）— 在 Update File 内重命名/移动文件

#### Chunk 语法

- `@@` 或 `@@ context_line` — chunk 分隔符，可选上下文锚点行（如函数签名）
- ` `（空格前缀）— 上下文行（不修改，用于定位）
- `-` 前缀 — 删除行
- `+` 前缀 — 添加行
- `*** End of File` — 标记在文件末尾插入

#### 匹配算法（4 级递进模糊匹配）

定位 Update File 中的 chunk 时，先通过 `@@` 上下文锚点缩小搜索范围，再在锚点之后匹配 `old_lines`。匹配采用 4 级递进策略，逐级放宽：

```
seek_sequence(lines, pattern, start, eof):
  // 1. 精确行匹配
  for i in start..end:
    if lines[i..i+len] == pattern: return i

  // 2. 忽略行尾空白
  for i in start..end:
    if all(lines[i+j].trimEnd() == pattern[j].trimEnd()): return i

  // 3. 忽略行首+行尾空白
  for i in start..end:
    if all(lines[i+j].trim() == pattern[j].trim()): return i

  // 4. Unicode 标准化匹配
  for i in start..end:
    if all(normalise(lines[i+j]) == normalise(pattern[j])): return i
  // normalise: trim + Unicode 映射（弯引号→直引号、en-dash→连字符、特殊空格→空格）

  return not_found
```

#### 关键设计点

- **EOF 优先**：当 chunk 标记 `*** End of File` 时，先从文件末尾开始搜索，匹配失败再回退到 `start`
- **空尾行容错**：`old_lines` 末尾为空字符串时，若匹配失败则去掉末尾空行重试
- **多 chunk 顺序匹配**：多个 chunk 按顺序匹配，`line_index` 追踪当前位置。后续 chunk 的 `old_lines` 在前一个 chunk 已应用的修改之上匹配，因此 chunk 之间不应修改重叠区域
- **行尾模式**：支持 `NormalizeToLf`（统一 LF）和 `PreserveLineEndings`（保留原始 CRLF/LF）

#### 参数

```json
{
  "patch": "*** Begin Patch\n*** Update File: src/foo.py\n@@\n-old\n+new\n*** End Patch"
}
```

#### Prompt 指引

```
The `line-patch` tool edits files using a custom patch format. The patch must
start with "*** Begin Patch" and end with "*** End Patch". Use "*** Add File:"
to create files, "*** Delete File:" to remove them, and "*** Update File:" to
modify existing files. For updates, use "@@" to mark chunk boundaries with
optional context lines. Lines prefixed with "-" are removed, "+" are added,
and " " (space) are unchanged context lines.
```

---

### 策略 3：`fuzzy-replace` — 模糊文本替换

**来源**：Pi Agent (`edit` tool)

#### 行为

模型提供一个或多个 `{oldText, newText}` 替换对，系统在文件中定位并替换。所有 edits 对原始文件并行匹配（非增量）。

#### 匹配算法（2 级字符串级匹配）

```
fuzzyFindText(content, oldText):
  // 1. 精确子串匹配
  index = content.indexOf(oldText)
  if index >= 0: return { found: true, index, fuzzy: false }

  // 2. Unicode 标准化后匹配
  fuzzyContent = normalizeForFuzzyMatch(content)
  fuzzyOldText = normalizeForFuzzyMatch(oldText)
  index = fuzzyContent.indexOf(fuzzyOldText)
  if index >= 0: return { found: true, index, fuzzy: true }

  return { found: false }

// normalizeForFuzzyMatch:
//   1. NFKC Unicode 标准化
//   2. 逐行 trimEnd
//   3. 弯引号 → 直引号
//   4. 各种 dash/hyphen → ASCII '-'
//   5. 特殊空格 → 普通空格
```

当使用 fuzzy 匹配时，替换在标准化空间中执行，但只改写被替换的行块，未修改的行保留原始字节（`applyReplacementsPreservingUnchangedLines`）。

#### 约束

- `oldText` 必须非空
- `oldText` 必须在文件中**唯一**存在（否则报错提示增加上下文）
- 多个 edits **不可重叠**（按位置排序后检测）
- 所有 edits 匹配完成后，**从后往前**依次应用替换（避免偏移）

#### 参数

```json
{
  "path": "src/foo.py",
  "edits": [
    { "oldText": "old_value", "newText": "new_value" },
    { "oldText": "another_change", "newText": "replaced" }
  ]
}
```

#### Prompt 指引

```
Use `fuzzy-replace` for precise changes to existing files. Each edit specifies
oldText (exact text to find) and newText (replacement). oldText must match
exactly and be unique in the file. When changing multiple locations, use one
edit call with multiple entries. Keep oldText as small as possible while still
being unique.
```

---

### 策略 4：`exact-replace` — 精确文本替换

**来源**：DeepSeek Harness (`fs-local` 模块)

#### 行为

模型提供 `old_string` 和 `new_string`，系统执行精确字符串替换。与 `fuzzy-replace` 的核心差异：**无模糊匹配、单次替换或全量替换、CAS 版本锁**。

#### 匹配算法（纯精确字符串匹配）

```
applyLiteralEdit(content, oldString, newString, replaceAll):
  oldNorm = normalizeLineEndings(oldString)   // CRLF → LF
  newNorm = normalizeLineEndings(newString)   // CRLF → LF
  if oldNorm.length == 0: throw FS_EDIT_NOT_FOUND
  replacements = countOccurrences(content, oldNorm)  // indexOf 循环计数
  if replacements == 0: throw FS_EDIT_NOT_FOUND
  if !replaceAll && replacements > 1: throw FS_AMBIGUOUS_EDIT
  return content.split(oldNorm).join(newNorm)
```

**无任何模糊容错**。匹配到就是对了，匹配不到就报错。

#### 并发保护

- **文件级互斥锁**：`withLock(targetKey, ...)` — 同一文件的编辑操作串行执行
- **CAS 版本检查**：`expected.version` — 编辑前读取文件版本，写入前校验版本未变；若文件在 read-modify-write 期间被修改，抛出 `FS_STALE_VERSION`

```
editText(target, edit, expectedVersion):
  withLock(target.targetKey):
    existing = probe(target.targetKey)
    if expectedVersion && existing.version != expectedVersion.version:
      throw FS_STALE_VERSION
    original = readForEdit(target)
    edited = applyLiteralEdit(original.content, ...)
    content = restoreLineEndings(edited, original.lineEndings)
    writeFileAtomic(target.targetKey, content, ...)
```

#### 约束

- `old_string` 必须非空
- `old_string` 与 `new_string` 不能相同
- 默认要求 `old_string` 唯一（否则报错，可设 `replace_all=true` 替换所有匹配）

#### 参数

```json
{
  "file_path": "src/foo.py",
  "old_string": "old_value",
  "new_string": "new_value",
  "replace_all": false
}
```

#### Prompt 指引

```
Use `exact-replace` to replace literal text in an existing UTF-8 text file.
old_string must match exactly including all whitespace and newlines. By default,
old_string must appear exactly once. If it appears multiple times, provide a
more specific old_string or set replace_all to true. Read the file first unless
you just created or edited it in this session.
```

---

## 四、策略对比

| 维度 | write | line-patch | fuzzy-replace | exact-replace |
|------|-------|------------|---------------|---------------|
| **匹配单位** | 无 | 行数组 | 字符串片段 | 字符串片段 |
| **模糊容错** | 无 | 4 级（精确→rstrip→trim→Unicode） | 2 级（精确→NFKC+Unicode） | 无 |
| **多处替换** | 全量 | 多 chunk 按序 | 多 edits 并行（不可重叠） | 单次或 replace_all |
| **上下文锚点** | 无 | `@@` context line | 无 | 无 |
| **文件操作** | 创建/覆盖 | Add/Delete/Update/Move | Edit | Edit |
| **唯一性要求** | 无 | 无 | oldText 必须唯一 | old_string 必须唯一 |
| **并发保护** | 文件系统原子替换 | 文件系统原子替换 | 互斥队列 | CAS 版本锁 |
| **Token 效率** | 低（全量内容） | 中（patch 格式） | 高（仅 old/new 片段） | 高（仅 old/new 片段） |
| **模型学习成本** | 低 | 高（自定义语法） | 低（JSON 参数） | 低（JSON 参数） |

---

## 五、配置模型

### 5.1 Agent 配置

```yaml
nop.ai.agent:
  # 编辑策略选择：列出的工具会注册到该 Agent 的工具集中
  toolNames:
    - plain-read  # 或 numbered-read / shell-read（见文件读取策略设计）
    - write            # 策略 1：全量写入（基础能力，通常始终启用）
    - line-patch       # 策略 2：行级 patch（可选）
    - fuzzy-replace    # 策略 3：模糊文本替换（可选）
    - exact-replace    # 策略 4：精确文本替换（可选）
    - bash
```

### 5.2 预设配置

| 预设 | 工具组合 | 适用场景 |
|------|---------|---------|
| `minimal` | write | 简单文件生成、纯创建场景（无精确编辑需求） |
| `default` | write, fuzzy-replace | 日常开发、大多数场景 |
| `strict` | write, exact-replace | 对替换精度要求高、需要并发安全（无模糊容错） |
| `full` | write, line-patch, fuzzy-replace, exact-replace | 复杂重构、多策略对比 |

### 5.3 Prompt 组装

每个策略实现通过 `getSystemPromptContribution()` 返回自己的 prompt 指引。未启用的策略不返回内容，不占 prompt 空间。系统将启用的策略指引拼接到 system prompt 的工具使用区域。

同一 session 中启用多个编辑策略时，系统 prompt 需明确指引模型在什么情况下使用哪个策略。例如 `full` 预设的指引：

```
## File Editing
You have multiple file editing tools available. Choose based on the task:
- `line-patch`: for complex multi-file or multi-location edits with context anchors
- `fuzzy-replace`: for precise single-file replacements with Unicode tolerance
- `exact-replace`: for exact text replacement with version safety
- `write`: for new files or complete rewrites
```

---

## 六、拒绝了什么

### 6.1 拒绝：算法融合

**方案**：将三种匹配算法合并为一个统一的匹配引擎，根据参数自动选择模糊级别。

**拒绝理由**：三种策略的匹配算法差异是设计意图的体现——`line-patch` 的 4 级匹配依赖 patch 格式的上下文锚点才能安全工作；`fuzzy-replace` 的 2 级匹配是在唯一性约束下的最小容错；`exact-replace` 的无容错是刻意追求可预测性。融合后无法保留各自的语义保证。

### 6.2 拒绝：统一的编辑工具抽象

**方案**：设计一个 `IUnifiedEditTool` 接口，内部根据参数自动选择策略。

**拒绝理由**：不同策略的参数结构、匹配算法、错误处理、prompt 指引差异太大。保持策略独立、通过配置选择暴露哪些工具，是更清晰的职责分离。

### 6.3 拒绝：将 patch 格式绑定为 Lark grammar freeform tool

**方案**：像 Codex 一样将 `line-patch` 注册为 freeform tool（通过 Lark grammar 定义语法，模型直接生成结构化文本而非 JSON），模型直接生成 patch 文本。

**拒绝理由**：Nop 平台的工具体系基于 JSON schema 参数定义。改为：`line-patch` 使用 JSON schema 参数（`patch` 字段为字符串），patch 内容作为字符串值传递。模型通过 prompt 学习 patch 格式，但工具调用仍是标准 JSON。

---

## 七、与已有设计的关系

### 7.1 依赖

- **`nop-ai-tool-filesystem-design.md`**：编辑策略通过 `IToolFileSystem` 进行所有文件读写。原子替换（§9.1）为 `write` 和 `exact-replace` 提供崩溃安全性。
- **`04-tool-invocation.md`**：编辑策略作为 `IToolExecutor` 注册，遵循标准工具调用生命周期。
- **`01-architecture-baseline.md`**：编辑策略属于 Tool Layer（nop-ai-toolkit），不向上依赖 Agent Engine Layer。

### 7.2 影响

- **`02-execution-model.md`**：`exact-replace` 的 CAS 版本锁需要文件版本追踪机制（`probe()` + `version`），当前 `IToolFileSystem` 尚未提供。
- **`04-tool-invocation.md`**：工具注册表需支持编辑策略的动态启停。
- **prompt 组装**：system prompt 的工具指引区域需支持多策略共存时的使用指引。

### 7.3 后续演进

- **`line-patch` 的 shell 拦截**：当模型通过 bash 工具间接调用 line-patch（如 `bash -c "line-patch <<'EOF'..."`），可在 `nop-ai-shell` 中检测并路由。P2。
- **编辑预览**：`fuzzy-replace` 和 `line-patch` 可在执行前计算 diff 并展示预览。P2。
- **`exact-replace` 版本追踪**：为 `IToolFileSystem` 增加 `probe(path)` 返回版本标识，支持 CAS 语义。P1。
