# 维度 08：IoC 与 Bean 配置 — nop-ai-agent

## 第 1 轮（初审）

### [维度08-1] `ai-agent-tools.beans.xml` 是死代码：没有任何 IoC 加载路径，文件注释与设计文档声称的 "auto-collected by `<ioc:collect-beans>`" 不成立

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml:1-38`（全文），以及 `ai-dev/design/nop-ai-agent/01-architecture-baseline.md:85`、`ai-dev/plans/225-.../227-.../239-...` 中多处类似声明
- **证据片段**:
  ```xml
  <!-- beans.xml 头部注释 -->
  <!-- Engine-aware tool executors that require access to IAgentEngine / IAgentMessenger.
       These beans are auto-collected by the existing <ioc:collect-beans by-type="...IToolExecutor"/>
       in nop-ai-toolkit's ai-tools-defaults.beans.xml. -->
  <bean id="ai-agent-tools:call-agent" class="io.nop.ai.agent.tool.CallAgentExecutor"/>
  ```
  对比 nop-ai-toolkit（它的 beans.xml 真正能被加载）：
  ```
  $ cat nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/autoconfig/nop-ai-toolkit.beans
  /nop/ai/beans/ai-tools-defaults.beans.xml
  ```
  nop-ai-agent 完全没有对应的 autoconfig 注册：
  ```
  $ find nop-ai/nop-ai-agent/src/main/resources/_vfs -name "*.beans" -o -name "_module"
  (无输出)
  $ grep -rn "ai-agent-tools" --include="*.java" --include="*.beans" --include="*.xml" --include="*.yaml" \
      --include="*.properties" .  | grep -v "/target/" | grep -v "/_dump/" | grep -v "/ai-dev/"
  (只命中 beans.xml 自身 10 个 bean 定义，无任何外部引用)
  ```
  测试代码全部手动 `new`，不经容器：
  ```java
  // src/test/java/.../TestActorRuntimeEndToEnd.java:312
  SendMessageExecutor sendMessageExecutor = new SendMessageExecutor();
  // src/test/java/.../TestSubAgentPermissionWiring.java:166
  CallAgentExecutor executor = new CallAgentExecutor();
  ```
- **严重程度**: P1
- **现状**: `ai-agent-tools.beans.xml` 声明的 10 个 bean（call-agent / send-message / read-memory / write-memory / search-memory / team-send-message / team-status / team-task-create / team-task-update / team-execute-flow）**从未被加载进任何 IoC 容器**。证据链：(a) 模块无 `_vfs/nop/ai/_module` 标记文件；(b) 模块无 `_vfs/nop/autoconfig/*.beans` 注册文件；(c) 模块无 `app*.beans.xml`；(d) 全仓 grep 无任何 `import resource="ai-agent-tools.beans.xml"`；(e) 全仓 grep 无任何 `loadBeans`/`BeanContainerBuilder.addResource` 程序化加载；(f) 无下游模块依赖 nop-ai-agent；(g) 测试代码 5 处全部 `new XxxExecutor()` 手动构造。`<ioc:collect-beans by-type="...IToolExecutor"/>` 在 nop-ai-toolkit 中真实存在（第 37 行），但 collect-beans 只扫描**已加载到容器**的 bean 定义，无法发现从未加载的 `ai-agent-tools.beans.xml` 里的 10 个 bean。
- **风险**: 文件头注释 + 设计基线文档 `01-architecture-baseline.md:85` + plan 225/227/239 多处声明"经 collect-beans 自动收集"——所有这些文档都**与现实不符**。未来任何集成方（或本仓 nop-ai-coder 等下游）按文档假设"只要依赖 nop-ai-agent，10 个工具就会自动注册"，实际拿到的是空的工具集，导致 team 工具、memory 工具、call-agent 工具全部静默丢失。这是跨模块契约漂移 + 会误导后续开发的公开面问题。注：当前未爆是因为没有任何下游模块依赖此模块；一旦集成即触发。
- **建议**: 二选一：(1) 若确实想用 auto-collect 机制，新增 `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/autoconfig/nop-ai-agent.beans` 文件，内容为 `/nop/ai/beans/ai-agent-tools.beans.xml`，与 nop-ai-toolkit 同款机制（最小改动，一行注册）。(2) 若模块定位是"纯库，由集成方手动注册"，则修正文件头注释 + 设计基线文档第 85 行 + plan 225/227/239 中所有"auto-collected"措辞为"由集成方手动注册或 autoconfig 启用"，避免文档欺骗。推荐方案 (1)。
- **信心水平**: 确定
- **误报排除**: 这不是"NopIoC 静默扫描 classpath"误报——`docs-for-ai/02-core-guides/ioc-and-config.md:125` 明确"Nop 应用侧 bean 发现默认是基于文件，不是基于 Java classpath scanning"。也不是"collect-beans 跨容器扫描"误报——collect-beans 是 bean 属性注入时的 by-type 收集，作用域是当前容器内已加载的 bean 定义，未加载的文件不参与。也不是"模块定位是纯库故无 autoconfig 是正常"误报——同仓 nop-ai-toolkit 同样是"工具库"，却提供了 autoconfig 注册，说明 autoconfig 是工具库的标准装配方式。
- **复核状态**: 未复核

---

### [维度08-2] `ai-agent-tools.beans.xml` 缺少 `x:schema` 声明且 `xmlns:ioc` URI 与平台标准不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml:1-3`
- **证据片段**:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <beans xmlns:x="/nop/schema/xdsl.xdef"
         xmlns:ioc="urn: nop-ioc:1.0">
  ```
  对照同仓内核约定（7 个其他 beans.xml 采用）：
  ```xml
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc">
  ```
  nop-ai 内部分裂（同一子系统两种 URI）：
  ```
  $ grep -rh "xmlns:ioc" nop-ai --include="*.beans.xml" | sort | uniq -c
     7  xmlns:ioc="ioc"          ← 平台标准
     6  xmlns:ioc="urn: nop-ioc:1.0"  ← nop-ai 局部变体（含本文件）
  ```
- **严重程度**: P3
- **现状**: 文件未声明 `x:schema="/nop/schema/beans.xdef"`；`xmlns:ioc="urn: nop-ioc:1.0"`（注意 `urn:` 后还有一个空格）与平台标准的 `"ioc"` 字符串约定不一致。前次审计 `10-xdsl-xlang.md:82-101` 已记录此问题，复核结论 `保留 P3`，本轮维度 08 复核确认**文件未修改，问题仍在**。
- **风险**: (a) 校验盲区——任何按 x:schema 识别 XDSL 资源的工具（IDE 校验、CI 校验、文档生成）会漏掉此文件；(b) nop-ai 内部命名空间分裂。前次审计已确认运行时 fallback 路径，故运行时不会出错，定级 P3。本文件全文未出现任何 `<ioc:*>` 元素，所以 URI 字符串实际是死代码。
- **建议**: 改为 `<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc">`，与平台核心约定对齐。若 [08-1] 选择"新增 autoconfig 让文件真正生效"方案，则本项应一并修正。
- **信心水平**: 高
- **误报排除**: 这不是"运行时崩坏"误报——前次审计已读 `BeanContainerBuilder.addResource` 确认有 fallback 路径，故 P3。也不是"重复报告已收敛问题"——前次审计复核结论是 `保留 P3`（未收敛），且本题指令要求独立执行维度 08。
- **复核状态**: 未复核（前次审计 10-4 已复核为 P3，本轮确认未修复）

---

## 零发现项（已检查且合规）

| 检查项 | 结论 |
|---|---|
| **beans.xml 的 class 路径正确性** | 合规。10 个 bean 的 class 路径全部与 Java 类一致 |
| **bean id 命名约定** | 合规。10 个 bean 全用 `ai-agent-tools:*` 前缀，不与 `nop*` 冲突 |
| **collect-beans 配置真实性** | 合规。`nop-ai-toolkit/ai-tools-defaults.beans.xml:37` 真实存在 |
| **生成文件手改检查** | 合规。模块内无 `_*.beans.xml` 生成产物 |
| **Java 代码 `@Inject` 注入方式** | 合规（无注入）。grep `@Inject` 在 `src/main/java` 返回 0 命中。模块 main 代码完全不使用 NopIoC 容器注入 |
| **Spring 注解误用** | 合规（零误用）。grep `@Component`/`@Service`/`@Autowired`/`@Value`/`org.springframework` 全部 0 命中 |
| **_module 注册检查** | 合规（register-model schema 引用真实存在） |
| **循环依赖检查** | 合规（beans.xml 无 import，无构造期循环） |

## 专项判断：DefaultAgentEngine 的 setter 注入模式是否合规

**判断结论**：**合规，不计为问题。这是模块的合理 opt-in 扩展点设计，与架构基线一致。**

**判断依据**：
1. 这些 setter 不是 NopIoC 容器注入（无 `@Inject` 注解），全部由业务/集成代码程序化调用，setter 内含 handler 注册/注销等有状态副作用逻辑。
2. 与架构基线 `01-architecture-baseline.md` §六多处明确声明的 opt-in 扩展点模型完全一致（setMessenger/setMailboxFactory/setContributionRegistry/setActorRuntime/setSessionTakeoverLock 等都有 NoOp 兜底实现保证零回归）。
3. NopIoC 文档明确支持 setter 注入。
4. 设计意图是"四层接口扩展通过添加接口实现，不通过阶段切换"。改为构造器注入会强制所有集成方一次性提供全部扩展点实现，破坏 opt-in 语义。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
