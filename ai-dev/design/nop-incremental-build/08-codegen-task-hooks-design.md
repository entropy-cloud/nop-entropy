# nop-ib 声明式代码生成任务钩子（codegen task hooks）设计

**范围**：`nop-tools/nop-ib`（配置加载 / 任务执行器 / planner 接入）
**状态**：active（规范性文档，只描述当前有效设计；变更历史见 git 与时效性文档）
**上游**：[05-nop-ib-engine-design.md](05-nop-ib-engine-design.md)（能力分级 L0–L3 与回退矩阵、classpath 物化）、[07-service-mode-design.md](07-service-mode-design.md)（服务化并发模型——本档不改变其约束）

> 本档定义 ib 的通用任务钩子机制：项目以声明式配置描述"代码生成任务"（输入/输出/执行体/触发点），ib 负责增量判定与执行。ib 不认识 xgen、不解析 Maven 插件配置——nop 平台只是该机制的一个使用方。

## 一、设计结论

| 编号 | 决策 | 理由 |
|---|---|---|
| IB-T1 | 任务以**项目自声明的配置文件**描述：文件名 `nop-ib.tasks.json`（辨识性命名，与 Maven 体系零关联）；放在 **repo root 或任意模块 basedir** 下，每个子项目可选自有文件；根级 + 模块级合并，**同名任务模块级覆盖根级**（模块可只改 `exec` 部分字段做局部覆写） | ib 是通用 Maven 项目增量引擎，配置必须 Maven 无关（liteflow 等外部项目同样可用）；分层可选让 nop 的每个 `-meta/-dao` 模块自带声明，与 Maven per-module 插件配置的使用习惯对齐；辨识性文件名保证在模块目录里一眼可辨，不与构建工具产物混淆 |
| IB-T2 | classpath 由 ib 自建，语义对齐 exec-maven-plugin：`exec.classpathScope` ∈ `compile \| runtime \| test`（默认 `test`）；`compile/runtime` → `ClasspathMaterializer` 的 main classpath，`test` → test classpath | nop 平台全部 exec 声明都用 `classpathScope=test`（ib 可等价复刻）；模块 classpath = 依赖闭包的 reactor 输出目录 + externalArtifacts，比 Maven 拼得更准（增量场景下刚编译完的模块立刻可见）；external artifact 缺失 → fail-loud 回退 Maven，不静默 |
| IB-T3 | 执行体两种：`exec.type = "java"`（项目 classpath 上的 mainClass + arguments）与 `exec.type = "xscript"`（项目 classpath 上的 XLang runner fork JVM 执行 `.xscript` 脚本）；**ib 不内嵌 XLang 引擎** | nop 的 xgen/precompile 本来就是普通 Java main（`java` 类型全覆盖）；xscript 依赖 nop-core 的 XLang 运行时，而它就在项目 classpath 上——fork JVM 用项目 classpath 天然可得；ib 若内嵌 XLang 则依赖面膨胀且破坏"通用工具"边界 |
| IB-T4 | 触发点声明式挂接：`trigger ∈ before-compile \| after-compile`；增量判定 = inputs/outputs 路径集的**内容指纹**，指纹未变且 outputs 存在 → 跳过；指纹缓存与任务临时文件全部落 `<module>/target/nop-ib/tasks/` | before-compile 覆盖 generate-sources 场景；after-compile 覆盖"需先编译再生成"，使"模板变更必须整体回退 Maven"的最大痛点可留在快路径；落 target/ 的理由：`mvn clean` 自动清理，任务产物语义 = 可重建的派生物，与 target/ 语义一致 |
| IB-T5 | 任务输出登记为**生成产物**，走既有 `_gen` 排除通道（永不进入快路径变更分类）；**拥有任务声明的模块自动按 L1 分级参与回退矩阵**，不再依赖 `hasXgenTemplates` 文件扫描（该扫描保留为无声明时的保守探测） | 生成产物 = 可重建派生物，与 05 §七 L1 的既有语义严格一致；声明替代启发式探测 = 少一份漂移面 |
| IB-T6 | 失败语义 fail-loud：任务执行失败、classpath 不完整、outputs 校验缺失 → 一次任务失败即该模块本轮**显式回退 Maven**（fallback reason 写明任务名与失败摘要），绝无静默跳过 | 与 roadmap 硬约束 1（零假绿）一致；生成缺失比生成过时更危险 |

## 二、配置契约

```jsonc
// nop-ib.tasks.json — repo root 或任意模块 basedir 下；UTF-8；数组
[
  {
    "name": "nop-xgen",                      // 必填，模块内唯一；同名模块级覆盖根级
    "trigger": "before-compile",             // before-compile | after-compile，必填
    "inputs":  ["model/*.orm.xml", "**/gen-*.xgen"],   // 相对模块 basedir 的 glob，必填
    "outputs": ["src/main/java/**/_gen/**"],           // 相对模块 basedir 的 glob，必填
    "exec": {
      "type": "java",                        // java | xscript
      "mainClass": "io.nop.xgen.XgenRunner", // java 必填
      "script": "gen/model.xscript",         // xscript 必填（相对模块 basedir）
      "arguments": ["precompile"],           // 可选，附加参数
      "classpathScope": "test"               // compile | runtime | test，默认 test
    }
  }
]
```

- 发现规则：从 repo root 到目标模块 basedir 的路径链上，每个目录的 `nop-ib.tasks.json` 都参与；根级先装、模块级后装（覆盖同名）。合并语义：**非 exec 顶层字段整体替换、exec 逐字段合并**（模块级只写要改的字段）。模块级文件只对**该模块**生效；根级文件对全部模块生效（inputs/outputs 相对各自模块 basedir 解析）。
- 校验 fail-loud：未知字段拒绝（防拼写漂移）、inputs/outputs glob 编译失败拒绝、`exec.type` 未知值拒绝——配置错误是显式 usage error（exit 3，`NopIbException` 全局捕获映射），不是静默忽略。
- xscript 的 runner 入口复用 `mainClass` 字段（不设专用字段）：xscript 执行 = 项目 classpath 上的 `mainClass` + script + arguments 的 java fork——runner 只是语义角色，执行形态与 `java` 类型同构。
- 模型关系：任务声明与 `model.json` 正交（不进 pom 哈希）；但模块的 capability 分级在运行时叠加任务声明（IB-T5）。

## 三、执行语义（伪代码规格）

```
runTasks(module, trigger, ctx):
  tasks := mergedTasks(repoRoot, module)            // IB-T1 合并结果
  for t in tasks where t.trigger == trigger:
    fpFile := module.target/nop-ib/tasks/<t.name>.fp.json
    inHash  := hashOf(glob(t.inputs, module))
    outState := existenceOf(glob(t.outputs, module))
    if fpFile matches {inHash, outState}: continue     // 增量跳过
    classpath := materialize(t.exec.classpathScope, module)   // IB-T2
    result := exec(t.exec, classpath, cwd=module.basedir)     // IB-T3, fork JVM
    if result.exit != 0 → plan.fallback("task <name> failed: <tail of stderr>")
    outState' := existenceOf(glob(t.outputs, module))
    if outState' != outState declared non-empty → fallback("task produced no outputs")
    write fpFile {inHash, outState'}
```

- `after-compile` 任务看到的 classpath 含本轮快路径刚编译的模块输出目录——这是"编译后再生成"成立的前提。
- 任务执行串行（per-module，在既有 lane 内），不做任务间并行；正确性优先。
- 服务模式零改动：任务在 job 执行路径内（check/compile 命令流程），lane 串行语义天然覆盖。

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 直接支持 exec-maven-plugin（解析其插件配置） | 任意执行面 = 复刻 Maven 插件配置模型；ib 被绑死 Maven 生态，外部项目不可用；维护第二套插件语义违反 roadmap 非目标 |
| 配置写进 pom（新 Maven 扩展/mojo） | 配置与 Maven 生命周期耦合后，ib 每次读取都要 effective-pom 全链路；且"构建事实只由 Maven 导出"的原则是关于编译/测试事实，任务声明是 ib 的扩展面，不应反向渗入构建系统 |
| ib 进程内嵌 XLang 引擎执行 xscript | nop-ib 依赖面必须极小（roadmap 硬约束 8）；项目 classpath 上本就有 XLang 运行时，fork JVM 零成本获得且隔离更干净 |
| 临时文件放 `.nop-ib/` | `.nop-ib/` 存持久状态（模型/索引/缓存），任务中间产物是可重建派生物，语义上属于 target/；`mvn clean` 自动删除目标也是 owner 期望 |
| 任务间并行调度 | 单模块任务数个位数，并行收益近零而互斥复杂度真实；正确性优先 |

## 五、与已有设计的关系

- **05 引擎设计**：L1 分级与回退矩阵的扩展面——任务声明是 L1 判定的声明式来源（IB-T5），`_gen` 排除通道复用不变；classpath 物化器复用（IB-T2）。
- **07 服务模式**：任务执行发生在 per-project lane 内的 job 路径中，并发模型零改动。
- **roadmap 硬约束**：零假绿（IB-T6）、不替换构建系统（任务失败回退 Maven）、模块边界（不内嵌 XLang）全部继续成立。
