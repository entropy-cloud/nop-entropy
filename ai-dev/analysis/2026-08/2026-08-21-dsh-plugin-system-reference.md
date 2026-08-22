# DSH 插件系统机制参考（nop-plugin / nop-ai-agent 改造基线）

> **状态**：active（调研基线，结论以源码为准；改造落地时若与源码冲突，以源码为最终事实）
> **日期**：2026-08-21
> **类型**：analysis（外部系统机制调研，供 `nop-plugin` 与 `nop-ai-agent` 后续改造参照）
> **证据基线**：npm 安装的 `@deepseek-ai/*` 包源码与编译产物（含 cordis 4.0.1 的 TypeScript `src/`）+ Cordis 论文 PDF（[cordiverse/paper](https://github.com/cordiverse/paper)）+ 本地 pi 源码（`~/ai/pi`）。所有关键论断均在本会话中逐条核对过，证据索引见 §10。
> **关联**：`nop-plugin` 设计系列见 [`ai-dev/design/nop-plugin/`](../../design/nop-plugin/README.md)；`nop-ai-agent` 设计见 [`ai-dev/design/nop-ai-agent/`](../../design/nop-ai-agent/README.md)；理论解读见 `ai-dev/articles/dsh-architecture-from-reversible-computation.md`。

---

## 0. 给 AI agent 的阅读指引

本文是 **DSH（DeepSeek Harness）插件系统的机制级参考**：三层模型、每个机制的原语、生命周期、可逆性语义，以及"哪些已核验、哪些待核验"。后续改造 `nop-plugin` / `nop-ai-agent` 时：

1. **先读 §1 总览**（三层模型）与 §8（可逆性语义），再按需进入对应机制章节；
2. **改造时优先保持的不变量**（§8.4）：逆的良构性（每个注册动作当场携带撤销）、单写者（一个坐标一个提供者）、无删除（只能撤自己的贡献）、坐标稳定性（名字/标签不可变）、激活窗口的时间静止（epoch/inertia）；
3. 文中的源码引用是 **npm 安装路径**（`node_modules/@deepseek-ai/...`）；仓库源码路径对照见 §10.1；
4. 与既有设计文档的关系：`design/nop-plugin/02-dsh-usage-coverage.md` 是"dsh 用法 → nop-plugin 覆盖度"的评估；本文是**机制本身**的完整参考，两者互补。

---

## 1. 总体架构：三层模型

DSH 插件系统（cordis 内核 + dsh 框架层）由三个正交层次构成：

```
┌─ 装配层（声明式，YAML 行树）───────────────────────────────────┐
│  profile bundle / cordis.patch.yml / agent.cordis.yml / --patch │
│  Entry 树：id / name / config / group / disabled / inject /     │
│           isolate / intercept；patch = insert + 按 id 整行覆盖   │
└───────────────┬────────────────────────────────────────────────┘
                │ loader：行 → 插件模块 → ctx.plugin()
┌─ 激活层（响应式，fiber 生命周期）───────────────────────────────┐
│  Fiber：PENDING → LOADING → ACTIVE → (FAILED|UNLOADING) →       │
│         DISPOSED；依赖满足自动激活（notify → _checkImpl →        │
│         _refresh）；inject 声明依赖；store 快照；inertia 转换链    │
└───────────────┬────────────────────────────────────────────────┘
                │ apply(ctx) / 类插件构造执行注册
┌─ 注册层（可逆，ctx.effect 坐标写入）────────────────────────────┐
│  一切注册 = 坐标写入 + 当场返回撤销函数（“Δ 出生即带逆”）          │
│  服务槽（('name', realm) 单写者）│ 事件表（名字→监听器数组）       │
│  条目层（(scope, name)，ScopedLayers）│ 句柄资源（timer 等）       │
└─────────────────────────────────────────────────────────────────┘
```

**核心命题**：装配是结构（可 patch、可热更、可静态检查）；激活是响应（依赖驱动）；注册是可逆（每个动作带逆）。三者通过 `Entry → ctx.plugin → fiber → ctx.effect` 一条链接通。

---

## 2. 原语层（cordis 内核）

### 2.1 Context 与 ctx 链

- `Context` 是插件拿到的界面，实际是一个 **Proxy**（`context.ts: new Proxy<this>(this, ReflectService.handler)`），属性读写被翻译成坐标查询。
- `ctx.extend(meta)` 派生子 ctx（原型链继承：`Object.create` 链）；`ctx.plugin(plugin, config)` 挂插件 → 产生 fiber 与专用派生 ctx（`fiber.ts:236: this.ctx = parent.extend({ fiber: this })`）。
- 三个"视图分歧"操作：
  - `ctx.isolate(name, label?)`：只为一个名字换**隔离符号**（新 realm 槽位），"Passing the same label to two isolate() calls joins their scopes"（context.ts:121）；
  - `ctx.intercept(name, config)`：为服务名加**配置视图覆盖**（沿链合并、后代覆盖祖先、属性级合并）；
  - `scopeOf(ctx)`（dsh-scope）：读出最近的 scope 标签（见 §4）。
- 服务方法内的 `this.ctx` 会被 **tracker/getTraceable** 机制重绑为**调用者 ctx**（`Service` 构造时打 `{associate: name, property: 'ctx'}` 标记；`utils.ts:117/160` 的 apply trap）——这是"同一个服务对象、不同调用者写进不同层/不同槽"的机制基础。

### 2.2 Fiber：生命周期单元

每个 `ctx.plugin()` 产生一个 Fiber：

```ts
// fiber.ts
export const enum FiberState { PENDING, LOADING, ACTIVE, FAILED, DISPOSED, UNLOADING }
// PENDING  — 等依赖；LOADING — apply 执行中；ACTIVE — 加载并提供服务
// FAILED   — apply/config 抛错；UNLOADING — 逆在执行；DISPOSED — 移除，不能重启
```

Fiber 字段/机制（fiber.ts）：

| 字段 | 作用 |
|---|---|
| `uid` | 注册表内唯一 id（root=0）；dispose 后置 null |
| `ctx` | 专用派生 ctx（`parent.extend({fiber: this})`） |
| `inject` | 解析后的依赖表（服务名 → intercept 配置） |
| `store` | 已解析服务实现快照（`{..._store}`），供查找 |
| `_disposables` | 撤销函数账本（每条 effect 一个 wrapper） |
| `inertia` | 正在进行的 load/unload 转换链承诺 |
| `state` | 生命周期状态，转变时发 `internal/status` |
| `dispose` | 卸载入口（经 `parent.fiber.effect` 注册，卸载时反序执行） |

机制要点：

- **激活 = 依赖就绪**：`_checkImpl(name)` 检查服务存在 + 可用性谓词 `Impl.check`；`_refresh()` 在依赖变化时重解析 → 激活/卸载/重载（§2.3 notify）。
- **effect**（`fiber.effect(execute, label)`，fiber.ts:418）：注册一个可逆单元——execute 立即执行，返回的 disposer 撤除；**单个 effect 内部逆按 LIFO**（`disposables.splice(0).reverse()` 串行链式）；fiber 顶层多个 effect 卸载时**并发**清理（`_unload`: `Promise.all(_disposables.clear().map(...))`）。缺省 label `'anonymous'`；`getEffects()` 可枚举。
- **异步展开的 epoch 检查**：异步 effect 每一步检查 `runner.epoch`，依赖一变立即中止展开（`if (runner.epoch !== oldEpoch) return`）——"应用窗口内时间静止"的工程化。
- **restart/update**：配置变化 → 同一 fiber `_refresh` → 卸载（执行全部逆）→ 重载（apply 重跑）；`fiber.update(config)` 校验后重启；`restart()` 是 `_setEpoch(INACTIVE) + _refresh + await()`。
- 状态转换由 `_updateState` 驱动，任何转换都走 `inertia` 链（await 可等到 settle，`async await()` 返回稳定态或抛出启动错误）。

### 2.3 服务：provide / set / 解析 / notify（realm 与单写者）

共享表（root 的 `ReflectService.store: Dict<Impl, symbol>`）是所有服务的**唯一权威存储**：

```ts
// reflect.ts
interface Impl { name: string; fiber: Fiber; value?: any; check?: () => boolean }
provide(name, value, check?) {           // 注册服务（类插件构造时自动调）
  const key = this.ctx[symbols.isolate][name]      // 坐标 = 名字 + 隔离符号（realm）
  if (this.store[key]) throw new Error(`service "${name}" has been registered at <...>`)
  this.store[key] = impl
  return disposer:  // 逆 = delete this.store[key] + notify 依赖者 + await 它们重协调
}
set(name, value) {                        // 只有提供者 fiber 可改值
  if (impl.fiber !== this.ctx.fiber) throw new Error(`cannot set property "${name}" in multiple fibers`)
}
get(name) → 解析：k → ρ(k) → σ(ρ(k))；沿 fiber 父链查找，realm 边界即停
notify(names) → 遍历所有 runtime 的 fibers：inject 该名字且同域 → _checkImpl → _refresh
```

语义要点：

- **单写者（single-source discipline）**：一个坐标（名字 × realm）只能一个提供者；改值也只有提供者能做；逆只能撤自己的条目（无删除）。
- **realm ≠ scope**：realm 是**逐名字**的隔离映射（isolate map，沿 ctx 链继承，`isolate(name)` 只分歧一个名字）；scope 是**逐上下文**的身份标签（§4）。服务默认进 root realm（行为全局）；`ctx.isolate` 或配置行 `isolate:` 显式分槽。
- **响应式**：服务出现/消失/替换 → notify → 依赖者重解析（重路径：依赖收敛）。
- 论文对照：Definition 22/23（σ 表与 get/set）、Definition 28（isolation realm）、Definition 58(2)（p 两两不相交）、O-Insert（单源前提）。

### 2.4 事件与派发模式

- 存储：events 服务实例的 `_hooks: Record<name, Hook[]>`（共享，非共享表）；`ctx.on` → `register()` → `this.ctx.fiber.effect(...)`（events.ts:256）——监听即 effect，逆 = 按 callback 引用移除自己的条目。
- **五种派发模式**（`DispatchMode`，事件声明必须自洽）：`emit`（同步广播、忽略返回值）、`parallel`（并发等待）、`serial`（顺序直到 bail）、`bail`（顺序同步直到 bail 值）、`waterfall`（洋葱中间件链，`next()` 委派下游）。
- 坐标代数：emit/parallel = 表语义（可交换）；serial/bail = 顺序短路；waterfall = 有序链（不可交换，论文"middleware inserted before another sees a different request"）。
- 内部事件（框架使用）：`internal/update`（配置热更）、`internal/status`（fiber 状态转变）、`internal/plugin`（插件卸载）、`internal/get`（服务缺省解析）、`internal/service`、`internal/dispatch`。

### 2.5 effect：可逆单元的统一

**所有**注册（服务 provide、事件 on、工具 insert、资源句柄）都收敛到 `ctx.fiber.effect`——执行即注册、当场返回逆、运行时记账（保存、执行、不执行两次；逆的正确性由作者负责）。这就是"一切注册可逆"与"四类服务注册原语 + 五类事件派发"的底层统一。论文效应层（Definition 17/19、Corollary 21、Theorem 61）按 effect 证明，天然覆盖所有领域。

---

## 3. 装配层：Entry 树与 YAML

### 3.1 EntryOptions 全字段（`cordis-plugin-loader/lib/types/config/entry.d.ts`）

```ts
interface EntryOptions {
  id: string;              // 稳定 id：patch 定位键、嵌套 id 用分隔符
  name: string;            // 模块说明符（插件包名/路径）
  config?: any;            // 传给插件的配置（整行替换，不深合并）
  group?: boolean | null;  // 嵌套组（config 变成子行列表）
  disabled?: boolean | null; // 禁用本行及后代（支持 !!js 表达式）
  inject?: Inject | null;  // 本行需要的服务或服务拦截配置
  //（loader 扩展，isolate.d.ts）
  intercept?: Dict | null; // 服务 intercept 配置
  isolate?: Dict<true | string> | null; // realm 隔离：true=entry-local；字符串=命名 GlobalRealm 共享
}
```

- `!!js` 表达式：YAML 方言（`entryListSchema`，`cordis-plugin-include`）支持 `!!js expr`——在行激活时求值（如 `!!js process.env.X ?? 'y'`、`!!js dshHomePath('sessions')`、`!!js process.platform === 'win32'`）。
- Entry 生命周期：`init()`（导入模块、建 fiber、启动）→ `refresh()`（reload）→ `update(options, create?)`（合并新选项、按 diff 重启 fiber：`if (fiber?.uid && (diff.includes("config") || options.group)) fiber.update(...)`）。
- `inactiveRows()` 诊断：行装载后若依赖不满足，报告"waiting for <missing services>"——行可以长期挂起等依赖。

### 3.2 patch 语义（与 x:extends 的对照）

```yaml
# patch 文件 = 顶层 YAML 数组，两种操作：
- id: tools                 # ① 按 id 整行覆盖（config/disabled/...）
  config: { mode: code }
- insert:                   # ② 插入一组行（可带 id 指向 group）
    - id: my-tool
      name: './extensions/my-tool'
```

- **整行替换、不做深合并**——比 `x:extends` 的节点级合并弱；"值因 mode 而异的行不放在公共 base 层，由各 mode bundle 完整重述"（dsh-base patch 注释）。
- patch 找不到目标 id → warn 并跳过；插入的行可被同一列表内后面的 patch 再定位。
- 输入永不原地修改（deep-clone 后应用）——保证热重载可回退。

### 3.3 文件体系与组装顺序

```
$DSH_HOME/profiles/<name>/
├── package.json          # dsh.profile.bundles：有序 bundle 列表（插件依赖）
├── cordis.patch.yml      # 用户自己的 patch 层（watchUserPatches 热重载）
└── node_modules/         # pnpm 管理（out-of-tree 插件）+ $DSH_HOME/profiles/node_modules 扁平回退

bundle 包内：cordis.patch.yml（manifest 声明 "dsh": {"bundle": {"patch": "./cordis.patch.yml"}}）
preset 目录：$DSH_HOME/.agent-presets/<id>/agent.cordis.yml（每 preset 一层；§6）

组装序：空树 ← bundle1.patch ← bundle2.patch ← … ← profile patch ← --patch ← 命令行 flag 补丁
```

层 ↔ 文件映射：**global 平面** = profile 整条 patch 链（装载后全在 host ctx → 无 scope 标签 → global 层）；**S 层** = 每 preset 一个文件；**agent 私有层** = 无配置文件（只能代码装载）。shipped 默认实例：`dsh-base/cordis.patch.yml`（451 行，80+ 行）、`dsh-web-app/cordis.patch.yml`（445 行，其中 297-340 行把 base 的工具行大规模 `disabled`，把工具集决策交给 preset）。

### 3.4 热重载

- **patch 文件监听**（app-boot `watchUserPatches`）：`dsh.profile.bundles` + profile 的 `cordis.patch.yml` 变化 → 事务性重组 patch 列表 → loader `update`。
- **HMR**（`cordis-plugin-hmr`，chokidar）：`registerConfig(filename, refresh)` 注册监听，变化串行刷新（debounce 任务集）；`include.refresh()`；发 `hmr/change` / `hmr/reload`。行模块变化 → Entry refresh → fiber.restart（同一 fiber 卸载重载，不是新建）。
- 记住：**新建 fiber 的唯一入口是 `ctx.plugin()`**；hot-reload 是重激活不是新建。

---

## 4. scope 与分层

### 4.1 createScope / kScope / scopeParents

```ts
// dsh-scope
function createScope(ctx, key, options?) {
  if (options?.parent) bindScopeParent(key, options.parent)
  const fiber = ctx.plugin(scope)            // no-op 插件：专用 fiber（生命周期）
  const scoped = fiber.ctx.extend({ [kScope]: key })  // 身份标签（可见性/路由）
  return { ctx: scoped, rawDispose: fiber.dispose, dispose: () => quiesceFiber(fiber) }
}
scopeOf(ctx) = ctx[kScope]                    // 沿 ctx 原型链继承
bindScopeParent(key, parent)                  // WeakMap；环检查；返回唯一 re-link 权柄
scopeChainOf(key) = [key, parent, …]          // 近→远
```

scope **不是**服务的命名空间：它只管注册路由（ScopedLayers 层归属）、事件路由（scopeTarget）、组合继承（preset 链）。调用点实列：AgentLoop 构造 `createScope(loopCtx, this)`（scope key = agent loop 实例对象）；preset standing `createScope(selfCtx, {agentPreset: id})`；子 agent 走 `composeFrom` 绑定父的 standing。

### 4.2 ScopedLayers：条目分层

```ts
class ScopedLayers {
  global: Layer                          // 无 scope 特例
  scoped: Map<scopeKey, Layer>           // 每个 scope 一层
  effect(ctx, action, options) {         // 注册路由：scopeOf(ctx) → 层
    const scope = scopeOf(ctx)
    layer = scope ? (scoped.get(scope) ?? createLayer(scope)) : global
    return ctx.effect(function* () {     // 条目记在调用方 fiber（可逆）
      yield () => { undo(); if (layer.isEmpty()) scoped.delete(scope); }
    })
  }
  chainLayers(scope)                     // 沿 scopeParents 远→近收集
  merge(scope, pick)                     // global 打底 + 链逐层覆盖，近者赢
}
```

使用面（全部已核验）：**tools / skill / commands / system-prompt（sections/contexts/variables）/ jobs**——凡是"同名多实现、按 scope 分组的条目注册表"都用它。判据：**该服务需不需要'同一个逻辑名在不同 agent 面前有不同可见条目'**。

### 4.3 事件路由 scopeTarget

`scopeTarget(base, key)` 构造事件载体：无标签监听者全局收；带标签的监听者接收**自己 key 及所有祖先 key** 的事件（"events flow up the chain, never down"）——一个 standing 组合的监听者能收到每个加入它的 agent 的事件。

---

## 5. 工具域（tools 服务）

### 5.1 工具服务本体

`ToolRuntime extends Service`（dsh-tools）：类插件；`static inject = ["systemPrompt"]`；`Config = { mode: native|code|both, maxParallelSubCalls }`；构造 `super(ctx, "tools")` → 自动 `provide('tools', self)`。`config` 从所在行来（profile/preset 行；dsh-web-app 用 `mode: !!js process.env.DSH_TOOLS_MODE` 作为过渡开关）。

### 5.2 工具条目：defineTool → register

```ts
ctx.tools.register(defineTool({
  name, description,
  parameters: <JSON Schema 规格>,        // defineTool 编译成 JSON Schema + 包参数校验
  output: { schema, render(args, value), presentationMeta? },
  timeoutMs?,                             // 部署策略；永不进模型请求
  isConcurrencySafe?, execute(args, exec) { ... },  // 实现 = 闭包（内部可调 ctx.x 服务）
}))
// register → layers.effect(this.ctx, (layer) => layer.tools.insert(name, def), {label: "tools.register()"})
// tools 服务方法内的 this.ctx = 调用者 ctx（tracker/getTraceable）→ scopeOf → 落层
```

- 工具 = **(scope, name) 条目**（NamedEntries：层内同名 duplicate error；跨层自动共存、读取近遮蔽远）；工具**不是**服务槽——实现是注册表条目，不是绑定。
- 实现分层模式（seam）：工具插件只拥有模型面（schema/描述/展示），实现委托服务——`web_search` 的 execute 内部调 `ctx.web.search(...)`（"Execution goes through ctx.web — this module owns only the model-facing"）；`ctx.web` 内部又是 provider 注册表（`searchProvider` 配置选实现）。
- MCP：外部工具的 schema 与实现完全来自远端服务器，本地桥接注册为 `mcp__<server>__<name>`。typert：生成式声明注册（`<package>#<name>` 键 + Zod schema + resolver 可被 host 配置替换）。

### 5.3 可见性与细化控制

```ts
view(scope) {
  const layers = chainLayers(scope)               // [S(远), A(近)]
  visible = global.tools 打底
  for 链上每层（除 own）：覆盖同名条目
  for inherited：layers.every(layer => layer.admits(name)) 才可见   // 限制交集一票否决
  own 层条目无条件最后覆盖
  mode ≠ native → 注入 run_code
}
```

- `tools.restrict({allow, deny})`：**只允许 agent.ctx**（全局遮罩被刻意禁止）；只能限制"已知的 global 工具"；限制**交集**生效；自己层的注册不受限。
- `tools.guard(fn)`：plain ctx = 全局、agent.ctx = 该 agent；**单调拒绝**（只能 deny 不能放行）；`guardReason` = global → 链，首个拒绝即止。
- `tools.presentAs(mode)`：每 scope 一份展示声明，近者遮蔽 preset 默认；code 模式下模型只见 `run_code`，实际工具经生成的 SDK 调用。
- 配置面：行级 `config`/`disabled`/preset 行集决定"装不装、在哪层"；**per-agent 细化没有 YAML**，只能运行时 API。

### 5.4 执行管线（调用时）

`agent loop` 调度 → `executionMode`（serial/parallel 判定）→ 预执行（ordered pre-execute + guards）→ `[TOOL_RUNTIME_SCHEDULER].prepare/dispatch`（并发槽、`exec.signal` 取消、并发安全标记）→ 后执行（ordered post-execute）→ 物化（materialize：`finalizeContent`、演示渲染、超时策略 `tool-call-timeout-policy` 包装）。工具结果物化后进会话。

---

## 6. agent 组合与 preset

### 6.1 preset 文件与 standing 层

- preset = 目录 `$DSH_HOME/.agent-presets/<id>/`（`COMPOSITION_FILE = "agent.cordis.yml"`），可由 profile 行 `agent-presets`（`Config: {default 必填, roots[{path, trust}], includeUserRoot}`）指定扫描根。
- **standing 组合**：每 preset 首次使用时 `createScope(selfCtx, {agentPreset:id})` + `mountPreset`（agent.cordis.yml 行树挂进 standing 层）；**全进程共享、永久**（agent 加入是复用，不是重挂）。

### 6.2 mount / composeFrom：agent 如何继承工具集

```ts
// agent 工厂 setup：agent 的 scope key 挂到 preset standing key 下
bindScopeParent(scopeOf(agentCtx), standing.key)
// 子 agent：与父 agent 平级共享同一 standing（继承组合而非私有层）
composeFrom(childCtx, parentCtx) → bindScopeParent(childKey, standing.key)
```

- 父 agent A 与子 agent B 的链都是 `[S, A]` / `[S, B]`：**平级**；A 的私有工具 B 看不到（链上没有 A）；S 层的工具/守卫/提示词两者共享**同一份实例**（"the same plugin objects, the same tool registrations"）。
- 会话可选 preset：settings 命名空间 `agent-presets: {default}`（`$DSH_HOME/settings.yaml` 热重载）或 UI 选择器（`dsh-client-ui-agent-preset`）；会话 header 记录实际 preset（"newest selection wins"，空白会话可切换/重链）。

### 6.3 审计与外部读取

- `inactiveRows(tree)`：挂载后逐行核对"是否激活/在等什么服务"——失败即拒绝整次 mount；
- `leakedServices(ctx, mount)`：预设子树若把服务提供进 **root realm**（未隔离）→ mount 失败（"a preset service must sit behind an isolate realm or move to the host composition"）——**每会话服务不碰撞是审计强制的**；
- `serviceForAgent(ctx, agent, name)`：从组合外部（如浏览器 RPC）按 fiber 成员资格读取 agent 的私有服务实例（只读寻址）。

---

## 7. 生命周期全链走查

```
进程启动
 └─ root fiber(uid=0) ← profile 行树装载（每行一个 fiber：timer/llm/tools/...）
     ├─ agentPresets standing（首次 ensureStanding；共享永久）
     │    └─ S 层行树（每行一个 fiber，挂 standing 组合）
     ├─ AgentLoop（每会话一个）：
     │    ├─ scope fiber（createScope 的 no-op 插件）
     │    ├─ 组合行 fibers（工厂 setup 挂载）
     │    └─ agents registry（agent/created / agent/disposed 事件）
     └─ subagent ContinuationManager 的 activationOwner fiber
          └─ 子 agent（又一个 AgentLoop）：
               ├─ scope fiber（key = 子 loop 实例）+ composeFrom → S 层（平级共享）
               ├─ 组合行 fibers
               └─ Activation 驻留：多 FIFO turn；子孙还在跑则驻留；完成可释放；
                  会话持久化 → 冷恢复新 Activation

回收时机：fiber.dispose() / 依赖丧失→_refresh / scope.dispose（quiesceFiber 等 inertia）/
         HMR restart（同 fiber 重激活）/ subagents 服务卸载→drain（全 Activation 释放，会话保留）/
         进程关闭（全树 dispose，等待超时后强杀）
```

子 agent 关键语义（dsh-subagent，已核验）：Activation "may execute many FIFO turns and stays resident while descendants it created are still running"；`interrupt(keepInbox)` 可暂停恢复；`send_message` 走 FIFO inbox；完成 = "will do no further work unless you send it more"；插件热更到行级 = fiber.restart，到 `dsh-subagent` 服务级 = drain（进行中的子 agent 工作被清空，会话保留可冷恢复）。

---

## 8. 可逆性与论文映射

### 8.1 三层机制的论文归属

| 层 | 论文机制（已逐条核验） | 覆盖 |
|---|---|---|
| 效应层（时间可逆） | Definition 17/19、Theorem 11/15/16、Corollary 21、Theorem 61 | 一切 ctx.effect：服务 provide、事件 on、工具 insert、资源句柄 |
| 坐标代数层（空间可交换） | Definition 24 `(V_k, ≃_k, A_k)`、Definition 33/34 观测等价、Definition 39/Theorem 40/42 | 每类坐标各自代数：表值可交换（事件 emit、工具条目）、链值不可交换（waterfall） |
| 依赖治理层（响应式） | Definition 43/45/46/58、Theorem 59/63/64/66/73、O-Insert、guard | **只有服务绑定**：单写者、committed view、ordering、reconciliation |

- **可逆性证明不在"service 层"**：表值坐标（route/事件监听）是论文点名的代表案例（§3.3.2："either registration can be withdrawn while the other stands"）；service 独有的只是治理层。
- **tools 的层不在论文里**，但无需在：层是 ctx.effect 的组合物，可逆性由效应层定理继承。

### 8.2 单写者（single-source discipline）的完整语义

- **论文**：Definition 23（`set(k,v) requires k∉dom(σ)`，逆 `λσ′.σ′∖k`）、Definition 58(2)（`m≠n ⇒ p_m∩p_n=∅`）、O-Insert 前提（"a key has one possible provider because the orchestrator may not admit a second component declaring it"）。
- **源码**：`provide` 的 duplicate error；`set` 的"Only the fiber that provided the service may set it"；`NamedEntries.insert` 层内同名报错；事件按引用注销。
- **两层次澄清**：单写锁**键的绑定归属**（每坐标一个提供者）；表值坐标上多 fiber 各做各的条目增删（每条目归注册者）——真正被锁死的是"删掉别人的贡献"（无删除，4.4 的"有逆而无负元素"）。

### 8.3 为什么单写者必要

逆的良构性（"撤销自己加的那一项"必须良定义）；独立性证明的前提（Theorem 40/42 → Corollary 21 → Theorem 61/73）；reconciliation 可判定（committed view ω 必须是函数）。

### 8.4 改造时必须保持的不变量

1. **逆的良构性**：每个注册动作当场返回撤销函数；框架只保证"保存、执行、不执行两次"，不验证逆正确性（作者契约）；
2. **单写者**：一个坐标一个提供者；改值只许提供者；
3. **无删除**：只能撤销自己的贡献（能力式删除）；
4. **坐标稳定性**：名字/隔离标签一经确立不可变（Git 行号式漂移坐标不可用）；
5. **应用窗口时间静止**：注册展开期间相关坐标不变（epoch 中断 + inertia 原子转换的等价物）；
6. **激活与可逆正交**：响应式（何时触发）与可逆（如何撤销）是独立机制，可分离实现。

---

## 9. 对 nop-plugin / nop-ai-agent 改造的衔接

（机制层面的借鉴要点；决策与接口对比见 `design/nop-plugin/00-vision.md`、`01-architecture-baseline.md`、`04-interface-comparison.md`，本文不重复决策。）

> **与 nop-plugin 设计的差异清单**（2026-08-21 对照评估，修改已落入 `design/nop-plugin/01` 的 W8 注解与 `02` 的 W9 注解）：01-architecture-baseline 补实例状态机中间态（UNLOADING/FAILED）、coeffect 依赖粒度取舍（插件级 vs 服务级）、激活窗口时间静止语义、HMR 取舍记录（destroy+重建 vs cordis restart）；02-dsh-usage-coverage 差距表升级（agent 层 scope/realm 两维立项）、effect 回退顺序补注（单 effect 内 LIFO vs 顶层并发）、HMR 粒度精确化、set↔Delta 语义分层注。结论：核心结构对齐，无需推倒；agent 层的 scope/realm 机制列入 `nop-ai-agent` 立项（`instanceKey`/`parent` 链作基座）。

| 机制 | dsh 形态 | 对 nop-plugin 的借鉴面 | 对 nop-ai-agent 的借鉴面 |
|---|---|---|---|
| 可逆注册 | ctx.effect 统一 + 当场逆 | 现有 plugin load/unload 之上的 effect 系统化（design 已有） | agent 会话装配的可逆性（scope 卸载 = 全部逆） |
| 条件激活 | inject + notify/_checkImpl/_refresh | 依赖声明式激活；"等待中"状态显式可诊断 | 服务依赖驱动的 agent 装配 |
| 分层可见性 | ScopedLayers（scope key → 层，近者赢） | 插件级分层注册表原语 | 每 agent 的工具/技能/命令可见性模型 |
| realm 隔离 | isolate（逐名字、单写者、审计强制） | 服务名级隔离（多租户/多实例） | 每会话服务的碰撞防护（leakedServices 式审计） |
| 组合继承 | preset standing（共享实例的平级继承） | 配置行组 + 组合挂载审计（inactiveRows） | 子 agent 继承"能力集合"而非"私有注册"的语义 |
| 工具域 | tools 服务 + 层条目 + view/restrict/guard | —— | nop-ai-agent 的 tool 系统（可见性合成 + 单调守卫） |
| 事件代数 | @mode 声明 + 5 种派发 + 表/链分类 | 事件总线（design 已列独立基础设施） | turn/step 事件的水fall 式管线 |

**给后续改造 agent 的检查清单**（任一机制移植前先回答）：
1. 这个坐标是"绑定"（需要单写者+生命周期纠缠）还是"条目"（调用时解析、近遮蔽远）？
2. 撤销函数由谁提供、何时提供？卸载顺序如何保证？
3. realm/scope 两个维度是否都建模了？（很多设计只做其一）
4. 层内容的可见性判定是"合并遮蔽"还是"唯一解析"？与业务语义匹配吗？
5. 热更语义是"重激活同实例"还是"新建实例"？激活窗口的时间静止如何保证？

---

## 10. 证据索引

### 10.1 路径对照

| 语义 | npm 安装路径（本文核验） | 仓库源码路径（惯例） |
|---|---|---|
| cordis 内核 | `node_modules/@deepseek-ai/cordis/src/{fiber,events,context,reflect,service,utils}.ts`（4.0.1 附带 TS 源码） | `dsh/vendor/cordis/src/...` |
| scope 原语 | `node_modules/@deepseek-ai/dsh-scope/lib/index.js` | `dsh/packages/core/scope/src/{index,store}.ts` |
| 工具服务 | `node_modules/@deepseek-ai/dsh-tools/lib/index.js` | `dsh/packages/core/tools/src/index.ts` |
| loader | `node_modules/@deepseek-ai/cordis-plugin-loader/lib/index.js` + `types/config/*.d.ts` | `dsh/vendor/...` |
| patch/include | `node_modules/@deepseek-ai/cordis-plugin-include/lib/index.js` | — |
| HMR | `node_modules/@deepseek-ai/cordis-plugin-hmr/lib/index.js` | — |
| agent-presets | `node_modules/@deepseek-ai/dsh-agent-presets/lib/{index,mount}.js` | — |
| agent loop | `node_modules/@deepseek-ai/dsh-agent-loop/lib/index.js` | — |
| subagent | `node_modules/@deepseek-ai/dsh-subagent/lib/index.js` | — |
| 工具插件示例 | `node_modules/@deepseek-ai/dsh-tool-web/lib/index.js` | — |
| shipped 默认配置 | `node_modules/@deepseek-ai/dsh-base/cordis.patch.yml`、`dsh-web-app/cordis.patch.yml` | — |
| profile boot | `node_modules/@deepseek-ai/dsh-app-boot/lib/index.js` | — |

### 10.2 本会话核验记录（抽查项全部属实）

- fiber.ts：状态机、`disposables.splice(0).reverse()`（LIFO 单 effect 内）、`_unload` 顶层并发、epoch 中止、`getEffects`、`parent.extend({fiber:this})`、`restart/_refresh/_unload/inertia`；
- reflect.ts：单表存储、`provide/set` 单写检查、`Impl{name,fiber,value,check}`、notify 链、`mixin('fiber',['runtime','effect'])`、get 的 realm 边界；
- events.ts：`register → ctx.fiber.effect`、按引用注销、5 种派发模式；
- dsh-tools：`ToolRuntime extends Service`、`layers.effect`、`view/restrict/guard/presentAs/executionMode`、`wireSchemas`；
- dsh-scope：createScope 构造、scopeOf、bindScopeParent、ScopedLayers.effect 路由、scopeTarget 向上路由；
- dsh-agent-presets：standing 共享、mount/composeFrom 平级继承、inactiveRows/leakedServices/serviceForAgent、settings 命名空间；
- loader/Include：EntryOptions 全字段、`isolate` 行选项（LocalRealm/GlobalRealm）、patch deep-clone、`!!js` 求值；
- 论文：Definition 22/23/24/28/33/34/43/45/46/50/58、Theorem 40/42/59/61/63/64/66/73、Corollary 21、Lemma 71、§3.3.2 引文——编号与内容全部对上（含 "The last premise of O-Insert is where the single-source discipline is imposed"、"Theorem 66 turns that into the claim that the guard always releases"）；
- pi 对照（`~/ai/pi`）：session_start/session_shutdown 事件、reload 序列、stale ctx 文档要求全部属实；**pi 无 activate/deactivate 钩子**（已修正文章 §3.4）。

### 10.3 待进一步核验（本文未深挖，改造时按需再查）

1. `dsh-typert-*` 生成式工具注册的完整生命周期（loader 发现产物 → registry 注册 → resolver 替换）；
2. per-session realm 标签的精确铸造点（standing 共享下"两个会话不碰撞"的每个细节）；
3. 工具执行管线的完整事件序（pre/post-execute 展开、materialize 流程）与权限/沙箱的交互；
4. web 前端侧的 client-plugin HMR（`dsh-client-hmr`）与 host HMR 的联动。

---

## 附录：术语表

| 术语 | 定义 |
|---|---|
| **fiber** | 一次插件实例化的生命周期单元（依赖、激活、撤销账本）；`ctx.plugin()` 唯一产生 |
| **ctx 链** | 派生 ctx 的原型链；决定视图（realm 映射/intercept）与归属（`ctx.fiber`） |
| **entry** | 装配树的节点（id/name/config/...），loader 把它们变成 fiber |
| **patch** | 对行树的结构操作（insert/整行覆盖/禁用），多层按序应用 |
| **bundle** | 声明 `dsh.bundle.patch` 的 npm 包，是行的来源层 |
| **profile** | `$DSH_HOME/profiles/<name>/`：bundles 列表 + 用户 patch + 依赖 |
| **preset** | 目录 + `agent.cordis.yml`；一个 preset = 一个 standing S 层 |
| **standing** | 每 preset 一个的共享组合挂载（全进程一次，agent 加入复用） |
| **scope** | 上下文身份（kScope 标签 + 专用 fiber + 父链）；层归属与事件路由的维度 |
| **realm** | 逐名字的隔离符号；服务槽位的维度（root realm = 全局默认） |
| **layer** | ScopedLayers 中以 scope key 为键的条目表；domain × scope 矩阵单元 |
| **effect** | 可逆注册单元（执行 + 当场逆）；一切注册的原语 |
| **inertia** | fiber 进行中的 load/unload 转换链；await 直到稳定 |
| **committed view** | 论文概念：激活时依赖解析的唯一快照（源码侧对应 fiber.store + target 比对） |