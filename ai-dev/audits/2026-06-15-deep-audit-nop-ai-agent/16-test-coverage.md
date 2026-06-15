# 维度 16：测试覆盖与质量

**目标模块**: nop-ai-agent
**深挖轮次**: 1（初审充分覆盖）

## 第 1 轮（初审）

### [维度16-01] ReAct 循环中 performCompaction 抛错路径完全无测试覆盖

- **文件**: `engine/ReActAgentExecutor.java:577, 1241-1245, 1271-1345`; 测试缺失
- **证据片段**:
  ```java
  // 行 577 主循环触发
  if (shouldTriggerCompaction(ctx)) { performCompaction(ctx, agentName, checkpointSeq); }
  // 行 1240-1245 forced-stop 容错路径吞错
  try { performCompaction(ctx, agentName, checkpointSeq); }
  catch (Exception e) { LOG.warn("Final-summary compaction ... failed, continuing...", sessionId, e); }
  ```
- **严重程度**: P2
- **现状**: 所有 compaction 测试用的 IContextCompactor 都正常返回。主循环 performCompaction 抛错（行 577）被外层 catch（行 1089）置 failed，与 forced-stop 吞错路径（行 1241）都未测。
- **风险**: 重构 performCompaction（改日志降级/catch 改 throw/行 577 包进 try）会让主循环失败语义无声漂移；forced-stop 容错路径无回归保护。
- **建议**: 注入抛异常的 IContextCompactor；主循环用例断言 status==failed；forced-stop 用例断言 status==forced_stopped。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度16-02] toolManager.callTool 抛异常（hard exception）路径无 ReAct 循环测试

- **文件**: `engine/ReActAgentExecutor.java:942-948, 1089-1100`; `test/.../TestReActAgentExecutor.java:252-303`
- **证据片段**: `.join()` 任一 future 抛错 → CompletionException → catch 置 failed。现有 testToolExecutionError 仅测软错误 errorResult。
- **严重程度**: P2
- **现状**: 生产环境 callTool 可能抛 hard exception（DB 锁超时/网络中断/NopAiAgentException）。一坏俱坏语义（4 并行之一失败终止整个循环）无测试守护。
- **建议**: 注入 failedFuture(new NopAiAgentException) 用例；多工具并行用例标记"documents current allOf-join fail-fast behavior"。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度16-03] DBDenialLedger 同会话并发 recordDenial 完全无测试（仅测跨会话并发）

- **文件**: `security/DBDenialLedger.java:110-158`; `test/.../TestDBDenialLedger.java:248-298`
- **证据片段**: 现有并发测试每线程独立 sid（跨会话隔离），真实竞态（同 sid 并发）从未触发。
- **严重程度**: P2
- **现状**: 源码注释声称"thread safety guaranteed by DB operations"但 INSERT+COUNT 非原子。已知竞态无回归测试守护。
- **建议**: 单 sid threshold=10，20 线程并发各 recordDenial 一次，断言 count==20 且 exceeded=true 个数合理。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度16-04] DB* 类完全无连接失败/SQL 异常路径测试

- **文件**: 4 个 DB* 类的 catch(SQLException)→NopAiAgentException 包装分支
- **证据片段**: 所有 DB* 测试用健康 H2+SimpleDataSource；grep 运行时连接失败测试零命中。
- **严重程度**: P2
- **现状**: catch(SQLException) 包装分支未覆盖——audit brief 要求检查的"DB 连接失败"路径。
- **建议**: 用 MockDataSource 注入运行时 SQLException，覆盖 getConnection/executeUpdate/executeQuery 失败。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度16-05] CallAgentExecutor Javadoc 承诺有 timeout 测试，但实际未实现

- **文件**: `test/.../TestCallAgentExecutor.java:54`; `tool/CallAgentExecutor.java`
- **证据片段**: Javadoc 列"Timeout → result reflects timeout"，但 9 个 @Test 方法无一调用 timeout 路径。
- **严重程度**: P3
- **现状**: Javadoc 与实际测试集合不一致，误导维护者。timeout 行为无回归保护。
- **建议**: 实现 timeout 测试，或删除 Javadoc 第 7 条。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度16-06] resumeSession/restoreSession 未断言"不追加新用户消息"（透明续接契约）

- **文件**: `engine/DefaultAgentEngine.java:739-744, 858-862`; `test/.../TestResumeSession.java:317-350`
- **证据片段**: 源码注释明确"NO new user message is appended"为 resume 与 doExecute 的核心语义差异，但无测试断言。
- **严重程度**: P3
- **现状**: 一旦未来重构在 resume/restore 追加用户消息，LLM 会把每次 resume 当新对话，审计语义彻底变化，但无测试可发现。
- **建议**: resume 前后断言 ChatUserMessage 个数相等。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度16-07] 14 个 dispatch-path 测试各自重新声明 IChatService 内部类，setup 重复度高

- **文件**: 14 个测试文件（TestReActAgentExecutor/TestDispatchPath*/TestRestoreSession 等）
- **证据片段**: 每个文件都有 class XxxChatService implements IChatService，相同 3 方法接口+callStream 样板；IToolManager stub 同型重复；containsMessage 助手拷贝 3 份。
- **严重程度**: P3
- **现状**: ~1500-2000 行重复测试脚手架。IChatService 接口签名变化需改 14 处。
- **建议**: 抽取 test-support 公共类（scriptedChatService/stubToolManager/containsToolResponse）。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度16-08] mode 分发分支半数未覆盖（plan/unknown）；single-turn 与 react 失败语义可比性无测试

- **文件**: `engine/DefaultAgentEngine.java:1164-1170`; `test/.../TestSingleTurnExecutor.java:199-216`
- **证据片段**: resolveExecutor 4 条分支仅 react/single-turn 有测试；mode=plan 抛 UOE 和 mode=unknown 抛 NopAiAgentException 都未测。
- **严重程度**: P3
- **现状**: mode 切换的失败语义一致性无回归保护；plan 的 UOE fail-fast 无测试。
- **建议**: 新增 resolveExecutor 分发测试覆盖全部 4 条分支。
- **信心水平**: 中
- **复核状态**: 已保留

## 零发现项说明（高价值区域已充分覆盖）

- 6 条 deny 路径全部有专属 dispatch-path 测试 ✅
- checkpoint 一致性警告已测 ✅
- 跨实例 DB 持久化已测 ✅
- 父权限约束三层（A→B→C）端到端已测 ✅
- Hook 重入限制已测 ✅
- Completion-judge 死循环保护已测 ✅

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 16-01 | P2 | ReActAgentExecutor.java:577 | performCompaction 抛错路径无测试 |
| 16-02 | P2 | ReActAgentExecutor.java:942-948 | callTool hard exception 路径无测试 |
| 16-03 | P2 | DBDenialLedger.java | 同会话并发竞态无测试 |
| 16-04 | P2 | 4 个 DB* 类 | 连接失败/SQL 异常路径无测试 |
| 16-05 | P3 | TestCallAgentExecutor.java:54 | Javadoc 承诺 timeout 测试未实现 |
| 16-06 | P3 | TestResumeSession.java | 未断言 resume 不追加 user msg |
| 16-07 | P3 | 14 个 dispatch-path 测试 | IChatService stub 重复 ~1500 行 |
| 16-08 | P3 | DefaultAgentEngine.java:1164-1170 | mode 分发分支半数未覆盖 |
