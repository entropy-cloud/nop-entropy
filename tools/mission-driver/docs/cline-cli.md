# Cline 命令行说明文档

> 本文档记录 [Cline CLI](https://docs.cline.bot/cli)（`npm i -g cline`，本机验证版本 **3.0.49**）
> 的命令集合与 headless 用法，并说明 mission-driver 用 `--driver cline` 时如何映射。
> 与 mission-driver 的 `opencode`（默认）/ `pi` 驱动并列。

## 1. 是什么

Cline 是终端里的自主编码 agent CLI，与 VS Code 插件的 Cline 共享同一套 agent 核心。
支持 TUI 交互，也支持 fully headless 的 CI/CD / 脚本用法（one-shot / `--json` / `--yolo`）。

- 交互 TUI：`cline` 或 `cline -i`
- 一次性 one-shot：`cline "你的 prompt"`（单轮后退出）
- JSON 流式输出：`cline --json "..."`（NDJSON，便于管道处理）
- Yolo（跳过审批、跑完即退）：`cline --yolo "..."`
- Zen（投到后台 hub daemon 后立即退出）：`cline --zen "..."`

## 2. 安装 / 版本

```sh
npm install -g cline
cline --version   # 本机 3.0.49
```

## 3. 常用参数（Flags）

| 参数 | 含义 |
| --- | --- |
| `-m <model-id>` | 模型 ID（如 `deepseek/deepseek-v4-flash`） |
| `-P <provider-id>` | Provider ID（如 `cline`、`anthropic`、`openai`、`openrouter`…） |
| `-k, --key <api-key>` | 本次运行的 API key 覆盖（优先于环境变量） |
| `-s, --system <system-prompt>` | 覆盖系统提示词（persona 注入入口） |
| `--json` | 输出 NDJSON（headless 用；需要 prompt 参数或 stdin） |
| `-y, --yolo` | 跳过审批、启用 `submit_and_exit`，默认禁用 spawn/team 工具 |
| `--auto-approve [true\|false]` | 工具调用是否自动审批（默认 true） |
| `-t, --timeout <seconds>` | 单轮超时（秒） |
| `-c, --cwd <path>` | 工具工作目录 |
| `-p, --plan` | plan 模式（默认 act 模式） |
| `-i, --tui` | 交互 TUI |
| `--id <session-id>` | 恢复指定 session（配合 `cline history`） |
| `--thinking [none\|low\|medium\|high\|xhigh]` | 思考预算级别 |
| `--retries <count>` | 连续错误重试上限（默认 3） |
| `--compaction <agentic\|basic\|off>` | 上下文压缩模式 |
| `--data-dir <path>` | 使用隔离的本地状态目录（自动启用 sandbox） |
| `--config <path>` | 配置目录 |
| `--hooks-dir <path>` | hooks 目录提示 |
| `-z, --zen` | 投递到后台 hub daemon 后立即退出 |
| `--team-name <name>` | 覆盖 runtime team 状态名 |
| `-v, --verbose` | 详细运行诊断（耗时/token/估费） |
| `-V, --version` / `-h, --help` | 版本 / 帮助（退出） |

## 4. 顶层命令（Top-level commands）

```sh
cline config        # 交互式配置视图
cline history|h     # 列出 session 历史 / 管理已保存 session
cline version       # 版本
cline update        # 检查 CLI 与 kanban 更新
cline auth <provider>  # 认证：`cline auth cline` 走 OAuth；`--provider/--apikey/--modelid` 可带 key
cline connect <adapter> # chat connector（telegram/gchat/whatsapp）
cline schedule       # 定时任务
cline doctor         # 健康检查 / 清理陈旧进程（`cline doctor fix`）
cline hub            # 管理本地 hub daemon
cline mcp            # 管理 MCP servers
```

## 5. 常见用例

### 5.1 一次性调用（headless）

```sh
# 普通 one-shot
cline "审计这个仓库并给出修复建议"

# 指定 provider 与模型
cline -P openrouter -m google/gemini-3-pro -k sk-... "搭一个 storybook"

# NDJSON 输出给下游工具
cline --json "列出所有 TODO 注释" | jq -r 'select(.type == "agent_event" and .event.text) | .event.text'
```

### 5.2 认证（OAuth provider 不会自动弹浏览器）

```sh
cline auth cline                      # Cline 账号 OAuth 登录
cline auth --provider anthropic --apikey sk-... --modelid claude-sonnet-4-6
```

## 6. 免费 DeepSeek（本机实测配置）

本机 `providers.json` 只配了一个 provider `cline`（带 WorkOS accessToken），默认模型即
**`deepseek/deepseek-v4-flash`**。也就是说**免费 DeepSeek = 走 Cline 官方/额度通道的
DeepSeek 模型**，不需要额外 API key：

```sh
cline -m deepseek/deepseek-v4-flash -P cline "你好"
```

模型 ID 格式是 provider/model。用 `-m` 只传模型时沿用最后使用的 provider（本机即 `cline`），
也可显式 `-P cline`。

## 7. mission-driver 驱动映射（`--driver cline`）

mission-driver 用 `MISSION_DRIVER_EXEC` / `--driver cline` 把执行器切到 Cline。
映射关系如下：

| mission-driver 概念 | opencode | pi | **cline** |
| --- | --- | --- | --- |
| 模型选择 | `run -m {model} ...` | `-p --model {model}` | `-m {model} -P cline` |
| 跳过权限/审批 | `--dangerously-skip-permissions` | `-p`（拦截=免确认） | `--auto-approve true`（默认已 true） |
| person 注入 | `--agent {agent}`（agents.yaml） | `--append-system-prompt @<file>` | `-s <persona-content>` |
| prompt 传递 | `run ... "prompt"`（arg） | stdin | 位置参数 `"prompt"`（arg） |
| 输出格式（标记可解析） | 文本 | 文本 | `--json`（NDJSON，内含 `<AI_STEP_RESULT>` 子串） |
| session 连续性 | `--session <id>`（`opencode session list`） | 无（每步新进程） | 无（每步新进程，同 pi） |

引擎自动填充的 cline 默认参数模板：

```
-m {model} --json --yolo --auto-approve true -s {agentFile}
```

- `--json` + `--yolo`：headless、单步退出、禁用 spawn/team（引擎自己编排多进程独立 agent，
  不需要 Cline 的子 agent/team 特性）。
- `-s {agentFile}`：把 `agents/build.cline.md` 人格内容作为系统提示注入。
- prompt 是位置参数，`promptMode=arg`。
- 与 pi 一致：**没有跨步骤 session 连续性**，每步启动全新进程、状态从磁盘（roadmap/plans）恢复。

### 直接命令行对照示例

```sh
# mission-driver 内部等价于（每次 step 一个独立进程）
cline -m deepseek/deepseek-v4-flash -P cline --json --yolo --auto-approve true \
      -s "$(cat agents/build.cline.md)" "<step 完整 prompt>"
```

## 8. 已知限制 / 注意事项

### 8.1 单 hub daemon 约束（重要）

Cline 3.0.49 是「每机一个 hub daemon」模型：`~/.cline` 下有一个常驻 daemon 监听
`127.0.0.1:25463`（对应你正在跑的活跃 CLI 会话）。在已有活跃 daemon 在位时，
再起**任何**新的 `cline` 进程（包括 `config` / one-shot / `--json` / `--zen` / `--data-dir`）
都会尝试再启一个 daemon，随即因 `EADDRINUSE` 失败——这是版本行为，不是配置错误：

```
[hub-daemon] fatal: Failed to start hub server on 127.0.0.1:25463/hub:
Is port 25463 in use? (EADDRINUSE)
```

- **实测规避**：先退出/关闭正在跑的 cline 会话（或用 `cline doctor fix` 清掉陈旧 RPC
  进程）释放 25463，再以独立 data-dir + fresh 端口跑一次性会话做验证。
- 当前版本 env `CLINE_HUB_ADDRESS`、`CLINE_SESSION_BACKEND_MODE=local/remote` 均不能
  改变 daemon 绑定的 25463 端口（README 的 env 列表里也没有 `CLINE_HUB_ADDRESS`）。

### 8.2 `-s` 会整体覆盖系统提示

`-s` 是「覆盖」而非「追加」。mission-driver 的 `build.cline.md` 人格是精简的步骤执行
角色声明，步骤的完整指令由 prompt 自带；因此覆盖系统提示不影响任务执行。若你的人格
需要保留 Cline 默认系统提示，应改为把人格内容并入 prompt 而不是 `-s`。

### 8.3 `--json` 输出与结果标记

`--json` 输出为 NDJSON 行，结果为 `<AI_STEP_RESULT>xxx</AI_STEP_RESULT>` 会出现在
某一行的 `text` 字段里（作为普通子串，未被转义）。mission-driver 引擎对整个输出做
`/<AI_STEP_RESULT>\s*(\w+)/` 匹配，因此可正常解析。

## 9. 参考

- 官方 CLI 文档：<https://docs.cline.bot/cli>
- 本机 README：`/opt/homebrew/lib/node_modules/cline/README.md`（npm 全局安装，v3.0.49）
