# 维度 04：ORM 模型与实体设计

## 检查范围

以下是对 nop-stream 模块 ORM 模型文件的完整搜索路径和模式:

| 搜索模式 | 搜索路径 |
|----------|----------|
| `**/*.orm.xml` | `nop-stream/` |
| `**/*.orm.*` | `nop-stream/` |
| `**/_*.orm.xml` | `nop-stream/` |
| `**/model/*.orm.xml` | `nop-stream/` |
| `**/resources/**/*.xml` | `nop-stream/` |
| `find -name "*orm*"` | `nop-stream/` |

逐子模块检查:
- `nop-stream-core/model/` — Java 模型类 (StreamModel, StreamComponents)
- `nop-stream-cep/model/` — CEP 模式 Java 类 (CepPatternModel 等)
- `nop-stream-fraud-example/model/` — POJO 类 (TransactionEvent, FraudAlert)
- `nop-stream-runtime/resources/` — 仅 META-INF
- `nop-stream-connector/resources/` — 空
- `nop-stream-api, checkpoint, flink, flow` — 空占位模块

## 结论

nop-stream 是一个纯计算引擎框架，处理流式数据管道和 CEP。其 model 目录下的文件均为 Java 代码定义的引擎内部模型，而非持久化到数据库的 ORM 实体模型。

**零发现。**
