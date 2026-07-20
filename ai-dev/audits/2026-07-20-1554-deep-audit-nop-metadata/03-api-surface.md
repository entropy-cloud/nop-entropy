# 维度 03：API 表面积与契约一致性 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度03-01] 3 个公开 @BizMutation 方法未在 I*Biz 接口上声明（P1）

- **文件**: `INopMetaReconciliationConfigBiz.java:9`, `INopMetaReconciliationResultBiz.java:9`
- **说明**: `executeReconciliation`, `confirmMatch`, `batchConfirmMatches` 三个 @BizMutation 方法未在对应 I*Biz 接口声明，跨模块调用方无法通过代理调用。
- **严重程度**: P1

### [维度03-02] 4 个 I*Biz 查询方法缺少 IServiceContext 参数（P2）

- **文件**: `INopMetaLineageEdgeBiz.java:37-49`
- **说明**: `getUpstream`, `getDownstream`, `getLineagePath`, `getImpactAnalysis` 缺少 IServiceContext context 末参。
- **严重程度**: P2

### [维度03-03] 21 个方法返回 Map<String,Object> 而非类型安全结构（P2）

- **说明**: 21 个自定义方法（跨 8 个 BizModel）返回 Map<String,Object>，绕过 xmeta 字段可见性控制。
- **严重程度**: P2
