package io.nop.xlang.compare;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * corpus 单元的静态/动态类别，及其列适用性（设计 execution 01 §五）：
 * 静态单元三列（解释器/java/truffle），动态单元两列（解释器/truffle）。
 */
public enum CompareUnitKind {
    STATIC(CompareBackendIds.INTERPRETER, CompareBackendIds.JAVA, CompareBackendIds.TRUFFLE),
    DYNAMIC(CompareBackendIds.INTERPRETER, CompareBackendIds.TRUFFLE);

    private final Set<String> expectedBackendIds;

    CompareUnitKind(String... expectedBackendIds) {
        this.expectedBackendIds = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(expectedBackendIds)));
    }

    /**
     * 该类别单元在对拍矩阵中的期望列集合。缺席的期望列被显式记 skipped（不算通过）。
     */
    public Set<String> getExpectedBackendIds() {
        return expectedBackendIds;
    }
}
