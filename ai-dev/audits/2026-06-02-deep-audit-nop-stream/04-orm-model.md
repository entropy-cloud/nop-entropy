# Dimension 04: ORM Model & Entity Design — nop-stream Audit Report

## Context

nop-stream is a **streaming computation engine** (similar to Apache Flink), not a standard Nop CRUD business module. Zero `.orm.xml` files found across all submodules — this is correct and expected.

## 第 1 轮（初审）

### [维度04-01] Duplicated fields between `_CepPatternModel` and `_CepPatternGroupModel` — not unified through interface

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/_gen/_CepPatternModel.java:26-54` and `_CepPatternGroupModel.java:24-45`
- **Evidence snippet**:
```java
// _CepPatternModel.java (lines 26-54)
    private io.nop.stream.cep.model.AfterMatchSkipStrategyKind _afterMatchSkipStrategy ;
    private java.lang.String _afterMatchSkipTo ;
    private KeyedList<io.nop.stream.cep.model.CepPatternPartModel> _parts = KeyedList.emptyList();
    private java.lang.String _start ;

// _CepPatternGroupModel.java (lines 24-45) — identical fields repeated
    private io.nop.stream.cep.model.AfterMatchSkipStrategyKind _afterMatchSkipStrategy ;
    private java.lang.String _afterMatchSkipTo ;
    private KeyedList<io.nop.stream.cep.model.CepPatternPartModel> _parts = KeyedList.emptyList();
    private java.lang.String _start ;
```
- **Severity**: P2
- **Current state**: Three fields (`_afterMatchSkipStrategy`, `_afterMatchSkipTo`, `_start`) plus `_parts` are independently declared in both `_CepPatternModel` and `_CepPatternGroupModel`, even though both classes implement the same `ICepPatternGroupModel` interface. Generated from `pattern.xdef` where both elements define the same attributes independently.
- **Risk**: Field duplication means any future change must be duplicated or regenerated identically. Minor maintenance cost, not a runtime bug.
- **Recommendation**: Consequence of XDSL schema design. If considered undesirable, refactor XDEF to use shared attribute group. Since files are generated, no immediate action required.
- **Confidence**: Certain
- **False positive exclusion**: Not about normal getter/setter pairs — specifically about three identical field+accessor sets generated independently in two unrelated class hierarchies.
- **Review status**: Unreviewed

### [维度04-02] `gapWithin` field exists only on `CepPatternModel` but is not exposed via `ICepPatternGroupModel` interface

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/ICepPatternGroupModel.java:18-36`
- **Evidence snippet**:
```java
// ICepPatternGroupModel.java — no gapWithin getter
public interface ICepPatternGroupModel extends ISourceLocationGetter {
    List<CepPatternPartModel> getParts();
    String getStart();
    AfterMatchSkipStrategyKind getAfterMatchSkipStrategy();
    String getAfterMatchSkipTo();
    // Missing: Duration getGapWithin();
}
```
- **Severity**: P2
- **Current state**: The `gapWithin` property is declared only in `_CepPatternModel` (root pattern level) but not in the `ICepPatternGroupModel` interface. The interface does not fully capture the semantic distinction between root pattern and nested groups.
- **Risk**: If a future developer adds `gapWithin` support to `CepPatternGroupModel`, they would have to either extend the interface or cast. Currently no runtime error.
- **Recommendation**: Consider adding `getGapWithin()` to `ICepPatternGroupModel` with default `return null`, OR create a more specific `ICepPatternRootModel` extending it. Low priority.
- **Confidence**: Very likely
- **False positive exclusion**: About a semantic gap in the hand-written interface contract where `gapWithin` is accessible only through the concrete class, not the abstraction.
- **Review status**: Unreviewed

### [维度04-03] Discriminator `_type` field dead code due to hand-written overrides

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/_gen/_CepPatternPartModel.java:17` vs `CepPatternPartModel.java:12-18` vs `_CepPatternGroupModel.java:52-53` and `_CepPatternSingleModel.java:24`
- **Evidence snippet**:
```java
// CepPatternPartModel.java (hand-written) — adds abstract getType():
public abstract class CepPatternPartModel extends _CepPatternPartModel {
    public abstract String getType();
}

// _CepPatternGroupModel.java (generated) — has _type field + getter:
    private java.lang.String _type ;
    public java.lang.String getType(){ return _type; }

// CepPatternGroupModel.java (hand-written) — overrides with hardcoded "group":
    public String getType() { return "group"; }
    @Override
    public void setType(String type) { }  // no-op
```
- **Severity**: P2
- **Current state**: The `_type` field is generated independently in both subclasses but NOT in their common base. The hand-written overrides make the generated `_type` field dead code. `setType()` is a no-op.
- **Risk**: If someone calls `setType("foo")` on a `CepPatternGroupModel`, the field is set in the generated base but `getType()` always returns `"group"`. Maintenance trap, not a runtime bug.
- **Recommendation**: Consequence of XDSL `bean-sub-type-prop="type"`. The no-op `setType()` methods should at minimum have a comment explaining why they discard the value.
- **Confidence**: Certain
- **False positive exclusion**: Specifically about the interaction between XDSL-generated discriminator fields and hand-written overrides that create dead generated fields.
- **Review status**: Unreviewed

### [维度04-04] `StreamComponents` uses raw `Map<String, Object>` for transforms, streams, windowing strategies

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamComponents.java:26-33`
- **Evidence snippet**:
```java
    private final Map<String, Object> transforms;
    private final Map<String, Object> streams;
    private final Map<String, Object> windowingStrategies;
    private final Map<String, Object> coders;
    private final Map<String, Object> schemas;
    private final Map<String, Object> environments;
    private final Map<String, Object> sideInputs;

    public void registerTransform(String id, Object transform) {
        transforms.put(id, transform);
    }
    public Object getTransform(String id) {
        return transforms.get(id);
    }
```
- **Severity**: P2
- **Current state**: Central registry for streaming pipeline components stores all as `Map<String, Object>`. Any type mismatch is only caught at runtime.
- **Risk**: No compile-time safety for component registration/retrieval. If a transform is stored under a wrong key or type, the error manifests as a runtime `ClassCastException`.
- **Recommendation**: Parameterize maps where possible (e.g., `Map<String, Transformation<?>>`). Alternatively, add runtime type-checking in `registerXxx()` methods.
- **Confidence**: Very likely
- **False positive exclusion**: Not about standard Java generic erasure — about a core domain model using `Object` when more specific types are already available.
- **Review status**: Unreviewed

### [维度04-05] `CepPatternModel` implements `ICepPatternGroupModel` but adds `within` and `gapWithin` without interface exposure

- **File**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/CepPatternModel.java:12-16` and `CepPatternBuilder.java:29-36`
- **Evidence snippet**:
```java
// CepPatternBuilder.java — takes CepPatternModel, not ICepPatternGroupModel:
    public Pattern buildFromModel(CepPatternModel patternModel) {
        Pattern pattern = buildGroupPattern(patternModel);
        if (patternModel.getWithin() != null)
            pattern = pattern.within(patternModel.getWithin());
        if (patternModel.getGapWithin() != null)
            pattern = pattern.within(patternModel.getGapWithin(), WithinType.PREVIOUS_AND_CURRENT);
        return pattern;
    }
```
- **Severity**: P3
- **Current state**: Builder coupled to concrete `CepPatternModel` rather than interface. Deliberate design since within/gapWithin only apply to root pattern.
- **Risk**: Builder cannot be reused with mock/alternative implementation. Low practical risk.
- **Recommendation**: Acceptable as-is. Low priority.
- **Confidence**: Certain
- **False positive exclusion**: About the root-level builder entry point being coupled to concrete class when interface already exists for recursive logic.
- **Review status**: Unreviewed
