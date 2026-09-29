package io.nop.bytecode.bench;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic synthetic corpus for kernel benchmarks and oracle tests (test infrastructure
 * consumed by plan 03 Phase 2/3).
 *
 * <p>Generated with ASM {@link ClassWriter#COMPUTE_MAXS} — never COMPUTE_FRAMES (its
 * getCommonSuperClass would try to load these in-memory classes; the kernel chain does not
 * consume StackMapTable). Shape correctness of the generated methods is guaranteed by the
 * oracle shape-audit test, which covers this corpus.
 *
 * <p>Fixed parameters (do not tune without updating docs/perf-baseline.md):
 * {@value #CLASS_COUNT} classes x {@value #METHODS_PER_CLASS} methods, mixed shapes
 * (branches / loops / try-catch / field access / virtual + static + interface calls /
 * table + lookup switch / invokedynamic string concat).
 */
public final class CorpusGenerator {
    public static final int CLASS_COUNT = 20;
    public static final int METHODS_PER_CLASS = 15;

    private CorpusGenerator() { }

    public static List<byte[]> generate() {
        List<byte[]> out = new ArrayList<>(CLASS_COUNT);
        for (int c = 0; c < CLASS_COUNT; c++) {
            out.add(generateClass(c));
        }
        return out;
    }

    private static byte[] generateClass(int c) {
        String cn = "corpus/Synth" + c;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, cn, null, "java/lang/Object",
                new String[]{"corpus/SynthApi"});

        // one field for load/store shapes
        cw.visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;", null, null).visitEnd();

        for (int m = 0; m < METHODS_PER_CLASS; m++) {
            generateMethod(cw, cn, m);
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void generateMethod(ClassWriter cw, String cn, int m) {
        int shape = m % 6;
        String name = "m" + m;
        MethodVisitor mv;
        switch (shape) {
            case 0 -> { // guarded/unguarded branches + loop
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "(Ljava/lang/String;I)Ljava/lang/String;", null, null);
                mv.visitCode();
                Label nullBranch = new Label();
                Label join = new Label();
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitJumpInsn(Opcodes.IFNULL, nullBranch);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitLabel(nullBranch);
                mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                mv.visitLdcInsn("was-null-");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitVarInsn(Opcodes.ILOAD, 2);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(I)Ljava/lang/StringBuilder;", false);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                        "()Ljava/lang/String;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitLabel(join);
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            case 1 -> { // loop with long arithmetic + field access
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "(J)J", null, null);
                mv.visitCode();
                mv.visitInsn(Opcodes.LCONST_0);
                mv.visitVarInsn(Opcodes.LSTORE, 3);
                mv.visitInsn(Opcodes.LCONST_0);
                mv.visitVarInsn(Opcodes.LSTORE, 5);
                Label loop = new Label();
                Label loopEnd = new Label();
                mv.visitLabel(loop);
                mv.visitVarInsn(Opcodes.LLOAD, 5);
                mv.visitVarInsn(Opcodes.LLOAD, 1);
                mv.visitInsn(Opcodes.LCMP);
                mv.visitJumpInsn(Opcodes.IFGE, loopEnd);
                mv.visitVarInsn(Opcodes.LLOAD, 3);
                mv.visitVarInsn(Opcodes.LLOAD, 5);
                mv.visitInsn(Opcodes.LADD);
                mv.visitVarInsn(Opcodes.LSTORE, 3);
                mv.visitVarInsn(Opcodes.LLOAD, 5);
                mv.visitInsn(Opcodes.LCONST_1);
                mv.visitInsn(Opcodes.LADD);
                mv.visitVarInsn(Opcodes.LSTORE, 5);
                mv.visitJumpInsn(Opcodes.GOTO, loop);
                mv.visitLabel(loopEnd);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.LLOAD, 3);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(J)Ljava/lang/String;", false);
                mv.visitFieldInsn(Opcodes.PUTFIELD, cn, "value", "Ljava/lang/String;");
                mv.visitVarInsn(Opcodes.LLOAD, 3);
                mv.visitInsn(Opcodes.LRETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            case 2 -> { // try/catch with guarded deref inside try
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "(Ljava/lang/String;)I", null,
                        new String[]{"java/lang/Exception"});
                mv.visitCode();
                Label tryStart = new Label();
                Label tryEnd = new Label();
                Label handler = new Label();
                mv.visitTryCatchBlock(tryStart, tryEnd, handler, "java/lang/Exception");
                mv.visitLabel(tryStart);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitJumpInsn(Opcodes.IFNULL, tryEnd);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitLabel(tryEnd);
                mv.visitInsn(Opcodes.ICONST_M1);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitLabel(handler);
                mv.visitVarInsn(Opcodes.ASTORE, 2);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            case 3 -> { // table + lookup switch
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "(II)I", null, null);
                mv.visitCode();
                Label dflt = new Label();
                Label l1 = new Label();
                Label l2 = new Label();
                Label l3 = new Label();
                mv.visitVarInsn(Opcodes.ILOAD, 1);
                mv.visitTableSwitchInsn(0, 1, dflt, l1, l2);
                mv.visitLabel(l1);
                mv.visitVarInsn(Opcodes.ILOAD, 2);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitLabel(l2);
                mv.visitVarInsn(Opcodes.ILOAD, 2);
                mv.visitInsn(Opcodes.INEG);
                mv.visitInsn(Opcodes.IRETURN);
                Label ldflt = new Label();
                Label lk1 = new Label();
                Label lk2 = new Label();
                mv.visitLabel(dflt);
                mv.visitVarInsn(Opcodes.ILOAD, 1);
                mv.visitLookupSwitchInsn(ldflt, new int[]{10, 20}, new Label[]{lk1, lk2});
                mv.visitLabel(ldflt);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitLabel(lk1);
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitLabel(lk2);
                mv.visitInsn(Opcodes.ICONST_2);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitLabel(l3);
                mv.visitInsn(Opcodes.ICONST_M1);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            case 4 -> { // interface + static + virtual calls, null param flows into deref
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "(Ljava/util/List;)Ljava/lang/String;", null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get",
                        "(I)Ljava/lang/Object;", true);
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
                mv.visitVarInsn(Opcodes.ASTORE, 2);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim",
                        "()Ljava/lang/String;", false);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
                        "(Ljava/lang/Object;)Ljava/lang/String;", false);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Objects", "requireNonNull",
                        "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            default -> { // invokedynamic string concat (bootstrap declared below)
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "(Ljava/lang/String;)Ljava/lang/String;", null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, cn, "bootstrap",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/String;I)Ljava/lang/invoke/CallSite;", false);
                mv.visitInvokeDynamicInsn("makeConcatWithConstants",
                        "(Ljava/lang/String;)Ljava/lang/String;", bsm, "\u0001 !");
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }
    }

    /** Interface implemented by all synthetic classes (for interface-call shapes). */
    public static byte[] generateApi() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                "corpus/SynthApi", null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "m0",
                "(Ljava/lang/String;I)Ljava/lang/String;", null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Writes the corpus into a directory tree rooted at {@code root} (package corpus). */
    public static void writeToDirectory(java.nio.file.Path root) throws java.io.IOException {
        java.nio.file.Files.createDirectories(root.resolve("corpus"));
        java.nio.file.Files.write(root.resolve("corpus/SynthApi.class"), generateApi());
        List<byte[]> classes = generate();
        for (int i = 0; i < classes.size(); i++) {
            java.nio.file.Files.write(root.resolve("corpus/Synth" + i + ".class"), classes.get(i));
        }
    }
}
