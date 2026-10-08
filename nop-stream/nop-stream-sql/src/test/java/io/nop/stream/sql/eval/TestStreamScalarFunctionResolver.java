/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.sql.eval;

import io.nop.stream.flow.spi.IScalarFunctionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import io.nop.stream.core.exceptions.StreamException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WI30: the UDF SPI — eval channel (compileScalar dispatch) + static-call xpl form.
 */
public class TestStreamScalarFunctionResolver {

    private StreamScalarFunctionResolver resolver;

    @BeforeEach
    public void setUp() {
        resolver = new StreamScalarFunctionResolver();
        resolver.register("reverse", args -> args[0] == null ? null
                : new StringBuilder(String.valueOf(args[0])).reverse().toString());
        resolver.register("len", args -> args[0] == null ? 0
                : String.valueOf(args[0]).length());
        resolver.registerAsDefault();
    }

    @AfterEach
    public void tearDown() {
        StreamScalarFunctionResolver.setDefault(null);
    }

    @Test
    public void registeredFunctionEvaluates() {
        assertEquals("cba", eval("reverse(a)", rec("a", "abc")));
        assertEquals(3, ((Number) eval("len(a)", rec("a", "abc"))).intValue());
    }

    @Test
    public void nullArgumentPropagates() {
        assertNull(eval("reverse(a)", rec("a", null)));
        assertEquals(0, ((Number) eval("len(a)", rec("a", null))).intValue());
    }

    @Test
    public void unknownFunctionFailsFastListingRegistered() {
        StreamException e = assertThrows(StreamException.class,
                () -> eval("upper(a)", rec("a", 1)));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
        assertTrue(e.getMessage().contains("upper") || e.getMessage().contains("outside"),
                () -> e.getMessage());
    }

    @Test
    public void containsAndRegisteredNames() {
        assertTrue(resolver.contains("reverse"));
        assertTrue(!resolver.contains("no_such_fn"));
        List<String> names = resolver.namesList();
        assertTrue(names.contains("reverse") && names.contains("len"));
    }

    @Test
    public void udfInCompoundExpression() {
        assertEquals(4, ((Number) eval("len(reverse(a)) + 1", rec("a", "abc"))).intValue(),
                "UDF composed with scalar ops");
    }

    @Test
    public void staticCallEntryWorks() {
        assertEquals("cba", StreamScalarFunctionResolver.call("reverse", new Object[]{"abc"}));
    }

    private static Object eval(String expr, Map<String, Object> record) {
        return StreamSqlExprCompiler.compileScalar(
                io.nop.stream.sql.parse.EqlExprSupport.parseExpr(expr)).eval(record);
    }

    private static Map<String, Object> rec(Object... kv) {
        Map<String, Object> m = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void udfProjectionBodyContainsStaticCall() {
        // xpl channel: import hoisting + static call form
        io.nop.stream.sql.compile.SqlScalarXplPrinter printer =
                new io.nop.stream.sql.compile.SqlScalarXplPrinter(col -> "event['a']", null);
        StringBuilder sb = new StringBuilder();
        printer.printTo(io.nop.stream.sql.parse.EqlExprSupport.parseExpr("reverse(a)"), sb);
        assertTrue(sb.toString().contains("StreamScalarFunctionResolver.call('reverse'"),
                () -> "xpl body: " + sb);
        assertTrue(printer.getRequiredImports()
                .contains("io.nop.stream.sql.eval.StreamScalarFunctionResolver"),
                "the resolver class must be imported");
    }

    @Test
    public void closeIsIdempotent() {
        resolver.close();
        resolver.close(); // no throw
    }

    @Test
    public void spiInterfaceExposed() {
        IScalarFunctionResolver spi = resolver;
        assertTrue(spi.contains("reverse"));
    }
}
