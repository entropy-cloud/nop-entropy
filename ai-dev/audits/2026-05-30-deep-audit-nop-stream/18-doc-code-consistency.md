# 维度 18：文档-代码一致性

## P2 发现

| 编号 | 文件 | 摘要 |
|------|------|------|
| 18-01 | ai-dev/design/nop-stream/connector-design.md:186 | DrainableSource 签名与代码完全不同（有参 vs 无参、有返回值 vs void、extends SourceFunction） |
| 18-05 | docs-for-ai/01-repo-map/module-groups.md 等 | docs-for-ai 对 nop-stream 零路由覆盖（无 INDEX 条目、无 module-groups 条目、无 source-anchors） |

## P3 发现

| 编号 | 文件 | 摘要 |
|------|------|------|
| 18-02 | core-design.md:170 | WindowOperatorFactory 不存在于代码 |
| 18-03 | architecture.md:76-78 | core→api 依赖方向描述与空壳 api 不符 |
| 18-04 | connector-design.md:289 | IBatchChunkContext null 限制已过时（代码已修复） |
| 18-06 | architecture.md:411 | IEvalFunction 归属错误标注为 nop-xlang（实际在 nop-core） |
| 18-07 | design README.md + architecture.md | "五层"列举 6 项（nop-stream/README.md 正确列举 5 项） |
| 18-08 | component-roadmap.md:280 | runtime→cep ghost dependency 标注"未修复"，实际已修复 |
| 18-09 | core-design.md:33-44 | StreamComponents 强类型参数 vs 实际 Map<String, Object> |

## 正面确认

- error-handling.md 对异常类继承的描述准确 ✓
- nop-stream/README.md 模块列表和状态标注准确 ✓
- architecture.md 分布式执行组件与代码对齐 ✓
- connector-design.md 5 个连接器适配器与代码对齐 ✓

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 18-01 | P2 | connector-design.md | DrainableSource 签名不匹配 |
| 18-05 | P2 | docs-for-ai/ | 零路由覆盖 |
| 18-02~18-04 | P3 | 设计文档 | 过时描述 (3项) |
| 18-06~18-09 | P3 | 设计文档 | 文档错误 (4项) |
