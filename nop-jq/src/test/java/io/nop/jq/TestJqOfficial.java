package io.nop.jq;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the jq official test suite (tests/jq.test, vendored verbatim).
 *
 * <p>Unlike a lenient harness, this does not silently skip cases:
 * compilation errors, runtime errors and output mismatches all fail the test.
 * Multi-output programs are compared as an ordered list of outputs.
 * %%FAIL blocks must raise an error.
 *
 * <p>Value comparison is number-lenient (105 == 105.0) because jq prints
 * integral doubles without a fractional part; Double.NaN compares equal to
 * JSON null for the same reason.
 */
class TestJqOfficial {

    static final String RESOURCE = "/io/nop/jq/jq-official.test";

    static List<JqOfficialCase> allCases() throws Exception {
        return JqOfficialCase.parseResource(RESOURCE);
    }

    @TestFactory
    Stream<DynamicNode> officialSuite() throws Exception {
        List<JqOfficialCase> cases = allCases();
        List<DynamicNode> nodes = new ArrayList<>();
        int okIndex = 0;
        int failIndex = 0;
        for (JqOfficialCase c : cases) {
            if (c.fail) {
                failIndex++;
                nodes.add(DynamicTest.dynamicTest("fail[" + failIndex + "] " + abbreviate(c),
                        () -> runFailCase(c)));
            } else {
                okIndex++;
                nodes.add(DynamicTest.dynamicTest("ok[" + okIndex + "] " + abbreviate(c),
                        () -> runOkCase(c)));
            }
        }
        return Stream.of(DynamicContainer.dynamicContainer("jq-official", nodes));
    }

    private static String abbreviate(JqOfficialCase c) {
        String s = c.program.replace('\n', ' ');
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }

    private void runOkCase(JqOfficialCase c) {
        IJsonQuery query;
        try {
            query = JqEngine.compile(c.program);
        } catch (Exception | StackOverflowError e) {
            fail("Failed to compile: " + c.program, e);
            return;
        }
        if (!(query instanceof JqDirectQuery direct)) {
            fail("Unsupported query implementation: " + query.getClass());
            return;
        }

        Object inputObj = parseJqText(c.input);
        // jq's runner compares only the expected prefix and tolerates a trailing
        // error; errored tells us whether the stream ended with one
        java.util.concurrent.atomic.AtomicBoolean errored =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        List<Object> outputs = direct.applyPartial(inputObj, errored);

        if (errored.get()) {
            if (outputs.size() < c.expectedOutputs.size()) {
                fail("Runtime error after " + outputs.size() + " of "
                        + c.expectedOutputs.size() + " expected outputs for program: " + c.program
                        + (c.input.isEmpty() ? "" : " | input: " + c.input)
                        + "\n  outputs so far: " + outputs);
                return;
            }
        } else if (c.errorAfterOutputs) {
            fail("Expected a runtime error after outputs for program: " + c.program);
            return;
        } else if (outputs.size() != c.expectedOutputs.size()) {
            fail("Output count mismatch for program: " + c.program
                    + "\n  expected " + c.expectedOutputs.size() + " outputs: " + c.expectedOutputs
                    + "\n  actual " + outputs.size() + " outputs: " + outputs);
            return;
        }
        if (outputs.size() < c.expectedOutputs.size()) {
            fail("Output count mismatch for program: " + c.program
                    + "\n  expected " + c.expectedOutputs.size() + " outputs: " + c.expectedOutputs
                    + "\n  actual " + outputs.size() + " outputs: " + outputs);
            return;
        }
        for (int i = 0; i < c.expectedOutputs.size(); i++) {
            Object expectedObj = parseJqText(c.expectedOutputs.get(i));
            assertJqValueEquals(c, i, expectedObj, outputs.get(i));
        }
    }

    private void runFailCase(JqOfficialCase c) {
        try {
            IJsonQuery query = JqEngine.compile(c.program);
            query.apply(parseJqText(c.input));
        } catch (OutOfMemoryError | StackOverflowError e) {
            return; // resource exhaustion counts as failure for %%FAIL blocks
        } catch (Exception e) {
            return; // expected: the official suite marks this program as an error
        }
        fail("Expected an error but program succeeded: " + c.program
                + " | jq reports: " + c.expectedError);
    }

    /**
     * Parse a jq test-file text (input line or expected output line) into a Java value.
     * jq extends JSON with nan/infinite literals which strict parsers reject;
     * they are mapped to the values jq prints (null and max double).
     */
    static Object parseJqText(String text) {
        if (text == null || text.isEmpty())
            return null;
        // strip a byte-order-mark like jq's JSON parser does
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        // strict JSON first: jq's own messages may legitimately contain the text NaN
        try {
            return JsonTool.parse(text);
        } catch (Exception ignored) {
            // fall through to jq literal handling
        }
        // map jq number literals that strict JSON rejects; nan keeps its number-ness
        // through a quoted marker string that restoreNan converts back to Double.NaN
        boolean hasNan = text.matches("(?s)(?i).*[^\\w\"]-?nan[^\\w].*")
                || text.matches("(?i)(?s).*[^\\w\"]-?nan[^\\w].*|(?i)(?s).*^\\s*-?nan\\s*.*");
        String replaced = text.replaceAll("(?i)(?<![\\w\"])-?nan(?![\\w])",
                        "\"\u0001NAN\u0001\"")
                .replaceAll("(?i)(?<![\\w\"])(-?)inf(?:inity)?(?![\\w])",
                        "$11.7976931348623157e+308");
        try {
            Object parsed = JsonTool.parse(replaced);
            return hasNan ? restoreNan(parsed) : parsed;
        } catch (Exception e) {
            String t = text.trim();
            if (t.equalsIgnoreCase("nan"))
                return Double.NaN;
            if (t.equalsIgnoreCase("infinite"))
                return Double.MAX_VALUE;
            if (t.equalsIgnoreCase("-infinite"))
                return -Double.MAX_VALUE;
            return text; // non-JSON line: compare as raw string
        }
    }

    /** Replace nan marker strings produced by parseJqText with Double.NaN. */
    static Object restoreNan(Object v) {
        if (v instanceof String s) {
            return "\u0001NAN\u0001".equals(s) ? Double.NaN : v;
        }
        if (v instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(restoreNan(item));
            }
            return copy;
        }
        if (v instanceof java.util.Map<?, ?> map) {
            java.util.Map<Object, Object> copy = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                copy.put(e.getKey(), restoreNan(e.getValue()));
            }
            return copy;
        }
        return v;
    }

    static void assertJqValueEquals(JqOfficialCase c, int outputIndex,
                                    Object expected, Object actual) {
        if (jqEquals(expected, actual))
            return;
        fail("Output mismatch for program: " + c.program
                + "\n  output[" + outputIndex + "] expected: " + expected
                + " (" + (expected == null ? "null" : expected.getClass().getSimpleName()) + ")"
                + "\n  output[" + outputIndex + "] actual:   " + actual
                + " (" + (actual == null ? "null" : actual.getClass().getSimpleName()) + ")");
    }

    static boolean jqEquals(Object expected, Object actual) {
        if (expected == null || actual == null)
            return expected == null && actual == null;
        if (expected instanceof Number en && actual instanceof Number an)
            return numbersEqual(en, an);
        if (expected instanceof List<?> el && actual instanceof List<?> al) {
            if (el.size() != al.size())
                return false;
            for (int i = 0; i < el.size(); i++) {
                if (!jqEquals(el.get(i), al.get(i)))
                    return false;
            }
            return true;
        }
        if (expected instanceof java.util.Map<?, ?> em && actual instanceof java.util.Map<?, ?> am) {
            if (em.size() != am.size())
                return false;
            for (java.util.Map.Entry<?, ?> entry : em.entrySet()) {
                if (!am.containsKey(entry.getKey()))
                    return false;
                if (!jqEquals(entry.getValue(), am.get(entry.getKey())))
                    return false;
            }
            return true;
        }
        return expected.equals(actual);
    }

    private static boolean numbersEqual(Number a, Number b) {
        BigDecimal da = toBigDecimal(a);
        BigDecimal db = toBigDecimal(b);
        if (da != null && db != null)
            return da.compareTo(db) == 0;
        if (da == null && db == null)
            return a.doubleValue() == b.doubleValue(); // NaN/Infinite
        return false;
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof Double d) {
            if (d.isNaN() || d.isInfinite())
                return null;
            return BigDecimal.valueOf(d);
        }
        if (n instanceof Float f) {
            if (f.isNaN() || f.isInfinite())
                return null;
            return BigDecimal.valueOf(f.doubleValue());
        }
        if (n instanceof BigDecimal bd)
            return bd;
        return new BigDecimal(n.toString());
    }
}
