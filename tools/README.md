# tools/ — 项目工具目录

本目录集中放置项目自带的开发/校验工具。**除下述两类内容外，任何工具脚本必须落在仓库内，禁止引用仓库外的工具路径**（例外：mission driver 使用 AGE 模板，见 `mission-driver/`）。

## Node 依赖（pnpm 管理）

`package.json` + `pnpm-lock.yaml` 统一管理 AI 工具面与门禁脚本的 Node 依赖（当前：`mermaid` + `jsdom`，供 `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs` 的 Mermaid 真实解析校验使用）。

克隆仓库后执行一次：

```shell
cd tools
pnpm install
```

之后即可运行全部依赖 Node 包的工具；未安装时相关校验会显式输出"跳过"并降级为轻量检查（不产生误报）。

- `node_modules/` 不入库（.gitignore 已覆盖）。
- 新增工具依赖：改 `package.json` 后在 `tools/` 下重跑 `pnpm install`，并在本 README 的使用方清单中登记。

| 依赖包 | 使用方 |
|---|---|
| mermaid + jsdom | `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`（Mermaid 块无头渲染校验） |

## mission-driver

mission 驱动器（AGE 模板），入口 `./tools/mission-driver.sh`，详见 `mission-driver/README.md`。此工具允许引用仓库外的 AGE 模板，是本目录唯一的例外。
