/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.spi.IAggregatorFunctionResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8c: the sql-side resolver semantics plus the two anti-hollow pins —
 * (1) the app-beans registration really auto-assembles through the Nop module
 * mechanism (full initialization + lookup by type), and (2) the builtin id set is
 * exactly the five aggregate keywords of BaseRule.g4 (MAX|MIN|SUM|COUNT|AVG).
 */
public class TestStreamAggregatorFunctionResolver {

    private static final Function<String, BasicTypeInfo<?>> NO_SCHEMA = c -> null;

    @BeforeAll
    public static void init() {
        // FULL initialization (not initializeTo(IOC-1)): the auto-assembly proof needs
        // the app container that discovers module _module markers and app-*.beans.xml.
        // The dao-defaults Hikari datasource then needs JDBC config vars — supply the
        // same H2 in-memory values the autotest localDb form uses (D15 default).
        System.setProperty("nop.datasource.driver-class-name", "org.h2.Driver");
        System.setProperty("nop.datasource.jdbc-url", "jdbc:h2:mem:" + java.util.UUID.randomUUID()
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        System.setProperty("nop.datasource.username", "sa");
        System.setProperty("nop.datasource.password", "");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
        System.clearProperty("nop.datasource.driver-class-name");
        System.clearProperty("nop.datasource.jdbc-url");
        System.clearProperty("nop.datasource.username");
        System.clearProperty("nop.datasource.password");
    }

    @Test
    public void resolverAutoAssemblesThroughModuleMechanism() {
        // Anti-hollow pin for the app-aggregator.beans.xml registration: with the sql
        // module on the classpath and a FULL IoC startup, the SPI is discoverable by
        // type without any manual container wiring.
        IAggregatorFunctionResolver resolver =
                BeanContainer.instance().tryGetBeanByType(IAggregatorFunctionResolver.class);
        assertNotNull(resolver,
                "the module app-beans registration must auto-assemble the resolver");
        assertTrue(resolver instanceof StreamAggregatorFunctionResolver,
                "the registered provider is the sql-side implementation");
    }

    @Test
    public void builtinIdsAlignWithGrammarKeywords() {
        // BaseRule.g4:273 sqlIdentifier_agg_ : MAX|MIN|SUM|COUNT|AVG — the catalog is
        // the same closed set (lowercase, matching the parser's normalized names).
        Set<String> expected = new HashSet<>(Arrays.asList("sum", "count", "avg", "min", "max"));
        assertEquals(expected, StreamSqlAggregations.builtinIds());
    }

    @Test
    public void unknownFnIdFailsFast() {
        IAggregatorFunctionResolver resolver = resolver();
        StreamException e = assertThrows(StreamException.class,
                () -> resolver.resolve("median", "amount", NO_SCHEMA));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
        assertTrue(e.getMessage().contains("median"), () -> "names the bad id: " + e.getMessage());
    }

    @Test
    public void exprCompileFailureFailsFast() {
        IAggregatorFunctionResolver resolver = resolver();
        // CASE is outside the v1 scalar subset
        StreamException e = assertThrows(StreamException.class,
                () -> resolver.resolve("sum", "case when a then 1 else 2 end", NO_SCHEMA));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
    }

    @Test
    public void missingArgumentOnNonCountFailsFast() {
        IAggregatorFunctionResolver resolver = resolver();
        StreamException e = assertThrows(StreamException.class,
                () -> resolver.resolve("sum", null, NO_SCHEMA));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
        assertTrue(e.getMessage().contains("exactly one argument"),
                () -> "names the arity rule: " + e.getMessage());
    }

    @Test
    public void countWithoutArgumentIsLegal() {
        IAggregatorFunctionResolver resolver = resolver();
        AggregateFunction<Object, Object, Object> fn = resolver.resolve("count", null, NO_SCHEMA);
        Object acc = fn.createAccumulator();
        acc = fn.add(new HashMap<>(), acc);
        acc = fn.add(new HashMap<>(), acc);
        assertEquals(2L, fn.getResult(acc));
    }

    @Test
    public void sumOfNonNumericColumnFailsFastWithSchema() {
        IAggregatorFunctionResolver resolver = resolver();
        Map<String, BasicTypeInfo<?>> columns = new HashMap<>();
        columns.put("name", BasicTypeInfo.STRING);
        StreamException e = assertThrows(StreamException.class,
                () -> resolver.resolve("sum", "name", columns::get));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
        assertTrue(e.getMessage().contains("numeric"), () -> "names the type rule: " + e.getMessage());
    }

    @Test
    public void maxOfStringColumnIsLegal() {
        IAggregatorFunctionResolver resolver = resolver();
        Map<String, BasicTypeInfo<?>> columns = new HashMap<>();
        columns.put("name", BasicTypeInfo.STRING);
        AggregateFunction<Object, Object, Object> fn = resolver.resolve("max", "name", columns::get);
        Map<String, Object> r1 = new HashMap<>();
        r1.put("name", "b");
        Map<String, Object> r2 = new HashMap<>();
        r2.put("name", "a");
        Object acc = fn.add(r2, fn.add(r1, fn.createAccumulator()));
        assertEquals("b", fn.getResult(acc));
    }

    @Test
    public void unknownColumnTypesSkipTypeCheck() {
        // no schema declared → column types unknown → validation skipped, not failed
        IAggregatorFunctionResolver resolver = resolver();
        AggregateFunction<Object, Object, Object> fn = resolver.resolve("sum", "amount", NO_SCHEMA);
        assertNotNull(fn);
    }

    @Test
    public void resolvedFunctionCarriesRealSemantics() {
        // end-to-end through the SPI: the returned function IS the WI9 accumulator
        IAggregatorFunctionResolver resolver = resolver();
        AggregateFunction<Object, Object, Object> fn = resolver.resolve("sum", "amount", NO_SCHEMA);
        Object acc = fn.createAccumulator();
        acc = fn.add(row("amount", 2), acc);
        acc = fn.add(row("amount", 3), acc);
        acc = fn.add(row("amount", null), acc);
        assertEquals(5L, fn.getResult(acc));
    }

    private IAggregatorFunctionResolver resolver() {
        IAggregatorFunctionResolver resolver =
                BeanContainer.instance().tryGetBeanByType(IAggregatorFunctionResolver.class);
        assertNotNull(resolver, "resolver must be auto-assembled for these tests");
        return resolver;
    }

    private static Map<String, Object> row(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }
}
