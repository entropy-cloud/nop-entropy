# Nop Lint — 规则 DSL 的 XDef 元模型

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）
> Phase 归属：`lint-rule.xdef` 随 Phase 1 YAML 解析器交付（XDSL 校验是解析器的一部分）；ruleset xdef（exemptions）Phase 2。`x:extends` delta 合并是 XDSL 平台自带能力，Phase 1 即随 DslModelParser 生效。
>
> **语法权威**：`docs-for-ai/02-core-guides/xdef-and-xdsl.md`（平台文档）+ `nop-xdefs` 的 `xdef.xdef` 自举定义（源码级补充，覆盖文档未展开的 check-* 等）。本文示例已按两者核对：key-attr 显式声明与类型一致（文档 §3）、csv-set（§5）、简单文本直写类型（§6）、`!` 在 `enum:` 前（§7）。

## 1. 为什么必须用 xdef

规则 DSL 是 XDSL 模型，YAML 只是序列化格式。用 xdef 元模型定义后，平台已有设施**免费**获得：

| 能力 | 来源 | 不用 xdef 的代价 |
|------|------|----------------|
| 结构校验（必填/枚举/类型） | `DslModelParser` + xdef | 手写 schema 校验器 |
| YAML/JSON/XML 多格式 | XDSL 统一解析 | 每种格式各写一个 |
| **规则继承 = Delta 定制** | `x:extends`（XDSL 自带） | 02 §2 的继承需自实现合并语义 |
| IDE 补全/校验 | xdef 注册到 `_vfs` schema 目录 | 无 |
| 规则条件编译 | `x:if` / XPL 条件 | 自实现 |
| message 国际化 | XDSL label/dict 约定 | 自实现 |
| 生成强类型模型 | xdef → Java model（XDslModel） | 手写 POJO + 映射 |

> 02 §2 的规则集继承直接使用 `x:extends`（见 §3）；09 §4 的 exemptions 在 ruleset xdef 中定义。

## 2. 规则文件元模型 `/nop/lint/schema/lint-rule.xdef`

> 语法依据：`nop-kernel/nop-xdefs` 的 `xdef.xdef` 自举定义与 `wf.xdef` 等实例。匹配器唯一性由 `RuleDslParser`（`xdef:parser-class` 指定，模型构建期）fail-closed 强制；`xdef:check-mutex` 声明同一意图。
>
> **落地形态（2026-09-21 plan 06 交付，与 live 资源一致）**：
> - `xdef:name` 值必须是合法 var name（`attr-not-valid-local-ref` 校验），故用 `LintRule` 而非 `lint-rule`。
> - `message` 必填须显式 `xdef:mandatory="true"`（`!string` 只约束值非空，不约束节点存在）。
> - **check-mutex 无 `atLeastOne`**：平台 `XDefConstraintValidator` 已在实例校验期运行 check-*（修正早前"无运行时消费方"的记载），但**只检查属性值（`node.attrText`）**；匹配器为子元素形态，`atLeastOne="true"` 会对每条合法规则误报。故两处声明保留 `id`/`select`/`props`（fail-fast 加载校验覆盖 id 唯一 + select 可编译），"至少一个/至多一个"的运行时强制全部在 RuleDslParser。
> - `any` 子项是 `<matcher pattern kind regex/>` 对象：body-type=list 中同名异构子标签无法保留标签身份，且对象形态才能表达 ast-grep 的"分支内合取"分组（`{pattern, kind}` 同分支）。
> - `metadata.source` 与 `files.include/exclude` 以 **csv-set 属性**落地：同一子标签重复出现会在 DynamicObject 构建期 duplicate-prop 崩溃，属性形态天然多值（YAML 写 CSV 字符串）。
> - `xscript` 以 `string` 落地：xpl std-domain 在解析期编译为 ExprEvalAction、不保留原文文本；Phase 1 只保证文本往返，XPL 编译归 item 14 RuleCompiler。
> - `options`/`settings` map+key-attr 的 YAML 形态：`options: {maxFiles: {value: "16"}}`（map 值按 `<option>` 定义展开，key 由 map 键注入）。

```xml
<lint-rule x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
           xmlns:xdef="/nop/schema/xdef.xdef" xdef:name="LintRule"
           xdef:parser-class="io.nop.lint.core.rule.RuleDslParser"
           id="!string" language="enum:Java|TypeScript|TSX|XML"
           severity="enum:hint|info|warning|error|off"
           xscriptTimeoutMs="int=100">

    <!-- 匹配器唯一性：声明式意图（平台 check-* 加载期 fail-fast 校验 id 唯一 + select 可编译；
         实例级校验只覆盖属性形态，见上方落地形态说明）。运行时唯一性权威在 RuleDslParser -->
    <xdef:check-mutex id="one-matcher" select="/lint-rule/rule" props="pattern,kind,regex,any"/>
    <xdef:check-mutex id="any-child-matcher" select="/lint-rule/rule/any" props="pattern,kind,regex"/>

    <message xdef:mandatory="true">!string</message>

    <!-- 01 §2 的 rule: 容器：匹配器恰好一个（XOR 由 RuleDslParser 强制） -->
    <rule>
        <pattern>!string</pattern>
        <kind>!string</kind>
        <regex>!string</regex>
        <!-- 分支对象：至少一个匹配器（RuleDslParser 强制）；pattern+kind 同分支为合取（ast-grep 超集） -->
        <any xdef:body-type="list">
            <matcher pattern="string" kind="string" regex="string"/>
        </any>
    </rule>

    <!-- 关系匹配器/all/not/matches/utils/constraints/fix 随 Wave 4（items 21–24）扩展本 xdef -->

    <!-- xscript：xpl 片段文本，字符串直存；编译执行归 RuleCompiler（item 14） -->
    <xscript>string</xscript>

    <!-- 分析器依赖声明（11 §1 档位聚合的依据）：L1|L2|tsc|dataflow|scope|metrics|...，CSV 多值 -->
    <requires>csv-set</requires>

    <metadata category="!string" severity="enum:hint|info|warning|error|off"
              autoFixable="boolean=false" version="string=1.0" source="csv-set"/>

    <!-- 规则可配置项（对标 ESLint rule options）：键值对，xscript 经 rule.options 读取 -->
    <options xdef:body-type="map" xdef:key-attr="key">
        <option key="!var-name" value="!string"/>
    </options>
    <!-- 共享环境配置（对标 context.settings）：由 ruleset 层注入，规则只读 -->
    <settings xdef:body-type="map" xdef:key-attr="key">
        <setting key="!var-name" value="!string"/>
    </settings>

    <!-- 文件范围 glob：include/exclude 各为 CSV 多值 -->
    <files include="csv-set" exclude="csv-set"/>
</lint-rule>
```

说明：
- **命名（camelCase 直写，XML 名 = Java 属性名 = YAML 键）**：本 xdef 的标签与属性名一律用**首字母小写的 camelCase** 声明（`sameText`、`stopBy`、`xscriptTimeoutMs`）。平台对无分隔符的名字做**恒等映射**（`xmlNameToVarName` 短路原样返回，`beanPropName` 对首字母小写 camelCase 不改写），因此 xdef 声明名、生成的 Java 属性名、YAML/JSON 键**三者同名**，无映射心智负担。这与平台 page/view/xmeta/rule 系 schema 的主流风格一致（nop-xdefs 实测 camelCase 标签 445 种 vs kebab 133 种）。**边界约束**：声明名必须以小写字母开头，且避免"第二字符大写"形态（如 `aBc`——`beanPropName` 会将其改写为 `ABc` 导致名字不稳定）；`xdef:` 前缀的命名空间属性（`xdef:body-type`、`xdef:key-attr` 等）是平台 schema 自有名字，保持平台原样不改
- **csv-set 值形态**：属性位 csv-set（`requires`/`metadata.source`/`files.include/exclude`）在 YAML 中的规范形态是 **CSV 字符串**（如 `requires: "L1,L2"`）；平台 `ConvertHelper.toCsvSet` 在 API 层同时接受集合形态。规则模型内 csv-set 子标签重复出现不可行（duplicate-prop 崩溃），故一律走属性位
- **pattern 内容**是源码文本，类型为 `string`（XML 语言的规则同样以文本形式书写 pattern，运行期由 XNode 引擎解析）
- **stopBy**（Wave 4 关系匹配器）：`neighbor|end|rule` 三档对齐 04 §5；`stopByRule` 为 util 规则名（string），在 `stopBy=rule` 时必填（由 parser-class 校验）
- **递归匹配器**（any/all/not 嵌套，Wave 4）：扩展时按 `<matcher>` 对象模式展开分支容器
- **复杂跨字段校验**（如 stopBy=rule 时 stopByRule 必填、Wave 4 matches 引用的 util 是否存在）：经 `xdef:parser-class="io.nop.lint.core.rule.RuleDslParser"` 声明，**由 nop-lint 加载管线在规则加载时调用**（平台无 parser-class 运行时消费方；RuleDslParser 当前强制 rule 容器 XOR 与 any 分支 atLeastOne，Wave 4 扩展时在同处追加对应校验）

## 3. 规则集元模型 `/nop/lint/schema/lint-ruleset.xdef`（Phase 2）

```xml
<lint-ruleset x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
              xmlns:xdef="/nop/schema/xdef.xdef" xdef:name="lint-ruleset" id="!string">
    <!-- 规则继承：XDSL delta 合并（02 §2 的 extends 即此，Phase 1 随 DslModelParser 自带） -->
    <!-- 用法：x:extends="/nop/lint/rulesets/nop-base.ruleset.yml"，同名规则按 key-attr 覆盖合并 -->
    <!-- docs-for-ai §3 规则：key-attr 属性必须在子节点显式声明，且类型与 xdef:ref 继承的定义完全一致 -->
    <rules xdef:body-type="list" xdef:key-attr="id">
        <rule id="!string" xdef:ref="/nop/lint/schema/lint-rule.xdef"/>
        <!-- 外部 ref 只支持整文件→根节点（XDefRefResolver）；规则条目本身即完整 lint-rule 模型 -->
    </rules>
    <!-- 09 §4 配置豁免 -->
    <exemptions xdef:body-type="list">
        <exemption rule="!string" reason="!string">
            <files>string</files>
            <ranges>string</ranges>
        </exemption>
    </exemptions>
</lint-ruleset>
```

## 4. 加载与编译管线（与 01 §4 对齐）

```
nop-lint-core 的 lint.register-model.xml          ← Phase 1 交付物（新增）
  注册 fileType=rule.yml → xdsl-loader（schemaPath=/nop/lint/schema/lint-rule.xdef）
  （照抄 dict.register-model.xml 模式；YAML 加载走平台 DslJsonResourceLoader）
        ↓
*.rule.yml / *.ruleset.yml        （VFS 下规则文件，03 §2.2 vfsRuleLoader）
  ↓ DslJsonResourceLoader（YAML→JObject→XNode，按 xdef 元模型转换）
  ↓ DslNodeLoader（xdef 结构校验 + x:extends delta 合并）
  ↓ RuleDslParser（io.nop.lint.core.rule.RuleDslParser；loadRuleModel 单方法入口 = 注册链路加载 + 唯一性校验 + 类型化构建）
RuleDslModel（xdef 校验后的强类型模型）
  ↓ RuleCompiler（01 §4：SourcePatternCompiler / Constraint / xscript 编译）
CompiledRule（不可变，可缓存，与执行档位无关）
  ↓ LintEngine（按执行档位运行，见 11-performance-profiles.md）
```

> **必要环节**：`DslModelParser.parseFromResource` 本身只解析 XML；YAML 模型必须经 register-model 注册的 `DslJsonResourceLoader`（平台已有，dict.yaml/page.yaml 同款路径）进入 XDSL 管线，`x:extends` 与 xdef 校验才会生效。漏注册则 `.rule.yml` 会被当 XML 解析直接失败，因此 register-model 文件是 Phase 1 的显式交付物（见 08 §1 与 roadmap）。

## 5. 设计约束（与其他文档的协调）

| 约束 | 出处 | 一致性要求 |
|------|------|-----------|
| 匹配器恰好一个 | §2 `xdef:check-mutex` 声明 + `RuleDslParser` 运行时强制 | 01 §2 `rule:` 内允许的标签集合 = 本 xdef 定义 |
| schema 字段名 | §2 属性（camelCase 直写，XML 名 = 属性名 = YAML 键，§2 说明） | 01 §2 YAML 示例字段逐字段相同（含 `metadata.severity`、`autoFixable`、`requires`、`options`/`settings`、`xscriptTimeoutMs`） |
| 命名一致性 | §2 说明：本 xdef 全部 camelCase 声明 + 恒等映射（`xmlNameToVarName` 无分隔符短路）+ 小写开头边界约束 | 01 §3.2 约束示例键与 xdef 标签同名（`sameText` 等） |
| severity 枚举 | §2 | 07 §2.3 report() severity 子集；09 baseline 不改 severity |
| `xscript` 是 XPL 片段 | §2（文本直存，编译归 item 14 RuleCompiler） | 07 的 API 契约（node/captures/report/typeAnalyzer/scopeAnalyzer）是 XPL 上下文变量来源 |
| 约束依赖标注 | §2（typeOf 需 L2、controlFlow Phase 3）+ `requires` 属性 | 08 §2 依赖矩阵按 requires 聚合跳过规则 |
| 规则集继承语法 | §3 `x:extends` + key-attr 覆盖 | 02 §2 示例必须用 x:extends 语法（不用 add/override 包装） |
| 规则 id 命名空间 | `nop-` 前缀平台保留 | 09 §3 `@SuppressWarnings("nop-lint:...")` 识别依赖 |

## 6. 交付物（状态跟踪见 [backlog roadmap](../../backlog/nop-lint-roadmap.md)）

- Phase 1：`lint-rule.xdef`（pattern/kind/regex/单层 any/xscript/requires/metadata/options/files）+ `lint.register-model.xml`（YAML 加载注册，§4）+ `RuleDslParser`（parser-class，由 nop-lint 加载管线调用）+ DslJsonResourceLoader 接入；`x:extends` 基础 delta 合并随 XDSL 平台自带生效（`settings` 字段随 Phase 2 ruleset 交付，先在 xdef 中占位定义）
- Phase 2：关系匹配器/all/not/matches + constraints 全量（**controlFlow 除外，Phase 3**）+ `lint-ruleset.xdef`（ruleset 级 x:extends 使用 + exemptions + settings 注入）
- Phase 3：controlFlow 约束 + typeOf 完整语义（L2/L4 标注生效）
