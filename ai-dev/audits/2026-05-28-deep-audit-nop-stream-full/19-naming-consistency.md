# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] Method name typo: setNewStartPartiailMatch

- **文件**: `nop-stream-cep/.../nfa/NFAState.java:111`
- **严重程度**: P2
- **现状**: 方法名 setNewStartParti**ai**lMatch 应为 setNewStartParti**a**lMatch。对应的 getter isNewStartPartialMatch 和 resetter resetNewStartPartialMatch 拼写正确。
- **风险**: 拼写错误降低可读性，破坏与同级方法的命名一致性。
- **建议**: 重命名为 setNewStartPartialMatch()，更新 NFA.java 中的调用点。
- **误报排除**: 不是风格偏好，是确定的拼写错误。
- **复核状态**: 未复核

### [维度19-02] FollowKind enum uses camelCase instead of UPPER_SNAKE_CASE

- **文件**: `nop-stream-cep/.../model/FollowKind.java:24-48`
- **严重程度**: P3
- **现状**: 枚举值使用 camelCase（next, followedBy, followedByAny），与其他18个枚举的 UPPER_SNAKE_CASE 不一致。
- **建议**: 这是为 XDSL 可读性的有意偏离（pattern.xdef 中 followKind="followedBy" 比 "FOLLOWED_BY" 更可读）。建议在 Javadoc 中记录此有意偏离。
- **误报排除**: XDSL 模型枚举的 camelCase 是有意的设计选择。
- **复核状态**: 未复核
