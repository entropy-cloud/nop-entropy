# 维度 21：单元测试有效性审查

## 通过检查

- 无 P-1 至 P-4 反模式 ✓
- 测试命名整体良好 ✓
- 无隐式测试依赖 ✓

## 发现

### [21-01] P3 — TestDefaultJobTaskBuilder.testBuildWithNullSnapshots 断言过于宽松

- **文件**: TestDefaultJobTaskBuilder.testBuildWithNullSnapshots
- **现状**: 测试对默认为空 map 的返回值仅使用 `assertNotNull` 断言，而实际上该方法在 snapshots 为 null 时总是返回非空 map（空 HashMap）。这属于 P-5 反模式——断言可以无条件通过。
- **建议**: 增加更有意义的断言，如验证 map 为空或包含预期的默认值。

### [21-02] P3 — TestTrigger.testPeriod 包含无价值断言

- **文件**: TestTrigger.testPeriod
- **现状**: 测试中包含 `assertTrue(beginTime > 0)` 断言，该断言永远为真（因为 beginTime 是 System.currentTimeMillis() 的返回值），无法独立检测任何缺陷。属于 P-5 反模式。
- **建议**: 替换为有意义的断言，如验证 beginTime 在合理的时间范围内。
