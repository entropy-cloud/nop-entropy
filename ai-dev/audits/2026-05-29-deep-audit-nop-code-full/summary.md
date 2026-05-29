# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-code（多语言代码索引与智能分析服务）
- **审核日期**: 2026-05-29
- **执行维度**: 全部 21 个维度
- **目标范围**: nop-code 下全部 13 个子模块（core、graph、flow、lang-java/python/typescript、codegen、dao、meta、service、web、app、api）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01 依赖图 | 1 | 3 | 0 | 3 | 0 | 0 |
| 02 模块职责 | 1 | 5 | 0 | 5 | 0 | 0 |
| 03 API 表面积 | 1 | 3 | 0 | 3 | 0 | 0 |
| 04 ORM 模型 | 1 | 17 | 0 | 17 | 0 | 0 |
| 05 生成管线 | 1 | 1 | 0 | 1 | 0 | 0 |
| 06 Delta 定制 | 1 | 0 | 0 | 0 | 0 | 0 |
| 07 BizModel | 1 | 5 | 0 | 5 | 0 | 0 |
| 08 IoC 配置 | 1 | 1 | 0 | 1 | 0 | 0 |
| 09 错误处理 | 1 | 2 | 0 | 2 | 0 | 0 |
| 10 XDSL/XLang | 1 | 1 | 0 | 1 | 0 | 0 |
| 11 XMeta 对齐 | 1 | 0 | 0 | 0 | 0 | 0 |
| 12 GraphQL API | 1 | 0 | 0 | 0 | 0 | 0 |
| 13 安全权限 | 1 | 3 | 0 | 3 | 0 | 0 |
| 14 异步事务 | 1 | 6 | 0 | 6 | 0 | 0 |
| 15 类型安全 | 1 | 2 | 0 | 2 | 0 | 0 |
| 16 测试覆盖 | 1 | 3 | 0 | 3 | 0 | 0 |
| 17 代码风格 | 1 | 2 | 0 | 2 | 0 | 0 |
| 18 文档一致 | 1 | 3 | 0 | 3 | 0 | 0 |
| 19 命名一致 | 1 | 1 | 0 | 1 | 0 | 0 |
| 20 跨模块契约 | 1 | 2 | 0 | 2 | 0 | 0 |
| 21 测试有效性 | 1 | 4 | 0 | 4 | 0 | 0 |
| **合计** | **21** | **63** | **0** | **63** | **0** | **0** |

注：本轮为初审+总结报告，未执行深挖追加轮次和独立复核。所有发现的复核状态为"未复核"。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 5 | 上帝类(02)、ORM逻辑删除失效(04)、全局锁(14)、超长事务(14)、内存泄漏(14)、核心测试缺失(16) |
| P2 | 22 | ORM 模型缺陷、安全防护不足、事务/缓存问题、API 契约漂移、测试有效性 |
| P3 | 36 | 代码风格、命名一致性、文档缺失、低优先级改进 |
| 无问题 | 5 | 维度 06/11/12 + 多个通过项 |

## 关键发现摘要

### P1 发现

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 02-01 | CodeIndexService.java | 3005 行上帝类，8 类职责混合 |
| 04-05 | nop-code.orm.xml | NopCodeSemanticEdge 有 delFlag 但未声明 useLogicalDelete，逻辑删除失效 |
| 14-01 | CodeIndexService.java | synchronized 全局锁导致多项目串行化 |
| 14-02 | CodeIndexService.java | deleteIndex 超长事务（11 步级联删除） |
| 14-03 | CodeIndexService.java | analysisCacheMap 无界增长，OOM 风险 |
| 16-01 | TestCodeIndexService.java | 3006 行核心服务无直接单元测试 |

### P2 发现

| 编号 | 文件 | 一句话摘要 |
|------|------|-----------|
| 04-01 | nop-code.orm.xml | 所有实体缺少审计属性声明 |
| 04-02 | nop-code.orm.xml | 审计字段命名不一致（createdTime vs createTime） |
| 04-04 | nop-code.orm.xml | NopCodeSemanticEdge 关系缺少 refPropName |
| 04-07 | nop-code.orm.xml | NopCodeFlow.memberships 缺少 cascadeDelete |
| 04-13 | nop-code.orm.xml | NopCodeIndex 9 表级联删除性能风险 |
| 04-15 | nop-code.orm.xml | caller/callee 的 refDisplayName 语义反转 |
| 07-02 | CodeIndexService.java | saveFileResultInSession 260 行方法 |
| 07-03 | CodeIndexService.java | deleteIndex 手动逐表删除未利用 ORM 级联 |
| 08-01 | lang-* 模块 | 三个 lang 模块缺少 _module 文件 |
| 09-01 | NopCodeErrors.java | 6 个 ErrorCode 使用中文消息 |
| 09-02 | GraphExporter.java | 使用 IllegalArgumentException 而非 NopException |
| 13-01 | NopCodeIndexBizModel | 无细粒度权限注解 |
| 13-02 | CodeIndexService | validatePath 仅检查 .. 子串 |
| 13-03 | NopCodeIndexBizModel | indexFile 无 sourceCode 大小限制 |
| 14-05 | CodeIndexService | findReferencedBy N+1 查询 |
| 14-06 | CodeIndexService | SymbolTable/CallGraph 全量加载 |
| 15-01 | CodeIndexService | findImplementations 使用 String[] 传混合类型 |
| 15-02 | CodeIndexService | 双括号初始化 QueryBean |
| 16-02 | TestNopCodeFlowBizModel | 核心功能 assumeTrue 静默跳过 |
| 18-01 | module-groups.md | nop-code-api 文档描述与实际不符 |
| 20-01 | ICodeIndexService | 接口和 DTO 应在 api 模块而非 service |
| 21-01 | TestBuildHierarchyCycleProtection | 测试 Java 标准库而非项目代码 |
| 21-04 | TestCodeIndexService | 全部 happy path 无错误路径测试 |

## 总评

nop-code 是一个功能丰富的 WIP 实验模块，核心设计意图清晰（多语言代码索引 + 图分析 + 执行流追踪），模块分层整体合规（无循环依赖、计算模块隔离良好）。

**主要优势**:
1. 依赖图清晰：core→graph→flow→lang-*→service→web→app 的分层方向正确
2. 生成管线完整：model→codegen→dao→meta→service→web 链路闭合
3. XDSL/XLang 层面健康：schema 引用、extends 链路、beans 配置均正确
4. BizModel 基础合规：11/11 通过继承和注解检查
5. 核心算法（社区检测、影响分析、死代码检测、调用层次）有合理的测试覆盖

**需关注的关键风险**:
1. **CodeIndexService 上帝类**：3005 行、70+ 方法、8 类职责，是最高的重构优先级
2. **运行时稳定性**：全局 synchronized 锁、无界缓存、超长事务三者叠加，在多项目长期运行场景下可能导致服务不可用
3. **ORM 模型缺陷**：NopCodeSemanticEdge 的逻辑删除失效、caller/callee refDisplayName 反转是确定的 bug
4. **安全防护不足**：路径校验简单、输入无大小限制、无细粒度权限控制
5. **API 分层漂移**：ICodeIndexService 在 service 而非 api 模块，I*Biz 接口空壳

## 优先修复建议

### 第一优先级（P1，影响正确性或运行时稳定性）

1. **修复 NopCodeSemanticEdge 逻辑删除**：添加 `deleteFlagProp="delFlag" useLogicalDelete="true"`（04-05，一行改动）
2. **修复 caller/callee refDisplayName 反转**：交换两个标签（04-15，一行改动）
3. **将 analysisCacheMap 改为有界缓存**：使用 Caffeine 替代 ConcurrentHashMap（14-03）
4. **将 synchronized 改为按 indexId 粒度的锁**（14-01）
5. **重构 deleteIndex 使用数据库级联删除或分步事务**（14-02）

### 第二优先级（P2，架构和安全性改进）

6. **拆分 CodeIndexService**：按职责域拆分为独立服务类（02-01）
7. **为 CodeIndexService 增加单元测试**（16-01）
8. **将 ICodeIndexService 和 DTOs 迁移到 nop-code-api**（20-01）
9. **添加 sourceCode 大小限制**（13-03）
10. **增强路径校验**（13-02）
11. **中文错误消息改英文**（09-01）
12. **为高危操作添加 @BizPermission**（13-01）

### 第三优先级（P3，代码质量改善）

13. 修复审计字段命名（04-02）
14. 补充 cascadeDelete（04-07）
15. 清理空壳模块和文件（02-04、02-05）
16. 提取测试公共基类（16-03）
17. 补充设计文档和锚点（18-02、18-03）

## 本次审核盲区自评

1. **未执行深挖追加轮次**：所有维度仅执行了 1 轮初审。对 CodeIndexService 的 70+ 方法逐行审计可能发现更多问题。
2. **未执行独立复核**：所有发现的复核状态为"未复核"，可能存在误报或过度判级。
3. **未运行测试基线**：未实际执行 `./mvnw test -pl nop-code`，测试报告基线缺失。
4. **未深度审计生成模板**：codegen 模板的正确性依赖模板内容，本次仅检查了生成管线配置。
5. **运行时行为未验证**：synchronized 的实际竞争情况、analysisCacheMap 的实际内存占用等需要运行时数据确认。
