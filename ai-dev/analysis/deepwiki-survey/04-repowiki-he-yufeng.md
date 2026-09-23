# he-yufeng/RepoWiki 项目深度分析

> Status: open
> Date: 2026-09-23
> Scope: he-yufeng/RepoWiki 项目的架构设计、处理管线、LLM 集成、增量缓存、前端架构及与 Nop 平台的可借鉴性全面调研
> Conclusion: open
> Superseded By: —

## Context

`he-yufeng/RepoWiki`（GitHub: `he-yufeng/RepoWiki`）是一个轻量级 DeepWiki 替代品，当前已获 259 stars，基于 Python 构建（版本 0.4.2，requires-python >= 3.10）。其核心定位是为代码库自动生成结构化 Wiki 文档，提供 CLI 和 Web UI 两种交互方式，零依赖安装（`pip install repowiki`），仅需 SQLite 作为缓存后端。与 DeepWiki（SaaS 仅、本地仓库不支持）和 deepwiki-open（Docker Compose + PostgreSQL）不同，RepoWiki 以极低的部署门槛实现了类似功能——用户仅需一个 API 密钥即可为任意本地目录或 GitHub 仓库生成完整的 Wiki 文档。

本分析旨在深入调研其架构设计、五阶段处理管线、LLM 提示工程、增量缓存机制、前端实现模式，重点关注以下维度：零依赖设计哲学、PageRank 阅读路径推荐、导入感知的文件排序、以及与 Nop 平台文档自动化能力的可借鉴性。分析的最终目的是为 Nop 平台的代码文档自动生成能力建设提供架构参考。

`RepoWiki` 的出现反映了一个更大的趋势：LLM 驱动的自动化工具正在从代码生成扩展到知识生成。然而，传统的文档生成工具往往面临两个核心挑战——一是 LLM 容易编造不存在的信息（幻觉问题），二是生成的内容缺乏可验证的证据链。RepoWiki 通过 PageRank 驱动的阅读路径和诚实覆盖率报告直接回应了第二个挑战（但未解决第一个），这使其在同类项目中具有独特的差异化定位。

---

## Analysis

### 1. 项目概览与架构

#### 1.1 项目定位与技术选型

**he-yufeng/RepoWiki** 是一个纯 Python 实现的 CLI + Web UI 代码库文档生成工具。其技术选型体现了明确的"轻量优先"哲学——在功能完备性和部署极简之间寻找平衡点。

**核心依赖分析**（来自 `pyproject.toml`）：

| 依赖 | 版本约束 | 职责 | 是否可延迟导入 |
|------|----------|------|----------------|
| `click>=8.0` | — | CLI 命令框架 | 否 |
| `rich>=13.0` | — | 终端美化输出 | 否 |
| `litellm>=1.40.0` | <1.98.0 on 3.10 | LLM 抽象层 | ✅ `_load_litellm()` |
| `pydantic>=2.0` | — | 数据模型验证 | 否 |
| `aiosqlite>=0.20.0` | — | 异步 SQLite 缓存 | 否 |
| `python-dotenv>=1.0` | — | 环境变量加载 | 否 |
| `networkx>=3.0` | — | 依赖图构建 | 否 |
| `numpy>=1.24` | — | 数值计算 | 否 |

**Web 扩展依赖**（`pip install repowiki[web]`）：

- `fastapi>=0.115.0`：Web 框架
- `uvicorn[standard]>=0.30.0`：ASGI 服务器
- `python-multipart>=0.0.9`：文件上传

**技术选型决策分析**：

- **Python 3.10+** 而非更高级版本：`litellm 1.98.0 broke 3.10 with an unguarded typing.NotRequired import`，因此 pyproject.toml 中明确限制了 litellm 版本。Python 3.10 的选择既兼容了最广泛的运行环境，又避免了新版本引入的类型系统不兼容问题。
- **Click** 而非 `argparse`：Click 提供命令分组（`@click.group()`）、自动帮助生成和类型安全的参数解析，大幅简化 CLI 开发。
- **Pydantic** 而于 dataclasses：Pydantic 提供运行时类型验证和序列化，贯穿整个管线（`FileInfo` → `ProjectContext` → `WikiData`），确保每一阶段的数据完整性。
- **NetworkX** 但不自实现 PageRank：NetworkX 提供图数据结构和算法框架，但 `_pagerank_power_iteration()` 自行实现幂迭代以避免 scipy 依赖。这是一个精心设计的权衡——NetworkX 3.6 将 PageRank 移到了 scipy 后端，而 RepoWiki 刻意不引入 scipy。
- **aiosqlite** 而非 `sqlite3`：异步 I/O 避免阻塞事件循环，在 Web 模式下尤为重要。

项目已获 259 stars，反映了市场对轻量级代码文档生成工具的强烈需求。与 LangChain 官方的 `openwiki`（15.8k stars，TypeScript + DeepAgents 框架）相比，RepoWiki 的差异化在于**零依赖安装**和**极简架构**。

#### 1.2 项目结构详解

| 目录 | 职责 | 关键文件 | 行数规模 |
|------|------|----------|----------|
| `src/repowiki/` | 主源码包 | `__init__.py` (3行), `cli.py` (533行), `config.py` (93行) | ~650行 |
| `src/repowiki/core/` | 核心逻辑 | `scanner.py` (406行), `analyzer.py` (320行), `graph.py` (279行), `wiki_builder.py` (416行), `models.py` (157行), `cache.py` (111行), `state.py` (28行), `skeleton.py` (108行), `rag.py` (387行) | ~2,212行 |
| `src/repowiki/llm/` | LLM 客户端 + 提示模板 | `client.py` (107行), `prompts.py` (239行) | ~346行 |
| `src/repowiki/ingest/` | 本地/GitHub 接入 | `local.py` (62行), `github.py` (127行) | ~189行 |
| `src/repowiki/export/` | 多格式导出 | `markdown.py` (118行), `json_export.py` (50行), `html.py` (221行), `site.py` (46行) | ~435行 |
| `src/repowiki/server/` | FastAPI Web 后端 | `app.py` (157行), `models.py` (43行), `routers/` (scan.py 133行 + wiki.py 110行 + chat.py 94行) | ~537行 |
| `frontend/` | React + Vite + TailwindCSS 前端 | `src/App.tsx`, `src/pages/`, `src/components/` | — |
| `tests/` | Python 测试 | pytest + pytest-asyncio | — |
| `evals/` | 检索评估框架 | `run_eval.py` | — |

核心代码量约 3,600 行（不含前端），是一个精炼且功能完整的项目。

#### 1.3 核心架构特征

RepoWiki 的架构围绕以下五个核心特征构建：

1. **五阶段流水线**：Scan → Graph → Analyze → Build → Export，每个阶段有明确的输入输出和错误边界。Scan 和 Graph 阶段完全基于文件系统操作，不涉及任何 LLM 调用，这使得 `repowiki map` 命令可以零 LLM 成本运行。
2. **SQLite 内容哈希缓存**：`{model}:{language}:{stage}:{content_hash}` 键，1 年 TTL，使重新扫描未变更的模块零 API 调用。缓存键嵌入模型和语言信息，因此切换任一参数都会自动失效。
3. **PageRank 阅读路径**：基于真实 import 图的 PageRank 排序，推荐文件阅读顺序。PageRank 算法参数：alpha=0.85, max_iter=100, tol=1e-6。
4. **增量再生**：`.repowiki-state.json` 记录每页的输入指纹，仅重新生成变更页面。JSON/HTML 导出在内容未变时跳过写入。
5. **零依赖核心**：`pip install repowiki` 即可运行，Web UI 为可选扩展（`pip install repowiki[web]`）。

---

### 2. 处理管线（五阶段）

RepoWiki 的处理管线分为五个严格排序的阶段，每个阶段有明确的职责边界和错误处理机制。管线设计遵循"先 cheap 后 expensive"的原则——文件系统操作（Scan、Graph）先于 LLM 调用（Analyze），且每个阶段都可以独立运行。

#### 2.1 Phase 1: Scan（文件系统扫描）

**核心函数**：`scan_directory()` in `core/scanner.py` (406行)

扫描阶段完全基于文件系统操作，不涉及任何 LLM 调用，是整个管线中速度最快的阶段。该函数接收 `root` 路径、`max_file_size`（默认 200KB）、`max_files`（默认 1000）和可选的 `report: ScanReport` 对象，返回 `FileInfo[]` 列表。

**多层过滤机制**：

| 过滤类型 | 实现 | 说明 |
|----------|------|------|
| `_SKIP_DIRS` | 30+ 跳过目录名集合 | `.git`, `node_modules`, `__pycache__`, `.venv`, `venv`, `env`, `.idea`, `.vscode`, `.next`, `dist`, `build`, `.tox`, `.mypy_cache`, `.pytest_cache`, `.ruff_cache`, `egg-info`, `.turbo`, `coverage`, `.cache`, `vendor`, `target`, `__snapshots__`, `.svn`, `.hg`, `.gradle`, `.m2`, `Pods`, `.dart_tool`, `.pub-cache` |
| `_SKIP_EXTS` | 30+ 跳过扩展名集合 | 二进制、编译产物、`.min.js`, `.min.css`, `.map`, `.wasm`, `.lock`, `.db`, `.sqlite`, `.pdf`, `.docx`, `.xlsx` 等 |
| `_SENSITIVE_NAMES` | 敏感文件名集合 | `.env`, `.env.local`, `.env.production`, `.env.development`, `.npmrc`, `.pypirc`, `.netrc`, `id_rsa`, `id_ed25519`, `known_hosts` |
| `_MINIFIED_SOURCE_EXTS` | 压缩文件检测 | `.js`, `.mjs`, `.cjs`, `.css` 文件中单行超 1000 字符且非空行 ≤ 5 的被跳过 |
| `.gitignore` / `.repowikiignore` | `IgnoreRules` 类 | 支持 `!` 反模式和 `/` 目录模式，fnmatch 匹配 |
| `max_file_size` (200KB) | 文件大小上限 | 超过 200KB 的文件被标记为 oversized 并记录路径 |
| `max_files` (1000) | 文件数量上限 | 按优先级排序后截断，超出部分记录 `priority_dropped` |
| `_is_binary()` | 二进制检测 | 检查前 1024 字节是否包含 `\x00` |
| `_is_sensitive_name()` | 敏感文件名 | 除精确匹配外，`.env.*` 模式（除 `.env.example` 外）也被排除 |

**优先级排序机制**：当文件数量超过 `max_files` 限制时，RepoWiki 不按文件名字母序截断，而是按优先级排序：

```python
key=lambda i: (
    0 if results[i].is_config or results[i].is_entrypoint  # 最高优先级
    else 1 if results[i].language in _CODE_LANGS           # 代码文件
    else 2,                                                # 文档/资产
    i,
)
```

这确保了 LLM 上下文中始终包含最重要的项目信息——配置文件和入口文件优先于文档和测试文件。

**文件排序规则**：扫描完成后，结果按 `(is_config, is_entrypoint, path)` 排序，配置文件在前，入口点次之。

**关键输出**：
- `FileInfo[]`：每个文件包含 path, size, language, lines, preview, content, is_config, is_entrypoint
- `file_tree`：ASCII 树状结构，最多 200 行
- `ScanReport`：包含 candidates, kept, oversized, binary_count, minified_count, priority_dropped, skipped_dirs 等覆盖率统计

#### 2.2 Phase 2: Graph（依赖图构建）

**核心函数**：`DependencyGraph.build_from_project()` in `core/graph.py` (279行)

图构建阶段同样完全基于文件系统操作，使用正则表达式解析 6 种语言的 import 语句，构建有向图后运行 PageRank。

**6 种语言的 Import 解析策略**：

| 语言 | 正则模式 | 解析策略 | 候选路径生成 |
|------|----------|----------|-------------|
| Python | `import X` / `from X import Y` | 包路径 → 文件路径映射，支持 `src/` 前缀和 `__init__.py` | `{rel}.py`, `{rel}/__init__.py`, `src/{rel}.py` |
| JS/TS/JSX/TSX/MJS/CJS | `import ... from 'X'` / `require('X')` | 相对路径解析，`posixpath.normpath` | `{rel}`, `{rel}.ts/.tsx/.js/.jsx/.mjs/.cjs`, `{rel}/index.*` |
| Go | `"path"` | 取最后两段作为文件名 | `{last_two}.go` |
| Rust | `use X` / `mod X` | `::` → `/` 路径映射 | `src/{rel}.rs`, `src/{rel}/mod.rs`, `{rel}.rs` |
| Java | `import X.Y;` | `.` → `/` 路径映射 | `src/main/java/{rel}.java`, `{rel}.java` |

**PageRank 实现细节**：`_pagerank_power_iteration()` 采用纯 Python 实现的幂迭代算法：

```python
alpha = 0.85       # 阻尼因子
max_iter = 100     # 最大迭代次数
tol = 1.0e-6       # 收敛容差
```

- 初始化：所有节点分数 = 1/n
- 每次迭代：处理 dangling nodes（无出边的节点），将其分数均匀重新分配
- 收敛判断：所有节点分数变化绝对值之和 < tol
- 失败回退：PageRank 异常时返回均匀分数

**NetworkX 依赖规避**：注释明确说明原因——"networkx 3.6 moved its own implementation onto scipy, which RepoWiki does not depend on -- and pulling in scipy for one algorithm is a bad trade for a CLI install"。这体现了 RepoWiki 对零依赖哲学的严格遵守。

**附加图分析功能**：

| 方法 | 用途 | 算法 |
|------|------|------|
| `get_core_files(top_n=10)` | 排名前 N 的核心文件 | PageRank 降序 |
| `get_entry_points()` | 入度 ≤ 1 且出度 > 0 的文件 | 图论入度/出度分析 |
| `find_isolated_files()` | 入度和出度均为 0 的文件 | 图论度分析 |
| `find_circular_dependencies(limit=10)` | 强连通分量大小 > 1 | Tarjan 算法（NetworkX 实现） |
| `get_module_dependencies()` | 跨顶层目录的模块依赖 | 按 `_get_module()` 分组 |
| `to_mermaid()` | 生成 Mermaid 流程图 | 图遍历 + 节点 ID 清洗 |

**`_get_module()` 函数**：将文件路径映射到模块名。对于 `src/`, `lib/`, `pkg/`, `internal/`, `app/` 等常见包装目录，取第二层目录名作为模块名。例如 `src/auth/user.py` → 模块名 `auth`。

**PageRank 排序的独特价值**：PageRank 不只是排序文件——它同时驱动了阅读指南的文件顺序、卡片页面的入口文件推荐、以及依赖页面的核心文件列表。同一个算法在三个不同页面复用，这是 RepoWiki 架构设计的精妙之处。

#### 2.3 Phase 3: Analyze（4 次 LLM 调用）

**核心函数**：`Analyzer.analyze()` in `core/analyzer.py` (320行)

分析阶段是整个管线中唯一涉及 LLM 调用的阶段，也是成本最高的阶段。4 次调用按严格顺序执行，每次调用有独立的缓存键。`Analyzer` 类接收 `LLMClient`、`Cache`、`language` 和 `concurrency`（默认 5）参数。

**4 次 LLM 调用的详细流程**：

**Pass 1 - Overview**（`_generate_overview`）：
- **输入**：`project.file_tree` + 所有 config/entrypoint 文件内容（通过 `_build_key_files_context` 收集）
- **提示**：`build_overview_prompt(file_tree, key_files, language)`
- **LLM 参数**：`max_tokens=4096`, `temperature=0.3`
- **输出**：`ProjectOverview`（name, one_liner, description, tech_stack, setup_instructions, key_features）
- **缓存键**：`{model}:{lang}:overview:{tree_hash}`，其中 `tree_hash = sha256(file_tree + key_files_text)`
- **降级处理**：JSON 解析失败时返回 `ProjectOverview(name=project.name)`

**Pass 2 - Modules**（`_analyze_modules`）：
- **模块分组**：`_group_into_modules()` 按顶层目录分组。`src/`, `lib/`, `pkg/`, `internal/`, `app/` 前缀的文件取第二层目录作为模块名
- **并发控制**：`asyncio.Semaphore(5)` 限制并发数为 5，防止 API 速率限制
- **提示**：`build_module_prompt(name, files_context, project_summary, language)`
- **每个文件的上下文**：通过 `context_for_file()` 确保在 4096 字符预算内（超长 Python 文件使用 AST 骨架）
- **缓存键**：`{model}:{lang}:module:{name}:{content_hash(''.join(content_parts))}`
- **缓存复用**：已缓存的模块跳过 LLM 调用，`asyncio.as_completed` 处理并发结果
- **排序**：按文件数降序排列，名称作为 tiebreaker

**Pass 3 - Architecture**（`_generate_architecture`）：
- **输入**：`project.file_tree` + 关键文件内容
- **提示**：`build_architecture_prompt(file_tree, key_files, language)`
- **输出**：`ArchitectureDiagram`（architecture_type, description, components[], mermaid_component, mermaid_sequence, data_flow）
- **缓存键**：`{model}:{lang}:arch:{tree_hash}`

**Pass 4 - Reading Guide**（`_generate_reading_guide`）：
- **输入**：PageRank 排序的文件列表（前 20 名）+ 模块摘要
- **排名构建**：PageRank 降序 + 未排名文件按文件列表顺序补充，确保至少 20 个条目
- **提示**：`build_reading_guide_prompt(rankings, module_summaries, language)`
- **缓存键**：`{model}:{lang}:guide:{tree_hash}:{content_hash(rankings + module_summaries)}`
- **关键设计**：缓存键包含排名文本，因此 import-only 编辑导致排名变化时也会使缓存失效——确保阅读指南始终基于最新的依赖结构

**Prompt 模板设计**：所有 5 个 prompt builder（`build_overview_prompt`, `build_module_prompt`, `build_architecture_prompt`, `build_reading_guide_prompt`, `build_chat_prompt`）都强制执行"输出 ONLY valid JSON"的约束，通过 `extract_json()` 函数处理 markdown 围栏和多余文本。语言支持：en, zh, ja, ko。

**骨架提取机制**：`context_for_file()` 在 `core/skeleton.py` 中实现。对于超过 4096 字符预算的 Python 文件，使用 `ast` 模块提取符号骨架（所有顶层类和函数的签名 + docstring），而非简单的头部截断。这使得 2000 行的模块通过结构而非前 4096 字符被分析。骨架输出格式：

```
# [skeleton: 45 symbols from 2000 lines]
"""module docstring"""
class Foo:
    """docstring"""
    def bar(self, x: int) -> str:
        """docstring"""
...
# ... 40 more symbols omitted
```

**LLM 客户端实现**：`LLMClient` in `core/llm/client.py`：
- `_load_litellm()`：延迟导入，`repowiki map` 等零 LLM 路径不触发 litellm 导入（litellm 导入耗时数秒，且在 Python 3.14 可能 hang）
- `complete()`：非流式调用，返回完整响应文本，跟踪 token 使用量和成本
- `stream()`：流式调用，yield 文本 chunks，用于 Web 端的 SSE 流式输出
- 错误处理：LLM 调用失败时返回 `[LLM Error: {e}]` 而非抛出异常，确保管线不会因单次失败而中断

#### 2.4 Phase 4: Build（Wiki 页面组装）

**核心函数**：`WikiBuilder.build()` in `core/wiki_builder.py` (416行)

构建阶段将 LLM 分析结果组装为结构化的 Wiki 页面，共 7 种页面类型：

| 页面类型 | ID | 内容来源 | 顺序 | 是否可选 |
|----------|-----|----------|------|----------|
| Overview | `index` | `ProjectOverview` | 0 | 始终生成 |
| Architecture | `architecture` | `ArchitectureDiagram` | 1 | 需 `architecture_type` 非空 |
| Knowledge Cards | `cards` | `ModuleDoc[]` + `DependencyGraph` | 2 | 需至少 1 个模块 |
| Modules | `modules/{name}` | `ModuleDoc` | 3+ | 需至少 1 个模块 |
| Reading Guide | `reading-guide` | `ReadingGuide` | 10 | 需 `guide.steps` 非空 |
| Dependencies | `dependencies` | `DependencyGraph` | 11 | 需 Mermaid 非空 |
| Symbol Index | `symbols` | `ModuleDoc.file.key_symbols` | 12 | 需至少 1 个符号 |

**跨页链接机制**：`_link_pages()` 实现反向链接——单反引号包裹的符号名或文件路径如果匹配到另一个 Wiki 页面，就被替换为相对 `.md` 链接。代码围栏内的内容不被链接化（通过 `_CODE_SPAN` 正则排除）。当多个页面定义了同名符号时，第一个页面胜出。

链接生成使用 `_rel_href(from_page_id, to_page_id)` 计算相对路径：
```python
base = posixpath.dirname(from_page_id)
target = f"{to_page_id}.md"
return target if not base else posixpath.relpath(target, base)
```

**知识卡片页面**：每个模块生成一个紧凑卡片，包含 purpose、文件数、symbols（前 5 个）、concepts（前 4 个）、入口文件、内部链接和完整页面链接。这是在打开完整模块页之前的快速导航入口。

**增量状态机制**：`core/state.py`（28行）维护 `.repowiki-state.json`，记录每页的输入指纹。状态文件结构：
```json
{
    "version": 1,
    "model": "deepseek/deepseek-chat",
    "language": "en",
    "pages": {
        "index": {"inputs": "overview_hash|page:content_hash"},
        "modules/auth": {"inputs": "module_auth_hash|page:content_hash"}
    }
}
```

重新扫描时，仅重新生成指纹变化的页面，删除已移除模块的页面。JSON/HTML 导出在内容未变时跳过写入（比较文件内容是否相同）。

#### 2.5 Phase 5: Export（多格式导出）

**导出格式与函数**：

| 格式 | 函数 | 增量支持 | 说明 |
|------|------|----------|------|
| Markdown | `export_markdown()` | ✅ | `.md` + `_sidebar.md` + `README.md` |
| JSON | `export_json()` | ✅ | `repowiki.json`，内容未变时返回 `False` |
| HTML | `export_html()` | ✅ | 自包含 HTML，Mermaid 由 CDN 渲染 |
| Site | `write_site_loader()` | — | `index.html` + `.nojekyll`，docsify + Mermaid |

**Markdown 导出细节**：
- `_sidebar.md`：docsify 侧边栏导航，格式为 `- [Title](page.md)` 的嵌套列表
- `README.md`：GitHub 渲染用的概述 + 目录，解决"wiki 提交到 repo 后显示空白文件列表"的问题
- 增量模式：`fingerprint = {inputs}|page:{content_hash(page.content)}`，跨页链接变化时也触发重新生成
- `--site` 标志：额外生成 GitHub Pages 就绪的 loader（`index.html` + `.nojekyll`）

**HTML 导出细节**：
- 自包含单文件 HTML，含内联 CSS（220行模板）和 SPA 导航
- Mermaid 图表通过 CDN 的 `mermaid@11` 渲染
- 简单的 `markdown → HTML` 转换器（无外部依赖），支持标题、引用、列表、代码块、内联格式
- `showPage()` JavaScript 函数实现页面切换和 Mermaid 渲染

**Site 导出细节**：
- `index.html` 使用 docsify 加载 `README.md` 和 `_sidebar.md`
- `.nojekyll` 空文件防止 GitHub Pages 忽略 `_sidebar.md`
- CDN 引入 docsify@4、mermaid@11、docsify-mermaid@2

---

### 3. 提取模板与提示系统

#### 3.1 Prompt 模板设计

5 个 prompt builder 位于 `src/repowiki/llm/prompts.py`（239行）：

| 函数 | 输出模型 | 输入 | 用途 | temperature | max_tokens |
|------|----------|------|------|-------------|------------|
| `build_overview_prompt` | `ProjectOverview` | file_tree + key_files + language | 项目概述 | 0.3 | 4096 |
| `build_module_prompt` | `ModuleDoc` | module_name + files_context + project_summary + language | 模块文档 | 0.3 | 4096 |
| `build_architecture_prompt` | `ArchitectureDiagram` | file_tree + key_files + language | 架构图 | 0.3 | 4096 |
| `build_reading_guide_prompt` | `ReadingGuide` | rankings + module_summaries + language | 阅读路径 | 0.3 | 4096 |
| `build_chat_prompt` | LLM messages | question + context_chunks + language + history | Q&A 对话 | 0.3 | 4096 |

**系统提示角色设计**：

所有 prompt 的系统提示都遵循统一的设计原则——"Do NOT use filler phrases like 'leveraging', 'utilizing', 'cutting-edge', 'robust', or 'comprehensive'. Just describe what things do."这一约束确保生成的文档简洁、具体、无冗余。

**语言指令**：
```python
lang_map = {
    "en": "Respond in English.",
    "zh": "请用中文回答。",
    "ja": "日本語で回答してください。",
    "ko": "한국어로 답변해주세요.",
}
```

**Chat Prompt 特殊设计**：`_MAX_CHAT_HISTORY_TURNS = 6`，仅保留最近 6 轮对话（12 条消息）到 prompt 中，平衡多轮上下文与 token 成本。历史消息从最新到最旧截取。

#### 3.2 JSON 提取与容错

`extract_json()` 函数处理 LLM 输出的各种格式：

1. 去除 markdown 代码围栏（` ```json ` / ` ``` `）
2. 尝试直接 JSON 解析
3. 定位第一个 `{` 或 `[` 到最后一个 `}` 或 `]`，截取后解析
4. 失败时返回 `None`

各 pass 有降级处理：
- Overview 失败 → `ProjectOverview(name=project.name)`
- Module 失败 → `ModuleDoc(name=name, purpose=f"Module containing {len(files)} files")`
- Architecture 失败 → `ArchitectureDiagram()`
- Reading Guide 失败 → `ReadingGuide()`

这种"永远返回有效数据"的设计确保了管线不会因 LLM 输出异常而中断。

---

### 4. 缓存与增量机制

#### 4.1 SQLite 分析缓存

**核心类**：`Cache` in `core/cache.py` (111行)

缓存键格式：`{model}:{language}:{stage}:{content_hash}`，其中 `content_hash` 是 SHA-256 截断到 24 字符的哈希。

- **存储**：`~/.repowiki/cache.db`，单表 `cache(key TEXT PRIMARY KEY, value TEXT, created_at REAL)`，另有 `projects` 表
- **TTL**：1 年（365 * 24 * 3600 秒），过期条目自动删除
- **失效机制**：模型或语言切换时缓存键前缀变化，自动失效；`repowiki cache-clear` 手动清除
- **关键设计**：缓存键包含 `content_hash`，因此源文件修改后对应模块的缓存自动失效——即使模型和语言不变

缓存键的设计哲学：嵌入模型和语言信息，使得"切换模型后缓存中的旧输出不会被错误使用"。这是缓存正确性的关键保障。

#### 4.2 增量状态与导出

**核心类**：`state.py` (28行)

- `.repowiki-state.json` 记录每页的 `inputs`（fingerprint）
- `page_inputs` 映射 `page_id → cache_key`
- 增量比较时，`fingerprint = {inputs}|page:{content_hash(page.content)}`，确保跨页链接变化时也触发重新生成
- JSON/HTML 导出在内容未变时跳过写入
- `full=True` 参数忽略状态文件，强制全量重建

增量设计的关键在于：不仅比较 LLM 缓存键（`inputs`），还比较最终渲染内容（`page:content_hash`）。这是因为跨页链接取决于全局符号索引——一个兄弟模块的变化可能导致当前页面的链接也需要更新，即使当前页面的自身输入未变。

#### 4.3 RAG 索引增量构建

**核心类**：`SimpleRAG` in `core/rag.py` (387行)

- **索引位置**：`~/.repowiki/rag/{repo_key}.json`
- **增量机制**：`index_incremental()` 比较 per-file 哈希，未变更文件的 chunks 和 TF-IDF 向量复用
- **TF-IDF**：纯 Python 实现，无外部依赖；CJK 使用 bigram 分词（`re.findall(r"[一-鿿]+")` 生成 bigram）
- **ModuleIndex**：额外的模块级 TF-IDF 索引，桥接自然语言问题到文件——解决"paraphrased question with no lexical overlap against the code"的问题
- **缓存持久化**：原子写入（先写 `.tmp` 再 `replace`），`index_fingerprint()` 包含 repo root 路径 + 每个文件的 path/size/content_hash
- **检索**：`retrieve()` 支持 `boost` 参数（模块索引匹配），`alpha=0.4` 权重平衡 lexical hit 和 module card boost

**Tokenize 函数**：
```python
def _tokenize(text: str) -> list[str]:
    tokens = re.findall(r"[a-zA-Z_]\w*", text.lower())
    for run in re.findall(r"[一-鿿]+", text):
        if len(run) == 1:
            tokens.append(run)
        else:
            tokens.extend(run[i : i + 2] for i in range(len(run) - 1))
    return tokens
```

英文按单词 tokenize，中文按 bigram tokenize。这使得中文问题可以有效匹配代码中的中文标识符和注释。

---

### 5. Web 界面与 API

#### 5.1 FastAPI 后端

**入口**：`server/app.py` (157行) — `create_app()` 工厂函数

- **3 个 Router**：`scan`（项目扫描）、`wiki`（Wiki 内容查询）、`chat`（Q&A 对话）
- **内存项目存储**：`_projects` dict，key 为 8 字符 UUID，value 包含 `info`, `wiki`, `project`, `progress`
- **静态文件服务**：React SPA，`_SPAStaticFiles` 处理刷新回退（所有非 `/api/` 路径返回 `index.html`）
- **CORS**：允许 `localhost:5173`（Vite dev server）和 `localhost:3000`
- **SSE 进度流**：`/api/project/{id}/status` 提供扫描进度的 Server-Sent Events
- **无前端回退**：构建的静态文件不存在时返回 `_NO_FRONTEND_PAGE` 提示用户构建前端

**生命周期管理**：`lifespan` 上下文管理器初始化 Cache 并预加载项目（通过 `REPOWIKI_SERVE_TARGET` 环境变量）。

#### 5.2 前端架构

- **React + Vite + TailwindCSS**：现代前端技术栈
- **三栏布局**：导航栏 + 内容区 + 文件查看器
- **SPA 路由**：`/project/<id>/file/<path>#L120-L140` 风格的行号链接，刷新后仍可正确导航
- **Mermaid 渲染**：docsify-mermaid 在 markdown 导出中，CDN mermaid 在 HTML 中
- **API 代理**：CORS 配置允许前端 dev server 访问后端 API

#### 5.3 API 端点

| 端点 | 方法 | 用途 | 关键参数 |
|------|------|------|----------|
| `/api/scan` | POST | 启动后台扫描 | `path` 或 `url`，`language`，`model`，`api_key` |
| `/api/project/{id}` | GET | 获取项目状态 | — |
| `/api/project/{id}/status` | GET (SSE) | 扫描进度流 | — |
| `/api/project/{id}/wiki` | GET | 获取 Wiki 结构（sidebar + pages） | — |
| `/api/project/{id}/wiki/{page_id}` | GET | 获取单页内容 | — |
| `/api/project/{id}/file/{path}` | GET | 获取源文件内容 | — |
| `/api/project/{id}/graph` | GET | 获取依赖图数据（nodes, edges, rankings, mermaid） | — |
| `/api/project/{id}/chat` | POST (SSE) | 流式 Q&A 对话 | `question`，`history` |
| `/api/health` | GET | 健康检查 | — |

**Scan 后台任务**：`_run_scan()` 使用 `asyncio.create_task()` 创建后台任务，扫描过程通过 `progress` 回调更新 `proj["progress"]`，前端通过 SSE 实时接收进度更新。

**Chat 流式响应**：`event_stream()` 先发送 references（JSON），再流式发送 LLM 响应 chunks，最后发送 `done: true`。

---

### 6. 关键设计决策与创新

#### 6.1 零依赖安装哲学

RepoWiki 的 `pip install repowiki` 不拉取任何重量级依赖：

- **litellm 延迟导入**：`_load_litellm()` 确保 `repowiki map` 等零 LLM 路径不触发 litellm 导入（litellm 导入耗时数秒，且在 Python 3.14 可能 hang）
- **NetworkX 零 scipy 依赖**：`_pagerank_power_iteration()` 自行实现幂迭代，避免 scipy 依赖
- **aiosqlite** 作为唯一数据库，SQLite 无需独立服务
- **rich** 用于终端美化，非核心功能依赖
- **hatchling** 作为构建后端，轻量且现代

对比 `openwiki` 需要 `deepagents` 框架和多种 LangChain 依赖，RepoWiki 的零依赖设计大幅降低了安装门槛和安全审计成本。

**`_load_litellm()` 的设计精妙之处**：注释明确说明"litellm's import takes seconds (and can hang on 3.14), so zero-LLM paths like `repowiki map` must not pay it"。这是一个对 CLI 工具至关重要的性能优化——`repowiki map` 命令应该在毫秒级完成，而不是等待数秒导入 litellm。

#### 6.2 PageRank 阅读路径推荐

RepoWiki 的 PageRank 不只是排序工具——它是一个**阅读路径推荐系统**：

1. 基于真实 import 依赖图（而非字母序或文件大小）
2. 入度少的文件（入口点）排名高，被多文件引用的核心模块排名高
3. 阅读指南的 Step 1 → Step N 对应 PageRank 降序
4. 模块页面的"Entry"字段显示该模块的入口文件
5. 依赖页面的"Core Files"列出 PageRank 前 10 名

这一设计的精妙之处在于：PageRank 结果同时驱动了 `reading-guide`、`knowledge-cards` 的入口文件选择、`dependency` 页面的核心文件列表——同一个算法在三个不同页面复用。这避免了为不同场景维护多套排序逻辑。

#### 6.3 骨架提取替代截断

对于超过 4096 字符预算的 Python 文件，传统做法是截断前 N 行。RepoWiki 使用 `ast` 模块提取符号骨架：

```python
# 2000 行模块 → 结构而非头部
# [skeleton: 45 symbols from 2000 lines]
"""module docstring"""
class Foo:
    """docstring"""
    def bar(self, x: int) -> str:
        """docstring"""
class Baz:
    ...
# ... 40 more symbols omitted
```

这使得 LLM 能够看到模块的全局结构（类、函数、签名），而非仅仅前 4K 字符的内容。对大型模块的文档生成质量有显著提升。骨架提取仅适用于 Python 语言，其他语言仍使用简单的头部截断。

#### 6.4 诚实覆盖率报告

RepoWiki 在 wiki 中明确标注部分覆盖：

> **Partial coverage**: this wiki was built from 150 of 200 files. Oversized files left out: `bundle.js`. Excluded directories: `node_modules/`, `.git/`.

`ScanReport.partial` 属性自动计算 `kept < candidates`。这一设计避免了"wiki 看起来完整但实际上遗漏了大量文件"的问题。对于大型项目或受限扫描场景，这是重要的诚实性保障。

#### 6.5 导入感知排序

RepoWiki 的 `_SKIP_EXTS` 明确排除了 `.min.js`, `.min.css`, `.map`, `.wasm` 等压缩/生成文件，且 minified 检测通过行长度启发式实现（单行 > 1000 字符且非空行 ≤ 5 或最长行 > 文本长度 50%）。同时，文件排序优先级确保 config 和 entrypoint 文件在 LLM 上下文中优先出现——文档和资产文件在文件预算受限时首先被丢弃。

#### 6.6 GitHub 私有仓库支持

`ingest/github.py` 支持私有仓库克隆：
- `GITHUB_TOKEN` 或 `GH_TOKEN` 环境变量提供认证
- Token 仅用于 git clone 命令参数，从未写入日志或错误输出
- 显示 URL 始终为裸 URL（`https://github.com/owner/repo.git`），凭证在 argv 层面处理
- 浅克隆（`--depth 1 --single-branch`）减少克隆时间和磁盘使用
- 500MB 仓库大小上限防止克隆过大的仓库

---

### 7. 与 openwiki 的对比分析

| 维度 | RepoWiki (he-yufeng) | openwiki (langchain-ai) |
|------|---------------------|------------------------|
| Stars | 259 | 15.8k |
| Language | Python | TypeScript (95.6%) |
| Install | `pip install repowiki` | `npx openwiki` / npm |
| Database | SQLite (cache only) | 无持久化（内存） |
| Agent 架构 | 无 Agent，4 次 LLM 调用 | DeepAgents (Planner + Page Worker) |
| Claim 系统 | 无 | Grounded Claims (`repo://path#Lx-Ly`) |
| 增量更新 | `.repowiki-state.json` + SQLite cache | Git checkpoint 比较 |
| 文档格式 | Markdown + JSON + HTML + Site | OKF v0.2 YAML Front Matter |
| 图表 | Mermaid | Mermaid |
| 跨语言 | Python + 6 语言 import 解析 | TypeScript 为主 |
| 依赖数量 | ~8 核心依赖 | LangChain + DeepAgents + 多种 |
| 部署 | CLI + 可选 Web UI | CLI + IDE 集成 |
| 独特功能 | PageRank 阅读路径、骨架提取、终端 Q&A | Claim 溯源、双层 Agent、虚拟文件系统 |
| 安全规则 | 跳过 `.env` 等敏感文件 | 系统级禁止泄露 secrets |

**RepoWiki 的优势**：零依赖安装、Python 生态集成、PageRank 阅读路径、骨架提取、终端 Q&A、SQLite 持久化缓存、诚实覆盖率报告。

**RepoWiki 的差距**：无 Claim 溯源系统、无虚拟文件系统抽象、无 IDE 集成、技能系统缺失、AI Agent 编排能力弱于 openwiki、无版本控制、无自动刷新。

**对 Nop 平台的借鉴点**：RepoWiki 的增量缓存机制（内容哈希 + SQLite）、骨架提取（AST 解析替代截断）、PageRank 阅读路径、以及诚实覆盖率报告，都是可以直接移植到 Nop 平台文档自动化的成熟模式。

---

### 8. 配置与部署

#### 8.1 配置管理

`Config` 数据类（`config.py`，93行）管理所有运行时配置：

```python
@dataclass
class Config:
    model: str = "deepseek/deepseek-chat"
    api_key: str = ""
    api_base: str = ""
    language: str = "en"
    max_file_size: int = 200 * 1024  # 200 KB
    max_files: int = 1000
    output_dir: str = "./wiki"
    concurrency: int = 5
```

配置加载优先级：
1. CLI 标志（`-m`, `-l`, `-o`）
2. 环境变量（`REPOWIKI_MODEL`, `REPOWIKI_API_KEY`, `REPOWIKI_API_BASE`, `REPOWIKI_LANG`）
3. 配置文件（`~/.repowiki/config.json`）
4. Provider 特定 env vars（`DEEPSEEK_API_KEY`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`）

**模型别名**：`deepseek`, `claude`, `gpt`, `gpt-mini`, `gemini`, `gemini-flash`, `qwen`, `kimi`, `glm`, `minimax` 等 12 个快捷别名。`resolve_model()` 函数将别名映射为完整的模型标识符。

#### 8.2 部署方式

```bash
# 最小安装（CLI only）
pip install repowiki

# 完整安装（CLI + Web UI）
pip install repowiki[web]

# 开发安装
pip install -e ".[dev,web]"

# 从 GitHub 扫描私有仓库
GITHUB_TOKEN=ghp_xxx repowiki scan https://github.com/acme/private-repo

# 生成 GitHub Pages 站点
repowiki scan . --site

# 启动 Web 服务器
repowiki serve ./my-project --port 8000

# 终端 Q&A
repowiki chat .

# 零 LLM 成本的项目地图
repowiki map . --format json
```

**配置文件存储**：`~/.repowiki/config.json`，仅持久化非空值。`~/.repowiki/cache.db` 存储 LLM 缓存。`~/.repowiki/repos/` 存储克隆的 GitHub 仓库。`~/.repowiki/rag/` 存储 RAG 索引。

---

### 9. 局限性与改进空间

#### 9.1 当前局限

1. **无 Claim 溯源**：LLM 输出无源文件行号级证据引用，无法验证生成内容的准确性。与 openwiki 的 `repo://path#Lx-Ly` 格式相比，RepoWiki 的 wiki 页面虽然包含源文件链接，但不提供结构化的 Claim 验证机制
2. **无虚拟文件系统**：Agent 直接操作真实文件系统，缺乏统一命名空间抽象
3. **无增量 Web 索引**：Web 端 RAG 索引重建在冷启动时需全量 tokenize
4. **模块分组粗糙**：按顶层目录分组，对 `src/` 前缀的特殊处理仅覆盖 5 种常见目录名（`src`, `lib`, `pkg`, `internal`, `app`）
5. **前端功能有限**：无文件树可视化、无实时编辑、无搜索高亮
6. **无版本控制**：Wiki 生成无历史版本管理，无法 diff 不同运行间的变更
7. **无自动刷新**：需手动触发扫描或通过 git hooks/CI 集成
8. **骨架提取仅限 Python**：其他语言的大文件仍使用简单的头部截断
9. **单线程 PageRank**：`_pagerank_power_iteration()` 为纯 Python 实现，超大图的计算可能较慢

#### 9.2 路线图（来自 README）

- **More diagram types** — call graph 和 data-flow view
- 已有 roadmap 提到更丰富的图表类型，但尚未实现。分析阶段已经解析了导入关系，理论上可以生成调用图和数据流图。

---

## Conclusion

`he-yufeng/RepoWiki` 是一个设计精良的轻量级代码文档生成工具，其核心优势在于**零依赖安装**、**极简架构**和**PageRank 阅读路径推荐**。五阶段流水线（Scan → Graph → Analyze → Build → Export）职责清晰，SQLite 内容哈希缓存机制使增量重新扫描近乎免费。

**主要优势**：
- **零依赖安装**：`pip install repowiki` 即可运行，无 Docker、无数据库服务
- **增量缓存**：SQLite 内容哈希缓存使未变更模块零 API 调用，`.repowiki-state.json` 实现增量导出
- **PageRank 阅读路径**：基于真实 import 图的排序驱动阅读指南、卡片入口和核心文件列表
- **骨架提取**：AST 派生的符号骨架替代盲目截断，提升大模块分析质量
- **诚实覆盖率**：明确标注部分覆盖，避免误导性完整性声明
- **终端 Q&A**：`repowiki chat` 提供带 TF-IDF 检索的多轮终端对话
- **GitHub 私有仓库支持**：Token 安全处理，浅克隆优化
- **多语言支持**：6 种语言的 import 解析，4 种输出语言

**值得关注的方面**：
- 无 Claim 溯源系统，生成内容的准确性无法通过源文件行号验证
- 模块分组仅按顶层目录，对复杂项目结构（如 `src/` 前缀）的支持有限
- 前端功能较基础，缺乏文件树可视化和搜索功能
- 与 openwiki 的双层 Agent 编排和 Claim 系统相比，Agent 能力较弱
- 骨架提取仅限 Python 语言，其他语言大文件仍使用截断
- `litellm` 的引入带来了依赖管理复杂性（版本约束、多模型支持）

**对 Nop 平台的借鉴价值**：
- **增量缓存机制**（内容哈希 + SQLite + 状态文件）可直接移植到 Nop 文档自动化
- **骨架提取**（AST 解析替代截断）适用于 Nop 的大型 ORM 模型文件
- **PageRank 阅读路径**可用于 Nop 的模块文档导航
- **诚实覆盖率报告**可应用于 Nop 的文档生成质量保障
- **零依赖设计哲学**与 Nop 的模块化架构理念一致
- **延迟导入**（`_load_litellm()`）模式适用于 Nop 的按需加载场景
- **导入感知排序**（优先级排序替代字母序）适用于 Nop 的文档生成流程
- **Pydantic 数据模型贯穿管线**的模式与 Nop 的 IoC 容器设计思想一致

---

## Open Questions

- [ ] RepoWiki 在超大型代码库（10k+ 文件）中的性能表现如何？PageRank 计算和 4 次 LLM 调用的总耗时是多少？
- [ ] 无 Claim 溯源系统的 Wiki 如何保证生成内容的准确性？用户如何验证 LLM 输出？
- [ ] 模块分组按顶层目录的策略在 `src/` 前缀项目中（如 Python 项目 `src/myapp/`）是否有效？当前特殊处理仅覆盖 5 种目录名
- [ ] `repowiki chat` 的 TF-IDF 检索在中文场景下的效果如何？bigram 分词是否足够？
- [ ] 前端 Web UI 的完整功能规划是什么？是否有文件树、搜索、实时编辑等计划？
- [ ] 与 Nop 平台的文档自动化能力相比，RepoWiki 在增量更新和缓存机制方面有哪些可以直接移植的模式？
- [ ] `litellm` 的引入是否带来了额外的安全审计负担？如何管理 100+ 模型提供者的 API 密钥轮换？
- [ ] 骨架提取仅限 Python 语言，是否计划扩展至 Java/Kotlin/Go/Rust 等其他语言？
- [ ] PageRank 在超过 1000 个文件的图中计算时间是否会显著增加？是否需要异步或近似算法优化？

---

## References

- `https://github.com/he-yufeng/RepoWiki` — 项目主页
- `ai-dev/analysis/deepwiki-survey/01-langchain-openwiki.md` — openwiki 对比分析
- `docs-for-ai/02-core-guides/service-layer.md` — Nop 服务层架构参考
- `docs-for-ai/02-core-guides/model-first-development.md` — Nop ORM 模型开发参考
- `docs-for-ai/02-core-guides/ioc-and-config.md` — Nop IoC 配置参考
- `ai-dev/analysis/00-analysis-writing-guide.md` — 分析文档写作指南
