# nop-ai-rag 空模块定位（P3-MA3-003 裁定）

**日期**：2026-08-01
**范围**：`nop-ai/nop-ai-rag`（空模块：仅 pom.xml，0 Java 文件、0 依赖、0 资源）
**状态**：active（裁定已落地）
**相关裁定**：P3-MA3-003（MA3.1 审计，第九批承接）、P1-MA5-003（SPI 扩展点契约，MV 裁定）、`ai-dev/plans/2026-08-01-0206-2-arm-p2-tools-structure-residual.md` Phase 3

---

## 一、设计结论

**裁定 = 保留空模块 + 文档化为 SPI 预期实现落点（不补实现、不从父 pom 移除）**。

1. `nop-ai-rag` 保持为 `nop-ai/pom.xml` modules 中的占位模块，职责声明为 **IVectorStore / IEmbeddingModel 的预期实现落点**（模块 README 已写明）。
2. 不新增 InMemory 最小实现（零消费方，投机代码 = 空壳风险）。
3. 不从父 pom 移除（结构声明价值 + 未来实现成本考量，见 §三）。

## 二、背景与动机（live 引用面）

| 事实 | 证据 |
|------|------|
| 父 pom modules 引用 | `nop-ai/pom.xml:35` `<module>nop-ai-rag</module>` |
| 无其他依赖方 | 全仓 grep `nop-ai-rag` 仅命中 nop-ai/pom.xml 与本模块 pom（不含 target/_dump） |
| 无源码/依赖/资源 | 模块目录仅 pom.xml + target（构建产物） |
| SPI 定位 | P1-MA5-003 裁定：IVectorStore/IEmbeddingModel 为 SPI 扩展点契约，"平台无生产实现属设计意图，集成方提供实现"；MA5.1/MA5.2 审计将其排除在空壳扫描外（"预期实现模块"） |

## 三、为什么拒绝另外两个选项

**拒绝 (b) 从父 pom 移除**：

1. 移除 = 丢失"RAG 实现放这里"的结构声明；未来实现 RAG 需重建模块并改父 pom（churn）。
2. 空模块构建开销可忽略（一个 pom，无编译）。
3. audit 建议原文即为"若模块是 future placeholder，加 README 说明意图"——保留 + 文档化是 audit 认可的第一方案。

**拒绝 (c) 补最小 InMemory 实现**：

1. 当前零消费方；无消费者需求时补实现 = 投机代码（Anti-Hollow：没有调用链的实现比没有实现更危险——开发者的维护成本 + 虚假完成感）。
2. P1-MA5-003 裁定明确"无生产实现属设计意图"；InMemory 实现会打破该契约语义（平台承诺的默认实现反而掩盖 SPI 边界）。
3. 接线验证要求（容器测试断言 bean 注册）在无消费方时无意义。

## 四、使用契约

- **模块职责**：RAG 检索增强的向量存储/Embedding 集成实现落点；未来代码只实现 nop-ai-core 的 SPI 契约（`IVectorStore` / `IEmbeddingModel`）。
- **集成方**：需要向量检索的应用自行提供实现（本模块或自有实现模块），SPI 契约见 nop-ai-core 对应接口 javadoc。
- **迁移触发条件**：出现第一个真实 RAG 消费方时，在本模块落 InMemory 或存储后端实现。

## 五、与已有设计的关系

- 上游：P3-MA3-003 审计记录（`ai-dev/audits/2026-07-31-0753-arm-MA3.1-nop-ai-cross-module-deps.md`）。
- 相关：P1-MA5-003 SPI 裁定（`ai-dev/audits/arm-index.md` §P1 可追溯性矩阵）。
- 追踪：`ai-dev/audits/arm-index.md` §P3 追踪（第九批）P3-MA3-003 行。
