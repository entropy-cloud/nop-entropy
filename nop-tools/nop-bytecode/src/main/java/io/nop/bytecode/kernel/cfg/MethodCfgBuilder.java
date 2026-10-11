package io.nop.bytecode.kernel.cfg;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.ArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link MethodCfg} from an ASM {@code MethodNode}.
 *
 * <p>Handler edges are added only from instructions lexically inside the handler's try range
 * ({@code [start, end)}) that may throw — the conservative all-to-handler shape is deliberately
 * avoided because it pollutes handler joins with unrelated stack heights. Pseudo instructions
 * (labels / frames / line numbers, opcode -1) pass through with linear edges only.
 */
public final class MethodCfgBuilder {

    public MethodCfg build(MethodNode mn) {
        int n = mn.instructions.size();
        Map<AbstractInsnNode, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) {
            idx.put(mn.instructions.get(i), i);
        }

        record Handler(int head, int rangeStart, int rangeEnd) {}
        List<Handler> handlers = new ArrayList<>();
        boolean[] handlerHead = new boolean[n];
        if (mn.tryCatchBlocks != null) {
            for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
                Integer head = idx.get(tcb.handler);
                Integer start = idx.get(tcb.start);
                Integer end = idx.get(tcb.end);
                if (head == null || start == null || end == null) continue;
                handlers.add(new Handler(head, start, end));
                handlerHead[head] = true;
            }
        }

        List<List<Integer>> successors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AbstractInsnNode in = mn.instructions.get(i);
            int op = in.getOpcode();
            List<Integer> s = new ArrayList<>(2);
            if (in instanceof JumpInsnNode j) {
                s.add(idx.get(j.label));
                if (op != Opcodes.GOTO && op != Opcodes.JSR) s.add(i + 1);
            } else if (in instanceof TableSwitchInsnNode ts) {
                s.add(idx.get(ts.dflt));
                for (AbstractInsnNode t : ts.labels) s.add(idx.get(t));
            } else if (in instanceof LookupSwitchInsnNode ls) {
                s.add(idx.get(ls.dflt));
                for (AbstractInsnNode t : ls.labels) s.add(idx.get(t));
            } else if (op == Opcodes.ATHROW || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN)) {
                // terminal for normal flow (ATHROW still reaches handlers via mayThrow below)
            } else if (op != Opcodes.RET && i + 1 < n) {
                // pseudo instructions (labels/frames/line numbers) pass through with a linear edge
                s.add(i + 1);
            }
            if (mayThrow(in)) {
                for (Handler h : handlers) {
                    if (i >= h.rangeStart && i < h.rangeEnd) s.add(h.head);
                }
            }
            successors.add(s);
        }
        return new MethodCfg(n, successors, handlerHead);
    }

    static boolean mayThrow(AbstractInsnNode in) {
        int op = in.getOpcode();
        return switch (op) {
            case Opcodes.GETFIELD, Opcodes.PUTFIELD, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL,
                 Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE, Opcodes.INVOKEDYNAMIC,
                 Opcodes.ARRAYLENGTH, Opcodes.AALOAD, Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD,
                 Opcodes.DALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.AASTORE,
                 Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.BASTORE,
                 Opcodes.CASTORE, Opcodes.SASTORE, Opcodes.NEWARRAY, Opcodes.ANEWARRAY,
                 Opcodes.MULTIANEWARRAY, Opcodes.ATHROW, Opcodes.MONITORENTER, Opcodes.MONITOREXIT,
                 Opcodes.IDIV, Opcodes.LDIV, Opcodes.IREM, Opcodes.LREM, Opcodes.CHECKCAST -> true;
            default -> false;
        };
    }

}
