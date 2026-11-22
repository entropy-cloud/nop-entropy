# AGE 模板 v2 六仓消费者适配（升级调研与执行记录）

> Status: resolved
> Date: 2026-11-22
> Scope: ~/app/{nop-chaos-next, nop-app-iot, nop-app-mall-wt/nop-app-mall-master, nop-app-erp, nop-chaos-flux, nop-entropy}
> Conclusion: 六仓全部完成 attractor-guided-engineering-template v2 适配；Java 三仓（iot/mall/erp）nop-ib 增量编译 check exit=0 实证；nop-entropy 按用户裁定保留 ai-dev/docs-for-ai 双体系做最小适配。

## 背景与模板 v2 原理

模板已从"带 mission-driver 引擎的模板"演化为纯文档+轻工具链模板：

- 安装机制：`install-age.sh`（实为 `tools/install-age.mjs`）按 `install-age.manifest` 分发，**只支持首装**——有既往 origin 或模板文件内容冲突即整体拒绝（`Capability parity conflict`），无原地升级路径。升级 = 备份冲突文件 → 安装 → 手工合并定制。
- 方法论收束：16 篇 guide 收进 `docs/guides/`（`<artifact>-<activity>` 命名），roadmaps/plans 只留骨架。
- Mission Driver 退出消费者分发：无引擎/missions 配置；plan frontmatter `mission:` 只是普通字段；机械门收束为 standalone `tools/plan-status.mjs`；交付为 `commit-plan.mjs` 一计划一提交（`AGE-Plan:` trailer）。
- git skill 统一为 `.agents/skills/nop-git`（一 plan 一 commit + `Verification:` 行）+ `nop-grillme`。

## 各仓执行记录

| 仓库 | 收口在途 | 安装 | mission 退役 | git skill 换代 | ib |
|---|---|---|---|---|---|
| nop-chaos-next（pilot） | 1 commit | 74 文件/5 冲突保留 | shim 删 + 1 json 归档 | 无旧 skill | N/A（Node） |
| nop-app-iot | 1 commit | 68/11 | 12 json 归档；plan guide 深度绑定引擎，独有内容（Decision Table/回执陷阱/plan-gates 批门/scoped verification）并入 Local Additions | 无旧 git skill | ✅ refresh+check exit=0 |
| nop-app-mall | 1 commit（162 文件） | 52/24 | 2 json 归档 | 删 master/worktree | ✅ exit=0 |
| nop-app-erp | 1 commit | 47/31 | 14 json 归档；补 mvnw wrapper（自 mall 引入） | 删 master | ✅ exit=0 |
| nop-chaos-flux | 1 commit | 63/13 | shim 删 + 31 json 归档，全仓引用改指归档路径 | 删 master/worktree | N/A（Node） |
| nop-entropy | 2 个 Java 文件**保留未提交**（会话前已在途，非本任务产物） | **不安装**（见下） | 33 json 归档至 `ai-dev/archived/missions-2026-11-22/`；`tools/mission-driver/` 引擎按产品代码保留（nop-ai-agent 移植成果） | 删 mission-driver/master/worktree/bundle，新增 .agents/skills | 已有 |

## 关键裁定

1. **nop-entropy 不跑 installer**：其 `ai-dev/`+`docs-for-ai/` 双体系是本地权威（用户明示 `docs/` 被历史遗留占用不要动），installer 会写入 docs/ 结构与之冲突。改为最小手工适配（技能/missions/AGENTS 引用），自家 `ai-dev/tools/check-doc-links.mjs --strict` exit=0。
2. **missions 一律归档不删除**：erp 14/flux 31 个 json 内含 commands/prompts 资产，归档至 `docs/archive/missions-2026-11-22/`（entropy 为 `ai-dev/archived/`），引用机械改指归档路径。
3. **旧 guide 退役 + Local Additions 并入**：各仓旧 plan guide 中仍有效的项目独有硬规则（回执行陷阱、五处一致性、contract surface≠semantics、design 最新态）追加进新装 `docs/guides/plan-authoring-and-execution.md` 的 Local Additions 节；历史 roadmap/plan/log 的旧引用机械替换保持检查器绿色。
4. **AGE pre-commit 不启用**（next：core.hooksPath=husky/lint-staged 在用）。

## 残留与 watch-only

- nop-chaos-next/iot/mall/erp/flux 各仓 `docs/guides/README.md` 均已双段路由（AGE 段+本地段）；如后续模板再升级，`age-drift.mjs` 会报告 Local Additions/自定义 README 的漂移——这是有意为之（本地定制不纳管）。
- next 的 docs/logs/index.md、iot 的 docs/process 与 docs/logs/00-log-writing-guide.md 等本地旧 guide 与模板新 guide 并存，hub 已路由；后续可按 doc-maintenance-checklist 逐步收敛。
- erp 的 `docs/plans/` 仍为旧命名风格（无 slug 子目录），模板建议 `docs/plans/<roadmap-slug>/`，未强制迁移（存量 982 文件，迁移属独立 plan）。
