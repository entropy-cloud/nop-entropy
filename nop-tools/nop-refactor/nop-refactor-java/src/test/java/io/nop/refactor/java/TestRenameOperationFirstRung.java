package io.nop.refactor.java;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.RefactorResult;
import io.nop.refactor.core.operation.RenameOperation;
import io.nop.refactor.core.operation.RefactorOperationRunner;
import io.nop.refactor.core.operation.RenameRequest;
import io.nop.refactor.core.operation.RenameScope;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SourceFile;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI10 first-rung end-to-end proof (plan 10 Phase 2, Minimum Rules
 * #22/#23): a rename request rides the shared framework runner — check,
 * plan, the single WI4 apply, the single WI5 assemble — over real files
 * with the real Java adapter. The landed file renames the declaration and
 * every bound reference byte-for-byte while everything else stays
 * untouched, the payload carries the pre-computed {@code symbolIntact}
 * assertion, and the three refusals surface as zero-edit structured
 * {@code NonApply} entries. This is the same {@code runner.run} entry the
 * codemod face tests drive — one plan/apply/verify mechanism, no second
 * path.
 */
class TestRenameOperationFirstRung {

    private static final JavaSymbolResolverAdapter ADAPTER = new JavaSymbolResolverAdapter();
    private static final String SERVICE_PATH = "a/Service.java";
    private static final String SERVICE_SOURCE = """
            package a;

            public class Service {
                private int limit;

                void run(int limit) {
                    int doubled = limit * 2;
                    System.out.println(doubled);
                }
            }
            """;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static List<SourceFile> module() {
        return List.of(new SourceFile(SERVICE_PATH, SERVICE_SOURCE));
    }

    private static RenameRequest request(SymbolTarget target, String newName) {
        return new RenameRequest(target, newName, RenameScope.MODULE, module(), ADAPTER,
                javaLanguage(), javaEngine());
    }

    private static LintLanguage javaLanguage() {
        return LanguageRegistry.discoverDefaults().resolve("java");
    }

    private static LintEngine javaEngine() {
        return new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
    }

    /** The parameter declaration's byte offset (the identifier, not the type). */
    private static long paramOffset() {
        return SERVICE_SOURCE.substring(0, SERVICE_SOURCE.indexOf("run(int limit") + 8)
                .getBytes(StandardCharsets.UTF_8).length;
    }

    private static long localOffset() {
        return SERVICE_SOURCE.substring(0, SERVICE_SOURCE.indexOf("int doubled") + 4)
                .getBytes(StandardCharsets.UTF_8).length;
    }

    private static Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "rename-first-rung", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void applyRenamesDeclarationAndReferencesThroughTheSharedRunner() throws Exception {
        // the on-disk copy is byte-identical to the module source, so the
        // byte offsets hold
        Path file = writeTarget(SERVICE_PATH, SERVICE_SOURCE);
        String diskPath = file.toString();
        RenameRequest request = new RenameRequest(
                SymbolTarget.ofOffset(diskPath, paramOffset()), "maxCount",
                RenameScope.MODULE,
                List.of(new SourceFile(diskPath, SERVICE_SOURCE)), ADAPTER,
                javaLanguage(), javaEngine());

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, false);

        assertTrue(result.applied(), "apply reports applied=true: " + result);
        assertEquals("package a;\n"
                + "\n"
                + "public class Service {\n"
                + "    private int limit;\n"
                + "\n"
                + "    void run(int maxCount) {\n"
                + "        int doubled = maxCount * 2;\n"
                + "        System.out.println(doubled);\n"
                + "    }\n"
                + "}\n", Files.readString(file),
                "the declaration and the bound reference renamed; the same-named "
                        + "field access and the local stayed untouched");
        assertEquals(2, result.stats().editsApplied());
        assertEquals("rename limit to maxCount", result.edits().get(0).summary());
        assertEquals(Boolean.TRUE, result.verification().symbolIntact(),
                "the reference count holds across the rename");
        assertTrue(result.verification().parseOk());
        assertEquals(0, result.nonApplied().size());
    }

    @Test
    void previewComputesTheAssertionAndWritesNothing() throws Exception {
        Path file = writeTarget("preview/" + SERVICE_PATH, SERVICE_SOURCE);
        String diskPath = file.toString();
        RenameRequest request = new RenameRequest(
                SymbolTarget.ofOffset(diskPath, localOffset()), "twiceValue",
                RenameScope.MODULE,
                List.of(new SourceFile(diskPath, SERVICE_SOURCE)), ADAPTER,
                javaLanguage(), javaEngine());

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, true);

        assertEquals(false, result.applied(), "preview is a dry run");
        assertEquals(Boolean.TRUE, result.verification().symbolIntact(),
                "the assertion is pre-computed at plan time (deterministic preview)");
        assertEquals(SERVICE_SOURCE, Files.readString(file),
                "preview wrote nothing");
        assertEquals(2, result.stats().editsApplied(),
                "declaration + the println bound reference");
    }

    @Test
    void conflictSurfacesAsZeroEditNonApply() throws Exception {
        Path file = writeTarget("conflict/" + SERVICE_PATH, SERVICE_SOURCE);
        // renaming the parameter to `doubled` collides with the method's
        // local declared in the same body
        String diskPath = file.toString();
        RenameRequest request = new RenameRequest(
                SymbolTarget.ofOffset(diskPath, paramOffset()), "doubled",
                RenameScope.MODULE,
                List.of(new SourceFile(diskPath, SERVICE_SOURCE)), ADAPTER,
                javaLanguage(), javaEngine());

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, false);

        // the applied flag is the MODE (apply re-lands the same plan); the
        // refusal itself is the zero-edit plan plus its structured entry
        assertTrue(result.applied(), "apply mode reports applied=true");
        assertEquals(1, result.nonApplied().size());
        assertEquals(NonApply.Reason.CONFLICT, result.nonApplied().get(0).reason());
        assertEquals(0, result.stats().editsApplied(), "a refusal edits nothing");
        assertNull(result.verification().symbolIntact(),
                "a refused rename has no assertion to report");
        assertEquals(SERVICE_SOURCE, Files.readString(file),
                "the conflict left the file untouched");
    }

    @Test
    void constructorTargetIsOutOfScopeForTheTypeFace() throws Exception {
        // the constructor-source module: an explicit constructor kinned as
        // CONSTRUCTOR refuses — a constructor rename IS the class rename
        String source = SERVICE_SOURCE.replace(
                "    void run(int limit) {",
                "    Service() {\n    }\n\n    void run(int limit) {");
        Path file = writeTarget("scope/" + SERVICE_PATH, source);
        String diskPath = file.toString();
        long ctorOffset = source.substring(0, source.indexOf("Service() {"))
                .getBytes(StandardCharsets.UTF_8).length;

        RenameRequest request = new RenameRequest(
                SymbolTarget.ofOffset(diskPath, ctorOffset), "Created",
                RenameScope.MODULE,
                List.of(new SourceFile(diskPath, source)), ADAPTER,
                javaLanguage(), javaEngine());

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, false);

        assertEquals(1, result.nonApplied().size());
        assertEquals(NonApply.Reason.OUT_OF_SCOPE, result.nonApplied().get(0).reason());
        assertTrue(result.nonApplied().get(0).detail().contains("class rename"),
                "the refusal names the TYPE face: "
                        + result.nonApplied().get(0).detail());
        assertEquals(0, result.stats().editsApplied());
    }

    @Test
    void methodRenameLandsThroughTheSecondRung() throws Exception {
        // a method with no receiver-shaped same-name token anywhere renames
        // through the member face (plan 11 adjudication 4)
        Path file = writeTarget("method/" + SERVICE_PATH, SERVICE_SOURCE);
        String diskPath = file.toString();
        long methodOffset = SERVICE_SOURCE.substring(0, SERVICE_SOURCE.indexOf("void run") + 5)
                .getBytes(StandardCharsets.UTF_8).length;

        RenameRequest request = new RenameRequest(
                SymbolTarget.ofOffset(diskPath, methodOffset), "execute",
                RenameScope.MODULE,
                List.of(new SourceFile(diskPath, SERVICE_SOURCE)), ADAPTER,
                javaLanguage(), javaEngine());

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, false);

        assertTrue(result.applied(), "the method rename lands: " + result);
        assertEquals(1, result.stats().editsApplied());
        assertEquals(Boolean.TRUE, result.verification().symbolIntact());
        assertTrue(Files.readString(file).contains("void execute(int limit)"),
                "the method declaration renamed");
    }

    @Test
    void unresolvedLocatorSurfacesAsNonApply() throws Exception {
        Path file = writeTarget("unresolved/" + SERVICE_PATH, SERVICE_SOURCE);
        String diskPath = file.toString();
        RenameRequest request = new RenameRequest(
                SymbolTarget.ofOffset(diskPath, 1_000_000L), "anything",
                RenameScope.MODULE,
                List.of(new SourceFile(diskPath, SERVICE_SOURCE)), ADAPTER,
                javaLanguage(), javaEngine());

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, false);

        assertEquals(1, result.nonApplied().size());
        assertEquals(NonApply.Reason.UNRESOLVED_TARGET, result.nonApplied().get(0).reason());
        assertEquals(0, result.stats().editsApplied());
    }
}
