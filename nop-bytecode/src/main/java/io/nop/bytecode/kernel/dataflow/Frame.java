package io.nop.bytecode.kernel.dataflow;

import java.util.Objects;

/**
 * Slot-accurate abstract frame: locals live in {@code [0, base)}, the operand stack grows from
 * {@code base}. Category-2 values (long/double) occupy two slots (value + TOP placeholder) so
 * stack heights at join points match JVM verification exactly.
 *
 * <p>Values are opaque lattice members owned by the {@link DataflowSemantics}. The frame also
 * carries per-slot provenance ({@code srcs}: local-slot index if the value was produced by an
 * ALOAD, else -1) — the nullness analysis uses it to refine locals on null-branch edges.
 *
 * <p>Stack-shape contract: {@code push} below {@code base} and pops past {@code base} throw —
 * an empty operand stack is never silently tolerated.
 */
public final class Frame {
    private final Object[] slots;
    private final int[] srcs;
    private final int base;
    private int sp;

    public Frame(int maxSlots, int base) {
        this.slots = new Object[maxSlots];
        this.srcs = new int[maxSlots];
        this.base = base;
        this.sp = base;
    }

    private Frame(Frame proto) {
        this.slots = new Object[proto.slots.length];
        this.srcs = new int[proto.srcs.length];
        this.base = proto.base;
        this.sp = proto.sp;
    }

    public Frame copy() {
        Frame f = new Frame(this);
        System.arraycopy(slots, 0, f.slots, 0, slots.length);
        System.arraycopy(srcs, 0, f.srcs, 0, srcs.length);
        return f;
    }

    public int base() {
        return base;
    }

    public int sp() {
        return sp;
    }

    public Object slot(int i) {
        return slots[i];
    }

    public void setLocal(int var, Object value) {
        slots[var] = value;
    }

    public Object local(int var) {
        return slots[var];
    }

    public int srcOfTop() {
        return srcs[sp - 1];
    }

    /** Provenance of the slot just below the top (second operand of binary comparisons). */
    public int srcOfSecondFromTop() {
        return srcs[sp - 2];
    }

    public void push(DataflowSemantics sem, Object value) {
        if (sp < base) throw new IllegalStateException("push below stack base");
        slots[sp] = value;
        srcs[sp] = -1;
        sp++;
    }

    public void push2(DataflowSemantics sem, Object value) {
        push(sem, value);
        push(sem, sem.topValue());
    }

    public void pushFromLocal(DataflowSemantics sem, Object value, int var) {
        if (sp < base) throw new IllegalStateException("push below stack base");
        slots[sp] = value;
        srcs[sp] = var;
        sp++;
    }

    public Object pop() {
        if (sp <= base) throw new IllegalStateException("pop past stack base");
        return slots[--sp];
    }

    public void pop(int n) {
        if (sp - n < base) throw new IllegalStateException("pop past stack base");
        sp -= n;
    }

    public Object peekTop() {
        return slots[sp - 1];
    }

    /** Lattice merge of an incoming frame into this one (heights must match). */
    public boolean mergeFrom(DataflowSemantics sem, Frame in) {
        if (sp != in.sp) {
            throw new IllegalStateException("stack height mismatch at join: " + sp + " vs " + in.sp);
        }
        boolean changed = false;
        for (int i = 0; i < slots.length; i++) {
            Object m = sem.merge(slots[i], in.slots[i]);
            if (!Objects.equals(m, slots[i])) {
                slots[i] = m;
                changed = true;
            }
        }
        return changed;
    }
}
