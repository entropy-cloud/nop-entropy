package io.nop.metadata.service.invariant;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import io.nop.metadata.service.search.NopMetaSearchBizModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Invariant INV-LIMIT guard (plan 2026-08-13-1930-2, Workstream B1).
 *
 * <p>Exhaustively checks every public entry method that accepts a {@code @Name("limit")}
 * parameter: passing {@code limit = -1} must throw a {@link NopException} with an
 * {@link io.nop.api.core.exceptions.ErrorCode} (explicit reject), not silently accept
 * the negative value.
 *
 * <p><b>Default surefire exclusion</b>: this class is excluded from the default
 * {@code ./mvnw test} run via {@code pom.xml} surefire {@code <excludes>}, so the
 * default build stays green. It is invoked by a separate documented command:
 * <pre>
 *   ./mvnw test -pl nop-metadata-service -Dtest=TestLimitNegativeValueInvariant
 * </pre>
 * Non-zero exit = at least one limit-taking method silently accepts negative limit
 * = the limit-component of the initial red list.
 *
 * <p>Method table source: I0 {@code audit-target-set.md} §1.4 — 4 limit-taking public
 * methods. Table completeness is independently verified by
 * {@link TestLimitTargetSetCompleteness} (runs in default surefire, always PASS unless
 * a new limit-taking method is added without updating this table).
 *
 * <p>Anti-Hollow: each test case actually invokes the limit-handling logic via reflection
 * on the real normalize methods (or the public method for inline checks). Methods that
 * silently accept negative limit will cause the test to FAIL — this is by design
 * (the FAIL IS the red-list entry, not a bug in the test).
 */
public class TestLimitNegativeValueInvariant {

    /**
     * Method table: every public entry method that accepts a {@code @Name("limit")} parameter.
     * Format: {class#method, limit-handler-type}
     *
     * <p>limit-handler-type:
     * <ul>
     *   <li>{@code normalizeQueryLimit} — NopMetaTableBizModel private method (queryTableData)</li>
     *   <li>{@code normalizeJoinQueryLimit} — NopMetaTableBizModel private method (queryJoinData/queryAggregation)</li>
     *   <li>{@code inline-searchMetadata} — NopMetaSearchBizModel public method (searchMetadata)</li>
     * </ul>
     */
    static Stream<Arguments> limitTakingMethods() {
        return Stream.of(
                Arguments.of("NopMetaTableBizModel#queryTableData", "normalizeQueryLimit"),
                Arguments.of("NopMetaTableBizModel#queryJoinData", "normalizeJoinQueryLimit"),
                Arguments.of("NopMetaTableBizModel#queryAggregation", "normalizeJoinQueryLimit"),
                Arguments.of("NopMetaSearchBizModel#searchMetadata", "inline-searchMetadata")
        );
    }

    @ParameterizedTest(name = "{0}: negative limit must be rejected with ErrorCode")
    @MethodSource("limitTakingMethods")
    void negativeLimitMustBeRejected(String methodLabel, String limitHandler) throws Throwable {
        assertThrows(NopException.class,
                () -> invokeLimitHandler(limitHandler),
                methodLabel + " must throw ErrorCode exception for negative limit, not silently accept");
    }

    /**
     * Invoke the limit-handling path with limit = -1 for the given handler type.
     * If the handler does NOT throw, the calling assertThrows will FAIL (red-list entry).
     */
    private void invokeLimitHandler(String limitHandler) throws Throwable {
        switch (limitHandler) {
            case "normalizeQueryLimit":
                invokeNormalize("normalizeQueryLimit", Long.valueOf(-1L));
                break;
            case "normalizeJoinQueryLimit":
                invokeNormalize("normalizeJoinQueryLimit", Long.valueOf(-1L));
                break;
            case "inline-searchMetadata":
                new NopMetaSearchBizModel().searchMetadata(null, null, Integer.valueOf(-1), null);
                break;
            default:
                throw new IllegalArgumentException("Unknown limit handler: " + limitHandler);
        }
    }

    /**
     * Invoke a private normalize method on NopMetaTableBizModel via reflection.
     * Unwraps InvocationTargetException to expose the original exception (NopMetadataException).
     */
    private void invokeNormalize(String methodName, Long limit) throws Throwable {
        NopMetaTableBizModel bizModel = new NopMetaTableBizModel();
        try {
            Method m = NopMetaTableBizModel.class.getDeclaredMethod(methodName, Long.class);
            m.setAccessible(true);
            m.invoke(bizModel, limit);
        } catch (InvocationTargetException e) {
            rethrow(e.getCause());
        }
    }

    private static void rethrow(Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, t);
    }
}
