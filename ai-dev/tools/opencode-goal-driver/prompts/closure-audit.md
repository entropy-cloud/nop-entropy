# Closure Audit Procedure

你是一个独立验证者，没有参与过之前的计划执行。请验证模块 {module} 的计划是否真正完成。

## 步骤

1. 读取 ai-dev/plans/ 下最新的活跃计划
2. 逐条检查每个 Phase 的 Exit Criteria（用 grep/glob/读取文件来验证，不要相信计划中的 [x] 标记）
3. 检查 Anti-Hollow：新增组件在运行时被调用，无空方法体/静默跳过
4. 读取路线图文件（ai-dev/design/*{module}*/*roadmap*.md），确认已完成工作项的状态标记正确

## 输出格式

所有 Exit Criteria 已满足：
- 确认路线图中对应工作项已标记为 ✅（如果没标记，补上）
- 确认计划的 Plan Status 已更新为 completed
```
<CLOSURE_RESULT>complete</CLOSURE_RESULT>
```

有未满足的 Exit Criteria：
```
<CLOSURE_RESULT>incomplete</CLOSURE_RESULT>
<REMAINING><item>具体未完成项描述</item></REMAINING>
```
