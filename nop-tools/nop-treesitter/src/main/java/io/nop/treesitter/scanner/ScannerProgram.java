package io.nop.treesitter.scanner;

/**
 * The external-scanner bytecode program format (roadmap ISA, documented in
 * {@code blob-format.md} §22) plus load-time validation.
 *
 * <p>Instructions are variable-width: an opcode byte followed by
 * opcode-specific operands (all big-endian). {@link #validate} walks the whole
 * program, rejects unknown opcodes / malformed operands / out-of-range or
 * mid-instruction jump targets, and runs a fixpoint stack-height analysis over
 * the control-flow graph so an underflowing {@code SPAN} is rejected at load
 * time — no silent skip, no runtime surprise.</p>
 */
public final class ScannerProgram {

    public static final int FAIL = 0x00;
    public static final int JMP = 0x01;
    public static final int JMP_IF_EQ = 0x02;
    public static final int JMP_IF_NE = 0x03;
    public static final int JMP_IF_IN_RANGE = 0x04;
    public static final int JMP_IF_WS = 0x05;
    public static final int JMP_IF_ALPHA = 0x06;
    public static final int JMP_IF_DIGIT = 0x07;
    public static final int JMP_IF_VALID = 0x08;
    public static final int JMP_IF_STATE_EQ = 0x09;
    public static final int SET_STATE = 0x0A;
    public static final int ADVANCE = 0x0B;
    public static final int SKIP = 0x0C;
    public static final int MARK_END = 0x0D;
    public static final int EMIT = 0x0E;
    public static final int CALL = 0x0F;
    public static final int RET = 0x10;
    public static final int SET_RESULT = 0x11;
    public static final int JMP_IF_RESULT_EQ = 0x12;
    public static final int SET_FLAG = 0x13;
    public static final int JMP_IF_FLAG_EQ = 0x14;
    public static final int PUSH_BYTE = 0x15;
    public static final int PUSH_BYTES = 0x16;
    public static final int SPAN = 0x17;

    private static final int MAX_CALL_DEPTH = 64;

    private ScannerProgram() {
    }

    /**
     * Byte length of the instruction starting at {@code pc}, or a negative value
     * when {@code pc} is not an instruction start / the program ends mid-operand.
     */
    static int instructionSize(byte[] program, int pc) {
        if (pc < 0 || pc >= program.length) {
            return -1;
        }
        int op = program[pc] & 0xFF;
        return switch (op) {
            case FAIL, ADVANCE, SKIP, MARK_END, RET -> 1;
            case JMP, JMP_IF_WS, JMP_IF_ALPHA, JMP_IF_DIGIT, EMIT, CALL -> 3;
            case SET_FLAG, PUSH_BYTE -> 2;
            case JMP_IF_VALID, JMP_IF_FLAG_EQ, SPAN -> 4;
            case SET_STATE, SET_RESULT -> 5;
            case JMP_IF_EQ, JMP_IF_NE, JMP_IF_STATE_EQ, JMP_IF_RESULT_EQ -> 7;
            case JMP_IF_IN_RANGE -> 11;
            case PUSH_BYTES -> {
                if (pc + 3 > program.length) {
                    yield -1;
                }
                int len = readU16(program, pc + 1);
                yield 3 + len;
            }
            default -> -1;
        };
    }

    private static int readU16(byte[] program, int off) {
        return ((program[off] & 0xFF) << 8) | (program[off + 1] & 0xFF);
    }

    /**
     * Validates the program and returns the set of instruction-start offsets.
     * Throws {@link IllegalStateException} naming the offending offset on any
     * violation.
     */
    public static int[] validate(byte[] program) {
        if (program == null || program.length == 0) {
            throw new IllegalStateException("empty scanner program");
        }
        int[] starts = new int[program.length];
        int startCount = 0;
        int pc = 0;
        while (pc < program.length) {
            int size = instructionSize(program, pc);
            if (size < 0 || pc + size > program.length) {
                throw new IllegalStateException("scanner program: malformed instruction at offset " + pc);
            }
            starts[startCount++] = pc;
            pc += size;
        }

        boolean[] isStart = new boolean[program.length];
        for (int i = 0; i < startCount; i++) {
            isStart[starts[i]] = true;
        }

        checkJumpTargets(program, isStart);
        checkStackDiscipline(program, starts, startCount, isStart);
        return java.util.Arrays.copyOf(starts, startCount);
    }

    private static void checkJumpTargets(byte[] program, boolean[] isStart) {
        int pc = 0;
        while (pc < program.length) {
            int op = program[pc] & 0xFF;
            int[] targets = jumpTargets(program, pc);
            for (int t : targets) {
                if (t < 0 || t >= program.length || !isStart[t]) {
                    throw new IllegalStateException("scanner program: invalid jump target " + t
                            + " at offset " + pc + " (program length " + program.length + ")");
                }
            }
            pc += instructionSize(program, pc);
        }
    }

    private static int[] jumpTargets(byte[] program, int pc) {
        int op = program[pc] & 0xFF;
        return switch (op) {
            case JMP, JMP_IF_WS, JMP_IF_ALPHA, JMP_IF_DIGIT, CALL -> new int[]{readU16(program, pc + 1)};
            case JMP_IF_EQ, JMP_IF_NE, JMP_IF_STATE_EQ, JMP_IF_RESULT_EQ -> new int[]{readU16(program, pc + 5)};
            case JMP_IF_IN_RANGE -> new int[]{readU16(program, pc + 9)};
            case JMP_IF_VALID, JMP_IF_FLAG_EQ, SPAN -> new int[]{readU16(program, pc + 2)};
            default -> new int[0];
        };
    }

    /**
     * Fixpoint stack-height analysis: for every reachable program point track the
     * set of possible operand-stack heights; {@code PUSH_BYTE}/{@code PUSH_BYTES}
     * grow it, {@code SPAN} shrinks it and must never go negative. {@code CALL}
     * merges into the callee entry and the continuation with the same height
     * (procedures are balanced and must restore the stack before {@code RET}),
     * so the analysis is finite and terminates.
     */
    private static void checkStackDiscipline(byte[] program, int[] starts, int startCount, boolean[] isStart) {
        java.util.List<java.util.Set<Integer>> heights = new java.util.ArrayList<>();
        for (int i = 0; i < program.length; i++) {
            heights.add(new java.util.HashSet<>());
        }
        java.util.ArrayDeque<Integer> work = new java.util.ArrayDeque<>();
        heights.get(0).add(0);
        work.add(0);
        while (!work.isEmpty()) {
            int pc = work.poll();
            int op = program[pc] & 0xFF;
            int size = instructionSize(program, pc);
            int next = pc + size;
            int[] targets = jumpTargets(program, pc);
            for (int h : heights.get(pc)) {
                int nh = h;
                switch (op) {
                    case PUSH_BYTE -> nh = h + 1;
                    case PUSH_BYTES -> nh = h + readU16(program, pc + 1);
                    case SPAN -> {
                        int len = program[pc + 1] & 0xFF;
                        nh = h - len;
                        if (nh < 0) {
                            throw new IllegalStateException("scanner program: SPAN at offset " + pc
                                    + " pops " + len + " bytes but the operand stack can be as shallow as " + h);
                        }
                    }
                    default -> {
                    }
                }
                switch (op) {
                    case FAIL, EMIT, RET -> {
                        // terminators: no successor
                    }
                    case JMP -> enqueue(heights, work, targets[0], nh);
                    case CALL -> {
                        enqueue(heights, work, targets[0], nh);
                        enqueue(heights, work, next, nh);
                    }
                    default -> {
                        for (int t : targets) {
                            enqueue(heights, work, t, nh);
                        }
                        if (op != SPAN || nh >= 0) {
                            enqueue(heights, work, next, nh);
                        }
                    }
                }
            }
        }
    }

    private static void enqueue(java.util.List<java.util.Set<Integer>> heights,
                                java.util.ArrayDeque<Integer> work, int pc, int h) {
        if (pc < 0 || pc >= heights.size()) {
            return;
        }
        if (heights.get(pc).add(h)) {
            work.add(pc);
        }
    }

    public static int maxCallDepth() {
        return MAX_CALL_DEPTH;
    }
}