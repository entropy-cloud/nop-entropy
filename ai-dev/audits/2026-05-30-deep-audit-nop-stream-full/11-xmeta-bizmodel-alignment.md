# 维度 11：模型与接口对齐（pattern.xdef vs CepPatternBuilder）

## 第 1 轮（初审）

### 检查范围

对比 pattern.xdef 中所有字段与 CepPatternBuilder 中的实际使用。

### 结论：无发现——完全对齐

**CepPatternModel（根）**: start, within, gapWithin, afterMatchSkipStrategy, afterMatchSkipTo, parts — 全部在 Builder 中消费。

**CepPatternPartModel**: name, oneOrMore, timesOrMore, consecutive, allowCombinations, optional, times, windowTime, greedy, subType, next, followKind — 全部对齐。

**CepPatternSingleModel**: type, where, until — 全部对齐。

**CepPatternGroupModel**: type, start, afterMatchSkipStrategy, afterMatchSkipTo, parts — 全部对齐。

**枚举值三方一致性**: FollowKind（5 值）和 AfterMatchSkipStrategyKind（5 值）在 xdef/enum/builder/策略类四层之间完全一致。
