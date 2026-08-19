package io.nop.xlang.compare;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * corpus 单元（I2/I5 的消费契约），四字段：源 / 初始求值现场声明 / 预期 / 列适用性。
 *
 * <p>源：静态单元 = 测试资源路径（xpl 编译单元，经标准编译前端取树）；动态单元 = 表达式串（经动态编译出口取树）。
 * 初始求值现场声明：输入变量名与值，harness 为每列独立新建现场并等价初始化。
 * 预期：{@link ExpectedOutcome}。
 * 列适用性：由 {@link CompareUnitKind} 派生（静态三列 / 动态两列）。
 */
public final class CompareUnit {
    private final String name;
    private final CompareUnitKind kind;
    private final String source;
    private final String sourceLocationPath;
    private final Map<String, Object> inputVars;
    private final ExpectedOutcome expectation;

    private final String category;

    public CompareUnit(String name, CompareUnitKind kind, String source, String sourceLocationPath,
                       Map<String, Object> inputVars, ExpectedOutcome expectation, String category) {
        this.name = name;
        this.kind = kind;
        this.source = source;
        this.sourceLocationPath = sourceLocationPath;
        this.inputVars = inputVars == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(inputVars));
        this.expectation = expectation;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public CompareUnitKind getKind() {
        return kind;
    }

    /**
     * 静态单元为测试资源路径；动态单元为表达式串。
     */
    public String getSource() {
        return source;
    }

    /**
     * 编译时使用的合成/资源 SourceLocation path（异常层源位置断言依据）。
     */
    public String getSourceLocationPath() {
        return sourceLocationPath;
    }

    public Map<String, Object> getInputVars() {
        return inputVars;
    }

    public ExpectedOutcome getExpectation() {
        return expectation;
    }

    /**
     * 单元所属的表达式子集类别（字面量/slot 标识符/算术/逻辑/比较/简单方法调用/组合）。
     */
    public String getCategory() {
        return category;
    }
}
