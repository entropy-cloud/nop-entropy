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

**存储格式裁定（D1，plan 0900-1，2026-07-16）**：
- `schema` 列：存储 **JSON Schema 文档**（`domain="mediumtext"` + `stdDomain="json"`，**不用 json-4000**——宽表 JSON Schema 易超 4KB，对齐 Manifest.content/Catalog.details 先例）。首版仅存储不执行逐行校验（运行时逐行校验为 Non-Blocking Follow-up）。
- `sla` 列：**结构化 JSON**（`domain="json-4000"` + `stdDomain="json"`），约定键：
  - `refreshFrequency`：`{interval, unit}`（采集新鲜度，对应 NopMetaCatalog.collectedAt）
  - `maxLatency`：`{value, unit}`（数据延迟，对应 NopMetaCatalog.lastModified）
  - `retention`：`{period, unit}`（保留期）
  - 未知键保留不报错（前向兼容）。
- `latestResult` 列：`mediumtext` + `stdDomain="json"`（**不用 json-4000**，因 qualitySummary.details 含每规则 message，多规则易超 4KB，对齐 Manifest.content/Catalog.details 先例）。
- **裁定理由（拒绝自定义 DSL）**：JSON Schema / 结构化 JSON 无需额外解析器、与平台 JSON 列原生对齐、可被 AI/外部工具直接消费；自定义 DSL 增加学习与维护成本无收益。

**status 枚举收口（D1）**：§2.3 旧表述 status 为 3 值（draft/active/deprecated）与状态流转图 4 值（含 retired）自相矛盾。本裁定**统一为 4 值大写**（对齐平台 dict 惯例 + dict `meta/contract-status`）：`DRAFT / ACTIVE / DEPRECATED / RETIRED`。删除旧 3 值/小写表述。

> **已知限制（v1，来自 §2.3.2 Catalog）**：`NopMetaCatalog.lastModified` 在 v1 **恒为 null**（`MetaCatalogCollector` 始终 `setLastModified(null)` + `markUnavailable`，方言特定降级策略）。故 `sla.maxLatency ↔ lastModified` 路径在 v1 恒判 `unknown`、`dataStale` 永不触发——SLA 判定在 v1 实际只由 `refreshFrequency ↔ collectedAt` 驱动。`maxLatency` 路径为前向就绪（未来 Catalog 扩展填充 lastModified 后自动生效）。

```
MetaDataContract                  — 数据契约
  ├── contractName                — "用户数据契约"
  ├── displayName                 — "User Data Contract"
  ├── entityTableId               → MetaTable.metaTableId（关联的数据资产，plain string 列 + to-one relation）
  ├── status                      — "DRAFT" | "ACTIVE" | "DEPRECATED" | "RETIRED"（dict meta/contract-status，大写）
  ├── ownerUserId                 — 契约所有者（domain="userId"，precision=50）
  ├── schema                      — JSON Schema 文档（mediumtext + stdDomain json，首版仅存储不执行逐行校验）
  ├── sla                         — 结构化 JSON（json-4000 + stdDomain json），约定键 refreshFrequency/maxLatency/retention
  ├── qualityExpectations         — 结构化 JSON（json-4000），形状钉死见 §5.2 D2：{"qualityRuleIds":["<ruleId>",...]}
  ├── security                    — JSON 安全策略（json-4000）
  ├── latestResult                — JSON 最新执行结果（mediumtext + stdDomain json），结构钉死见 §5.2 D2
  ├── tagSet
  └── extConfig
```

**契约状态流转（D1 收口 4 值）**：
```
DRAFT → ACTIVE → DEPRECATED → RETIRED
```
合法前置状态：`DRAFT→ACTIVE`、`ACTIVE→DEPRECATED`、`DEPRECATED→RETIRED`。非法流转（如 `DRAFT→RETIRED`、`RETIRED→*`、已 `RETIRED` 再流转）**显式失败抛 ErrorCode**（不静默跳过、不静默改状态）。`checkContract` 不受 status 阻断（DRAFT 可预检）。

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

**契约检查语义裁定（D2，plan 0900-1，2026-07-16，钉死数据结构与算法）**：
- **action 名统一 `checkContract`**（同步更新本节 GraphQL 示例，废弃旧名 `executeDataContractCheck`）。
- **`qualityExpectations` 的 JSON 形状（钉死）**：`{"qualityRuleIds": ["<ruleId1>", "<ruleId2>", ...]}`（裸字符串数组，key 固定 `qualityRuleIds`）。空数组或缺 key 视为"无质量检查项"。
- **质量路径**：对 `qualityRuleIds` 中每个 ruleId 取 `NopMetaQualityResult` 按 `executeTime desc` 最新一条（取不到记为 `no-result`）；汇总 `qualitySummary = {totalRules, passedRules, failedRules, noResultRules, details:[{ruleId, latestStatus, message}]}`（`latestStatus` 取 QualityResult.status 原值，无结果记 `no-result`）。
- **SLA 路径（算法钉死）**：以 `entityTableId` 之值作为 `metaTableId` 取 `NopMetaCatalog` 按 `collectedAt desc` 最新一条（无 Catalog 记 `catalogAvailable=false`）：
  - `refreshFrequency`（若存在）：判定 `now - catalog.collectedAt > refreshFrequency` → `collectionStale=true`（采集过期）；
  - `maxLatency`（若存在）：判定 `now - catalog.lastModified > maxLatency` → `dataStale=true`（数据过期，lastModified 为空则该项记 `unknown` 不判定——v1 恒走此分支）；
  - `slaFresh = !collectionStale && !dataStale`；`slaSummary = {catalogAvailable, collectedAt, lastModified, collectionStale, dataStale, slaFresh}`。时间单位归一为毫秒比较。
- **混合 status 归并规则（钉死）**：
  - 若 `qualityRuleIds` 为空且 `sla` 为空 → `status=ERROR`，message="契约无可检查项"（**不静默 pass**）。
  - 否则按优先级：`ERROR`（任一被引用 ruleId 在 QualityRule 表不存在 / Catalog 解析异常 / JSON 解析失败）> `FAIL`（任一质量 latestStatus=FAIL，或 `slaFresh=false`）> `PASS`（所有可判定项通过）。
  - 即：SLA stale 或 质量 FAIL 任一成立 → `FAIL`；全部通过 → `PASS`。
- **汇总写回**：`latestResult`(mediumtext+stdDomain json: `{timestamp, status, message, qualitySummary, slaSummary}`)。
- **失败路径显式**：契约不存在抛 ErrorCode（不 NPE）；JSON 解析失败 / ruleId 不存在 / 无可检查项 均显式失败或 status=ERROR + 明确 message（不吞异常、不静默 pass）。详见下 §5.2 行为契约。

```graphql
# 创建数据契约（标准 CRUD，由 CrudBizModel 自动暴露，无需手写 mutation）
mutation SaveDataContract($data: NopMetaDataContract__save__input!) {
  NopMetaDataContract__save(data: $data) {
    contractId
    contractName
    status
  }
}

# 执行契约检查（自定义 @BizMutation action，action 名 checkContract）
mutation CheckContract($contractId: ID!) {
  NopMetaDataContract__checkContract(contractId: $contractId) {
    timestamp
    status
    message
  }
}
```

---

## 六、待定问题

- [ ] 通用 Domain 的来源：是单独维护还是从现有 ORM 模型提取？
- [x] ~~数据契约的 SLA 定义格式：JSON Schema vs 自定义 DSL？~~ **已裁定（P4-4，2026-07-16）**：`schema` 列存 JSON Schema 文档（mediumtext + stdDomain json），`sla` 列存结构化 JSON（json-4000 + stdDomain json，约定键 refreshFrequency/maxLatency/retention）。拒绝自定义 DSL（详见 §2.3 D1 裁定）。检查语义（D2）钉死于 §5.2。
- [ ] 域定义的 UI 编辑方式：表单编辑 vs SQL 导入？
