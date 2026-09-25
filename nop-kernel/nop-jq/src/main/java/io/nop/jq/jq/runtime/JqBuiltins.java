package io.nop.jq.jq.runtime;

import io.nop.jq.jq.ast.FuncCallNode;
import io.nop.jq.jq.ast.JqAstNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Built-in jq functions. Special forms (limit/first/any/all/isempty/path/del)
 * evaluate argument ASTs lazily; everything else evaluates arguments eagerly
 * with the current input, as jq does.
 */
final class JqBuiltins {

    private JqBuiltins() {
    }

    static void call(JqExecutor executor, FuncCallNode n, JqValue input,
                     JqEnvironment env, List<JqValue> outputs) {
        String name = n.name();
        List<JqAstNode> args = n.args();

        // --- lazy special forms (need argument ASTs) ---
        switch (name) {
            case "limit" -> {
                requireArgs(n, 2);
                for (JqValue count : executor.execute(args.get(0), input, env)) {
                    long n2 = requireNumber(count, "limit").longValue();
                    if (n2 < 0) {
                        throw new JqRuntimeException("limit cannot be negative");
                    }
                    List<JqValue> taken = take(executor, args.get(1), input, env,
                            list -> list.size() >= n2);
                    outputs.addAll(taken);
                }
                return;
            }
            case "first" -> {
                if (args.isEmpty()) {
                    // first/0: first element of the input array
                    if (input instanceof JqArray arr && !arr.isEmpty())
                        outputs.add(arr.get(0));
                    return;
                }
                outputs.addAll(take(executor, args.get(0), input, env,
                        list -> list.size() >= 1));
                return;
            }
            case "last" -> {
                if (args.isEmpty()) {
                    if (input instanceof JqArray arr && !arr.isEmpty())
                        outputs.add(arr.get(arr.size() - 1));
                    return;
                }
                List<JqValue> all = executor.execute(args.get(0), input, env);
                if (!all.isEmpty())
                    outputs.add(all.get(all.size() - 1));
                return;
            }
            case "nth" -> {
                if (args.isEmpty()) {
                    throw new JqRuntimeException("nth/1 and nth/2 are defined");
                }
                if (args.size() == 1) {
                    // nth/1: nth element of the input array
                    for (JqValue idx : executor.execute(args.get(0), input, env)) {
                        long i = requireNumber(idx, "nth").longValue();
                        if (i < 0)
                            throw new JqRuntimeException("nth doesn't support negative indices");
                        if (input instanceof JqArray arr && i < arr.size())
                            outputs.add(arr.get((int) i));
                    }
                    return;
                }
                for (JqValue idx : executor.execute(args.get(0), input, env)) {
                    long i = requireNumber(idx, "nth").longValue();
                    if (i < 0)
                        throw new JqRuntimeException("nth doesn't support negative indices");
                    List<JqValue> taken = take(executor, args.get(1), input, env,
                            list -> list.size() >= i + 1);
                    if ((int) i < taken.size())
                        outputs.add(taken.get((int) i));
                }
                return;
            }
            case "isempty" -> {
                requireArgs(n, 1);
                List<JqValue> probe = take(executor, args.get(0), input, env,
                        list -> list.size() >= 1);
                outputs.add(JqBoolean.of(probe.isEmpty()));
                return;
            }
            case "any" -> {
                if (args.size() == 2) {
                    outputs.add(JqBoolean.of(scanGenerator(executor, args.get(0),
                            args.get(1), input, env, true)));
                    return;
                }
                anyAll(executor, n, input, env, outputs, true);
                return;
            }
            case "all" -> {
                if (args.size() == 2) {
                    outputs.add(JqBoolean.of(scanGenerator(executor, args.get(0),
                            args.get(1), input, env, false)));
                    return;
                }
                anyAll(executor, n, input, env, outputs, false);
                return;
            }
            case "path" -> {
                requireArgs(n, 1);
                JqPathEval pathEval = new JqPathEval(executor::executeInto);
                for (List<Object> path : pathEval.evalPaths(args.get(0), input, env)) {
                    outputs.add(new JqArray((List<JqValue>) (List<?>) path));
                }
                return;
            }
            case "del" -> {
                JqPathEval pathEval = new JqPathEval(executor::executeInto);
                List<List<Object>> allPaths = new ArrayList<>();
                for (JqAstNode arg : args) {
                    allPaths.addAll(pathEval.evalPaths(arg, input, env));
                }
                allPaths.sort(JqPathEval::compareObjectPaths);
                // delete deepest/last paths first so earlier deletions don't shift indices
                for (int i = allPaths.size() - 1; i >= 0; i--) {
                    input = JqPathEval.deleteAt(input, allPaths.get(i), 0);
                }
                outputs.add(input);
                return;
            }
            case "until" -> {
                requireArgs(n, 2);
                JqValue state = input;
                int guard = 0;
                while (!JqTruthiness.of(first(executor.execute(args.get(0), state, env)))) {
                    if (++guard > JqEnvironment.MAX_CALL_DEPTH)
                        throw new JqRuntimeException("until did not converge");
                    state = first(executor.execute(args.get(1), state, env));
                }
                outputs.add(state);
                return;
            }
            case "while" -> {
                requireArgs(n, 2);
                JqValue state = input;
                while (JqTruthiness.of(first(executor.execute(args.get(0), state, env)))) {
                    env.checkOutputLimit();
                    outputs.add(state);
                    state = first(executor.execute(args.get(1), state, env));
                }
                return;
            }
            case "repeat" -> {
                requireArgs(n, 1);
                JqValue state = input;
                while (true) {
                    List<JqValue> next = take(executor, args.get(0), state, env,
                            list -> list.size() >= 1);
                    if (next.isEmpty())
                        return;
                    state = next.get(0);
                    env.checkOutputLimit();
                    outputs.add(state);
                }
            }
            case "error" -> {
                if (args.isEmpty()) {
                    throw errorFor(executor, input);
                }
                List<JqValue> msgVals = executor.execute(args.get(0), input, env);
                throw errorFor(executor, msgVals.isEmpty() ? JqValue.NULL : msgVals.get(0));
            }
            default -> {
                // not a special form
            }
        }

        // --- user-defined functions shadow plain builtins (resolved by name/arity) ---
        String fnKey = JqExecutor.functionKey(name, args.size());
        if (!isPlainBuiltin(name) && env.hasFunction(fnKey)) {
            executor.callFunctionFromBuiltin(fnKey, args, input, env, outputs);
            return;
        }

        // --- plain eager builtins ---
        dispatchPlain(executor, n, input, env, outputs);
    }

    /** ASCII-only case mapping, like jq (é etc. are left untouched). */
    private static String asciiCase(String s, boolean up) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (up && c >= 'a' && c <= 'z')
                c = (char) (c - 'a' + 'A');
            else if (!up && c >= 'A' && c <= 'Z')
                c = (char) (c - 'A' + 'a');
            sb.append(c);
        }
        return sb.toString();
    }

    /** Names reported by the builtins builtin (name/arity), matching jq's format. */
    private static final String[] BUILTIN_NAMES = {
            "abs/0", "add/0", "all/0", "all/1", "all/2", "any/0", "any/1", "any/2",
            "ascii_downcase/0", "ascii_upcase/0", "asin/0", "acos/0", "atan/0",
            "atan2/2", "bsearch/1", "builtins/0", "capture/1", "capture/2",
            "ceil/0", "combinations/0", "contains/1", "del/1", "del/2", "delpaths/1",
            "env/0", "error/0", "error/1", "explode/0", "exp/0", "exp2/0", "exp10/0",
            "fabs/0", "first/0", "first/1", "flatten/0", "flatten/1", "floor/0",
            "fromdate/0", "fromjson/0", "fromstream/1", "getpath/1", "gmtime/0",
            "group_by/1", "halt/0", "halt_error/0", "halt_error/1", "has/1", "has/2",
            "implode/0", "in/1", "in/2", "index/1", "index/2", "indices/1", "indices/2",
            "infinite/0", "input/0", "input_line_number/0", "inputs/0", "inside/1",
            "isempty/1", "isinfinite/0", "isnan/0", "isnormal/0", "join/1", "join/2",
            "keys/0", "keys_unsorted/0", "last/0", "last/1", "length/0", "limit/2",
            "localtime/0", "log/0", "log10/0", "log2/0", "ltrimstr/1", "map/1",
            "map_values/1", "match/1", "match/2", "max/0", "max_by/1", "min/0",
            "min_by/1", "mktime/0", "modulemeta/0", "nan/0", "nl/0", "not/0",
            "now/0", "nth/1", "nth/2", "path/1", "paths/0", "paths/1", "pick/1",
            "pow/2", "range/1", "range/2", "range/3", "recurse/0", "recurse/1",
            "reverse/0", "rindex/1", "rindex/2", "rtrimstr/1", "scalars/0",
            "scan/1", "scan/2", "select/1", "setpath/2", "sort/0", "sort_by/1",
            "splits/1", "splits/2", "split/1", "split/2", "sqrt/0", "strftime/1",
            "strptime/1", "strflocaltime/1", "startswith/1", "endswith/1",
            "test/1", "test/2", "to_entries/0", "tojson/0", "tonumber/0",
            "tostream/0", "tostring/0", "todate/0", "transpose/0", "trim/0",
            "type/0", "unique/0", "unique_by/1", "until/2", "unutf8/0", "utf8bytelength/0",
            "values/0", "walk/1", "weights/0", "while/2", "with_entries/1"
    };

    /**
     * jq string repetition: s * n repeats s floor(n) times; a negative or NaN
     * count yields null. Returns null when the operands are not (string, number).
     */
    private static JqValue repeatString(JqValue a, JqValue b) {
        if (a instanceof JqString s && b instanceof JqNumber n) {
            double count = n.doubleValue();
            if (Double.isNaN(count) || count < 0)
                return JqValue.NULL;
            StringBuilder sb = new StringBuilder();
            for (double i = 0; i < count; i += 1)
                sb.append(s.value());
            return JqString.of(sb.toString());
        }
        return null;
    }

    private static JqValue first(List<JqValue> vals) {
        return vals.isEmpty() ? JqValue.NULL : vals.get(0);
    }

    /** Evaluate expr collecting outputs until the stop condition holds. */
    static List<JqValue> take(JqExecutor executor, JqAstNode expr, JqValue input,
                              JqEnvironment env, Predicate<List<JqValue>> stopWhen) {
        JqShortCircuitList sink = new JqShortCircuitList(stopWhen);
        try {
            executor.executeInto(expr, input, env, sink);
        } catch (JqStopException e) {
            // consumer satisfied
        }
        return sink;
    }

    /** any/2, all/2 generator scan: returns whether the scan short-circuited. */
    private static boolean scanGenerator(JqExecutor executor, JqAstNode gen, JqAstNode cond,
                                         JqValue input, JqEnvironment env, boolean anyMode) {
        boolean[] found = {false};
        JqShortCircuitList sink = new JqShortCircuitList(list -> {
            if (list.isEmpty())
                return false;
            JqValue item = list.get(list.size() - 1);
            boolean truthy = !executor.execute(cond, item, env).isEmpty()
                    && JqTruthiness.of(first(executor.execute(cond, item, env)));
            return anyMode == truthy;
        });
        try {
            executor.executeInto(gen, input, env, sink);
        } catch (JqStopException e) {
            found[0] = true;
        }
        return anyMode ? found[0] : !found[0];
    }

    private static void anyAll(JqExecutor executor, FuncCallNode n, JqValue input,
                               JqEnvironment env, List<JqValue> outputs, boolean anyMode) {
        List<JqValue> items;
        if (n.args().isEmpty()) {
            items = input instanceof JqArray arr ? arr.items() : List.of(input);
        } else if (input instanceof JqArray arr) {
            items = arr.items();
        } else {
            items = List.of(input);
        }
        boolean result = anyMode ? false : true;
        for (JqValue item : items) {
            boolean truthy;
            if (n.args().isEmpty()) {
                truthy = JqTruthiness.of(item);
            } else {
                List<JqValue> vals = executor.execute(n.args().get(0), item, env);
                truthy = !vals.isEmpty() && JqTruthiness.of(vals.get(0));
            }
            if (anyMode == truthy) {
                result = truthy;
                break;
            }
        }
        if (!anyMode && n.args().isEmpty() && n.args().size() == 1) {
            result = true; // unreachable; kept for clarity
        }
        outputs.add(JqBoolean.of(result));
    }

    // ===== plain builtins =====

    private static void dispatchPlain(JqExecutor executor, FuncCallNode n, JqValue input,
                                      JqEnvironment env, List<JqValue> outputs) {
        String name = n.name();
        List<JqAstNode> args = n.args();
        switch (name) {
            case "length" -> outputs.add(lengthOf(input));
            case "utf8bytelength" -> {
                if (!(input instanceof JqString s)) {
                    throw new JqRuntimeException(input.typeName() + " ("
                            + JqPrinter.print(input) + ") only strings have UTF-8 byte length");
                }
                outputs.add(JqNumber.of(s.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
            }
            case "keys", "keys_unsorted" -> {
                if (input instanceof JqObject obj) {
                    java.util.List<String> keys = new ArrayList<>(obj.keySet());
                    if (name.equals("keys"))
                        java.util.Collections.sort(keys);
                    List<JqValue> result = new ArrayList<>(keys.size());
                    for (String key : keys)
                        result.add(JqString.of(key));
                    outputs.add(new JqArray(result));
                } else if (input instanceof JqArray arr) {
                    List<JqValue> result = new ArrayList<>(arr.size());
                    for (int i = 0; i < arr.size(); i++)
                        result.add(JqNumber.of(i));
                    outputs.add(new JqArray(result));
                } else {
                    throw new JqRuntimeException(input.typeName() + " ("
                            + JqPrinter.print(input) + ") has no keys");
                }
            }
            case "values" -> {
                // jq: def values: select(. != null)
                if (!(input instanceof JqNull))
                    outputs.add(input);
            }
            case "type" -> outputs.add(JqString.of(input.typeName()));
            case "not" -> outputs.add(JqBoolean.of(!JqTruthiness.of(input)));
            case "empty" -> {
            }
            case "tostring" -> outputs.add(JqString.of(JqPrinter.tostring(input)));
            case "tojson" -> outputs.add(JqString.of(JqPrinter.print(input)));
            case "fromjson" -> {
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, "fromjson input must be a string");
                }
                String val = s.value().trim();
                if (val.startsWith("nan")) {
                    outputs.add(JqNumber.of(Double.NaN));
                    break;
                }
                try {
                    Object parsed = io.nop.core.lang.json.JsonTool.parse(val);
                    outputs.add(JqValue.of(parsed));
                } catch (JqBreakException | JqStopException e) {
                    throw e;
                } catch (Exception e) {
                    throw new JqRuntimeException(s.value() + " cannot be parsed as JSON: "
                            + e.getMessage());
                }
            }
            case "tonumber" -> {
                if (input instanceof JqNumber num) {
                    outputs.add(num);
                } else if (input instanceof JqString s) {
                    try {
                        String text = s.value().trim();
                        double d = Double.parseDouble(text);
                        if (d == Math.floor(d) && !text.contains(".")
                                && !text.contains("e") && !text.contains("E")
                                && Math.abs(d) < 9.2e18) {
                            outputs.add(JqNumber.of(Long.parseLong(text)));
                        } else {
                            outputs.add(JqNumber.of(d));
                        }
                    } catch (NumberFormatException e) {
                        throw new JqRuntimeException("Cannot parse '" + s.value()
                                + "' as number");
                    }
                } else {
                    throw new JqRuntimeException(input.typeName() + " ("
                            + JqPrinter.print(input) + ") cannot be parsed as a number");
                }
            }
            case "toboolean" -> {
                if (input instanceof JqBoolean b) {
                    outputs.add(b);
                } else if (input instanceof JqString s) {
                    String val = s.value();
                    if ("true".equals(val))
                        outputs.add(JqBoolean.TRUE);
                    else if ("false".equals(val))
                        outputs.add(JqBoolean.FALSE);
                    else
                        throw new JqRuntimeException("string (\""
                                + s.value() + "\") cannot be parsed as a boolean");
                } else {
                    throw new JqRuntimeException(input.typeName() + " ("
                            + JqPrinter.print(input) + ") cannot be parsed as a boolean");
                }
            }
            case "ascii_downcase" -> {
                requireString(n, input);
                outputs.add(JqString.of(asciiCase(((JqString) input).value(), false)));
            }
            case "ascii_upcase" -> {
                requireString(n, input);
                outputs.add(JqString.of(asciiCase(((JqString) input).value(), true)));
            }
            case "ltrimstr", "rtrimstr" -> {
                if (!(input instanceof JqString s)) {
                    outputs.add(input); // non-string input passes through unchanged
                    break;
                }
                for (JqValue arg : evalArgs(executor, args, input, env)) {
                    if (!(arg instanceof JqString str)) {
                        throw new JqRuntimeException(name + "() requires string inputs");
                    }
                    String value = s.value();
                    if (name.equals("ltrimstr")) {
                        outputs.add(JqString.of(value.startsWith(str.value())
                                ? value.substring(str.value().length()) : value));
                    } else {
                        outputs.add(JqString.of(value.endsWith(str.value())
                                ? value.substring(0, value.length() - str.value().length())
                                : value));
                    }
                }
            }
            case "startswith" -> {
                requireArgs(n, 1);
                requireString(n, input);
                for (JqValue arg : evalArgs(executor, args, input, env)) {
                    if (!(arg instanceof JqString str)) {
                        throw new JqRuntimeException("startswith() requires string inputs");
                    }
                    outputs.add(JqBoolean.of(((JqString) input).value().startsWith(str.value())));
                }
            }
            case "endswith" -> {
                requireArgs(n, 1);
                requireString(n, input);
                for (JqValue arg : evalArgs(executor, args, input, env)) {
                    if (!(arg instanceof JqString str)) {
                        throw new JqRuntimeException("endswith() requires string inputs");
                    }
                    outputs.add(JqBoolean.of(((JqString) input).value().endsWith(str.value())));
                }
            }
            case "explode" -> {
                requireString(n, input);
                String value = ((JqString) input).value();
                List<JqValue> codepoints = new ArrayList<>(value.length());
                for (int i = 0; i < value.length(); ) {
                    int cp = value.codePointAt(i);
                    codepoints.add(JqNumber.of(cp));
                    i += Character.charCount(cp);
                }
                outputs.add(new JqArray(codepoints));
            }
            case "implode" -> {
                if (!(input instanceof JqArray arr)) {
                    throw new JqRuntimeException("implode input must be an array");
                }
                StringBuilder sb = new StringBuilder();
                for (JqValue item : arr.items()) {
                    if (!(item instanceof JqNumber num)) {
                        throw new JqRuntimeException(item.typeName() + " ("
                                + JqPrinter.print(item)
                                + ") can't be imploded, unicode codepoint needs to be numeric");
                    }
                    double raw = num.doubleValue();
                    if (Double.isNaN(raw)) {
                        throw new JqRuntimeException("number (null) can't be imploded, "
                                + "unicode codepoint needs to be numeric");
                    }
                    double d = Math.floor(raw);
                    // out-of-range or surrogate code points become U+FFFD, like jq
                    if (d < 0 || d > Character.MAX_CODE_POINT
                            || (d >= 0xD800 && d <= 0xDFFF)) {
                        d = 0xFFFD;
                    }
                    sb.appendCodePoint((int) d);
                }
                outputs.add(JqString.of(sb.toString()));
            }
            case "split" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, "split input must be a string");
                }
                if (s.value().length() > JqEnvironment.MAX_SPLIT_LIMIT) {
                    throw new JqRuntimeException("Input string too large for split (max="
                            + JqEnvironment.MAX_SPLIT_LIMIT + " chars)");
                }
                for (JqValue arg : evalArgs(executor, args, input, env)) {
                    if (!(arg instanceof JqString sep)) {
                        throw new JqRuntimeException("split input and separator must be strings");
                    }
                    List<JqValue> parts = new ArrayList<>();
                    String value = s.value();
                    if (sep.value().isEmpty()) {
                        for (int i = 0; i < value.length(); i++)
                            parts.add(JqString.of(String.valueOf(value.charAt(i))));
                    } else {
                        int from = 0;
                        int at;
                        while ((at = value.indexOf(sep.value(), from)) >= 0) {
                            parts.add(JqString.of(value.substring(from, at)));
                            from = at + sep.value().length();
                        }
                        parts.add(JqString.of(value.substring(from)));
                    }
                    outputs.add(new JqArray(parts));
                }
            }
            case "join" -> {
                if (!(input instanceof JqArray arr)) {
                    throw typeError(n, input, "join input must be an array");
                }
                for (JqValue arg : evalArgs(executor, args, input, env)) {
                    String sep = arg instanceof JqString s ? s.value() : JqPrinter.tostring(arg);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.size(); i++) {
                        if (i > 0)
                            sb.append(sep);
                        JqValue item = arr.get(i);
                        if (item instanceof JqObject || item instanceof JqArray) {
                            throw new JqRuntimeException("string ("
                                    + JqPrinter.printTruncated(JqString.of(sb.toString()))
                                    + ") and " + item.typeName() + " ("
                                    + JqPrinter.printTruncated(item)
                                    + ") cannot be added");
                        }
                        if (!(item instanceof JqNull))
                            sb.append(JqPrinter.tostring(item));
                    }
                    outputs.add(JqString.of(sb.toString()));
                }
            }
            case "test" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, "test input must be a string");
                }
                for (JqRegexSupport.JqRegex regex : regexArgs(executor, args, input, env)) {
                    outputs.add(JqBoolean.of(JqRegexSupport.test(s.value(), regex)));
                }
            }
            case "match" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, "match input must be a string");
                }
                for (JqRegexSupport.JqRegex regex : regexArgs(executor, args, input, env)) {
                    JqRegexSupport.matchAll(s.value(), regex, outputs);
                }
            }
            case "capture" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, "capture input must be a string");
                }
                for (JqRegexSupport.JqRegex regex : regexArgs(executor, args, input, env)) {
                    outputs.add(JqRegexSupport.capture(s.value(), regex));
                }
            }
            case "scan" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, "scan input must be a string");
                }
                for (JqRegexSupport.JqRegex regex : regexArgs(executor, args, input, env)) {
                    JqRegexSupport.scan(s.value(), regex, outputs);
                }
            }
            case "splits" -> {
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, "splits input must be a string");
                }
                for (JqRegexSupport.JqRegex regex : regexArgs(executor, args, input, env)) {
                    JqRegexSupport.splits(s.value(), regex, outputs);
                }
            }
            case "sub", "gsub" -> {
                requireArgs(n, 2);
                if (!(input instanceof JqString s)) {
                    throw typeError(n, input, name + " input must be a string");
                }
                boolean global = name.equals("gsub");
                for (JqRegexSupport.JqRegex regex : regexArgs(executor, args, input, env)) {
                    JqRegexSupport.JqRegex effective = global
                            ? new JqRegexSupport.JqRegex(regex.pattern(), true) : regex;
                    outputs.add(JqString.of(substitute(executor, s.value(), effective,
                            args.get(1), input, env)));
                }
            }
            case "has" -> {
                requireArgs(n, 1);
                for (JqValue key : evalArgs(executor, args, input, env)) {
                    outputs.add(JqBoolean.of(hasKey(input, key)));
                }
            }
            case "in" -> {
                requireArgs(n, 1);
                for (JqValue container : evalArgs(executor, args, input, env)) {
                    outputs.add(JqBoolean.of(hasKey(container, input)));
                }
            }
            case "contains" -> {
                requireArgs(n, 1);
                for (JqValue arg : evalArgs(executor, args, input, env)) {
                    outputs.add(JqBoolean.of(jqContains(input, arg)));
                }
            }
            case "inside" -> {
                requireArgs(n, 1);
                for (JqValue arg : evalArgs(executor, args, input, env)) {
                    outputs.add(JqBoolean.of(jqContains(arg, input)));
                }
            }
            case "add" -> {
                if (!(input instanceof JqArray arr)) {
                    throw typeError(n, input, "Cannot add " + input.typeName());
                }
                if (arr.isEmpty()) {
                    outputs.add(JqValue.NULL);
                } else {
                    JqValue result = arr.get(0);
                    for (int i = 1; i < arr.size(); i++) {
                        result = executor.evalMathOpPublic(
                                io.nop.jq.jq.ast.MathOpNode.Op.ADD, result, arr.get(i));
                    }
                    outputs.add(result);
                }
            }
case "abs" -> {
                if (input instanceof JqNumber num) {
                    double d = num.doubleValue();
                    if (d < 0)
                        outputs.add(JqNumber.of(-d));
                    else
                        outputs.add(num);
                } else if (input instanceof JqBoolean || input instanceof JqNull) {
                    throw new JqRuntimeException(input.typeName() + " ("
                            + JqPrinter.print(input)
                            + ") and number (0) cannot be compared");
                } else {
                    outputs.add(input);
                }
            }
            case "floor" -> outputs.add(floorLike(input, Math::floor));
            case "ceil" -> outputs.add(floorLike(input, Math::ceil));
            case "round" -> outputs.add(floorLike(input, Math::rint));
            case "sqrt" -> outputs.add(doubleFunc(input, Math::sqrt));
            case "fabs" -> outputs.add(doubleFunc(input, Math::abs));
            case "pow" -> {
                requireArgs(n, 2);
                List<JqValue> xs = executor.execute(args.get(0), input, env);
                List<JqValue> ys = executor.execute(args.get(1), input, env);
                for (JqValue x : xs) {
                    for (JqValue y : ys) {
                        outputs.add(JqNumber.of(Math.pow(
                                requireNumber(x, "pow").doubleValue(),
                                requireNumber(y, "pow").doubleValue())));
                    }
                }
            }
            case "log" -> outputs.add(doubleFunc(input, Math::log));
            case "log2" -> outputs.add(doubleFunc(input,
                    d -> Math.log(d) / Math.log(2)));
            case "log10" -> outputs.add(doubleFunc(input, Math::log10));
            case "exp" -> outputs.add(doubleFunc(input, Math::exp));
            case "exp2" -> outputs.add(doubleFunc(input, d -> Math.pow(2, d)));
            case "exp10" -> outputs.add(doubleFunc(input, d -> Math.pow(10, d)));
            case "sin" -> outputs.add(doubleFunc(input, Math::sin));
            case "cos" -> outputs.add(doubleFunc(input, Math::cos));
            case "tan" -> outputs.add(doubleFunc(input, Math::tan));
            case "asin" -> outputs.add(doubleFunc(input, Math::asin));
            case "acos" -> outputs.add(doubleFunc(input, Math::acos));
            case "atan" -> outputs.add(doubleFunc(input, Math::atan));
            case "sinh" -> outputs.add(doubleFunc(input, Math::sinh));
            case "cosh" -> outputs.add(doubleFunc(input, Math::cosh));
            case "tanh" -> outputs.add(doubleFunc(input, Math::tanh));
            case "atan2" -> {
                requireArgs(n, 2);
                List<JqValue> ys = evalArgs(executor, args, input, env);
                outputs.add(doubleFunc(input, x -> Math.atan2(x,
                        requireNumber(ys.get(0), "atan2").doubleValue())));
            }
            case "isnan" -> {
                if (input instanceof JqNumber num)
                    outputs.add(JqBoolean.of(Double.isNaN(num.doubleValue())));
            }
            case "isinfinite" -> {
                if (input instanceof JqNumber num)
                    outputs.add(JqBoolean.of(Double.isInfinite(num.doubleValue())));
            }
            case "isnormal" -> {
                if (input instanceof JqNumber num) {
                    double d = num.doubleValue();
                    outputs.add(JqBoolean.of(!Double.isNaN(d) && !Double.isInfinite(d)
                            && d != 0 && Math.abs(d) >= Double.MIN_NORMAL));
                }
            }
            case "infinite" -> outputs.add(JqNumber.of(Double.POSITIVE_INFINITY));
            case "nan" -> outputs.add(JqNumber.of(Double.NaN));
            case "min", "max" -> {
                if (!(input instanceof JqArray arr)) {
                    if (input instanceof JqNull)
                        outputs.add(JqValue.NULL);
                    break;
                }
                if (arr.isEmpty()) {
                    outputs.add(JqValue.NULL);
                    break;
                }
                JqValue best = arr.get(0);
                for (JqValue item : arr.items()) {
                    int cmp = JqOrdering.compare(item, best);
                    if (name.equals("min") ? cmp < 0 : cmp > 0)
                        best = item;
                }
                outputs.add(best);
            }
            case "min_by", "max_by" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqArray arr)) {
                    if (input instanceof JqNull)
                        outputs.add(JqValue.NULL);
                    break;
                }
                if (arr.isEmpty()) {
                    outputs.add(JqValue.NULL);
                    break;
                }
                JqValue best = arr.get(0);
                List<JqValue> bestKey = executor.execute(args.get(0), best, env);
                for (JqValue item : arr.items()) {
                    List<JqValue> key = executor.execute(args.get(0), item, env);
                    int cmp = JqPrinter.compareLists(key, bestKey);
                    // min_by keeps the first of ties, max_by the last (jq sort semantics)
                    if (name.equals("min_by") ? cmp < 0 : cmp >= 0) {
                        best = item;
                        bestKey = key;
                    }
                }
                outputs.add(best);
            }
            case "sort" -> {
                if (!(input instanceof JqArray arr))
                    break;
                List<JqValue> sorted = new ArrayList<>(arr.items());
                sorted.sort(JqOrdering::compare);
                outputs.add(new JqArray(sorted));
            }
            case "sort_by" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqArray arr))
                    break;
                List<JqValue> sorted = new ArrayList<>(arr.items());
                Map<JqValue, List<JqValue>> keys = new LinkedHashMap<>();
                for (JqValue item : sorted) {
                    keys.put(item, executor.execute(args.get(0), item, env));
                }
                sorted.sort((a, b) -> JqPrinter.compareLists(keys.get(a), keys.get(b)));
                outputs.add(new JqArray(sorted));
            }
            case "group_by" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqArray arr))
                    break;
                List<JqValue> sorted = new ArrayList<>(arr.items());
                Map<JqValue, List<JqValue>> keys = new LinkedHashMap<>();
                for (JqValue item : sorted) {
                    keys.put(item, executor.execute(args.get(0), item, env));
                }
                sorted.sort((a, b) -> JqPrinter.compareLists(keys.get(a), keys.get(b)));
                List<List<JqValue>> groups = new ArrayList<>();
                List<JqValue> currentKey = null;
                List<JqValue> currentGroup = null;
                for (JqValue item : sorted) {
                    List<JqValue> key = keys.get(item);
                    if (currentGroup == null || JqPrinter.compareLists(key, currentKey) != 0) {
                        currentGroup = new ArrayList<>();
                        currentGroup.add(item);
                        groups.add(currentGroup);
                        currentKey = key;
                    } else {
                        currentGroup.add(item);
                    }
                }
                List<JqValue> wrapped = new ArrayList<>(groups.size());
                for (List<JqValue> group : groups) {
                    wrapped.add(new JqArray(group));
                }
                outputs.add(new JqArray(wrapped));
            }
            case "unique" -> {
                if (!(input instanceof JqArray arr))
                    break;
                List<JqValue> sorted = new ArrayList<>(arr.items());
                sorted.sort(JqOrdering::compare);
                outputs.add(new JqArray(dedupSorted(sorted)));
            }
            case "unique_by" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqArray arr))
                    break;
                List<JqValue> sorted = new ArrayList<>(arr.items());
                Map<JqValue, List<JqValue>> keys = new LinkedHashMap<>();
                for (JqValue item : sorted) {
                    keys.put(item, executor.execute(args.get(0), item, env));
                }
                sorted.sort((a, b) -> JqPrinter.compareLists(keys.get(a), keys.get(b)));
                List<JqValue> result = new ArrayList<>();
                List<JqValue> lastKey = null;
                for (JqValue item : sorted) {
                    List<JqValue> key = keys.get(item);
                    if (lastKey == null || JqPrinter.compareLists(key, lastKey) != 0) {
                        result.add(item);
                        lastKey = key;
                    }
                }
                outputs.add(new JqArray(result));
            }
            case "reverse" -> {
                if (input instanceof JqArray arr) {
                    List<JqValue> reversed = new ArrayList<>(arr.items());
                    java.util.Collections.reverse(reversed);
                    outputs.add(new JqArray(reversed));
                } else if (input instanceof JqString s) {
                    outputs.add(JqString.of(new StringBuilder(s.value()).reverse().toString()));
                } else if (input instanceof JqNull) {
                    outputs.add(new JqArray(new ArrayList<>()));
                } else {
                    throw typeError(n, input, "Cannot reverse " + input.typeName());
                }
            }
            case "flatten" -> {
                if (!(input instanceof JqArray arr))
                    break;
                if (!args.isEmpty()) {
                    for (JqValue d : evalArgs(executor, args, input, env)) {
                        int requested = (int) requireNumber(d, "flatten").doubleValue();
                        if (requested < 0) {
                            throw new JqRuntimeException("flatten depth must not be negative");
                        }
                        List<JqValue> flat = new ArrayList<>();
                        flatten(arr, flat, requested, 0);
                        outputs.add(new JqArray(flat));
                    }
                } else {
                    List<JqValue> flat = new ArrayList<>();
                    flatten(arr, flat, Integer.MAX_VALUE, 0);
                    outputs.add(new JqArray(flat));
                }
            }
            case "range" -> {
                List<List<JqValue>> argVals = evalAllArgs(executor, args, input, env);
                if (argVals.size() == 1) {
                    for (JqValue v : argVals.get(0)) {
                        emitRange(executor, outputs, JqNumber.of(0), v, JqNumber.of(1), env);
                    }
                } else if (argVals.size() == 2) {
                    for (JqValue from : argVals.get(0)) {
                        for (JqValue to : argVals.get(1)) {
                            emitRange(executor, outputs, from, to, JqNumber.of(1), env);
                        }
                    }
                } else if (argVals.size() >= 3) {
                    for (JqValue from : argVals.get(0)) {
                        for (JqValue to : argVals.get(1)) {
                            for (JqValue by : argVals.get(2)) {
                                emitRange(executor, outputs, from, to, by, env);
                            }
                        }
                    }
                }
            }
            case "to_entries" -> {
                if (!(input instanceof JqObject obj))
                    break;
                List<JqValue> entries = new ArrayList<>();
                for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                    Map<String, JqValue> entry = new LinkedHashMap<>();
                    entry.put("key", JqString.of(e.getKey()));
                    entry.put("value", e.getValue());
                    entries.add(new JqObject(entry));
                }
                outputs.add(new JqArray(entries));
            }
            case "from_entries" -> {
                if (!(input instanceof JqArray arr))
                    break;
                Map<String, JqValue> map = new LinkedHashMap<>();
                for (JqValue item : arr.items()) {
                    if (!(item instanceof JqObject entry))
                        continue;
                    JqValue key = firstNonNull(entry, "key", "k", "name", "Name", "K", "Key");
                    JqValue value = firstNonNull(entry, "value", "v", "Value", "V");
                    if (key == null)
                        key = JqValue.NULL;
                    String keyText = JqPrinter.tostring(key);
                    map.put(keyText, value != null ? value : JqValue.NULL);
                }
                outputs.add(new JqObject(map));
            }
            case "with_entries" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqObject obj))
                    break;
                List<JqValue> entries = new ArrayList<>();
                for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                    Map<String, JqValue> entry = new LinkedHashMap<>();
                    entry.put("key", JqString.of(e.getKey()));
                    entry.put("value", e.getValue());
                    JqValue entryVal = new JqObject(entry);
                    entries.addAll(executor.execute(args.get(0), entryVal, env));
                }
                Map<String, JqValue> map = new LinkedHashMap<>();
                for (JqValue item : entries) {
                    if (!(item instanceof JqObject entry))
                        continue;
                    JqValue key = firstNonNull(entry, "key", "k", "name", "Name", "K", "Key");
                    JqValue value = firstNonNull(entry, "value", "v", "Value", "V");
                    if (key == null)
                        key = JqValue.NULL;
                    map.put(JqPrinter.tostring(key), value != null ? value : JqValue.NULL);
                }
                outputs.add(new JqObject(map));
            }
            case "map" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqArray arr)) {
                    throw typeError(n, input, "Cannot iterate over "
                            + input.typeName() + " (" + JqPrinter.print(input) + ")");
                }
                List<JqValue> mapped = new ArrayList<>();
                for (JqValue item : arr.items()) {
                    mapped.addAll(executor.execute(args.get(0), item, env));
                }
                outputs.add(new JqArray(mapped));
            }
            case "map_values" -> {
                requireArgs(n, 1);
                if (input instanceof JqArray arr) {
                    List<JqValue> mapped = new ArrayList<>();
                    for (JqValue item : arr.items()) {
                        List<JqValue> vals = executor.execute(args.get(0), item, env);
                        if (!vals.isEmpty())
                            mapped.add(vals.get(0));
                    }
                    outputs.add(new JqArray(mapped));
                } else if (input instanceof JqObject obj) {
                    Map<String, JqValue> mapped = new LinkedHashMap<>();
                    for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                        List<JqValue> vals = executor.execute(args.get(0), e.getValue(), env);
                        if (!vals.isEmpty())
                            mapped.put(e.getKey(), vals.get(0));
                    }
                    outputs.add(new JqObject(mapped));
                }
            }
            case "select" -> {
                requireArgs(n, 1);
                for (JqValue cond : executor.execute(args.get(0), input, env)) {
                    if (JqTruthiness.of(cond))
                        outputs.add(input);
                }
            }
            case "recurse" -> {
                JqAstNode filter = args.isEmpty() ? null : args.get(0);
                recurse(executor, input, env, outputs, filter);
            }
            case "recurse_down" -> recurse(executor, input, env, outputs, null);
            case "indices", "index", "rindex" -> {
                requireArgs(n, 1);
                for (JqValue needle : evalArgs(executor, args, input, env)) {
                    search(executor, outputs, name, input, needle);
                }
            }
            case "transpose" -> {
                if (!(input instanceof JqArray arr))
                    break;
                int maxLen = 0;
                for (JqValue item : arr.items()) {
                    if (item instanceof JqArray row)
                        maxLen = Math.max(maxLen, row.size());
                }
                List<JqValue> columns = new ArrayList<>();
                for (int i = 0; i < maxLen; i++) {
                    List<JqValue> col = new ArrayList<>();
                    for (JqValue item : arr.items()) {
                        if (item instanceof JqArray row)
                            col.add(i < row.size() ? row.get(i) : JqValue.NULL);
                        else
                            col.add(JqValue.NULL);
                    }
                    columns.add(new JqArray(col));
                }
                outputs.add(new JqArray(columns));
            }
            case "getpath" -> {
                requireArgs(n, 1);
                for (JqValue pathVal : evalArgs(executor, args, input, env)) {
                    if (!(pathVal instanceof JqArray path)) {
                        throw new JqRuntimeException("getpath requires a path array");
                    }
                    outputs.add(JqPathEval.getAt(input, path.items()));
                }
            }
            case "setpath" -> {
                requireArgs(n, 2);
                for (JqValue value : executor.execute(args.get(1), input, env)) {
                    JqValue result = input;
                    for (JqValue pathVal : executor.execute(args.get(0), input, env)) {
                        if (!(pathVal instanceof JqArray path)) {
                            throw new JqRuntimeException("setpath requires a path array");
                        }
                        result = JqPathEval.setAt(result, path.items(), value);
                    }
                    outputs.add(result);
                }
            }
            case "delpaths" -> {
                requireArgs(n, 1);
                for (JqValue pathsVal : evalArgs(executor, args, input, env)) {
                    if (!(pathsVal instanceof JqArray pathsArr)) {
                        throw new JqRuntimeException("Paths must be specified as an array");
                    }
                    List<List<JqValue>> paths = new ArrayList<>();
                    for (JqValue p : pathsArr.items()) {
                        if (p instanceof JqArray path)
                            paths.add(path.items());
                    }
                    paths.sort(JqPrinter::compareLists);
                    // delete deepest/last paths first so earlier deletions don't shift indices
                    JqValue result = input;
                    for (int i = paths.size() - 1; i >= 0; i--) {
                        result = JqPathEval.deleteAt(result, paths.get(i), 0);
                    }
                    outputs.add(result);
                }
            }
            case "walk" -> {
                requireArgs(n, 1);
                walk(executor, args.get(0), input, env, outputs);
            }
            case "pick" -> {
                requireArgs(n, 1);
                JqPathEval pathEval = new JqPathEval(executor::executeInto);
                List<List<Object>> paths = pathEval.evalPaths(args.get(0), input, env);
                JqValue result = JqValue.NULL;
                for (List<Object> path : paths) {
                    JqValue value = JqPathEval.getAt(input, path);
                    result = JqPathEval.setAt(result, path, value);
                }
                outputs.add(result);
            }
            case "paths" -> {
                List<JqValue> all = new ArrayList<>();
                collectPaths(input, new ArrayList<>(), all);
                if (args.isEmpty()) {
                    // all paths except the root
                    for (JqValue p : all) {
                        if (p instanceof JqArray path && !path.isEmpty())
                            outputs.add(p);
                    }
                } else {
                    for (JqValue p : all) {
                        if (p instanceof JqArray path && !path.isEmpty()) {
                            JqValue value = JqPathEval.getAt(input, path.items());
                            List<JqValue> selected = executor.execute(args.get(0), value, env);
                            boolean truthy = !selected.isEmpty()
                                    && JqTruthiness.of(selected.get(0));
                            if (truthy)
                                outputs.add(p);
                        }
                    }
                }
            }
            case "leaf_paths" -> {
                List<JqValue> all = new ArrayList<>();
                collectPaths(input, new ArrayList<>(), all);
                for (JqValue p : all) {
                    if (p instanceof JqArray path && !path.isEmpty()) {
                        JqValue value = JqPathEval.getAt(input, path.items());
                        if (!(value instanceof JqArray) && !(value instanceof JqObject))
                            outputs.add(p);
                    }
                }
            }
            case "env" -> outputs.add(executor.environmentObject());
            case "now" -> outputs.add(JqNumber.of(System.currentTimeMillis() / 1000.0));
            case "debug" -> {
                executor.debug(input);
                outputs.add(input);
            }
            case "input" -> outputs.add(env.nextInput());
            case "inputs" -> {
                while (env.hasMoreInputs()) {
                    outputs.add(env.nextInput());
                }
            }
            case "halt" -> throw new JqRuntimeException("halt", JqValue.NULL);
            case "halt_error" -> {
                JqValue errorValue = input;
                int exitCode = 5;
                if (!args.isEmpty()) {
                    List<JqValue> codes = executor.execute(args.get(0), input, env);
                    if (!codes.isEmpty() && codes.get(0) instanceof JqNumber num)
                        exitCode = num.intValue();
                }
                throw new JqHaltException(JqPrinter.tostring(errorValue), exitCode);
            }
            case "input_line_number" -> outputs.add(JqNumber.of(0));
            case "trim" -> {
                if (input instanceof JqString s) {
                    outputs.add(JqString.of(s.value().trim()));
                } else {
                    throw new JqRuntimeException("trim input must be a string");
                }
            }
            case "tostream" -> {
                List<JqValue> streams = new ArrayList<>();
                toStream(input, new ArrayList<>(), streams);
                outputs.addAll(streams);
            }
            case "fromstream" -> {
                requireArgs(n, 1);
                List<JqValue> events = executor.execute(args.get(0), input, env);
                outputs.add(fromStream(events));
            }
            case "strftime" -> {
                requireArgs(n, 1);
                for (JqValue fmt : evalArgs(executor, args, input, env)) {
                    if (!(fmt instanceof JqString formatStr)) {
                        throw new JqRuntimeException("strftime/1 requires parsed datetime inputs");
                    }
                    outputs.add(JqTimeFunctions.strftime(formatStr.value(), input, "strftime/1"));
                }
            }
            case "gmtime" -> {
                if (input instanceof JqNumber num) {
                    outputs.add(JqTimeFunctions.gmtime(num.doubleValue()));
                } else {
                    throw new JqRuntimeException("gmtime requires a number of seconds since epoch");
                }
            }
            case "localtime" -> {
                if (input instanceof JqNumber num) {
                    outputs.add(JqTimeFunctions.localtime(num.doubleValue()));
                } else {
                    throw new JqRuntimeException("localtime requires a number of seconds since epoch");
                }
            }
            case "mktime" -> outputs.add(JqNumber.of(JqTimeFunctions.mktime(input)));
            case "strptime" -> {
                requireArgs(n, 1);
                for (JqValue fmt : evalArgs(executor, args, input, env)) {
                    if (!(fmt instanceof JqString formatStr)) {
                        throw new JqRuntimeException("strptime/1 requires a string format");
                    }
                    outputs.add(JqTimeFunctions.strptime(input, formatStr.value()));
                }
            }
            case "strflocaltime" -> {
                requireArgs(n, 1);
                for (JqValue fmt : evalArgs(executor, args, input, env)) {
                    if (!(fmt instanceof JqString formatStr)) {
                        throw new JqRuntimeException(
                                "strflocaltime/1 requires parsed datetime inputs");
                    }
                    if (input instanceof JqNumber num) {
                        JqValue local = JqTimeFunctions.localtime(num.doubleValue());
                        outputs.add(JqTimeFunctions.strftime(formatStr.value(), local,
                                "strflocaltime/1"));
                    } else if (input instanceof JqArray) {
                        List<JqValue> parts = ((JqArray) input).items();
                        boolean numeric = parts.size() >= 6;
                        for (int i2 = 0; numeric && i2 < 6; i2++) {
                            numeric = parts.get(i2) instanceof JqNumber;
                        }
                        if (!numeric) {
                            throw new JqRuntimeException(
                                    "strflocaltime/1 requires parsed datetime inputs");
                        }
                        double epoch = JqTimeFunctions.mktime(input);
                        JqValue local = JqTimeFunctions.localtime(epoch);
                        outputs.add(JqTimeFunctions.strftime(formatStr.value(), local,
                                "strflocaltime/1"));
                    } else {
                        throw new JqRuntimeException(
                                "strflocaltime/1 requires parsed datetime inputs");
                    }
                }
            }
            case "todate" -> {
                if (input instanceof JqNumber num) {
                    outputs.add(JqTimeFunctions.todate(num.doubleValue()));
                } else {
                    throw new JqRuntimeException("todate requires a number of seconds since epoch");
                }
            }
            case "fromdate", "fromdateiso8601" -> {
                if (input instanceof JqString s) {
                    outputs.add(JqNumber.of(JqTimeFunctions.fromdate(s.value())));
                } else {
                    throw new JqRuntimeException("fromdate requires a string");
                }
            }
            case "todateiso8601" -> {
                if (input instanceof JqNumber num) {
                    outputs.add(JqTimeFunctions.todateIso(num.doubleValue()));
                }
            }
            case "date" -> {
                if (input instanceof JqNumber num) {
                    outputs.add(JqTimeFunctions.todate(num.doubleValue()));
                }
            }
            case "splits_impl" -> {
            }
            case "IN" -> {
                if (args.size() >= 2) {
                    // IN(s; t): true when the streams s and t share a value
                    List<JqValue> tVals = executor.execute(args.get(1), input, env);
                    boolean found = false;
                    for (JqValue sVal : executor.execute(args.get(0), input, env)) {
                        for (JqValue tVal : tVals) {
                            if (JqOrdering.compare(sVal, tVal) == 0) {
                                found = true;
                                break;
                            }
                        }
                        if (found)
                            break;
                    }
                    outputs.add(JqBoolean.of(found));
                } else {
                    // IN(s): is the input produced by s?
                    requireArgs(n, 1);
                    boolean found = false;
                    for (JqValue candidate : executor.execute(args.get(0), input, env)) {
                        if (JqOrdering.compare(input, candidate) == 0) {
                            found = true;
                            break;
                        }
                    }
                    outputs.add(JqBoolean.of(found));
                }
            }
            case "INDEX" -> {
                if (args.size() >= 2) {
                    // INDEX(stream; idx_expr)
                    Map<String, JqValue> index = new LinkedHashMap<>();
                    for (JqValue row : executor.execute(args.get(0), input, env)) {
                        List<JqValue> keys = executor.execute(args.get(1), row, env);
                        if (!keys.isEmpty()) {
                            index.put(JqPrinter.tostring(keys.get(0)), row);
                        }
                    }
                    outputs.add(new JqObject(index));
                } else {
                    requireArgs(n, 1);
                    Map<String, JqValue> index = new LinkedHashMap<>();
                    if (input instanceof JqArray arr) {
                        for (JqValue row : arr.items()) {
                            List<JqValue> keys = executor.execute(args.get(0), row, env);
                            if (!keys.isEmpty()) {
                                index.put(JqPrinter.tostring(keys.get(0)), row);
                            }
                        }
                    }
                    outputs.add(new JqObject(index));
                }
            }
            case "JOIN" -> {
                // JOIN($idx; idx_expr): [.[] | [., $idx[idx|tostring]]]
                requireArgs(n, 2);
                JqValue idxObj = first(executor.execute(args.get(0), input, env));
                List<JqValue> joined = new ArrayList<>();
                if (input instanceof JqArray arr) {
                    for (JqValue row : arr.items()) {
                        List<JqValue> keys = executor.execute(args.get(1), row, env);
                        JqValue match = JqValue.NULL;
                        if (!keys.isEmpty() && idxObj instanceof JqObject obj) {
                            JqValue m = obj.get(JqPrinter.tostring(keys.get(0)));
                            if (m != null)
                                match = m;
                        }
                        List<JqValue> pair = new ArrayList<>(2);
                        pair.add(row);
                        pair.add(match);
                        joined.add(new JqArray(pair));
                    }
                }
                outputs.add(new JqArray(joined));
            }
            case "bsearch" -> {
                requireArgs(n, 1);
                if (!(input instanceof JqArray arr))
                    break;
                for (JqValue target : evalArgs(executor, args, input, env)) {
                    int lo = 0, hi = arr.size() - 1;
                    int found = -1;
                    while (lo <= hi) {
                        int mid = (lo + hi) >>> 1;
                        int cmp = JqOrdering.compare(arr.get(mid), target);
                        if (cmp == 0) {
                            found = mid;
                            break;
                        }
                        if (cmp < 0)
                            lo = mid + 1;
                        else
                            hi = mid - 1;
                    }
                    outputs.add(found >= 0 ? JqNumber.of(found) : JqNumber.of(-(lo + 1)));
                }
            }
            case "builtins" -> {
                List<JqValue> names = new ArrayList<>();
                for (String b : BUILTIN_NAMES) {
                    names.add(JqString.of(b));
                }
                outputs.add(new JqArray(names));
            }
            case "scalars" -> {
                String type = input.typeName();
                if (!type.equals("object") && !type.equals("array"))
                    outputs.add(input);
            }
            case "objects" -> {
                if (input instanceof JqObject)
                    outputs.add(input);
            }
            case "arrays" -> {
                if (input instanceof JqArray)
                    outputs.add(input);
            }
            case "booleans" -> {
                if (input instanceof JqBoolean)
                    outputs.add(input);
            }
            case "numbers" -> {
                if (input instanceof JqNumber)
                    outputs.add(input);
            }
            case "strings" -> {
                if (input instanceof JqString)
                    outputs.add(input);
            }
            case "nulls" -> {
                if (input instanceof JqNull)
                    outputs.add(input);
            }
            case "iterables" -> {
                if (input instanceof JqArray || input instanceof JqObject)
                    outputs.add(input);
            }
            case "combinations" -> {
                if (input instanceof JqArray arr) {
                    combinations(arr, 0, new ArrayList<>(), outputs);
                }
            }
            case "modulemeta" -> outputs.add(new JqObject(Map.of()));
            case "builtins_list" -> outputs.add(new JqArray(new ArrayList<>()));
            default -> {
                String fnKey = JqExecutor.functionKey(name, args.size());
                if (env.hasFunction(fnKey)) {
                    executor.callFunctionFromBuiltin(fnKey, args, input, env, outputs);
                } else if (env.hasFunction(name)) {
                    executor.callFunctionFromBuiltin(name, args, input, env, outputs);
                } else {
                    throw new JqRuntimeException(name + "/" + args.size()
                            + " is not defined");
                }
            }
        }
    }

    // ===== shared helpers =====

    private static JqValue lengthOf(JqValue v) {
        if (v instanceof JqNull)
            return JqNumber.of(0);
        if (v instanceof JqBoolean)
            throw new JqRuntimeException("boolean (" + v + ") has no length");
        if (v instanceof JqNumber num)
            return JqNumber.of(Math.abs(num.doubleValue()));
        if (v instanceof JqString s)
            return JqNumber.of(s.value().codePointCount(0, s.value().length()));
        if (v instanceof JqArray a)
            return JqNumber.of(a.size());
        return JqNumber.of(((JqObject) v).size());
    }

    private static List<JqValue> evalArgs(JqExecutor executor, List<JqAstNode> args,
                                          JqValue input, JqEnvironment env) {
        List<JqValue> vals = new ArrayList<>();
        for (JqAstNode arg : args) {
            vals.addAll(executor.execute(arg, input, env));
        }
        return vals;
    }

    private static List<List<JqValue>> evalAllArgs(JqExecutor executor, List<JqAstNode> args,
                                                   JqValue input, JqEnvironment env) {
        List<List<JqValue>> result = new ArrayList<>();
        for (JqAstNode arg : args) {
            result.add(executor.execute(arg, input, env));
        }
        return result;
    }

    private static List<JqRegexSupport.JqRegex> regexArgs(JqExecutor executor,
                                                          List<JqAstNode> args,
                                                          JqValue input,
                                                          JqEnvironment env) {
        List<JqValue> patterns = executor.execute(args.get(0), input, env);
        List<JqValue> flagVals = args.size() >= 2
                ? executor.execute(args.get(1), input, env) : List.of();
        String flags = flagVals.isEmpty() ? null : JqPrinter.tostring(flagVals.get(0));
        List<JqRegexSupport.JqRegex> result = new ArrayList<>();
        for (JqValue pattern : patterns) {
            if (!(pattern instanceof JqString p)) {
                throw new JqRuntimeException(JqPrinter.print(pattern)
                        + " cannot be matched, as it is not a string");
            }
            result.add(JqRegexSupport.compile(p.value(), flags));
        }
        return result;
    }

    /** jq sub/gsub: the replacement is a filter receiving the capture object. */
    private static String substitute(JqExecutor executor, String input,
                                     JqRegexSupport.JqRegex regex,
                                     JqAstNode replacement, JqValue originalInput,
                                     JqEnvironment env) {
        java.util.regex.Matcher m = regex.pattern().matcher(input);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            JqValue matchObj = JqRegexSupport.matchObject(m);
            List<JqValue> reps = executor.execute(replacement, matchObj, env);
            String rep = reps.isEmpty() ? "" : JqPrinter.tostring(reps.get(0));
            sb.append(input, last, m.start()).append(rep);
            last = m.end();
            if (!regex.global())
                break;
            if (m.end() == m.start()) {
                if (m.end() < input.length())
                    sb.append(input.charAt(m.end()));
                last = m.end() + 1;
                if (!m.find(last))
                    break;
                continue;
            }
        }
        if (last < input.length())
            sb.append(input, last, input.length());
        return sb.toString();
    }

    private static boolean hasKey(JqValue container, JqValue key) {
        if (container instanceof JqObject obj && key instanceof JqString s)
            return obj.has(s.value());
        if (container instanceof JqArray arr && key instanceof JqNumber num) {
            if (Double.isNaN(num.doubleValue()))
                return false;
            int idx = num.intValue();
            if (idx < 0)
                idx += arr.size();
            return idx >= 0 && idx < arr.size();
        }
        if (container instanceof JqNull)
            return false;
        throw new JqRuntimeException("Cannot check whether " + container.typeName()
                + " has a " + key.typeName() + " key");
    }

    private static boolean jqContains(JqValue input, JqValue arg) {
        if (input instanceof JqString s && arg instanceof JqString a)
            return s.value().contains(a.value());
        if (input instanceof JqNumber && arg instanceof JqNumber)
            return JqOrdering.compare(input, arg) == 0;
        if (input instanceof JqBoolean && arg instanceof JqBoolean)
            return input.equals(arg);
        if (input instanceof JqArray ia && arg instanceof JqArray aa) {
            for (JqValue argItem : aa.items()) {
                boolean found = false;
                for (JqValue inputItem : ia.items()) {
                    if (jqContains(inputItem, argItem)) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    return false;
            }
            return true;
        }
        if (input instanceof JqObject io && arg instanceof JqObject ao) {
            for (String key : ao.keySet()) {
                if (!io.has(key))
                    return false;
                if (!jqContains(io.get(key), ao.get(key)))
                    return false;
            }
            return true;
        }
        if (input instanceof JqNull && arg instanceof JqNull)
            return true;
        throw new JqRuntimeException(JqPrinter.print(input) + " and "
                + JqPrinter.print(arg) + " cannot have their containment checked");
    }

    private static void search(JqExecutor executor, List<JqValue> outputs, String mode,
                               JqValue input, JqValue needle) {
        if (input instanceof JqString s && needle instanceof JqString ns) {
            String value = s.value();
            String search = ns.value();
            if (search.isEmpty()) {
                if (mode.equals("indices")) {
                    List<JqValue> empty = new ArrayList<>();
                    outputs.add(new JqArray(empty));
                } else {
                    outputs.add(JqValue.NULL);
                }
                return;
            }
            if (mode.equals("index")) {
                int idx = value.indexOf(search);
                outputs.add(idx >= 0 ? JqNumber.of(idx) : JqValue.NULL);
            } else if (mode.equals("rindex")) {
                int idx = value.lastIndexOf(search);
                outputs.add(idx >= 0 ? JqNumber.of(idx) : JqValue.NULL);
            } else {
                List<JqValue> indices = new ArrayList<>();
                int idx = 0;
                while ((idx = value.indexOf(search, idx)) >= 0) {
                    indices.add(JqNumber.of(idx));
                    idx += 1;
                }
                outputs.add(new JqArray(indices));
            }
            return;
        }
        if (input instanceof JqArray arr) {
            if (needle instanceof JqArray target) {
                // subarray search
                List<JqValue> indices = new ArrayList<>();
                if (target.isEmpty()) {
                    outputs.add(JqValue.NULL);
                    return;
                }
                for (int i = 0; i + target.size() <= arr.size(); i++) {
                    boolean match = true;
                    for (int j = 0; j < target.size(); j++) {
                        if (JqOrdering.compare(arr.get(i + j), target.get(j)) != 0) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        if (mode.equals("index")) {
                            outputs.add(JqNumber.of(i));
                            return;
                        }
                        if (mode.equals("rindex")) {
                            indices.clear();
                        }
                        indices.add(JqNumber.of(i));
                    }
                }
                if (mode.equals("index")) {
                    outputs.add(indices.isEmpty() ? JqValue.NULL : indices.get(0));
                } else if (mode.equals("rindex")) {
                    outputs.add(indices.isEmpty() ? JqValue.NULL : indices.get(indices.size() - 1));
                } else {
                    outputs.add(new JqArray(indices));
                }
                return;
            }
            // scalar search
            List<JqValue> indices = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                if (JqOrdering.compare(arr.get(i), needle) == 0) {
                    if (mode.equals("index")) {
                        outputs.add(JqNumber.of(i));
                        return;
                    }
                    indices.add(JqNumber.of(i));
                }
            }
            if (mode.equals("index")) {
                outputs.add(JqValue.NULL);
            } else if (mode.equals("rindex")) {
                outputs.add(indices.isEmpty() ? JqValue.NULL : indices.get(indices.size() - 1));
            } else {
                outputs.add(new JqArray(indices));
            }
            return;
        }
        if (input instanceof JqNull) {
            outputs.add(JqValue.NULL);
            return;
        }
        throw new JqRuntimeException(input.typeName() + " (" + JqPrinter.print(input)
                + ") cannot be searched, as it is not a string");
    }

    private static void flatten(JqArray arr, List<JqValue> result, int maxDepth, int depth) {
        for (JqValue item : arr.items()) {
            if (item instanceof JqArray inner && (maxDepth < 0 || depth < maxDepth)) {
                flatten(inner, result, maxDepth, depth + 1);
            } else {
                result.add(item);
            }
        }
    }

    private static void emitRange(JqExecutor executor, List<JqValue> outputs,
                                  JqValue from, JqValue to, JqValue by, JqEnvironment env) {
        double start = requireNumber(from, "range").doubleValue();
        double end = requireNumber(to, "range").doubleValue();
        double step = requireNumber(by, "range").doubleValue();
        if (step == 0)
            return;
        if (step > 0) {
            for (double i = start; i < end; i += step) {
                env.checkOutputLimit();
                outputs.add(JqNumber.of(i));
            }
        } else {
            for (double i = start; i > end; i += step) {
                env.checkOutputLimit();
                outputs.add(JqNumber.of(i));
            }
        }
    }

    private static void recurse(JqExecutor executor, JqValue input, JqEnvironment env,
                                List<JqValue> outputs, JqAstNode filter) {
        env.checkOutputLimit();
        outputs.add(input);
        List<JqValue> children;
        if (filter == null) {
            children = new ArrayList<>();
            if (input instanceof JqArray arr) {
                children.addAll(arr.items());
            } else if (input instanceof JqObject obj) {
                children.addAll(obj.properties().values());
            }
        } else {
            try {
                children = executor.execute(filter, input, env);
            } catch (JqRuntimeException e) {
                children = new ArrayList<>();
            }
        }
        for (JqValue child : children) {
            recurse(executor, child, env, outputs, filter);
        }
    }

    private static void walk(JqExecutor executor, JqAstNode filter, JqValue input,
                             JqEnvironment env, List<JqValue> outputs) {
        JqValue rebuilt = input;
        if (input instanceof JqArray arr) {
            List<JqValue> items = new ArrayList<>();
            for (JqValue item : arr.items()) {
                List<JqValue> walkVals = new ArrayList<>();
                walk(executor, filter, item, env, walkVals);
                items.addAll(walkVals);
            }
            rebuilt = new JqArray(items);
        } else if (input instanceof JqObject obj) {
            Map<String, JqValue> props = new LinkedHashMap<>();
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                List<JqValue> walkVals = new ArrayList<>();
                walk(executor, filter, e.getValue(), env, walkVals);
                if (!walkVals.isEmpty())
                    props.put(e.getKey(), walkVals.get(0));
            }
            rebuilt = new JqObject(props);
        }
        outputs.addAll(executor.execute(filter, rebuilt, env));
    }

    private static void collectPaths(JqValue value, List<JqValue> currentPath,
                                     List<JqValue> results) {
        if (value instanceof JqObject obj) {
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                List<JqValue> newPath = new ArrayList<>(currentPath);
                newPath.add(JqString.of(e.getKey()));
                results.add(new JqArray(newPath));
                collectPaths(e.getValue(), newPath, results);
            }
        } else if (value instanceof JqArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                List<JqValue> newPath = new ArrayList<>(currentPath);
                newPath.add(JqNumber.of(i));
                results.add(new JqArray(newPath));
                collectPaths(arr.get(i), newPath, results);
            }
        }
    }

    private static List<JqValue> dedupSorted(List<JqValue> sorted) {
        List<JqValue> result = new ArrayList<>();
        JqValue last = null;
        for (JqValue item : sorted) {
            if (result.isEmpty() || JqOrdering.compare(item, last) != 0) {
                result.add(item);
                last = item;
            }
        }
        return result;
    }

    private static JqValue floorLike(JqValue input, java.util.function.DoubleUnaryOperator fn) {
        if (!(input instanceof JqNumber num))
            return JqValue.NULL;
        double d = fn.applyAsDouble(num.doubleValue());
        if (d == Math.floor(d) && Double.isFinite(d))
            return JqNumber.of((long) d);
        return JqNumber.of(d);
    }

    private static JqValue doubleFunc(JqValue input, java.util.function.DoubleUnaryOperator fn) {
        if (!(input instanceof JqNumber num))
            return JqValue.NULL;
        return JqNumber.of(fn.applyAsDouble(num.doubleValue()));
    }

    private static JqValue doubleFunc(JqValue input,
                                      java.util.function.DoubleUnaryOperator primary,
                                      java.util.function.DoubleUnaryOperator secondary) {
        if (!(input instanceof JqNumber num))
            return JqValue.NULL;
        return JqNumber.of(secondary.applyAsDouble(primary.applyAsDouble(num.doubleValue())));
    }

    private static List<JqValue> numbersInput(JqValue input) {
        return input instanceof JqNumber ? List.of(input) : List.of();
    }

    private static JqNumber requireNumber(JqValue v, String fn) {
        if (v instanceof JqNumber num)
            return num;
        throw new JqRuntimeException(fn + " requires a number, got " + v.typeName());
    }

    private static void requireString(FuncCallNode n, JqValue input) {
        if (!(input instanceof JqString)) {
            throw new JqRuntimeException(n.name() + " input must be a string");
        }
    }

    private static void requireArgs(FuncCallNode n, int count) {
        if (n.args().size() < count) {
            throw new JqRuntimeException(n.name() + "/" + count + " is not defined");
        }
    }

    private static JqRuntimeException typeError(FuncCallNode n, JqValue input, String message) {
        return new JqRuntimeException(n.name() + ": " + message);
    }

    private static JqValue firstNonNull(JqObject obj, String... keys) {
        for (String key : keys) {
            JqValue v = obj.get(key);
            if (v != null)
                return v;
        }
        return null;
    }

    private static int comparePathsForDelete(List<JqValue> a, List<JqValue> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int cmp = JqOrdering.compare(a.get(i), b.get(i));
            if (cmp != 0)
                return cmp;
        }
        // longer path sorts later but must be deleted first
        return Integer.compare(b.size(), a.size());
    }

    private static JqRuntimeException errorFor(JqExecutor executor, JqValue errorValue) {
        if (errorValue instanceof JqString s) {
            return new JqRuntimeException(s.value(), s);
        }
        return new JqRuntimeException(JqPrinter.print(errorValue), errorValue);
    }

    private static void combinations(JqArray arr, int index, List<JqValue> current,
                                     List<JqValue> outputs) {
        if (index == arr.size()) {
            outputs.add(new JqArray(new ArrayList<>(current)));
            return;
        }
        if (arr.get(index) instanceof JqArray options) {
            for (JqValue option : options.items()) {
                current.add(option);
                combinations(arr, index + 1, current, outputs);
                current.remove(current.size() - 1);
            }
        }
    }

    private static void toStream(JqValue value, List<JqValue> path, List<JqValue> events) {
        if (value instanceof JqObject obj) {
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                List<JqValue> p = new ArrayList<>(path);
                p.add(JqString.of(e.getKey()));
                toStream(e.getValue(), p, events);
            }
            if (obj.isEmpty()) {
                events.add(streamEvent(path, JqValue.NULL));
            }
        } else if (value instanceof JqArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                List<JqValue> p = new ArrayList<>(path);
                p.add(JqNumber.of(i));
                toStream(arr.get(i), p, events);
            }
            if (arr.isEmpty()) {
                events.add(streamEvent(path, JqValue.NULL));
            }
        } else {
            events.add(streamEvent(path, value));
        }
    }

    private static JqValue streamEvent(List<JqValue> path, JqValue leaf) {
        List<JqValue> event = new ArrayList<>();
        event.add(new JqArray(new ArrayList<>(path)));
        event.add(leaf);
        return new JqArray(event);
    }

    private static JqValue fromStream(List<JqValue> events) {
        JqValue result = JqValue.NULL;
        for (int i = 0; i < events.size(); i++) {
            JqValue event = events.get(i);
            if (event instanceof JqArray pair && pair.size() == 2) {
                JqValue path = pair.get(0);
                JqValue value = pair.get(1);
                if (path instanceof JqArray pathArr) {
                    if (value instanceof JqNull && i + 1 < events.size()) {
                        // [path] + {value} pattern: value comes as next event object
                        JqValue next = events.get(++i);
                        result = JqPathEval.setAt(result, pathArr.items(), next);
                    } else {
                        result = JqPathEval.setAt(result, pathArr.items(), value);
                    }
                }
            }
        }
        return result;
    }

    /** True for plain builtins that user functions may not shadow. */
    private static boolean isPlainBuiltin(String name) {
        return switch (name) {
            case "empty", "error", "not", "length", "keys", "keys_unsorted", "values",
                 "has", "in", "contains", "inside", "type", "tostring", "tojson",
                 "fromjson", "tonumber", "toboolean" -> true;
            default -> false;
        };
    }
}
