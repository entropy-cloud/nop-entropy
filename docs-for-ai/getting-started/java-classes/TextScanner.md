# TextScanner 使用指南

TextScanner 是 Nop 平台的文本解析工具类，用于处理复杂文本解析场景，尤其是正则表达式和 StringHelper 无法直接满足需求时。

## 适用场景

- 自定义语法解析器
- 复杂文本格式解析
- 需要精确位置跟踪的解析任务
- 性能敏感的流式解析

## 核心概念

### 1. 创建 Scanner

```java
// 从字符串创建
TextScanner sc = TextScanner.fromString(SourceLocation.fromPath("file.txt"), text);

// 从 Reader 创建（适合大文件）
TextScanner sc = TextScanner.fromReader(loc, reader);
```

### 2. 当前字符位置

```java
// 当前字符（-1 表示结束）
int c = sc.cur;

// 当前行列号（从 1 开始）
int line = sc.line;
int col = sc.col;

// 当前字符位置（从 0 开始）
int pos = sc.pos;

// 是否已结束
boolean end = sc.isEnd();
```

### 3. 基本读取操作

```java
// 读取下一个字符
sc.next();

// 读取下一行
String line = sc.nextLine().toString();

// 跳过空白字符
sc.skipBlank();

// 跳过行内空白
sc.skipBlankInLine();
```

### 4. 匹配操作（推荐）

```java
// 尝试匹配字符串（匹配成功则跳过，失败则位置不变）
if (sc.tryMatch("begin")) {
    // 匹配成功
}

// 尝试匹配整行（适合匹配行首标记）
if (sc.tryMatchLine("---")) {
    // 匹配整行
}

// 匹配字符串（必须匹配，否则抛异常）
sc.match("(");
```

### 5. 智能读取

```java
// 读取直到遇到指定字符
String text = sc.nextUntil(',', true).toString();

// 读取直到遇到指定字符串
String content = sc.nextUntil("---", false).toString();
// allowEnd=false: 文档结束时报错
// allowEnd=true: 文档结束时返回已读内容

// 读取数字
Number num = sc.nextNumber();

// 读取 Java 标识符
String identifier = sc.nextJavaVar();
```

### 6. 查看而不移动

```java
// 查看下一个字符
int nextChar = sc.peek();

// 查看第 n 个字符
int nthChar = sc.peek(n);

// 检查是否以指定字符串开头
if (sc.startsWith("prefix")) {
    // ...
}
```

### 7. 跳过操作

```java
// 跳过直到遇到指定字符串
sc.skipUntil("end");

// 跳过一行
sc.skipLine();

// 跳过多个指定字符
sc.skipChars(" \t");
```

## 典型使用模式

### 模式 1: 解析简单结构

```java
public MyStruct parseStruct(TextScanner sc) {
    sc.skipBlank();
    sc.match("{");
    sc.skipBlank();

    String name = sc.nextWord();
    sc.skipBlank();

    sc.match(",");
    sc.skipBlank();

    int value = sc.nextNumber().intValue();
    sc.skipBlank();

    sc.match("}");
    return new MyStruct(name, value);
}
```

### 模式 2: 解析重复项

```java
public List<String> parseList(TextScanner sc) {
    List<String> items = new ArrayList<>();
    sc.match("[");

    while (!sc.tryMatch("]")) {
        sc.skipBlank();
        String item = sc.nextUntil(',', true).toString();
        items.add(item.trim());
        sc.skipBlank();
    }

    return items;
}
```

### 模式 3: 条件分支解析

```java
public Token parseToken(TextScanner sc) {
    if (sc.tryMatch("if")) {
        return parseIfStatement(sc);
    } else if (sc.tryMatch("while")) {
        return parseWhileLoop(sc);
    } else if (sc.tryMatchLine("---")) {
        return parseFrontMatter(sc);
    } else {
        return parseExpression(sc);
    }
}
```

### 模式 4: 解析带分隔符的内容

```java
public String parseQuotedString(TextScanner sc) {
    sc.match('"');
    return sc.nextUntil('"', false).toString();
}

public String parseDelimitedBlock(TextScanner sc, String start, String end) {
    sc.match(start);
    String content = sc.nextUntil(end, false).toString();
    sc.skipLine(); // 跳过结束标记
    return content;
}
```

## 最佳实践

### 1. 优先使用 tryMatch 而非手动检查

```java
// ❌ 不推荐
if (sc.cur == '<' && sc.peek(1) == '!' && sc.peek(2) == '-') {
    // ...
}

// ✅ 推荐
if (sc.tryMatch("<!--")) {
    // ...
}
```

### 2. 使用 tryMatchLine 处理行标记

```java
// ✅ 适合解析行首标记（如 ---, #, 等）
if (sc.tryMatchLine("---")) {
    // 解析 YAML Front Matter
}
```

### 3. 合理使用 nextUntil

```java
// 读取到标记但不包含标记
String content = sc.nextUntil("---", false).toString();
sc.skipLine(); // 跳过标记行

// allowEnd 参数：
// false = 文档结束时报错（严格模式）
// true = 文档结束时返回已读内容（宽松模式）
```

### 4. 及时跳过空白

```java
// 在读取数据前跳过空白
sc.skipBlank();
String text = sc.nextUntil(',', true).toString();

// 数据处理后再跳过空白
sc.skipBlank();
```

### 5. 使用 location() 记录错误位置

```java
SourceLocation loc = sc.location();
String content = sc.nextUntil("end", false).toString();
if (content.isEmpty()) {
    throw new NopException(ERR_INVALID_CONTENT)
        .source(loc)
        .param(ARG_CONTENT, content);
}
```

## 错误处理

```java
// 方法 1: 使用 match() 自动抛异常
sc.match("expected"); // 不匹配时抛出 NopException

// 方法 2: 使用 tryMatch() 后手动抛异常
if (!sc.tryMatch("optional")) {
    throw sc.newUnexpectedError()
        .param(ARG_EXPECTED, "optional");
}

// 方法 3: 使用 nextUntil 的 allowEnd 参数
String content = sc.nextUntil("end", false).toString();
// 如果文档结束且未找到 "end"，会自动抛异常
```

## 高级技巧

### 1. 组合操作

```java
// 先检查，再匹配，最后读取
if (sc.tryMatch("key") && sc.tryMatch(":")) {
    sc.skipBlank();
    String value = sc.nextUntil('\n', true).toString();
    return new Entry(key, value);
}
```

### 2. 使用 peek 预读

```java
// 检查下一个字符而不移动
if (sc.cur == '"' && sc.peek(1) == '"') {
    // 处理空字符串 ""
    sc.next(); // 跳过第一个 "
    sc.next(); // 跳过第二个 "
}
```

### 3. 回溯（记录位置后恢复）

```java
int savedPos = sc.pos;
try {
    // 尝试解析复杂结构
    return parseComplex(sc);
} catch (NopException e) {
    // 回退位置，尝试其他解析方式
    sc.pos = savedPos;
    return parseAlternative(sc);
}
```

## 实际案例参考

- `MarkdownTableParser.java` - 表格解析
- `MarkdownDocumentParser.java` - Markdown 文档解析
- `JsonParser.java` - JSON 解析
- `XPathSelectorParser.java` - XPath 选择器解析
- `XNodeParser.java` - XML 解析

## 性能考虑

1. **避免频繁创建临时字符串**：使用 `MutableString` 或直接操作 `sc.pos`
2. **批量读取**：使用 `nextUntil()` 而非逐字符 `next()`
3. **预读优化**：使用 `tryMatch()` 或 `peek()` 减少回溯
4. **流式处理**：对大文件使用 `fromReader()` 而非 `fromString()`

## 注意事项

1. `line` 和 `col` 从 **1** 开始
2. `pos` 从 **0** 开始
3. `next()` 会移动位置，`peek()` 不会
4. 匹配失败时 `tryMatch()` 不会移动位置
5. 使用 `isEnd()` 检查结束，避免读取越界
6. 某些方法（如 `nextNumber()`）会跳过前导空白
