# 维度 20：跨模块契约一致性 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度20-01] nop-sys-dao 编译依赖 — 对等模块的不必要耦合（P2）

- **文件**: `nop-metadata-service/pom.xml:54`
- **说明**: nop-metadata-service 在 compile 范围依赖 `nop-sys-dao`，零 Java 引用。创建了跨模块耦合：若 nop-sys 开始依赖 nop-metadata，存在双向依赖风险。
- **严重程度**: P2

### 合规确认

- nop-job-api 依赖设计良好（compile 接口 + test scope 实现）✅
- nop-http-api 依赖按预期使用 ✅
- 模块版本在 BOM 中全部对齐 2.0.0-SNAPSHOT ✅
