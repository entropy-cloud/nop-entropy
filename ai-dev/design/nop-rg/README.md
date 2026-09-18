# nop-rg Design Docs

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

nop-rg 是 Nop 平台的高性能文件搜索工具，提供内容搜索（grep）和文件名模式匹配（glob）能力，目标是在 JVM 上尽可能逼近原生 ripgrep 的性能。

## 阅读顺序

1. `00-vision.md` — Vision 层：产品定位、成功标准、非目标
2. `01-architecture-baseline.md` — Architecture Baseline 层：分层、模块边界、核心契约、降级策略

## 与 nop-search 的边界

| | nop-rg | nop-search |
|---|---|---|
| **用途** | 文件系统内容扫描（grep/glob） | 全文检索引擎（BM25/向量语义搜索） |
| **数据源** | 实时扫描文件系统 | 预构建索引 |
| **典型用户** | 开发者命令行工具、CI/CD 管线 | 应用内搜索功能 |
| **依赖** | 无重量级依赖 | Lucene |
