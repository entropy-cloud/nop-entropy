# SequentialThinking 会话存储方案（P3-MA1-013 裁定）

**日期**：2026-08-01
**范围**：`nop-ai/nop-ai-tools` 的 `sequential_thinking` 包（`ThoughtStorage` / `SequentialThinkingBizModel`）
**状态**：active（裁定已落地）
**相关裁定**：P3-MA1-013（MA1.3 审计，第九批承接）、`ai-dev/plans/2026-08-01-0206-2-arm-p2-tools-structure-residual.md` Phase 2

---

## 一、设计结论

**裁定 = 保持文件持久化（不迁移 ORM）**。

1. `ThoughtStorage` 继续使用 JSON 文件持久化（每会话一个 `<sessionId>.json` 文件），单实例 `ReentrantLock` 保护并发。
2. 默认存储路径修正为可写值：`nop.ai.sequential-thinking-tool.storage-dir-path` 默认值由绝对路径 `/nop/ai/sequential-thinking/store` 改为相对路径 `./_tmp/ai/sequential-thinking/store`（相对 JVM 工作目录解析，默认可写）。配置为空时回退 `~/.mcp_sequential_thinking`（用户主目录，行为不变）。
3. 多实例部署限制为文档化扩展点：实例间共享同一路径（网络盘/共享卷）或触发后续迁移（见 §四 迁移触发条件）。

## 二、背景与动机（live 使用面评估）

| 维度 | 事实 | 证据 |
|------|------|------|
| 调用方 | 仅 `SequentialThinkingBizModel`（processThought / generateSummary / clearHistory 三个 action） | 全仓 grep 唯一消费面 |
| 数据量级 | 每会话数十~数百条 thought，每条 ~1KB 文本；每次操作全量读/写该会话文件 | `ThoughtStorage` loadSession/saveSession |
| 并发/事务 | 单 JVM 内 `ReentrantLock` 串行化；无跨进程/跨节点一致性需求 | `ThoughtStorage.lock` |
| 部署形态 | nop-ai-tools 为框架模块（工具库），非独立服务；会话数据是对话辅助记录，非业务主数据 | 模块定位见 `docs-for-ai/01-repo-map/module-groups.md` |
| 默认路径可用性 | `/nop/ai/sequential-thinking/store` 为**文件系统绝对路径**（`FileHelper.resolveFile` 对 `/` 开头路径直接 `new File(path)`），普通用户无 `/nop` 写权限 → 默认配置实际不可写（live defect） | `FileHelper.resolveFile:621-633`；beans.xml 默认值 |

## 三、为什么拒绝 ORM 迁移（audit 原建议）

**拒绝"迁移至 Nop ORM 实体"**：

1. **依赖成本**：nop-ai-tools 无 ORM/DAO 依赖；nop-ai-core 已按 P2-MA3-001 移除 `nop-dao`。迁移 = 新增 nop-ai-dao/nop-orm 依赖 + 建表 + DAO bean + 事务接线，对一个会话级对话辅助工具是重型化。
2. **数据性质**：会话 thought 记录依赖聊天会话上下文（`AiToolsHelper.makeChatSessionId(ctx)`），生命周期与聊天会话一致，属 ephemeral 数据；ORM 迁移收益（跨实体一致性、审计、查询）无从发挥。
3. **并发需求**：单 JVM 串行化已满足；ORM 的事务/锁能力无消费场景。
4. **风险收益比**：P3 严重度 + 审计信心"中"；文件持久化已满足当前全部功能需求，迁移引入模块契约与 DB 依赖变化，收益为 0 的场景不成立。

**拒绝"引入原子写（临时文件+rename）"**：单会话文件 + 单 JVM 锁，崩溃窗口内的半写文件仅影响该会话一次加载（读失败抛错，不损坏其他数据）；收益低于改动成本，不阻塞任何当前使用面。

## 四、使用契约与限制

- **配置键**：`nop.ai.sequential-thinking-tool.storage-dir-path`。默认 `./_tmp/ai/sequential-thinking/store`（相对 CWD）；空值回退 `~/.mcp_sequential_thinking`。
- **路径解析语义**：`FileHelper.resolveFile`——`/` 开头为绝对路径；`./` 开头或相对路径相对 JVM 工作目录解析。
- **会话隔离**：`<sessionId>.json` 文件名由 `AiToolsHelper.makeChatSessionId(ctx)` 生成；恶意会话 id 的路径穿越风险属调用方（BizModel 层）职责，不在本类处理。
- **多实例限制（文档化，非 defect）**：文件持久化是 per-JVM 的；多实例共享同一聊天会话时，会话 thought 会分叉。多实例部署需配置共享路径（网络卷）或迁移 ORM。
- **迁移触发条件**（未来出现任一条件时正式迁移 ORM）：①SequentialThinking 数据需要跨节点共享或审计查询；②数据量级超出单文件读写可接受范围；③nop-ai-tools 模块出现 ORM 依赖的强需求。

## 五、与已有设计的关系

- 上游：P3-MA1-013 审计记录（`ai-dev/audits/2026-07-31-2200-arm-MA1.3-nop-ai-toolkit.md`）。
- 相关：P2-MA3-001（nop-ai-core 移除 nop-dao 依赖，支持"工具模块不引入 ORM 面"的裁定）。
- 追踪：`ai-dev/audits/arm-index.md` §P3 追踪（第九批）P3-MA1-013 行。
