package io.nop.treesitter.scanner;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;

import java.util.Arrays;

/**
 * Interpreter for the external-scanner bytecode (the roadmap ISA documented in
 * {@code blob-format.md} §22), mirroring the C runtime's
 * {@code ts_lexer_external_scan}: given a parse state's external lex state, the
 * valid external-token ordinals are computed from the scanner states matrix,
 * the symbol map and the parse-table actions, the program runs against the
 * lexer cursor, and a successful {@code EMIT} yields the external token
 * {@code (symbol id, start, mark_end)} exactly as the C scanner would:
 * {@code start} is the skip-adjusted token start clamped to at most
 * {@code mark_end} (C {@code ts_lexer_finish}), so tokens whose content was
 * entirely skipped are zero-width.
 *
 * <p>Failure modes are all explicit: an unknown opcode, an out-of-range
 * program counter, a call-stack or operand-stack underflow/overflow, or an
 * exhausted step budget raise {@link TreeSitterException} with the program
 * offset — no silent fallback inside the VM (the caller decides whether to
 * fall back to the internal DFA lexer, per the C runtime).</p>
 */
public final class ScannerVM {

    private static final int MAX_STACK = 256;

    private byte[] program;
    private byte[] source;
    private boolean[] validSymbols;

    private int pc;
    private int position;
    private int tokenStart;
    private int markEnd;
    private int resultSymbol = -1;
    private int result;
    private int flag;
    private int state;
    private int steps;

    private final int[] callStack = new int[ScannerProgram.maxCallDepth()];
    private int callDepth;

    private final int[] operandStack = new int[MAX_STACK];
    private int operandTop;

    private ScannerVM(byte[] program, byte[] source, int position, boolean[] validSymbols) {
        reset(program, source, position, validSymbols);
    }

    private void reset(byte[] program, byte[] source, int position, boolean[] validSymbols) {
        this.program = program;
        this.source = source;
        this.position = position;
        this.validSymbols = validSymbols;
        this.tokenStart = position;
        this.markEnd = position;
        this.pc = 0;
        this.resultSymbol = -1;
        this.result = 0;
        this.flag = 0;
        this.state = 0;
        this.steps = 0;
        this.callDepth = 0;
        this.operandTop = 0;
    }

    /**
     * Scans at {@code position} for {@code parseState}: valid ordinals come from
     * the language's external scanner tables filtered by parse-table actions
     * (C {@code ts_language_has_actions}). Returns the external token, or null
     * when the scanner rejects (the caller falls back to the internal lexer).
     */
    public static Result scan(Language language, byte[] source, int position, int parseState) {
        byte[] program = language.validatedScannerProgram();
        if (program.length == 0) {
            return null;
        }
        return run(program, source, position, language.validSymbols(parseState));
    }

    /**
     * The valid external-token ordinals for a parse state, mirroring
     * {@code ts_lexer_external_scan}: {@code states[external_lex_state][ordinal]}
     * gated by {@code has_actions(state, symbol_map[ordinal])}.
     */
    public static boolean[] validSymbols(Language language, int parseState) {
        int extState = language.externalLexState(parseState);
        boolean[][] states = language.externalStates();
        int[] symbolMap = language.externalSymbolMap();
        boolean[] valid = new boolean[symbolMap.length];
        if (extState == 0 || extState >= states.length) {
            return valid;
        }
        for (int ordinal = 0; ordinal < symbolMap.length; ordinal++) {
            valid[ordinal] = states[extState][ordinal] && language.hasActions(parseState, symbolMap[ordinal]);
        }
        return valid;
    }

    /**
     * Runs a program directly against raw bytes with an explicit valid-symbol
     * array (used by the token-level tests and the language-driven scan above).
     * The program must have passed {@link ScannerProgram#validate} (the
     * language path validates once per language, not per scan). Returns the
     * token or null on scan failure.
     */
    private static final ThreadLocal<ScannerVM> POOL = new ThreadLocal<>();

    public static Result run(byte[] program, byte[] source, int position, boolean[] validSymbols) {
        ScannerVM vm = POOL.get();
        if (vm == null) {
            vm = new ScannerVM(program, source, position, validSymbols);
            POOL.set(vm);
        } else {
            vm.reset(program, source, position, validSymbols);
        }
        try {
            vm.execute();
            if (vm.resultSymbol < 0) {
                return null;
            }
            // C ts_lexer_finish: when the scanner skipped past mark_end, the token
            // start is clamped back down to the end (zero-width token).
            return new Result(vm.resultSymbol, Math.min(vm.tokenStart, vm.markEnd), vm.markEnd);
        } finally {
            // Drop the input references so the pooled VM does not pin the last
            // scanned source bytes across calls (a long-lived thread would
            // otherwise hold them indefinitely). reset() reassigns all three.
            vm.program = null;
            vm.source = null;
            vm.validSymbols = null;
        }
    }

    private void execute() {
        int budget = Math.max(program.length * 16, (source.length - position) * 4 + 128);
        while (pc < program.length) {
            if (++steps > budget) {
                throw new TreeSitterException("external scanner step budget exceeded at program offset "
                        + pc + " (input position " + position + ")");
            }
            int op = program[pc] & 0xFF;
            switch (op) {
                case ScannerProgram.FAIL -> {
                    resultSymbol = -1;
                    return;
                }
                case ScannerProgram.JMP -> {
                    pc = u16(pc + 1);
                }
                case ScannerProgram.JMP_IF_EQ -> {
                    int v = i32(pc + 1);
                    int target = u16(pc + 5);
                    pc = lookahead() == v ? pc + 7 : target;
                }
                case ScannerProgram.JMP_IF_NE -> {
                    int v = i32(pc + 1);
                    int target = u16(pc + 5);
                    pc = lookahead() != v ? pc + 7 : target;
                }
                case ScannerProgram.JMP_IF_IN_RANGE -> {
                    int lo = i32(pc + 1);
                    int hi = i32(pc + 5);
                    int target = u16(pc + 9);
                    int l = lookahead();
                    pc = l >= lo && l <= hi ? pc + 11 : target;
                }
                case ScannerProgram.JMP_IF_WS -> {
                    int target = u16(pc + 1);
                    pc = isWhitespace(lookahead()) ? pc + 3 : target;
                }
                case ScannerProgram.JMP_IF_ALPHA -> {
                    int target = u16(pc + 1);
                    pc = Character.isLetter(lookahead()) ? pc + 3 : target;
                }
                case ScannerProgram.JMP_IF_DIGIT -> {
                    int target = u16(pc + 1);
                    int l = lookahead();
                    pc = l >= '0' && l <= '9' ? pc + 3 : target;
                }
                case ScannerProgram.JMP_IF_VALID -> {
                    int ordinal = program[pc + 1] & 0xFF;
                    int target = u16(pc + 2);
                    pc = ordinal < validSymbols.length && validSymbols[ordinal] ? pc + 4 : target;
                }
                case ScannerProgram.JMP_IF_STATE_EQ -> {
                    int v = i32(pc + 1);
                    int target = u16(pc + 5);
                    pc = state == v ? pc + 7 : target;
                }
                case ScannerProgram.SET_STATE -> {
                    state = i32(pc + 1);
                    pc += 5;
                }
                case ScannerProgram.ADVANCE -> {
                    advance(false);
                    pc += 1;
                }
                case ScannerProgram.SKIP -> {
                    advance(true);
                    pc += 1;
                }
                case ScannerProgram.MARK_END -> {
                    markEnd = position;
                    pc += 1;
                }
                case ScannerProgram.EMIT -> {
                    resultSymbol = u16(pc + 1);
                    return;
                }
                case ScannerProgram.CALL -> {
                    if (callDepth == callStack.length) {
                        throw new TreeSitterException("external scanner call stack overflow at program offset " + pc);
                    }
                    callStack[callDepth++] = pc + 3;
                    result = 0;
                    pc = u16(pc + 1);
                }
                case ScannerProgram.RET -> {
                    if (callDepth == 0) {
                        // Top-level return = the C scan() returned false — no token.
                        resultSymbol = -1;
                        return;
                    }
                    pc = callStack[--callDepth];
                }
                case ScannerProgram.SET_RESULT -> {
                    result = i32(pc + 1);
                    pc += 5;
                }
                case ScannerProgram.JMP_IF_RESULT_EQ -> {
                    int v = i32(pc + 1);
                    int target = u16(pc + 5);
                    pc = result == v ? pc + 7 : target;
                }
                case ScannerProgram.SET_FLAG -> {
                    flag = program[pc + 1] & 0xFF;
                    pc += 2;
                }
                case ScannerProgram.JMP_IF_FLAG_EQ -> {
                    int v = program[pc + 1] & 0xFF;
                    int target = u16(pc + 2);
                    pc = flag == v ? pc + 4 : target;
                }
                case ScannerProgram.PUSH_BYTE -> {
                    pushOperand(program[pc + 1] & 0xFF);
                    pc += 2;
                }
                case ScannerProgram.PUSH_BYTES -> {
                    int len = u16(pc + 1);
                    if (pc + 3 + len > program.length) {
                        throw new TreeSitterException("external scanner PUSH_BYTES past program end at offset " + pc);
                    }
                    for (int i = 0; i < len; i++) {
                        pushOperand(program[pc + 3 + i] & 0xFF);
                    }
                    pc += 3 + len;
                }
                case ScannerProgram.SPAN -> {
                    int len = program[pc + 1] & 0xFF;
                    int failTarget = u16(pc + 2);
                    if (len > operandTop) {
                        throw new TreeSitterException("external scanner SPAN underflow at program offset "
                                + pc + " (pops " + len + ", stack has " + operandTop + ")");
                    }
                    boolean matched = true;
                    for (int i = 0; i < len; i++) {
                        int expected = operandStack[operandTop - len + i];
                        if (peekByte() != expected) {
                            matched = false;
                            break;
                        }
                        advance(false);
                    }
                    operandTop -= len;
                    pc = matched ? pc + 4 : failTarget;
                }
                default -> throw new TreeSitterException("external scanner unknown opcode 0x"
                        + Integer.toHexString(op) + " at program offset " + pc);
            }
        }
        // Fell off the end of the program without emitting — scan failure.
        resultSymbol = -1;
    }

    private int lookahead() {
        int[] dec = decodeCodepoint(source, position);
        return dec == null ? 0 : dec[0];
    }

    /** Raw source byte at the cursor, 0 past the end (ASCII literals only in SPAN). */
    private int peekByte() {
        return position < source.length ? source[position] & 0xFF : 0;
    }

    private void advance(boolean skip) {
        if (position >= source.length) {
            return;
        }
        int[] dec = decodeCodepoint(source, position);
        position += dec == null ? 1 : dec[1];
        if (skip) {
            // C ts_lexer__advance: skipped characters become token padding.
            tokenStart = position;
        }
    }

    private void pushOperand(int b) {
        if (operandTop == operandStack.length) {
            throw new TreeSitterException("external scanner operand stack overflow at program offset " + pc);
        }
        operandStack[operandTop++] = b;
    }

    private int u16(int off) {
        return ((program[off] & 0xFF) << 8) | (program[off + 1] & 0xFF);
    }

    private int i32(int off) {
        return (program[off] << 24) | ((program[off + 1] & 0xFF) << 16)
                | ((program[off + 2] & 0xFF) << 8) | (program[off + 3] & 0xFF);
    }

    private static boolean isWhitespace(int c) {
        return (c >= 0x09 && c <= 0x0D) || c == 0x20 || c == 0x85 || c == 0xA0 || c == 0x1680
                || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F
                || c == 0x205F || c == 0x3000;
    }

    private static int[] decodeCodepoint(byte[] source, int p) {
        int len = source.length;
        if (p >= len) {
            return null;
        }
        int b0 = source[p] & 0xFF;
        if (b0 < 0x80) {
            return new int[]{b0, 1};
        }
        if ((b0 & 0xE0) == 0xC0 && p + 1 < len) {
            int b1 = source[p + 1] & 0xFF;
            if ((b1 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x1F) << 6) | (b1 & 0x3F), 2};
            }
            return new int[]{b0, 1};
        }
        if ((b0 & 0xF0) == 0xE0 && p + 2 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F), 3};
            }
            return new int[]{b0, 1};
        }
        if ((b0 & 0xF8) == 0xF0 && p + 3 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            int b3 = source[p + 3] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80 && (b3 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12)
                        | ((b2 & 0x3F) << 6) | (b3 & 0x3F), 4};
            }
            return new int[]{b0, 1};
        }
        return new int[]{b0, 1};
    }

    /** The external token produced by a successful scan. */
    public record Result(int symbol, int startOffset, int endOffset) {

        /**
         * The C runtime's token span after {@code ts_lexer_finish}: start is the
         * skip-adjusted token start clamped to at most the marked end, so a token
         * whose content was entirely skipped (e.g. ASI) is zero-width.
         */
        public int size() {
            return endOffset - startOffset;
        }

        public byte[] bytes(byte[] source) {
            return Arrays.copyOfRange(source, startOffset, endOffset);
        }
    }
}