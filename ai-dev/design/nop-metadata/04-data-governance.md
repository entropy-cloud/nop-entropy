# nop-metadata 数据治理设计

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 数据治理能力（域定义、分类、血缘、质量、数据契约）
> Goal: 定义数据治理层面的元数据模型，覆盖 metadata-survey 中的关键治理模式

---

## 一、设计决策

### 1.1 Domain 定义归属模块

**决策**: Domain 定义（MetaDomain）归属模块，跟随模块版本管理。

**理由**:
- Domain 是模块级别的约定（如 "amount", "date", "status"）
- 通用 Domain 可以被其他模块引用/拷贝，但每个模块有自己的副本
- 版本管理需要 Domain 跟随模块

### 1.2 通用 Domain 与引用拷贝

**决策**: 支持通用 Domain 的引用和拷贝机制。

**机制**:
```
通用 Domain 池（Global Domain）
  ├── nop-common-domain: amount, date, status, uuid, ...
  └── nop-finance-domain: currency, exchange_rate, ...

模块引用/拷贝:
  nop-auth: 使用通用 Domain（amount → 拷贝为自己的 MetaDomain）
  nop-erp-fin: 使用通用 Domain + 自定义 Domain（account_code）
```

### 1.3 数据治理范围

| 治理能力 | 来源平台 | nop-metadata 策略 |
|---------|----------|-------------------|
| 域定义 (Domain) | OpenMetadata | MetaDomain 归属模块，支持通用引用 |
| 分类/标签 (Classification) | Atlas Classification | extConfig + tagSet 已满足 |
| 血缘 (Lineage) | DataHub/OpenMetadata/Marquez | MetaLineageEdge + MetaPipeline 已设计 |
| 质量 (Quality) | OpenMetadata TestCase | MetaQualityRule + MetaQualityResult 已设计 |
| 数据契约 (Contract) | OpenMetadata ODCS | MetaDataContract（新增） |
| 访问控制 (Access) | Nop RBAC | 复用 nop-auth，无需新设计 |

---

## 二、核心模型

### 2.1 MetaDomain（域定义）

域定义描述字段的语义类型（如 "amount", "date", "status"），用于跨表的语义对齐。

```
MetaDomain                        — 域定义
  ├── modelId                     → MetaOrmModel（所属模型，即所属模块版本）
  ├── isDelta                     — true: 本模块声明, false: 合并后
  ├── domainName                  — "amount"（唯一标识）
  ├── displayName                 — "金额"
  ├── description                 — 域的语义描述
  ├── stdDataType                 — "DECIMAL" | "STRING" | "INTEGER" | ...
  ├── stdSqlType                  — "VARCHAR" | "INT" | "BIGINT" | ...
  ├── precision                   — 精度
  ├── scale                       — 小数位
  ├── validationPattern           — 校验正则（可选）
  ├── defaultValue                — 默认值表达式（可选）
  ├── isGlobal                    — 是否为通用域（可被其他模块引用）
  ├── sourceModuleId              — 来源模块（拷贝时追溯原始定义）
  ├── tagSet                      — 标签集合
  └── extConfig                   — 扩展属性 JSON
```

**域的层级**:
```
通用域（isGlobal=true）
  ├── nop-common: amount, date, status, uuid, email, phone, ...
  └── nop-finance: currency, exchange_rate, account_code, ...

模块域（isGlobal=false）
  ├── nop-auth: user_role, permission_scope
  └── nop-erp-fin: journal_type, posting_status
```

### 2.2 MetaDict（字典定义）

字典定义描述字段的枚举值集合，跟随模块版本管理。

```
MetaDict                          — 字典定义
  ├── modelId                     → MetaOrmModel
  ├── isDelta                     — true: 本模块声明, false: 合并后
  ├── dictName                    — "DocStatus"（唯一标识）
  ├── displayName                 — "单据状态"
  ├── valueType                   — "string" | "int"
  ├── static                      — 是否静态（不可运行时修改）
  ├── normalized                  — 是否归一化
  ├── tagSet
  ├── isGlobal                    — 是否为通用字典
  ├── sourceModuleId              — 来源模块
  ├── deprecated                  — 是否已废弃
  ├── internal                    — 是否内部使用
  └── extConfig

MetaDictItem                      — 字典项
  ├── dictId                      → MetaDict
  ├── itemValue                   — "DRAFT"
  ├── itemLabel                   — "草稿"
  ├── itemCode                    — "01"（可选编码）
  ├── group                       — 分组（可选）
  ├── description                 — 描述
  ├── sortOrder                   — 排序
  ├── deprecated                  — 是否已废弃
  ├── internal                    — 是否内部使用
  └── extConfig
```

### 2.3 MetaDataContract（数据契约）

数据契约描述数据资产的 SLA、质量承诺和访问策略。参考 OpenMetadata ODCS 3.1。

```
MetaDataContract                  — 数据契约
  ├── contractName                — "用户数据契约"
  ├── displayName                 — "User Data Contract"
  ├── entityTableId               → MetaTable（关联的数据资产）
  ├── status                      — "draft" | "active" | "deprecated"
  ├── ownerUserId                 — 契约所有者
  ├── schema                      — JSON Schema 定义（字段约束）
  ├── sla                         — JSON SLA 定义
  │   ├── refreshFrequency        — {"interval": 1, "unit": "day"}
  │   ├── maxLatency              — {"value": 4, "unit": "hour"}
  │   └── retention               — {"period": 90, "unit": "day"}
  ├── qualityExpectations         — JSON 质量期望（引用 MetaQualityRule）
  ├── security                    — JSON 安全策略
  │   ├── dataClassification      — "Confidential" | "PII" | "Public"
  │   └── accessPolicy            — 访问策略
  ├── latestResult                — JSON 最新执行结果
  │   ├── timestamp               — 执行时间
  │   ├── status                  — "pass" | "fail" | "error"
  │   └── message                 — 结果描述
  ├── tagSet
  └── extConfig
```

**契约状态流转**:
```
draft → active → deprecated → retired
```

### 2.4 MetaLineageEdge（血缘边）

已在 01-architecture-baseline.md §2.5 中设计，此处补充数据治理视角：

```
MetaLineageEdge
  ├── sourceTableId / targetTableId → MetaTable
  ├── sourceColumn / targetColumn   — 列级血缘（可选）
  ├── transformType                 — "direct" | "derived" | "aggregated"
  ├── transformExpression           — 转换表达式
  ├── lineageSource                 — "manual" | "sql_parse" | "open_lineage" | "hook"
  ├── pipelineId                    → MetaPipeline
  ├── confidence                    — 置信度 0.0~1.0
  └── extConfig
```

**血缘治理用途**:
- 影响分析：列级变更影响范围
- 数据溯源：追踪数据来源
- 合规审计：敏感数据流转路径

### 2.5 MetaQualityRule（质量规则）

已在 01-architecture-baseline.md §2.6 中设计，此处补充治理视角：

```
MetaQualityRule
  ├── ruleName / ruleType / entityType / entityId
  ├── severity                     — "error" | "warning" | "info"
  ├── sqlExpression / threshold / params
  └── extConfig
```

**质量治理用途**:
- 数据质量评分（通过率）
- 趋势监控（最近 N 天）
- 异常告警（连续失败）

---

## 三、Domain 跨模块引用机制

### 3.1 通用 Domain 注册

通用域（`isGlobal=true`）存储在"公共模块"中，可被其他模块引用：

```
MetaModule (nop-common, released)
  └── MetaOrmModel
      └── MetaDomain[]
          ├── domainName="amount", isGlobal=true
          ├── domainName="date", isGlobal=true
          └── domainName="status", isGlobal=true
```

### 3.2 模块引用/拷贝

模块使用通用 Domain 时，有两种策略：

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| **引用** | `sourceModuleId` 指向来源模块，不复制数据 | 需要实时同步通用域变更 |
| **拷贝** | 复制 Domain 到本模块，`sourceModuleId` 记录来源 | 需要版本隔离，允许本地覆盖 |

**建议**: 采用**拷贝**策略。理由：
1. 版本隔离：模块版本发布后不可变，通用域变更不应影响已发布模块
2. 本地覆盖：模块可以覆盖通用域的某些属性（如精度、校验规则）
3. 追溯能力：`sourceModuleId` 保留来源追溯

### 3.3 Domain 继承规则

```
nop-common: amount (precision=10, scale=2)
    ↓ 拷贝
nop-erp-fin: amount (precision=12, scale=4)  ← 本地覆盖精度
```

**继承/覆盖规则**:
- 默认继承通用域的所有属性
- 模块可以覆盖：precision, scale, validationPattern, defaultValue
- 不可覆盖：domainName（唯一标识）, stdDataType（语义类型）

---

## 四、与 metadata-survey 的对比

### 4.1 已覆盖的治理能力

| 能力 | nop-metadata | DataHub | OpenMetadata | Atlas | Marquez |
|------|-------------|---------|-------------|-------|---------|
| 域定义 | MetaDomain | ❌ | ❌ | ❌ | ❌ |
| 字典 | MetaDict | ❌ | ❌ | ❌ | ❌ |
| 血缘 | MetaLineageEdge | ✅ 关系遍历 | ✅ DAG | ✅ Process | ✅ CTE |
| 质量 | MetaQualityRule | ❌ | ✅ TestCase | ❌ | ❌ |
| 分类/标签 | extConfig+tagSet | ✅ GlobalTags | ✅ Tags | ✅ Classification | ✅ Tags |
| 访问控制 | nop-auth RBAC | ✅ DNF | ✅ RBAC | ✅ Ranger | ❌ |

### 4.2 补充的治理能力

| 能力 | 来源平台 | nop-metadata 实现 |
|------|----------|-------------------|
| 数据契约 | OpenMetadata ODCS | MetaDataContract |
| 质量测试框架 | OpenMetadata TestCase | MetaQualityRule 已有，可扩展 |
| 域跨模块引用 | 无先例 | sourceModuleId + 拷贝策略 |

---

## 五、数据治理 API

### 5.1 Domain 管理

```graphql
# 查询模块的 Domain 定义
query Domains($moduleId: String!) {
  metaDomains(moduleId: $moduleId) {
    domainName
    displayName
    stdDataType
    isGlobal
    sourceModuleId
  }
}

# 查询通用 Domain 池
query GlobalDomains {
  metaDomains(isGlobal: true) {
    domainName
    displayName
    sourceModuleId
  }
}
```

### 5.2 数据契约管理

```graphql
# 创建数据契约
mutation CreateDataContract($input: CreateDataContractInput!) {
  createDataContract(input: $input) {
    contractName
    status
  }
}

# 执行契约检查
mutation ExecuteDataContractCheck($contractId: ID!) {
  executeDataContractCheck(contractId: $contractId) {
    timestamp
    status
    message
  }
}
```

---

## 六、待定问题

- [ ] 通用 Domain 的来源：是单独维护还是从现有 ORM 模型提取？
- [ ] 数据契约的 SLA 定义格式：JSON Schema vs 自定义 DSL？
- [ ] 域定义的 UI 编辑方式：表单编辑 vs SQL 导入？
