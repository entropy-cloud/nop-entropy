package io.nop.bytecode.analysis.nullflow;

import io.nop.bytecode.NopBytecodeException;
import io.nop.bytecode.kernel.dataflow.DataflowSemantics;
import io.nop.bytecode.kernel.dataflow.Frame;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

import java.util.function.Consumer;

/**
 * Nullness lattice semantics: 3-valued nullness (NONNULL/MAYNULL/NULL; TOP = cat2 slot marker),
 * formal dialect (roadmap item 6): branch-sensitive for every condition that can carry a null
 * fact — IFNULL/IFNONNULL and ACMP-with-NULL-constant (serving {@code null == x}, cast-compare
 * and ternary-null forms; numeric branches have no null fact to refine). Exemption faces:
 * guard-branch refinement (assert / if-null-throw-NopException shapes), Objects.requireNonNull
 * semantic gate, and the $assertionsDisabled idiom modeled as assertions-always-enabled (the
 * bypass branch is dropped, so assert guards hold regardless of runtime instrumentation).
 *
 * <p>Branch refinement works on LOCALS: when the tested operand was produced by an ALOAD
 * (per-frame srcs provenance), the null/nonnull fact is written into the source local on the
 * corresponding edge; operands without provenance fall back to join-insensitive MAYNULL.
 *
 * <p>Semantic gate: {@code Objects.requireNonNull(...)} results are NONNULL. JSR/RET are not
 * supported (never emitted in v61+ class files). Unhandled opcodes throw
 * {@link NopBytecodeException} with class/method/instruction context — never silently skipped.
 */
public final class NullnessSemantics implements DataflowSemantics {

    private static final String REQ_NONNULL_OWNER = "java/util/Objects"; // ASM MethodInsnNode.owner is slash-form
    private static final String REQ_NONNULL_NAME = "requireNonNull";

    private final MethodNode mn;
    private final String className;
    private final String methodName;
    private final Consumer<DerefFinding> findings;

    // $assertionsDisabled idiom marker (pushed by GETSTATIC, consumed by the following IFNE):
    // modeling "assertions always enabled" — the bypass branch is dropped so assert guards hold
    private static final Object ASSERTIONS_DISABLED = new Object();

    private boolean suppressPendingJumpEdge;

    // per-instruction refinement stash (single-threaded per solve). Solver calls edgeFrame per
    // successor in CFG order — for a conditional jump that is [jump target, fallthrough] — so the
    // edge ordinal (0/1) identifies the branch even when target == fallthrough (empty then-body).
    private int pendingRefineInsn = -1;
    private int pendingRefineVar = -1;
    private int pendingEdgeOrdinal = 0;

    // exemption/refinement statistics (plan 05 formal-dialect FP-control data)
    private long guardRefinements;
    private long requireNonNullGates;
    private long acmpRefinements;

    public NullnessSemantics(MethodNode mn, String className, String methodName,
                             Consumer<DerefFinding> findings) {
        this.mn = mn;
        this.className = className;
        this.methodName = methodName;
        this.findings = findings;
    }

    @Override
    public Frame initialFrame(MethodNode method) {
        Frame init = new Frame(method.maxLocals + method.maxStack + 2, method.maxLocals);
        // v1: this = NONNULL, all reference params = MAYNULL; annotation-contract input arrives in Wave 5
        int slot = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            init.setLocal(slot++, Nullness.NONNULL);
        }
        for (org.objectweb.asm.Type t : org.objectweb.asm.Type.getArgumentTypes(method.desc)) {
            if (t.getSort() == org.objectweb.asm.Type.OBJECT || t.getSort() == org.objectweb.asm.Type.ARRAY) {
                init.setLocal(slot, Nullness.MAYNULL);
            }
            slot += t.getSize();
        }
        return init;
    }

    @Override
    public void transfer(Frame f, int insnIndex) {
        pendingRefineInsn = -1;
        pendingRefineVar = -1;
        AbstractInsnNode in = mn.instructions.get(insnIndex);
        int op = in.getOpcode();
        switch (op) {
            case Opcodes.NOP -> { }
            case Opcodes.ACONST_NULL -> f.push(this, Nullness.NULL);
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                 Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                 Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2,
                 Opcodes.BIPUSH, Opcodes.SIPUSH -> f.push(this, Nullness.NONNULL);
            case Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1 -> f.push2(this, Nullness.NONNULL);
            case Opcodes.LDC -> {
                Object cst = ((LdcInsnNode) in).cst;
                if (cst instanceof Long || cst instanceof Double) f.push2(this, Nullness.NONNULL);
                else f.push(this, Nullness.NONNULL);
            }
            case Opcodes.ILOAD, Opcodes.FLOAD -> f.push(this, Nullness.NONNULL);
            case Opcodes.ALOAD -> {
                int var = varOf(in);
                f.pushFromLocal(this, f.local(var), var);
            }
            case Opcodes.LLOAD, Opcodes.DLOAD -> f.push2(this, Nullness.NONNULL);
            case Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> f.setLocal(varOf(in), f.pop());
            case Opcodes.LSTORE, Opcodes.DSTORE -> {
                f.pop();
                f.setLocal(varOf(in), f.pop());
            }
            case Opcodes.IINC -> { /* no stack effect */ }
            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD,
                 Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> {
                f.pop(2);
                f.push(this, Nullness.NONNULL);
                if (op == Opcodes.LALOAD || op == Opcodes.DALOAD) f.push(this, topValue());
            }
            case Opcodes.AALOAD -> {
                Object arr = f.slot(f.sp() - 2);
                f.pop(2);
                if (arr == Nullness.MAYNULL || arr == Nullness.NULL) {
                    findings.accept(new DerefFinding(className, methodName, insnIndex, op, "[array]"));
                }
                f.push(this, Nullness.MAYNULL);
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
                 Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR -> {
                f.pop(2);
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
                 Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR,
                 Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM -> {
                f.pop(4);
                f.push2(this, Nullness.NONNULL);
            }
            case Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM -> {
                f.pop(2);
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR -> {
                f.pop(3);
                f.push2(this, Nullness.NONNULL);
            }
            case Opcodes.LCMP, Opcodes.DCMPL, Opcodes.DCMPG -> {
                f.pop(4);
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.FCMPL, Opcodes.FCMPG -> {
                f.pop(2);
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.I2L, Opcodes.I2D, Opcodes.F2L, Opcodes.F2D -> {
                f.pop();
                f.push2(this, Nullness.NONNULL);
            }
            case Opcodes.L2I, Opcodes.L2F, Opcodes.D2I, Opcodes.D2F -> {
                f.pop(2);
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.L2D, Opcodes.D2L -> {
                f.pop(2);
                f.push2(this, Nullness.NONNULL);
            }
            case Opcodes.I2F, Opcodes.F2I, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S -> {
                f.pop();
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.INEG, Opcodes.FNEG -> {
                f.pop();
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.LNEG, Opcodes.DNEG -> {
                f.pop(2);
                f.push2(this, Nullness.NONNULL);
            }
            case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
                 Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE -> f.pop(2);
            case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE -> {
                Object popped = f.pop();
                if (popped == ASSERTIONS_DISABLED) {
                    suppressPendingJumpEdge = true;
                    pendingRefineInsn = -1;
                }
            }
            case Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                int src = f.srcOfTop();
                f.pop();
                pendingRefineInsn = insnIndex;
                pendingRefineVar = src; // -1 = no provenance, join-insensitive on both edges
                pendingEdgeOrdinal = 0;
                if (src >= 0) guardRefinements++;
            }
            // ACMP with a NULL constant on one side: refine the other side's local (formal
            // dialect addition — serves `null == x`, `x == (Object) null`, ternary-null forms;
            // assert / if-null-throw are IFNULL/IFNONNULL forms and were covered by v1)
            case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                // provenance MUST be captured before the pop: after pop(2) the srcs slots point
                // below the operands (audit Blocker 2 — refining local 0 there was a latent bug)
                Object op1 = f.slot(f.sp() - 2);
                Object op2 = f.slot(f.sp() - 1);
                int src1 = f.srcOfSecondFromTop();
                int src2 = f.srcOfTop();
                f.pop(2);
                int otherSrc = -1;
                if (op1 == Nullness.NULL && op2 != Nullness.NULL && src2 >= 0) {
                    otherSrc = src2;
                } else if (op2 == Nullness.NULL && op1 != Nullness.NULL && src1 >= 0) {
                    otherSrc = src1;
                }
                if (otherSrc >= 0) {
                    pendingRefineInsn = insnIndex;
                    pendingRefineVar = otherSrc;
                    pendingEdgeOrdinal = 0;
                    acmpRefinements++;
                }
            }
            case Opcodes.GOTO -> { }
            case Opcodes.JSR, Opcodes.RET -> throw new NopBytecodeException(
                    "JSR/RET not supported by the nullness kernel (v61+ corpus never contains them): "
                            + className + "." + methodName + " @insn " + insnIndex);
            case Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH -> f.pop();
            case Opcodes.IRETURN, Opcodes.FRETURN, Opcodes.ARETURN -> f.pop();
            case Opcodes.LRETURN, Opcodes.DRETURN -> f.pop(2);
            case Opcodes.RETURN -> { }
            case Opcodes.GETSTATIC -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                if ("$assertionsDisabled".equals(fin.name)) {
                    f.push(this, ASSERTIONS_DISABLED);
                    return;
                }
                if (org.objectweb.asm.Type.getType(fin.desc).getSize() == 2) f.push2(this, Nullness.MAYNULL);
                else f.push(this, Nullness.MAYNULL);
            }
            case Opcodes.PUTSTATIC -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                f.pop(org.objectweb.asm.Type.getType(fin.desc).getSize());
            }
            case Opcodes.GETFIELD -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                Object recv = f.pop();
                if (recv == Nullness.MAYNULL || recv == Nullness.NULL) {
                    findings.accept(new DerefFinding(className, methodName, insnIndex, op, fin.owner + "#" + fin.name));
                }
                if (org.objectweb.asm.Type.getType(fin.desc).getSize() == 2) f.push2(this, Nullness.MAYNULL);
                else f.push(this, Nullness.MAYNULL);
            }
            case Opcodes.PUTFIELD -> {
                FieldInsnNode fin = (FieldInsnNode) in;
                f.pop(org.objectweb.asm.Type.getType(fin.desc).getSize());
                Object recv = f.pop();
                if (recv == Nullness.MAYNULL || recv == Nullness.NULL) {
                    findings.accept(new DerefFinding(className, methodName, insnIndex, op, fin.owner + "#" + fin.name));
                }
            }
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE -> {
                MethodInsnNode mi = (MethodInsnNode) in;
                popArgs(f, org.objectweb.asm.Type.getArgumentTypes(mi.desc));
                Object recv = f.pop();
                if (recv == Nullness.MAYNULL || recv == Nullness.NULL) {
                    findings.accept(new DerefFinding(className, methodName, insnIndex, op, mi.owner + "." + mi.name));
                }
                pushRet(f, org.objectweb.asm.Type.getReturnType(mi.desc), mi.owner, mi.name);
            }
            case Opcodes.INVOKESTATIC -> {
                MethodInsnNode mi = (MethodInsnNode) in;
                popArgs(f, org.objectweb.asm.Type.getArgumentTypes(mi.desc));
                pushRet(f, org.objectweb.asm.Type.getReturnType(mi.desc), mi.owner, mi.name);
            }
            case Opcodes.INVOKEDYNAMIC -> {
                InvokeDynamicInsnNode id = (InvokeDynamicInsnNode) in;
                popArgs(f, org.objectweb.asm.Type.getArgumentTypes(id.desc));
                org.objectweb.asm.Type ret = org.objectweb.asm.Type.getReturnType(id.desc);
                if (ret.getSize() == 2) f.push2(this, Nullness.MAYNULL);
                else if (ret.getSort() != org.objectweb.asm.Type.VOID) f.push(this, Nullness.MAYNULL);
            }
            case Opcodes.NEW -> f.push(this, Nullness.NONNULL);
            case Opcodes.NEWARRAY, Opcodes.ANEWARRAY -> {
                f.pop();
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.MULTIANEWARRAY -> {
                f.pop(((MultiANewArrayInsnNode) in).dims);
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.ARRAYLENGTH -> {
                Object arr = f.pop();
                if (arr == Nullness.MAYNULL || arr == Nullness.NULL) {
                    findings.accept(new DerefFinding(className, methodName, insnIndex, op, "[array]"));
                }
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.ATHROW -> f.pop();
            case Opcodes.CHECKCAST -> { /* value unchanged */ }
            case Opcodes.INSTANCEOF -> {
                f.pop();
                f.push(this, Nullness.NONNULL);
            }
            case Opcodes.MONITORENTER -> {
                Object o = f.pop();
                if (o == Nullness.MAYNULL || o == Nullness.NULL) {
                    findings.accept(new DerefFinding(className, methodName, insnIndex, op, "[monitor]"));
                }
            }
            case Opcodes.MONITOREXIT -> f.pop();
            default -> throw new NopBytecodeException("Unhandled opcode " + op + " in "
                    + className + "." + methodName + " @insn " + insnIndex);
        }
    }

    @Override
    public boolean edgeFrame(Frame edge, int insnIndex, int target, boolean targetIsHandlerHead) {
        if (targetIsHandlerHead) {
            // exception clears the stack; catch parameter is never null per JLS
            while (edge.sp() > edge.base()) edge.pop();
            edge.push(this, Nullness.NONNULL);
            return true;
        }
        if (suppressPendingJumpEdge) {
            suppressPendingJumpEdge = false;
            // drop the jump edge of the $assertionsDisabled branch (assertions-modeled-enabled);
            // the fallthrough edge carries the actual nullness check
            if (insnIndex != pendingRefineInsn) return false;
            // degenerate case: the suppressed branch IS the pending refine insn's jump edge —
            // never happens for the idiom (ifne and ifnonnull are distinct instructions), but
            // fall through defensively
        }
        if (insnIndex == pendingRefineInsn && pendingRefineVar >= 0) {
            AbstractInsnNode in = mn.instructions.get(insnIndex);
            boolean isJumpEdge = (pendingEdgeOrdinal == 0);
            pendingEdgeOrdinal++;
            int op = in.getOpcode();
            // "eqNull" = condition true means the refined side IS null.
            // IFNULL: true -> null; IFNONNULL: true -> nonnull;
            // IF_ACMPEQ: true -> equal -> the non-null-constant side IS null;
            // IF_ACMPNE: true -> not equal -> the non-null-constant side is NONNULL.
            boolean eqNull = switch (op) {
                case Opcodes.IFNULL, Opcodes.IF_ACMPEQ -> true;
                default -> false; // IFNONNULL, IF_ACMPNE
            };
            Nullness jumpVal = eqNull ? Nullness.NULL : Nullness.NONNULL;
            Nullness fallVal = eqNull ? Nullness.NONNULL : Nullness.NULL;
            edge.setLocal(pendingRefineVar, isJumpEdge ? jumpVal : fallVal);
        }
        return true;
    }

    private int varOf(AbstractInsnNode in) {
        if (in instanceof org.objectweb.asm.tree.VarInsnNode v) return v.var;
        throw new NopBytecodeException("expected var instruction in " + className + "." + methodName);
    }

    private void popArgs(Frame f, org.objectweb.asm.Type[] args) {
        for (int a = args.length - 1; a >= 0; a--) f.pop(args[a].getSize());
    }

    private void pushRet(Frame f, org.objectweb.asm.Type ret, String owner, String name) {
        if (ret.getSort() == org.objectweb.asm.Type.VOID) return;
        if (REQ_NONNULL_OWNER.equals(owner) && REQ_NONNULL_NAME.equals(name)) {
            requireNonNullGates++;
            if (ret.getSize() == 2) f.push2(this, Nullness.NONNULL);
            else f.push(this, Nullness.NONNULL);
            return;
        }
        if (ret.getSize() == 2) f.push2(this, Nullness.MAYNULL);
        else f.push(this, Nullness.MAYNULL);
    }

    public long guardRefinements() {
        return guardRefinements;
    }

    public long requireNonNullGates() {
        return requireNonNullGates;
    }

    public long acmpRefinements() {
        return acmpRefinements;
    }

    @Override
    public Object merge(Object a, Object b) {
        return Nullness.merge((Nullness) a, (Nullness) b);
    }

    @Override
    public Object topValue() {
        return Nullness.TOP;
    }

}
