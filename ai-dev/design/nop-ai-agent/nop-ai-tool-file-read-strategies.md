# Nop AI Tool 文件读取策略设计

**日期**：2026-09-20
**范围**：nop-ai-toolkit（文件读取工具）、nop-ai-agent（工具配置、prompt 组装）
**状态**：active
**灵感来源**：OpenAI Codex (`exec` shell)、Pi Agent (`read`)、DeepSeek Harness (`read` / `str_replace_editor view`)

---

## 一、设计结论

1. Nop 平台提供 **4 种独立的文件读取策略**，各自保留原始系统的设计，作为可插拔工具供 Agent 配置选择。
2. **行号**是策略间的核心差异：`numbered-read` 和 `cat-view` 返回带行号的内容；`plain-read` 返回纯文本；`shell-read` 返回 shell 原始输出。
3. 每种策略的截断策略、分页机制、大文件处理方式各不相同，均原样保留。

### 策略来源映射

| 策略 | 来源 Agent | 源码仓库 | 核心文件 |
|------|-----------|---------|---------|
| `shell-read` | OpenAI Codex | openai/codex | codex-rs/core/src/tools/handlers/shell_spec.rs (exec_command schema) + codex-rs/core/src/unified_exec/head_tail_buffer.rs (1MiB 截断) |
| `plain-read` | Pi Agent | earendil-works/pi | packages/coding-agent/src/core/tools/read.ts (工具定义) + truncate.ts (双重截断) |
| `numbered-read` | DeepSeek Harness | deepseek-ai/deepseek-harness | packages/fs/tool-fs/src/read.ts (工具注册) + read-render.ts (行号格式化 + 流式窗口) |
| `cat-view` | DeepSeek Harness | deepseek-ai/deepseek-harness | packages/fs/tool-str-replace-editor/src/index.ts (view 命令, cat -n 格式) |

---

## 二、四种独立读取策略

### 策略 1：`shell-read` — Shell 命令读取

**来源**：OpenAI Codex（无独立 read 工具，通过 `exec` shell 执行命令）

#### 行为

没有专用的文件读取工具。模型通过 shell 工具执行 `cat`、`head`、`sed`、`grep` 等命令来读取文件。文件读取能力完全依赖 shell 命令的输出。

#### 工具定义

```
工具名：exec（shell 工具）
参数：cmd（shell 命令字符串）
```

#### 读取方式

模型自行决定读取命令：
- `cat file.py` — 读取全部内容
- `head -n 100 file.py` — 读取前 100 行
- `sed -n '50,100p' file.py` — 读取第 50-100 行
- `grep -n "pattern" file.py` — 搜索并显示行号

#### 行号

**不返回行号**（除非模型主动使用 `cat -n` 或 `grep -n`）。shell 输出是原始文本，系统不附加行号元数据。

#### 截断策略

多层截断：
1. **收集层**：`HeadTailBuffer` 保留 1 MiB 的前 512KB（head）+ 后 512KB（tail），中间部分用 `"... {N} bytes omitted ..."` 替代
2. **模型面向层**：默认 token 预算 10,000 tokens（工具层面的 `max_output_tokens` 参数，与模型推理参数无关），超出时保留首尾、中间截断
3. **shell 命令自身**：`cat` 不截断，`head -n N` 由模型自行控制行数

截断输出格式：
```
Warning: truncated output (original token count: N)
Total output lines: N

{head content}...N tokens truncated...{tail content}
```

#### 大文件处理

模型需要自行分页（如 `sed -n '1,100p'` → `sed -n '101,200p'`）。无内置的 offset/limit 机制。HeadTailBuffer 的 1 MiB 收集上限意味着超大命令输出会被自动截断。

#### 适用场景

- 简单的文件查看
- 需要 shell 管道组合（如 `cat file | grep -A5 "pattern"`）
- 模型习惯使用命令行的场景

---

### 策略 2：`plain-read` — 纯文本读取

**来源**：Pi Agent (`read` tool)

#### 行为

专用的 `read` 工具，返回**纯文本内容，不带行号**。支持文本文件和图片。

#### 工具参数

```json
{
  "path": "src/foo.py",
  "offset": 100,
  "limit": 500
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径 |
| `offset` | number | 否 | 起始行号（1-indexed），默认 1 |
| `limit` | number | 否 | 最大行数 |

#### 行号

**不返回行号**。输出是文件的原始文本内容（截断后）。截断提示中包含行号范围信息，但内容本身不带行号前缀。

#### 截断策略

双重限制，先到先停：

| 限制 | 默认值 | 说明 |
|------|--------|------|
| 行数限制 | 2000 行 | `DEFAULT_MAX_LINES` |
| 字节限制 | 50KB | `DEFAULT_MAX_BYTES` |

截断时返回提示信息：
```
[Showing lines 1-2000 of 5000. Use offset=2001 to continue.]
```

首行超限（单行 > 50KB）时：
```
[Line 42 is 65.2KB, exceeds 50.0KB limit. Use bash: sed -n '42p' foo.py | head -c 51200]
```

#### 分页机制

- `offset`：指定起始行号（1-indexed）
- `limit`：指定读取行数
- 截断后提示 `offset=N` 供模型继续读取

#### 大文件处理

全部读入内存后截断。无流式读取。

#### 图片支持

自动检测图片 MIME 类型（jpg/png/gif/webp/bmp），以 `image` 内容块返回（base64）。自动缩放至 2000x2000 最大尺寸。非 vision 模型返回文本提示。

#### 输出格式

纯文本，示例：
```python
import os
from pathlib import Path

def main():
    print("hello")
```

#### 适用场景

- 日常文件查看
- 与 `fuzzy-replace` 工具配合（读取 → 精确替换）
- 图片文件查看

---

### 策略 3：`numbered-read` — 带行号读取

**来源**：DeepSeek Harness (`tool-fs` 模块)

#### 行为

专用的 `read` 工具，返回**带行号的结构化内容**。支持大文件流式读取和语法高亮元数据。

#### 工具参数

```json
{
  "file_path": "src/foo.py",
  "offset": 100,
  "limit": 500
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file_path` | string | 是 | 文件路径 |
| `offset` | number | 否 | 起始行号（1-indexed），默认 1 |
| `limit` | number | 否 | 最大行数，默认 2000 |

#### 行号

**返回行号**。每行以 `行号: 内容` 格式输出：

```
42: import os
43: from pathlib import Path
44: 
45: def main():
46:     print("hello")
```

#### 输出信封

模型看到的输出包含 XML 信封：

```
<path>src/foo.py</path>
<type>file</type>
<content>
42: import os
43: from pathlib import Path
44: 
45: def main():
46:     print("hello")

(Showing lines 42-46 of 500. Use offset=47 to continue.)
</content>
```

#### 截断策略

三重限制：

| 限制 | 默认值 | 说明 |
|------|--------|------|
| 行数限制 | 2000 行 | `READ_LIMIT` |
| 单行长度 | 2000 字符 | `READ_MAX_LINE_LENGTH`，超出截断并追加 `... (line truncated to 2000 chars)` |
| 字节限制 | 50KB | `READ_MAX_BYTES`，超出停止扫描 |

截断提示格式：
- 未到文件末尾：`(Showing lines 42-2041 of 5000. Use offset=2042 to continue.)`
- 到达文件末尾：`(End of file - total 500 lines)`
- 字节截断：`(Output capped. Showing lines 42-1500. Use offset=1501 to continue.)`

#### 分页机制

- `offset`：指定起始行号（1-indexed）
- `limit`：指定读取行数
- 超出范围时抛出 `FS_NOT_FOUND` 错误

#### 大文件处理

支持**流式读取**：文件 ≥ 10MB 或大小未知时使用 `streamText()`，避免全量加载内存。小文件整体读取。

#### 结构化返回值

工具返回结构化数据（不仅文本）：

```json
{
  "path": "src/foo.py",
  "offset": 42,
  "lines": [
    { "number": 42, "text": "import os" },
    { "number": 43, "text": "from pathlib import Path" }
  ],
  "totalLines": 500,
  "lang": "python"
}
```

UI 层可基于此数据渲染行号高亮视图。

#### 适用场景

- 需要精确行号定位的场景（与 `exact-replace` 工具的行号引用配合）
- 大文件读取（流式）
- UI 渲染行号视图

---

### 策略 4：`cat-view` — cat -n 风格读取

**来源**：DeepSeek Harness (`tool-str-replace-editor` 模块的 `view` 命令)

#### 行为

`str_replace_editor` 的 `view` 命令，使用 **`cat -n` 风格**的行号格式（6 字符右对齐）。与 `numbered-read` 的区别在于格式、分页方式和截断策略。

#### 工具参数

```json
{
  "command": "view",
  "path": "/workspace/src/foo.py",
  "view_range": [42, 60]
}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `command` | `"view"` | 固定值 |
| `path` | string（绝对路径） | 文件或目录路径 |
| `view_range` | `[start, end]` 或 `null` | 行范围，`[start, -1]` 表示到文件末尾 |

#### 行号格式（`cat -n` 风格）

```
     1  import foo
     2  const x = 1
     3  function hello() {
     ...
    42      return result
    43  }
```

行号右对齐，固定 6 字符宽度，后跟两个空格，再跟行内容。

#### 截断策略

- 总输出截断至 `maxOutputChars`（默认 16,000 字符）
- 超出时追加 `<response clipped>` 标记
- 无分页提示（不像 `numbered-read` 那样提示 offset）

#### 目录支持

`view` 命令支持目录查看（`numbered-read` 不支持）：
- 列出非隐藏条目，最多 2 层深度
- 排除 `.hidden`、`node_modules`、`__pycache__`
- 格式：`d\tdirname` 或 `f\tfilename`，按路径排序

#### 与 `numbered-read` 的差异

| 维度 | numbered-read | cat-view |
|------|---------------|----------|
| 行号格式 | `N: content` | `N（6 字符右对齐）  content` |
| 流式读取 | 支持（≥10MB） | 不支持（全量读入） |
| 二进制检测 | NUL 字节检测 | 依赖 fs 层 |
| 截断 | 2000 行 / 50KB / 单行 2000 字符 | 16,000 字符总量 |
| 分页 | offset + limit | view_range [start, end] |
| 结构化输出 | 有（lines[]） | 无（纯文本） |
| 目录支持 | 不支持 | 支持（2 层深度列表） |
| 语法提示 | 有（lang 字段） | 无 |

---

## 三、对比矩阵

| 维度 | shell-read | plain-read | numbered-read | cat-view |
|------|-----------|------------|---------------|----------|
| **行号** | 无（除非 `cat -n`） | 无 | 有（`N: content`） | 有（`cat -n` 风格） |
| **截断策略** | shell 命令自身 + 1MiB head/tail | 2000 行 / 50KB 先到先停 | 2000 行 / 50KB / 单行 2000 字符 | 16,000 字符总量 |
| **分页** | 模型自行 `sed` | offset + limit | offset + limit | view_range [start, end] |
| **大文件** | 模型自行分页 | 全量读入后截断 | ≥10MB 流式读取 | 全量读入 |
| **图片** | 不支持（需 shell 命令） | 支持（base64 + 自动缩放） | 不支持（仅文本） | 不支持 |
| **目录** | 支持（`ls`） | 不支持 | 不支持 | 支持（2 层深度） |
| **结构化输出** | 无（shell 原始输出） | 纯文本 | 结构化（lines[] + totalLines） | 纯文本 |
| **语法高亮元数据** | 无 | 无 | 有（lang 字段，供 UI 使用） | 无 |
| **模型学习成本** | 低（shell 命令） | 低（JSON 参数） | 低（JSON 参数） | 低（JSON 参数） |
| **Token 效率** | 低（shell 输出含噪音） | 高（纯文本） | 中（行号前缀开销） | 中（行号前缀开销） |

---

## 四、配置模型

```yaml
nop.ai.agent:
  toolNames:
    # 读取策略（按需选择一种或多种）
    - shell-read        # 策略 1：通过 bash 读取（需要 bash 工具）
    - plain-read        # 策略 2：纯文本读取（无行号）
    - numbered-read     # 策略 3：带行号读取
    - cat-view          # 策略 4：cat -n 风格读取 + 目录查看
```

### 预设

| 预设 | 读取工具 | 配合的编辑工具 | 适用场景 |
|------|---------|---------------|---------|
| `minimal` | shell-read | write | 命令行风格、简单场景 |
| `default` | plain-read | write, fuzzy-replace | 日常开发 |
| `strict` | numbered-read | write, exact-replace | 需要行号定位 |
| `deepseek` | numbered-read, cat-view | write, exact-replace | 完整精确替换体系 |
| `full` | plain-read, numbered-read | write, line-patch, fuzzy-replace, exact-replace | 多策略对比 |

### 策略组合建议

- **fuzzy-replace** 配 **plain-read**：`fuzzy-replace` 基于文本匹配不需要行号，配纯文本读取即可
- **exact-replace** 配 **numbered-read**：精确替换需要读取后定位，行号辅助模型理解文件结构
- **cat-view** 配 `str_replace_editor`：view 行号格式与 `cat -n` 一致，模型可直接引用行号
- **line-patch** 不需要专用读取工具：patch 格式自带上下文锚点（`@@`），模型通过 shell-read 或任一 read 工具获取文件内容后自行构造 patch

---

## 五、拒绝了什么

### 5.1 拒绝：统一读取接口

**方案**：设计一个 `IFileReadTool` 接口，内部根据配置返回不同格式（行号/无行号）。

**拒绝理由**：四种策略的差异不仅是行号——截断策略、大文件处理（全量 vs 流式）、输出格式（纯文本 vs 结构化 vs shell 输出）、错误处理各不相同。统一接口会导致大量条件分支。保持策略独立、通过配置选择更清晰。

### 5.2 拒绝：只保留行号读取

**方案**：统一使用带行号的读取方式（numbered-read），淘汰无行号方案。

**拒绝理由**：行号增加 token 开销（每行多 3-5 个字符），对于不需要行号的场景（如 `fuzzy-replace` 工具的文本匹配）是纯浪费。无行号方案在 token 效率上更优。保留选择权。

---

## 六、与已有设计的关系

### 6.1 依赖

- **`nop-ai-tool-filesystem-design.md`**：读取策略通过 `IToolFileSystem.readText()` / `readLines()` 进行文件读取。`shell-read` 策略除外——它通过 `BashExecutor` 直接执行 shell 命令。
- **`nop-ai-tool-file-edit-strategies.md`**：读取策略与编辑策略配合使用，策略组合由 Agent 配置决定。

### 6.2 影响

- **prompt 组装**：启用多种读取工具时，system prompt 需指引模型在什么情况下使用哪个工具。
- **`nop-ai-shell`**：`shell-read` 策略依赖 bash 执行能力，`nop-ai-shell` 的虚拟 shell 需支持 `cat`、`head`、`sed` 等常用读取命令。

### 6.3 后续演进

- **图片读取扩展**：当前仅 `plain-read` 支持图片。可为 `numbered-read` 增加图片检测和 base64 返回能力。P2。
- **编码检测**：四种策略均假设 UTF-8 编码。对于非 UTF-8 文件的处理（如 GBK）可作为后续增强。P2。
- **grep 内置**：当前 grep 是独立工具。可在 read 工具中增加 `pattern` 参数，实现 read+grep 一体化。P1。
