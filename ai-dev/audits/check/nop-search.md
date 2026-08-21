# nop-search 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-search
- 文件数: 19（src/main/java）
- 覆盖范围声明: 实际 src/main/java 主代码文件为 17 个（nop-search-api 8 个、nop-search-core 4 个、nop-search-lucene 5 个），任务所述 19 个中另含 2 个测试文件（`TestLuceneSearchEngine.java`、`TestVectorSearch.java`），按任务要求不在审计范围。17 个主代码文件全部深读，无遗漏、无 `_` 前缀生成文件。另核查了模块内全部非 Java 资源（beans.xml x2、autoconfig x2、api-demo.flux.yaml / api-demo.page.yaml、4 个 pom.xml）以验证 bean 注册与暴露面。依赖类（FilterBeanVisitor / FilterOp / FilterBeanConstants / BatchQueue / FutureHelper / FileHelper / StringHelper）仅做契约验证，不属于本模块，发现问题只作背景说明不计入。Lucene 版本为 9.7.0（`nop-kernel/nop-dependencies/pom.xml`）。所有涉及 Lucene 行为的结论均用本地 9.7.0 jar 编写探针程序实证（探针临时文件已从 `_tmp/` 清除），未实证的推测性结论一律未写入。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 8 |
| P3 | 5 |

## 发现列表

### [P0] 数值/路径字段的 filter 过滤永不命中（查询构造与索引结构不匹配）

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneFilterBeanTransformer.java:49-76`（对照 `LuceneSearchEngine.java:1044-1049、979-981`）
- **维度**: D1（查询构造错误 = 搜不到数据）
- **证据**:
```java
// LuceneFilterBeanTransformer: 数值比较走 DoublePoint
if (value instanceof Number) {
    double numValue = ((Number) value).doubleValue();
    if (filterOp == FilterOp.GT) {
        return DoublePoint.newRangeQuery(field, numValue, Double.POSITIVE_INFINITY);
    } ...

// LuceneSearchEngine.addNumericField: 索引却用 LongPoint
protected void addNumericField(Document doc, String fieldName, long value) {
    if (value > 0) {
        doc.add(new LongPoint(fieldName, value));
        doc.add(new StoredField(fieldName, value));
    }
}

// LuceneSearchEngine.buildDocument: path 只建 StoredField，无索引项
ret.add(new StoredField(FIELD_PATH, doc.getPath()));
```
- **现状**: `SearchRequest.filter`（TreeBean）是公开查询契约，但 transformer 与索引结构三处不匹配：(1) 数值比较（GT/GE/LT/LE）用 `DoublePoint.newRangeQuery`，而 `publishTime/modifyTime/fileSize` 全部以 `LongPoint` 建索引，编码不同无法命中；(2) 数值走 between 也错（见 P2 between 条目，TermRangeQuery 依赖 terms 字典，point 字段无 terms）；(3) `path` 仅 StoredField 未建索引，对 path 做 eq 过滤永不命中，ne 过滤因 `MUST_NOT(不命中子句)+MUST(matchAll)` 反而匹配全部文档。数值 NE 同理：`TermQuery(String.valueOf(100))` 在 LongPoint 字段上不命中，导致 NE 数值条件匹配所有文档。另外 `DoublePoint.newRangeQuery(field, v, +inf)` 官方 javadoc 明确"Ranges are inclusive"，GT/LT 即使类型修对也会含等值边界（应传 `Math.nextUp(v)`）。
- **实证**: 用仓库锁定的 Lucene 9.7.0 构造 LongPoint=100 的文档：`DoublePoint.newRangeQuery("num", 50, +inf)` 命中 0（`LongPoint.newRangeQuery` 命中 1）；`TermRangeQuery("num", "50", "100")` 命中 0。
- **风险**: 任何客户端对时间/文件大小等数值字段或 path 字段设置 filter，结果为空（或 NE 时全量返回），属于静默的数据错误；这是 `SearchRequest.filter` 这一公开 API 能力的整体失效。
- **建议**: transformer 需要按字段类型分发：数值字段用 `LongPoint.newRangeQuery` 且 GT/LT 用 `nextUp/nextDown` 排除边界；between 数值分支同样用 LongPoint；`path` 需按可过滤需求改用 StringField 建索引或在 transformer 中拒绝该字段；为 EQ/NE 增加与索引类型一致的编码。
- **误报排除**: 已确认 filter 转换路径真实接通（`LuceneSearchEngine.buildQuery:841-845` 将 `request.getFilter()` 经 transformer 以 `Occur.FILTER` 加入最终查询）；已用 9.7.0 jar 实测排除"Lucene 会自动做数值类型兼容"的可能。

### [P1] 空 query 时构造空 BooleanQuery，匹配 0 文档，与"Match all"注释意图相反

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:831-836`
- **维度**: D1
- **证据**:
```java
Query mainQuery = StringHelper.isEmpty(request.getQuery())
        ? new BooleanQuery.Builder().build() // Match all if no query
        : parser.parse(request.getQuery(), FIELD_CONTENT);

BooleanQuery.Builder finalQueryBuilder = new BooleanQuery.Builder()
        .add(mainQuery, BooleanClause.Occur.MUST);
```
- **现状**: Lucene 中零子句 BooleanQuery 不匹配任何文档；它又以 `Occur.MUST` 加入最终查询，导致整体（含 tag/filter 子句）匹配 0 文档。正确写法是 `new MatchAllDocsQuery()`（同文件 visitAlwaysFalse 的对照注释也表明作者了解空 BooleanQuery 语义）。
- **实证**: 9.7.0 探针：索引 1 篇文档，`new BooleanQuery.Builder().build()` 命中 0；`MUST(空BooleanQuery)+FILTER(MatchAllDocs)` 命中 0。
- **风险**: 不带关键词、仅按 tags/filter 浏览的场景（SearchRequest.query 为空的合法调用）永远搜不到数据。
- **建议**: 空 query 时使用 `new MatchAllDocsQuery()`。
- **误报排除**: 已排除"空 MUST 子句被忽略"的可能（实证 #9：MUST(空)+FILTER(matchAll) 命中 0）。

### [P1] visitNot 生成纯 MUST_NOT 查询，在 Lucene 中匹配空集

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneFilterBeanTransformer.java:118-125`
- **维度**: D1
- **证据**:
```java
@Override
public Query visitNot(ITreeBean filter, IVariableScope scope) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (ITreeBean child : filter.getChildren()) {
        builder.add(visit(child, scope), BooleanClause.Occur.MUST_NOT);
    }
    return builder.build();
}
```
- **现状**: Lucene 的经典陷阱：只含 MUST_NOT 子句的 BooleanQuery 不匹配任何文档（取补集必须额外加一个 `MatchAllDocsQuery` MUST 锚点，同文件 NE 分支（43-48 行）正是这么写的，NOT 分支却漏了）。
- **实证**: 9.7.0 探针：文档不含 'zzz'，`MUST_NOT(TermQuery("zzz"))` 命中 0（补集语义应为 1）。
- **风险**: filter 中任何 `not` 组合条件使查询结果恒为空。
- **建议**: 仿照 NE 分支补 `builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)`。
- **误报排除**: 已确认 `FilterBeanVisitor.visit` 对 `FilterOp.NOT`（GROUP_OP）确实分发到 visitNot；Lucene 行为已实证。

### [P1] addDocs/removeDocs 失败路径 rollback 会关闭 IndexWriter，但 writer 仍留在缓存 map 中，topic 从此不可写

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:815-818、1070-1073`
- **维度**: D1/D2（资源状态管理）
- **证据**:
```java
} catch (Exception e) {
    rollback(writer);
    throw NopException.adapt(e);
}

protected void rollback(IndexWriter writer) {
    try {
        writer.rollback();
    } catch (Exception e) {
        LOG.error("nop.search.rollback-fail", e);
    }
}
```
- **现状**: Lucene 官方 javadoc：`IndexWriter.rollback()` "Close the IndexWriter without committing..."，调用后 writer 不可再用（实证：rollback 后 addDocument 抛 `AlreadyClosedException`）。但 `indexWriters` 这个 ConcurrentHashMap 中的条目没有移除，后续 `getIndexWriter(topic)` 继续返回已关闭的 writer，该 topic 的所有写操作（以及依赖 writer 的 SearcherManager 刷新）持续抛 `AlreadyClosedException`，直到重启。
- **触发路径现实**: catch 捕获的是写路径一切异常，包括批量中有一条文档缺 id 时 `buildDocument` 里 `Guard.notEmpty(doc.getId())` 的失败——单条坏文档即可永久打死整个 topic。
- **风险**: 一次可恢复的写入失败演变为 topic 级永久不可用（索引服务瘫痪面扩大）。
- **建议**: 失败时从 `indexWriters`（及对应 SearcherManager）移除该条目后再 rollback/close，下次访问时重建 writer；或改用不关闭 writer 的回滚方式（如先 delete 后 add 的两阶段 + commit 失败重试）。
- **误报排除**: 已实证 9.7.0 中 rollback 后 writer 抛 AlreadyClosedException；已确认 map 条目未移除（读取全文核对）。

### [P1] getDoc 忽略 topic，遍历所有已打开 topic 返回第一个命中文档，跨 topic 数据串读

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:272-300`（对照 `SearchEngineBizModel.java:108-112`、`ISearchEngine.java:23`）
- **维度**: D5/D8（接口契约与实现不匹配）
- **证据**:
```java
// SearchEngineBizModel: 声明了 topic 并按 topic 做访问检查
public SearchableDoc getDoc(@Name("topic") String topic, @Name("docId") String docId) {
    checkAllowAccess(ACTION_GET_DOC, topic, Collections.singletonList(docId));
    return searchEngine.getDoc(docId);   // topic 未传入
}

// LuceneSearchEngine.getDoc: 遍历所有 topic
for (String topic : searcherManagers.keySet()) {
    ...
    Query query = new TermQuery(new Term(FIELD_ID, docId));
    TopFieldDocs docs = searcher.search(query, 1, Sort.RELEVANCE);
```
- **现状**: 接口签名 `SearchableDoc getDoc(String docId)` 无 topic 参数，实现遍历所有已缓存 topic 取第一个命中。topic 是该模块的唯一隔离单元（索引按 topic 分目录，`getDocsByTerm`、search 都按 topic 定位），唯独 getDoc 破坏隔离。且 `BuildIndexTool` 的 docId 是相对路径的 md5，不同 topic 索引相同目录会产生相同 docId，串读概率现实存在。
- **风险**: 以 topic A 权限查询拿到 topic B 的文档内容；biz 层基于 topic 的 `checkAllowAccess` 被绕过（当前为空实现，一旦子类启用即成越权通道）。
- **建议**: `ISearchEngine.getDoc` 增加 topic 参数（或提供 `getDoc(topic, docId)`），实现限定在指定 topic 内查找。
- **误报排除**: 已通读 getDoc 全文确认无任何 topic 过滤；biz 层调用点确认未传 topic。

### [P1] indexDir biz 操作接受任意服务器路径建立索引，checkAllowAccess 为空实现，构成任意文件读取面

- **文件**: `nop-search/nop-search-core/src/main/java/io/nop/search/core/biz/SearchEngineBizModel.java:65-90、135-137`
- **维度**: D5（安全）
- **证据**:
```java
@BizMutation
public void indexDir(@Name("topic") String topic, @Name("path") String path,
        @Name("pattern") @Optional String pattern) {
    checkAllowAccess(ACTION_INDEX_DIR, topic, null);   // 空实现
    BuildIndexTool tool = new BuildIndexTool(searchEngine);
    File dir = getLocalFile(path);
    tool.indexAll(topic, dir, getFilter(pattern));
}

protected void checkAllowAccess(String action, String topic, List<String> docIds) {
}
```
- **现状**: `SearchEngineBizModel` 已在 `search-core.beans.xml` 注册（`<bean id="nopSearchEngineBizModel" .../>`），`/r/SearchEngine__indexDir` 是远程可达的 mutation；随模块发布的演示页（`_vfs/nop/search/pages/SearchEngine/api-demo.flux.yaml`）明确以"服务器上的绝对路径"为输入。`getLocalFile` 在 resourceLocator 为 null 时直接 `FileHelper.resolveFile(path)`，对绝对路径（如 `/`、`/etc`、用户主目录）无任何白名单限制，索引后即可通过 search/getDoc 读回文件内容。`setResourceLocator`（53-55 行）无 `@Inject` 注解且 beans.xml 未配置，默认装配下 resourceLocator 恒为 null，限制分支不可达。另外 `pattern` 直接 `Pattern.compile`，用户可控正则可构造灾难性回溯。
- **风险**: 默认部署下，任何能调用该 GraphQL 操作的客户端可索引并读取服务器任意可读文件（配置、密钥、源码）。
- **建议**: 在 `checkAllowAccess` 中提供默认拒绝/管理员角色校验（而不是空方法）；对 `path` 做基于配置根目录的规范化白名单校验（canonical path 前缀检查）；pattern 编译加超时或限制语法。
- **误报排除**: 已确认 bean 注册文件、@BizMutation 注解、演示页 AJAX 目标、resolveFile 对绝对路径原样返回（`FileHelper.java:621-633`）；非臆测的"可能有权限框架拦截"——平台层鉴权不覆盖业务级 topic/路径授权，且空 checkAllowAccess 即模块自身的授权钩子缺失。

### [P2] between 上边界包含性参数传反（excludeMax 直接传入 includeUpper 位置）

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneFilterBeanTransformer.java:89-98`
- **维度**: D1
- **证据**:
```java
boolean excludeMin = isExcludeMin(filter);
boolean excludeMax = isExcludeMax(filter);

return new TermRangeQuery(field, new BytesRef(String.valueOf(lowerValue)),
        new BytesRef(String.valueOf(upperValue)), !excludeMin, excludeMax);
```
- **现状**: `TermRangeQuery` 构造器第 5 参是 `includeUpper`，这里传入的是 `excludeMax`，语义恰好相反：默认 `excludeMax=false` 时上边界被排除，`excludeMax=true` 时反而包含。下边界 `!excludeMin` 写法正确，反证上边界是笔误。另注：基类 `FilterBeanVisitor.isExcludeMax()`（nop-core，模块外）读的是 `FILTER_ATTR_EXCLUDE_MIN` 属性，本身也错，两者叠加使 between 边界语义完全不可控；数值 between 永不命中已并入 P0 条目。
- **实证**: 9.7.0 探针：字段值 'b'，`TermRangeQuery("a","b",includeUpper=false)` 命中 0，`=true` 命中 1。
- **风险**: 字符串字段的 between 过滤在边界值上多删/多留数据。
- **建议**: 第 5 参改为 `!excludeMax`；同时向上游反馈 nop-core `isExcludeMax` 的属性名错误。
- **误报排除**: TermRangeQuery 构造器参数顺序已经 javap + 探针双向确认。

### [P2] SearchRequest.limit 无默认值且无校验：TEXT 静默返回空，VECTOR/HYBRID 直接抛异常崩溃

- **文件**: `nop-search/nop-search-api/src/main/java/io/nop/search/api/SearchRequest.java:24`；`nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:481、529、670、676-681`
- **维度**: D1/D4（分页边界）
- **证据**:
```java
private int limit;   // 默认 0

int k = (int) (request.getLimit() * 1.5); // vectorSearch: limit=0 -> k=0
KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery(FIELD_EMBEDDING, queryVector, k);
```
- **现状**: `limit` 缺省 0：TEXT 搜索 `searcher.search(query, 0, ...)` 静默返回空列表；VECTOR/HYBRID 中 k=0 使 `KnnFloatVectorQuery` 构造器抛 `IllegalArgumentException: k must be at least 1, got: 0`（实证），且该异常不在 `catch (IOException)` 范围内，以裸 Lucene 异常直接冒到调用方。负面 limit 同样未拦截。
- **风险**: 调用方漏设 limit（GraphQL 请求中可省略字段）时，文本搜索无结果、向量搜索报系统错误，错误信息难以定位。
- **建议**: search 入口统一校验/归一化 limit（如 <=0 时取配置默认值或直接拒绝并抛带参数的 NopException）。
- **误报排除**: k=0 抛 IAE 已用 9.7.0 jar 实证；catch 子句范围已核对（只捕 IOException）。

### [P2] HYBRID 搜索把 similarityThreshold（语义为向量相似度 [0,1]）应用在 RRF 分数（≤1/61）上，设置任何常规阈值即清空结果

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:693-696`（对照 546 行 VECTOR 路径的阈值语义、735/770 行 RRF 分数来源）
- **维度**: D8/D1
- **证据**:
```java
List<SearchHit> filteredHits = mergedHits.stream()
    .filter(hit -> hit.getScore() >= request.getSimilarityThreshold())
    .limit(request.getLimit())
    .collect(Collectors.toList());

// mergeWithRRF 中: double rrfScore = 1.0 / (k + i + 1);  k=60，最高分 1/61 ≈ 0.0164
```
- **现状**: `similarityThreshold` 字段在 VECTOR 路径按余弦相似度 [0,1] 过滤；HYBRID 路径却拿同一字段过滤 RRF 融合分（上限约 0.0164）。用户按余弦语义设置 0.3 之类阈值时，HYBRID 结果被整体过滤为空。同名字段两种量纲，属于接口契约漂移。
- **风险**: HYBRID + threshold 的组合静默返回空结果，用户难以归因。
- **建议**: HYBRID 中阈值仅作用于向量命中项的相似度（在向量检索阶段过滤），或定义独立的 RRF 阈值参数；至少在文档/错误信息中明确量纲。
- **误报排除**: RRF 分数上限由 735 行公式直接算得（k=60 硬编码），两处代码均已通读确认。

### [P2] topic 为 null 直接 NPE；topic 为空串时三张缓存 map 键归一化不一致，引发同目录双 IndexWriter 锁冲突

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:215-233、235-245、247-260、470-471`
- **维度**: D1（NPE、资源状态）
- **证据**:
```java
protected Directory getDirectory(String topic) {
    if (StringHelper.isEmpty(topic)) {
        topic = DEFAULT_TOPIC;          // 仅 getDirectory 归一化空 topic
    }
    ...
    return indexDirs.computeIfAbsent(topic, key -> {...});

protected SearcherManager getSearcherManager(String topic) {
    return searcherManagers.computeIfAbsent(topic, key -> {   // null -> NPE；"" -> 键为 ""
        IndexWriter writer = getIndexWriter(topic);            // indexWriters 键为 ""
```
- **现状**: `SearchRequest.topic` 无默认值。null 时 `ConcurrentHashMap.computeIfAbsent(null, ...)` 抛 NPE（textSearch 471 行首先触达）。空串时 `indexDirs` 以归一化后的 "default" 为键，而 `indexWriters`/`searcherManagers` 以 "" 为键：先以 "" 访问再以 "default" 访问（或反之），会对同一 FSDirectory 创建两个 IndexWriter，第二个抛 `LockObtainFailedException`（Lucene 同 JVM 内有锁持有集合防重入），"default" topic 从此无法打开。
- **风险**: 客户端省略/传空 topic 时轻则 NPE 500，重则把默认 topic 的写入能力锁死。
- **建议**: 在 engine 入口统一做 `topic = isEmpty(topic) ? DEFAULT_TOPIC : topic` 归一化并校验（现有 `isValidSimpleVarName` 校验只在 getDirectory 生效，应前移）。
- **误报排除**: getDirectory 的归一化逻辑与另外两个方法的不归一化逻辑均逐行核对；ConcurrentHashMap null 键抛 NPE 是 JDK 既定行为。

### [P2] removeTopic 不删除磁盘索引数据，且与并发使用存在无同步竞态

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:1076-1109`
- **维度**: D1/D3
- **证据**:
```java
@Override
public void removeTopic(String topic) {
    SearcherManager manager = searcherManagers.remove(topic);
    if (manager != null) { manager.close(); ... }
    IndexWriter writer = indexWriters.remove(topic);
    if (writer != null) { writer.close(); ... }
    Directory dir = indexDirs.remove(topic);
    if (dir != null) { dir.close(); ... }
    LOG.info("nop.search.remove-topic:topic={}", topic);
```
- **现状**: 只关闭并移除内存对象，未删除 `rootPath/<topic>` 下的索引文件。同 topic 再次写入时 `CREATE_OR_APPEND` 会重新打开旧目录，已被"删除"的文档全部复活。同时 removeTopic 与并发 getIndexWriter/getSearcherManager 之间无同步：另一线程可能在 writer.close() 前后拿到正在关闭或已关闭的 writer（AlreadyClosedException），或在 remove 之后重新建出第二个 writer 与残留 close 操作竞争。
- **风险**: removeTopic 语义不完整（数据未删除、重新打开后复活）；并发场景下出现难以复现的锁冲突/已关闭异常。
- **建议**: 关闭资源后删除 topic 子目录（或提供显式 `deleteTopicData`）；对三个 map 的生命周期用同一把锁/统一状态机管理。
- **误报排除**: 已通读方法全文确认无任何文件删除调用；IndexWriter 打开模式为 CREATE_OR_APPEND（252 行）已核对。

### [P2] 未配置 ITextEmbedding 时 VECTOR/HYBRID 查询静默退化为 hash 随机向量，返回无意义结果

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:609-619、626-652`
- **维度**: D4/D8
- **证据**:
```java
// 如果有ITextEmbedding，使用它生成embedding
if (textEmbedding != null) { ... }

// 使用简单的文本hash模拟（仅用于测试，实际应使用ITextEmbedding）
return generateSimpleEmbedding(query);
```
- **现状**: `generateSimpleEmbedding` 注释明言"仅用于测试"，但生产路径上 textEmbedding 未注入时无任何告警日志，直接用 `Random(hash)` 伪随机向量参与 kNN 检索。结果是向量/混合搜索"正常"返回与语义无关的随机排序文档，用户无从得知 embedding 后端缺失。文档侧同理：未配置 embedding 的文档根本没有向量字段，kNN 查询只覆盖有向量的子集。
- **风险**: 生产环境静默降级为随机结果，属于比报错更危险的失败模式。
- **建议**: 生产配置下（或总是）在 fallback 时打 WARN；更稳妥的是未配置 ITextEmbedding 时对 VECTOR/HYBRID 直接抛带 ErrorCode 的 NopException。
- **误报排除**: parseQueryVector 全文核对，fallback 路径无日志无异常；textEmbedding 为可选注入（beans.xml 未定义，140 行注释确认可空）。

### [P2] visitFixedValue 覆盖丢失 op 分发：alwaysTrue/alwaysFalse 过滤器抛"缺少 name"异常，visitAlwaysTrue/visitAlwaysFalse 成为死代码

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneFilterBeanTransformer.java:18-33`
- **维度**: D1/D8
- **证据**:
```java
@Override
protected Query visitFixedValue(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
    String field = getName(filter);          // alwaysTrue bean 无 name 属性 -> 抛 ERR_FILTER_NO_NAME_ARG
    Object value = getValue(filter);
    return new TermQuery(new Term(field, String.valueOf(value)));
}

@Override
public Query visitAlwaysTrue(ITreeBean filter, IVariableScope scope) {
    return new MatchAllDocsQuery();          // 永远不会被调用到
}
```
- **现状**: 基类 `visit` 对 FIXED_VALUE 类型（仅 ALWAYS_TRUE/ALWAYS_FALSE，见 `FilterOp.java:316-317`）固定调 `visitFixedValue`；本类覆盖后未按 filterOp 分发到 visitAlwaysTrue/visitAlwaysFalse。`FilterBeans.alwaysTrue()` 生成的 TreeBean 只有 tagName 无 name 属性，`getName` 直接抛 NopException(ERR_FILTER_NO_NAME_ARG)。下游两个精心实现的 visitAlwaysTrue/False 是不可达死代码。
- **风险**: filter 中出现 alwaysTrue/alwaysFalse（常见于条件化拼接过滤条件的化简结果）即报错；语义正确的实现被绕过。
- **建议**: visitFixedValue 开头补 `if (filterOp == FilterOp.ALWAYS_TRUE) return visitAlwaysTrue(...)` 等分发（与基类 128-134 行结构一致）。
- **误报排除**: 基类分发 switch（FIXED_VALUE -> visitFixedValue）与 FilterOp 类型定义均已核对；alwaysTrue 无 name 属性由 `FilterBeans.alwaysTrue()` 实现确认。

### [P2] visitCompareOp 对 in/like/contains 等常用操作符抛裸 UnsupportedOperationException（非 NopException，契约漂移）

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneFilterBeanTransformer.java:36-80`
- **维度**: D4/D7/D8
- **证据**:
```java
} else if (filterOp == FilterOp.LT) {
    return DoublePoint.newRangeQuery(field, Double.NEGATIVE_INFINITY, numValue);
} ...
throw new UnsupportedOperationException("Unsupported comparison operator: " + filterOp);
```
- **现状**: 只处理 EQ/NE/GT/GE/LT/LE；`FilterOp.DEFAULT_COMPARE_OPS` 还包含 IN、NOT_IN、STARTS_WITH、CONTAINS、LIKE 等平台常用操作符（`FilterOp.java:356-357`），传入即抛 `UnsupportedOperationException`——既违背平台错误处理规范（公共查询路径应使用 NopException + ErrorCode + .param），也未向调用方传达支持的算子集合。
- **风险**: 客户端使用平台标准 filter 算子（如 in）时收到不可预期的裸运行时异常；`SearchRequest.filter` 的对外契约与实现能力不符。
- **建议**: 未支持算子抛 `NopException`（定义专用 ErrorCode 并带 op 参数）；或补齐常用算子（in 可用 TermInSetQuery，startsWith/contains 可用前缀/通配查询）。
- **误报排除**: 全方法通读确认仅六个比较算子有实现；异常类型为 UnsupportedOperationException（grep 全模块仅此一处 + biz 层一处 IllegalArgumentException）。

### [P3] getLocalFile 抛裸 IllegalArgumentException 且用字符串拼接消息（公共 biz API 层应使用 NopException + ErrorCode + .param）

- **文件**: `nop-search/nop-search-core/src/main/java/io/nop/search/core/biz/SearchEngineBizModel.java:85-88`
- **维度**: D7（错误处理两档策略）
- **证据**:
```java
File file = resourceLocator.getResource(path).toFile();
if (file == null)
    throw new IllegalArgumentException("path not resolve to file:path=" + path);
return file;
```
- **现状**: 该类是 @BizModel 公共 API 层（GraphQL 可达），按平台规范应使用 `NopException` + ErrorCode + `.param(...)`；现为裸 IllegalArgumentException 加手工字符串拼接。
- **风险**: 错误码体系/参数化诊断信息缺失，客户端无法按 ErrorCode 处理。
- **建议**: 定义模块 ErrorCode 并以 NopException 抛出，path 用 param 附加。
- **误报排除**: 直接读源码确认；非框架核心内部代码，不适用"模块内部可用模块异常类"豁免（biz 层属公共 API 面）。

### [P3] init() 中 analyzer 构建失败复用"打开索引失败"错误码且未填声明的 ARG_TOPIC 参数

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:150-155`（对照 `LuceneErrors.java:12-13`）
- **维度**: D4/D7
- **证据**:
```java
try {
    this.analyzer = buildAnalyzer();
} catch (Exception e) {
    throw new NopException(ERR_LUCENE_OPEN_INDEX_FAIL, e);
}
```
- **现状**: 分析器构建失败与"打开索引"无关却复用 ERR_LUCENE_OPEN_INDEX_FAIL；该 ErrorCode 声明了参数 ARG_TOPIC 但未设置，错误消息中的 topic 占位无值。
- **风险**: 排障时错误语义误导（实际是 analyzer 配置问题）。
- **建议**: 为初始化失败定义独立 ErrorCode（或至少补齐参数），并附带 cause 已有（做得对）。
- **误报排除**: LuceneErrors 定义与 init 代码均核对。

### [P3] getDocsByTerm 无结果上限，高频词全量加载进内存

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:398-441`
- **维度**: D6/D1
- **证据**:
```java
while ((docId = postings.nextDoc()) != PostingsEnum.NO_MORE_DOCS) {
    foundDocIds.add(docId);
}
for (Integer docId : foundDocIds) {
    Document doc = reader.document(docId);
    docs.add(convertDocumentToSearchableDoc(doc));
}
```
- **现状**: 对包含某 token 的所有文档无上限取回并加载 stored 字段（含未 storeContent=false 之外的全文 content），常见词（如代码索引中的 "public"）会把几乎整个索引拉进内存。
- **风险**: 大索引 + 通用词触发内存压力/OOM。
- **建议**: 增加 maxDocs 参数或分页；仅取 id/摘要字段。
- **误报排除**: 方法全文核对无 limit；接口为公开 ISearchEngine 方法（虽然当前 biz model 未直接暴露）。

### [P3] addDocs 对每条文档打 INFO 日志，批量索引时日志洪泛

- **文件**: `nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java:786-789`
- **维度**: D6
- **证据**:
```java
public void addDocs(String topic, List<SearchableDoc> docs) {
    for (SearchableDoc doc : docs) {
        LOG.info("nop.search.add-doc:docId={},path={}", doc.getId(), doc.getPath());
    }
```
- **现状**: `BuildIndexTool` 以 3000 一批走 addDocs，索引大型目录时每文件一条 INFO（结尾另有 bulk-update 汇总日志 814 行，信息重复）。
- **风险**: 索引数万文件时产生数万行日志，拖慢索引并淹没有效日志。
- **建议**: 降为 DEBUG 或删除（保留 bulk-update 汇总即可）。
- **误报排除**: 循环体逐行核对，无级别条件判断。

### [P3] ISearchEngine.removeDocs 形参名笔误 dodIds

- **文件**: `nop-search/nop-search-api/src/main/java/io/nop/search/api/ISearchEngine.java:39`
- **维度**: D8（契约表面）
- **证据**:
```java
void removeDocs(@Name("topic") String topic, @Name("docIds") List<String> dodIds);
```
- **现状**: Java 形参名 `dodIds`，`@Name` 声明的对外参数名 `docIds` 正确，功能不受影响，但影响可读性且易在后续重构中被复制。
- **风险**: 极低，纯维护性。
- **建议**: 重命名为 docIds（跨模块公共 API 模块，按规范属 plan-first 区域，仅记录不建议立即改动）。
- **误报排除**: 直接读源码确认。

## 其他核对结论（未形成发现）

- D2: `destroy()` 按 SearcherManager -> IndexWriter -> Directory 顺序关闭并 clear，异常均被捕获记录，无连接泄漏；`analyzeText` 用 try-with-resources 关闭 TokenStream。
- D3: IndexWriter/SearcherManager 按 topic 缓存共享，未每请求新建（D6 无"每请求新建客户端"问题）；addDocs 并发依赖 Lucene IndexWriter 自身线程安全性，可接受。
- D5: topic 在 getDirectory 处经 `isValidSimpleVarName` 校验（无 `/`、`.`、`$`），目录穿越被阻断；文本查询经 StandardQueryParser 解析，无字符串直拼查询 DSL。
- D7: 未发现 private 字段注入（均为可见 setter 注入）；两个 bean 均在 `_vfs` 下 beans.xml 显式定义；配置经 `ioc:config`（nop.search 前缀）注入，符合 @InjectValue 等价机制；无空 catch、无 printStackTrace、无 System.out、无 bare RuntimeException（grep 全量扫描）。
- 依赖背景（不计入本模块）: nop-core `FilterBeanVisitor.isExcludeMax()` 误读 `FILTER_ATTR_EXCLUDE_MIN`（`FilterBeanVisitor.java:245-247`），加重本模块 between 语义问题，建议上游另行修复。
- 本模块全部结论中涉及 Lucene 运行时行为的 6 项断言（空 BooleanQuery、纯 MUST_NOT、DoublePoint 查 LongPoint、DoublePoint 含边界、TermRangeQuery includeUpper、rollback 关闭 writer、k=0 抛 IAE）均已用仓库锁定的 Lucene 9.7.0 编写探针程序实证。
