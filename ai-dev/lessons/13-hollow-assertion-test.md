# 13: 空洞断言测试 — 只测"不抛异常"的回归测试无法捕获回归

> Date: 2026-08-05
> Severity: Medium — nop-metadata P1-MA4-401：judgeByRuleId 的"回归测试"用不存在的 ruleId 跑一遍，只断言不抛异常，核心逻辑改动完全不被捕获

## 场景

nop-metadata 测试 `TestNopMetaQualityRuleBizModel` 中 judgeByRuleId 的测试：

```java
// R2.3 修复前的空洞测试
@Test
void testJudgeByRuleId() {
    // 用不存在的 ruleId 调用 —— 任何实现都"成功"（找不到规则直接返回或抛错）
    var result = bizModel.judgeByRuleId("non-exist-rule");
    assertNotNull(result);   // 只断言非 null，不断言语义
}
```

缺陷：测试输入是**不存在的 ruleId**（happy path 从未真正执行），断言只有 `assertNotNull`。结果：

1. judgeByRuleId 的核心逻辑（真实 ruleId → status 语义）从未被验证
2. 核心逻辑被改坏（如状态迁移错误、返回值错）时测试**仍然通过**
3. 测试存在 = "看起来有回归保护"，实际零保护力——比没有测试更有害（给审计者假安全感）

R2.3 修复：真实 ruleId → 断言 status 语义 + 非存在 ruleId → 断言错误码。

## 根因

1. **测试输入选错**：用了不存在的实体 ID，被测代码路径（正常分支）根本没执行——相当于在测 catch/异常分支。
2. **断言只写"不抛异常/非 null"**：assertNotNull 无法表达预期行为，任何返回非 null 的实现都通过。
3. **"补测试"变成"补存在感"**：为满足"每个方法有测试"的覆盖率目标，生成无行为断言的测试，覆盖数字上去了、保护力没有。

## 正确做法

1. **测试必须用真实存在的输入**：正路径测试的输入必须能触发目标分支（真实 ruleId → 真实规则执行）；用不存在的 ID 测的是"缺失路径"，不是功能。
2. **断言行为语义而非存在性**：真实 ruleId → 断言返回 status 符合规则判定；非存在 ruleId → 断言错误码。正路径 + 负路径各一条行为断言。
3. **有效性自检（Mutation 测试思想）**：写完测试后反问——"把核心逻辑改成错误实现，这个测试会失败吗？"；不会失败 = 空洞测试，必须重写（R2.3 判定标准）。

## 判定规则

> **回归测试的有效性判定：把被测逻辑改为错误实现，测试必须失败。** 只断言 `assertNotNull` / `不抛异常` / `instanceof` 的测试，无法捕获行为回归，按空洞测试处理，必须重写为行为断言。
>
> 测试输入必须命中被测分支：用真实 ID 测正路径、用缺失 ID 测错误码路径，二者都断言语义结果。

## 适用范围

- 所有"补测试"任务（R2.3 类）
- 单元测试审计（与 `unit-test-antipatterns.md` P-5/P-8 配套）
- 回归保护有效性评估

## 参考

- `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaQualityRuleBizModel.java`（R2.3 重写后行为断言）
- 审计：`ai-dev/audits/arm-index-nop-metadata.md`（P1-MA4-401）
- 修复：roadmap R2.3（空洞测试 → 行为断言）
- 相关教训：07（零测试模块不可见）、08（校验函数存在≠接线）
