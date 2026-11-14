package io.nop.ib.index;

import io.nop.ib.NopIbException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Builds a module's {@link UnitIndex} by scanning its compiled output directories
 * with ASM (design 05 §6.2; substrate: nop-bytecode's ASM adjudication, but the
 * index needs richer facts than the collect layer's artifact manifest, so we run
 * a focused ClassVisitor over the same substrate library).
 *
 * <p>Read failures are loud: a module whose products cannot be parsed is
 * {@code indexUnhealthy} and must fall back, never degrade to a partial index.
 */
public final class UnitIndexer {
    /** class access flag test: private members are excluded from the ABI surface */
    private static final int ACC_PRIVATE = 0x0002;

    /** method annotations that make a method executable by the JUnit platform */
    private static final Set<String> TEST_METHOD_ANNOTATIONS = Set.of(
            "Lorg/junit/jupiter/api/Test;",
            "Lorg/junit/jupiter/params/ParameterizedTest;",
            "Lorg/junit/jupiter/api/TestFactory;",
            "Lorg/junit/jupiter/api/TestTemplate;",
            "Lorg/junit/jupiter/api/RepeatedTest;",
            "Lorg/junit/Test;");
    private static final String NESTED_ANNOTATION = "Lorg/junit/jupiter/api/Nested;";

    public UnitIndex scan(String moduleArtifactId, List<Path> outputDirs, Path moduleBasedir) {
        UnitIndex index = UnitIndex.empty(moduleArtifactId);
        List<Path> files = new ArrayList<>();
        for (Path dir : outputDirs) {
            if (!Files.isDirectory(dir))
                continue;
            collectClassFiles(dir, files);
        }
        for (Path file : files) {
            try {
                byte[] bytes = Files.readAllBytes(file);
                ClassInfo info = parse(bytes, moduleBasedir, file);
                index.put(info);
            } catch (IOException | RuntimeException e) {
                throw new NopIbException("indexUnhealthy: cannot parse class file " + file, e);
            }
        }
        index.setIndexedAt(System.currentTimeMillis());
        return index;
    }

    private void collectClassFiles(Path dir, List<Path> files) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(files::add);
        } catch (IOException e) {
            throw new NopIbException("cannot walk output dir " + dir, e);
        }
        files.sort(Comparator.comparing(Path::toString));
    }

    ClassInfo parse(byte[] bytes, Path moduleBasedir, Path file) {
        ClassReader reader = new ClassReader(bytes);
        RefCollector visitor = new RefCollector();
        reader.accept(visitor, ClassReader.SKIP_FRAMES);
        ClassInfo info = visitor.info();
        info.setContentHash(io.nop.ib.model.ModelStore.sha256(bytes));
        info.setAbiDigest(visitor.abiDigest());
        // sourceFile: javac emits SourceFile = simple name + ".java" (no path). Map it to
        // the module-relative path only when the class binary name package prefix can
        // locate it; otherwise keep the bare name for source->classes matching.
        if (visitor.sourceFile != null) {
            String pkg = visitor.className.contains(".")
                    ? visitor.className.substring(0, visitor.className.lastIndexOf('.') + 1)
                    : "";
            String guessed;
            if (moduleBasedir != null) {
                String rel = moduleBasedir.relativize(file).toString();
                String prefix = rel.contains("test-classes") ? "src/test/java/" : "src/main/java/";
                guessed = prefix + pkg.replace('.', '/') + visitor.sourceFile;
            } else {
                guessed = pkg.replace('.', '/') + visitor.sourceFile;
            }
            info.setSourceFile(guessed);
        }
        return info;
    }

    static class RefCollector extends ClassVisitor {
        private final ClassInfo info = new ClassInfo();
        private final Set<String> refs = new LinkedHashSet<>();
        private final TreeSet<String> abiLines = new TreeSet<>();
        private String sourceFile;
        private String className;

        RefCollector() {
            super(Opcodes.ASM9);
        }

        ClassInfo info() {
            info.setRefs(new ArrayList<>(refs));
            return info;
        }

        String abiDigest() {
            StringBuilder sb = new StringBuilder();
            for (String line : abiLines)
                sb.append(line).append('\n');
            return io.nop.ib.model.ModelStore.sha256(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
            info.setClassName(this.className);
            info.setAccess(access);
            info.setSuperName(superName == null ? null : superName.replace('/', '.'));
            abiLines.add("C:" + Integer.toHexString(access) + ":" + name + ":" + superName
                    + ":" + String.join(",", interfaces != null ? interfaces : new String[0]));
            if (superName != null)
                refs.add(superName.replace('/', '.'));
            if (interfaces != null) {
                for (String i : interfaces)
                    refs.add(i.replace('/', '.'));
            }
        }

        @Override
        public void visitSource(String source, String debug) {
            this.sourceFile = source;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            abiLines.add("A:" + descriptor);
            refs.add(Type.getType(descriptor).getClassName());
            if (NESTED_ANNOTATION.equals(descriptor))
                info.setNested(true);
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            refs.add(Type.getType(descriptor).getClassName());
            if ((access & ACC_PRIVATE) == 0)
                abiLines.add("F:" + Integer.toHexString(access) + ":" + name + ":" + descriptor);
            if (value != null) {
                // static final primitive/String constant that javac inlines at call sites
                info.getInlineConstants().put(name, String.valueOf(value));
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if ((access & ACC_PRIVATE) == 0) {
                abiLines.add("M:" + Integer.toHexString(access) + ":" + name + ":" + descriptor
                        + ":" + (exceptions != null ? String.join(",", exceptions) : ""));
            }
            if (exceptions != null) {
                for (String e : exceptions)
                    refs.add(e.replace('/', '.'));
            }
            for (Type arg : Type.getArgumentTypes(descriptor))
                refs.add(arg.getClassName());
            refs.add(Type.getReturnType(descriptor).getClassName());
            return new RefMethodVisitor(refs, abiLines, (access & ACC_PRIVATE) == 0, name,
                    info::setTestMethods);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            refs.add(name.replace('/', '.'));
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            refs.add(owner.replace('/', '.'));
        }

        @Override
        public void visitNestHost(String nestHost) {
            refs.add(nestHost.replace('/', '.'));
        }

        @Override
        public void visitNestMember(String nestMember) {
            refs.add(nestMember.replace('/', '.'));
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            refs.add(permittedSubclass.replace('/', '.'));
        }
    }

    static class RefMethodVisitor extends MethodVisitor {
        private final Set<String> refs;
        private final TreeSet<String> abiLines;
        private final boolean inAbi;
        private final String methodName;
        private final java.util.function.Consumer<Boolean> testMethodFlag;

        RefMethodVisitor(Set<String> refs, TreeSet<String> abiLines, boolean inAbi, String methodName,
                         java.util.function.Consumer<Boolean> testMethodFlag) {
            super(Opcodes.ASM9);
            this.refs = refs;
            this.abiLines = abiLines;
            this.inAbi = inAbi;
            this.methodName = methodName;
            this.testMethodFlag = testMethodFlag;
        }

        private void ref(String internal) {
            if (internal != null)
                refs.add(internal.replace('/', '.'));
        }

        private void ref(Type type) {
            if (type == null)
                return;
            if (type.getSort() == Type.OBJECT)
                ref(type.getClassName());
            else if (type.getSort() == Type.ARRAY)
                ref(type.getElementType().getClassName());
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (inAbi)
                abiLines.add("m@" + methodName + ":" + descriptor);
            if (TEST_METHOD_ANNOTATIONS.contains(descriptor))
                testMethodFlag.accept(true);
            ref(Type.getType(descriptor).getClassName());
            return null;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitParameterAnnotation(int parameter,
                                                                            String descriptor, boolean visible) {
            ref(Type.getType(descriptor).getClassName());
            return null;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            ref(type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            ref(owner);
            ref(descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                    boolean isInterface) {
            ref(owner);
            if (isInterface || !owner.startsWith("[")) {
                for (Type arg : Type.getArgumentTypes(descriptor))
                    ref(arg);
                ref(Type.getReturnType(descriptor));
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                           Handle bootstrapMethod, Object... arguments) {
            // descriptor here is a METHOD descriptor: Type.getType() asserts on it
            for (Type arg : Type.getArgumentTypes(descriptor))
                ref(arg);
            ref(Type.getReturnType(descriptor));
            ref(bootstrapMethod.getOwner());
            if (arguments != null) {
                for (Object arg : arguments) {
                    if (arg instanceof Type t)
                        ref(t);
                    else if (arg instanceof Handle h)
                        ref(h.getOwner());
                }
            }
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            ref(type);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            ref(descriptor);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type t)
                ref(t);
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            if (signature != null)
                collectSignature(signature);
        }

        private void collectSignature(String signature) {
            // generic signatures name classes with '/' separators; extract lazily:
            // every identifier between non-name characters that contains a '/'
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("L[a-zA-Z0-9_/$<>;]*;").matcher(signature);
            while (matcher.find()) {
                String raw = matcher.group();
                String internal = raw.substring(1, raw.length() - 1);
                int lt = internal.indexOf('<');
                if (lt >= 0)
                    internal = internal.substring(0, lt);
                ref(internal);
            }
        }
    }
}
