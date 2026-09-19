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
| check-\*.mjs 的 regex/AST 规则 | 24 个脚本（逐脚本规则数以迁移 manifest 记录，替代笼统的"25+"） | **Phase 2** 逐脚本迁移 + 切换下线（含 `check-silent-wrong-result.mjs` 的 5 条子规则逐条枚举） |
| `check-bean-naming.mjs`（beans.xml 命名，**当前 CI 唯一活跃的 mjs 门禁**） | 1 | **Phase 2** XNode 引擎交付后迁移（XML 规则） |
| checkstyle.xml / pmd-ruleset.xml | 17 + 9 | **Phase 4** 映射迁移 + 并行期（06 §8） |
| 反模式规则（调研识别、当前 0 实现） | 8 | **Phase 3** 随 48+ 规则库交付，进入前须先交枚举清单（roadmap Wave 5） |
| 文档一致性检查（跨文档引用核对等） | 3 | **明确排除**（非代码检查，继续由 doc-link-checker 等工具承担） |
| ORM 模型检查（orm-icons 等） | 3 | Phase 2 XNode 引擎（orm-unique-key 首批，其余随 manifest） |

**数量口径**：内置规则目标 48+ = 首批 20 + 反模式 8 + mjs/checkstyle/pmd 迁移吸收（去重后 ~20+）；PMD/ErrorProne 移植池 ~190 条（06 §4.7）是 manifest 层的另一口径，不计入 48+。
