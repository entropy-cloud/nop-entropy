# Nop Lint — 抑制与豁免机制（Suppression & Exemption）

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）
> Phase 归属：v1（注释 + @SuppressWarnings 识别）Phase 1；v2（baseline + CI stale 检查）Phase 2。见 08-migration.md。

## 1. 四层抑制机制

```
1. 内联注释   nop-lint-disable / nop-lint-enable
2. 注解      @SuppressWarnings("nop-lint:rule-id")
3. 配置豁免   规则集 YAML 中的 exemptions（按规则/文件 glob/范围）
4. 基线文件   baseline.yml（存量违规豁免）
```

- 判定语义是**单调的**：诊断命中任意一层的有效抑制即不输出（四层之间没有"解除抑制"的对抗关系）
- "优先级"仅用于**诊断归属**：同一诊断被多层命中时，`--report-suppressed` 输出取最高优先级层作为 `suppressionKind`；`unused-disable-directive` 判定只针对第 1/2 层（配置层豁免与 baseline 不产生 unused 告警）
- **未被使用的抑制指令本身是可检测的问题**（`unused-disable-directive`，默认 warning，可关闭）

## 2. 内联注释 DSL

### 2.1 语法（Java/TS 通用，按行注释与块注释均可）

| 形式 | 语义 |
|------|------|
| `// nop-lint-disable-next-line rule-a, rule-b` | 仅抑制下一行 |
| `// nop-lint-disable-line` | 抑制当前行 |
| `// nop-lint-disable rule-a` | 从本行起抑制 rule-a |
| `// nop-lint-disable` | 从本行起抑制全部规则（需给出 reason 时：`--reason "..."`，可选） |
| `// nop-lint-enable rule-a` | 恢复 rule-a |
| `// nop-lint-enable-all` | 恢复全部 |

### 2.2 语义规则

- 诊断 range 与注释作用范围**有重叠**即被抑制（含多行诊断）
- `disable`/`enable` 必须配对：文件结束仍处 disable 状态 → `unpaired-disable` 提示（info 级）
- 未匹配到任何诊断的 disable → `unused-disable-directive`（warning）

## 3. @SuppressWarnings 集成（Java）

```java
// 识别三种值格式：
@SuppressWarnings("nop-lint:nop-no-raw-exception")   // 推荐：nop-lint: 前缀
@SuppressWarnings("nop-no-raw-exception")            // 兼容：直接规则 id
@SuppressWarnings("all")                             // 抑制全部
```

- 作用范围 = 注解所在的类/方法/字段/参数声明节点
- 映射到 PMD/ErrorProne 迁移场景：manifest（06 §7）中导入的规则保留原名称作为别名，`@SuppressWarnings("PMD.AvoidUsingVolatile")` 亦可识别（迁移期兼容）

## 4. 配置豁免（规则集 YAML）

```yaml
id: nop-base
rules:                       # 规则条目为完整内联规则体（10 §3：外部 ref 只支持整文件→根节点，不能引用单个规则文件）
  - id: nop-no-raw-exception
    language: Java
    severity: error
    message: "禁止直接抛出 RuntimeException"
    rule:
      pattern: throw new RuntimeException($$$ARGS)
exemptions:
  - rule: nop-no-raw-exception
    files: ["**/legacy/**/*.java"]        # glob
    reason: "legacy module, refactor planned in Q4"   # 必填，文档化豁免原因
  - rule: nop-orm-mandatory-default
    files: ["**/*.orm.xml"]
    ranges: ["MyEntity.slowMigration"]    # 可选：语义范围（规则自定义 key）
```

## 5. 基线文件（Baseline）

### 5.1 格式

```yaml
# nop-lint-baseline.yml（生成：nop-lint baseline --write）
version: 1
entries:
  - rule: nop-no-raw-exception
    file: "nop-biz/src/main/java/io/nop/biz/LegacyBizModel.java"
    fingerprint: "sha256:9f3a..."   # 规则id+节点文本+range 的内容哈希（抗行号漂移）
    count: 3
```

### 5.2 生命周期

1. **生成**：`LintEngine.writeBaseline(report)` Java API（Phase 2 随测试基座可用）；Maven goal 随 Phase 4 插件、CLI 包装 `nop-lint baseline --write` 同期提供
2. **匹配**：诊断的 fingerprint 命中 baseline → 抑制；行号变化不影响（内容寻址）
3. **收紧**：`LintEngine.checkBaseline(report)` Java API（Phase 2）／CI 中 `nop-lint baseline --check`（Phase 4）：实际命中数 < baseline 记录数 → 报 stale，要求重新生成（基线只减不增；新增条目 CI 失败）
4. **目标**：随模块清理逐步缩小 baseline 至删除

## 6. 与其他机制的交互

| 交互点 | 规则 |
|--------|------|
| autofix | 被抑制的诊断不生成 fix；fix 应用后若产生新诊断走正常抑制判定 |
| xscript 多次 report | 每个 diagnostic 独立判定抑制 |
| RuleTester（03 §4） | fixtures **不受** baseline/exemptions 影响（只测内联抑制，保证规则行为可测） |
| SARIF 输出 | 被抑制的诊断默认不输出；`--report-suppressed` 可输出并带 `suppressionKind` 标记 |
