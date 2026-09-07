package io.nop.treesitter.scanner;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles the external-scanner DSL (a hand translation of an upstream
 * {@code scanner.c}) into the roadmap bytecode documented in
 * {@code blob-format.md} §22.
 *
 * <p>DSL syntax: one instruction per line, labels are {@code name:} markers,
 * {@code #} starts a comment, and {@code token NAME} lines declare the external
 * tokens in scanner.c enum order. Operands are integers, {@code 'x'} char
 * literals (with the usual escapes plus unicode escapes), {@code "..."} strings
 * (for {@code push_bytes}) and identifiers (labels / token names). The
 * compiler resolves every label to an instruction start, maps token names to
 * symbol ids through the grammar's external scanner symbol map, and runs the
 * load-time validator on the result. Any construct it cannot express (unknown
 * mnemonic, unresolvable label or token, malformed operand) raises a typed
 * {@link IllegalStateException} naming the construct — no silent skip.</p>
 */
public final class ScannerCompiler {

    private final List<Line> lines = new ArrayList<>();
    private final Map<String, Integer> labels = new LinkedHashMap<>();
    private final List<Map.Entry<String, Integer>> patchPositions = new ArrayList<>();
    private final Map<String, Integer> tokenOrdinals = new LinkedHashMap<>();
    private final int[] externalSymbolMap;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private ScannerCompiler(int[] externalSymbolMap) {
        this.externalSymbolMap = externalSymbolMap;
    }

    /**
     * Compiles DSL text to a validated program. {@code externalSymbolMap} is the
     * grammar's {@code ts_external_scanner_symbol_map} (ordinal -&gt; symbol id).
     */
    public static byte[] compile(String dsl, int[] externalSymbolMap) {
        ScannerCompiler c = new ScannerCompiler(externalSymbolMap);
        c.parse(dsl);
        return c.assemble();
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    private void parse(String dsl) {
        String[] rawLines = dsl.split("\n", -1);
        for (String raw : rawLines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("token ")) {
                String name = line.substring("token ".length()).trim();
                if (!name.matches("[a-zA-Z_]\\w*")) {
                    throw new IllegalStateException("malformed token declaration: " + line);
                }
                if (tokenOrdinals.containsKey(name)) {
                    throw new IllegalStateException("duplicate token declaration: " + name);
                }
                tokenOrdinals.put(name, tokenOrdinals.size());
                continue;
            }
            if (line.endsWith(":") && line.substring(0, line.length() - 1).matches("[a-zA-Z_]\\w*")) {
                String name = line.substring(0, line.length() - 1);
                if (labels.containsKey(name)) {
                    throw new IllegalStateException("duplicate label: " + name);
                }
                labels.put(name, null);
                lines.add(new Line(name, List.of(), true));
                continue;
            }
            String mnemonic = line.split("\\s+")[0];
            List<String> operands = new ArrayList<>();
            String rest = line.substring(mnemonic.length()).trim();
            for (String op : splitOperands(rest)) {
                if (!op.isEmpty()) {
                    operands.add(op.trim());
                }
            }
            lines.add(new Line(mnemonic, operands, false));
        }
        if (tokenOrdinals.size() != externalSymbolMap.length) {
            throw new IllegalStateException("DSL declares " + tokenOrdinals.size()
                    + " tokens but the grammar has " + externalSymbolMap.length
                    + " external tokens (scanner.c enum order mismatch)");
        }
    }

    private static String stripComment(String line) {
        int i = line.indexOf('#');
        return i < 0 ? line : line.substring(0, i);
    }

    private static List<String> splitOperands(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                char quote = c;
                cur.append(c);
                i++;
                while (i < s.length()) {
                    cur.append(s.charAt(i));
                    if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                        cur.append(s.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (s.charAt(i) == quote) {
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }

    // ------------------------------------------------------------------
    // Assembly
    // ------------------------------------------------------------------

    private byte[] assemble() {
        emitAll();
        byte[] program = out.toByteArray();
        ScannerProgram.validate(program);
        return program;
    }

    private void emitAll() {
        int pc = 0;
        Map<String, Integer> labelOffsets = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line.isLabel()) {
                labelOffsets.put(line.mnemonic(), pc);
                continue;
            }
            pc += emitInstruction(line);
        }
        if (pc != out.size()) {
            throw new IllegalStateException("scanner compiler internal offset mismatch: " + pc + " vs " + out.size());
        }
        byte[] bytes = out.toByteArray();
        for (Map.Entry<String, Integer> e : patchPositions) {
            Integer target = labelOffsets.get(e.getKey());
            if (target == null) {
                throw new IllegalStateException("scanner DSL: unresolved label '" + e.getKey() + "'");
            }
            int pos = e.getValue();
            bytes[pos] = (byte) (target >>> 8);
            bytes[pos + 1] = (byte) (int) target;
        }
        out.reset();
        out.write(bytes, 0, bytes.length);
    }

    private int emitInstruction(Line line) {
        String m = line.mnemonic();
        return switch (m) {
            case "fail" -> {
                out.write(ScannerProgram.FAIL);
                yield 1;
            }
            case "advance" -> {
                out.write(ScannerProgram.ADVANCE);
                yield 1;
            }
            case "skip" -> {
                out.write(ScannerProgram.SKIP);
                yield 1;
            }
            case "mark_end" -> {
                out.write(ScannerProgram.MARK_END);
                yield 1;
            }
            case "ret" -> {
                out.write(ScannerProgram.RET);
                yield 1;
            }
            case "jmp" -> emitWithTarget(ScannerProgram.JMP, line);
            case "jmp_if_ws" -> emitWithTarget(ScannerProgram.JMP_IF_WS, line);
            case "jmp_if_alpha" -> emitWithTarget(ScannerProgram.JMP_IF_ALPHA, line);
            case "jmp_if_digit" -> emitWithTarget(ScannerProgram.JMP_IF_DIGIT, line);
            case "call" -> emitWithTarget(ScannerProgram.CALL, line);
            case "set_flag" -> {
                out.write(ScannerProgram.SET_FLAG);
                out.write(intOperand(line, 0));
                yield 2;
            }
            case "jmp_if_flag_eq" -> {
                out.write(ScannerProgram.JMP_IF_FLAG_EQ);
                out.write(intOperand(line, 0));
                patchTarget(line, 1);
                out.write(new byte[2], 0, 2);
                yield 4;
            }
            case "push_byte" -> {
                out.write(ScannerProgram.PUSH_BYTE);
                out.write(intOperand(line, 0));
                yield 2;
            }
            case "jmp_if_valid" -> {
                out.write(ScannerProgram.JMP_IF_VALID);
                out.write(tokenOrdinal(line, 0));
                patchTarget(line, 1);
                out.write(new byte[2], 0, 2);
                yield 4;
            }
            case "span" -> {
                out.write(ScannerProgram.SPAN);
                out.write(intOperand(line, 0));
                patchTarget(line, 1);
                out.write(new byte[2], 0, 2);
                yield 4;
            }
            case "emit" -> {
                out.write(ScannerProgram.EMIT);
                writeU16(symbolIdForToken(line, 0));
                yield 3;
            }
            case "jmp_if_eq" -> emitCondEq(ScannerProgram.JMP_IF_EQ, line);
            case "jmp_if_ne" -> emitCondEq(ScannerProgram.JMP_IF_NE, line);
            case "set_result" -> {
                out.write(ScannerProgram.SET_RESULT);
                writeI32(intOperand(line, 0));
                yield 5;
            }
            case "jmp_if_result_eq" -> {
                out.write(ScannerProgram.JMP_IF_RESULT_EQ);
                writeI32(intOperand(line, 0));
                patchTarget(line, 1);
                out.write(new byte[2], 0, 2);
                yield 7;
            }
            case "jmp_if_state_eq" -> {
                out.write(ScannerProgram.JMP_IF_STATE_EQ);
                writeI32(intOperand(line, 0));
                patchTarget(line, 1);
                out.write(new byte[2], 0, 2);
                yield 7;
            }
            case "set_state" -> {
                out.write(ScannerProgram.SET_STATE);
                writeI32(intOperand(line, 0));
                yield 5;
            }
            case "jmp_if_in_range" -> {
                out.write(ScannerProgram.JMP_IF_IN_RANGE);
                writeI32(intOperand(line, 0));
                writeI32(intOperand(line, 1));
                patchTarget(line, 2);
                out.write(new byte[2], 0, 2);
                yield 11;
            }
            case "push_bytes" -> {
                byte[] bytes = stringOperand(line, 0);
                out.write(ScannerProgram.PUSH_BYTES);
                writeU16(bytes.length);
                out.write(bytes, 0, bytes.length);
                yield 3 + bytes.length;
            }
            default -> throw new IllegalStateException("scanner DSL: unknown mnemonic '" + m
                    + "' (construct not expressible — fail loud)");
        };
    }

    private int emitWithTarget(int op, Line line) {
        out.write(op);
        patchTarget(line, 0);
        out.write(new byte[2], 0, 2);
        return 1 + 2;
    }

    private int emitCondEq(int op, Line line) {
        out.write(op);
        writeI32(intOperand(line, 0));
        patchTarget(line, 1);
        out.write(new byte[2], 0, 2);
        return 1 + 4 + 2;
    }

    private void patchTarget(Line line, int operandIndex) {
        String name = identifierOperand(line, operandIndex);
        if (!labels.containsKey(name)) {
            throw new IllegalStateException("scanner DSL: jump target '" + name
                    + "' is not a declared label");
        }
        patchPositions.add(Map.entry(name, out.size()));
    }

    private int tokenOrdinal(Line line, int operandIndex) {
        String name = identifierOperand(line, operandIndex);
        Integer ordinal = tokenOrdinals.get(name);
        if (ordinal == null) {
            throw new IllegalStateException("scanner DSL: token '" + name + "' is not declared");
        }
        return ordinal;
    }

    private int symbolIdForToken(Line line, int operandIndex) {
        int ordinal = tokenOrdinal(line, operandIndex);
        if (ordinal >= externalSymbolMap.length) {
            throw new IllegalStateException("scanner DSL: token '" + line.operands().get(operandIndex)
                    + "' ordinal " + ordinal + " out of range for the grammar's external symbol map");
        }
        return externalSymbolMap[ordinal];
    }

    // ------------------------------------------------------------------
    // Operand parsing
    // ------------------------------------------------------------------

    private String identifierOperand(Line line, int index) {
        requireOperand(line, index);
        String op = line.operands().get(index);
        if (!op.matches("[a-zA-Z_]\\w*")) {
            throw new IllegalStateException("scanner DSL: expected an identifier operand in '"
                    + line.mnemonic() + "', got '" + op + "'");
        }
        return op;
    }

    private int intOperand(Line line, int index) {
        requireOperand(line, index);
        String op = line.operands().get(index);
        if (op.startsWith("'") && op.endsWith("'") && op.length() >= 3) {
            return parseCharLiteral(op);
        }
        try {
            if (op.startsWith("0x") || op.startsWith("0X")) {
                return Integer.parseInt(op.substring(2), 16);
            }
            return Integer.parseInt(op);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("scanner DSL: malformed integer operand in '"
                    + line.mnemonic() + "': " + op);
        }
    }

    private byte[] stringOperand(Line line, int index) {
        requireOperand(line, index);
        String op = line.operands().get(index);
        if (!(op.startsWith("\"") && op.endsWith("\"")) || op.length() < 2) {
            throw new IllegalStateException("scanner DSL: expected a string operand in '"
                    + line.mnemonic() + "', got '" + op + "'");
        }
        StringBuilder sb = new StringBuilder();
        String inner = op.substring(1, op.length() - 1);
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length()) {
                char n = inner.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '0' -> sb.append('\0');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    default -> {
                        if (n == 'u' && i + 4 < inner.length()) {
                            sb.append((char) Integer.parseInt(inner.substring(i + 1, i + 5), 16));
                            i += 4;
                        } else if (n == 'x' && i + 2 < inner.length()) {
                            sb.append((char) Integer.parseInt(inner.substring(i + 1, i + 3), 16));
                            i += 2;
                        } else {
                            throw new IllegalStateException("scanner DSL: unknown escape \\" + n);
                        }
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int parseCharLiteral(String op) {
        String inner = op.substring(1, op.length() - 1);
        if (inner.length() == 1) {
            return inner.charAt(0);
        }
        if (inner.startsWith("\\")) {
            char esc = inner.charAt(1);
            return switch (esc) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case '0' -> 0;
                case '\\' -> '\\';
                case '\'' -> '\'';
                case '"' -> '"';
                case 'u' -> {
                    if (inner.length() < 6) {
                        throw new IllegalStateException("malformed \\u escape: " + op);
                    }
                    yield Integer.parseInt(inner.substring(2, 6), 16);
                }
                case 'x' -> {
                    if (inner.length() < 4) {
                        throw new IllegalStateException("malformed \\x escape: " + op);
                    }
                    yield Integer.parseInt(inner.substring(2, 4), 16);
                }
                default -> throw new IllegalStateException("unsupported char escape: " + op);
            };
        }
        throw new IllegalStateException("malformed char literal: " + op);
    }

    private static void requireOperand(Line line, int index) {
        if (index >= line.operands().size()) {
            throw new IllegalStateException("scanner DSL: '" + line.mnemonic() + "' needs "
                    + (index + 1) + " operand(s)");
        }
    }

    private void writeU16(int v) {
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private void writeI32(int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private record Line(String mnemonic, List<String> operands, boolean label) {

        boolean isLabel() {
            return label;
        }
    }
}