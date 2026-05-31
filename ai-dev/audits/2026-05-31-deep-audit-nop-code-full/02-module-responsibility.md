# 审核维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService.java 1551 行，严重违反 SRP

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **证据片段**: 混合了索引操作、查询委派(~20个getXxx方法)、ORM持久化(~360行实体创建L730-L1110)、Entity-Model转换、子服务生命周期管理、验证等7类职责
- **严重程度**: P2
- **现状**: 1551行，混合了至少7类职责。虽然已拆分出 CodeQueryService/CodeGraphService/CodeSearchService，但 ORM 持久化和构造器初始化仍内嵌。
- **风险**: 维护困难，单点故障风险高。
- **建议**: 将 ORM 持久化提取为 CodePersistenceService，实体-模型转换提取为 CodeEntityMapper。
- **信心水平**: 95%
- **误报排除**: 已确认 CodeQueryService/CodeGraphService 已拆出，但剩余职责仍然过多。
- **复核状态**: 未复核

### 合规项

- 手写代码未混入 _gen/ 目录
- _ 前缀生成文件未被手写修改
- dao 模块边界清晰（仅 ORM 实体/DAO 接口/I*Biz）
- service 模块边界基本清晰
- CommunityDetector(891行)、ProjectAnalyzer(861行)、JavaFileAnalyzer(771行) 虽大但职责聚焦
