package io.nop.treesitter.scanner;

import io.nop.treesitter.TreeSitterException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2: ScannerVM — every opcode of the roadmap ISA has an observable
 * focused test, load-time validation rejects invalid programs, and all failure
 * modes (step budget, out-of-bounds pc, stack underflow) raise typed
 * exceptions. Programs are assembled by {@link P} against the encoding
 * documented in {@code blob-format.md} §22.
 */
class ScannerVMTest {

    private static final byte[] SRC = "aB 12@\u2028xyz".getBytes(StandardCharsets.UTF_8);

    // ------------------------------------------------------------------
    // Every opcode has an observable focused test
    // ------------------------------------------------------------------

    @Test
    void emitProducesTokenWithMarkEndSpan() {
        // mark_end at start, advance 'a', mark_end at 'a' end, emit symbol 7
        byte[] prog = new P()
                .markEnd()
                .advance()
                .markEnd()
                .emit(7)
                .bytes();
        ScannerVM.Result r = run(prog, 0, allValid(8));
        assertEquals(7, r.symbol());
        assertEquals(0, r.startOffset());
        assertEquals(1, r.endOffset());
        assertArrayEquals("a".getBytes(StandardCharsets.UTF_8), r.bytes(SRC));
    }

    @Test
    void advanceConsumesWholeCodepoint() {
        // consume the multi-byte U+2028 (3 bytes), then mark_end, then emit
        byte[] prog = new P().advance().markEnd().emit(1).bytes();
        ScannerVM.Result r = run(prog, 6, allValid(8));
        assertEquals(6, r.startOffset());
        assertEquals(9, r.endOffset(), "U+2028 is 3 bytes");
    }

    @Test
    void skipConsumesAsPaddingButSpansStayAtMarkEnd() {
        // mark_end at the start, then skip two bytes: the token stays zero-width
        byte[] prog = new P().markEnd().skip().skip().emit(3).bytes();
        ScannerVM.Result r = run(prog, 0, allValid(8));
        assertEquals(0, r.endOffset(), "skipped bytes do not extend the token");
    }

    @Test
    void jumpIfEqTakenAndNotTaken() {
        P taken = new P();
        int notTaken = taken.label();
        taken.jmpIfEq('a', notTaken).emit(1).fail();
        taken.here(notTaken).emit(99);
        assertEquals(1, run(taken.bytes(), 0, allValid(8)).symbol(), "eq branch taken falls through");

        P notTakenP = new P();
        int t = notTakenP.label();
        notTakenP.jmpIfEq('b', t).emit(7).markEnd();
        notTakenP.here(t).emit(8);
        assertEquals(8, run(notTakenP.bytes(), 0, allValid(8)).symbol(), "eq branch not taken jumps");
    }

    @Test
    void jumpIfNeTakenAndNotTaken() {
        P taken = new P();
        int dead = taken.label();
        taken.jmpIfNe('b', dead).emit(6).fail();
        taken.here(dead).emit(99);
        assertEquals(6, run(taken.bytes(), 0, allValid(8)).symbol(), "ne branch taken falls through");

        P notTaken = new P();
        int t = notTaken.label();
        notTaken.jmpIfNe('a', t).emit(7).markEnd();
        notTaken.here(t).emit(8);
        assertEquals(8, run(notTaken.bytes(), 0, allValid(8)).symbol(), "ne branch not taken jumps");
    }

    @Test
    void spanMatchesLiteralAndAdvances() {
        // push_bytes "aB", span 2 — matches source "aB" at position 0 and advances
        P p = new P();
        int done = p.label();
        p.pushBytes("aB").span(2, done).markEnd().emit(5);
        p.here(done).markEnd();
        ScannerVM.Result r = run(p.bytes(), 0, allValid(8));
        assertEquals(5, r.symbol());
        assertEquals(2, r.endOffset(), "span consumed both bytes");
    }

    @Test
    void spanMismatchJumpsToFailTarget() {
        // push_bytes "xy", span 2 — source has "aB", mismatch -> jump to the emit
        P p = new P();
        int failTarget = p.label();
        p.pushBytes("xy").span(2, failTarget).emit(6).markEnd();
        p.here(failTarget).emit(9);
        ScannerVM.Result r = run(p.bytes(), 0, allValid(8));
        assertEquals(9, r.symbol(), "span mismatch jumped to the fail target");
        assertEquals(0, r.endOffset(), "span mismatch consumed nothing before the jump");
    }

    @Test
    void callRetRoundTripsWithResultRegister() {
        // caller: call proc; result==0 falls through to fail; result==1 jumps to emit 4
        // proc: set_result 1; ret
        P p = new P();
        int proc = p.label();
        int emitLabel = p.label();
        p.call(proc).jmpIfResultEq(0, emitLabel).fail();
        p.here(emitLabel).emit(4).markEnd();
        p.here(proc).setResult(1).ret();
        assertEquals(4, run(p.bytes(), 0, allValid(8)).symbol(),
                "call/ret round-trip preserved the result register");
    }

    @Test
    void validSymbolGateBranchesOnTheOrdinalArray() {
        P p = new P();
        int fallback = p.label();
        p.jmpIfValid(0, fallback).emit(2).markEnd();
        p.here(fallback).emit(1);
        byte[] prog = p.bytes();
        assertEquals(2, run(prog, 0, new boolean[]{true, false, false, false}).symbol());
        assertEquals(1, run(prog, 0, new boolean[]{false, false, false, false}).symbol());
        assertEquals(2, run(prog, 0, new boolean[]{true}).symbol(), "out-of-range ordinal reads false");
    }

    @Test
    void stateRegisterSetAndRequire() {
        P p = new P();
        int fail = p.label();
        p.setState(3).jmpIfStateEq(3, fail).emit(7).markEnd();
        p.here(fail).emit(8);
        assertEquals(7, run(p.bytes(), 0, allValid(4)).symbol());

        P p2 = new P();
        int fail2 = p2.label();
        p2.jmpIfStateEq(3, fail2).emit(7).markEnd();
        p2.here(fail2).emit(8);
        assertEquals(8, run(p2.bytes(), 0, allValid(4)).symbol(), "initial state 0 != 3 so require branches");
    }

    @Test
    void eofLookaheadIsZero() {
        // at EOF lookahead == 0: jmp_if_eq 0 falls through to emit
        P p = new P();
        int jump = p.label();
        p.jmpIfEq(0, jump).emit(6).markEnd();
        p.here(jump).emit(5);
        assertEquals(6, run(p.bytes(), SRC.length, allValid(4)).symbol());
        assertEquals(5, run(p.bytes(), 0, allValid(4)).symbol(), "'a' != 0 so the jump is taken");
    }

    @Test
    void whitespaceAlphaDigitClasses() {
        P ws = new P();
        int wsJump = ws.label();
        ws.jmpIfWs(wsJump).emit(1).markEnd();
        ws.here(wsJump).emit(8);
        assertEquals(1, run(ws.bytes(), 2, allValid(4)).symbol(), "space is whitespace");

        P alpha = new P();
        int aJump = alpha.label();
        alpha.jmpIfAlpha(aJump).emit(2).markEnd();
        alpha.here(aJump).emit(8);
        assertEquals(2, run(alpha.bytes(), 0, allValid(4)).symbol(), "'a' is alpha");

        P digit = new P();
        int dJump = digit.label();
        digit.jmpIfDigit(dJump).emit(3).markEnd();
        digit.here(dJump).emit(8);
        assertEquals(3, run(digit.bytes(), 4, allValid(4)).symbol(), "'1' is a digit");
    }

    @Test
    void flagRegisterSetAndCompare() {
        P p = new P();
        int fail = p.label();
        p.setFlag(1).jmpIfFlagEq(1, fail).emit(9).markEnd();
        p.here(fail).emit(8);
        assertEquals(9, run(p.bytes(), 0, allValid(4)).symbol());

        P p2 = new P();
        int fail2 = p2.label();
        p2.jmpIfFlagEq(1, fail2).emit(9).markEnd();
        p2.here(fail2).emit(8);
        assertEquals(8, run(p2.bytes(), 0, allValid(4)).symbol(), "initial flag 0 != 1 so require branches");
    }

    @Test
    void jumpInRangeBranch() {
        P inRange = new P();
        int miss = inRange.label();
        inRange.jmpIfInRange('0', '9', miss).emit(1).markEnd();
        inRange.here(miss).emit(2);
        byte[] prog = inRange.bytes();
        assertEquals(1, run(prog, 4, allValid(4)).symbol(), "'1' is in [0-9]");
        assertEquals(2, run(prog, 5, allValid(4)).symbol(), "'@' is not in [0-9]");
    }

    @Test
    void spanAdvancesContentFromPushByteAndPushBytes() {
        P p = new P();
        int done = p.label();
        p.pushByte('1').pushByte('2').span(2, done).markEnd().emit(4);
        p.here(done).markEnd();
        ScannerVM.Result r = run(p.bytes(), 3, allValid(8));
        assertEquals(4, r.symbol());
        assertEquals(5, r.endOffset(), "span advanced over '12' as content");
    }

    // ------------------------------------------------------------------
    // Failure modes are typed and explicit
    // ------------------------------------------------------------------

    @Test
    void stepBudgetExhaustionRaisesTypedException() {
        P p = new P();
        int loop = p.label();
        p.here(loop).jmp(loop);  // infinite loop
        TreeSitterException ex = assertThrows(TreeSitterException.class, () -> run(p.bytes(), 0, allValid(4)));
        assertTrue(ex.getMessage().contains("step budget"), ex.getMessage());
    }

    @Test
    void unknownOpcodeRaisesAtLoadTime() {
        byte[] prog = new byte[]{0x7F, 0x00, 0x00};
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ScannerProgram.validate(prog));
        assertTrue(ex.getMessage().contains("malformed"), ex.getMessage());
    }

    @Test
    void outOfRangeJumpTargetRejectedAtLoad() {
        // JMP 50 in a 2-byte program: the target is past the end
        byte[] prog = new byte[]{ScannerProgram.JMP, 0, 50};
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ScannerProgram.validate(prog));
        assertTrue(ex.getMessage().contains("jump target"), ex.getMessage());
    }

    @Test
    void jumpIntoMiddleOfInstructionRejectedAtLoad() {
        // JMP target 4 lands inside the JMP_IF_EQ instruction (starts at 3)
        byte[] prog = {ScannerProgram.JMP, 0, 4, ScannerProgram.JMP_IF_EQ, 0, 0, 0, 0x61, 0, 0};
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ScannerProgram.validate(prog));
        assertTrue(ex.getMessage().contains("jump target"), ex.getMessage());
    }

    @Test
    void spanUnderflowRejectedAtLoadTime() {
        P p = new P();
        int done = p.label();
        p.span(3, done).emit(1);
        p.here(done).markEnd();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ScannerProgram.validate(p.bytes()));
        assertTrue(ex.getMessage().contains("SPAN"), ex.getMessage());
    }

    @Test
    void emptyProgramRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ScannerProgram.validate(new byte[0]));
        assertTrue(ex.getMessage().contains("empty"), ex.getMessage());
    }

    @Test
    void scanFailsWithNoTokenByDefault() {
        assertNull(run(new P().fail().bytes(), 0, allValid(4)));
        assertNull(run(new P().ret().bytes(), 0, allValid(4)), "RET at top level fails the scan");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ScannerVM.Result run(byte[] prog, int position, boolean[] valid) {
        return ScannerVM.run(prog, SRC, position, valid);
    }

    private static boolean[] allValid(int count) {
        boolean[] v = new boolean[count];
        java.util.Arrays.fill(v, true);
        return v;
    }

    /**
     * Two-pass assembler for the §22 encoding: instructions write raw bytes, a
     * {@code label()} returns a symbolic id, {@code here(id)} binds it to the
     * current offset, and branch operands are resolved on {@code bytes()}.
     */
    private static final class P {
        private final List<byte[]> parts = new ArrayList<>();
        private final Map<Integer, Integer> labels = new HashMap<>();
        private final Map<Integer, Integer> patchLocs = new HashMap<>();
        private int offset;
        private int nextLabel;

        P here(int label) {
            if (labels.containsKey(label)) {
                throw new IllegalStateException("duplicate label " + label);
            }
            labels.put(label, offset);
            return this;
        }

        int label() {
            return nextLabel++;
        }

        private void bindLabel(int label) {
            patchLocs.put(label, parts.size());
        }

        P fail() {
            raw(ScannerProgram.FAIL);
            return this;
        }

        P jmp(int label) {
            raw(ScannerProgram.JMP);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfEq(int v, int label) {
            raw(ScannerProgram.JMP_IF_EQ);
            emit32(v);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfNe(int v, int label) {
            raw(ScannerProgram.JMP_IF_NE);
            emit32(v);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfInRange(int lo, int hi, int label) {
            raw(ScannerProgram.JMP_IF_IN_RANGE);
            emit32(lo);
            emit32(hi);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfWs(int label) {
            raw(ScannerProgram.JMP_IF_WS);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfAlpha(int label) {
            raw(ScannerProgram.JMP_IF_ALPHA);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfDigit(int label) {
            raw(ScannerProgram.JMP_IF_DIGIT);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfValid(int ordinal, int label) {
            raw(ScannerProgram.JMP_IF_VALID);
            raw(ordinal);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P jmpIfStateEq(int v, int label) {
            raw(ScannerProgram.JMP_IF_STATE_EQ);
            emit32(v);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P setState(int v) {
            raw(ScannerProgram.SET_STATE);
            emit32(v);
            return this;
        }

        P advance() {
            raw(ScannerProgram.ADVANCE);
            return this;
        }

        P skip() {
            raw(ScannerProgram.SKIP);
            return this;
        }

        P markEnd() {
            raw(ScannerProgram.MARK_END);
            return this;
        }

        P emit(int symbol) {
            raw(ScannerProgram.EMIT);
            emit16(symbol);
            return this;
        }

        P call(int label) {
            raw(ScannerProgram.CALL);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P ret() {
            raw(ScannerProgram.RET);
            return this;
        }

        P setResult(int v) {
            raw(ScannerProgram.SET_RESULT);
            emit32(v);
            return this;
        }

        P jmpIfResultEq(int v, int label) {
            raw(ScannerProgram.JMP_IF_RESULT_EQ);
            emit32(v);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P setFlag(int v) {
            raw(ScannerProgram.SET_FLAG);
            raw(v);
            return this;
        }

        P jmpIfFlagEq(int v, int label) {
            raw(ScannerProgram.JMP_IF_FLAG_EQ);
            raw(v);
            bindLabel(label);
            emit16(0);
            return this;
        }

        P pushByte(int b) {
            raw(ScannerProgram.PUSH_BYTE);
            raw(b);
            return this;
        }

        P pushBytes(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            raw(ScannerProgram.PUSH_BYTES);
            emit16(b.length);
            for (byte value : b) {
                raw(value);
            }
            return this;
        }

        P span(int len, int failLabel) {
            raw(ScannerProgram.SPAN);
            raw(len);
            bindLabel(failLabel);
            emit16(0);
            return this;
        }

        byte[] bytes() {
            for (Map.Entry<Integer, Integer> e : patchLocs.entrySet()) {
                Integer target = labels.get(e.getKey());
                if (target == null) {
                    throw new IllegalStateException("unresolved label " + e.getKey());
                }
                parts.get(e.getValue())[0] = (byte) (target >>> 8);
                parts.get(e.getValue())[1] = target.byteValue();
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (byte[] part : parts) {
                out.write(part, 0, part.length);
            }
            return out.toByteArray();
        }

        private void raw(int b) {
            parts.add(new byte[]{(byte) b});
            offset += 1;
        }

        private void emit16(int v) {
            parts.add(new byte[]{(byte) (v >>> 8), (byte) v});
            offset += 2;
        }

        private void emit32(int v) {
            parts.add(new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v});
            offset += 4;
        }
    }
}