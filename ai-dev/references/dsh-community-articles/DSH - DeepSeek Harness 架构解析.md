# DSH：DeepSeek Harness 架构解析

> 来源: https://mp.weixin.qq.com/s/Kf87hcNdSmY4ODWI4UZ8cg
> 作者: lencx
> 抓取: 2026-08-19（curl 原始 HTML + stdlib 转换；图片未保留；标题/作者经页面 og 元数据核验与链接一致）

本文基于 DSH[1]47f9438（含其 vendored Cordis 补丁）与独立 Cordis[2]8cc9e33，并参考 OpenClaw[3]f70d5b8、Pi[4]6f707eb、Hermes-Agent[5]fa83af3、Codex[6]fe614a6 及 88 页《面向时空可组合性的编程范式》论文 paper[7]948a07b。讨论聚焦源码中的架构与实现；DSH 尚处开发者预览阶段，当前 API 与论文目标模型均不视为稳定承诺。

在已经安装 Node.js 开发工具链的环境中，可以先通过 npx 启动 DSH 的 Web UI，无需预先进行全局安装：

```
npx @deepseek-ai/dsh web

```

DeepSeek Harness（下文简称 DSH）用“一切皆插件”概括自己的架构。源码基本支持官方宣传，但“插件很多”还不足以解释 DSH。

DSH 想插件化的不只是工具。模型适配器、系统提示词、工具目录、会话、存储、沙箱、Agent loop、Web UI，乃至标准、PTC、极简和创造模式，都尽量通过同一种机制组合。真正有辨识度的不是包很多或每个包都有 apply()，而是 Cordis 同时处理了五个通常彼此分离的问题：

- 一个插件在当前位置能看到哪些能力；

- 必需能力尚未出现时，插件是否应该启动；

- 同名 service 能否在不同会话或作用域中解析到不同实现；

- 插件退出时，注册项、监听器、进程和句柄由谁清理；

- 静态配置怎样变成一棵可以更新和检查的运行时插件树。

更合适的理解方式，是把 DSH 看成两套同时运行的系统：

- Cordis 维护一张运行时插件图。这张图描述当前有哪些能力、它们依赖谁、在哪个作用域生效、卸载时怎样清理。

- Session 维护一份追加式事件流。这份日志记录一次 Agent 工作实际发生了什么，并投影出模型上下文、UI 轨迹、恢复与分叉所需的数据。

Agent loop 位于两者之间。它从插件图取得模型、工具、提示词和会话服务，再把执行过程写回事件流。

Cordis 解决“系统现在由什么组成”，Session 解决“系统刚才做过什么”。这两条线合起来，才是 DSH 的主体。

这个区分也解释了为什么一个产品级 Harness 不能只看 Agent loop。最小 loop 只需拼 prompt、调用模型、执行工具并把结果送回模型；完整产品还要处理模型与凭证、会话恢复和压缩、工具策略与审批、沙箱与远程执行、多种宿主、子 Agent、插件启停，以及 UI 如何观察同一份运行事实。DSH 主要把后一组组合复杂度交给 Cordis，把历史连续性交给 Session。

## 从配置到运行图

DSH 启动时没有直接构造一个固定的 Agent 应用。它先读取 Profile 与 Bundle，再叠加用户 Patch，由 Cordis Loader 将配置行逐条挂载成插件实例。

这里有三层配置概念：

- Bundle：分发一组 Cordis 配置和对应的插件代码。

- Profile：决定一个进程堆叠哪些 Bundle，内置模板包括 web 与 headless。

- Patch：替换或插入配置行，用于用户配置和命令行覆盖。

Loader 处理后的结果不是普通配置对象，而是一棵正在运行的插件树。配置行决定插件名称、父子位置、参数、启停条件和 service 隔离域；配置变化后，相应插件实例也可以重新配置、卸载或挂载。

Profile 的各层不是模糊地“合并一下”。源码从空 entry list 开始，依次应用 Profile 声明的 Bundle、Profile 自己的 cordis.patch.yml、Harness home 下的 patch，再应用命令行 --patch；如果启动参数关闭 telemetry，launcher 还会在其后叠加对应的 override。后层按配置行 id 替换整段配置或插入新行。因而调试启动问题时，源码 import 图只表示“可能加载什么”，dsh --dump-config 输出的最终树才表示该机器实际会挂载什么。

这使 DSH 不只支持开发时扩展，也具备了部署时装配的能力。若更换一项实现仍要修改启动代码，选择权就在程序内部；当部署方可以直接通过配置替换它，替换本身才成为系统提供的能力。

DSH 还区分了宿主组合与单个 Agent 的组合。

宿主层持有进程共享的注册表、持久化、沙箱与审批、模型路由等能力。Agent Preset 挂在会话作用域中，主要贡献该会话使用的工具、人格、提示词片段和少量隔离 service。Preset id 还会写入 Session header；恢复会话时必须重新使用同一组合，否则历史中已经出现过的工具和提示词可能与当前 Agent 能力不一致。

这也是理解官方四种“模式”的前提（标准、PTC、极简、创造）。源码中的组合实际有两条轴：

- Runtime Profile 有 web 和 headless 两种内置选项，决定整个进程以 Web 应用还是无 Web UI 的一次性 runner 运行。

- Agent Preset 有 standard、code、minimal 和 cordis 四种内置选项，决定某个会话看到的工具、提示词与局部能力。

所以，四种模式属于 per-session preset，不是四套互斥的进程启动方案。一个 Web 进程可以同时承载使用不同 preset 的会话。

## Cordis 运行时

一份 YAML 能描述插件树，仍然没有回答运行时问题：依赖未就绪怎么办，同名能力怎样隔离，Provider 被替换后谁需要重启，插件制造的副作用由谁回收？

下面把 minimal preset 中的文件系统组合缩成一个例子。为便于阅读，工具源码已有的 inject 被显式写进 Entry，并故意把 Consumer 放在 Provider 前面：

```
# agent.cordis.yml
- id: filesystem
  name: cordis:group
  group: true

  # 为这棵子树创建私有的 fs service realm。
  isolate:
    fs: true

  config:
    # Consumer：tools 或当前 realm 的 fs 缺失时，Fiber 保持 PENDING。
    - id: editor
      name: '@deepseek-ai/dsh-tool-str-replace-editor'
      inject: [tools, fs]
      config:
        maxOutputChars: 16000

    # Provider：在上面的私有 realm 中发布 ctx.fs。
    - id: fs-provider
      name: '@deepseek-ai/dsh-fs-local'
      config:
        cwd: !!js process.env.DSH_CWD ?? process.cwd()

```

isolate.fs: true 让这个 group 拥有 entry-local realm，另一个 group 可以发布自己的 fs 而不冲突。inject 让 editor 在 tools 和 fs 都可解析之前保持等待，因此 YAML 行顺序不承担启动顺序；Provider 出现后，Cordis 才激活它。在宿主已经提供 sandboxPolicy 的 DSH 组合中，若把 fs-provider 这一行替换为 @deepseek-ai/dsh-fs-sandbox，Provider Fiber 的身份变化会刷新 Consumer 的依赖 epoch，使旧 editor Fiber 先卸载，再用新 fs 重载。

YAML 仍然没有描述“怎样关闭”。这部分留在插件代码中：Provider 和 Consumer 通过 ctx.effect()、ctx.provide()、ctx.on() 登记 disposer，Cordis 在 Fiber 卸载时执行它们。换言之，YAML 声明期望拓扑，Context、Fiber 与 effect 负责让拓扑在运行时成立并安全退出。

Cordis 用 Context、Service、Fiber 与 effect 共同维护这些关系。它们不是四层互不相关的抽象，而是一套插件节点的运行规则。

这也是 Cordis 与普通“插件表 + 事件总线”的差别。下面这种注册循环足以添加几个工具，却没有表达作用域、依赖和退出责任：

```
for (const plugin of plugins) {
  plugin.register(app)
}

```

系统扩大后，启动器就会逐渐承担本不属于它的工作：手工排列 A、B、C 的启动顺序，为每个注册项另写卸载逻辑，为两个 Agent 复制并隔离状态，替换 Provider 后追踪旧引用，再另外维护一份“当前到底加载了什么”的诊断表。Cordis 把这些关系收进运行时模型，而不是继续给注册循环增加条件分支。

### Context 与 Service

DSH 插件通过 ctx.tools、ctx.llm、ctx.sessions 等 service 名称协作。消费者依赖的是能力接口，不直接绑定具体实现。

ctx 看起来像装满单例的对象，实际是经过 Proxy 包装的 service 解析边界。Context 不只保存“有什么”，还携带父作用域、隔离域、依赖声明和当前 Fiber。子 Context 默认继承父级 service；isolate() 可以为某项 service 建立独立 realm。两个会话即使都访问 ctx.tools，也可以得到各自的工具目录。

Cordis 使用 Proxy 处理 Context 上的 service 访问。通过 ctx.foo 直接读取未声明在 inject 中的 service 会报错；显式调用 ctx.get("foo") 是较底层的读取入口，不受这项声明检查。必需依赖缺失时，插件不会进入活跃状态。

这是一种依赖约束，不是权限控制。原生 JavaScript 插件仍在宿主进程中执行，未注入 fs 不代表它在操作系统层面失去文件访问能力。

在 DSH 中，一条可替换能力通常有三个角色：

- Service Definition 定义接口和公共语义；

- Service Provider 提供本地、远程、沙箱或测试实现；

- Consumer 通过当前 Context 使用该能力，常见消费者是模型可见工具。

三者合起来才构成 capability seam。以文件系统和子进程为例，它们共享一套 execution world；把 Provider 指向远程沙箱，可以连带移动 Bash、PTY 和 LSP，而不是分别 fork 每个工具。isolate 又允许这种替换只作用于某个 Agent，而不是全进程切换。

### Fiber：插件实例

Plugin 是可复用定义，一次实际挂载则对应一个 Fiber。同一个 Plugin 使用不同配置、挂在不同 Context 下，会得到多个 Fiber。

Fiber 是 Cordis 为这次插件运行创建的实例记录，它把依赖解析、生命周期和资源所有权收束到同一个对象中，主要保存：

- 父 Context 与当前 Context；

- 插件配置和 inject 依赖；

- 当前解析到的 service 实现；

- 加载、活跃、卸载等生命周期状态；

- 运行期间登记的清理函数。

Fiber 会根据依赖计算自己的激活状态。必需 Provider 尚未出现时，它停在等待状态；Provider 就绪后加载。Provider 消失或更换实现时，Cordis 通知相关 Fiber，重新计算依赖并触发卸载或重载。

因此，Cordis 维护的不是启动时解析一次的依赖注入，而是一张会随 Provider 变化而刷新的运行图。Plugin 是代码定义，Fiber 才是“这份定义在这个父 Context 下，使用这份配置和这组依赖”的活实例。所谓依赖顺序也不再只是 boot 脚本中的先后关系，而是运行时持续维护的状态。

### effect：副作用归属

插件加载通常会产生注册项、事件监听器、定时器、文件句柄或后台进程。Cordis 要求插件在创建这些资源时，同时登记对应 disposer。与单独维护 activate() / deactivate() 相比，资源获取和释放写在同一个 effect 中，不容易随着功能迭代而失配。ctx.on()、ctx.provide() 等框架辅助方法自身也接入了 effect 所有权。

DSH 的 JSON 存储插件可以把这套机制串起来：

```
export const inject = ["storage"]

export function apply(ctx, config) {
  const backend = new JsonStorageBackend(config.root)

  ctx.effect(() => {
    const unregister = ctx.storage.backend.register("json", backend)

    return async () => {
      unregister()
      await backend.close()
    }
  })

  ctx.provide("storageBackend:json", backend)
}

```

inject 让插件等待存储中心，Context 为它解析当前作用域中的 storage，effect 把注销和关闭 backend 归到本 Fiber，provide 再将 JSON backend 暴露给其他消费者。

单次 ctx.effect() 内收集的 disposer 会按逆序串联，适合处理“后创建的资源依赖先创建的资源”一类关系。Fiber 整体卸载时，DSH 内置的 Cordis 会并发启动多个顶层 effect 的清理并等待它们全部结束，因此不能把所有顶层 disposer 理解为一条严格串行的 LIFO 栈。

effect 的能力止于声明边界。插件没有登记的进程和句柄不会自动消失，已经提交到外部系统的事务也不会因 Fiber 卸载而撤销。它提供结构化资源管理，不提供事务级回滚。

### 与 React 的类比

React 的类比是论文主动建立的。论文以 useEffect 说明依赖调用顺序和隐藏运行时状态的 effect 定位方式，又比较了两者的 setup/cleanup 与组合能力。Cordis 通过显式 Context 确定 effect 归属，并允许 effect 进入普通控制流、异步函数和 iterator。这属于设计层的参照；源码与提交历史没有显示 Cordis 移植了 React Fiber。React Fiber 服务于 UI 协调和渲染调度，Cordis Fiber 管理插件依赖、生命周期与资源回收。

两边的源码结构可以压缩成下面两个示意。

React 在 render 期间按调用顺序维护 Hook 链。useEffect 将 Effect 记录追加到 Fiber 的循环链表；挂载或依赖变化时，setup 才会在提交后的 passive-effect flush 中执行：

```
// React 源码结构的简化示意，不是可运行代码
currentlyRenderingFiber.memoizedState
  -> hook1 -> hook2 -> hook3

currentlyRenderingFiber.updateQueue.lastEffect
  -> circularEffectList

useEffect(create, deps)
  -> 按调用顺序取得或创建 Hook
  -> 记录 { create, deps, inst: { destroy } }
  -> 挂载或 deps 变化时标记为待执行 Effect
  -> passive-effect flush 调用旧 destroy，再执行 create()
  -> 将新的 cleanup 保存到 inst.destroy

```

DSH vendored Cordis 没有 Hook dispatcher。ctx.effect() 是当前 Fiber 上的普通方法，调用时立即启动 setup，并由一个 wrapper 持有产生的 disposer：

```
// DSH vendored Cordis 源码结构的简化示意，不是可运行代码
fiber._disposables = [wrapperA, wrapperB, wrapperC]

ctx.effect(execute)
  -> 先将 wrapper 登记到当前 Fiber
  -> 立即启动 execute()
  -> wrapper 收集 execute 产生的 disposer
  -> 提前调用 wrapper 时，等待进行中的 setup 后执行清理
  -> wrapper 只清理一次

```

两者都把 Effect 记录归属到生命周期节点，并由运行时跟踪 cleanup。React Fiber 是带 child、sibling、alternate 和 lanes 的渲染工作节点；Cordis Fiber 保存插件配置、inject、service 绑定、生命周期状态和 disposer。

依赖变化的处理粒度不同。React 先重新 render，再比较 useEffect 的依赖数组，决定是否清理并重建 Effect；Context value 变化可以调度 Consumer render。Cordis 在 service 变化时主动刷新依赖，把 Provider Fiber UID 编入 Consumer epoch；UID 变化会触发整个 Consumer Fiber 卸载和重载。

Cordis ctx 同时承担命名 service、realm 隔离、依赖声明和 effect 归属。ctx.on()、ctx.provide() 是建立在 fiber.effect() 上的生命周期辅助方法，可以出现在条件、循环和嵌套函数中。Cordis effect 还返回显式 disposer，并支持 Promise、同步 iterator 和异步 iterator；这些能力不受 React Hooks 调用规则约束。

### Event：流程拦截

Service 适合“明确调用一个能力”，Event 适合观察、决策和包裹一段过程。Cordis 不是只有一种广播方式：

- emit 同步通知全部监听器并忽略返回值，适合状态变化通知。

- parallel 并发执行并等待全部监听器，适合彼此独立的异步观察者。

- serial 顺序执行监听器，遇到第一个有效结果便停止，适合有优先级的异步决策。

- bail 是 serial 的同步版本，用于快速的同步决策。

- waterfall 允许监听器通过 next() 包裹余下链路，也可以改写或短路流程，适合请求、模型流与工具中间件。

监听器同样归注册它的 Fiber 所有，因此插件卸载会移除监听器。DSH 使用 waterfall 处理 agent/pre-step、agent/request、llm/stream 和工具执行前后的扩展点；agent/turn-stopping 则使用 serial。审批、提示词注入和 Provider 适配可以进入已有 seam，而不必不断给默认 loop 增加条件分支。

### Loader：配置挂载

Loader 负责连接配置与上述运行规则。一个配置 Entry 被导入后，Loader 解析插件、修补 Context，再通过 Registry 创建 Fiber。Entry 被禁用、修改或删除时，Loader 对相应 Fiber 执行更新或 dispose。

因此，cordis.yml 不是“给固定程序填几个参数”，而是在声明装哪些插件、挂在什么父节点、采用什么配置、哪些 service 建立新 realm，以及哪些组合在当前环境启用。若源码支持扩展、部署时却不能替换 Provider，使用者仍需 fork 核心；Loader 把替换从开发行为推进成了配置行为。

Cordis 所说的“时空可组合性”可以由此落到两个具体维度：

- Context 的继承与隔离决定插件在哪个位置看到哪个 service；

- Fiber 的装载、卸载与 effect 回收决定插件在什么时间生效和退出。

所谓时空组合，没有超出这些源码机制。

## 论文中的 Cordis

Cordis 论文 A Programming Paradigm for Spatiotemporal Composability 为上述机制建立了一套形式模型，说明 Cordis 的组合语义、成立条件与适用边界。其结论不覆盖 DSH 整体架构的正确性或性能。

本文涉及三份材料：

- 论文：描述时空可组合性的目标模型、性质与成立前提。

- 独立 Cordis 仓库 4.0.0-rc.8：用来观察论文与上游实现快照怎样对应。

- DSH vendored Cordis：DSH 真正运行的版本。包版本为 4.0.1，vendor 清单记录的来源基线是 4.0.0-rc.7，并叠加了 DSH 的本地加固。

下文分别以“论文”、“独立 Cordis”和“DSH vendored Cordis”称呼它们。按本文固定的源码快照，DSH 本地版本已修补 effect 设置期间重入卸载、异步清理失联、Loader 更新失败回滚、配置监听串行化等问题，与独立仓库快照存在实质差异。包版本号也无法代替实现核对，论文中的性质是否成立仍取决于代码和证明前提。

论文将组件的能力需求、环境贡献和撤销逻辑合成可由运行时持续调和的对象，并研究多个对象交错运行时，系统在什么条件下仍能恢复、正确排序并最终收敛。

### 组件级组合

论文从动态组合的粒度错位切入。函数调用、模块导入和继承关系擅长静态组合；进程与容器可以在运行时重启、替换和编排；两者之间缺少一种能在同一进程内独立装卸组件、同时保留其他运行状态的通用抽象。重启进程会丢失缓存、连接和进行中的计算，把每项能力拆成远程服务又会增加部署与通信成本。Cordis 试图填补这个组件级空档。

论文以 VS Code 插件系统和自演化 Agent Harness 作为两个动机。前者说明传统插件宿主常把可执行扩展的完整卸载推迟到进程重启；后者把问题推到更动态的场景：Harness 若持续生成并替换自身组件，每次变更都重启宿主，或让每个模块自行监听依赖变化，都难以长期维护。自演化 Harness 在论文中属于后续验证方向。

论文的 88 页主要用于建立语义、状态机和证明，API 只占很小一部分。

工程上，论文把 Cordis 分成三层：core library 实现 effect 跟踪与 coeffect 解析；component loader 负责声明式配置、调和与 HMR；Koishi、DSH 这类应用框架在上面定义领域能力。Cordis 保持领域中立，只提供组件共同运行与退出的底层语义。

### Context 范式

论文把动态组合拆成两个正交方向：

- Temporal composability（时间组合性）：组件退出后，它对环境造成的改变能否被完整撤回。源码主要落在 ctx.effect()、disposer 与 Fiber 生命周期上。

- Spatial composability（空间组合性）：组件需要什么，以及 Provider 出现、消失或更换时怎样响应。源码主要落在 inject、service realm、target、committed 与依赖通知上。

Effect 描述组件对环境做了什么，coeffect 描述组件要求环境提供什么。论文将两者递归地合并进同一种 Context：Context 同时保存当前可见环境、effect 的逆操作累加器和依赖表。组件与环境的每次受管交互都经过 Context，因此修改和依赖读取都能归因到具体组件；父 Context 还能聚合子 Context 的 effect，形成可整体退休的层级。

论文中的 coeffect 覆盖范围比“注入一个 service” 更广，可以表示任何由组件共享、并通过受控操作访问的状态位置。统一 Context 因而可以讨论路由表、资源句柄和权限等对象。组合性证明只覆盖已经表达为 coeffect 的状态，游离于 Context 之外的全局状态不在证明边界内。

作者将其称为一种 programming paradigm，因为它位于函数式显式状态传递与命令式隐式环境之间。开发者仍通过普通方法读写 ctx，省去日志、配置和 I/O 状态在函数签名中的层层传递；每次操作又归属于明确的 Context，避免散落在全局单例或 service locator 中。论文用 React useEffect 辅助定位“隐式运行时状态”这一端。Cordis 的 effect owner 由接收调用的 Context 显式确定，与 Hook 调用序号无关；这是一种范式比较，不涉及实现来源。

插件作者只需为原子 effect 给出 inverse，并声明所需的 coeffect；组合 effect 的逆序回收、Provider 解析和依赖变化后的重连由运行时处理。原本散落在 activate()、deactivate()、全局注册表和依赖监听器里的约定，由此变成结构化运行规则。

### 两条轴为何要统一

单个 effect 可逆，多个插件交错后还需要满足独立性条件。论文用 coeffect 划定 effect 的观察边界：若两组操作在各自 coeffect 下观察等价，并且正向与逆向操作可以交换，它们才可独立撤回。形式模型中的不同 key 分隔了状态位置；真实插件即使使用不同 service 名，若仍暗中改写同一个外部单例，也不满足这项条件。同一 key 上的操作则由接口定义何为“观察上相同”以及操作能否交换。

例如，向一张按键存储的路由表注册两个不同路由，通常可以交换先后顺序，也能独立撤销；向一条有顺序的 middleware chain 插入两个中间件则不同，先后次序本身会改变行为。后者不应假装成彼此独立的 effect。论文的处理方式，是把不可交换的顺序提升为组件间的 coeffect 关系，让 Provider 先于 Consumer 激活、晚于 Consumer 退出。

Effect 负责组合和回收可交换的环境变化，coeffect 依赖图约束顺序敏感的关系。两条轴统一后，论文才能把单个组件的局部恢复性质推广到多个组件交错运行的整体系统。

### 组件与 Fiber

论文中的 component 也比“一个插件函数”更严格。为了连接后面的源码，可以解释性地压缩成：

```
Component = requirements + provisions + revertible effect
Fiber     = Component 的一次运行实例 + parent + lifecycle + committed dependencies

```

第一行概括论文的 component 三元组；第二行是面向源码的说明式摘要，将论文概念与实现中的 parent、lifecycle 和 committed dependency view 放在一起。

requirements 声明组件读取哪些 coeffect，provisions 声明它可能向环境提供哪些 key，effect 则描述激活时贡献的行为及其 inverse。Fiber 为这份声明赋予运行时身份：同一组件可以实例化多次，每个实例独立记录父节点、退休标记、生命周期状态、effect 累加器和已经提交的 Provider 绑定；形式演算未纳入 realm，因此同一 key 在一个 registry 中仍只允许一个 Provider，实际 Cordis 再用 realm 放宽这个限制。

Fiber 还把代码模块、一次挂载和当前依赖快照区分开，让父子组合、Provider 身份变化和正在进行的异步切换进入同一状态机。论文先从 INACTIVE ↔ ACTIVE 的最小模型出发，再逐项放宽“切换原子完成、立即完成且不会失败”三个理想假设，引入 withdrawal、iteration、asynchrony 与 failure。已经开始切换、尚未提交为稳定状态的阶段称为 transition-in-progress，并作为全局状态的一部分参与推理。安全退出和异步重载都建立在这套完整生命周期演算上。

### 可恢复的边界

论文用 observational equivalence 定义恢复：通过系统公开操作观察，两份状态无法区分即可。堆布局和生成式 ID 无需逐字还原。

这个定义同时划出了三个边界。

第一，inverse 由插件作者提供。ctx.effect() 可以跟踪、组合并调用 disposer，却不能证明 disposer 真正撤销了前面的操作。漏掉清理、清错对象或过早释放资源，仍然是插件缺陷。

第二，一个 effect 内部按 LIFO 撤回，各项操作无需彼此独立；多个 Fiber 的效果交错后，论文要求不同组件的效果彼此独立，使当前 Fiber 的贡献可以单独撤回。直观地说，它们的正向操作与逆操作需要能够交换顺序。两个插件若直接改写同一个全局数组、环境变量或单例对象，通常无法满足这项条件。

第三，恢复只覆盖系统边界内的状态。打开文件并获得句柄，可以用关闭句柄撤回；写入共享文件的字节、发到网络的消息和已提交的支付已经越过边界，需要延迟提交、幂等协议或补偿事务。

### Provider 安全退出

Cordis 将 Provider Fiber 的身份纳入 target。Provider A 被 Provider B 替换时，即使两者返回相同对象或相同值，Consumer 也可能需要重新初始化。当前源码中的 _refresh() 会把每个已解析实现的 Fiber UID 编进 epoch。

Fiber 开始加载时还会保存一份 committed view，运行中始终读取这份已提交绑定。Provider 开始退出后，新的 Consumer 不再绑定它，已有 Consumer 仍能用原来的依赖完成 teardown。

论文给出的目标时序可以写成：

这里最重要的是“先停止提供，再真正释放”。如果 Provider 一消失就立刻关闭连接池，Consumer 的清理代码可能还需要把连接归还给已经关闭的池。

论文伪代码在 Provider 的整个 fiber.dispose() 之前设置了 dependent-drain barrier。本文固定的独立 Cordis 4.0.0-rc.8 与 DSH vendored 源码实现都更弱一些：ReflectService.provide() 的 disposer 会撤下公开绑定、通知并等待相关 Fiber，再删除 Provider 自己提交的 service；但 Fiber._unload() 会用 Promise.all 并发运行多个顶层 disposer。

这意味着 service 注册项本身会等待 Consumer，另一个顶层 effect 所拥有的连接、进程或 backend 却可能同时开始关闭。若 Provider 资源必须活到所有 Consumer teardown 完成，插件需要把资源清理与 service disposer 收进同一个外层 effect，利用内部逆序清理让 service 先退出，或设置显式 barrier。DSH 当前实现尚未提供 Algorithm 5 所描述的全 Fiber 级顺序保证。

### 异步切换

真实插件的加载可能正在等待连接、启动进程或执行异步生成器，此时依赖已经换成另一个 Provider。

Cordis 允许已经发出的异步 iteration 完成。完成后若 Fiber 的 target 已经变化，刚产生的 effect 会直接进入卸载路径，不会短暂对外宣告为稳定的 ACTIVE Provider。源码中的 inertia 保存当前切换任务，_reload() 结束后再次比较 epoch，不一致便链入 _unload()。

加载中途抛错也走回收路径：已经登记的 disposer 会被执行，错误记录在当前 Fiber，兄弟 Fiber 可以继续运行。失败隔离以 Fiber 为边界，不具备事务数据库式的全局原子性。disposer 自身失败时，当前实现会记录错误并继续处理其他清理项。

### Loader 收敛条件

论文把 Loader 看成一个 reconciler：配置树描述期望状态，Loader 通过插入、退休、更新和重新加载 Fiber，使运行图向该状态收敛。论文进一步讨论四项性质：

- Recovery exactness（恢复精确性）：卸载一个 Fiber 后，其他独立 Fiber 的贡献仍然保留；前提是 effect 两两独立。

- Ordering / resolution coherence（顺序与解析一致性）：Provider 先于 Consumer 激活，并晚于 Consumer 退出；一次加载使用同一组已提交依赖。它要求依赖声明完整，Provider 身份稳定且可追踪。

- Progress（进展性）：依赖变化后，生命周期最终到达静止状态；前提是 Provider 图无环、effect iteration 有界且 Fiber 数量有限。

- Confluence（合流性）：相同编排输入最终得到与“从头按依赖加载”一致的静止状态；此外还要求 provision 完整、没有失败 Fiber 等条件。

这些条件直接限定证明结论。循环依赖会让相关 Fiber 永久停在未激活状态；持续注册自身子 Fiber 的插件会破坏有限性；相互干扰的全局副作用会破坏恢复精确性。Cordis 可以检查和暴露部分状态，但插件作者仍需满足独立性、有界性和依赖完整性等前提。

论文还把 HMR 描述成三段式事务：识别受影响模块、找出 stale entries、备份模块缓存后替换 Fiber；重新导入失败时恢复缓存和旧 Fiber。按本次源码观察，DSH 确实加载了 Cordis HMR，并用它持续监听用户 patch 配置。不过 Web 与 Headless bundle 都明确关闭了共享的模块级 HMR，启动器只在缺少 HMR service 时挂载 root: [] 的 watch-only 实例，原因是对应 reload 生命周期尚未验证。论文可以解释 DSH 的配置热重组思路；各运行模式的完整模块热替换仍缺少验证。

### 工程外延

论文后半沿这套模型推导了几种更大的系统形态，定位为设计空间与开放问题：

- Service broker。多个 Provider 可以通过一个稳定 broker 共存，由 broker 负责负载均衡、滚动升级或跨进程调用。更换后端 Provider 时，Consumer 可以继续依赖 broker；跨进程接口仍需采用异步合同并处理调用中途失败。论文由此把 coeffect 模型延伸到服务编排，DSH 当前源码中没有通用流量治理实现。

- Capability access。inject 声明可以理解成组件的能力申请，Context proxy 充当访问中介，interception 允许宿主按组件附加只读路径、数据库权限等细粒度政策。政策变化可以绕过 Consumer 重载，因为 interception 只改变能力的调用方式。其约束范围限于经 Context 访问的能力；恶意代码仍需进程、WebAssembly 或容器沙箱。

- 语言与操作系统协同。论文认为这套范式可以扩展到 TypeScript 之外：宿主语言需要能保存 inverse、动态引入和退出代码，并能对依赖访问做类型声明与运行时拦截。若语言或操作系统原生理解 Context，还可以在编译期发现依赖环、把文件描述符等资源直接归属到组件，甚至让持久写入具备事务回滚语义。这些属于未来方向。

- 粒度与版本问题。依赖环会让相关组件保持未激活。拆成更细的 core 与 integration component 可以消环，也会增加配置和认知负担。仅按 key 连接依赖无法自动解决接口漂移和同名碰撞；Cordis 当前主要借助 npm peer dependency，论文把命名空间和结构兼容性留作开放问题。

这些讨论把 Cordis 的范围扩展到组件如何取得能力、贡献状态、被替换并最终退出。源码实现覆盖了 Context、Fiber、service 与 Loader；通用 service broker、跨语言 runtime、操作系统协同和完整自演化验证仍停留在论文提出的方向。

### 证据边界

Koishi 案例提供了插件规模、领域跨度和运行时行为三方面的观察证据。服务端把聊天机器人能力建在 Cordis 上，浏览器管理台则用另一套 Cordis 应用组合 UI 与浏览器能力；插件可以在不中断其他连接和缓存的情况下禁用或热替换，Provider 变化只重新激活解析结果发生变化的 Consumer。论文据此说明 core/loader 可以跨领域使用，并能承载不同作者独立贡献的真实依赖图。

作者同时限定了这份证据的强度：

- 证据来自单一 TypeScript 生态中的观察性案例，缺少与其他架构的对照实验；

- 论文讨论 Cordis v4，而案例中的 Koishi 当时仍使用 Cordis v3；

- 没有给出运行时开销、热更新延迟或开发效率的量化基准；

- inject 与 Context interception 只约束通过 Context 访问的能力；不受信任的插件仍需进程、虚拟机、WebAssembly 或容器级沙箱。

论文为 Cordis 提供了理论解释，也明确了责任边界：effect 与依赖可以纳入同一套运行时组合语义；DSH 的安全性、性能、插件质量和恢复完整性仍需独立验证。

88 页中，第 80–88 页是参考文献，主体可以按五段阅读：第 4–6 页提出组件级组合问题；第 7–27 页构造 revertible effect、reactive coeffect 与统一 Context；第 28–53 页给出 Fiber 生命周期演算和全局性质证明；第 54–66 页对应 Cordis 实现、Loader、HMR 与 Koishi 案例；第 67–79 页讨论系统边界、服务编排、安全、语言与版本问题。若只关心 DSH，优先读第 4–6、22–38、54–73 页即可。

## Agent loop 执行链

插件图装好以后，DSH 的 Agent loop 才有运行条件。默认 AgentLoop 明确依赖五项 service：

```
["agents", "sessions", "llm", "tools", "systemPrompt"]

```

这五项依赖勾勒出一次请求的主干：

- sessions 保存规范事件日志，并将事件投影成模型消息。

- systemPrompt 汇总提示词片段、变量与工具 schema。

- llm 提供模型适配与流式输出。

- tools 管理工具目录、策略和执行管线。

- agents 管理 Agent 实例与运行中的协调。

一次 step 通常承载一次成功的模型请求及其工具执行；请求错误触发内部重试时，同一 step 可能发起多次 Provider 请求。一个 turn 可以包含零到多个 step。被拒绝或被改写为空的第一次输入仍会留下 turn 边界，但不会花费一次模型 step。工具结果要求模型继续工作，或有新输入进入下一 step 时，turn 暂不结束。

主流程可以压缩为：

```
输入进入 inbox
  → turn/start
  → 领取 next-step input 与一条排队消息
  → 读取 prompt sections 与 tool schemas
  → agent/pre-step 接纳、拒绝或改写输入
     → 拒绝，或第一次输入被改写为空：turn/end，不产生 step
     → 接纳：
       step/start
       → user/message 写入会话日志
       → deriveMessages() 投影模型历史
       → agent/request → llm/stream
       → assistant/chunk* → assistant/message
       → tool/call* → tools/pre-execute
                    → tools/execute
                    → tools/post-execute
                    → tool/result*
       → step/end
       → 仍有工具后续或 next-step input：进入下一 step
  → agent/turn-stopping
  → turn/end

```

这条路径中有三类事件，不能因为都叫 event 就混为一谈：

- Session events 是持久事实，包括 turn/*、step/*、user/message、assistant/* 和 tool/*，必须能跨重启恢复；

- Agent events 携带活的 Agent 对象，用于 inbox、请求、验证、继续运行和状态协调；

- Capability events 附着在 fs/*、tools/*、telemetry/* 等能力 seam 上，用于策略与适配。

Cordis Service 负责直接提供能力，Cordis Event 负责观察和拦截运行过程。持久事件进入 Session log；agent/* 与 tools/* 中间件大多只存在于当次运行。这个分层既避免把所有 middleware 临时状态都写入日志，也避免只保存聊天文本而丢掉恢复所需的因果边界。

默认 loop 因此不必内置每一种审批、提示词注入或 Provider 行为。插件在已有事件位置接入。如果一项新能力只能通过修改 loop 实现，说明现有 service 或 event seam 尚不足以表达它。

## 追加式事件流

运行时插件图描述的是“现在有哪些能力”。会话事件流描述的是“这次工作已经发生了什么”。两者的生命周期不同。

插件可以卸载，Provider 可以替换，Context 的 service 图会变化；已经写入 Session 的 user/message、assistant/message、tool/call 与 tool/result 则是历史事实。恢复、分叉、轨迹 UI 和统计都从这份日志派生。

DSH 将 Session 设计为 append-only log。turn / step 边界、原始流式 chunk、最终模型消息和工具往返都有连续序号。它不会为了压缩上下文直接修改旧事件。

架构文档把核心约束写成 “Model-visible means logged”：凡是进入模型请求的输入，都必须能从规范日志重建；新增一种模型可见输入时，应先扩展 SessionEventMap，再由日志投影它。这里的重点是“可重建”，不是要求所有内部调度状态都变成模型消息，也不是把每次 prompt 的完整副本机械保存一遍。

模型下一次看到的内容也不是整份日志。deriveMessages() 从日志维护的 surface 中投影消息：

- assistant/chunk 保留流式回放细节，不与最终 assistant/message 重复进入模型历史；

- turn / step 边界和统计类事件不会变成模型消息；

- compaction 追加 replacement 节点，在当前 surface 中遮蔽一段旧消息，原始事件仍留在日志里。

因此，“完整记录”与“完整发送”是两件事。Token 消耗取决于当次消息投影、系统提示词和工具 schema 的大小，不由磁盘日志长度直接决定。

Append-only 设计增加的是存储、索引、格式迁移和隐私成本。日志可能保留用户输入、工具参数、命令输出、文件内容，以及模型接口实际返回的 reasoning chunk。向外部遥测系统导出时，部署方需要另外设计脱敏与保留策略。

“记录思维链”也要按接口边界理解：如果模型 Provider 在流中返回 reasoning chunk，Harness 可以将它保存；模型没有返回的内部推理过程，Harness 无法读取。完整日志提高了可审计性，同时扩大了敏感数据保留面，这两件事必须一起评估。

它也没有让外部副作用变得可回放。日志能证明模型请求过一次命令，不能保证重复执行命令仍然安全。恢复工具调用仍需要幂等性、检查点和“结果未知”处理。

## 四种 Agent Preset

标准、PTC、极简和创造四个 preset 共用 Agent loop 与宿主服务，差异由会话作用域中的配置行表达。

standard 提供完整 coding agent 工具组合。

code 保留标准工具注册和执行管线，增加 tool-presentation，将工具目录以 Code Mode 暴露给模型。模型主要看到 run_code 与生成的 TypeScript SDK，用一段程序组合多个工具调用。PTC 改变的是模型调用工具的协议层，没有建立第二套调度循环。

minimal 使用完整 persona，只贡献持久 Bash 与 str_replace_editor，并且不加载 compaction。这里的“仅保留两个工具”指模型 surface；宿主层共用的 Session、持久化、审批与沙箱服务并未随之删除，Web 组件则取决于当前是否使用 Web Profile。还要注意，Minimal 为编辑器所在的私有 realm 提供的是裸 fs-local；宿主存在沙箱服务，并不表示 str_replace_editor 的文件写入会经过 fs-sandbox。

cordis 在标准组合上增加运行时检查与临时插件管理工具。动态 package 位于共享 DSH 进程内存，重启后消失，不会自动写成插件文件或持久化配置。执行模型生成的 JavaScript 接近 Shell 权限，不能当作安全隔离环境。

这些 preset 说明 Cordis 带来的产品价值：模式差异可以表现为插件图增量，不需要复制 Agent loop。

## 与其他 Harness 比较

这些项目并不完全处在同一层。Pi 更接近 DSH 里的 Agent 内环；OpenClaw、Hermes-Agent、Codex 与 DSH 更接近完整 Harness 或产品宿主；Cordis 则再低一层，它不理解模型与工具，只负责组合、依赖和生命周期。直接比较“谁的插件更多”，很容易把层级差异误写成功能差异。参考阅读：Agent 开发指南：技术太多，该怎么学？、第一个 Agent 从 Pi 开始、Loop 不是 Agent 架构，Harness 才是、深度解析：Harness Engineering。

更合适的比较方式，是沿着三个共同问题横向展开：一项能力怎样接入系统，同一套核心怎样服务多个入口，连续请求又怎样维持稳定的模型前缀。

### 各自的重心

- Pi 把 Agent 状态、模型调用和工具循环放在中心。它的扩展面并不浅，工具、Provider、compaction 和 UI 都能替换；特色是内环短、合同直接，没有再引入一套领域无关的组合运行时。

- OpenClaw 的中心更接近 Gateway 和产品领域。工具、消息渠道、Provider、Hook 与 Agent Harness 都有明确入口，扩展能做什么通常由宿主语义直接说明。

- Hermes-Agent 围绕 AIAgent 组织产品能力，同时把“这项能力会占用多少永久模型上下文”当作设计约束。Skill、条件工具、Plugin、MCP 与 core toolset 不是同一种入口，而是一组成本不同的选择。

- Codex 更强调 Thread / Turn / Item 这组执行语义，以及围绕它建立的类型化客户端协议。扩展边界较强，也更容易从类型中判断某个客户端或插件被允许做什么。

- DSH 同时把插件运行图与 Session 事件流放在中心。它的辨识度不在某个工具或 loop，而在于让许多能力共用 Context、service、effect、Fiber 和 Loader 这套组合语义。

### 多端复用

这些项目都在复用 Agent core，只是边界画在不同位置。Pi 直接提供交互、单次输出、JSON、RPC 与 SDK 等入口；OpenClaw 用 Gateway 把内部 Agent core 接到消息渠道和原生 macOS companion；Hermes 的 CLI、TUI、消息渠道与 Electron Desktop 共用 AIAgent，桌面端通过 JSON-RPC / WebSocket 连接 headless hermes serve，并没有把 Python runtime 嵌进 Electron。

早期 OpenClaw 核心依赖 Pi，在最近的重构中，已经将它内化吸收（Hard Contract[8]）。Agent runtime 现由 OpenClaw 自己维护，Pi 相关的外部依赖主要只剩 pi-tui。

Codex 将多端复用收束到一条清晰的协议边界。根据安装包结构与实际运行观察，Electron App 会以 app-server 模式启动 codex 可执行文件；公开源码则显示，TUI 通过 AppServerSession 接入同一套接口，VS Code 也被列为 app-server 客户端。各端共享的不是终端 UI，而是 codex-core 的执行语义，以及由 Thread、Turn、Item 构成的类型化合同：

```
App        TUI      VS Code 等客户端
  \         |        /
    typed app-server
            |
    codex-core runtime

```

DSH 的 Web 与 Headless Profile 同样复用 Cordis、Session 和 Agent 的基础合同，但二者加载的默认插件集合并不相同。Profile 决定进程级的宿主组合，Preset 再决定每个会话使用哪些工具、提示词与局部服务。因此，DSH 的复用边界不只是一层客户端协议：配置本身也参与决定 runtime 由什么组成。

简单概括：Pi 把 Agent core 做成可运行、可嵌入的接口；OpenClaw 与 Hermes 将共享内核放到 Gateway 或常驻服务之后；Codex 用类型化 app-server 统一多端语义；DSH 则进一步把能力拓扑、作用域与生命周期变成可配置的运行时对象。Codex 更关注不同客户端对 Thread / Turn / Item 的一致投影，DSH 更关注运行拓扑能否按作用域重组，而 Session 再负责维持历史的一致性。

### 动态会浪费缓存吗

Cordis 允许运行时装卸插件，很容易让人以为 DSH 每轮都会生成一份全新的 Prompt，既浪费 token，也无法利用 Provider 的前缀缓存。实际并不是这样。DSH 虽然会在每个 step 重新读取当前插件图，但只要模型可见的 system prompt、工具 schema、模型路由与历史前缀没有变化，重新组装仍可以得到相同前缀。为此，它会稳定 prompt section 和工具的顺序；使用 pi-ai adapter 时，也会把缓存保留策略与 session id 传给底层 Provider。

真正导致失效的不是“运行时是动态的”，而是动态变化最终穿透到了模型请求：例如工具集合改变、提示词 section 改写、模型被切换，或 compaction 替换了历史。DSH 的 request/header 会记录这类请求面变化，但它本身不是 cache key；Session.deriveMessages() 的本地缓存也只是减少重复投影，不能减少模型实际接收的上下文。

因此，前缀缓存不会缩短模型的有效上下文，也不会把那段历史变成零 token；它复用的是相同前缀的计算，可能降低延迟和缓存输入计费，具体效果仍取决于 Provider。Pi、OpenClaw、Hermes 与 Codex 都在处理同一个问题：Pi 和 Codex 更偏向在 Provider 请求层维持缓存身份，OpenClaw 把稳定区与动态区分得很明确，Hermes 则把稳定 Toolset、Prompt 分层和会话冻结做成一套贯穿产品的缓存纪律。DSH 的取舍，是允许更深的运行时重组，同时让真正的模型 surface 变化成为明确的失效边界。

### DSH 的位置

把这些差异压缩一下：Pi 追求一条短而直接的 Agent 内环；OpenClaw 以 Gateway 和产品领域连接大量真实入口；Hermes-Agent 把模型 surface 的长期成本纳入扩展决策；Codex 用类型化协议维持多个客户端的一致执行语义；DSH 则把 Harness 的组合关系本身做成运行时系统。

这也解释了 DSH 的优势与代价。它更容易在配置层替换 Provider、隔离同名能力、按会话装入插件，或由同一套组件组合出不同模式；但实际行为不再只由 import 图和调用栈决定，还要还原 Context、realm、Fiber 与最终配置树。Pi 和领域型 registrar 更容易局部理解，Codex 的协议边界更容易静态审计，DSH 则更擅长处理跨作用域、跨生命周期的组合变化。

所以这些架构没有统一的胜负关系。若复杂度主要在 Agent loop，Pi 的直接性更有吸引力；若复杂度主要在渠道和产品集成，OpenClaw 的领域边界更自然；若主要问题是多客户端一致性，Codex 的 typed app-server 更合适；当 Provider、工具、会话、沙箱、UI 与 loop 都需要按部署或会话重新组合时，Cordis 的价值才最明显。

## “一切皆插件”的边界

源码能支持的结论，可以分成四项。

Cordis 根 Context 在构造时会直接创建根 Fiber、Reflect、Registry、Events 与 Logger service。Session 的具体运行对象、Boot 和部分 UI 启动代码也不可能由尚未建立的插件图先行解释。

所以“一切皆插件”适合描述 DSH 的应用能力组织方式，不适合当作递归到底的字面事实。它仍然有核心，只是核心从 Agent 业务逻辑下沉成了组合内核。

## 收益与成本

Cordis 的收益不是笼统的“更灵活”，而是把几种原本分散的组合问题放进同一套语义。

组合方式统一。增加工具、替换 Provider、注册 UI、构建 benchmark preset，不必各自发明一套注册、作用域和卸载协议。插件作者反复使用 Context、service、event、effect 与 Fiber，发行者再用同一种配置树装配它们。

生命周期成为一等问题。配置、依赖和 disposer 都归一次 Fiber 挂载所有。开发期热更新、测试隔离、临时插件和会话级能力因此有共同的退休路径，而不是依赖每个子系统约定自己的 shutdown()。

依赖可以晚绑定并按位置替换。Consumer 依赖稳定 service 名称；Provider 晚到、消失、换成远程实现或只在某个 realm 中替换时，Cordis 有对应的等待、通知和重载语义。一个 Agent 使用本地能力、另一个使用沙箱能力，不需要复制 Consumer。

模式是组合，不是主程序分叉。Web / Headless 与四种 Agent Preset 位于不同轴，PTC 主要替换工具 presentation，Minimal 主要收窄模型 surface。它们复用同一套会话、策略和 loop，降低了多种体验各自漂移的风险。

运行时本身可检查和试验。配置 Entry、Fiber、service 与 effect 都是可观察实体。创造模式能查看当前组合并挂载内存插件，说明这里的“插件生态”不只是构建期扩展点，也可以成为 Harness 实验台。想看看社区已经出现了哪些相关项目，可以从 GitHub 的 dsh-plugin Topic[9] 搜索 DSH 插件；这是社区标签聚合页，不代表官方审核或兼容性认证。

相应成本同样来自这套统一机制。

静态代码不再等于实际系统。import 图只能说明可能性；Profile、Bundle、Patch、Preset、条件表达式和 realm 共同决定真实拓扑。问题报告若缺少最终配置树，常常连复现对象都没有描述完整。

动态依赖会放大因果链。Provider 的一次变化可能使一组 Consumer Fiber 依次进入卸载和重载；异步 setup、event 通知与 disposer 又可能交错。排障除了调用栈，还要看 Provider 身份、Fiber epoch、当前 committed binding 和清理是否收敛。

可逆不等于事务 。effect 只能回收插件声明过的资源，无法自动补偿网络消息、共享文件写入或支付。顶层 effect 还会并发清理，存在严格资源顺序时必须由插件显式建立边界。

插件化不等于安全。inject 约束通过 Context 使用能力，不能阻止同进程代码直接导入 Node API；effect 解决所有权，worker thread 解决部分执行隔离，都不是不受信任代码的权限边界。创造模式尤其应按高权限运行时实验室理解。

元框架与本地分叉成为新的核心。Cordis 根 Context、Reflect、Registry、Events、Fiber、Boot 与 Loader 必须先存在，应用插件图才能建立。DSH 又选择 source-vendor Cordis 并维护生命周期与 Loader 加固；这便于固定和审计运行时，也把上游同步、语义差异和兼容成本带进 Harness 自身。

性能代价仍缺少量化。当前论文和仓库没有给出 Cordis 运行时开销、配置重组延迟或大规模插件图的对照基准，因此既不能证明统一运行图几乎没有成本，也不能据此断言它会成为瓶颈。

## 结语

我起初也好奇，DSH 是否主要由 DeepSeek 自家的模型辅助开发。没想到社区已有人根据仓库痕迹做出统计：估计约 20% 的提交和 PR 与 Codex worktree 有关。

DSH 借助 DeepSeek、Claude Code 还是 Codex 开发，并不是本文评价它的重点。DSH v0.1 有价值的是它抓住了 Harness 产品化后的另一种复杂度：组合关系可能比 loop 本身更难管理。Cordis 将能力的可见范围、激活条件、依赖重载和资源清理做成显式运行图；Session 则把执行历史保存为可恢复、分叉和投影的事件流。运行图与事件流一横一纵，构成了 DSH 最有辨识度的架构。

这套设计是否划算，取决于项目面对的组合压力。对于单一宿主、固定 loop 和少量稳定工具，显式插件图可能带来多于收益的概念；当多宿主、多 Provider、会话级隔离、运行时装卸和第三方生态同时出现时，组合关系本身就成了主要问题，Cordis 的投入才开始显出价值。

配置重组后依赖图能否收敛，Fiber 退出能否可靠回收资源，Session 投影能否在压缩、恢复和分叉后保持一致都是需要关注的点。

📌 小技巧

阅读源码可以沿着这三件事展开：先看 Loader 输出的配置树，再追踪 service 的 provide / inject、Context realm 与 Fiber effect，最后沿 Session event 到 deriveMessages() 检查模型实际看到什么。这些运行时性质，比“一切皆插件”这句口号更能决定 DSH 能否成为可靠的 Harness 基础设施。

### References

[1]

DSH:https://github.com/deepseek-ai/deepseek-harness

[2]

Cordis:https://github.com/cordiverse/cordis

[3]

OpenClaw:https://github.com/openclaw/openclaw

[4]

Pi:https://github.com/earendil-works/pi

[5]

Hermes-Agent:https://github.com/nousresearch/hermes-agent

[6]

Codex:https://github.com/openai/codex

[7]

paper:https://github.com/cordiverse/paper

[8]

Hard Contract:https://github.com/openclaw/openclaw/blob/79f41079d2e7314db9f98dc61a82327c32475492/docs/refactor/database-first.md?plain=1#L85

[9]

dsh-plugin Topic:https://github.com/topics/dsh-plugin

  

  

预览时标签不可点

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

  

微信扫一扫
关注该公众号

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 知道了 

 

 

   

 

 

  微信扫一扫
使用小程序 

 

 

 

 

 

 

  

 

 

 取消 允许 

 

 

 

 

 

 

  

 

 

 取消 允许 

 

 

 

 

 

 

  

 

 

 取消 允许 

 

 

 

 × 分析 

 

 

 

 

  

  

  

 

 

 

 

微信扫一扫可打开此内容，
使用完整服务

 

 

 

 

 

 

 

 

  ： ， ， ， ， ， ， ， ， ， ， ， ， 。   视频 小程序 赞 ，轻点两下取消赞 在看 ，轻点两下取消在看 分享 留言 收藏 听过
