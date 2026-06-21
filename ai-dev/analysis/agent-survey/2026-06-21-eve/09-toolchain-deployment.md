# 09 · CLI、构建管线与部署

> 来源：`packages/eve/src/cli/run.ts` + `packages/eve/package.json` scripts + `docs/reference/cli.md` + `docs/guides/deployment.md`

## CLI 入口

`bin/eve.js`（359 行）→ `dist/src/cli/run.js` → `runCli`（`src/cli/run.ts:677-700`）。

`bin/eve.js` 做的事：
1. 检查 Node 版本（用 vendored semver，从 `#compiled/semver/index.js` 加载）
2. `ensureBuiltCli`（`bin/eve.js:241-295`）检查 dist 是否需重建（对比 `src/bin/scripts` mtime 与 entrypoint mtime），需要时调 `tsc -p tsconfig.json` 重建
3. 动态 import `runCli` 并执行
4. 顶层捕获 error，遍历 `cause` 链打印；`process.exit` 防 dev 工具 native Handle 泄漏

## CLI 命令清单（Commander.js）

| 命令 | 描述 | 实现位置 |
|---|---|---|
| `eve init [target]` | 创建新 agent 或给已有项目添加 agent | `cli/commands/init.ts`（setup/ 引擎） |
| `eve build` | 编译 `.eve/` 并 build host 输出 | `run.ts:427-443` → `buildApplication` |
| `eve start` | 启动已 build 的 production server | `run.ts:446-470` → `startProductionHost` |
| `eve dev` | 启动 dev server + TUI（默认）或 headless | `run.ts:472-637` → `startDevelopmentServer` + TUI / `--url` 连远程 |
| `eve info [--json]` | 打印 resolved application 信息 | `run.ts:640-647` → `printApplicationInfo` |
| `eve eval [evalIds...]` | 运行 evals | `run.ts:650-669` → `runEvalCommand` |
| `eve channels add [kind]` | 交互式或按 kind 脚手架 channel | `run.ts:394-402` |
| `eve channels list [--json]` | 列出项目里的 authored channels | `run.ts:404-411` |
| `eve link` | 关联 Vercel 项目并拉取 AI Gateway 凭证 | `register-project-commands.ts:14-20` |
| `eve deploy` | 部署到 Vercel production（需要时先 link） | `register-project-commands.ts:22-28` |

**无参数运行 `eve` 等价于 `eve dev`**（`run.ts:683`）。

## 关键命令选项

### `eve dev`（`run.ts:472-519`）

```
--host                    监听 host
--port                    监听端口
-u, --url                 连远程部署（headless）
--no-ui                   不启 TUI
--name                    agent 显示名
--input                   初始输入
--tools <mode>            工具显示模式
--reasoning <mode>        推理显示模式
--subagents <mode>        子代理显示模式
--connection-auth <mode>  连接鉴权显示模式
--assistant-response-stats <mode>
--context-size
--logs <mode>
```

display modes: `full | collapsed | auto-collapsed | hidden`

### `eve eval`

```
--url                指向已有服务器/部署
--tag                按 tag 过滤
--strict             soft 阈值不达也 fail
--list               列出所有 eval
--timeout            单 eval 超时
--max-concurrency    最大并发
--json               JSON 输出
--junit              JUnit XML 输出
--skip-report        跳过 reporter
--verbose            详细输出
```

## eve 自身构建管线

`packages/eve/package.json` scripts（`266-285`）：

```
pnpm build
├── check:bin-runtime-dependencies.mjs        # 校验 bin 不引入新依赖
├── build:js
│   ├── build:compiled                          # vendor 第三方库到 .generated/compiled/
│   ├── clean                                   # 删 dist/
│   ├── build:types                             # tsc -p tsconfig.build.json（仅 .d.ts）
│   ├── copy-compiled-assets.mjs                # .generated/compiled/ → dist/src/compiled/
│   ├── build-rolldown.mjs                      # rolldown 把 src/**/*.ts → dist/src/**/*.js
│   │   └── 用 nitro 自带的 rolldown，preserveModules 保持 1:1
│   └── copy-runtime-assets.mjs                 # 拷运行时静态资源
├── copy-docs.mjs                               # 拷 docs/
└── stamp-version-tokens.mjs                    # 写版本信息
```

关键点：
- **JS emit 由 rolldown 接管**，tsc 只产 `.d.ts`（`build:types`）
- **rolldown 复用 nitro 的**（`loadNitroRolldown`，`src/internal/bundler/nitro-rolldown.ts:43-52`，通过 `createRequire(nitro/package.json).resolve("rolldown")`），eve 自己不直接依赖 rolldown——省一份 native binary
- **preserveModules 保持 1:1**：`src/**/*.ts` → `dist/src/**/*.js`，路径一一对应

其他 scripts：
- `clean`、`dev`（`build:compiled` → `copy-runtime-assets.mjs` → `tsc -p tsconfig.dev.json --watch`）
- `prepack`（npm 发布前打包）
- `test` / `test:unit` / `test:integration` / `test:scenario` / `test:watch` / `test:tui` / `test:vercel`
- `typecheck`（`check:web-template` → `build:compiled` → `tsc --noEmit`）
- `generate:web-template` / `check:web-template`（web 模板生成/校验）

## `#compiled/*` 虚拟模块（关键设计）

`packages/eve/package.json:48-52`：

```json
"#compiled/*": {
  "eve-source": "./.generated/compiled/*",
  "default":    "./dist/src/compiled/*"
},
"#*.js": {
  "eve-source": "./src/*.ts",
  "default":    "./dist/src/*.js"
}
```

这是 Node 的 [subpath imports](https://nodejs.org/api/packages.html#subpath-imports)——**虚拟模块别名**。

### 目的

让 eve 全代码库（生产时只 ship `dist/`）引用第三方依赖时**不依赖用户项目的 node_modules 解析**，而是引用 eve 自己 vendored 的副本。

- **Dev / 源码模式**（`eve-source` 条件）：解析到 `.generated/compiled/*`，由 `pnpm build:compiled`（`scripts/vendor-compiled.mjs`）生成
- **发布 / dist 模式**（`default` 条件）：解析到 `dist/src/compiled/*`，由 `scripts/copy-compiled-assets.mjs` 在 build 时从 `.generated/compiled/` 拷贝

### `#*.js` 是什么

eve 全代码库的内部别名：
- dev 走源码 TS（`./src/*.ts`）
- 构建产物走 dist JS（`./dist/src/*.js`）

这让源码里可以写 `import { foo } from "#discover/discover-agent.js"`，dev/build/production 三态都正确解析。

### Vendored 的 27 个库

`scripts/vendor-compiled/index.mjs` 列出：

| 类别 | 库 |
|---|---|
| **AI SDK** | `@ai-sdk/anthropic` / `@ai-sdk/google` / `@ai-sdk/mcp` / `@ai-sdk/openai` / `@ai-sdk/otel` / `@ai-sdk/provider` |
| **Chat adapter** | `@chat-adapter/slack` / `@chat-adapter/state-memory` |
| **OpenTelemetry** | `@opentelemetry/api` |
| **Schema** | `@standard-schema/spec` |
| **Vercel** | `@vercel/detect-agent` / `@vercel/oidc` / `@vercel/sandbox` |
| **Workflow DevKit** | `@workflow/core` / `@workflow/errors` / `@workflow/world` |
| **三方工具** | `chat` / `chokidar` / `commander` / `experimental-ai-sdk-code-mode` / `gray-matter` / `jose` / `jsonc-parser` / `picocolors` / `semver` / `turndown` / `zod` / `zod-validation-error` |

代码里通过 `#compiled/zod/index.js`、`#compiled/@workflow/core/runtime.js`、`#compiled/commander/index.js` 引用。

每个模块由 `scripts/vendor-compiled/<pkg>.mjs` 单独配置。

## 应用构建（`eve build`）

`buildApplication`（`src/internal/nitro/host/build-application.ts`）→ `createApplicationNitro`（`create-application-nitro.ts`）→ `prepare` + `build` from `nitro/builder`。

- preset 自动选 `vercel`（VERCEL 环境时）或 `standalone`
- 输出到 `.output/`
- Workflow bundle 由 `WorkflowBundleBuilder`（`src/internal/workflow-bundle/builder.ts:56-`）在 Nitro build 时把 `src/execution/**` 编译成单个 workflow bundle

Nitro 在 eve 中做三件事：
1. **HTTP 服务器**（dev + production）
2. **路由系统**：`configureNitroRoutes` 注册所有 HTTP/WS 路由
3. **Bundler 提供者**：rolldown（dev bundle authored module / build eve 自身 / workflow bundle / Nitro application build）

## 部署

### 部署清单（`docs/guides/deployment.md`）

按顺序：

1. **`eve build`** → `.eve/`；Vercel 上再产出 `.vercel/output`
2. **环境变量**：模型凭证（`AI_GATEWAY_API_KEY` 或 `ANTHROPIC_API_KEY` 等）+ 路由鉴权密钥；**绝不**进源码或编译产物
3. **模型路由**：字符串 model id 走 Vercel AI Gateway；`LanguageModel` 实例直连 provider
4. **沙箱后端**：Vercel 上 `vercel()`；自托管 `defaultBackend()`（Docker → microsandbox → justbash）或自定义 `SandboxBackend`
5. **build-time prewarm**（仅 Vercel）：构建时预热可复用 sandbox 模板
6. **鉴权**：把脚手架的 `placeholderAuth()` 换成 `httpBasic()` / `jwtHmac()` / `oidc()` / `vercelOidc()` 或自定义 `AuthFn`。**fail-closed**
7. **Vercel 部署**：`vercel deploy` 或 Git 连接；平台自动识别 `eve` 并在 Observability 加 "Agent Runs" tab
8. **自托管**：`eve build && PORT=3000 eve start --host 0.0.0.0`，用 Nitro Node 输出；自行包 TLS / 进程管理；workflow state 默认在 `.workflow-data`
9. **冒烟验证**：`/eve/v1/health` → 起 session → 流式

### Vercel 专属增强（离开时需显式替换）

`docs/guides/deployment.md:141-151`：

| Vercel 能力 | 离开时 |
|---|---|
| Vercel Workflow | workflow-sdk local world（`.workflow-data/`） |
| Vercel Sandbox | Docker / microsandbox / just-bash |
| Vercel Cron | 手动触发 dev route |
| Vercel OIDC | `httpBasic()` / `jwtHmac()` / 自定义 |
| Deployment Protection bypass | 显式处理 |
| AI Gateway | 直连 provider + 自备凭证 |
| Vercel Connect | 自行管理 bot token |
| Agent Runs dashboard | 自建 OTel / Braintrust |

## 稳定 HTTP 协议（`src/protocol/routes.ts:5-134`）

| 路由模式 | 用途 |
|---|---|
| `/eve/v1/session` (POST) | 起新 session |
| `/eve/v1/session/:sessionId` (POST) | 给 session 发后续消息（用 continuationToken） |
| `/eve/v1/session/:sessionId/stream` (GET) | NDJSON 流式订阅 |
| `/eve/v1/health` | 健康检查 |
| `/eve/v1/callback/:token` | session callback channel |
| `/eve/v1/connections/:name/callback/:token` | connection callback channel |
| `/eve/v1/dev/schedules/:scheduleId` | dev-only schedule 触发 |

所有 `/eve/v1/*` 是**稳定协议**，跨版本兼容。

## 工程化水平总览

| 维度 | eve 的做法 |
|---|---|
| **依赖管理** | 单运行时依赖（nitro）+ 27 vendored 库 + syncpack 校验版本一致 |
| **构建** | rolldown（复用 nitro 的）+ tsc（仅 types）+ 机械不变量守卫 |
| **测试** | 四层（unit/integration/scenario/e2e）+ fixture-owned eval |
| **CI** | `pnpm guard:invariants`（baseline 只能缩）+ DCO 签名 + changesets |
| **文档** | 随包发布（`node_modules/eve/docs`）+ frontmatter/nav 校验（`pnpm docs:check`） |
| **代码风格** | oxlint（auto-fix）+ oxfmt（pre-commit hook） |
| **可检视** | `.eve/` 编译产物 git diff 友好 + `eve info` + `eve eval` |

这是**框架级工程化**的标杆——不只是一个能跑的 agent runtime，而是一个有完整工具链、测试体系、变更管理、文档发布的工程项目。
