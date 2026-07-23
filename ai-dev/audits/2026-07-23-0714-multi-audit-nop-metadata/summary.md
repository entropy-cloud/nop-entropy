# 深度审核汇总报告

> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

## 基本信息

- **审核模块**: nop-metadata
- **审核日期**: 2026-07-23
- **执行维度**: 01(依赖图), 04(ORM模型), 05(生成管线), 07(BizModel), 09(错误处理), 11(XMeta对齐), 16(测试质量)
- **目标范围**: nop-metadata/ 全部子模块 — 代码, 配置, 测试, 公共契约

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|------|------|------|
| 01 | 1 | 5 | 待复核 | 待复核 | 待复核 |
| 04 | 1 | 7 | 待复核 | 待复核 | 待复核 |
| 05 | 1 | 13(含8信息性) | 待复核 | 待复核 | 待复核 |
| 07 | 1 | 8 | 待复核 | 待复核 | 待复核 |
| 09 | 1 | 7 | 待复核 | 待复核 | 待复核 |
| 11 | 1 | 6 | 待复核 | 待复核 | 待复核 |
| 16 | 1 | 10 | 待复核 | 待复核 | 待复核 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P1 | 1 | 依赖图违规 (dao→core) |
| P2 | 14 | ORM约束缺失、数据权限绕过、代码生成管线gap、ErrorCode规范、XMeta保护缺失、测试覆盖 |
| P3 | 21 | 命名不一致、异常类型不一致、测试反模式、代码质量 |
| P4 | 1 | icon重复 |

## 关键发现摘要

### P1 发现
- **[维度01-01]** nop-metadata-dao 编译期依赖 nop-metadata-core，违反分层 Rule #2

### P2 发现
- **[维度04-01]** NopMetaTableJoin 双FK系统缺少声明式约束强制的互斥性
- **[维度04-02]** 跨模块 dict 引用 wf/approve-status 创建硬运行时依赖
- **[维度04-03]** NopMetaDataContract FK列名 entityTableId 命名与所有其他实体不一致
- **[维度05-08]** CRUD API 代码生成被有意禁用，所有 BizModel 手写
- **[维度07-01]** NopMetaQualityCheckpointBizModel.delete 覆盖缺少 @Name("id") 注解
- **[维度07-02]** NopMetaDataContractBizModel 5个mutation方法绕过 requireEntity() 数据鉴权
- **[维度09-01]** NopMetadataException 缺少 String 构造器
- **[维度09-07]** ErrorCode 命名系统性使用连字符替代点号作为子域分隔符
- **[维度11-01]** NopMetaDataSource connectionConfigComponent 未随 parent 一起限制
- **[维度11-04]** computeQualityScore 绕过 xmeta insertable 验证
- **[维度11-06]** sourceSql/buildSql 敏感度未标记用于事件脱敏
- **[维度16-01]** 最小 AutoTest 快照覆盖（82个测试文件中仅1个）
- **[维度16-04]** 4个 BizModel 接口方法缺少测试覆盖
- **[维度16-07]** 数据鉴权测试绕过框架执行验证

### P3 发现（精选）
- **[维度01-02]** service 缺少对 -api 的依赖
- **[维度07-03]** NopMetaTagLabelBizModel 使用 BeanContainer.getBeanByType() 服务定位器
- **[维度07-04]** AutoClassificationService / LineageTagPropagationService 违反命名规范
- **[维度09-05]** 广泛使用内联字符串键代替 ARG_* 常量
- **[维度16-03]** 重复 CRUD 测试反模式

## 总评

nop-metadata 模块是一个结构规范、拥有良好测试覆盖的质量模块。审核范围涵盖7个维度，共发现36项问题（1项P1，14项P2，21项P3/P4）。

**优势：**
- 无循环依赖，模块 DAG 严格无环
- ORM 模型结构良好：39个实体质量规范，displayName 已本地化（中英文）
- 生成管线完整：model→dao→meta→web 管线在所有层次保持 39:39:39:39 实体一致性
- BizModel 实现整体遵循平台规范：42个 @BizModel 都使用正确继承和 setEntityName()
- 测试覆盖丰富：82个测试文件、~450-500个测试方法，含安全测试（SSRF、数据鉴权、连接安全）
- 错误处理整体遵循两档策略，无裸 RuntimeException，所有 throw 使用 ErrorCode

**核心改进项（按优先级）：**
1. **P1**: 解决 dao→core 编译期依赖（维度01-01）
2. **P2**: 修复 NopMetaDataContractBizModel 数据鉴权绕过（维度07-02）
3. **P2**: 为 NopMetadataException 添加 String 构造器（维度09-01）
4. **P2**: 统一 ErrorCode 命名分隔符（维度09-07）
5. **P2**: 增加敏感字段 xmeta 保护（维度11-01, 维度11-06）
6. **P2**: 增加关键 BizModel 方法的 AutoTest 快照覆盖（维度16-01）
7. **P2**: 为4个未覆盖的 BizModel 方法添加测试（维度16-04）

## 优先修复建议

| 优先级 | 维度 | ID | 简述 |
|--------|------|----|------|
| 最高 | 01 | 01-01 | 解决 dao→core 依赖违规 - 将 DTO 移出 core 或调整分层规则 |
| 高 | 07 | 07-02 | 修复 NopMetaDataContractBizModel requireEntity() 绕过 |
| 高 | 09 | 09-01 | 添加 NopMetadataException String 构造器 |
| 高 | 09 | 09-07 | 统一 ErrorCode 命名分隔符（连字符→点号或更新文档） |
| 高 | 11 | 11-01 | 限制 connectionConfigComponent 与 connectionConfig 一致 |
| 高 | 11 | 11-06 | 标记 sourceSql/buildSql 为 sensitive |
| 中 | 16 | 16-01 | 启用 AutoTest 快照覆盖关键 BizModel |
| 中 | 16 | 16-04 | 为4个未覆盖方法添加测试 |

## 本次审核盲区自评

- 未执行实际 Maven 构建以验证编译和测试是否通过（基线命令未运行）
- 所有发现未经独立复核（复核状态均为"待复核"）
- 仅覆盖7个维度，尚有14个维度未审计
- 未进行 deep-dive 追加轮次（初审即停，未深挖关联问题）
- 未检查 xbiz 文件（*.xbiz 在 nop-metadata-service 中的内容）
- 未检查 web 模块的页面资源
- 未检查部署配置和 app 模块
