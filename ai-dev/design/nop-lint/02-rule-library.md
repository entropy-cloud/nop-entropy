# Nop Lint — 规则库设计

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）

## 1. Nop 平台内置规则（首批 20 条，目标 48+）

> 首批 20 条分批交付：**Phase 1 = 10 条核心规则**（异常 5 + API 4 + VFS 1）；Phase 2 = 质量 5 + 安全 2 + Nop 特有中依赖 XNode 引擎的 2 条（xpl-escaping、orm-unique-key）；Phase 3 = query-limit-required + 反模式扩展至 48+。PMD/ErrorProne 移植规则的完整清单以覆盖 manifest 为准（06 §7）。

```yaml
# nop-lint-nop/src/main/resources/rules/

# === 异常处理 ===
exception/no-raw-exception.yml          # 禁止 RuntimeException
exception/no-empty-catch.yml           # 空 catch 检测
exception/silent-swallow.yml           # 静默吞异常
exception/errorcode-param-consistency.yml  # ErrorCode 参数一致性
exception/no-log-getmessage.yml        # 禁止 LOG.warn(e.getMessage())

# === API 契约 ===
api/ibiz-missing-annotation.yml        # I*Biz 缺少注解
api/ibiz-missing-context.yml           # I*Biz 缺少 IServiceContext
api/bizmodel-dao-access.yml            # BizModel 直接 dao() 访问
api/bizmodel-safe-api.yml              # CrudBizModel 安全 API 使用

# === 代码质量 ===
quality/no-system-out.yml              # 禁止 System.out
quality/no-return-null.yml             # 禁止 return null
quality/no-transactional-annotation.yml  # 禁止 @Transactional
quality/import-order.yml               # Import 分组排序
quality/no-star-import.yml             # 禁止星号导入

# === 安全 ===
security/no-sensitive-literal.yml      # 敏感字面量泄漏
security/no-hardcoded-crypto.yml       # 禁止硬编码密钥

# === Nop 特有 ===
nop/no-vfs-violation.yml               # 禁止直接文件 IO
nop/orm-unique-key.yml                 # ORM 唯一键约束
nop/xpl-escaping.yml                   # XPL 模板转义
nop/query-limit-required.yml           # 查询必须声明 limit
```

> **Phase 1 落地裁定增注（2026-09-22，item 11 执行期）**：上述 10 条核心规则的 v1 形态裁定见执行日志分类表（`ai-dev/logs/2026/09-22.md`）。7 条落地于 `nop-lint-nop`（no-raw-exception、no-empty-catch、ibiz-missing-annotation、ibiz-missing-context、bizmodel-dao-access、bizmodel-safe-api、no-vfs-violation，均带 RuleTester fixtures）；3 条显式裁定 successor、不落地近似版：**silent-swallow** 与 **no-log-getmessage** 需 `not`/`has` 关系算子（→ item 23 对应 plan），**errorcode-param-consistency** 需跨文件 ErrorCode 注册表分析（→ item 28 迁移 manifest；超出 v1 与 items 22/23 能力面）。规则文件 VFS 落位为 `src/main/resources/_vfs/nop/lint/rules/<category>/<rule>.rule.yml`（`.rule.yml` 后缀经 `lint.register-model.xml` 走 XDSL 加载管线；本节 YAML 注释中的 `rules/` 路径为示意）。
>
> **Phase 3 承接落地增注（2026-09-22，item 23 plan 0544-2）**：successor 规则中的两条已 faithful 落地于 `nop-lint-nop` 主资源并带全量 RuleTester fixtures（valid 1 + invalid ≥2 + `.expect`，经 `TestNopRuleSuites` 真实管线全绿）：**exception/silent-swallow**（catch 块七信号缺失判定 = `all` + `kind: catch_clause` + 11 个 `not: has:` 信号形状，字段读信号用关系匹配器的 contextual pattern 形态 `context`+`selector` 表达——裸 `A.B` snippet 在 java grammar 下解析为类型引用，须钉住表达式语境）与 **exception/no-log-getmessage**（`has: $E.getMessage()` 且 `not: has: throw $$$`，与 ast-grep `java-lint-getmessage-only.yml` 逐 matcher 对齐）。行为对照记录（与 `check-silent-swallow.mjs` / `java-lint-getmessage-only.yml` 同语料逐块对照 + 已裁定 delta）见本日日志 Phase 3 对照表；两规则主资源与 suite 入口为 `x:extends` 指针零重复。现有 mjs/ast-grep 门禁保持不动，切换下线归 item 28 manifest。errorcode-param-consistency 维持 item 28 路由不变。
>
> **XNode 规则落地增注（2026-09-22，item 21 plan 1045-2）**：design 01 §3.5 两条示例规则已作为**生产 XNode 规则**落地于 `nop-lint-nop` 主资源 `_vfs/nop/lint/rules/nop/`（`nop-orm-mandatory-default`、`nop-xbiz-auth-not-sole-guard`，均 language: XML + 全量 RuleTester fixtures：valid 1 + invalid 2 + `.expect`，经 `TestNopRuleSuites` XML 套件全绿）。**归属裁定**：落生产规则而非测试套件 fixtures——两条规则是真实平台约定检查（ORM mandatory 列缺省值 / xbiz auth 唯一守卫），且生产化使 XML 引擎链路受真实规则库约束（与 item 11/23 规则落地同口径）。**与 item 29 的区分**：item 29 的"9 条 XNode 规则批量"指 first-20 清单中的 orm-unique-key、xpl-escaping、query-limit-required 等剩余 XNode/质量/安全规则——本增注两条是 §3.5 示例规则的先行落地，不计入该批量，亦不改变 item 29 的 scope。**fixture 规则归属裁定**中 design 01 §3.5 示例的 `<action>` 标签在真实 xbiz 模型中不存在（实际形态为 query/mutation/subscription/action 四类 typed 元素），生产规则的 faithful 形态与裁定记录见 design 01 §3.5 增注与规则文件头注。

## 2. 规则继承与覆盖

规则集继承使用 XDSL 自带的 `x:extends` delta 合并（元模型见 10 §3）：同名规则按 key（`id`）覆盖合并，新增规则直接列出。基础 delta 合并能力随 Phase 1 DslModelParser 自带；ruleset 级使用与 exemptions 在 Phase 2 交付。

```yaml
# 基础规则集 nop-base.ruleset.yml
id: nop-base
rules:
  - id: nop-no-raw-exception
    language: Java
    severity: error
    message: "禁止直接抛出 RuntimeException"
    rule:
      pattern: throw new RuntimeException($$$ARGS)
  - id: nop-no-empty-catch
    language: Java
    severity: warning
    message: "空 catch 块必须处理或记录异常"
    rule:
      pattern: catch ($E) { }
  - id: nop-no-system-out
    language: Java
    severity: warning
    message: "禁止 System.out 调试输出"
    rule:
      pattern: System.out.println($$$)

# 域规则集 nop-purchase.ruleset.yml（继承 + 扩展 + 覆盖）
id: nop-purchase
x:extends: /nop/lint/rulesets/nop-base.ruleset.yml
rules:
  # 新增规则
  - id: nop-purchase-order-state-guard
    language: Java
    message: "订单状态变更必须走状态机守卫"
    rule:
      pattern: $ORDER.setState($$$)
  # 覆盖：同 id 规则仅覆盖声明的字段（severity 降低，其余继承）
  - id: nop-no-raw-exception
    severity: warning
```

## 3. 需求追溯表（现有检查机制 → Nop Lint）

> 本表是"消灭 regex 误报 + 不丢现有门禁"的验收底账。现有资产盘点（2026-09-20 修订，以仓库实测为准）：`ai-dev/tools/` 下 check-\*.mjs 共 24 个；`ai-dev/tools/rules/` 下已有 3 条 ast-grep YAML 规则（bare-runtimeexception / empty-catch / getmessage-only，经 run-java-lint.sh + sgconfig.yml 运行）——这 3 条是 pattern DSL 的现成验收用例，Phase 1 直接吸收重写；checkstyle.xml 激活 17 条 + pmd-ruleset.xml 9 条（06 §8）。

| 现有需求来源 | 条数 | 去向 |
|------------|------|------|
| ast-grep 规则（ai-dev/tools/rules/） | 3 | **Phase 1** 吸收为内置规则（含 fixtures 对照 sg 行为） |
| check-\*.mjs 的 regex/AST 规则 | 24 个脚本（**逐脚本规则数/能力映射/切换门禁以迁移 manifest 为权威**：[12-check-scripts-migration-manifest.md](./12-check-scripts-migration-manifest.md)，item 28 落地；替代笼统的"25+"） | **Phase 2** 逐脚本迁移 + 切换下线（含 `check-silent-wrong-result.mjs` 的 5 条子规则逐条枚举；errorcode-param-consistency 经 item 28 裁定**维持 mjs hard gate**，不立项引擎级 analyzer，重估触发条件见 manifest 裁定节） |
| `check-bean-naming.mjs`（beans.xml 命名，**当前 CI 唯一活跃的 mjs 门禁**） | 1 | **Phase 2** XNode 引擎交付后迁移（XML 规则） |
| checkstyle.xml / pmd-ruleset.xml | 17 + 9 | **Phase 4** 映射迁移 + 并行期（06 §8） |
| 反模式规则（调研识别、当前 0 实现） | 8 | **Phase 3** 随 48+ 规则库交付，进入前须先交枚举清单（roadmap Wave 5） |
| 文档一致性检查（跨文档引用核对等） | 3 | **明确排除**（非代码检查，继续由 doc-link-checker 等工具承担） |
| ORM 模型检查（orm-icons 等） | 3 | Phase 2 XNode 引擎（orm-unique-key 首批，其余随 manifest） |

**数量口径**：内置规则目标 48+ = 首批 20 + 反模式 8 + mjs/checkstyle/pmd 迁移吸收（去重后 ~20+）；PMD/ErrorProne 移植池 ~190 条（06 §4.7）是 manifest 层的另一口径，不计入 48+。
