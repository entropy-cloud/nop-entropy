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

### 2.3 v1 落地裁定（2026-09-22，plan 2026-09-22-0128-3，item 17）

> **扫描载体裁定**：CST 注释节点导航（`LintNode.isExtra()` 或 kind 名含 comment，经 LintNode 门面），**不是源码行级扫描**。依据：(a) 语义保证——字符串字面量/标识符中的 `nop-lint-disable` 文本不构成指令（测试钉死）；(b) 语言无关——门面提供注释节点与文本切片，Java/TS 共用同一扫描器；(c) 引擎管线（design 03 §1.1）在抑制判定点本就持有 `LintTree`。行注释与块注释均为合法载体；指令必须位于注释**单独一行语义段**内（一个注释节点至多一条指令，两条即 fail-closed 报错——参数归属无法无歧义切分）。

> **作用域表示**：UTF-8 字节半开区间 span（`SuppressionSpan`），规则集为空 = 全部规则；重叠判定 = 区间相交（多行诊断与单行 span 相交即抑制）。`disable-next-line`/`disable-line` 生成**立即闭合的行 span**（指令所在行/下一行的整行含换行），不参与配对状态机；`disable` 生成**开放区域**，由 `enable`/`enable-all` 闭合，文件结束仍开放 → span 延伸至 EOF 并产出 `unpaired-disable`。

> **未知形态处置（fail-closed，二选一之裁定）**：`nop-lint-` 后跟五个指令关键字（`disable-next-line|disable-line|enable-all|disable|enable`，非字母数字边界）之一 → 指令；非字母数字边界外的其他后缀（如 `nop-lint-disabled`）→ 普通文本忽略。可识别关键字 + 畸形参数（`enable` 缺规则列表、`enable-all` 带参数、空规则段/连续逗号、规则 token 超出 `[A-Za-z0-9_.$/-]`）→ 抛 `NopLintException`（消息含源码行号），不静默忽略。`--reason "..."`（§2.1）接受并丢弃——v1 无 reason 域，接受是设计语法、丢弃是文档化裁剪。

> **XML 路径落地裁定（2026-09-22，item 21，plan 2026-09-22-1045-2）**：XNode 注释参与内联抑制 = **落地**（非 v1 gap）。载体：XNode 注释挂靠于后继节点（平台解析器行为），`XNodeLintNode` 门面将其以 `#comment` extra trivia child 暴露，既有 `CommentSuppressionScanner` 的门面导航（isExtra / comment kind）零 XML 特判命中——六种指令形态、配对状态机、unused/unpaired 元诊断语义全部原样生效。XML 块注释终止符与 C 族 `*/` 同位处理（`rawArguments` 终止符剥离）。span 字节区间 = 注释原文（含定界符）精确 span（门面由后继节点起点反扫定位，解析器保证终止符存在，缺失即 fail-closed）。注解 provider 恒 null（XML 无注解载体）；根节点后注释被平台解析器丢弃（平台行为，非 lint 裁定）。断言：`TestXmlRuleEngineEndToEnd` 抑制生效 + `suppressedDiagnostics` 计数 + unused-directive 元诊断三路径。

> **配对语义 v1**：裸 `disable`（全规则）只能由 `enable-all` 解除；`enable <rule>` 只关闭该规则自身的具名 span；`enable`/`enable-all` 无匹配的开放 disable → `unpaired-disable`（info）。`unused-disable-directive` 按**指令**粒度判定（该指令的任一 span 抑制过 ≥1 条诊断即已使用）；覆盖第 1 层注释与第 2 层 `@SuppressWarnings` span。元诊断 rule id 固定为 `unused-disable-directive`（warning）/`unpaired-disable`（info），非 `.rule.yml` 规则（无加载面），本身**不可被抑制**。

## 3. @SuppressWarnings 集成（Java）

```java
// 识别三种值格式：
@SuppressWarnings("nop-lint:nop-no-raw-exception")   // 推荐：nop-lint: 前缀
@SuppressWarnings("nop-no-raw-exception")            // 兼容：直接规则 id
@SuppressWarnings("all")                             // 抑制全部
```

- 作用范围 = 注解所在的类/方法/字段/参数声明节点
- 映射到 PMD/ErrorProne 迁移场景：manifest（06 §7）中导入的规则保留原名称作为别名，`@SuppressWarnings("PMD.AvoidUsingVolatile")` 亦可识别（迁移期兼容）

### 3.1 v1 落地裁定（2026-09-22，plan 2026-09-22-0128-3，item 17）

> **提取载体与装配裁定**：core 定义 `SuppressionProvider` 接口（输入 `LintTree`，输出 `SuppressionSpan` 列表），语言绑定经 `LintLanguage.suppressionProvider()` 默认方法（缺省 null）暴露实现——`nop-lint-java` 以 CST 导航实现 `JavaSuppressWarningsProvider`（annotation/marker_annotation 节点名 `SuppressWarnings`，简单名或限定名按后缀匹配；span range = 注解外层声明节点，经 `modifiers` 包装层向上取）。**拒绝**引擎层硬编码 Java 文法（破坏 core 语言无关）与 ServiceLoader 独立发现（provider 脱离其语言绑定无意义，生命周期必须耦合）。core 仅消费接口契约，零文法知识。

> **值形态解析**：三种形态按**每个字符串值**独立判定，数组参数逐值贡献 span；`value = "..."` 命名形态与直接形态等价；marker（无值）无 span。`nop-lint:` 前缀值须解析出合法 rule id（`[A-Za-z0-9_.$/-]+`），空白或畸形 → fail-closed 抛 `NopLintException`（显式声明 nop-lint 语义却不可解析 = 契约违规，不得伪造抑制，Minimum Rules #24）；裸值按兼容规则识别为字面 rule id（javac 内置值如 `unchecked` 与 PMD/ErrorProne 别名随之成为永不匹配的裸 id span，经 unused 检查浮出——关闭旋钮归 item 27；**别名→迁移规则的语义映射**仍归 item 29 manifest，v1 不做）。Java 转义序列不处理（nop-lint rule id 无转义需求）。

> **unused 判定覆盖注解层**：每个注解 = 一个独立"指令"（directiveRange = 注解节点 range），其 span 抑制零诊断 → `unused-disable-directive`（design 09 §1：unused 检查覆盖第 1/2 层）。

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
