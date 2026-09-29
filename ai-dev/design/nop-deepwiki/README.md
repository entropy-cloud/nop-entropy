# nop-deepwiki 设计文档索引

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，是 `.opencode/skills/nop-deepwiki/`（项目 DeepWiki 生成工作流）的架构决策权威来源。skill 的操作指令本体在 `.opencode/skills/nop-deepwiki/SKILL.md`（怎么跑），本目录回答"为什么这样设计、边界在哪、下一步吸收什么"。

## 文档结构与阅读顺序

| 文档 | 层级 | 职责 | 状态 |
|------|------|------|------|
| [00-overview.md](./00-overview.md) | **Vision + Architecture Baseline 双职** | skill 定位、成功标准、non-goals、方法论原则、五阶段管线契约、质量门禁、增量维护模型 | active |
| [01-toolchain-absorption-design.md](./01-toolchain-absorption-design.md) | 吸收设计（需求规格） | 外部同类工具链机制盘点结论 → P0/P1/P2 吸收决策、拒绝清单与理由；含二轮对标吸收（deepwiki.com 新样本六项） | active（P0 由 plan 367 实施中） |

必读路径：00 → 01。执行 skill 时读 SKILL.md 即可，只有当要修改 skill 行为时才需要读本目录并遵循其中决策。

## 职责边界

- **本目录**：skill 的设计决策（链接基准为何是 blob 永久链接、密度门禁为何这么定、吸收什么拒绝什么）。
- **`.opencode/skills/nop-deepwiki/SKILL.md`**：操作协议（Phase 0-5 步骤、派发模板、反模式）——行为细节的唯一事实。
- **`deepwiki/`**：wiki 产出物与各模块 PLAN.md 契约——生成结果，不是设计。
- **`docs-for-ai/`**：平台使用文档——deepwiki 产出永不混入（定位互补不互替，见 00-overview §non-goals）。

## 演进记录

- 2026-09-25：skill 创建（v1：五阶段管线 + verify-claims 雏形）。
- 2026-09-26：v3 内容质量升级（概念章规划/密度模板）+ v4 形态自适应（六分法/legacy 预处理）。
- 2026-09-29：链接基准升级（git origin → blob 永久链接）+ nop-orm 10 页落地 + Sources 纯文本死链门禁修复；同日对标复验确立密度差距（代码块/mermaid）。
- 2026-09-30：外部工具链机制盘点（8 项目源码级 + 269 仓扫描）收束为本目录 01 吸收设计；P0 实施载体为 `ai-dev/plans/367-nop-deepwiki-survey-absorption-and-density-upgrade.md`（active）。
- 2026-09-30：工具依赖收敛裁定——mermaid/jsdom 并入 `ai-dev/tools/` 既有 pnpm 根（该目录为仓库唯一 pnpm 根）；禁止引用项目外工具脚本路径，唯一例外为 mission driver 使用 AGE 模板。
- 2026-09-30：二轮对标吸收（deepwiki.com 首页 + vite/fastapi 渲染页一手样本）——六项模板层决策落入 01（图题/推断标注/编号目录/API 面表/版本行/来源广度），同日落地 SKILL 模板与脚本；产品形态项维持 Deferred。
