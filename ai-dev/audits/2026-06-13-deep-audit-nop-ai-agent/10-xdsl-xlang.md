# 维度 10：XDSL 与 XLang 正确性 — nop-ai-agent

**目标模块**: `nop-ai/nop-ai-agent`
**审计基线**: live code
**审计日期**: 2026-06-13

## 第 1 轮（初审）

### 审计范围（XDSL 文件清单）

主资源（`src/main/resources`）: agent.register-model.xml、agent-plan.register-model.xml、agent-plan.record-mappings.xml
测试资源（`src/test/resources`）: test-agent.agent.xml、test-plan-agent.agent.xml、test-react-agent.agent.xml、test-single-turn-agent.agent.xml、test-unknown-mode-agent.agent.xml

引用的 xdef（位于 nop-kernel/nop-xdefs，仅校验引用正确性）: `/nop/schema/register-model.xdef`✓、`/nop/schema/record/record-mappings.xdef`✓、`/nop/schema/ai/agent.xdef`✓、`/nop/schema/ai/agent-plan.xdef`✓

### [维度10-01] test-unknown-mode-agent.agent.xml 声明 `mode="unknown"` 违反其 x:schema 所引用的 agent.xdef 枚举约束，且为孤儿测试资源

- **文件**: `nop-ai/nop-ai-agent/src/test/resources/_vfs/test-unknown-mode-agent.agent.xml:1-8`
- **证据片段**:
  ```xml
  <agent x:schema="/nop/schema/ai/agent.xdef" xmlns:x="/nop/schema/xdsl.xdef"
         name="test-unknown-mode-agent" mode="unknown">

      <description>Test agent for unknown mode</description>

      <prompt>You are a test assistant.</prompt>

  </agent>
  ```
  对照 `nop-kernel/nop-xdefs/.../schema/ai/agent.xdef:8`:
  ```
  mode="enum:react,plan,single-turn|react"
  ```
- **严重程度**: P3
- **现状**: 该 XDSL 文件通过 `x:schema="/nop/schema/ai/agent.xdef"` 显式声明接受 `agent.xdef` 的模式校验，但根元素属性 `mode="unknown"` 不在 xdef 声明的枚举集合 `{react, plan, single-turn}` 内（默认值 `react`）。全仓搜索显示该资源未被任何测试通过 `ResourceComponentManager.loadComponentModel(...)` 加载，是孤儿文件。`TestModeDispatch.testUnknownModeThrowsNopAiAgentException()` 测试 unknown 模式分支时是直接 `new AgentModel()` 并 `model.setMode("unknown")`，完全绕过 XDSL 加载与 xdef 枚举校验，因此当前的孤儿状态掩盖了这个 schema 违约。
- **风险**: (1) 若未来有人按文件名直觉补一条 `loadComponentModel("/test-unknown-mode-agent.agent.xml")` 以"补全 XDSL 层面的 unknown 模式回归"，会立即在 `XDefinitionParser` 阶段抛出枚举校验异常，与文件名暗示的语义相反。(2) 任何对 `_vfs/test-*` 做批量 schema 健康检查的 CI 步骤/平台工具会因此文件失败。(3) 误导开发者：以为 Nop 平台允许 `mode` 取任意字符串。
- **建议**: 删除该孤儿文件（真实的 unknown 模式回归已由 `TestModeDispatch.testUnknownModeThrowsNopAiAgentException` 在 Java 层覆盖）；或若确需保留"加载即失败"的负向 XDSL 用例，则改名为 `test-invalid-mode-agent.agent.xml`、在文件顶部加注释明确意图，并补一个 `assertThrows` 的加载测试真正消费它。
- **信心水平**: 高（0.9）
- **误报排除**: 这是 xdef:enum 强约束下的客观违约（结构性原因），且叠加孤儿状态形成维护陷阱（可量化风险：未来按名引用即崩）。不属于"`_` 前缀生成文件"豁免（本文件是手写测试 XDSL，无 `_` 前缀，非生成产物）。与 dim05-04 同一问题，从 XDSL 正确性角度立项。
- **复核状态**: 未复核

### 其他 XDSL 文件合规性核查（零发现）

- **agent.register-model.xml（合规）**: `x:schema`/`xmlns:x` 正确；`xdsl-loader fileType="agent.xml" schemaPath="/nop/schema/ai/agent.xdef"` 满足必需属性，schemaPath 存在，与 agent.xdef 根 `xdef:name="AgentModel"`/`xdef:bean-package="io.nop.ai.agent.model"` 一致。
- **agent-plan.register-model.xml（合规）**: 三个 loader 配置正确；`optional="true"` 与 `mappingName` 满足 schema；`class` 真实存在；`mappingName` 遵循 `{modelName}.{mappingName}` 约定。（注：`nop-record-mapping` test scope 与 main 资源的依赖范围关切由 dim05 处理，XDSL 配置本身语法/语义合法。）
- **agent-plan.record-mappings.xml（合规）**: 根 `x:schema` 存在；命名空间声明与平台 record-mappings 标准约定同构；`x:post-extends` 中 c:import 与 `<record-mapping-gen:GenReverseMappings/>` 配对正确；mapping name 与 register-model 引用精确匹配；`dict="io.nop.ai.agent.model.AgentExecStatus"` 指向存在的枚举类并由测试覆盖。
- **test-agent.agent.xml（合规，被实际加载）**: 被 `TestAgentModelLoading.testLoadAgentModel` 加载。
- **test-plan-agent.agent.xml（合规）**: `mode="plan"` 在枚举内；schema 合法（孤儿但 schema 合法）。
- **test-react-agent.agent.xml（合规，被实际加载）**: 被 `TestEndToEndReAct` 加载。
- **test-single-turn-agent.agent.xml（合规）**: `mode="single-turn"` 在枚举内；schema 合法。
- **生成类 _gen 头部 xdef 路径引用**: 按口径第 7 条不作为 dim10 发现对象。`model/_gen` 下 7 个陈旧 plan 类头部引用 `/nop/schema/ai/agent-plan.xdef`——xdef 路径本身正确，但 Java 包与当前 xdef bean-package 不一致，属陈旧生成产物，已由 dim04/05 立项，按口径第 2 条不重复立项。

### 未发现

x:extends 误用、x:override 语义错误、命名空间缺失、bean 定义与 Java 类路径不一致、引用不存在的资源路径、xbiz 方法签名不兼容（本模块无 xbiz 文件）。

## 复核结论表

| 发现 ID | 文件 | 严重程度 | 信心 | 复核状态 |
|---------|------|---------|------|---------|
| 维度10-01 | `src/test/resources/_vfs/test-unknown-mode-agent.agent.xml:1-8` | P3 | 高（0.9） | 未复核 |

**维度小结**: XDSL 文件总数 8（主资源 3 + 测试资源 5）；发现数 1（P3×1）；合规 7。
