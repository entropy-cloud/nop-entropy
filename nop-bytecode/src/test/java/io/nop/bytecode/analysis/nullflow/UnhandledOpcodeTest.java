package io.nop.bytecode.analysis.nullflow;

import io.nop.bytecode.NopBytecodeException;
import io.nop.bytecode.kernel.cfg.MethodCfg;
import io.nop.bytecode.kernel.cfg.MethodCfgBuilder;
import io.nop.bytecode.kernel.dataflow.ForwardSolver;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unhandled opcodes must fail loudly (no silent skip). Legal class files never contain
 * IMPDEP1/2 (and WIDE never appears as a standalone opcode in ASM's tree), so the trigger is a
 * hand-crafted MethodNode.
 */
class UnhandledOpcodeTest {

    @Test
    void unhandledOpcodeThrowsWithContext() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "broken", "(I)I", null, null);
        MethodVisitor mv = mn;
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        // IMPDEP1 (0xFE = 254) cannot appear in legal class files — exactly why it exercises
        // the default branch. ASM 9.x removed the IMPDEP constants from Opcodes.
        mv.visitInsn(254);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();

        NopBytecodeException ex = assertThrows(NopBytecodeException.class, () ->
                new NullflowAnalyzer().analyzeMethod("corpus.Broken", mn));
        assertTrue(ex.getMessage().contains("Unhandled opcode"), ex.getMessage());
        assertTrue(ex.getMessage().contains("corpus.Broken.broken"), ex.getMessage());
    }
}
