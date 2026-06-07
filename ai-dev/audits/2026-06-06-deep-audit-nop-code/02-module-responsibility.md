# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService.java 承担过多持久化职责（1932 行）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:752-1361`
- **证据片段**:
  ```java
  // 第 752-813 行 — persistInSession: 将 ProjectAnalysisResult 持久化为 11 种 ORM 实体
  private void persistInSession(String indexId, String rootPath, ProjectAnalysisResult result,
                                IOrmSession session) { ... }

  // 第 1052-1361 行 — saveFileResultInSession: 单文件持久化约 310 行
  private void saveFileResultInSession(String indexId, CodeFileAnalysisResult file,
                                       IOrmSession session) { ... }
  ```
- **严重程度**: P3
- **现状**: CodeIndexService 共 1932 行，约 610 行是 ORM 持久化逻辑。已部分分解（查询→CodeQueryService，图分析→CodeGraphService），但持久化未被提取。
- **风险**: 持久化逻辑难以独立测试；修改实体字段映射需在 1932 行文件中定位。
- **建议**: 将持久化方法提取为独立的 CodeIndexPersistence 类。
- **信心水平**: 高
- **误报排除**: CommunityDetector(938行)、JavaFileAnalyzer(915行)等大文件职责单一。CodeIndexService 混合了协调和持久化两个关注点。
- **复核状态**: 未复核

## 其他检查项（无问题）

- 子模块职责单一性：dao(ORM实体), api(DTO), core(模型/接口), service(BizModel), meta(xmeta), graph/flow/lang-*(各自算法) — 全部合规
- _gen 目录无手写代码
- _前缀生成文件无手写修改痕迹
- 保留文件（NopCodeSymbol.java 等）均为标准空壳扩展
