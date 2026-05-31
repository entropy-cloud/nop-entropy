# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] ShardPrefixedKey 同名类重复定义，公共版为死代码

- **文件**: `nop-stream-core/.../state/backend/memory/ShardPrefixedKey.java` (41行) + `nop-stream-core/.../state/shard/ShardPrefixedKey.java` (52行)
- **严重程度**: P2
- **现状**: 两个同名但不同实现的类。shard 包版本是 public 但全模块零引用（死代码），memory 包版本是 package-private 活跃使用。
- **建议**: 删除 shard.ShardPrefixedKey 或合并。
- **信心水平**: 确定
- **误报排除**: grep 确认 shard 版本无 import。
- **复核状态**: 未复核

### [维度19-02] TimerService 跨模块同名，core 版已废弃

- **文件**: `nop-stream-core/.../time/TimerService.java` (@Deprecated) + `nop-stream-cep/.../time/TimerService.java`
- **严重程度**: P3
- **现状**: core 版已 @Deprecated，CEP 版是极简接口且 Javadoc 引用了已废弃版本。
- **建议**: CEP 版重命名为 CepTimerService。
- **信心水平**: 确定
- **误报排除**: 两者功能不同。
- **复核状态**: 未复核

### [维度19-03] nop-stream-flow 模块文档/README/代码三处描述不一致

- **文件**: `docs-for-ai/01-repo-map/module-groups.md:21` vs `nop-stream/README.md:21` vs core/execution/flow/ 包
- **严重程度**: P2
- **现状**: docs-for-ai 说"流控"，README 说"XDSL 声明式流编排"，而实际流控代码已在 core 中。
- **建议**: 统一 nop-stream-flow 的描述。
- **信心水平**: 确定
- **误报排除**: 已验证 core 中的 flow 包包含活跃使用类。
- **复核状态**: 未复核

### [维度19-04] StreamException 命名违反 Java 异常命名惯例

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/exceptions/StreamException.java`
- **严重程度**: P3
- **现状**: 以 Exception 结尾但实际是非受检异常（extends RuntimeException）。
- **建议**: 在 Javadoc 中标注 "unchecked exception"。
- **信心水平**: 很可能
- **误报排除**: 不影响运行时。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 19-01 | P2 | ShardPrefixedKey.java | 同名重复，公共版为死代码 |
| 19-02 | P3 | TimerService.java | 跨模块同名，CEP 版引用废弃版 |
| 19-03 | P2 | module-groups.md + README.md | flow 模块三处描述不一致 |
| 19-04 | P3 | StreamException.java | 命名违反受检异常惯例 |
