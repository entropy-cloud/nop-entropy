# mission-driver — 项目上下文

> 让 AI 在 30 秒内了解本工具，不包含废话。


## 是什么

`tools/mission-driver/` — AI 开发循环引擎。读 `missions/<name>.json`，按 flow JSON 定义的状态机循环执行**可配置 driver 子进程**（默认 `opencode run`；`--driver pi` 切到 `pi -p`）。附监控 Dashboard（Node http + SSE + Vue 3 前端）。

**语言**: Node.js (ESM) + TypeScript (仅前端)  
**依赖**: 引擎**零 npm 依赖**（`commander` 已 vendor 内联至 `vendor/`，见 commit 0a40c5f）；前端独立 `web/package.json`，但 `web/dist/` 已提交入 git → **整体 clone 即跑，消费者零 install / 零 build**  
**位置**: 本工具位于项目仓库的 `tools/mission-driver/` 子目录，所有路径以此为基准。运行命令从仓库根目录执行。


## 目录结构

```
tools/mission-driver/
├── src/
│   ├── main.js            # 入口：解析 CLI → 加载 mission → 启引擎 + monitor
│   ├── config.js          # 配置解析（CLI/env/mission.json → 运行参数）
│   ├── engine.js          # 状态机核心，最复杂的文件
│   ├── executor.js        # 步骤执行：spawn opencode 子进程，心跳/超时/SIGTERM
│   ├── runner.js          # opencode 进程管理 + sessionId 提取
│   ├── monitor.js         # HTTP/SSE server（纯 Node http 模块）+ REST + SSE 端点
│   ├── mission-check.mjs  # mission 校验 + extends 合并（base.json → base.local.json → mission）
│   ├── flow-loader.js     # flow JSON 加载 + plans 扫描 + 表达式函数注册
│   ├── expression.mjs     # 轻量表达式引擎（when 条件 / forEach 源）
│   └── platform.mjs       # 平台兼容层（Windows/macOS/Linux）
├── flows/                 # 流程定义 JSON
│   ├── mission-driver.json    # 主流程: CHECK → REVIEW → EXEC → DRAFT → DEEP_AUDIT
│   ├── plan-execution.json    # 子流程: EXECUTE → CLOSURE_SCRIPT → CLOSURE_AUDIT → BUILD_VERIFY
│   └── deep-audit-loop.json   # 审计子流程
├── prompts/               # AI 指令模板（{{var}} 替换）
├── web/                   # Vue 3 前端（Naive UI + TypeScript + Vite）
├── memory/                # Reflexion 自记忆（--analyze-run 生成）
├── test/                  # 后端测试（node --test）
└── design/                # 引擎设计文档
```

> Mission 配置放在项目根的 `{projectRoot}/missions/`，不在 tools/ 下。


## Mission 配置系统

**文件位置**: `{projectRoot}/missions/`（不在 tools/ 下）

**优先级**: `CLI --model/--parse-model/--driver` > `MISSION_DRIVER_EXEC`/`MISSION_DRIVER_ARGS`/`MISSION_PROMPT_MODE` env > `mission.json` 自有字段 > `base.local.json` > `base.json`

**driver 可选 `opencode`（默认）/ `pi`**（pi-driver 支持）：`driver=="pi"` 时 config.js 自动套用 pi 默认 `driverArgs`（`-p --model {model} --append-system-prompt @{agentFile} --tools read,write,edit,bash,grep,find,ls`）+ `promptMode:"stdin"` + 计算字段 `agentFile`（引擎相对绝对路径 `<engine>/agents/build.pi.md`，消费端经 `import.meta.url` 定位）。`runner.js` 对非 opencode driver 抑制 `--pure`/`--variant`/`--dangerously-skip-permissions`，且 `findLatestSessionId` 对 pi 跳过 `opencode session list`。详见 `README.md` §Driver selection、`docs/architecture/mission-driver-baseline.md` §Driver selection。pi 已知限制：无 session 连续性（每 step 起 fresh pi，靠 prompt 从磁盘恢复状态）。

**base.json**（进 git）— 全仓库 mission 共享默认值，任何模块可通过 `extends: "base"` 继承:
```
model, parseModel, agent, maxCycles, planGuide, auditsDir, contextDir, moduleDir, commands, commitFormat
```

**base.local.json**（不进 git，`missions/.gitignore` 已配置）— 个人覆盖:
```
sourcePaths（依赖模块源码路径，不同同事路径不同）
```

`mission-check.mjs` 中的 `resolveExtends` 实现浅合并链。`validateMission` 仅校验 `name/roadmapPath/plansDir/commands.test`——缺失 `roadmapPath` 的文件（如 base 配置）被 monitor.js 的 `GET /api/configs` 自动过滤。

**可选 mission 字段 `promptsDir`**（mdr-fix-2）：mission 可独立覆盖整套 prompt。Prompt 解析优先级链（高→低）：`mission.promptsDir` → `missions/prompts/`（全仓库共享覆盖）→ 内置 `TOOL_ROOT/prompts/`（`flow-loader.js loadPrompt` 兜底）。主流程（`createMissionDriverFlow`）与子流程（`loadSubFlow`）均读 `config.missionPromptsDir`（`config.js` 解析为绝对路径或空串），未设置时行为与旧版完全一致。`promptsDir` 为可选字段（不在 `REQUIRED_FIELDS`），但若设置则由 `mission-check.mjs` 校验路径存在（typo fail-fast，同 `moduleDir`/`contextDir`）。

**CHECK 为可配置确定性状态门**（mdr-fix-3）：主流程 entry `CHECK`（`prompts/health-check.md`）按 `commands.check`（base.json 默认 `""` = 未配置）决定行为——配置时运行 `{{checkCmd}}`，失败且可自动修复则诊断+修复+重跑并 emit `needs_fix`（engine 经 `needs_fix → {retry:"CHECK",maxRetries:2}` 重试，耗尽则 `onMaxRetries:{done:"failed"}` 终止），不可修复或 `commands.test` 类问题 emit `fail`（`fail → {done:"failed"}` 终态，无重试）；未配置时回退 git 冲突标记检测（clean/dirty → `pass`，未解决冲突标记 → `fail`）。`needs_fix` 是新增 transition key（非 markerAlias），与 OPT-4 既有契约兼容——未配置 mission 的 `fail`/`onError`/`onMaxRetries` 仍一次性终止（`check-lightweight.test.js` 守护，无 repair death-loop）。CHECK 不跑 `commands.test`（那是 BUILD_VERIFY 的职责）。`checkCmd` 经 `main.js delegates.vars` 注入，已在 `context-map.mjs` VAR_PROVENANCE/EXPECTED_VARS 登记（drift gate）。


## Monitor Dashboard 前端

**技术栈**: Vue 3 + Naive UI 2 + TypeScript + Vite + xterm.js + Pinia（资源监控用 Naive UI 表格，ECharts 已移除）

**路由**: `/` → RunList, `/runs/:runId` → RunDetail

**API 端点**（monitor.js 提供）:
- `GET /api/runs` — 最近 run 列表
- `GET /api/runs/:id` — run 详情 + events + stepLogs
- `GET /api/runs/:id/logs/:step` — 日志 tail
- `GET /api/runs/:id/sysmon` — 系统资源快照
- `GET /api/configs` — Mission 配置列表（跳过无 roadmapPath 的 base 文件）
- `GET /api/configs/:name/roadmap` — 解析 roadmap markdown
- `GET /api/configs/:name/plans` — Plans 列表
- `GET /api/configs/base` — 合并后的 base.json + base.local.json
- `GET /api/runs/:id/events` — SSE 实时事件流

**关键 UI 交互**:
- Mission Config: n-card（可折叠，默认收起，标题右侧 ChevronDown/Up 切换）
- Log Viewer: xterm.js 终端，文件名点击 → Blob URL 新标签页打开完整日志
- Log Viewer 图标: ArrowDownOutline/PauseOutline/ChevronDownOutline/ChevronUpOutline（Ionicons 5）
- Resource View: Naive UI 表格，最近 8 条 sysmon 快照（Time / Free Mem GB / Opencode RSS GB / Opencode / Node / Pressure）+ Active Processes 表（ECharts 已移除）
- Base Config: 任意页面右上角 ⚙ 齿轮按钮 → Modal（n-code JSON 高亮）
- NFR-3: xterm 按 RunDetail 路由懒加载；naive-ui 按需导入（`unplugin-vue-components` + `NaiveUiResolver`，无全局 `app.use(naive)`，Vite tree-shake 掉 Calendar/DatePicker/Transfer 等未用组件）；ECharts 已移除。首屏 JS gzip ≈198KB（由旧单一入口 409KB 降约一半）
- WI5: `GET /api/runs/:id` 返回的 `run` 含 `auditRound` / `maxAuditRounds`（旧 run-state.json `?? 0` 兜底）；RunDetail 顶部展示 'Deep Audit: N / M'（仅当 `maxAuditRounds > 0`；额度用完 tag→success，进行中→info）。RunList 与 AppHeader 的 `statusTagType` 同步把 `single_step_done` 识别为 success（与 `main.js` exitMap 的 exit code 0 对齐）。


## 构建与验证

```bash
# 后端测试（同时跑 prompt-check.mjs 结构性校验，任一失败即整体失败）
npm --prefix tools/mission-driver test

# 前端构建
npm --prefix tools/mission-driver/web run build

# Mission 校验
node tools/mission-driver/src/mission-check.mjs missions/<name>.json .

# 启动 mission（从项目根）
./tools/mission-driver.sh <mission-name>

# dry-run
node tools/mission-driver/src/main.js <mission-name> --step CHECK --dry-run --no-monitor
```


## 关键约束

- 引擎核心 **零 npm 依赖**（`commander` 已 vendor 至 `vendor/commander/`；monitor.js 仅用 Node 内置 `http`/`fs`/`path`/`url`）
- 前端 **零构建步骤**于运行时（Vite 构建产物 `web/dist/` **已提交入 git**，由 monitor 静态托管；新鲜度由 `.github/workflows/web-dist-check.yml` + `pnpm check:dist` 守卫）
- `memory/_index.md` 为 always-load 核心（`_` 前缀此处为例外，非生成文件）
- `extends` 为浅合并——嵌套对象（如 `commands`）整体替换，非深度合并
- Windows 环境：Git Bash 启动脚本
- 监控端口默认 9300，冲突时自动 +1 重试
- draft-robustness WI5（mdr-remediate-4 后扩展到非-forEach 分支）：subflow step 的 `subflowRuns` 在 `_executeSubflowStep` 中，无论 forEach 还是单子流程，都在子流程开始前写入 `status: "running"` placeholder（`_wfAppendSubflowRun`，镜像 `_onAgentStepUpdate` 模式但额外匹配 `visits` 以避免 re-entry 串味），forEach 每项完成后增量追加、子流程结束后由 `_wfClose` 用终态覆盖 placeholder，父进程中途被杀时 run-state 仍反映"在跑"或已完成项（不依赖 monitor fallback 扫描磁盘 `run-state-<stepName>-<visits>-<i>.json` 文件）。`_wfClose` 仍是最终真相（forEach 结束时 sort + 覆盖 placeholder）。与 step-audit mission 的 WI5（auditRound / maxAuditRounds 计数，见上方 Monitor Dashboard 段）同名但分属不同 mission。


## 故障排查

- `TROUBLESHOOTING.md` — 卡住时的诊断手册
- orphan 清理: `node tools/mission-driver/src/reap-orphans.mjs --startup _tmp <PID>`
- **并行安全**: 支持 N 个 mission-driver 并行（同项目 / 跨项目）。startup reaper（`reap-orphans.mjs`）按 run 维度判孤儿——spawn 的 opencode 带 `[MISSION_DRIVER:<runId>]` 标记，reaper 查全局 active-run 登记（`~/.mission-driver/active/`）+ `isAliveAndOurs` 判活，**永不误杀活跃的并行 run**；只回收"拥有进程已确证死亡"的崩溃 run 残留。无法证明死亡时一律 spare（保守）。
- Monitor 独立模式: `node tools/mission-driver/src/main.js --monitor`


## 文档入口

| 文档 | 路径 |
|------|------|
| 引擎设计 | `tools/mission-driver/design/mission-design.md` |
| 流程设计 | `tools/mission-driver/design/mission-driver-flow-design.md` |
| 执行原则 | `tools/mission-driver/EXECUTION-PRINCIPLE.md` |
| plan 编写指南 | `docs/plans/00-plan-authoring-and-execution-guide.md` |
