# PageIndex 无向量推理检索与提示注入防护分析 & Nop AI Agent Memory/安全

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/pageindex`（Python，无向量推理 RAG，84 文件）vs `nop-ai-agent`（memory 包 + security）
> Conclusion:

## 一、总览与机制
PageIndex 完全无向量、无分块，用 **LLM 推理 + 结构化树索引**替代相似度搜索，检索可追溯可解释。核心：**两步式推理检索**（先为长文档生成层级树索引，再 LLM tree search，`page_index.py` 1320 行）；**Flash 确定性引擎**（pdfium 字符级解析→heading detection→outline assembly→tree building，无需 LLM，`flash/`）；**Agentic 工具检索**（get_document/get_document_structure/get_page_content 三工具，OpenAI Agents SDK 驱动）；**提示注入三重防护**（`_sanitize_doc_text` 正则脱敏 + `_wrap_doc_text` 分隔符包裹 + `_SYSTEM_HARDENING` 系统提示，`page_index.py:12-49`）。

## 二、对 nop-ai-agent 的借鉴要点
1. **树检索模式（结构→内容两步）**（中价值，memory 包）——先检索记忆索引树再精确取内容，作为向量检索的补充策略（memory 适配器增加"树索引"实现）。
2. **提示注入三重防护**（高价值）——`_wrap_doc_text` + `_sanitize_doc_text` + system hardening 三重，对应 nop AgentSession 消息流注入用户内容时的安全加固（与 trustgraph 来源元数据 `2026-08-01-trustgraph-context-graph-analysis.md`、gstack 分层防御 `2026-08-01-gstack-sprint-workflow-analysis.md` 呼应）。
3. **确定性 Flash 管线**（中价值）——无 LLM 的纯规则树构建，嵌入适配器可支持"无嵌入、规则索引"模式。

## 三、结论
PageIndex 的提示注入三重防护与无向量树检索是 nop memory/安全的参考。局限：PDF 导向、高质量树构建依赖云端 OCR、tree building 延迟较高。

## References
- `~/ai/pageindex/page_index.py`、`flash/`、`retrieve.py`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-memory.md`、`nop-ai-agent-security.md`
- `ai-dev/analysis/agent-survey/2026-08-01-trustgraph-context-graph-analysis.md`、`2026-08-01-gstack-sprint-workflow-analysis.md`
