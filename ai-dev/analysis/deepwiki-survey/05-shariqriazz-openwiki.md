# shariqriazz/openwiki — Deep Analysis

**Project**: [shariqriazz/openwiki](https://github.com/shariqriazz/openwiki)
**Language**: Rust (10 crates modular architecture)
**Stars**: ~0 (but high technical quality)
**Description**: MCP-native AI code Wiki generator, Tree-sitter + Tantivy
**Date**: 2026-09-23

---

## 1. Overview

OpenWiki is a Rust-based, AI-powered code documentation generator that automatically produces structured Wiki pages for software repositories. It combines Tree-sitter syntax-aware parsing with Tantivy hybrid search and multi-backend LLM integration. The project is organized as a workspace of 10 crates, each responsible for a distinct concern, and exposes its functionality through three interfaces: a CLI binary, an Axum HTTP server with SSE streaming, and an MCP (Model Context Protocol) endpoint.

The project's core value proposition is that it transforms raw source code into navigable, searchable, and LLM-annotated documentation without requiring manual authoring. Unlike tools like Doxygen or Sphinx that rely on existing doc comments, OpenWiki synthesizes documentation from code structure, cross-file relationships, and LLM-generated summaries. Its architecture is notable for the tight integration between its indexing pipeline and its search/generation pipeline, all orchestrated through a SQLite-backed persistent store.

---

## 2. Modular Architecture — 10 Crates

The workspace is decomposed into 10 semantically aligned crates, each encapsulating a single responsibility. This decomposition follows the principle of bounded contexts from domain-driven design, where each crate owns its data model and business logic.

### 2.1 `openwiki-core`
The foundation crate. Defines shared types (`FileInfo`, `Chunk`, `Edge`), configuration structures, and centralized error handling via `NopException`-style error codes. This crate has no external dependencies beyond the Rust standard library and foundational traits, making it the most stable and reusable component. All other crates depend on `openwiki-core` for its types and error abstractions.

### 2.2 `openwiki-store`
Persistence layer built on `rusqlite`. Manages SQLite storage for `FileInfo`, `Chunk`, `Edge`, and Wiki page entities. The store abstracts all database interactions behind a clean API, enabling other crates to perform CRUD operations without direct SQL exposure. SQLite was chosen for its zero-configuration, embedded nature, and strong transactional guarantees, which are sufficient for the repository-scale data volumes OpenWiki targets.

### 2.3 `openwiki-graph`
Constructs and manages the file dependency graph. It extracts import relationships between files and produces Mermaid diagram representations. The graph structure is fundamental to cross-file analysis, enabling the system to understand how modules interrelate and to generate architecture-overview documentation that reflects actual code dependencies rather than inferred ones.

### 2.4 `openwiki-indexer`
The most complex crate. Implements the full indexing pipeline: Tree-sitter parsing, chunking, scoring, deduplication, cross-file chunk synthesis, and LLM summarization. It coordinates with `openwiki-store` for persistence and `openwiki-llm` for summarization. The indexer is the engine that transforms raw source code into structured, searchable content.

### 2.5 `openwiki-llm`
Multi-backend LLM client supporting OpenAI, Anthropic, Gemini, Ollama, and OpenRouter. Provides a unified interface for chat completions, embedding generation, and structured output. The abstraction layer allows swapping backends without affecting downstream code. Configuration is managed through the core crate, and the LLM client handles authentication, request formatting, and response parsing per provider.

### 2.6 `openwiki-search`
Full-text and vector hybrid search using Tantivy. Implements Reciprocal Rank Fusion (RRF) to combine results from the full-text index and the vector similarity index. The `HybridSearchEngine` is the primary retrieval mechanism during wiki generation, ensuring that both keyword-matched and semantically relevant chunks are surfaced. Query expansion via LLM further improves retrieval quality.

### 2.7 `openwiki-wiki`
Wiki page planning and generation logic. Contains the `planner.rs` module that determines the structure of the Wiki (which pages to create and their content outline), and the `generator.rs` module that produces the actual Markdown content. Manages citation tracking, cross-page chunk deduplication, and the interaction between retrieval and LLM generation.

### 2.8 `openwiki-github`
GitHub repository integration. Handles repository cloning and webhook processing. This crate enables OpenWiki to automatically index repositories hosted on GitHub and to trigger re-indexing on code changes via webhook events, supporting continuous documentation workflows.

### 2.9 `openwiki-server`
Axum-based HTTP server providing REST API, SSE (Server-Sent Events) streaming, and the MCP endpoint. The SSE implementation enables real-time streaming of wiki generation progress and search results. The MCP `/mcp` endpoint exposes the system as a Model Context Protocol server, allowing AI assistants to interact with the Wiki directly through standardized tool calls.

### 2.10 `openwiki-cli`
The CLI binary with 18 subcommands. Provides the primary user interface for indexing repositories, generating Wiki pages, searching, and managing configurations. The CLI delegates to the server or internal modules depending on the subcommand, making it both a standalone tool and a development interface.

---

## 3. Processing Pipeline — Phase 1: Indexing

The indexing pipeline is the heart of OpenWiki, transforming a directory of source code into structured, searchable, and summarized content. It proceeds through a series of deterministic steps, each producing artifacts that feed into the next.

### 3.1 Step 1a — Directory Walking (`walker.rs`)

The pipeline begins with a directory walker that respects `.gitignore` rules and applies hard-coded exclusions for common build artifacts (`node_modules`, `target`, `dist`, `build`, `vendor`). Files are screened for binary content using `content_inspector` and skipped if they exceed 2MB. This pre-filtering ensures that the Tree-sitter parser only encounters textual source files, avoiding parse failures and excessive memory consumption. The walker produces a list of `FileInfo` objects containing the file path, detected language, and content hash.

### 3.2 Step 1b — Tree-sitter Parsing & Chunking (`chunker.rs`)

This is the most technically sophisticated step. For each source file, a Tree-sitter `Parser` is instantiated for the detected language. OpenWiki supports Rust, Go, TypeScript, JavaScript, Python, Java, C, C++, JSON, TOML, YAML, and Markdown. The parser walks the AST to identify "classifiable" nodes — language-specific syntactic constructs that represent meaningful code units.

The classification mapping is language-aware:
- **Function** nodes include `function_item`, `function_declaration`, `method_declaration`, and `arrow_function`
- **Class** nodes include `class_declaration` and `class`
- **Struct** nodes include `struct_item` and `struct_specifier`
- **Interface** nodes include `interface_declaration` and `type_alias_declaration`
- **Module** nodes include `mod_item` and `module`
- **Import** nodes include `use_declaration` and `import_statement`
- **Enum** nodes are classified as `Class` via `enum_item` and `enum_declaration`

Each classified node becomes a `Chunk` with a `ChunkKind` variant. The chunk content includes the node's source text, any preceding doc comments (`///` or `/**`), and metadata (file path, line range, symbol name). For files where no classifiable nodes are found, a fallback line-window chunking strategy produces 50-line windows with 10-line overlap, ensuring no code is left unindexed.

### 3.3 Step 1c — Import Extraction (`imports.rs`)

Language-specific logic extracts `Edge` objects representing import relationships. Each edge has a source file and a target file, forming the basis of the dependency graph. These edges are critical for the `fan_in_bonus` scoring factor and for cross-file chunk synthesis.

### 3.4 Step 1d — Scoring (`scoring.rs`)

Each chunk receives an `importance_score` computed from multiple weighted factors:
- **kind_weight**: Functions and classes carry higher base weights than imports
- **path_bonus**: Files in `src/lib` or `src/core` paths receive a bonus
- **symbol_bonus**: Publicly exported symbols are weighted higher
- **fan_in_bonus**: Chunks from files with many incoming imports score higher

This scoring ensures that the most architecturally significant code units are prioritized during retrieval and summarization.

### 3.5 Step 1e — Deduplication (`dedup.rs`)

Content is hashed using BLAKE3, and near-duplicate chunks are identified via Jaccard similarity. This prevents the same code pattern from consuming multiple slots in the vector store and inflating search noise. The deduplication threshold is configurable, allowing fine-grained control over uniqueness strictness.

### 3.6 Step 1f — Cross-file Chunks (`cross_file.rs`)

Mutual import pairs produce synthetic chunks that document cross-file relationships. These chunks are not derived from AST nodes but are generated artifacts that capture the semantic relationship between files. They are essential for producing architecture-overview documentation that explains how modules interact.

### 3.7 Step 1g — Storage

All artifacts — `FileInfo`, `Chunk`, `Edge` — are persisted to SQLite via `openwiki-store`. The storage schema is optimized for the access patterns of the search and generation phases, with indexes on file paths, chunk IDs, and embedding vectors.

### 3.8 Step 1h — LLM Summarization (`summarizer.rs`)

Chunks are batched in groups of 10 and sent to the LLM backend with a maximum context of 2048 tokens and temperature 0.0 (deterministic output). The prompt instructs the LLM to act as "a concise code documentation assistant," producing a summary that captures the chunk's purpose and interface. These summaries are stored alongside the chunks and serve as the primary content for semantic retrieval.

### 3.9 Step 1i — Tantivy Full-text Index

A Tantivy index is built over chunk content, enabling keyword-based full-text search. The index includes fields for file path, symbol name, summary, and raw content, supporting multi-field query strategies.

### 3.10 Step 1j — Embeddings + Vector Store

Chunk embeddings are generated via the configured LLM backend and stored in a vector store. The embedding text format follows a structured template:

```
File: {path}
Symbol: {symbol} ({kind})
Summary: {summary}
---
{content}
```

This format ensures that the embedding captures both metadata and content, improving retrieval quality for both semantic and keyword queries.

### 3.11 Incremental Indexing (`incremental.rs`)

The system supports incremental re-indexing by comparing content hashes. Files whose hashes have changed since the last indexing are re-processed, avoiding full re-indexing costs. This makes OpenWiki practical for active development workflows where files change frequently.

---

## 4. Processing Pipeline — Phase 2: Wiki Generation

### 4.1 Step 2a — Wiki Planning (`planner.rs`)

The planner determines the structure of the output Wiki. It first checks for `.openwiki/wiki.json`, a custom configuration file that allows users to override default page definitions. If no custom config exists, the system uses LLM-driven planning in two phases:

- **Phase 1 — Analysis**: The LLM analyzes the indexed codebase and produces a summary of its architecture, key modules, and relationships.
- **Phase 2 — Planning**: Based on the analysis, the LLM generates 8-15 page definitions, each with a title, outline, and target content scope.

If the LLM planning fails or the repository is too small, a fallback `auto_plan` produces 6 fixed pages covering common documentation needs (Architecture, Getting Started, API Reference, etc.). The `should_replan` condition triggers when more than 20% of files have changed since the last generation, ensuring the Wiki stays current without unnecessary regeneration.

### 4.2 Step 2b — Wiki Generation (`generator.rs`)

For each planned page, the generator performs the following:

**Semantic Retrieval**: The `HybridSearchEngine` retrieves relevant chunks using RRF with `K=60`, combining full-text and vector search results. This ensures that both keyword-matched and semantically related chunks are available.

**Code Context Building**: The `build_code_context()` function assembles the prompt context with a weighted distribution: 60% of the most relevant chunks, 20% structural chunks (imports, modules), and 20% remaining chunks. This distribution balances relevance, structural completeness, and diversity.

**Generation Strategy**: If the total chunk count is below 50, a single-turn generation is used. For larger codebases (>50 chunks), a multi-turn strategy is employed where an initial LLM call produces an outline, and subsequent calls fill in each section. This approach manages context window limits while maintaining coherence across the document.

**Citations**: Every factual claim in the generated Wiki is annotated with `[file_path:line_start-line_end]` citations pointing to the source chunk IDs. This creates a verifiable link between the documentation and the codebase.

**Cross-page Dedup**: Chunks used in one page are tracked and excluded from other pages where possible, preventing redundant content and ensuring each piece of information appears in the most appropriate context.

---

## 5. Phase 3: Search & Q&A

### 5.1 Hybrid Search Engine

The `HybridSearchEngine` is the retrieval backbone, implementing Reciprocal Rank Fusion (RRF) to combine Tantivy full-text scores with vector cosine similarity scores. RRF produces a unified ranking that respects both lexical matching and semantic similarity, outperforming either approach alone for code documentation queries.

Query expansion via LLM transforms user questions into optimized search queries before retrieval, improving recall for ambiguous or multi-faceted questions.

### 5.2 Ask Interface

The `ask` endpoint streams responses via SSE, emitting citation events that associate each generated answer segment with its source chunks. This enables the web UI to display inline citations and allow users to verify the provenance of AI-generated answers.

---

## 6. Extraction Templates and Prompts

OpenWiki uses a sophisticated prompt engineering strategy to extract structured documentation from code. The key templates are:

### 6.1 Chunking Template (`chunker.rs`)
Tree-sitter AST classification with fallback line-window chunking ensures comprehensive coverage across all code constructs.

### 6.2 Chunk Summarization Prompt
The LLM is prompted as "a concise code documentation assistant" to produce short, factual summaries of each chunk. Temperature 0.0 ensures deterministic, reproducible summaries.

### 6.3 Wiki Page Generation Prompt (`prompts.rs`)
Generates Markdown with inline code blocks, Mermaid diagrams for architecture visualization, and structured citations. The prompt instructs the LLM to adopt the voice of a technical documentation writer.

### 6.4 Architecture Overview Prompt (`overview_prompt`)
A specialized prompt for generating high-level architecture overviews, instructing the LLM to act as a technical doc writer who understands software architecture patterns.

### 6.5 Outline/Section Prompts
For multi-turn generation, specialized prompts guide the LLM through outlining each section and then expanding it into full content.

### 6.6 LLM Wiki Planning Prompts (`planner.rs`)
Two-phase prompts for analysis and planning, enabling the LLM to first understand the codebase and then produce a structured documentation plan.

### 6.7 Chunk Embedding Text Format
The structured embedding format ensures that semantic search captures both metadata (file path, symbol, kind) and content, improving retrieval precision.

### 6.8 Cross-file Chunk Content
Synthetic chunks documenting cross-file relationships, enabling the LLM to generate architecture documentation that reflects actual code dependencies.

---

## 7. MCP Protocol Integration

OpenWiki exposes an MCP (Model Context Protocol) endpoint at `/mcp` using JSON-RPC 2.0. This allows AI assistants (such as Claude, GPT-based tools, or any MCP-compatible client) to interact with the Wiki programmatically.

Available tools:
- **`read_wiki_structure`**: Retrieves the page hierarchy and outlines
- **`read_wiki_contents`**: Fetches the full content of a specific page
- **`ask_question`**: Submits a question and receives an AI-generated answer with citations
- **`list_repos`**: Lists all indexed repositories
- **`search_code`**: Performs hybrid search across indexed code
- **`get_file_content`**: Retrieves the source content of a specific file
- **`regenerate_page`**: Triggers regeneration of a specific Wiki page

This MCP integration transforms OpenWiki from a documentation generator into an interactive knowledge base that AI assistants can query and manipulate, closing the loop between code and documentation in a way that is natively accessible to modern AI workflows.

---

## 8. Key Technical Decisions and Trade-offs

### 8.1 Rust over Go/TypeScript
Rust provides memory safety and zero-cost abstractions critical for the parsing and indexing pipeline, where performance and determinism matter. The choice aligns with the project's emphasis on technical quality and maintainability.

### 8.2 SQLite over PostgreSQL
SQLite's embedded nature eliminates deployment complexity. For the repository-scale data volumes OpenWiki targets, SQLite's performance is more than adequate, and its transactional guarantees ensure data integrity during concurrent access from the CLI and server.

### 8.3 Tantivy over Elasticsearch
Tantivy is a pure Rust full-text search library, avoiding the operational overhead of a separate search service. For the document volumes involved, Tantivy provides sufficient performance while keeping the architecture simple and self-contained.

### 8.4 Tree-sitter over Regex-based Parsing
Tree-sitter provides language-aware parsing that is robust to syntax variations and capable of identifying precise code constructs. This is essential for accurate chunking and citation generation, where line-level precision is required.

### 8.5 RRF over Weighted Fusion
Reciprocal Rank Fusion is a well-established method for combining heterogeneous ranking signals. Its parameter-free nature makes it simpler to tune than weighted-sum approaches, and it performs well across diverse query types.

---

## 9. Comparison with Nop Platform (`nop-code`)

OpenWiki and Nop's `nop-code` module share overlapping concerns in code documentation and AI-assisted development, but differ significantly in approach:

| Dimension | OpenWiki | Nop `nop-code` |
|-----------|----------|----------------|
| Language | Rust | Java |
| Parsing | Tree-sitter (external) | Source-code generated |
| Search | Tantivy (Rust native) | Custom Java search |
| Storage | SQLite (rusqlite) | Spring Data JPA |
| LLM Integration | Multi-backend client | Nop AiAgent |
| Deployment | Binary + HTTP server | Maven artifact |
| MCP Support | Native endpoint | Via Nop AI infrastructure |
| Wiki Generation | LLM-driven, citation-based | Template-driven |
| Architecture | 10 independent crates | Monolithic module |

OpenWiki's strength lies in its language-agnostic parsing and its self-contained architecture, making it a standalone tool that can index any codebase regardless of the host language. Nop's `nop-code` benefits from deep integration with the Nop IoC container and the broader platform ecosystem, but is inherently tied to Java projects and the Nop framework.

OpenWiki's MCP endpoint provides a more modern and accessible interface for AI assistants compared to Nop's internal agent infrastructure, though Nop's approach offers tighter coupling with the platform's authentication, configuration, and deployment systems.

---

## 10. Limitations and Risks

1. **LLM Dependency**: The system relies heavily on LLM backends for summarization, planning, and generation. Without a configured LLM, the indexing pipeline degrades to raw code indexing without summaries or Wiki generation.

2. **Embedding Quality**: The embedding text format is fixed and may not capture all semantic nuances. Complex code patterns or domain-specific abstractions may not be well-represented in the vector space.

3. **SQLite Concurrency**: While sufficient for most use cases, SQLite's write contention could become a bottleneck during concurrent indexing operations from the CLI and server.

4. **Tree-sitter Language Coverage**: The supported languages are limited to 12. Languages without Tree-sitter grammars cannot be indexed, though the fallback line-window chunking provides partial coverage.

5. **Small Ecosystem**: With ~0 stars, the project has limited community testing, documentation, and contribution, which may affect long-term maintenance and feature velocity.

---

## 11. Strategic Insights for Nop Platform

OpenWiki's architecture offers several lessons for the Nop platform:

1. **Modular Crate Design**: The 10-crate decomposition demonstrates a clean separation of concerns that could inform a future Rust-based component of Nop, particularly for performance-sensitive indexing or parsing tasks.

2. **Hybrid Search Pattern**: The RRF-based combination of full-text and vector search is a pattern that Nop could adopt in its `nop-code` module to improve code retrieval quality.

3. **MCP Protocol Exposure**: The MCP endpoint pattern is a forward-looking design that aligns with the emerging standard for AI-tool interoperability. Nop could benefit from exposing its AI capabilities through MCP as well.

4. **Citation-Driven Generation**: OpenWiki's approach to annotating AI-generated content with source citations addresses a critical trust issue in AI-assisted documentation. Nop's `nop-code` could adopt a similar citation mechanism.

5. **Incremental Indexing**: The content-hash-based incremental re-indexing strategy is a practical optimization that Nop could implement for its code indexing pipelines.

---

*End of analysis*
