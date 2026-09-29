package io.nop.bytecode.kernel.dataflow;

import io.nop.bytecode.kernel.cfg.MethodCfg;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;

/**
 * Forward worklist solver to a fixpoint. The semantics ({@link DataflowSemantics}) owns all
 * lattice knowledge; the solver only schedules: transfer per instruction, then one
 * {@link DataflowSemantics#edgeFrame} call per successor — the semantics mutates the per-edge
 * frame copy in place (branch refinement, handler-entry shape), the solver merges it into the
 * target's in-frame.
 *
 * <p>Result: {@code inFrame(i)} is the merged input state of instruction {@code i} (null for
 * unreachable instructions); {@code outFrame(i)} the post-transfer state.
 */
public final class ForwardSolver {

    private final MethodNode mn;
    private final MethodCfg cfg;
    private final DataflowSemantics sem;
    private final Frame[] inFrames;
    private final Frame[] outFrames;
    private final ArrayDeque<Integer> work = new ArrayDeque<>();

    public ForwardSolver(MethodNode mn, MethodCfg cfg, DataflowSemantics sem) {
        this.mn = mn;
        this.cfg = cfg;
        this.sem = sem;
        this.inFrames = new Frame[cfg.instructionCount()];
        this.outFrames = new Frame[cfg.instructionCount()];
    }

    public Frame inFrame(int index) {
        return inFrames[index];
    }

    public Frame outFrame(int index) {
        return outFrames[index];
    }

    public void solve() {
        if (cfg.instructionCount() == 0) return;
        inFrames[0] = sem.initialFrame(mn);
        work.add(0);

        while (!work.isEmpty()) {
            int i = work.poll();
            Frame cur = inFrames[i].copy();
            if (mn.instructions.get(i).getOpcode() != -1) {
                sem.transfer(cur, i);
            }
            outFrames[i] = cur;

            for (int t : cfg.successors(i)) {
                Frame edge = cur.copy();
                // semantics mutates the per-edge copy: branch refinement / handler-entry shape
                sem.edgeFrame(edge, i, t, cfg.isHandlerHead(t));
                merge(inFrames, work, t, edge);
            }
        }
    }

    private void merge(Frame[] inFrames, ArrayDeque<Integer> work, int target, Frame out) {
        if (inFrames[target] == null) {
            inFrames[target] = out.copy();
            work.add(target);
            return;
        }
        Frame merged = inFrames[target].copy();
        if (merged.mergeFrom(sem, out)) {
            inFrames[target] = merged;
            work.add(target);
        }
    }
}
