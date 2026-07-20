# 维度 13：安全与权限模型 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度13-01] BizModel 自定义方法缺少细粒度权限注解（P2）

- **文件**: 所有 `entity/*BizModel.java`
- **说明**: `testConnection`, `syncExternalTables`, `collectCatalog`, `executeCheckpoint` 等 @BizMutation 方法没有 @Auth 注解，仅受通用 :mutation 权限保护。获得 mutation 权限的用户可调用所有自定义方法。
- **严重程度**: P2

### [维度13-02] DataAuth 覆盖率不足且外部直查路径不受限（P2）

- **文件**: `nop-metadata-service/auth/nop-metadata.data-auth.xml`
- **说明**: 仅 3 个实体有行级权限。NopMetaQualityRule（含 custom_sql）、NopMetaTable（含 sourceSql）等实体无行级保护。外部数据源直查路径（withConnection）完全绕过 ORM data-auth。
- **严重程度**: P2

### [维度13-03] connectionConfig 的 xmeta 写入侧未完全受限（P3）

- **文件**: `NopMetaDataSource/NopMetaDataSource.xmeta`
- **说明**: 留存层仅设置 published="false"，未设置 insertable="false" 和 updatable="false"。GraphQL mutation 理论上可写入 connectionConfig。
- **严重程度**: P3

### 防御机制亮点

- ExpressionMeasureValidator 分词级 SQL 安全检查
- custom_sql 关键字黑名单覆盖多语句/UNION/DDL/DCL
- Fail-closed data-auth 设计（null userId 不匹配任何行）
- Webhook host allowlist + 可配置超时（SSRF 防护）
- JDBC 凭据脱敏链路完整（tagSet → published=false → 变更事件 → 测试）
