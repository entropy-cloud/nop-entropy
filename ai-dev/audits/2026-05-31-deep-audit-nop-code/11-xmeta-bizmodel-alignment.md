# Audit Dimension 11: XMeta and BizModel Alignment — nop-code

### [11-01] NopCodeSymbolBizModel @BizLoader 方法的 ContextSource 类型不匹配

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:96-120`
- **Evidence Snippet**:
```java
@BizLoader
public List<AnnotationUsageDTO> usages(
        @ContextSource SymbolDTO symbol,   // DTO type, not entity type
        @Name("indexId") String indexId,
        @Name("limit") int limit) {

@BizLoader
public String sourceCode(
        @ContextSource SymbolDTO symbol,   // DTO type, not entity type
        @Name("indexId") String indexId,
        @Name("linesBefore") int linesBefore,
        @Name("linesAfter") int linesAfter) {
```
- **Severity**: P1
- **Current State**: BizModel 继承 CrudBizModel<NopCodeSymbol>，但 @BizLoader 使用 @ContextSource SymbolDTO（与 NopCodeSymbol 无继承关系的 @DataBean）。缺少 forType = SymbolDTO.class。NopCodeFileBizModel 正确使用了 forType。
- **Risk**: GraphQL 查询 NopCodeSymbol 上的 usages/sourceCode 字段时 ClassCastException。
- **Recommendation**: 添加 forType = SymbolDTO.class 或改用 @ContextSource NopCodeSymbol。
- **Confidence**: High
- **False Positive Exclusion**: @ContextSource 类型与 CrudBizModel 实体类型不匹配，Java 类型系统中不相关的类层次。
- **Review Status**: Not reviewed

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 11-01 | P1 | NopCodeSymbolBizModel.java | @BizLoader 缺少 forType，ContextSource 类型不匹配 |
