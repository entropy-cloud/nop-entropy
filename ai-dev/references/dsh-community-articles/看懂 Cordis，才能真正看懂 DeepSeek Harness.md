# 看懂 Cordis，才能真正看懂 DeepSeek Harness

> 来源: https://mp.weixin.qq.com/s/M-qiI071iceYMocKJytHrA
> 作者: 八小时之上
> 抓取: 2026-08-19（curl 原始 HTML + stdlib 转换；图片未保留；标题/作者经页面 og 元数据核验与链接一致）

如果你打开 DeepSeek Harness 的代码，直接从 Agent Loop 开始读，很容易产生一种挫败感：为什么一个 Agent 项目，要拆成这么多包、服务、事件、配置和作用域？

因为 DeepSeek Harness（dsh）并不是先写出一个大循环，再把功能塞进去。它先选择了一个更底层的运行时：Cordis。

dsh 官方的描述非常直接：模型适配器、工具注册表、Session 日志、Agent Loop，甚至 UI，都是 Cordis 插件。换句话说，Cordis 不是一个无关紧要的依赖，而是 dsh 的“插件内核”。

这一篇不从抽象定义开始。我们先看一个常见问题。

## 一、为什么普通插件系统不够用

假设你正在做一个企业 Agent，需要四项能力：

- 

1. LLM：调用大模型；

- 

2. Tools：注册和执行工具；

- 

3. Approval：危险操作前向用户申请批准；

- 

4. Session：记录消息和工具结果。

第一版通常很简单：在入口文件里按顺序初始化四个对象，再把它们传给 Agent Loop。

很快，需求会继续增加：

- 

• LLM Provider 可以在运行中切换；

- 

• 不同 Agent 使用不同工具集；

- 

• 某个工具只在文件系统服务可用时启动；

- 

• 修改配置后，希望局部热重载；

- 

• 插件卸载后，监听器、定时器和工具注册必须全部清掉；

- 

• 两个 Agent 都有 shell，但要分别指向本地和沙箱环境。

这时，问题已经不只是“模块怎么拆”，而是两个维度的组合问题：

- 

• 空间组合：一个组件依赖谁？在哪个作用域里能看到谁？

- 

• 时间组合：组件什么时候出现、消失、重载？它留下的副作用能不能完整撤销？

Cordis 官方把自己称为“时空可组合性的元框架”，说的就是这两件事。

## 二、用一句人话解释 Cordis

可以把 Cordis 想象成一座可以边营业边改造的商场。

- 

• 插件是店铺；

- 

• Context 是商场里的服务网络；

- 

• Service 是电力、收银、仓储等稳定能力；

- 

• Injection 是店铺开业前声明的依赖；

- 

• Event 是店铺之间的广播和协作机制；

- 

• Effect / Fiber 记录这家店装了哪些招牌、线路和设备，关店时负责完整拆除。

关键不是“能开很多店”，而是：新增一家店不必改商场总入口；供电恢复时，等待中的店能自动开业；店铺撤走时，不留下幽灵监听器、重复注册和失控定时器。

## 三、Cordis 的五个核心概念

### 1. Plugin：所有能力的部署单元

最简单的 Cordis 插件就是一个接收 ctx 的函数：

```
import type { Context } from '@deepseek-ai/cordis'

export const name = 'hello'

export function apply(ctx: Context) {
  console.log('hello from plugin')
}
```

它不负责创建整个应用，也不负责手动寻找其他插件。它只描述自己向当前 Context 贡献什么。

在 dsh 中，LLM、Tools、Session、Agent、UI 都服从相同模式。这就是“一切皆插件”的工程含义。

### 2. Context：不是全局变量，而是有作用域的服务视图

插件通过稳定的 key 使用服务，例如：

```
ctx.tools
ctx.llm
ctx.sessions
```

消费方依赖的是服务契约，而不是 Provider 的具体类。更重要的是，Context 有拓扑和作用域：同名服务在不同 isolate realm 中可以指向不同实例。

例如，父级 Agent 的 ctx.shell 指向本地环境，子 Agent 的 ctx.shell 指向受限沙箱。工具 Consumer 仍然调用同一个 key，但解析到的 Provider 不同。

### 3. Injection：用依赖决定激活，而不是靠配置文件顺序

一个工具插件需要 tools 服务，可以声明：

```
export const inject = ['tools']
```

如果服务还没出现，插件保持等待；服务就绪后再激活。配置列表中的上下顺序不负责启动顺序，依赖关系才负责。

这听起来像依赖注入，但 Cordis 更强调“响应式生命周期”：Provider 消失时，依赖它的插件也会退出；Provider 恢复后，插件可以重新激活。

代价是，PENDING 是一个合法状态。插件没有运行，不一定是报错，也可能是某项依赖一直没人提供。工程上必须能观察 fiber 状态，否则会遇到“配置写了但毫无反应”的静默问题。

### 4. Typed Events：事件不仅广播，还定义协作语义

Cordis 支持四种主要分发方式：

模式

适合做什么

emit

同步通知观察者，不等待返回

parallel

并行等待多个观察者完成

serial

按顺序执行并等待

waterfall

让监听器包装、替换或短路下游结果

其中最有辨识度的是 waterfall。它像洋葱中间件：监听器可以调用 next() 继续向下，也可以在自己拥有决策权时直接返回，从而短路后续逻辑。

dsh 用它处理模型请求包装、工具执行策略和审批请求等协作场景。

但要注意：如果一个只想记录日志的 waterfall 监听器忘了调用 next()，它会意外吞掉下游行为。这不是语法细节，而是插件作者必须遵守的纪律。

### 5. Effect 与 Fiber：让副作用可以撤销

插件最难管理的通常不是纯函数，而是副作用：监听器、定时器、文件 watcher、工具注册、连接和子插件。

Cordis 会把注册归属到插件 fiber。框架管理之外的资源，可以放进 ctx.effect() 并返回 disposer：

```
export function apply(ctx: Context) {
  ctx.effect(() => {
    const timer = setInterval(() => console.log('tick'), 1000)
    return () => clearInterval(timer)
  })
}
```

插件卸载时，disposer 负责清理定时器。HMR 的基础也是同一件事：旧插件先卸载并回卷 effect，新插件再加载。

“能动态添加插件”并不稀奇；“删除插件后，能把它曾经造成的影响完整撤销”，才是时间可组合性真正难的地方。

## 四、一个可运行案例：给 dsh 增加 greet 工具

下面用 dsh 官方 Cordis 教程的模式，做一个不需要 API Key 的最小实验。

### 第一步：准备教程目录

```
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
pnpm install
mkdir -p tmp/cordis-tutorial
cd tmp/cordis-tutorial
```

### 第二步：写工具插件

创建 greet-tool.ts：

```
import type { Context } from '@deepseek-ai/cordis'
import { defineTool } from '@deepseek-ai/dsh-tools'
import { CallId } from '@deepseek-ai/dsh-llm'

export const inject = ['tools']

export function apply(ctx: Context) {
  ctx.tools.register(defineTool({
    name: 'greet',
    description: 'Greet one person.',
    parameters: {
      name: { type: 'string', required: true },
    },
    output: {
      schema: { type: 'string' },
      render: (_args, value) => [{ type: 'text', text: value }],
    },
    async execute({ name }) {
      return `Hello, ${name}!`
    },
  }))

  void ctx.tools.execute({
    callId: CallId('demo-1'),
    name: 'greet',
    arguments: { name: 'Cordis' },
    signal: new AbortController().signal,
  }).then(result => console.log(result.content))
}
```

这个示例里已经出现了 Cordis 的三项核心机制：

- 

• inject = ['tools']：没有工具服务就不激活；

- 

• ctx.tools.register(...)：把工具注册归属到当前插件；

- 

• 插件卸载时，工具注册会随 fiber 一起撤销。

### 第三步：用配置组装应用

创建 cordis.yml：

```
- name: '@deepseek-ai/dsh-system-prompt'
- name: '@deepseek-ai/dsh-tools'
- name: './greet-tool.ts'
```

然后运行：

```
node --import tsx ../../vendor/cordis/bin.js
```

预期能看到 Hello, Cordis! 对应的工具结果。

这个实验没有调用大模型。它只是用真实 dsh Tool Runtime 执行了一次工具调用。把 LLM Adapter、Agent Loop、Session Persistence 和入口插件继续装上去，才会成为完整 Agent。

这恰好说明 dsh 的结构：Agent 不是一个不可拆分的对象，而是一组通过服务和事件连接的插件。

## 五、再往前一步：把能力做成 Definition、Provider、Consumer

真正可维护的插件，不应该把“能力是什么”“后端怎么实现”“如何暴露给模型”揉在一起。

假设我们要让 Agent 查询公司内部知识库，可以拆成：

### Service Definition

声明稳定服务 ctx.knowledge，定义 search(query) 的输入输出。这一层不关心数据来自 SQLite、向量库还是远程 API。

### Provider

分别实现：

- 

• knowledge-sqlite：读取本地数据库；

- 

• knowledge-http：调用企业搜索服务；

- 

• knowledge-mock：测试时返回固定结果。

### Consumer

注册模型可见的 knowledge_search 工具，把参数转给 ctx.knowledge.search()，再将规范结果渲染为模型内容和 UI 展示。

这样做有三个好处：

- 

1. 替换 Provider 时，工具 schema 不变；

- 

2. 测试可以换上 mock Provider，而不伪造整个工具运行时；

- 

3. Agent scope 可以选择不同 Provider，同时复用同一个 Consumer。

这就是 dsh 所说的 capability seam。它比“接口抽象”多了一层生命周期和部署语义。

## 六、Cordis 为什么特别适合 Agent Runtime

Agent 系统有几个天然特征：

- 

• 组件很多，而且供应商经常变化；

- 

• 会话持续时间长，中途可能切换模型、工具和策略；

- 

• 子 Agent 需要继承部分能力，又隔离部分能力；

- 

• 工具调用有权限、取消、并发和持久化要求；

- 

• 开发阶段需要频繁热更新，但不能留下旧注册；

- 

• 出错后要知道当时到底装配了什么。

这些问题都同时涉及空间与时间。

传统依赖注入容器主要回答“如何找到一个实现”；事件总线主要回答“如何通知别人”；插件管理器主要回答“如何加载扩展”。Cordis 尝试用同一套 Context、Fiber、Service、Event 和 Effect 模型把三者连接起来。

这也是 dsh 几乎完全依赖 Cordis 的原因：没有 Cordis，dsh 仍然可以实现 Agent 功能，但它现在这套“一切可配置替换、按依赖激活、按作用域隔离、卸载可回卷”的架构就失去了统一基础。

## 七、它的代价同样明显

Cordis 并不是“用了就自动优雅”。

### 1. 复杂度从代码分支迁移到了拓扑

主循环更薄了，但开发者必须理解：当前插件在哪个 fiber？能看到哪个 Context？服务由哪个祖先或 isolate realm 提供？为什么它处于 PENDING？

### 2. 生命周期错误更隐蔽

从根 Context 测试成功，不代表从真实插件 fiber 调用也成功。作用域、shadow proxy、祖先查找和卸载顺序都可能影响结果。

### 3. Waterfall 很强，也容易误用

一个忘记 next() 的观察型监听器，就可能让下游逻辑静默消失。协作协议必须写进文档和测试。

### 4. API 仍在变化

截至 2026 年 8 月 14 日，Cordis 官方仍说明 API 尚未稳定，设计论文也处于持续修订的预印本阶段。用于生产前，需要固定版本并建立组合测试和升级策略。

## 八、建议的学习顺序

如果你准备研究 dsh，不建议一上来横扫所有 package。更有效的顺序是：

- 

1. Plugin：跑通第一个函数插件；

- 

2. Effect：创建定时器，再卸载插件，确认资源被清理；

- 

3. Service + Injection：写一个 Greeter Provider 和 Consumer；

- 

4. Events：分别尝试 emit 与 waterfall，观察短路；

- 

5. Composition + HMR：修改插件并检查旧 effect 是否回卷；

- 

6. Harness Tool：注册一个只读工具，观察 tools/result；

- 

7. Session + Agent Loop：最后再进入完整 Agent 执行链。

验收标准不是“文档看完了”，而是你能回答：

- 

• 为什么 YAML 顺序不保证插件启动顺序？

- 

• Provider 消失后，Consumer 会发生什么？

- 

• 工具注册属于哪个 fiber，卸载后是否还存在？

- 

• 两个 Agent 如何使用同名但不同实例的 shell？

- 

• Waterfall 中谁有权短路，谁必须调用 next()？

## 结语

Cordis 的价值，不在于提供了又一种插件 API，而在于它把插件系统里最难的两件事放到同一个模型里：

组件在空间上如何依赖与隔离，组件在时间上如何出现、消失并撤销影响。

当软件只是一次性脚本时，这套模型可能显得过重；当系统变成长期运行、动态装配、多个 Agent 并存的 Runtime，它开始显示价值。

所以，看 DeepSeek Harness 时不要只问“Agent Loop 怎么写”。更值得问的是：

当 Loop、工具、模型、会话和 UI 都可以被替换时，谁负责让它们在正确的时间、正确的作用域里相遇，并在离开时不留下垃圾？

dsh 的回答，就是 Cordis。

## 主要资料

- 

• Cordis 官方仓库[1]

- 

• 《A Programming Paradigm for Spatiotemporal Composability》论文仓库[2]

- 

• DeepSeek Harness 官方仓库[3]

- 

• DeepSeek Harness 仓库内《Cordis 入门》与七章教程，源码快照 commit 47f943859bef60e4160492346772ded9b24f765a

说明：本文中的运行方式与 API 以 2026-08-14 的 dsh 源码快照为准。Cordis 与 dsh 均处于快速迭代期，实践前请以最新官方文档为准。

#### 引用链接

[1] Cordis 官方仓库: https://github.com/cordiverse/cordis
[2] 《A Programming Paradigm for Spatiotemporal Composability》论文仓库: https://github.com/cordiverse/paper
[3] DeepSeek Harness 官方仓库: https://github.com/deepseek-ai/deepseek-harness

  

  

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
