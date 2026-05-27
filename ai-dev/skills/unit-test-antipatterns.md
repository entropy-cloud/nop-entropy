# Skill: Unit Test Anti-Patterns & Writing Guidelines

> 用途：编写或审查单元测试时必须遵守的规则。AI 编写测试前应先读此文件。

---

## 核心原则

**测试的价值 = 它能捕获的 bug 数量 × 捕获概率。** 一条测试如果不能区分正确实现和错误实现，就没有价值。

判断标准：**把被测代码的核心逻辑改成错误的，测试应该失败。** 如果改了还不失败，说明测试没在检查任何有意义的东西。

---

## 反模式清单

### P-1. 纯 Getter/Setter 往返测试

```java
// ❌ 无价值：@DataBean 自动生成的 getter/setter，编译器已保证正确
@Test
void testGettersSetters() {
    CodeSemanticEdge edge = new CodeSemanticEdge();
    edge.setId("edge-1");
    edge.setDirected(true);
    assertEquals("edge-1", edge.getId());   // 这就是 set 什么 get 什么
    assertTrue(edge.isDirected());
}

// ❌ 等价的变体：测试枚举值数量
@Test
void testEnumCount() {
    assertEquals(8, SemanticRelationType.values().length);  // 改了枚举就改测试，没有独立验证意义
}

// ❌ assertNotNull 遍历每个枚举成员
@Test
void testAllEnumMembers() {
    assertNotNull(SemanticRelationType.SEMANTICALLY_SIMILAR_TO);
    assertNotNull(SemanticRelationType.CONCEPTUALLY_RELATED_TO);
    // ... 全部列一遍
}
```

**为什么不好：** 这些测试只是在确认 Java 语言的基本语义（赋值后能取回、枚举能编译）。它们无法发现任何业务逻辑 bug。

**什么时候可以写：** 如果 getter/setter 中有校验逻辑（如范围检查、格式校验、派生计算），那么测试那个校验逻辑。否则不写。

**替代做法：**
- 如果模型类有业务约束（如 confidenceScore 必须 ≥ 0），测试那个约束
- 如果模型类有 `fromValue()` 工厂方法，测试边界情况（未知值、null 等）
- 如果是枚举，只在有实际逻辑时测试（如 `fromValue` 的 fallback 行为）

```java
// ✅ 有价值：测试 fromValue 的 fallback 逻辑
@Test
void testEdgeConfidenceFromValueUnknown() {
    assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(99));
    assertEquals(EdgeConfidence.EXTRACTED, EdgeConfidence.fromValue(-1));
}

// ✅ 有价值：测试 @DataBean 的 equals/hashCode 是否正确（如果有自定义）
@Test
void testEdgeEquality() {
    CodeSemanticEdge a = new CodeSemanticEdge();
    a.setId("e1");
    CodeSemanticEdge b = new CodeSemanticEdge();
    b.setId("e1");
    assertEquals(a, b);  // 验证 ID 相同的对象确实相等
}
```

### P-2. 测试元数据属性而非行为

```java
// ❌ 无价值：getExtractorId() 返回一个常量字符串
@Test
void testExtractorId() {
    assertEquals("name-sim", extractor.getExtractorId());
}

// ❌ 无价值：requiresLlm() 返回固定 boolean
@Test
void testDoesNotRequireLlm() {
    assertFalse(extractor.requiresLlm());
}
```

**为什么不好：** 这只是在测试常量赋值。如果有人把 `"name-sim"` 改成 `"name-similarity"`，测试会失败，但这不是 bug —— 测试只是在镜像实现。

**替代做法：** 这些断言应嵌入到真正测试行为的测试方法中作为附带检查，不单独成方法。

```java
// ✅ 在行为测试中顺便验证
@Test
void testExtractsSimilarNames() {
    List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());
    assertFalse(edges.isEmpty());
    CodeSemanticEdge edge = edges.get(0);
    assertEquals("name-sim", edge.getExtractorId());  // 顺便检查，不单独成方法
}
```

### P-3. 只测 Happy Path

```java
// ❌ 只测正常情况
@Test
void testExtract() {
    List<CodeSemanticEdge> edges = extractor.extract(table, cg);
    assertFalse(edges.isEmpty());  // 只看"有结果"
}
```

**缺失的关键测试：**
- 空输入 → 应返回空集合还是抛异常？
- null 输入 → 防御性处理了吗？
- 边界值 → 阈值正好卡在临界点上呢？
- 重复数据 → 是否产生重复边？
- 极端规模 → 符号表超过 MAX_SYMBOLS 时是否截断？

```java
// ✅ 测试边界和异常
@Test
void testThresholdBoundary() {
    // Jaccard 正好等于 0.7 的两个名称
    CodeSymbol sym1 = createSymbol("a.UserService", "UserService", CLASS);
    CodeSymbol sym2 = createSymbol("b.UserService", "UserService", CLASS);
    // Jaccard = 1.0 (完全相同名称，不同 parent)
    // 确认阈值逻辑包含 >= 而不是 >
}

@Test
void testDuplicatePrevention() {
    // 同一对符号调用两次 extract，不应产生重复边
}

@Test
void testSymbolLimit() {
    // 添加 6000 个符号，确认只处理前 5000 个
}
```

### P-4. 测试与实现高度耦合

```java
// ❌ 测试直接引用内部实现常量
@Test
void testThreshold() {
    // 如果阈值从 0.7 改到 0.6，这个测试也要改 → 没有独立价值
    assertEquals(0.7, NameSimilarityExtractor.SIMILARITY_THRESHOLD);
}
```

**为什么不好：** 测试和实现说的是同一件事。改实现时必须同步改测试，说明测试没有独立验证能力。

**替代做法：** 通过输入/输出测试阈值效果，而不是直接断言常量值。

```java
// ✅ 用具体输入验证阈值行为
@Test
void testNamesExactlyAtThresholdAreIncluded() {
    // 选择一对 Jaccard 恰好为阈值的名称
    // 验证它们被包含
}

@Test
void testNamesJustBelowThresholdAreExcluded() {
    // 选择一对 Jaccard 恰好低于阈值的名称
    // 验证它们被排除
}
```

### P-5. 过度使用 assertNotNull

```java
// ❌ 几乎不可能失败
assertNotNull(result);
assertNotNull(result.getItems());
assertNotNull(result.getEdges());
```

**为什么不好：** 在 Java 中 `new ArrayList<>()` 就能让 `assertNotNull` 通过。它不验证任何业务正确性。

**替代做法：** 断言具体的值、数量或内容。

```java
// ✅
assertEquals(3, result.getItems().size());
assertTrue(result.getItems().stream().allMatch(i -> i.getScore() > 0));
```

### P-6. 测试方法名不表达预期行为

```java
// ❌ 看名字不知道在测什么
@Test
void testExtract() { ... }
@Test
void testProcess() { ... }
@Test
void testConstructor() { ... }

// ✅ 名字即文档
@Test
void testUnusedPrivateMethodDetectedAsDead() { ... }
@Test
void testPublicMethodsNeverMarkedDead() { ... }
@Test
void testSameNameDifferentPackagesProducesSimilarityEdge() { ... }
```

### P-7. 测试之间有隐式依赖

```java
// ❌ 测试 B 依赖测试 A 的副作用
static List<CodeSemanticEdge> sharedEdges;  // 共享可变状态

@Test
void testA() {
    sharedEdges = extractor.extract(table, cg);
}

@Test
void testB() {
    // 假设 sharedEdges 已被 testA 填充
    assertFalse(sharedEdges.isEmpty());  // 如果单独运行 testB 会 NPE
}
```

**规则：每个 @Test 方法必须能独立运行并通过。** 用 `@BeforeEach` 或在方法内部自行构造数据。

### P-8. 无效的负面测试

```java
// ❌ 测试空输入但不验证具体行为
@Test
void testEmptyInput() {
    List<CodeSemanticEdge> edges = extractor.extract(new SymbolTable(), new CallGraph());
    assertTrue(edges.isEmpty());  // 空进空出，没测到任何逻辑
}
```

**什么时候空输入测试有价值：** 当空输入可能引发 NPE、无限循环或意外行为时。如果空输入→空输出是显然的，这个测试价值低。

---

## 写测试前的检查清单

写每个 `@Test` 方法前问自己：

| 问题 | 如果"否" |
|------|---------|
| 把核心逻辑改成错的，这个测试会失败吗？ | **删掉它** |
| 这个测试和实现说的是同一件事吗？ | **重写为基于输入/输出的测试** |
| 换一个开发者看这个测试名，能理解预期行为吗？ | **重命名** |
| 单独运行这个测试能通过吗？ | **修复依赖** |
| 这个测试覆盖了一个真实可能的 bug 吗？ | **如果只是"不会出错"的场景，降低优先级** |

## 优先级排序

写测试时按此优先级分配精力：

1. **核心算法/业务逻辑** → 最详细的测试（正常、边界、异常）
2. **集成点**（接口实现、数据转换）→ 正常 + 错误路径
3. **防御性代码**（null 检查、范围校验）→ 触发防御的输入
4. **数据模型**（@DataBean）→ 只在有自定义逻辑时测试
5. **常量/配置** → 基本不测

## 本项目具体规则

1. **@DataBean 模型类**（如 `CodeSemanticEdge`）：不写纯 getter/setter 测试。只在有自定义 equals/hashCode、校验方法、或 `fromValue()` 等 factory 方法时才写测试。
2. **枚举**：不测试成员数量或 `assertNotNull` 每个成员。只在枚举有业务方法时测试。
3. **Extractor/Analyzer**：每个公共方法至少测 3 个场景：正常输入、空/null 输入、边界值。测试应验证输出内容的正确性（具体值、具体关系类型），不仅仅是"不为空"。
4. **测试方法命名**：使用 `testXxxWhenYyyThenZzz` 或 `testXxx_doesYyy` 格式，表达完整的"在什么条件下预期什么结果"。
5. **不要为了覆盖率数字写测试。** 覆盖率是副产品，不是目标。
