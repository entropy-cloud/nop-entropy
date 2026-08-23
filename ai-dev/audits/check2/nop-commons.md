# nop-commons 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-kernel/nop-commons
- 文件数: 403（src/main/java）
- 覆盖范围声明: 深读约 45 个核心文件（StringHelper、MathHelper、CollectionHelper、DateHelper、IoHelper、FileHelper、ClassHelper、ByteHelper/Bytes.equals 家族、ByteString、MutableString、IntHashMap、LocalCache/MapCache/CacheConfig、LocalResourceLockManager/ResourceLock/ResourceLockState、AESTextCipher、HashHelper、TextScanner（核心区段）、CharArrayReader、ByteQueue、SafeLineReader、BoundedInputStream、UriEncodeHelper、PlatformEnv、AntPathMatcher（缓存区段）、aggregator 全部、Lazy、SequentialTaskExecutor、HighWatermarkSemaphore、DefaultRateLimiter、JavaSerializer、random 包等）；全量模式扫描（grep）覆盖 100% 文件的以下模式：非线程安全日期类、空 catch、`new Random`、Java 原生反序列化/进程执行、`@Inject` private / Spring 注解、System.exit。未深读区域: MarkedStringBuilderT、StringTrie、EditDistance、bit 包（BloomFilter/FixedBitSet/DefaultBitSet）、LittleEndian/FastByteBuffer/ByteBufferHelper 大部分、NetHelper 主体、TextScanner 后 1000 行细节、concurrent/batch 包、tuple/type/diff/functional 多数小文件、metrics 包。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 7 |
| P3 | 12 |

## 发现列表

### [P0] StringHelper.parseQuery 重复参数名时多值收集逻辑完全失效

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java:2377-2388`
- **维度**: D1（逻辑错误/复制粘贴错误）
- **证据**:
```java
parseQuery(query, encoding, (key, value) -> {
    Object v = ret.get(key);
    if (v == null) {
        ret.put(key, value);
    } else if (v instanceof List<?>) {
        ((List<String>) v).add(value);
    } else {
        List<String> list = new ArrayList<>();
        list.add((String) v);
        list.add(value);
        ret.put(key, value);   // 应为 ret.put(key, list)
    }
});
```
- **现状**: 当同一参数名出现第二次时，代码构造了聚合 List，但放回 Map 的是单值 `value` 而不是 `list`。此后每次重复出现都会重新走 else 分支（v 永远不是 List），最终该 key 只保留最后一个值。`a=1&a=2&a=3` 的结果是 `a=3`，而按代码意图应为 `[1,2,3]`。
- **风险**: 数据错误。调用方 `XuiHelper.appendFilterProps`（nop-frontend-support/nop-ui）用它解析用户可控的 dashboard URL query 并重新编码回 URL，URL 中一旦存在重复参数名（Web 场景常见，如 `?id=1&id=2`），多值被静默丢弃、只保留最后一个。
- **建议**: 将 `ret.put(key, value)` 改为 `ret.put(key, list)`，并补充 `a=1&a=2` 的回归测试（现有测试 `TestStringHelper.java:278-280` 只覆盖不重复 key）。
- **误报排除**: 已通读同文件私有方法 `parseQuery(String,String,BiConsumer)`（2409-2447 行）确认回调确实对每个参数对调用一次；已确认 `parseSimpleQuery` 不受影响（重复 key 直接抛异常）；已核对调用方 XuiHelper.java:352 的输入来源为外部 URL。

### [P1] StringHelper.indexOfIgnoreCase off-by-one：漏掉子串位于末尾的匹配

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java:1258-1268`
- **维度**: D1（边界条件）
- **证据**:
```java
public static int indexOfIgnoreCase(String str, String subStr) {
    if (str == null || subStr == null)
        return -1;
    if (str.length() < subStr.length())
        return -1;
    for (int i = 0, n = str.length() - subStr.length(); i < n; i++) {  // 应为 i <= n
        if (str.regionMatches(true, i, subStr, 0, subStr.length()))
            return i;
    }
    return -1;
}
```
- **现状**: 最后一个合法起始位置 `str.length()-subStr.length()` 未被检查。`indexOfIgnoreCase("hello", "LO")` 返回 -1（正确应为 3）。
- **风险**: 调用方 `FilterOpHelper.java:113`（nop-core）用它实现忽略大小写的 contains 判断，当子串恰好出现在末尾时误判为不包含，导致过滤条件判断错误。
- **建议**: 循环条件改为 `i <= n`；补充末尾匹配测试。
- **误报排除**: 已用 `str.regionMatches(pos,...)` 的语义核算合法位置区间 [0, len-subLen]；已确认调用方 FilterOpHelper 的 contains 语义依赖完整区间。

### [P1] CollectionHelper.sumDouble 用 int 累加器累加 double，小数部分全部截断

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/CollectionHelper.java:1037-1043`
- **维度**: D1（错误的类型/默认值处理）
- **证据**:
```java
public static <T> double sumDouble(List<T> list, ToDoubleFunction<T> fn) {
    int ret = 0;                        // 应为 double ret = 0
    for (T item : list) {
        ret += fn.applyAsDouble(item);  // 复合赋值隐式截断回 int
    }
    return ret;
}
```
- **现状**: 累加器声明为 int，`ret += double` 每次都会截断小数。`sumDouble([0.5,0.5])` 返回 0，`sumDouble([1.5,2.5])` 返回 4。
- **风险**: 调用方 `BatchGenStateBuilder.java:40`（nop-batch-gen）用它累计测试数据的权重和；权重为小数（如 0.3/0.3/0.4）时总和为 0，`MathHelper.randomChoose` 的随机分支选择分布完全失真。
- **建议**: 改为 `double ret = 0;`，补充小数权重测试。
- **误报排除**: 已核对全仓唯一生产调用方 BatchGenStateBuilder（权重字段允许小数）；对照同文件 sumInt/sumLong 实现确认是笔误。

### [P1] SafeLineReader.countLines 对以换行符结尾的文件多数一行

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/io/stream/SafeLineReader.java:100-105, 114-123`
- **维度**: D1（边界条件）
- **证据**:
```java
// readLine():
int c = nextChar();
if (c == -1) {
    lineIndex++;                      // EOF 空读也累加行号
    return new LineRead(sb.length() == 0 && !truncated ? null : sb.toString(), truncated, false);
}
// countLines():
while (r.hasNext()) {
    LineRead line = r.readLine(Integer.MAX_VALUE);
    if (line.content == null && !line.truncated) break;
}
return (int) r.getLineIndex();
```
- **现状**: 读到换行符后 `eof` 尚未置位，`hasNext()` 仍返回 true；下一轮 `readLine` 触发底层 read 返回 -1，EOF 分支无条件 `lineIndex++` 后返回 null 才退出。因此内容为 `"a\n"` 的文件行数为 2（正确为 1）；所有以 `\n` 结尾的文本文件（绝大多数场景）行数都 +1。不以 `\n` 结尾的文件（如 `"abc"`）则正确。
- **风险**: 调用方 `LocalToolFileSystem.countLines`（nop-ai-toolkit）→ `ReadFileExecutor` 用于向 AI 工具报告文件总行数，几乎所有文件都报告错误行数。
- **建议**: EOF 分支仅在 `sb.length() > 0 || truncated` 时 `lineIndex++`；或 `countLines` 改为不计入返回 null 的最后一次读取。补充 `"a\n"`、`"a"`、`"a\nb\n"` 三种用例。
- **误报排除**: 已逐行推演 nextChar 的 eof 置位时序（reader.read(buf) <= 0 才置 eof）；已确认 hasNext 仅依赖 eof 字段；已核对调用链 ReadFileExecutor → IToolFileSystem.countLines。

### [P1] MutableString.substring(int,int) 使用相对参数 start 计算长度，结果错误或越界

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/text/MutableString.java:137-143`
- **维度**: D1（变量引用错误/复制粘贴错误）
- **证据**:
```java
public String substring(int start, int end) {
    if (start == end)
        return StringHelper.EMPTY_STRING;
    Guard.checkPositionIndex(start, length());
    return new String(buf, this.start + start, Math.min(start + end, this.limit));  // 应为 this.start + end
}
```
- **现状**: 第三参（长度语义上应为 `end-start`，代码写成 `min(start+end, limit)`）混用了相对参数 `start` 与绝对 `limit`。对照同类方法 `subSequence`（128-131 行）正确写法为 `Math.min(this.start + end, this.limit)`。当 `this.start > 0` 时，`substring(2, 4)` 会返回从 `this.start+2` 开始、长度为 `min(6, limit)` 的字符串——长度错误，且可能越过 limit 读到缓冲区外数据或抛 StringIndexOutOfBoundsException。
- **风险**: MutableString 是公开工具类，且 `TextScanner.getReusableBuffer()` 把它暴露给 XNodeParser、GraphQLDocumentParser、SimpleExprParser 等解析器（这些解析器传入的 MutableString 常由 subSequence 产生，`this.start > 0` 很常见）。当前仓内 grep 未发现直接调用 `.substring(int,int)` 的生产代码，属于一调用即错的潜伏 API 缺陷。
- **建议**: 改为 `new String(buf, this.start + start, end - start)`（与 subSequence 对齐），补充 `this.start > 0` 场景的单测。
- **误报排除**: 已对照 subSequence 与 getChars 的坐标系约定（参数相对、字段绝对）；已确认 `start==0` 时碰巧正确的路径不能掩盖 `start>0` 的错误。

### [P1] MutableString.insert/delete 相对/绝对坐标混用，insert 语义完全失效

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/text/MutableString.java:549-581`
- **维度**: D1（逻辑错误/变量覆盖）
- **证据**:
```java
public MutableString insert(int offset, CharSequence str) {
    ...
    offset = start + limit;      // 覆盖了入参 offset，应为 start + offset
    System.arraycopy(buf, offset, buf, offset + len, limit - offset);  // limit-offset = -start，start>0 时负长度
    ...
}
public MutableString delete(int begin, int end) {
    if (end > limit)             // 相对 end 与绝对 limit 比较，应为 length()
        end = limit;
    int newCount = limit - (end - begin);
    System.arraycopy(buf, end, buf, begin, limit - end);  // 相对下标当绝对下标用
```
- **现状**: `insert` 第 556 行将入参 offset 覆盖为 `start+limit`（缓冲末尾）：`start==0` 时退化为 append 且 arraycopy 长度为 0，`start>0` 时 arraycopy 长度为负直接抛 IndexOutOfBoundsException——任何调用都无法在指定位置插入。`delete`/`deleteCharAt` 将相对坐标当绝对下标使用：`start>0` 时删除的是缓冲区开头的错误数据（且 `end > limit` 的 clamp 语义错误），仅 `start==0` 时碰巧正确。
- **风险**: 公开 API 完全错误。当前仓内生产代码未直接调用这两个方法（TextScanner 内部的 localBuf 是 `new MutableString()` 且只 append，`start` 恒为 0，掩盖了问题），属于潜伏缺陷；`deleteWhitespace()`（595 行）内部调用 `deleteCharAt`，一旦被以 `start>0` 的实例调用即数据损坏。
- **建议**: insert 改为 `offset = start + offset`；delete 全程转换为绝对坐标（`begin += start; end += start;` 并以 limit 为界 clamp）；补充 `start>0` 的单测。
- **误报排除**: 已通读 425-604 行确认类内坐标系约定（charAt/indexOf/getChars 均为相对参数 + start 偏移）；已确认 replace(int,int,CharSequence)（529 行）与 delete 一样存在 `if (end > limit)` 相对/绝对混用，一并修复。

### [P2] MathHelper.max 与 min 对 v2==null 的处理不一致

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/MathHelper.java:712-726`
- **维度**: D8（契约不一致）
- **证据**:
```java
public static Object max(Object v1, Object v2) {
    if (v1 == null)
        return v1;
    if (v2 == null)
        return v1;              // 忽略 null，返回另一值
    ...
}
public static Object min(Object v1, Object v2) {
    if (v1 == null)
        return null;
    if (v2 == null)
        return null;            // 直接返回 null
    ...
}
```
- **现状**: `max(x, null)` 返回 x，`min(x, null)` 返回 null。SQL 语义下 MAX/MIN 都应忽略 null；两函数行为相反。
- **风险**: 依赖"忽略 null"语义的调用方（MaxAggregator/MinAggregator/ReportFunctions）当前都自行判空后才调用，未触发；但任何直接调用方会得到不一致结果，属于易踩雷的契约漂移。
- **建议**: 统一语义（建议与 SQL 一致：v2==null 时返回 v1），在 javadoc 中写明。
- **误报排除**: 已核对 MaxAggregator/MinAggregator/ReportFunctions 均 null 防护后调用，当前无运行时危害，故定 P2。

### [P2] AverageAggregator 将 null 值计入分母，与 SumAggregator 语义不一致

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/aggregator/AverageAggregator.java:11-18`
- **维度**: D8（契约一致性/D1）
- **证据**:
```java
public void update(Object value) {
    if(value != null)
        this.sum = MathHelper.add(this.sum, value);

    this.count++;    // null 也计数
}
```
- **现状**: null 不计入分子（sum）但计入分母（count）。同包 SumAggregator/MinAggregator/MaxAggregator 均忽略 null；调用链 `RecordAggregateState.aggregate`（nop-record:121-128）直接把 `getProp` 结果（可为 null）传入 update，不过滤。
- **风险**: 报表/文件聚合配置 AVG 时，含空值字段的平均值系统性偏低，与 SUM/COUNT 的"忽略 null"直觉不一致；全 null 数据 AVG=0 而 MIN/MAX=null，行为矛盾。
- **建议**: null 时直接 return（与 SQL/Excel 对齐），或明确文档化"AVG=SUM/COUNT(*)"并保持全聚合器一致。
- **误报排除**: 已核对 AggregateState.aggregate 无 null 过滤、RecordAggregateState.aggregate 的 value 可为 null（getProp 对 record==null 返回 null）。

### [P2] ByteString.arrayRangeEquals 第三参传错数组长度，indexOf/endsWith 等非零偏移匹配全部失效

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/bytes/ByteString.java:543-545`
- **维度**: D1（参数传递错误）
- **证据**:
```java
static boolean arrayRangeEquals(byte[] a, int aOffset, byte[] b, int bOffset, int byteCount) {
    return ByteHelper.equals(a, aOffset, b.length - aOffset, b, bOffset, byteCount);
    // Bytes.equals 签名为 (left, leftOffset, leftLen, right, rightOffset, rightLen)
    // leftLen 应为 a.length - aOffset
}
```
- **现状**: `leftLen` 误用 `b.length - aOffset`。`Bytes.equals`（Bytes.java:948-957）在 `leftLen != rightLen` 时直接返回 false，而 rightLen=byteCount。因此仅当 `b.length - aOffset == byteCount`（即 aOffset==0 且 b 为全量）时才可能匹配。后果：`indexOfBytes(other, fromIndex>0)`、`indexOf(other, n)`、`endsWith`、`rangeEquals(offset>0)` 永远返回 -1/false；`indexOf` 只能命中位置 0。
- **风险**: 当前唯一生产调用方 `RecordFileMeta.java:163` 使用 `startsWithBytes`（aOffset=0 恰好落在正确路径），未触发；属潜伏 API 缺陷。
- **建议**: 改为 `a.length - aOffset`；补充非零偏移 indexOf/endsWith 测试。
- **误报排除**: 已读 Bytes.equals 实现确认长度不等即 false 的短路；已核算 startsWith 的参数组合恰好凑出 leftLen==rightLen；已全仓搜索该家族方法调用方。

### [P2] ByteString.toByteArray 直接返回内部数组，违背不可变契约

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/bytes/ByteString.java:150-154`
- **维度**: D8/D1（不可变性契约破坏）
- **证据**:
```java
/**
 * ... 不对外暴露内部数组结构，toByteArray返回的是拷贝生成的新数组。
 */
@ImmutableBean
public final class ByteString implements ... {
    @Override
    public byte[] toByteArray() {
        return bytes;             // 未拷贝
    }
```
- **现状**: 类 javadoc 与 `@ImmutableBean` 声明为不可变、承诺 toByteArray 返回拷贝，实现却直接返回内部数组。调用方修改返回数组会污染 ByteString 内容；由于 `hashCode` 有懒缓存（127-130 行），污染后 hashCode/equals 不再一致，作为 HashMap key 时行为错乱。
- **风险**: 任何按文档使用 `toByteArray()` 并原地修改的代码都会产生隐蔽数据损坏；`IByteArrayView` 接口的实现语义也与不可变声明冲突。
- **建议**: 要么返回 `bytes.clone()`（与文档一致），要么修改文档/接口明确"返回内部引用，调用方不得修改"并去掉 ImmutableBean 语义承诺。二选一，保持一致。
- **误报排除**: 已确认构造路径 `of(byte[])`/`from(byte[])` 同样直接持有外部数组（okio 风格所有权转移可以接受），问题仅在 toByteArray 的文档与实现矛盾；已核对 hashCode 懒缓存导致污染后不一致的路径。

### [P2] IoHelper 默认序列化器为 Java 原生反序列化，构成潜在不安全反序列化入口

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/IoHelper.java:49-50` 与 `io/serialize/JavaSerializer.java:64-70`
- **维度**: D5（不安全的反序列化）
- **证据**:
```java
// IoHelper:
static volatile IStreamSerializer s_streamSerializer = JavaSerializer.INSTANCE;
static volatile IByteArraySerializer s_byteArraySerializer = JavaSerializer.INSTANCE;

// JavaSerializer:
public Object deserializeFromStream(InputStream is) {
    try {
        return getObjectInput(is).readObject();     // ObjectInputStream 原生反序列化
    ...
```
- **现状**: 全局默认的字节流/字节数组反序列化器是 `ObjectInputStream.readObject()`，无类型过滤（无 ObjectInputFilter）。`IoHelper.serializeClone`、`deserializeFromByteArray` 等公共入口均走该实现。
- **风险**: 当前仓内 grep 未见把不可信外部输入直接送入这些入口的生产调用（主要用于对象深拷贝），故不定 P0/P1；但作为底层公共组件，一旦任何上层将网络/缓存中的字节交给它反序列化，即构成经典 gadget RCE 面。
- **建议**: 为 `JavaSerializer` 增加 `ObjectInputFilter`（白名单或 JDK 序列化深度/数组限制）；或在文档中显著标注"仅限可信数据"，并提供默认安全的替代实现。
- **误报排除**: 已全仓搜索 JavaSerializer/IoHelper.serializeClone 的调用点，确认当前无可信边界外的输入路径，按"潜在风险"定级 P2。

### [P2] MutableString.indexOf(String,pos) off-by-one 漏掉末尾匹配

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/text/MutableString.java:216-228`
- **维度**: D1（边界条件）
- **证据**:
```java
pos += start;
if (pos + str.length() > limit)
    return -1;
for (int i = pos, n = limit - str.length(); i < n; i++) {   // 应为 i <= n
    if (_startsWith(str, i))
        return i - start;
}
return -1;
```
- **现状**: 与 StringHelper.indexOfIgnoreCase 同型 off-by-one：子串恰好结束于 limit 时（如 buf="hello", indexOf("lo")）不会被检查，返回 -1。
- **风险**: 内部调用方 `replace(String,String)`（583-593 行）依赖它定位子串，末尾出现的子串不会被替换。当前仓内无生产代码调用 replace(String,String)，属潜伏缺陷。
- **建议**: 循环条件改为 `i <= n`，补充末尾匹配测试。
- **误报排除**: 已核对 `_startsWith` 与 limit 边界的合法区间；确认与 StringHelper.indexOfIgnoreCase 是两处独立实现。

### [P2] FileHelper.countLines 对不以换行符结尾的多行文件少计一行

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/FileHelper.java:706-733`
- **维度**: D1（边界条件）
- **证据**:
```java
while (channel.read(buffer) != -1) {
    ...
    while (buffer.hasRemaining()) {
        if (buffer.get() == '\n') {
            count++;
        }
    }
    ...
}
// 处理不以换行符结尾的最后一行
return hasContent && count == 0 ? 1 : count;
```
- **现状**: 只数换行符个数；末尾无换行的最后一行仅在"全文件 0 个换行符"时被特判为 1 行。`"a\nb"`（2 行）返回 1，`"a\nb\n"`（2 行）返回 2，`"abc"` 返回 1。
- **风险**: 与 SafeLineReader.countLines（多计一行）方向相反，两套行数统计并存且都有错，跨用点行为不一致。
- **建议**: 记录最后一个字节是否为 '\n'：`return count + (lastByteIsNewline ? 0 : (hasContent ? 1 : 0));`；补充三种结尾形态测试。
- **误报排除**: 已推演三种输入形态的计数路径；确认该方法当前无生产调用方（潜伏）。

### [P3] MathHelper.gcd(int[]) 在 length==1 时数组越界，且修改入参

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/MathHelper.java:1082-1088`
- **维度**: D1（边界条件/副作用）
- **证据**:
```java
public static int gcd(int[] arr) {
    int i = 0;
    for (; i < arr.length - 1; i++) {
        arr[i + 1] = gcd(arr[i], arr[i + 1]);   // 修改入参
    }
    return gcd(arr[i], arr[i - 1]);             // length==1 时 arr[-1] 越界
}
```
- **现状**: 单元素数组时 `i=0`，`gcd(arr[0], arr[-1])` 抛 ArrayIndexOutOfBoundsException；空数组同样越界；且函数原地改写入参数组。
- **风险**: 当前无外部调用方（已全仓 grep），属死代码中的缺陷。
- **建议**: length==1 直接返回 `gcd(arr[0], arr[0])`，length==0 抛 IllegalArgumentException；去掉副作用。
- **误报排除**: 已全仓搜索确认无调用方，定 P3。

### [P3] MathHelper 杂项：randomChoose 空权重抛异常、toShortHash 负值、secureRandom 懒初始化竞态

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/MathHelper.java:191-197, 260-263, 1429-1442`
- **维度**: D1/D3（边界条件、懒初始化竞态）
- **证据**:
```java
// 191-197: 懒初始化无同步，s_secureRand 非 volatile
static IRandom s_secureRand;
public static IRandom secureRandom() {
    if (s_secureRand == null)
        s_secureRand = new DefaultSecureRandom();
    return s_secureRand;
}
// 260-263: Integer.MIN_VALUE 时 Math.abs 仍为负
public static short toShortHash(int value) {
    int hash = Math.abs(value);
    return (short) (hash % Short.MAX_VALUE);
}
// 1429-1442: items 为空或权重全 0 时 ttlWeight=0
int ttlWeight = sum(items);
int rnd = MathHelper.random().nextInt(ttlWeight);   // nextInt(0) 抛 IllegalArgumentException
```
- **现状**: 三处独立小问题：并发首调 secureRandom 可能重复创建实例（幂等、无正确性危害，但与 IoHelper.s_streamSerializer 的 volatile 处理不一致）；`toShortHash(Integer.MIN_VALUE)` 返回负 short；`randomChoose` 对空列表/零总权重抛未文档化异常。
- **风险**: 低。均需特定输入或特定时序。
- **建议**: s_secureRand 加 volatile（对齐 IoHelper）；toShortHash 用 `Math.abs(hash % Short.MAX_VALUE)` 或无符号处理；randomChoose 入口校验 ttlWeight>0。
- **误报排除**: 已确认 DefaultSecureRandom 重复创建无害（包装 SecureRandom）；已核对 nextInt(0) 的 JDK 行为。

### [P3] StringHelper.intToHex/longToHex 误用 Objects.requireNonNull 校验布尔表达式

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java:767-768, 785-786`
- **维度**: D4（错误的参数校验方式）
- **证据**:
```java
public static String intToHex(int value, int minLength) {
    Objects.requireNonNull(minLength >= 0, "minLength must be >= 0");
    char[] buf = new char[Math.max(8, minLength)];
```
- **现状**: 用 `requireNonNull(boolean, msg)` 校验数值条件：minLength<0 时抛的是 NullPointerException（而非 IllegalArgumentException），且写法表达的是"非空检查"而非"范围检查"，容易误导。实际兜底由 `Math.max(8, minLength)` 完成。
- **风险**: 极低，仅异常类型语义错误。
- **建议**: 改为 `Guard.checkArgument(minLength >= 0, ...)`。
- **误报排除**: 已确认 minLength<0 时实际由 Math.max 兜底不产生错误结果，仅异常路径问题。

### [P3] StringHelper.unescapeJava 对 "\u" 后不足 4 位十六进制的输入抛 StringIndexOutOfBoundsException

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java:503-545`
- **维度**: D1（边界输入的异常类型失控）
- **证据**:
```java
case 'u': {
    int sValue = 0;
    for (int k = 1; k <= 4; k++) {
        i++;
        char aChar = str.charAt(i);    // "a\u12" 结尾时 i 越界
        ...
```
- **现状**: 循环固定前移 4 位，不检查剩余长度；畸形输入得到 JDK 原生越界异常而非代码中规范的 `ERR_TEXT_INVALID_UNICODE` NopException。另外该函数禁止 `\u0000`~`\u00ff` 区间转义（544-545 行主动抛错），属于未在 javadoc 说明的隐含限制。
- **风险**: 低，仅畸形输入的异常体验问题。
- **建议**: 循环内检查 `i+k >= str.length()` 并抛 ERR_TEXT_INVALID_UNICODE；在 javadoc 记录 ≤0xFF 限制。
- **误报排除**: 已逐字符推演 "a\u12"（长度 5）在 k=4 时 charAt(6) 越界。

### [P3] StringHelper.isUSASCII 缺少 null 防护，违背类的 null 容忍约定

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java:4620-4630`
- **维度**: D1（NPE 风险）
- **证据**:
```java
/**
 * 所有关于String的常用帮助函数。...所有参数都允许为null。
 */
public static boolean isUSASCII(@Name("input") String input) {
    for (int i = 0, n = input.length(); i < n; i++) {   // input==null 时 NPE
```
- **现状**: 类注释承诺所有参数允许 null，该方法直接解引用。
- **风险**: 该方法带 @Name 注解可被 EL 表达式调用，EL 传 null 时抛 NPE。
- **建议**: 开头加 `if (input == null) return false;`。
- **误报排除**: 已对照同文件其他 @Name 方法（如 wrapExpr）均有 isEmpty 防护，确认是遗漏。

### [P3] ByteString.isSafeUtf8 条件逻辑写反，非空输入恒返回 false

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/bytes/ByteString.java:179-187`
- **维度**: D1（逻辑运算符错误）
- **证据**:
```java
public boolean isSafeUtf8() {
    for (int i = 0, n = bytes.length; i < n; i++) {
        byte b = bytes[i];
        if (!StringHelper.isDigit(b) || !StringHelper.isAsciiLetter(b) || b == '-' || b == '_') {
            return false;
        }
    }
    return true;
}
```
- **现状**: 一个字节不可能同时是数字和字母，OR 条件对任何字节都为真，因此任何非空内容都返回 false。意图应为 `!(isDigit(b) || isAsciiLetter(b) || b=='-' || b=='_')` 才返回 false。
- **风险**: 当前全仓无调用方（死代码），无运行时影响。
- **建议**: 修正条件或删除死方法。
- **误报排除**: 已全仓搜索 isSafeUtf8 确认无调用方。

### [P3] IntHashMap javadoc 声明允许 null 值，实际 put(key,null) 等价于 remove(key)

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/collections/IntHashMap.java:29-32, 128-130`
- **维度**: D8（文档与实现矛盾）
- **证据**:
```java
/**
 * ... Null values are allowed. ...
 */
public V put(int key, V value) {
    if (value == null)
        return remove(key);
```
- **现状**: 类注释（继承自 libgdx 原始实现）声明允许 null 值，本实现把 null 值 put 解释为删除。
- **风险**: 误导调用方；computeIfAbsent 中对命中槽位的 null 检查（471-477 行）因此成为不可达代码。
- **建议**: 更新 javadoc 为"null value 被视为删除映射"。
- **误报排除**: 已通读 put/computeIfAbsent/remove 确认 null 不会被存入 valueTable。

### [P3] IoHelper.peekFirstNBytes 空流抛无消息 IOException 且不重置流位置

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/IoHelper.java:365-387`
- **维度**: D4（异常信息丢失）/D2（mark 未 reset）
- **证据**:
```java
stream.mark(limit);
ByteArrayOutputStream bos = new ByteArrayOutputStream(limit);
copy(new BoundedInputStream(stream, limit), bos);

int readBytes = bos.size();
if (readBytes == 0) {
    throw new IOException();      // 无消息；且未 stream.reset()
}
```
- **现状**: 空流时抛出的 IOException 没有任何消息与上下文，且此前 mark 后已消费 0 字节（无位置漂移，但未按 javadoc 惯例 reset），调用方拿不到诊断信息。
- **风险**: 低（该行为继承自 POI 原实现）。
- **建议**: 抛出带消息的 IOException（如 "empty stream"）。
- **误报排除**: 已确认 0 字节消费下流位置未变，仅异常质量问题。

### [P3] FileHelper.writeBytes 对无父目录的相对路径文件 NPE

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/FileHelper.java:91-93`
- **维度**: D1（NPE 风险）
- **证据**:
```java
public static void writeBytes(File file, byte[] bytes) {
    file.getParentFile().mkdirs();   // getParentFile() 可能为 null
    FileOutputStream os = null;
```
- **现状**: `new File("a.txt")` 的 getParentFile() 返回 null，直接 NPE。同类方法 writeText（147-148 行）使用带 null 检查的 assureParent，写法不一致。
- **风险**: 低（多数调用方传带目录的路径）。
- **建议**: 改用 `assureParent(file)`。
- **误报排除**: 已对照 assureParent 的 null 防护确认不一致。

### [P3] CollectionHelper.getByIndex 负下标行为不一致；splitChunk 对 chunkSize<=0 除零

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/CollectionHelper.java:799-816, 494-512`
- **维度**: D1（边界条件）
- **证据**:
```java
public static <T> T getByIndex(Iterable<T> c, int index) {
    if (c instanceof List) {
        List<T> list = (List<T>) c;
        if (list.size() <= index)
            return null;
        return list.get(index);      // index<0 时抛 IndexOutOfBoundsException
    } else if (c instanceof Collection) { ... }  // index<0 时走迭代返回 null

public static <T> List<List<T>> splitChunk(Collection<T> allData, int chunkSize) {
    if (allData.size() < chunkSize) ...
    int batchCount = (int) Math.ceil(list.size() / (double) chunkSize);  // chunkSize==0 除零
```
- **现状**: getByIndex 负下标在 List 分支抛异常、非 List 分支返回 null；splitChunk 未校验 chunkSize<=0（size>0 且 chunkSize=0 时 `/ by zero`，chunkSize<0 时返回空列表或负 batchCount）。
- **风险**: 低。ChildIndexSelector 等调用方传入的下标来自 XPath 语法通常非负。
- **建议**: getByIndex 入口 `if (index < 0) return null;`；splitChunk 加 `Guard.checkArgument(chunkSize > 0, ...)`。
- **误报排除**: 已核对 ChildIndexSelector 调用路径与 XPath 下标来源。

### [P3] CollectionHelper.disjoint 无意义的 contains 自检与 O(n²) 复杂度、重复元素问题

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/CollectionHelper.java:767-797`
- **维度**: D6/D1（冗余计算、重复元素语义）
- **证据**:
```java
for (T o : list1) {
    boolean b1 = list1.contains(o);   // o 迭代自 list1，恒为 true（除非 equals 自异），O(n) 冗余调用
    boolean b2 = list2.contains(o);
    if (b1 && !b2 || (!b1 && b2)) {   // 实际退化为 !b2
        result.add(o);
    }
}
```
- **现状**: `b1 = list1.contains(o)` 是纯粹的冗余 O(n) 调用（o 就来自 list1）；整个实现为 O(n²)。若 list1 含重复元素，结果 List 中会重复出现。结果碰巧等价于对称差，但代码极具误导性。
- **风险**: 性能与可维护性；当前无生产调用方（已 grep）。
- **建议**: 用 HashSet 重写：`Set 差集` 两次再合并，去重且 O(n)。
- **误报排除**: 已全仓搜索确认无调用方，定 P3。

### [P3] LocalResourceLockManager 锁状态字段非 volatile 且 synchronized(latch) 未覆盖 checkTimeout 清理路径

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/LocalResourceLockManager.java:117-134, 226-249`
- **维度**: D3（可见性/锁覆盖不全）
- **证据**:
```java
public void checkTimeout() {                       // 定时线程，无同步
    for (LocalResourceLockState lock : locks.values()) {
        if (lock.getExpireTime() <= now) {         // ResourceLockState.expireTime 非 volatile
            removeExpiredLock(lock);
        }
    }
}
public boolean tryResetLease(IResourceLockState lock, long leaseTime) {
    synchronized (state.getLatch()) {              // 与 checkTimeout 不互斥（countDown/await 不取该监视器）
        ...
        state.setExpireTime(current + leaseTime);
```
- **现状**: `ResourceLockState` 的 `expireTime/lockTime` 为普通 long 字段；`tryResetLease`/`isHoldingLock` 在 latch 对象上 synchronized，但 `checkTimeout`/`removeExpiredLock` 完全不加锁。续租与过期清理并发时，checkTimeout 可能读到旧的 expireTime 而把刚续租的锁删除（tryResetLease 随后因 `locks.get(...)==lock` 失败返回 false，调用方可感知失败，不会出现双持有，但锁保护被意外提前解除）。
- **风险**: 低概率窗口下分布式互斥保护被提前撤除；最终一致性检查避免了双持有假象。
- **建议**: `expireTime/lockTime` 声明为 volatile；removeExpiredLock 与 tryResetLease 使用同一把锁（如在 state 对象上 synchronized）。
- **误报排除**: 已通读 tryLockWithLease/removeExpiredLock/tryResetLease/isHoldingLock 的全部交互路径，确认并发下不会出现"两个线程同时认为持有"（条件 remove `locks.remove(resourceId, lock)` 保证唯一性），问题限于提前释放窗口。

### [P3] AESTextCipher legacy 路径的 secretKey 缓存无 volatile，且 decryptInputStream 修改共享 this.iv

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java:88-89, 202-217, 300-313`
- **维度**: D3（可见性/共享可变状态）
- **证据**:
```java
private SecretKeySpec secretKey;                    // 无 volatile（对比 v1SecretKey 有）
...
public CipherInputStream decryptInputStream(InputStream is) {
    if (concatIv) {
        iv = new byte[getIvLength()];
        IoHelper.readFully(is, iv);
        this.iv = iv;                               // 把每流的 IV 写回共享实例字段
    }
```
- **现状**: v1 路径的 `v1SecretKey` 已按线程安全处理（volatile + 注释说明），legacy 路径的 `secretKey` 没有——`setEncKey` 将其置 null 后其他线程可能仍读到旧缓存 key 加密，产生解不开的密文；`decryptInputStream` 在并发多流时把不同流的 IV 互相覆盖到 `this.iv`（虽然本流解密用的是局部变量，覆盖无功能影响，但属于无意义的共享状态副作用）。
- **风险**: 仅在运行期动态修改 encKey/saltKey 的多线程场景触发；实例通常初始化后不变。
- **建议**: `secretKey` 加 volatile，对齐 v1SecretKey 的处理；decryptInputStream 去掉 `this.iv = iv` 赋值。
- **误报排除**: 已确认默认 versionedFormat=true 时加密走 encryptVersioned（每次随机 IV，不受此影响），问题限于 legacy/流式路径。

### [P3] DateHelper.buildFormatter 依赖 JVM 默认 Locale，且一周起始日由 Calendar locale 决定

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/DateHelper.java:120-129, 270-273`
- **维度**: D1/D8（环境相关行为）
- **证据**:
```java
static DateTimeFormatter buildFormatter(String pattern) {
    return s_formatters.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);  // 未指定 Locale
}
public static long getWeekStartWithTimeZoneTs(TimeZone timeZone, long ts) {
    Calendar calendar = Calendar.getInstance(timeZone, Locale.ROOT);
    calendar.add(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek() - calendar.get(Calendar.DAY_OF_WEEK));
```
- **现状**: `DateTimeFormatter.ofPattern(pattern)` 使用默认 Locale，含本地化文本的模式（如 `E`、`a`、`MMM`）在不同服务器 Locale 下输出不同并被永久缓存；`getWeekStart*` 的周起始日随 `Locale.ROOT` 的 Calendar 实现（通常周日）固定，未提供参数化，与中文环境"周一为一周开始"的直觉可能不符。
- **风险**: 跨环境部署时日期文本/周边界不一致，难以复现。
- **建议**: buildFormatter 用 `DateTimeFormatter.ofPattern(pattern, Locale.ROOT)`（或显式注入）；getWeekStart 增加 firstDayOfWeek 参数并文档化当前行为。
- **误报排除**: 已确认纯数字模式（yyyy-MM-dd 等主流用法）不受 Locale 影响，影响面限于本地化文本模式。

### [P3] MapCache.getAllPresent 跳过 null 值条目

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/MapCache.java:122-131`
- **维度**: D1（null 值语义）
- **证据**:
```java
public Map<K, V> getAllPresent(Collection<? extends K> keys) {
    Map<K, V> ret = new HashMap<>();
    for (K key : keys) {
        V value = getIfPresent(key);
        if (value != null) {          // put(key, null) 的条目被跳过
            ret.put(key, value);
        }
    }
    return ret;
}
```
- **现状**: 非 threadSafe 模式（HashMap）允许 put null 值，getAllPresent 与 containsKey 的结果会不一致（containsKey=true 但 getAllPresent 不含）。
- **风险**: 低；ICache 家族（Caffeine 底层）本身不允许 null 值，MapCache 的 HashMap 模式是特例。
- **建议**: 用 `containsKey(key)` 判定后取值，或在 MapCache 文档中声明不允许 null 值。
- **误报排除**: 已确认 HashMap 模式下 put(key,null) 合法且 containsKey 为 true。

## 补充说明（非缺陷观察）

- `Seq.java:59` 的 `catch (StopException expected) {}` 是 take/drop 控制流的正常写法，非异常吞噬。
- `StringHelper.escapeHtml` 将 `\r` 映射为空串、`\n` 映射为 `<br/>`（131-133 行），为有意设计。
- `DateHelper.formatTimestamp` 使用方法内新建的 SimpleDateFormat（254-261 行），无线程安全问题，仅未复用缓存。
- D7（Nop IoC 规范）扫描未发现 `@Inject` private 字段或 Spring `@Value` 用法；`DefaultServerAddrFinder` 使用的是正确的 `@InjectValue`。nop-commons 无 beans.xml 注册预期（无 IoC bean），符合模块定位。
- `HighWatermarkSemaphore`、`SequentialTaskExecutor`、`TaskQueue/StandardThreadPoolExecutor`（Tomcat 拷贝）、`IntHashMap`（libgdx 拷贝）经推演未发现正确性问题。
