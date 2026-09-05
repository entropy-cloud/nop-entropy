# Mission-Driver 用户手册

> 一份面向新用户的培训手册。读完这一份就能上手用 mission-driver 跑你的第一个 mission。
>
> 配套文档：[`../README.md`](../README.md)（命令速查）、[`../EXECUTION-PRINCIPLE.md`](../EXECUTION-PRINCIPLE.md)（内部执行原理，进阶）、[`../TROUBLESHOOTING.md`](../TROUBLESHOOTING.md)（卡住时怎么诊断）。

---

## 1. mission-driver 是什么

**一句话**：mission-driver 是一个 AI 开发循环引擎。你给它一个 mission 配置文件和一份需求/路线图文档，它就会循环驱动 AI agent 子进程，自动完成"状态检查 → 评审计划 → 执行计划 → 起草新计划 → 深度审计"的完整闭环，直到任务完成或审计配额耗尽。

它**不是**：
- 不是一个 chatbot 框架（不和人对话，全自动跑）
- 不是一个通用 agent runner（专为"长任务、有计划、要审计"的场景设计）
- 不是一个 IDE 插件（独立 CLI + Web 监控面板）

### 1.1 什么时候用 / 什么时候不用

| 场景 | 用不用 | 理由 |
|------|--------|------|
| 重构一个模块、预计要写 5-10 个 plan、需要审计 | ✅ 用 | 这就是 mission-driver 的甜区 |
| 修一个明确的小 bug（10 分钟内能搞定） | ❌ 不用 | 直接让 AI 改就行，overhead 不值 |
| 把一份 FSD（功能规格文档）落成代码 | ✅ 用 | FSD → Roadmap → 多个 plan → 自动执行 |
| 给一个文件改个名字 | ❌ 不用 | 一条命令的事 |
| 一次性清理 tech debt 清单（20+ 项） | ✅ 用 | mission 能批量处理、每项都有审计闭环 |
| 临时跑个脚本 | ❌ 不用 | 直接 bash |
| 给一个已有模块加新功能、需要文档+代码+测试同步 | ✅ 用 | mission 会反复审计确保文档/代码/测试不脱节 |

**判断准则**：如果你估这个任务要花 **超过 1 小时**、且**有明确的验收标准**（不是"看着改"），就用 mission-driver。几十分钟的小改动直接对话式开发更高效。

### 1.2 它是怎么工作的（30 秒版）

```
你的 mission.json + Roadmap 文档
        ↓
   mission-driver 启动
        ↓
   ┌→ CHECK（确定性状态门：commands.check 或 git 冲突标记检测）
   │      ↓ pass
   │  REVIEW_PLANS（评审 draft 状态的 plan）
   │      ↓ all_complete
   │  EXEC_PLANS（执行 active 状态的 plan，每个 plan 一个子流程）
   │      ↓ all_complete
   │  DRAFT_PLANS（从 roadmap 起草新 plan）
   │      ↓ created → 回 REVIEW_PLANS
   │      ↓ nothing（roadmap 没新东西了）
   │  DEEP_AUDIT（深度审计：multi-audit + open-audit + 起草补救 plan）
   └──── complete → 回 REVIEW_PLANS（接着执行审计创建的 plan）
                   ...
        ↓ maxAuditRounds 用完 + 没遗留 → 任务完成
```

每个方框是一个 **step**，由 AI agent（`opencode run` 子进程）或工具脚本执行。方框之间的箭头是 **transition**，由 agent 输出的 marker（`pass` / `created` / `nothing` / `complete` 等）决定。

### 1.3 安装（首次使用前必读）

**两种使用方式**：① 在本模板仓库直接试用；② 一键安装到你的项目。两者都是**零安装、零构建**——引擎已零 npm 依赖（`commander` vendor 内联）、`web/dist/` 已提交入 git。

**前置条件**：Node.js ≥ 18、`opencode` CLI 在 PATH 中；Windows 用 Git Bash / WSL。

---

#### 方式一：在本模板仓库直接试用

```bash
# 从 GitHub Releases 下载最新 Source code (zip/tar.gz)：
#   https://github.com/pymjer/attractor-guided-engineering-template/releases
# 或直接 clone：
git clone https://github.com/pymjer/attractor-guided-engineering-template.git
cd attractor-guided-engineering-template

# 验证即可用（无需 pnpm install / build）
./tools/mission-driver.sh list                  # 列出 missions
./tools/mission-driver.sh run demo --dry-run    # 跑 demo（全栈可跑，echo 命令）
./tools/mission-driver.sh monitor               # 打开 http://localhost:9300 → 完整 Dashboard
npm --prefix tools/mission-driver test          # 引擎自测（556 pass + prompt-check: OK）
```

> monitor 从已提交的 `web/dist/` 静态托管界面，**无需 build**。

---

#### 方式二：一键安装到你的项目（推荐）

模板自带 `install-age.sh` 脚本，自动完成脚手架拷贝、shim 创建、环境配置：

```bash
cd attractor-guided-engineering-template       # 进入模板仓库

# 安装到目标项目（项目名可选，缺省从目录名推导）
./install-age.sh /c/Work/my-project
./install-age.sh /c/Work/my-project "My Project"
```

脚本自动完成（~10 秒）：

| 步骤 | 内容 |
|------|------|
| ① 拷脚手架 | 77 个 docs/ 文件（manifest 精确控制，排除 template-internal 产物） |
| ② 建 shim | `tools/mission-driver.sh`（引擎不复制，经 `MISSION_DRIVER_HOME` 引用） |
| ③ 配环境 | `.env` + `.env.example`（自动算相对路径） |
| ④ 建 mission | `missions/base.json`（commands 占位待填）+ `demo.json` + `demo-roadmap.md` |
| ⑤ 建日志目录 | `docs/logs/{year}/` |
| ⑥ 补 .gitignore | `.env`、`_tmp/` |
| ⑦ 报告 | 打印 COPIED / SKIPPED 清单 |

安装后验证：

```bash
cd /c/Work/my-project

./tools/mission-driver.sh list                 # 应显示 base + demo
./tools/mission-driver.sh run demo --dry-run   # 全流程跑通（不消耗 AI 额度）
./tools/mission-driver.sh monitor              # 打开 :9300，多项目同时运行自动切换端口
```

安装后还需填写的 4 个文件（详见 `docs/context/project-context.md` 开头的注释）：

1. `docs/context/project-context.md` — 项目身份 + 验证命令
2. `docs/context/ai-autonomy-policy.md` — 保护区域 + reviewer availability
3. `docs/context/codebase-map.md` — 入口点 + 常见变更路径
4. `missions/base.json` — `commands.*` 改为你的 test/build/lint/typecheck 命令（`check` 可选，留空/省略 = git 冲突标记兜底）

---

#### （可选）前端开发者

只有修改 `web/` 前端源码时才需要装依赖并重建：

```bash
cd tools/mission-driver/web
pnpm install                              # 装前端依赖（vue / naive-ui / xterm / vue-flow …）
pnpm dev                                  # 热更新开发：vite 跑在 :5173，/api 代理到 :9300
#   另开一个终端：./tools/mission-driver.sh monitor --dev   （只提供 API/SSE，不托管静态文件）

# 改完前端后，必须重建并提交 dist（否则别人 clone 到的是旧界面）：
pnpm build                                # vue-tsc 类型检查 + vite 打包 → 更新 web/dist/
pnpm check:dist                           # 校验 dist 与源码一致（CI 也会跑同样检查）
git add dist && git commit                # 把更新后的 dist 一并提交
```

> **为什么要提交 dist？** 让使用者 clone 即用、免安装免构建。CI（`.github/workflows/web-dist-check.yml`）会在 `web/` 变更时自动重建并比对，若忘了提交最新 dist 就会失败拦截。

### 1.4 在其他项目中集成（不复制引擎，用 shim）

> **快速路径**：上节"方式二"的 `install-age.sh` 已自动完成下面 ①②③ 的全部手动步骤。
> 本节描述**脚本背后做了什么**，以及脚本完成后的**手动定制要点**。如果你已用脚本安装，
> 直接跳到 [③ missions 配置](#③-missions-配置) 和 [④ 项目级定制](#④-项目级定制)。

引擎**只在本模板仓库维护一份**（单一真相源）。其他项目**不复制引擎**，而是放一个瘦 shim 脚本，
经环境变量 / `.env` 指向本模板的 `tools/mission-driver`。下面用一个示例项目 **`orion-pay`**
（一个 Java/Maven 多模块项目，模块如 `CORE` / `BILLING`）演示。

**前提**：本模板已 clone 到本地（引擎零依赖、`web/dist/` 已随仓库提交，无需安装或构建）。

**① 在 `orion-pay` 项目建 shim** `tools/mission-driver.sh`（`install-age.sh` 自动生成，内容如下）：

```bash
#!/bin/bash
# 引擎不在本仓库；经 MISSION_DRIVER_HOME（环境变量 或 仓库根 .env）指向共享模板引擎。
DIR="$(cd "$(dirname "$0")" && pwd | tr -d '\r')"
PROJECT_ROOT="$(cd "$DIR/.." && pwd | tr -d '\r')"

# 环境变量优先于 .env：先存已设值，加载 .env，再恢复
_ENV_MDH="$MISSION_DRIVER_HOME"
if [ -f "$PROJECT_ROOT/.env" ]; then set -a; . "$PROJECT_ROOT/.env"; set +a; fi
[ -n "$_ENV_MDH" ] && MISSION_DRIVER_HOME="$_ENV_MDH"

if [ -z "$MISSION_DRIVER_HOME" ]; then
  echo "ERROR: MISSION_DRIVER_HOME 未配置。请 cp .env.example .env 并设置引擎路径。" >&2
  exit 1
fi

# 相对路径从项目根解析；绝对路径直接用
case "$MISSION_DRIVER_HOME" in
  /*|[A-Za-z]:[/\\]*) ABS_HOME="$MISSION_DRIVER_HOME" ;;
  *) ABS_HOME="$(cd "$PROJECT_ROOT/$MISSION_DRIVER_HOME" 2>/dev/null && pwd | tr -d '\r')" ;;
esac
if [ -z "$ABS_HOME" ] || [ ! -f "$ABS_HOME/src/main.js" ]; then
  echo "ERROR: MISSION_DRIVER_HOME 无效：$MISSION_DRIVER_HOME" >&2; exit 1
fi

exec node "$ABS_HOME/src/main.js" --dir "$PROJECT_ROOT" --missions-dir "missions" "$@"
```

**② 配置引擎路径**——用相对路径，别写死绝对路径。建 `.env.example`（进 git）+ `.env`（进 `.gitignore`）：

```bash
# orion-pay/.env.example
# 从项目根解析的相对路径；按你本地模板检出位置调整
MISSION_DRIVER_HOME=../attractor-guided-engineering-template/tools/mission-driver
```

```bash
cp .env.example .env        # 复制后按需修改；.env 不进 git
echo ".env" >> .gitignore   # 若尚未忽略
```

> env 优先于 .env：CI 里可直接 `export MISSION_DRIVER_HOME=...` 覆盖，无需改 .env。

<a id="③-missions-配置"></a>

**③ `missions/` 配置**。先写共享默认 `missions/base.json`（以 orion-pay 的 Maven 为例）：

```json
{
  "model": "zhipuai-coding-plan/glm-5.2",
  "agent": "build",
  "maxCycles": 8,
  "planGuide": "docs/plans/00-plan-authoring-and-execution-guide.md",
  "auditsDir": "docs/audits",
  "contextDir": "docs/context",
  "moduleDir": "CORE",
  "commands": {
    "test": "mvn -pl CORE -am test -T 4",
    "build": "mvn -pl CORE -am clean package -DskipTests -T 4",
    "lint": "mvn -pl CORE -am validate",
    "typecheck": "mvn -pl CORE -am test-compile -T 4",
    "check": "mvn -pl CORE -am compile -T 4"
  },
  "commitFormat": "<type>: [ORION-XXXX] [CORE] <description>"
}
```

再为每个目标写 `missions/<name>.json`（`extends: "base"`，只填差异字段：`name` / `description` /
`roadmapPath` / `plansDir` / 目标模块的 `moduleDir` + `commands`）。
**注意**：`moduleDir` 必须是真实存在的目录（引擎会校验）。

> **plansDir 约定**：每个 mission 的 `plansDir` 应指向 `docs/plans/<mission-name>/` 子目录
> （如 `docs/plans/abo-bug-fixes`），而不是直接用 `docs/plans`。这样多个 mission 的 plan
> 不会混在一起。`install-age.sh` 生成的 `demo.json` 已遵循此约定（`plansDir: "docs/plans/demo"`）。

<a id="④-项目级定制"></a>

**④ 项目级定制（不 fork 引擎）**。引擎**优先搜索项目目录**，再回退模板内置，所以定制放本项目即可：

- `missions/flows/*.json` — 项目专属 flow / 子流程（引擎先搜这里）
- `missions/prompts/*.md` — 覆盖某个内置 prompt，或新增项目专属 prompt

**⑤ 验证与运行**：

```bash
# 验证安装（install-age.sh 已自带 demo mission，可立即验证）
./tools/mission-driver.sh list                   # 列出 missions（应有 base + demo）
./tools/mission-driver.sh run demo --dry-run     # 全流程跑通（不消耗 AI 额度）
./tools/mission-driver.sh monitor                # 打开 :9300 Dashboard

# 正式运行你的 mission
./tools/mission-driver.sh list-steps <name>      # 校验 mission + 看步骤
./tools/mission-driver.sh run <name> --dry-run   # mock 验证编排（不调真实模型）
./tools/mission-driver.sh run <name>             # 正式运行
```

> **多项目同时运行 monitor**：端口冲突自动切换（:9300 → :9301 → :9302…），
> 最多支持 20 个并发 monitor。

**⑥ 限制（务必知道）**：自定义 `type: script` 步骤依赖引擎侧 `SCRIPT_REGISTRY` 注册，
**无法**从项目 `missions/` 注入。若你的 flow 用了自定义 scriptId，共享引擎会报 `Unknown scriptId`。
这类需求需在模板引擎里加脚本插件点，或暂不走共享引擎。**普通 mission（默认 flow）不受此限制**。

---

### 可选：切换到 pi 执行器

默认每个 AI step 调用 `opencode run`。如果你更想用 [`pi`](https://github.com/earendil-works/pi-coding-agent)，一行参数即可切换（opencode 仍是默认，零变化）：

```bash
./tools/mission-driver.sh run <mission> --driver pi --model zai-coding-cn/glm-5.2
```

- 前置条件改为 `pi` CLI 在 PATH（不再需要 `opencode`）。
- `--driver pi` 时引擎自动套用 pi 默认参数（`-p --append-system-prompt @<persona>` + stdin 传 prompt + 工具白名单），无需手填。
- **模型 id 格式不同**：opencode 用 `zhipuai-coding-plan/glm-5.2`；pi 用自己的 `provider/model`（如 `zai-coding-cn/glm-5.2`）。
- **已知限制**：pi 不支持跨 step 的 session 连续性，每个 step 起独立进程、靠 prompt 从磁盘读 roadmap/plans 恢复状态（与 prompt 设计一致）。
- 完整配置项见 `tools/mission-driver/README.md` §Driver selection。

## 2. 核心概念

读这一节不用全记住，遇到术语回来查就行。

| 术语 | 含义 |
|------|------|
| **Mission** | 一次任务。由 `missions/<name>.json` 配置文件定义：用哪个 flow、roadmap 在哪、测试命令是什么等。 |
| **Flow** | 状态机定义，描述 step 之间怎么跳转。存为 `flows/*.json`。默认 flow 是 `mission-driver`。 |
| **Step** | 状态机里的一个节点。可以是 `agent`（AI 子进程）、`tool`（shell 命令）、`script`（内联 JS）、`subflow`（子状态机）、`group`（容器）。 |
| **Plan** | 一个独立的执行单元（一份 markdown 文档，存在 `docs/plans/<mission>/`）。状态机：`draft` → `active` → `completed`。 |
| **Roadmap** | 一份 markdown 文档，列出 mission 要落地的所有工作项（Work Items）。DRAFT_PLANS 从这里起草 plan。 |
| **Marker** | agent 步骤的输出标签（包在 `<AI_STEP_RESULT>...</AI_STEP_RESULT>` 里），决定下一步走哪。例如 `pass` / `fail` / `created` / `nothing` / `issues` / `complete`。 |
| **Subflow** | 一个 step 内嵌套跑的子状态机。例如 `EXEC_PLANS` 是 subflow，对每个 active plan 跑一遍 `plan-execution` 子流程。 |
| **Audit** | 深度审计。两种：multi-dimensional（多维度检查清单）、open-ended（开放式找隐藏风险）。 |
| **Run** | 一次 mission 的运行实例。每次跑会在 `_tmp/<时间戳>-mission-driver/` 下生成一个 run 目录，存所有日志和状态。 |

---

## 3. 典型工作流（5 步上手）

### Stage A：准备需求文档

mission-driver 不接受口头需求。你必须先有一份**结构化的需求源**，三选一：

1. **FSD（Functional Specification Document）** —— 适合新功能开发。放在 `docs/design/` 或 `docs/requirements/`。
2. **Bug 列表** —— 适合修复任务。放在 `docs/bugs/` 或一份 issue 集合。
3. **优化点清单** —— 适合 tech debt 清理、性能优化、文档同步等。

这份文档是 mission 的**源头真相**。后面 Roadmap 从这里派生，agent 在审计时也会回查这里确认行为是否符合预期。

> 提示：文档越具体，mission 跑得越准。"优化性能"这种模糊描述会让 agent 反复试错；"把 list 渲染从 O(n²) 降到 O(n)，目标 1000 条数据 < 50ms"这种可验收的描述能让 agent 一次到位。

### Stage B：生成 Roadmap 和 mission.json

有了需求文档，接下来生成 mission 的两个核心配置。

**方式一（推荐）：用 `draft` 命令自动生成**

```bash
./tools/mission-driver.sh draft "为 user-service 模块加 OAuth2 登录，详见 docs/design/oauth-fsd.md"
```

`draft` 命令会：
1. 跑一个 brief agent，问清范围、生成简短的 brief 文档（`docs/backlog/<slug>-brief.md`）
2. 跑一个 draft agent，生成 mission.json + Roadmap 文档

`--target-file` 是可选输入辅助——description 可引用任意路径（单个文件、目录、多个文件或抽象目标），`--target-file` 只是把某个文件/目录喂给 brief agent 锚定范围；`--flow-hint` 指定 flow 类型：

```bash
./tools/mission-driver.sh draft "实现 X" --target-file docs/design/oauth-fsd.md --flow-hint mission-driver
```

description 也可以直接引用目录或多个文件，不传 `--target-file`：

```bash
./tools/mission-driver.sh draft "读取 docs/input/ 下所有需求文档，生成 roadmap"
```

**方式二：手写**

参考 `tools/mission-driver/mission.json.example`，最小可用的 mission.json：

```json
{
  "extends": "base",
  "name": "my-mission",
  "description": "一句话描述这个 mission 干什么",
  "flowName": "mission-driver",
  "roadmapPath": "docs/backlog/my-mission-roadmap.md",
  "plansDir": "docs/plans/my-mission",
  "commands": {
    "test": "pnpm test",
    "build": "pnpm build",
    "lint": "pnpm lint",
    "typecheck": "pnpm typecheck",
    "check": ""
  }
}
```

`extends: "base"` 让 mission 继承 `missions/base.json` 里的共享默认值（model、agent、maxCycles 等），通常不用自己写。`commands.check` 是 CHECK 步骤的可选确定性状态门；留空/省略则回退 git 冲突标记检测（见 [§5.1](#51-默认-flow-的-5-个-step)）。

Roadmap 文档是 markdown，格式大致：

```markdown
# My Mission Roadmap

| WI | Status | Description |
|----|--------|-------------|
| 1  | todo   | 实现 OAuth2 客户端 |
| 2  | todo   | 集成到登录流程 |
| 3  | todo   | 添加单元测试和 e2e 测试 |
```

### Stage C：执行 mission

```bash
./tools/mission-driver.sh run my-mission
# 或等价的主命令形式（不带 run 关键字）：
./tools/mission-driver.sh my-mission
```

启动后引擎会：
1. 在 `_tmp/<时间戳>-mission-driver/` 创建 run 目录
2. 启动 monitor dashboard（默认 `http://localhost:9300`）
3. 从 CHECK 步骤开始跑 flow
4. 把每个 step 的日志、状态、事件流写到 run 目录

**第一次跑某个 mission 时**，CHECK 步骤的 agent 会读 mission 配置和 roadmap，确认环境就绪。然后 DRAFT_PLANS 会从 roadmap 起草第一批 plan，REVIEW_PLANS 评审它们，EXEC_PLANS 执行它们。

### Stage D：打开 Monitor 监控

启动 mission 后会自动打开 monitor，或单独启动：

```bash
./tools/mission-driver.sh monitor
```

浏览器打开 `http://localhost:9300`：

- **Run List 页**（`/`）：列出所有 run，状态标签（running / completed / failed）、当前 step、进度条
- **Run Detail 页**（`/runs/:runId`）：
  - 顶部 timeline：每个 step 的开始/结束/耗时/marker
  - 中部 log viewer：点击 step 名查看完整 agent 日志（xterm.js 终端样式）
  - 右侧 MissionConfig：展开看 mission.json 解析后的配置
  - 底部 Resource Monitor：最近若干条快照的内存、opencode RSS/数量、node 数量、内存压力表格（看资源压力）
  - 顶部右侧 Deep Audit tag：显示当前 audit round / maxAuditRounds
- 实时事件流（SSE）：step 启动/完成、transition、心跳——无需刷新

> 如果同时跑多个 mission（或一个 mission + 一个独立 monitor），端口会自动 +1 重试（9300 → 9301 → 9302 …）。

### Stage E：完成后做复盘

```bash
./tools/mission-driver.sh analyze           # 复盘最近一次 run
./tools/mission-driver.sh analyze 2026-07-21-095220-mission-driver   # 复盘指定 run
```

`analyze` 会：
1. 扫描 run 目录的所有事件/日志
2. 跑一个 postmortem agent，生成结构化复盘报告（亮点、问题、根因、可复用经验）
3. 报告写到 `tools/mission-driver/memory/` 形成长期记忆，下次跑同类 mission 时 agent 会读到

---

## 4. 命令参考

### 4.1 主命令：run

```bash
./tools/mission-driver.sh run <mission-name> [options]
./tools/mission-driver.sh    <mission-name> [options]   # 等价（主命令形式）
```

**常用 flag**：

| Flag | 作用 | 示例 |
|------|------|------|
| `--dry-run` | 用 mock agent，不调真实模型。验证 flow 编排用 | `--dry-run` |
| `--step <STEP>` | 单步模式：只跑指定 step 然后停（`maxSteps=1`，调试用） | `--step CHECK` |
| `--from-step <STEP>` | 从指定 step 开始，之后照常循环（不改 transitions） | `--from-step DEEP_AUDIT` |
| `--no-monitor` | 不启动 monitor dashboard（CI / 后台跑用） | `--no-monitor` |
| `--fast` | 快速模式：跳过 `fastSkipSteps`（默认跳 DEEP_AUDIT） | `--fast` |
| `--skip-steps <list>` | 显式跳过某些 step（逗号分隔，与 `--fast` 取并集） | `--skip-steps DEEP_AUDIT,CHECK` |
| `--model <id>` | 覆盖模型 ID | `--model zhipuai-coding-plan/glm-4.7-flash` |
| `--parse-model <id>` | 覆盖解析/纠错路径用的模型（可用更便宜的） | `--parse-model gpt-4o-mini` |
| `--max-cycles <n>` | 主循环最大次数 | `--max-cycles 5` |
| `--max-total-steps <n>` | 总 step 数硬上限 | `--max-total-steps 100` |
| `--agent <name>` | 指定子 agent（默认 `build`） | `--agent refactor` |
| `--monitor-port <port>` | 指定 monitor 端口 | `--monitor-port 9400` |

**`--step` vs `--from-step` 区别**（重要）：

```bash
# 调试：只跑 CHECK 一次，跑完就停，整个 mission 退出
./tools/mission-driver.sh run my-mission --step CHECK

# 续跑：从 EXEC_PLANS 开始，跑完 EXEC_PLANS 后继续 DRAFT_PLANS → DEEP_AUDIT → ...
# 适合"上次 DEEP_AUDIT 创建了 plan 但 EXEC_PLANS 没跑完，想接着跑"的场景
./tools/mission-driver.sh run my-mission --from-step EXEC_PLANS
```

两者互斥，同时传会报错退出。

**环境变量**（同样可配置）：

```bash
OPENCODE_MODEL=<id>             # 等价 --model
OPENCODE_PARSE_MODEL=<id>       # 等价 --parse-model
OPENCODE_AGENT=<name>           # 等价 --agent
MAX_CYCLES=<n>                  # 等价 --max-cycles
MAX_TOTAL_STEPS=<n>             # 等价 --max-total-steps
MONITOR_PORT=<port>             # 等价 --monitor-port
MONITOR_DISABLE=1               # 等价 --no-monitor
PROJECT_ROOT=<path>             # 覆盖项目根目录
OPENCODE_PURE=1                 # opencode 以 --pure 模式运行（跳过外部插件）
```

### 4.2 draft：从描述生成 mission.json

```bash
./tools/mission-driver.sh draft "<description>" [options]
```

两阶段：
1. **brief 阶段**：生成 scope-gate brief（写到 `docs/backlog/<slug>-brief.md`），让 brief agent 判断范围是否清晰
2. **draft 阶段**：基于 brief 生成 mission.json + Roadmap

flag：`--target-file <path>`（可选输入辅助——指向目标文件/目录；description 可引用任意路径）、`--flow-hint <name>`（指定 flow）、`--skip-brief`（跳过 brief 阶段、退化成单阶段 draft）。

### 4.3 analyze：复盘

```bash
./tools/mission-driver.sh analyze              # 最近一次 run
./tools/mission-driver.sh analyze <runId>      # 指定 run
```

### 4.4 monitor：独立 monitor

```bash
./tools/mission-driver.sh monitor              # 浏览历史 run，不启 engine
./tools/mission-driver.sh monitor --dev        # 开发模式（前端用 vite，端口 5173）
./tools/mission-driver.sh monitor --monitor-port 9400
```

### 4.5 list / list-steps

```bash
./tools/mission-driver.sh list                 # 列出所有可用 mission
./tools/mission-driver.sh list-steps my-mission   # 列出某 mission 的所有 step
```

---

## 5. 理解 Flow：状态机怎么跑

这一节讲清楚 mission-driver 的核心循环。**看懂这一节，你就能预测任务跑下去会发生什么。**

### 5.1 默认 flow 的 5 个 step

`flows/mission-driver.json` 定义了主流程，5 个核心 step：

| Step | 类型 | 干什么 | 输入 | 输出 marker |
|------|------|--------|------|-------------|
| **CHECK** | agent | 确定性状态门：配置了 `commands.check` 就跑它（可自动修复则诊断 + 修复 + 重跑），没配置则回退 git 冲突标记检测。**不**跑 `commands.test`（那是 BUILD_VERIFY 的职责）。 | mission.commands | `pass` / `needs_fix` / `fail` |
| **REVIEW_PLANS** | agent (forEach) | 评审所有 `draft` 状态的 plan | `draftPlans()` | `all_complete` / `some_failed` / `all_failed` |
| **EXEC_PLANS** | subflow (forEach) | 执行所有 `active` 状态的 plan | `activePlans()` | `all_complete` / `some_failed` / `all_failed` |
| **DRAFT_PLANS** | agent | 从 roadmap 起草新 plan | roadmap 文档 | `created` / `nothing` |
| **DEEP_AUDIT** | subflow | 跑深度审计（multi-audit + open-audit） | 整个项目 | `complete` / `failed` |

### 5.2 状态流转图

```
                   ┌──────────────────────────────────────────┐
                   │                                          │
                   ▼                                          │
   ┌──── CHECK ────┴──── REVIEW_PLANS ──── EXEC_PLANS ──── DRAFT_PLANS ────┐
   │     │                  (forEach             (forEach         │        │
   │     │                   draftPlans)          activePlans)    │        │
   │     │ fail                │                    │            │        │
   │     ▼                     │ all_complete       │ all_complete│       │
   │   failed                  ▼                    ▼            │        │
   │                       EXEC_PLANS           DRAFT_PLANS       │        │
   │                                                                  │        │
   └──── pass (loop re-entry from terminal reconciliation)           │        │
                                                                       │        │
                                          ┌───────────────────────────┘        │
                                          │                                     │
                                          │ created → 回 REVIEW_PLANS            │
                                          │                                     │
                                          │ nothing                              │
                                          ▼                                     │
                                       DEEP_AUDIT (subflow)                      │
                                          │                                     │
                                          │ complete → REVIEW_PLANS ─────────────┘
                                          │   (执行审计创建的 plan)
                                          │
                                          │ failed → DRAFT_PLANS
                                          │
                                          └──→ (循环直到 maxAuditRounds 用完)
```

**第一次循环**（CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS）：
- CHECK 跑确定性状态门（配置了 commands.check 就跑它，否则 git 冲突标记检测）
- REVIEW_PLANS 没东西可评审（还没 draft plan），forEach 为空 → `all_complete`
- EXEC_PLANS 没东西可执行（还没 active plan），forEach 为空 → `all_complete`
- DRAFT_PLANS 从 roadmap 起草第一批 plan → `created`

**第二次循环**（DRAFT_PLANS created → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS）：
- REVIEW_PLANS 评审刚起草的 plan，把它们推到 `active`
- EXEC_PLANS 执行 active plan（每个 plan 跑一个 plan-execution 子流程）
- DRAFT_PLANS 再看 roadmap → 还有就 `created`，没有就 `nothing`

**进入审计循环**（DRAFT_PLANS nothing → DEEP_AUDIT）：
- DEEP_AUDIT 子流程跑 multi-audit + open-audit，发现遗漏的问题
- 在子流程里起草补救 plan，标记 `planned`
- DEEP_AUDIT 返回 `complete` → 回 REVIEW_PLANS（执行补救 plan）
- DRAFT_PLANS 又看 roadmap → 可能又 `nothing` → 又进 DEEP_AUDIT
- **如此循环直到 `maxAuditRounds`（默认 3）用完，且没遗留 audit/plan**

### 5.3 Subflow：子状态机

某些 step 类型是 `subflow`——在主 flow 内嵌套跑一个完整的状态机。

**EXEC_PLANS 的 subflow**（`plan-execution.json`）：对每个 active plan 跑一遍：
```
EXECUTE → CLOSURE_SCRIPT_CHECK → CLOSURE_AUDIT → BUILD_VERIFY
```
- EXECUTE：让 agent 按 plan 内容改代码
- CLOSURE_SCRIPT_CHECK：检查 plan 里规定的脚本是否能跑
- CLOSURE_AUDIT：审计 plan 是否真的完成（验收标准是否满足）
- BUILD_VERIFY：跑测试/构建确认没破坏 baseline

**DEEP_AUDIT 的 subflow**（`deep-audit-loop.json`）：
```
CHECK_OPEN_AUDITS → MULTI_AUDIT → OPEN_AUDIT → SCAN_NEW_RESULTS
```
- CHECK_OPEN_AUDITS：先看有没有遗留的 open audit（之前审计过但没解决的）
- MULTI_AUDIT：按多维度检查清单审计（设计/测试/架构/路由/安全等）
- OPEN_AUDIT：开放式找隐藏风险（不被清单限制）
- SCAN_NEW_RESULTS：把审计发现起草成补救 plan

### 5.4 Plan 的生命周期

```
   draft ──REVIEW_PLANS──→ active ──EXEC_PLANS──→ completed
     ↑                        │
     │                        └── (审计发现问题) ──→ 回 draft 修订
     │
     └── DRAFT_PLANS / SCAN_NEW_RESULTS 创建
```

- **draft**：刚起草、还没评审。REVIEW_PLANS 会派 sub-agent 独立评审，通过后推到 active。
- **active**：评审通过、可以执行。EXEC_PLANS 拿来跑。
- **completed**：EXEC_PLANS 跑完且 CLOSURE_AUDIT 通过。

### 5.5 maxAuditRounds：审计配额

审计循环不是无限的。`flows/mission-driver.json` 里 `maxAuditRounds: 3` 意思是：
- DEEP_AUDIT 最多跑 3 轮
- 每轮审计如果发现新问题 → 创建 plan → 下一轮 DRAFT_PLANS → EXEC_PLANS 执行
- 如果连续 3 轮审计都没新发现，或者所有 plan 都执行完了 → mission 完成

这个值可以改。审计密集的任务调高（5-10），快速迭代调低（1-2）。

---

## 6. Mission 配置详解

### 6.1 mission.json 字段

```json
{
  "extends": "base",                       // 继承 base.json
  "name": "my-mission",                    // mission 名称（唯一）
  "description": "一段话描述",              // 给 agent 看的使命说明
  "flowName": "mission-driver",            // 用哪个 flow（默认 mission-driver）
  "roadmapPath": "docs/backlog/roadmap.md",// roadmap 文档路径
  "plansDir": "docs/plans/my-mission",     // plan 文件存放目录
  "planGuide": "docs/plans/00-plan-...md", // plan 编写指南（agent 起草 plan 时参考）
  "auditsDir": "docs/audits/my-mission",   // 审计报告存放目录
  "contextDir": "docs/context",            // 项目上下文目录（agent 启动时读）
  "moduleDir": "tools/my-module",          // 目标模块根目录（agent 改代码的范围）
  "commands": {                            // 验证命令
    "test": "pnpm test",
    "build": "pnpm build",
    "lint": "pnpm lint",
    "typecheck": "pnpm typecheck",
    "check": ""                             // 可选：CHECK 的确定性状态门；留空/省略 = git 冲突标记兜底
  },
  "prompts": {                             // 审计 prompt 模板路径
    "multiAudit": "docs/skills/multi-dimensional-audit-prompt.md",
    "openAudit": "docs/skills/open-ended-audit-prompt.md"
  },
  "commitFormat": "feat(<scope>): <desc>"  // commit message 格式
}
```

### 6.2 base.json 共享默认值

`missions/base.json` 让多个 mission 共享配置：

```json
{
  "model": "zhipuai-coding-plan/glm-5.2",
  "agent": "build",
  "maxCycles": 8,
  "maxInnerCycles": 6,
  "maxTotalSteps": 500,
  "fastSkipSteps": ["DEEP_AUDIT"],
  "contextDir": "docs/context",
  "commands": { "test": "...", "build": "..." }
}
```

mission 通过 `"extends": "base"` 继承（浅合并：嵌套对象整体替换，不是深合并）。

### 6.3 自定义 flow

如果你的任务不适合默认的 5-stage flow，可以写自己的 `flows/my-flow.json`：

```json
{
  "name": "my-flow",
  "entry": "START",
  "maxCycleVisits": 10,
  "steps": {
    "START": {
      "type": "agent",
      "promptPath": "prompts/start.md",
      "transitions": {
        "ok": { "goto": "END" },
        "retry": { "retry": "START", "maxRetries": 3 }
      }
    },
    "END": { "type": "agent", "promptPath": "prompts/end.md", "transitions": { "ok": { "done": "completed" } } }
  }
}
```

mission.json 里 `"flowName": "my-flow"` 引用即可。

详细 flow schema 见 [`design/mission-design.md`](../design/mission-design.md)。

---

## 7. 常见模式与配方

### 7.1 Bug 修复 mission

需求文档：`docs/bugs/2026-07-21-login-crash.md`（一个 bug 报告）。

```bash
./tools/mission-driver.sh draft "修复登录崩溃 bug #123，详见 docs/bugs/2026-07-21-login-crash.md" \
  --target-file docs/bugs/2026-07-21-login-crash.md
```

### 7.2 新功能 mission

需求文档：`docs/design/feature-fsd.md`（FSD）。

```bash
./tools/mission-driver.sh draft "实现 OAuth2 登录，FSD 见 docs/design/oauth-fsd.md" \
  --target-file docs/design/oauth-fsd.md
```

### 7.3 Tech debt 清理 mission

需求文档：`docs/backlog/tech-debt-2026-q3.md`（一个清单）。

```bash
./tools/mission-driver.sh draft "清理 Q3 tech debt 清单" \
  --target-file docs/backlog/tech-debt-2026-q3.md
```

### 7.4 只想审计、不想执行（dry-run audit）

```bash
./tools/mission-driver.sh run my-mission --step DEEP_AUDIT --dry-run --no-monitor
```

### 7.5 续跑：上次跑到一半被 Ctrl+C 了

```bash
# 看 run-state.json 找到最后跑到的 step
cat _tmp/<runId>/run-state.json | grep currentStep

# 从那个 step 续跑
./tools/mission-driver.sh run my-mission --from-step <那个 step>
```

注意：mission-driver 不支持 checkpoint 续跑（每次 `--from-step` 是新 run），但 plan 文件状态会持久化在 `docs/plans/` 里，所以 EXEC_PLANS 不会重复跑已 completed 的 plan。

---

## 8. 常见问题与排错

### 任务跑着跑着卡住了？

看 [`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md)。先快速判断：
1. step 进程还活着吗？（`ps aux | grep opencode`）
2. step 日志还在长吗？（`tail -f _tmp/<runId>/oc-<STEP>-*.log`）
3. 网络请求还挂着吗？（模型 API 可能限流）


### 两个 mission 能同时跑吗？

可以，但端口会自动 +1（9300 → 9301）。建议不同 mission 用不同 monitor-port，避免 dashboard 混淆。

### 怎么看 agent 在干什么？

三种方式：
1. Monitor dashboard 的 log viewer（最直观）
2. `tail -f _tmp/<runId>/<mission-name>.log`（看 step 转换/心跳）
3. `tail -f _tmp/<runId>/oc-<STEP>-*.log`（看具体 step 的 agent 输出）

### 模型限流频繁怎么办？

- 调小 `maxCycles`（少跑几轮）
- 用 `--parse-model` 把纠错路径换便宜模型
- 错峰跑

---

## 9. 进阶：执行原理深读

读完前面就能用了。如果你想理解引擎内部细节（spawn 子进程、心跳、看门狗、原子写状态、reflexion 记忆机制），看：

- [`EXECUTION-PRINCIPLE.md`](../EXECUTION-PRINCIPLE.md) —— 组件分层、时序图、子进程管理
- [`design/mission-design.md`](../design/mission-design.md) —— flow schema 完整设计
- [`design/mission-driver-flow-design.md`](../design/mission-driver-flow-design.md) —— 默认 flow 的设计决策
- [`CONTEXT.md`](../CONTEXT.md) —— 给 AI agent 自己读的项目上下文（30 秒了解工具）

---

## 10. 快速参考卡

```bash
# 一次性看完所有命令
./tools/mission-driver.sh --help

# 列出 mission
./tools/mission-driver.sh list

# 跑 mission
./tools/mission-driver.sh run <name>
./tools/mission-driver.sh    <name>            # 等价
./tools/mission-driver.sh    <name> --dry-run  # mock 模式
./tools/mission-driver.sh    <name> --step CHECK          # 单步调试
./tools/mission-driver.sh    <name> --from-step DEEP_AUDIT # 续跑

# 生成 mission
./tools/mission-driver.sh draft "<description>" --target-file <fsd-or-bug.md>

# 监控
./tools/mission-driver.sh monitor                  # 独立 monitor
# 浏览器打开 http://localhost:9300

# 复盘
./tools/mission-driver.sh analyze
./tools/mission-driver.sh analyze <runId>
```

---

**Happy mission driving! 🚀** 遇到问题查 [`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md)，想加深理解查 [`EXECUTION-PRINCIPLE.md`](../EXECUTION-PRINCIPLE.md)。
