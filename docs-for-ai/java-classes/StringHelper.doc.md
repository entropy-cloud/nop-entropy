# StringHelper 使用指南

## 概述

StringHelper是Nop平台提供的**字符串处理工具类**，包含全面的字符串操作方法，用于统一处理各种字符串相关操作，替代第三方字符串工具库。StringHelper设计简洁易用，提供了丰富的字符串操作API，涵盖字符串转义、格式化、命名转换、UUID生成和编码转换等功能，是Nop平台中处理字符串数据的核心组件。

## 核心功能

### 1. 字符串检查
- `isEmpty(str)`：检查字符串是否为空或null
- `isBlank(str)`：检查字符串是否为空白、空或null
- `isNotBlank(str)`：检查字符串是否不为空白
- `isNotEmpty(str)`：检查字符串是否不为空
- `isAllZero(str)`：检查字符串是否全为零
- `isAllDigit(str)`：检查字符串是否全为数字
- `isNumber(str)`：检查字符串是否为数字（包含整数和浮点数）

### 2. 字符串转义
- `escapeHtml(str)`：HTML特殊字符转义，如 < 转为 &lt;
- `escapeXml(str)`：XML特殊字符转义，如 < 转为 &lt;
  - `unescapeXml(str)`：XML特殊字符反转义
- `escapeJson(str)`：JSON字符串转义，处理引号和反斜杠等
  - `unescapeJson(str)`：JSON字符串反转义
- `escapeSql(str, escapeSlash)`：SQL字符串转义，防止SQL注入
- `escapeJava(str)`：按照Java规范执行字符串转义
  - `unescapeJava(str)`：Java字符串反转义
- `escapeMarkdown(str)`：按照commonmark规范执行Markdown特殊字符转义
  - `unescapeMarkdown(str)`：Markdown特殊字符反转义
- `escapeUnicode(str)`：按照java规范执行Unicode字符串转义
- `escapeYaml(str)`：YAML字符串转义，处理YAML中的特殊字符

### 3. 字符串引用
- `quote(str)`：将字符串用双引号括起来，并对内部的特殊字符进行Java转义
- `unquote(str)`：移除字符串两端的引号，并对内部的转义字符进行反转义
- `quoteDupEscapeString(str, quoteChar)`：为字符串前后增加指定的引用字符，并对字符串内部的引用字符进行重复转义
- `unquoteDupEscapeString(str)`：移除字符串前后的引用字符，并对内部重复的引用字符进行反转义


### 4. 字符串格式化
- `padLeft(str, width, padChar)`：左填充，不足width长度时用padChar填充
- `padRight(str, width, padChar)`：右填充，不足width长度时用padChar填充
- `repeat(str, count)`：重复字符串count次
- `limitLength(str, maxLen, suffix)`：限制字符串长度，超过时添加suffix后缀
- `fileSizeString(len)`：将文件大小转换为人类可读的字符串（如1.2K, 3.5M）

### 5. 字符串处理
- `strip(str)`：去除字符串两端的空白字符，如果最后为空字符串，则返回null
- `join(iterable, sep)`：将集合元素连接成字符串，用sep分隔
- `join(iterable, sep, ignoreEmpty)`：将集合元素连接成字符串，用sep分隔，忽略空元素
- `split(str, sepChar)`：按单个字符分割字符串，返回`List<String>`
- `splitBy(str, sep)`：按字符串分割字符串,返回`List<String>`
- `stripedSplit(str, sepChar,includeNull)`：按单个字符分割，调用strip每个元素的空白。如果includeNull为false，则移除所有空元素和null。
- `replace(str, oldSub, newSub)`：替换字符串中的子串
- `countChar(str, c)`：统计字符在字符串中出现的次数
- `removeHtmlTag(str)`：移除HTML标签，保留文本内容

### 6. 命名转换
- `camelCaseToUnderscore(str, toLower)`：驼峰命名转下划线命名，toLower控制是否转为小写
- `camelCaseToHyphen(str)`：驼峰命名转连字符命名
- `underscoreToCamelCase(str, firstUpper)`：下划线命名转驼峰命名，firstUpper控制首字母是否大写
- `capitalize(str)`：字符串首字母大写
- `decapitalize(str)`：字符串首字母小写

### 7. UUID生成
- `generateUUID()`：返回长度32位的UUID，对应于StringHelper.replace(UUID.randomUUID(),"-","")
- `randomString(length)`：生成指定长度的随机字符串
- `randomDigits(length)`：生成指定长度的随机数字字符串

### 8. 编码转换
- `encodeBase64(bytes)`：字符串Base64编码
- `decodeBase64(str)`：Base64编码字符串解码
- `encodeBase64Url(bytes)`：字符串Base64 Url编码
- `decodeBase64Url(str)`：Base64 Url编码字符串解码
- `bytesToHex(bytes,upper)`：将字节数组转换为十六进制字符串，upper控制是否转为大写
- `hexToBytes(str)`：将十六进制字符串转换为字节数组
- `intToHex(value, minLength)`：将int转换为十六进制字符串，指定最小长度，不足时补零
- `longToHex(value, minLength)`：将long转换为十六进制字符串，指定最小长度，不足时补零
- `encodeURL(str)`：URL编码, 采用UTF8字符集
- `decodeURL(str)`：URL解码, 采用UTF8字符集

### 9. 模板渲染
- `renderTemplate(str, transformer)`：渲染模板字符串，变量占位符采用`{key}`格式，使用`Function<String,String>`类型的transformer替换变量
- `renderTemplateForScope(str, placeholderStart,placeholderEnd, scope)`：使用Map的变量渲染模板，需要指定占位符的开始和结束字符串

### 10. URL处理
- `appendQuery(url, query)`：给URL添加查询参数
- `encodeQuery(map)`：将Map转换为URL查询字符串, 采用UTF8字符集
- `parseQuery(str)`: 解析UTR查询字符串为`Map<String,Object>`

### 11. 字符串解析
- `parseStringMap(str)`：解析字符串为Map， 采用`key1=value1,key2=value2`或者`key1=value1\nkey2=value2`的格式
- `encodeStringMap(map,itemSepChar)`：将Map转换为字符串, 通过itemSepChar指定使用条目之间的分隔字符

### 12. 字符串比较
- `startsWithIgnoreCase(str, subStr)`：忽略大小写检查字符串是否以指定子串开头
- `endsWithIgnoreCase(str, subStr)`：忽略大小写检查字符串是否以指定子串结尾
- `indexOfIgnoreCase(str, subStr)`：忽略大小写查找子串位置

### 13. 字符串验证
- `isValidClassName(str)`：验证字符串是否为有效的Java类名
- `isValidJavaVarName(str)`：验证字符串是否为有效的Java变量名
- `isValidSimpleVarName(str)`：验证字符串是否为有效的简单变量名（不含$符号）
- `isValidPropName(str)`：验证字符串是否为有效的属性名
- `isValidPropPath(str)`：验证字符串是否为有效的属性路径, 例如a.b.c
- `isValidHtmlAttrName(str)`：验证字符串是否为有效的HTML属性名
- `isValidXmlName(str)`：验证字符串是否为有效的XML名称
- `isValidXmlNamespaceName(str)`：验证字符串是否为有效的XML命名空间名称

### 14. 文件路径处理
- `safeFileName(str)`：将文件名转换为安全的文件名，处理非法字符
- `fileName(path)`：获取文件的完整名称（包括扩展名）
- `fileNameNoExt(path)`：获取文件的名称，不包括扩展名
- `fileExt(path)`：获取文件的扩展名（不包括点）
- `fileType(path)`：获取文件类型，查找最后两个点，例如a.orm.xml返回orm.xml
- `removeFileExt(path)`：移除文件的扩展名
- `replaceFileExt(path, fileExt)`：替换文件的扩展名
- `appendPath(path, relativePath)`：将相对路径追加到基础路径
- `normalizePath(path)`：标准化路径，处理../和./等
- `removeFileType(path)`：移除文件类型，例如a.orm.xml返回a
- `replaceFileType(path, fileType)`：设置文件类型，例如a.xml设置为orm.xml, 返回a.orm.xml

## 示例代码

```java
import io.nop.commons.util.StringHelper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// 字符串检查
boolean empty = StringHelper.isEmpty(""); // true
boolean blank = StringHelper.isBlank("   "); // true
boolean notBlank = StringHelper.isNotBlank("hello"); // true
boolean allDigit = StringHelper.isAllDigit("12345"); // true
boolean allZero = StringHelper.isAllZero("0000"); // true

// HTML转义
String escaped = StringHelper.escapeHtml("<html>"); // &lt;html&gt;
String xmlEscaped = StringHelper.escapeXml("<xml>"); // &lt;xml&gt;
String jsonEscaped = StringHelper.escapeJson("{\"name\": \"test\"}"); // \{\"name\": \"test\"}

// 字符串格式化
String paddedLeft = StringHelper.padLeft("123", 5, '0'); // "00123"
String paddedRight = StringHelper.padRight("123", 5, '0'); // "12300"
String repeated = StringHelper.repeat("abc", 3); // "abcabcabc"
String limited = StringHelper.limitLength("hello world", 8,"..."); // hello...
String fileSize = StringHelper.fileSizeString(1536); // "1.5K"

// 字符串处理
String stripped = StringHelper.strip("  hello  "); // "hello"
String joined = StringHelper.join(Arrays.asList("a", "b", "c"), ","); // "a,b,c"
String[] split = StringHelper.splitToArray("a,b,c", ','); // ["a", "b", "c"]
String replaced = StringHelper.replace("hello world", "world", "nop"); // "hello nop"
int count = StringHelper.countChar("hello", 'l'); // 2
String noHtml = StringHelper.removeHtmlTag("<p>hello<br/>world</p>"); // "hello\nworld"

// 命名转换
String underscore = StringHelper.camelCaseToUnderscore("userName", true); // user_name
String hyphen = StringHelper.camelCaseToHyphen("userName"); // user-name
String camelCase = StringHelper.camelCase("user_name", false); // userName
String capitalized = StringHelper.capitalize("hello"); // Hello
String uncapitalized = StringHelper.uncapitalize("Hello"); // hello

// UUID生成
String uuid = StringHelper.generateUUID(); // 随机UUID字符串 (32位)
String customUuid = StringHelper.generateUUID(10); // 10位UUID字符串

// 编码转换
String base64Encoded = StringHelper.encodeBase64("test".getBytes()); // 编码为Base64
byte[] base64Decoded = StringHelper.decodeBase64(base64Encoded); // 解码Base64
String hexEncoded = StringHelper.bytesToHex("test".getBytes(),true); // 十六进制编码
byte[] hexDecoded = StringHelper.hexToBytes(hexEncoded); // 十六进制解码
String intHex = StringHelper.intToHex(255, 4); // "00FF"
String longHex = StringHelper.longToHex(255L,16); // "00000000000000FF"
String urlEncoded = StringHelper.encodeURL("hello world"); // URL编码
String urlDecoded = StringHelper.decodeURL(urlEncoded); // URL解码

// 模板渲染
Map<String, Object> scope = new HashMap<>();
scope.put("name", "world");
String rendered = StringHelper.renderTemplateForScope("Hello {name}!","{","}",scope); // "Hello world!"

// URL处理
String url = StringHelper.appendQuery("https://example.com", "page=1&sort=asc"); // "https://example.com?page=1&sort=asc"
Map<String, Object> queryMap = new HashMap<>();
queryMap.put("page", 1);
queryMap.put("sort", "asc");
String query = StringHelper.encodeQuery(queryMap); // "page=1&sort=asc"

// 字符串解析
Map<String, String> strMap = StringHelper.parseStringMap("key1=value1,key2=value2"); // {key1=value1, key2=value2}

// 随机字符串生成
String randomStr = StringHelper.randomString(8); // 8位随机字符串
String randomDigits = StringHelper.randomDigits(6); // 6位随机数字

// 字符串比较
boolean startsWith = StringHelper.startsWithIgnoreCase("Hello", "hello"); // true
boolean endsWith = StringHelper.endsWithIgnoreCase("Hello", "LLO"); // true
int index = StringHelper.indexOfIgnoreCase("Hello World", "WORLD"); // 6
```

## 在XLang中作为扩展方法

StringHelper的方法可在Nop平台的XLang表达式语言（XScript/XPL）中作为字符串扩展方法调用：

```xpl
<c:script>
  let x = 'hello'.$capitalize(); // Hello
</c:script>
<c:if test="${name.$indexOfIgnoreCase('hello')}">
</c:if>
```

**映射规则：**
`str.$method(args)` → `StringHelper.method(str, args)`


## 最佳实践

1. **优先使用**：所有字符串操作优先使用StringHelper，避免第三方库
2. **静态导入**：使用静态导入简化代码
3. **选择合适方法**：根据场景选择最优方法
4. **注意性能**：所有方法都经过优化，比正则匹配更高效

## 注意事项

- 所有方法都是静态的，直接调用
- **null安全**：所有方法都是null安全的，str参数可以为null
- 编码安全：所有编码转换都使用UTF-8

## 替代方案

避免使用以下第三方库：
- Apache Commons Lang StringUtils
- Google Guava Strings
- Spring的StringUtils
