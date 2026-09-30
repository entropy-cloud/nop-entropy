# nop-batch DeepWiki

> 目标：nop-batch · 结构契约：[PLAN.md](./PLAN.md) · 本文件由 gen-wiki-meta.mjs 生成，勿手改

```mermaid
mindmap
  root((nop-batch))
    指南
      nop-batch 总览：chunked 批处理框架
      快速上手
      术语表
      阅读指南
      架构与数据流
    机制
      chunk 管线：数据如何从 Loader 流向 Consumer
      断点续跑与记录：失败后从哪里再来
    模块
      核心执行引擎
      DSL 模型与 XLang 集成
    主题
      错误模型：BatchErrors 与取消链
```

> 快照：nop-batch @ 555f7a9731 · 2026-09-30

## 1. 指南

- 1.1 [nop-batch 总览：chunked 批处理框架](overview.md)
- 1.2 [快速上手](quickstart.md)
- 1.3 [术语表](glossary.md)
- 1.4 [阅读指南](reading-guide.md)
- 1.5 [架构与数据流](architecture.md)

## 2. 机制

- 2.1 [chunk 管线：数据如何从 Loader 流向 Consumer](flows/batch-pipeline.md)
- 2.2 [断点续跑与记录：失败后从哪里再来](flows/checkpoint-recovery.md)

## 3. 模块

- 3.1 [核心执行引擎](modules/batch-core.md)
- 3.2 [DSL 模型与 XLang 集成](modules/batch-dsl.md)

## 4. 主题

- 4.1 [错误模型：BatchErrors 与取消链](topics/error-model.md)
