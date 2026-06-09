# Execute Plan Procedure

执行模块 {module} 的活跃计划，**完整执行整个计划直到结束**。

## 执行步骤

1. 运行 node ai-dev/tools/check-plan-status.mjs 获取活跃计划列表
2. 选择 Active 列表中的第一个计划
3. 读取计划文件，跳过已标记 [x] 的 Phase，按顺序执行所有 [ ] Phase
4. 每完成一个 Phase：
   a. 运行 ./mvnw test -pl {module} -am -T 1C 确认测试通过
   b. 将该 Phase 在计划文件中标记为 [x]
   c. 用 nop-git-master skill 提交代码（commit message 包含工作项编号）
5. 所有 Phase 完成后：
   a. 将计划的 Plan Status 更新为 completed
   b. 读取计划中的工作项编号，在路线图文件（ai-dev/design/*{module}*/*roadmap*.md）中将该工作项从 ❌ 改为 ✅

如果执行中途中断或失败也没关系——计划自身记录了进度（[x]/[ ]），下次重新执行时会从断点继续。
不要试图节省步骤，完整执行每一个未完成的 Phase。

## 输出格式

```
<EXECUTE_RESULT>success</EXECUTE_RESULT>
```
或
```
<EXECUTE_RESULT>failed</EXECUTE_RESULT>
```
