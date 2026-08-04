# 审计维度矩阵（nop-metadata）

> **M0.1 交付物** — mission `nop-metadata-audit-remediation` 的 M0.1 工作项（按 `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md` 步骤 1 生成）。
> 状态：done
> 最后更新：2026-08-04
> 来源：三个来源（A：已有 skill 维度 24；B：残留风险新维度 6；C：元数据域特有风险维度 6），合计 36 行维度 × 8 子模块列。
> 口径：nop-metadata 8 子模块（api/core/codegen/dao/meta/service/web/app）；实体 39（`grep -c "<entity" model/nop-metadata.orm.xml` = 39）；`@BizModel` 注解 service main 41 处（含测试 42，roadmap 记 42 为含测试口径）。

## 单元格标注说明

- `✅ 已审计且无 finding` — 引用历史审计文件（该子模块在该维度已审计，未闭包 finding 为 0）
- `⚠️ 已审计但有未闭包 finding` — 引用轮次限定 finding 编号（格式 `<YYYY-MM-DD-HHmm>#<来源内编号>`，见 M0.3 清单）
- `❓ 未审计` — 本 roadmap MA1-MA7 新审计工作项的来源
- `N/A` — 该维度不适用于该子模块（如 ORM 维度对无 Java 的 meta/web 子模块）

历史审计文件引用简称：`0719m` = `2026-07-19-1118-multi-audit-nop-metadata.md`；`0719o` = `2026-07-19-1118-open-audit-nop-metadata.md`；`0720d` = `2026-07-20-1554-deep-audit-nop-metadata/`；`0720m` = `2026-07-20-1816-multi-audit-nop-metadata/`；`0720o` = `2026-07-20-1816-open-audit-nop-metadata.md`；`0721m` = `2026-07-21-2039-multi-audit-nop-metadata/`；`0721o` = `2026-07-21-2039-open-audit-nop-metadata.md`；`0723m` = `2026-07-23-0714-multi-audit-nop-metadata/`；`0723o` = `2026-07-23-0714-open-audit-nop-metadata.md`。

## 来源 A：已有 skill 维度（24）

| # | 维度 | api | core | codegen | dao | meta | service | web | app |
|---|------|-----|------|---------|-----|------|---------|-----|-----|
| A01 | 依赖图与模块边界（deep 01） | ✅ 0719m | ✅ 0719m | ✅ 0719m | ✅ 0719m | ✅ 0719m | ✅ 0719m | ✅ 0719m | ✅ 0719m |
| A02 | 模块职责与文件边界（deep 02） | ✅ 0720d | ⚠️ `2026-07-19-1118#维度01-02`（P3 watch-only：core 仅常量） | ✅ 0719m | ⚠️ `2026-07-19-1118#维度02-04`（P3：OrmModelImporter 位置） | ✅ 0719m | ⚠️ `2026-07-21-1200-2` 裁定 B2/B3（拆分 optimization candidate） | ❓ | ❓ |
| A03 | API 表面积与契约一致性（deep 03） | ✅ 0719m/0720m | ✅ 0720d | ❓ | ✅ 0720o SC-01（I*Biz 接口已补齐 + `TestNopMetaBizInterfaceCompleteness`） | ❓ | ⚠️ `2026-07-23-0714#维度07-003`（P2：getEntityById 残余）+ `2026-07-21-2039#维度07-03`（P2：queryAggregation 11 参数） | ❓ | ❓ |
| A04 | ORM 模型与实体设计（deep 04） | N/A | N/A | N/A | ⚠️ `2026-07-23-0714#维度04-004/05/06/07`（P3 残余） | ✅ 0719m | N/A | N/A | N/A |
| A05 | 生成管线完整性（deep 05） | ✅ 0721m | N/A | ⚠️ `2026-07-21-2039#维度05-01`（P3：gen-orm 缺第 3 步） | ✅ 0723m 05-01..07 | ✅ 0723m | N/A | ✅ 0723m 05-07 | N/A |
| A06 | Delta 定制合规性（deep 06） | N/A | N/A | N/A | ✅ 0719m/0720d（无 Delta） | ✅ 0719m | ✅ 0719m | ✅ 0719m | N/A |
| A07 | BizModel 规范遵循（deep 07） | N/A | N/A | N/A | ✅ 0720o SC-01 | N/A | ⚠️ `2026-07-23-0714#维度07-003/07-004`（P2）+ `2026-07-23-0714#维度11-04`（P2） | N/A | N/A |
| A08 | IoC 与 Bean 配置（deep 08） | N/A | N/A | N/A | ⚠️ `2026-07-23-0714-open#AR-38`（P3：空 `_dao.beans.xml`） | ✅ 0719m | ✅ 0719m/0721m | ❓ | ❓ |
| A09 | 错误处理与错误码（deep 09） | N/A | N/A | N/A | ✅ 0719m | N/A | ⚠️ `2026-07-23-0714#维度09-02/09-03/09-06`（P2/P3 静默吞异常）+ `2026-07-23-0714#维度09-07`（P2 watch-only 有意裁定） | N/A | ❓ |
| A10 | XDSL 与 XLang 正确性（deep 10） | N/A | N/A | ✅ 0720d | ✅ 0720d | ✅ 0720d | ❓（xbiz 从未审计，07-23 盲区） | ❓ | ❓ |
| A11 | XMeta 与 BizModel 对齐（deep 11） | N/A | N/A | N/A | N/A | ⚠️ `2026-07-23-0714#维度11-02/11-03`（P3 残余 + watch-only） | ⚠️ `2026-07-23-0714#维度11-04`（P2） | ❓ | N/A |
| A12 | GraphQL 与 API 层（deep 12） | ✅ 0720d（DTO schema） | N/A | N/A | ✅ 0720d | ✅ 0720d | ⚠️ `2026-07-19-1118#维度12-01`（P3：FieldSelectionBean 部分下推） | ❓（amis 页面未审计） | ❓ |
| A13 | 安全与权限模型（deep 13） | N/A | N/A | N/A | ✅ 0719m | ✅ 0719m | ⚠️ `2026-07-20-1554#MISSING-AUTH`（P1 watch-only 裁定）+ `2026-07-23-0714#维度07-003` 数据权限面 + `2026-07-23-0714#维度16-07`（P2） | ❓（页面权限未审计） | ✅ 0723o（app.yaml 加固已验证） |
| A14 | 异步与事务模式（deep 14） | N/A | N/A | N/A | N/A | N/A | ⚠️ `2026-07-20-1554#post-commit-SEMANTIC`（P2：dispatch 语义已文档化，watch-only）+ `2026-07-20-1554#RACE`（P2 待 MA6.6 复核） | N/A | ❓ |
| A15 | 类型安全与泛型使用（deep 15） | ✅ 0720d | ✅ 0720d | N/A | ✅ 0720d | N/A | ⚠️ `2026-07-23-0714#维度07-004`（P2：DTO 内 List\<Map\>）+ `2026-07-19-1118#维度15-03`（P3） | N/A | N/A |
| A16 | 测试覆盖与质量（deep 16） | ❓（api 无测试） | ❓（core 无测试） | ✅ 0721m（codegen 1 测试） | ❓（dao 无测试目录） | ❓ | ⚠️ `2026-07-23-0714#维度16-01/03/04/05/07/09`（P2 多项）+ `维度16-10`（P3） | ❓ | ❓ |
| A17 | 代码风格与规范（deep 17） | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d（import 分组已按仓库惯例裁定，见 AGENTS.md 导入分组约定） | ❓ | ✅ 0720d |
| A18 | 文档-代码一致性（deep 18） | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d（`docs-for-ai/03-modules/nop-metadata.md` + `module-groups.md` + `source-anchors.md` META-001..004 均已补齐，live 核实） | ✅ 0720d | ✅ 0720d |
| A19 | 命名与术语一致性（deep 19） | ✅ 0720d | ✅ 0720d | ✅ 0720d | ⚠️ `2026-07-19-1118#维度19-01`（P1→已裁定默认不重命名，watch-only residual） | ✅ 0720d | ⚠️ `2026-07-23-0714#维度09-07`（P2 有意裁定）+ `2026-07-23-0714#维度07-04`（P3：AutoClassificationService 命名） | ❓ | ❓ |
| A20 | 跨模块契约一致性（deep 20） | ✅ 0719m | ✅ 0719m | ✅ 0719m | ⚠️ `2026-07-19-1118#维度20-01`（P1：`System.currentTimeMillis` 残余 2 处于 `OrmModelImporter.java:58,68`） | ✅ 0719m | ✅ 0719m | ✅ 0719m | ✅ 0719m |
| A21 | 单元测试有效性（deep 21） | ❓ | ❓ | ❓ | ❓ | ❓ | ⚠️ `2026-07-23-0714#维度16-05`（P2 并发测试）+ `2026-07-23-0714#维度16-09`（P2 sleep）+ `2026-07-23-0714#维度16-03`（P2 重复 CRUD） | ❓ | ❓ |
| A22 | ORM 模型审计（orm-model-audit） | N/A | N/A | N/A | ⚠️ 同 A04（P3 残余同引用） | ✅ 0719m | N/A | N/A | N/A |
| A23 | 跨模块依赖审计（cross-module-dependency） | ✅ 0719m/0721m（BOM 已注册，live 核实） | ✅ 0719m | ✅ 0720m 01-06（平台标准模式） | ✅ 0720d（dao→core compile 依赖已移除，live 核实） | ✅ 0719m | ✅ 0719m（nop-wf 集成已验证 0723o AR-37） | ✅ 0719m | ✅ 0719m |
| A24 | 设计文档审计（design-doc-audit） | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d | ✅ 0720d（roadmap 实体数已更新为 39） | ✅ 0720d | ✅ 0720d |

## 来源 B：残留风险新维度（6，MA6 审计工作项来源）

| # | 维度 | api | core | codegen | dao | meta | service | web | app |
|---|------|-----|------|---------|-----|------|---------|-----|-----|
| B01 | 空壳实现扫描（H01，MA6.1） | ❓ | ❓ | ❓ | ❓ | ❓ | ❓（07-21-1200-2 已裁定空 entity retention 类为平台模式，watch-only） | ❓ | ❓ |
| B02 | 静默跳过检测（H02，MA6.2） | ❓ | ❓ | ❓ | ❓ | ❓ | ⚠️ `2026-07-23-0714#维度09-02/09-03/09-06`（P2/P3 已确认未闭包） | ❓ | ❓ |
| B03 | 接线完整性验证（H03，MA6.3） | ❓ | ❓ | ❓ | ❓ | ❓ | ❓（07-23 盲区：xbiz 未审计） | ❓ | ❓ |
| B04 | 敏感信息泄露扫描（H05，MA6.4） | N/A | N/A | N/A | ✅ 0720d（事件脱敏链路已验证） | ✅ 0720d（xmeta published=false） | ✅ 0720d/0721o（AR-07/AR-02 修复已验证；connectionConfig 脱敏链路完整） | ❓（页面字段回显未审计） | ✅ 0723o（app.yaml 加固已验证） |
| B05 | 测试隔离性审查（H06，MA6.5） | ❓ | ❓ | ❓ | ❓ | ❓ | ⚠️ `2026-07-23-0714#维度16-05`（P2 并发测试共享状态缺失） | ❓ | ❓ |
| B06 | 既有修复验证（H07，MA6.6） | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ | ❓ |

## 来源 C：元数据域特有风险维度（6，MA7 审计工作项来源）

| # | 维度 | api | core | codegen | dao | meta | service | web | app |
|---|------|-----|------|---------|-----|------|---------|-----|-----|
| C01 | SQL/表达式注入面（MA7.1） | N/A | N/A | N/A | ❓ | ❓ | ❓（07-19 AR-01/AR-02/AR-12 已修复已验证；custom_sql 黑名单绕过 + join 注入点待 MA7.1 全面复核） | ❓ | ❓ |
| C02 | 凭据管理与联邦查询数据权限（MA7.2） | N/A | N/A | N/A | ❓ | ❓ | ❓（07-23 盲区：withConnection 直查旁路数据权限面；connectionConfig/事件脱敏已修复） | ❓ | ❓ |
| C03 | 导入引擎与元数据同步安全（MA7.3） | N/A | N/A | N/A | ❓（OrmModelImporter 在 dao） | ❓ | ❓（07-23 盲区：ORM XML 解析 XXE/实体膨胀、外部表同步、多 schema） | ❓ | ❓ |
| C04 | 血缘大图与查询性能（MA7.4） | N/A | N/A | N/A | ❓ | ❓ | ⚠️ `2026-07-21-2039-open#AR-25`（P2：N+1 upsert 未闭包，已裁定 optimization candidate）+ `2026-07-19-1118-open#AR-09`（P1 已修复：maxEdges 守卫） | ❓ | ❓ |
| C05 | 调度与事件可靠性（MA7.5） | N/A | N/A | N/A | ❓ | ❓ | ❓（07-23 盲区：质量检查点 cron、事件脱敏、幂等；scheduler 集成 07-19 阴性已验证） | ❓ | ❓ |
| C06 | 工作流与审批集成（MA7.6） | N/A | N/A | N/A | ❓ | ❓ | ❓（07-23 AR-37 已修复：nop-wf compile scope；.xwf 审批流失败路径待 MA7.6） | ❓ | ❓ |

## 统计（roadmap §审计维度矩阵 要求）

- 总单元格数：36 行 × 8 列 = 288
- `✅ 已审计且无 finding`：87
- `⚠️ 已审计但有未闭包 finding`：24
- `❓ 未审计`：101（来源 A 33 + 来源 B 39 + 来源 C 29）——作为 MA1-MA7 审计覆盖范围的输入
- `N/A`：76
- 说明：⚠️ 单元格引用的 finding 编号均为轮次限定格式，见 `ai-dev/audits/arm-unclosed-findings-nop-metadata.md`；❓ 格与 roadmap MA1-MA7 工作项可追溯（来源 A 的 ❓ 归 MA1-MA4 对应维度工作项；来源 B 归 MA6.x；来源 C 归 MA7.x）

## 备注

- 矩阵基于 2026-08-04 live repo 核对（9 个历史审计来源 + 关键 finding 现场验证，验证记录见 M0.3 清单尾部与 `ai-dev/logs/2026/08-04.md`）
- 文档变化：`No owner-doc update required`（audits 为证据层非规范性文档）
