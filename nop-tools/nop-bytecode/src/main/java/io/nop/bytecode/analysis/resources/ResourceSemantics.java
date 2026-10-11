package io.nop.bytecode.analysis.resources;

import io.nop.bytecode.NopBytecodeException;
import io.nop.bytecode.kernel.dataflow.DataflowSemantics;
import io.nop.bytecode.kernel.dataflow.Frame;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Resource obligation semantics (plan 06 algorithm spec): tracked resource references carry
 * immutable {@link ObligationToken}s in frame slots; acquire creates a DEF token at the NEW
 * (or registry factory) site; close() on a tracked reference discharges the site via a
 * full-frame sweep (alias-correct); ownership transfer (ARETURN / field store / whitelist
 * wrapper constructor argument) discharges too; RETURN-family and explicit ATHROW paths with
 * residual open sites are findings.
 *
 * <p>Guard-form exemption: a DEF token compared against null makes the null branch
 * unreachable — dropped via {@code edgeFrame} (the $assertionsDisabled drop precedent),
 * which is what makes guarded finally-close and Java 9 existing-var TWR clean.
 *
 * <p>Known FN face (declared): implicit exception propagation exits (a call that throws with
 * no handler has no exit instruction on the CFG) are not reported in v1.
 */
public final class ResourceSemantics implements DataflowSemantics {

    private final MethodNode mn;
    private final String className;
    private final String methodName;
    private final ResourceRegistry registry;
    private final OwnershipWhitelist ownershipWhitelist = new OwnershipWhitelist();
    private final Consumer<UnclosedResourceFinding> findings;

    // per-instruction edge state (single-threaded per solve)
    private boolean suppressNullSideOfDefToken;

    // acquire site -> resource type (for finding refs)
    private final Map<Integer, String> siteTypes = new HashMap<>();

    public ResourceSemantics(MethodNode mn, String className, String methodName,
                             ResourceRegistry registry, Consumer<UnclosedResourceFinding> findings) {
        this.mn = mn;
        this.className = className;
        this.methodName = methodName;
        this.registry = registry;
        this.findings = findings;
    }

    @Override
    public Frame initialFrame(MethodNode method) {
        Frame f = new Frame(method.maxLocals + method.maxStack + 2, method.maxLocals);
        int slot = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            f.setLocal(slot++, ObligationToken.PLAIN_NONNULL);
        }
        for (org.objectweb.asm.Type t : org.objectweb.asm.Type.getArgumentTypes(method.desc)) {
            if (t.getSort() == org.objectweb.asm.Type.OBJECT || t.getSort() == org.objectweb.asm.Type.ARRAY) {
                f.setLocal(slot, ObligationToken.PLAIN_MAYBE);
            }
            slot += t.getSize();
        }
        return f;
    }

    @Override
    public void transfer(Frame f, int insnIndex) {
        suppressNullSideOfDefToken = false;
        AbstractInsnNode in = mn.instructions.get(insnIndex);
        int op = in.getOpcode();
        switch (op) {
            case Opcodes.NOP, Opcodes.IINC, Opcodes.GOTO, Opcodes.RET, Opcodes.CHECKCAST -> { }
            case Opcodes.ACONST_NULL -> f.push(this, ObligationToken.PLAIN_NULL);
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                 Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                 Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2,
                 Opcodes.BIPUSH, Opcodes.SIPUSH -> transferCat1Push(f);
            case Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1 -> f.push2(this, ObligationToken.TOP);
            case Opcodes.LDC -> {
                Object cst = ((LdcInsnNode) in).cst;
                if (cst instanceof Long || cst instanceof Double) f.push2(this, ObligationToken.TOP);
                else f.push(this, ObligationToken.PLAIN_NONNULL);
            }
            case Opcodes.ILOAD, Opcodes.FLOAD -> f.push(this, ObligationToken.PLAIN_MAYBE);
            case Opcodes.ALOAD -> f.push(this, f.local(varOf(in)));
            case Opcodes.LLOAD, Opcodes.DLOAD -> f.push2(this, ObligationToken.TOP);
            case Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> f.setLocal(varOf(in), f.pop());
            case Opcodes.LSTORE, Opcodes.DSTORE -> {
                f.pop();
                f.setLocal(varOf(in), f.pop());
            }
            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD,
                 Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> {
                f.pop(2);
                f.push(this, ObligationToken.PLAIN_MAYBE);
                if (op == Opcodes.LALOAD || op == Opcodes.DALOAD) f.push(this, ObligationToken.TOP);
            }
            case Opcodes.AALOAD -> {
                f.pop(2);
                f.push(this, ObligationToken.PLAIN_MAYBE);
            }
            case Opcodes.LASTORE, Opcodes.DASTORE -> f.pop(4);
            case Opcodes.IASTORE, Opcodes.FASTORE, Opcodes.AASTORE,
                 Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> f.pop(3);
            case Opcodes.POP -> f.pop();
            case Opcodes.POP2 -> f.pop(2);
            case Opcodes.DUP -> f.push(this, f.peekTop());
            case Opcodes.DUP_X1 -> {
                Object v1 = f.pop(), v2 = f.pop();
                f.push(this, v1); f.push(this, v2); f.push(this, v1);
            }
            case Opcodes.DUP_X2 -> {
                Object v1 = f.pop(), v2 = f.pop(), v3 = f.pop();
                f.push(this, v1); f.push(this, v3); f.push(this, v2); f.push(this, v1);
            }
            case Opcodes.DUP2 -> {
                Object v1 = f.slot(f.sp() - 1), v2 = f.slot(f.sp() - 2);
                f.push(this, v2); f.push(this, v1);
            }
            case Opcodes.DUP2_X1 -> {
                Object v1 = f.slot(f.sp() - 1), v2 = f.slot(f.sp() - 2), v3 = f.slot(f.sp() - 3);
                f.pop(3);
                f.push(this, v2); f.push(this, v1); f.push(this, v3); f.push(this, v2); f.push(this, v1);
            }
            case Opcodes.DUP2_X2 -> {
                Object v1 = f.slot(f.sp() - 1), v2 = f.slot(f.sp() - 2), v3 = f.slot(f.sp() - 3), v4 = f.slot(f.sp() - 4);
                f.pop(4);
                f.push(this, v2); f.push(this, v1); f.push(this, v4); f.push(this, v3); f.push(this, v2); f.push(this, v1);
            }
            case Opcodes.SWAP -> {
                Object v1 = f.pop(), v2 = f.pop();
                f.push(this, v1); f.push(this, v2);
            }
            case Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
                 Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR,
                 Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.FADD, Opcodes.FSUB,
                 Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM -> transferCat1Binary(f, op);
            case Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
                 Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR,
                 Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM -> {
                f.pop(4);
                f.push2(this, ObligationToken.TOP);
            }
            case Opcodes.LCMP, Opcodes.DCMPL, Opcodes.DCMPG -> {
                f.pop(4);
                f.push(this, ObligationToken.PLAIN_NONNULL);
            }
            case Opcodes.FCMPL, Opcodes.FCMPG -> {
                f.pop(2);
                f.push(this, ObligationToken.PLAIN_NONNULL);
            }
            case Opcodes.I2L, Opcodes.I2D, Opcodes.F2L, Opcodes.F2D -> {
                f.pop();
                f.push2(this, ObligationToken.TOP);
            }
            case Opcodes.L2I, Opcodes.L2F, Opcodes.D2I, Opcodes.D2F -> {
                f.pop(2);
                f.push(this, ObligationToken.PLAIN_MAYBE);
            }
            case Opcodes.L2D, Opcodes.D2L -> {
                f.pop(2);
                f.push2(this, ObligationToken.TOP);
            }
            case Opcodes.I2F, Opcodes.F2I, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S,
                 Opcodes.INEG, Opcodes.FNEG -> {
                f.pop();
                f.push(this, ObligationToken.PLAIN_MAYBE);
            }
            case Opcodes.LNEG, Opcodes.DNEG -> {
                f.pop(2);
                f.push2(this, ObligationToken.TOP);
            }
            case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
                 Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE -> f.pop(2);
            case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE -> f.pop();
            case Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                Object popped = f.pop();
                if (popped instanceof ObligationToken t && t.isDefTracked()) {
                    // fresh DEF token compared against null: the null branch is unreachable —
                    // drop it (guard-form exemption: guarded finally / existing-var TWR)
                    suppressNullSideOfDefToken = true;
                }
            }
            case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> f.pop(2);
            case Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH -> f.pop();
            case Opcodes.IRETURN, Opcodes.FRETURN -> {
                f.pop();
                reportResidualOpen(ownerlessScan(f), insnIndex);
            }
            case Opcodes.ARETURN -> {
                Object v = f.pop();
                dischargeEverywhere(f, sitesOf(v)); // ownership transfer to caller
                // other resources still open on this return path leak (RETURN-family parity)
                reportResidualOpen(ownerlessScan(f), insnIndex);
            }
            case Opcodes.LRETURN, Opcodes.DRETURN -> {
                f.pop(2);
                reportResidualOpen(ownerlessScan(f), insnIndex);
            }
            case Opcodes.RETURN -> reportResidualOpen(ownerlessScan(f), insnIndex);
            case Opcodes.ATHROW -> {
                f.pop();
                // explicit throw path: residual open obligations leak (exception-path leak)
                reportResidualOpen(ownerlessScan(f), insnIndex);
            }
            case Opcodes.GETSTATIC -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                if (org.objectweb.asm.Type.getType(fin.desc).getSize() == 2) f.push2(this, ObligationToken.TOP);
                else f.push(this, ObligationToken.PLAIN_MAYBE);
            }
            case Opcodes.PUTSTATIC -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                int vs = org.objectweb.asm.Type.getType(fin.desc).getSize();
                Object v = f.slot(f.sp() - vs);
                f.pop(vs);
                dischargeEverywhere(f, sitesOf(v)); // static field escape = ownership transfer
            }
            case Opcodes.GETFIELD -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                f.pop();
                if (org.objectweb.asm.Type.getType(fin.desc).getSize() == 2) f.push2(this, ObligationToken.TOP);
                else f.push(this, ObligationToken.PLAIN_MAYBE);
            }
            case Opcodes.PUTFIELD -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                int vs = org.objectweb.asm.Type.getType(fin.desc).getSize();
                Object v = f.slot(f.sp() - vs);
                f.pop(vs);
                f.pop();
                dischargeEverywhere(f, sitesOf(v)); // field store = ownership transfer to this object
            }
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE -> {
                MethodInsnNode mi = (MethodInsnNode) in;
                org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(mi.desc);
                // whitelist wrapper constructor: capture tracked argument sites BEFORE popping,
                // then discharge them across the frame (ownership transfer to the wrapper)
                SortedSet<Integer> wrapperSites = new TreeSet<>();
                if (op == Opcodes.INVOKESPECIAL && "<init>".equals(mi.name)
                        && ownershipWhitelist.isOwnershipWrapper(mi.owner)) {
                    int sp = f.sp();
                    for (org.objectweb.asm.Type t : argTypes) {
                        int slots = t.getSize();
                        int argPos = sp - slots; // first slot of this arg (walked below)
                        sp -= slots;
                    }
                    // re-walk capturing srcs-derived sites: scan arg slots via sitesOf on slots
                    int pos = f.sp();
                    for (int a = argTypes.length - 1; a >= 0; a--) {
                        int sz = argTypes[a].getSize();
                        pos -= sz;
                        Object v = f.slot(pos);
                        wrapperSites.addAll(sitesOf(v));
                    }
                }
                popArgs(f, argTypes);
                Object recv = f.pop();
                if (isCloseCall(mi)) {
                    dischargeEverywhere(f, sitesOf(recv));
                } else if (op == Opcodes.INVOKESPECIAL && "<init>".equals(mi.name)) {
                    dischargeEverywhere(f, wrapperSites);
                    pushConstructorResult(f, mi, recv, insnIndex);
                } else {
                    pushPlainReturn(f, org.objectweb.asm.Type.getReturnType(mi.desc));
                }
            }
            case Opcodes.INVOKESTATIC -> {
                MethodInsnNode mi = (MethodInsnNode) in;
                popArgs(f, org.objectweb.asm.Type.getArgumentTypes(mi.desc));
                if (registry.isFactoryAcquire(mi.owner, mi.name)) {
                    int site = insnIndex;
                    siteTypes.put(site, mi.owner);
                    f.push(this, ObligationToken.tracked(ObligationToken.NullState.DEF, site));
                } else {
                    pushPlainReturn(f, org.objectweb.asm.Type.getReturnType(mi.desc));
                }
            }
            case Opcodes.INVOKEDYNAMIC -> {
                InvokeDynamicInsnNode id = (InvokeDynamicInsnNode) in;
                popArgs(f, org.objectweb.asm.Type.getArgumentTypes(id.desc));
                org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(id.desc);
                if (ret.getSize() == 2) f.push2(this, ObligationToken.TOP);
                else if (ret.getSort() != org.objectweb.asm.Type.VOID) f.push(this, ObligationToken.PLAIN_MAYBE);
            }
            case Opcodes.NEW -> f.push(this, ObligationToken.NEW_MARKER);
            case Opcodes.NEWARRAY, Opcodes.ANEWARRAY -> {
                f.pop();
                f.push(this, ObligationToken.PLAIN_NONNULL);
            }
            case Opcodes.MULTIANEWARRAY -> {
                f.pop(((MultiANewArrayInsnNode) in).dims);
                f.push(this, ObligationToken.PLAIN_NONNULL);
            }
            case Opcodes.ARRAYLENGTH -> {
                f.pop();
                f.push(this, ObligationToken.PLAIN_NONNULL);
            }
            case Opcodes.INSTANCEOF -> {
                f.pop();
                f.push(this, ObligationToken.PLAIN_NONNULL);
            }
            case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> f.pop();
            default -> throw new NopBytecodeException("Unhandled opcode " + op + " in "
                    + className + "." + methodName + " @insn " + insnIndex);
        }
    }

    private void transferCat1Push(Frame f) {
        f.push(this, ObligationToken.PLAIN_NONNULL);
    }

    private void transferCat1Binary(Frame f, int op) {
        if (op == Opcodes.LSHL || op == Opcodes.LSHR || op == Opcodes.LUSHR) {
            f.pop(3);          // long(2) + int(1)
            f.push2(this, ObligationToken.TOP); // long result
            return;
        }
        f.pop(2);
        f.push(this, ObligationToken.PLAIN_NONNULL);
    }

    private void pushConstructorResult(Frame f, MethodInsnNode mi, Object recv, int insnIndex) {
        if (recv == ObligationToken.NEW_MARKER) {
            // NEW+DUP+<init>: <init> is void and the DUP copy of the marker IS the constructed
            // reference — transform remaining markers in place, push NOTHING (pushing here adds
            // an extra slot per constructor call — audit-found corpus-wide shape drift)
            boolean registered = registry.isNewResourceType(mi.owner);
            if (registered) siteTypes.put(insnIndex, mi.owner);
            Object constructed = registered
                    ? ObligationToken.tracked(ObligationToken.NullState.DEF, insnIndex)
                    : ObligationToken.PLAIN_NONNULL;
            for (int i = 0; i < f.sp(); i++) {
                if (f.slot(i) == ObligationToken.NEW_MARKER) f.setLocal(i, constructed);
            }
            return;
        }
        // super/this ctor chain: <init> is void — receiver consumed, nothing pushed
    }

    private void pushPlainReturn(Frame f, org.objectweb.asm.Type ret) {
        if (ret.getSort() == org.objectweb.asm.Type.VOID) return;
        if (ret.getSize() == 2) f.push2(this, ObligationToken.TOP);
        else f.push(this, ObligationToken.PLAIN_MAYBE);
    }

    private boolean isCloseCall(MethodInsnNode mi) {
        return "close".equals(mi.name) && "()V".equals(mi.desc);
    }

    private void popArgs(Frame f, org.objectweb.asm.Type[] args) {
        for (int a = args.length - 1; a >= 0; a--) {
            // whitelist wrapper constructor arguments: ownership transfer to the wrapper
            f.pop(args[a].getSize());
        }
    }

    // ---- alias-correct full-frame operations ----

    private SortedSet<Integer> sitesOf(Object v) {
        if (v instanceof ObligationToken t && t.kind == ObligationToken.Kind.TRACKED && !t.openSites().isEmpty()) {
            return t.openSites();
        }
        return new TreeSet<>();
    }

    /** Discharge the given acquire sites on every frame slot (locals + stack) — alias-correct. */
    private void dischargeEverywhere(Frame f, SortedSet<Integer> sites) {
        if (sites.isEmpty()) return;
        for (int i = 0; i < f.sp(); i++) {
            Object v = f.slot(i);
            if (v instanceof ObligationToken t && t.containsAnyOf(sites)) {
                f.setLocal(i, t.without(sites));
            }
        }
    }

    /** Collect residual open sites across the whole frame (locals + stack). */
    private SortedSet<Integer> ownerlessScan(Frame f) {
        SortedSet<Integer> open = new TreeSet<>();
        for (int i = 0; i < f.sp(); i++) {
            if (f.slot(i) instanceof ObligationToken t && t.isOpen()) {
                open.addAll(t.openSites());
            }
        }
        return open;
    }

    private void reportResidualOpen(SortedSet<Integer> open, int insnIndex) {
        for (int site : open) {
            String type = siteTypes.getOrDefault(site, "resource");
            findings.accept(new UnclosedResourceFinding(className, methodName, insnIndex,
                    type + "@insn" + site));
        }
    }

    @Override
    public boolean edgeFrame(Frame edge, int insnIndex, int target, boolean targetIsHandlerHead) {
        if (targetIsHandlerHead) {
            while (edge.sp() > edge.base()) edge.pop();
            edge.push(this, ObligationToken.PLAIN_NONNULL);
            return true;
        }
        if (suppressNullSideOfDefToken) {
            // IFNULL: the null side IS the jump edge; IFNONNULL: the null side is fallthrough
            boolean isJumpEdge = (target == jumpTargetOf(insnIndex));
            boolean isNullSide = (mn.instructions.get(insnIndex).getOpcode() == Opcodes.IFNULL) == isJumpEdge;
            return !isNullSide;
        }
        return true;
    }

    private int jumpTargetOf(int insnIndex) {
        AbstractInsnNode in = mn.instructions.get(insnIndex);
        if (in instanceof JumpInsnNode j) {
            for (int k = 0; k < mn.instructions.size(); k++) {
                if (mn.instructions.get(k) == j.label) return k;
            }
        }
        return -1;
    }

    private int varOf(AbstractInsnNode in) {
        if (in instanceof org.objectweb.asm.tree.VarInsnNode v) return v.var;
        throw new NopBytecodeException("expected var instruction in " + className + "." + methodName);
    }

    @Override
    public Object merge(Object a, Object b) {
        return ObligationToken.merge((ObligationToken) a, (ObligationToken) b);
    }

    @Override
    public Object topValue() {
        return ObligationToken.TOP;
    }
}
