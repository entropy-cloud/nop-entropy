package io.nop.refactor.graphql;

import io.nop.core.initialize.CoreInitialization;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.refactor.core.NopRefactorException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI12 rename-face service-level boundary matrix (plan 12 adjudication
 * 5, the two-layer presentation): check/construction-layer violations throw
 * structured errors (isOk=false on the RPC face), while plan-level refusals
 * surface as zero-edit nonApplied payloads. The container-wired adapter
 * proves the beans wiring (a bypassed-IoC instance exercises the missing-
 * bean pre-check).
 */
class TestNopRefactorRenameBizModel {

    private static final String SERVICE_PATH = "a/Service.java";

    private static final String SERVICE_SOURCE = """
            package a;

            public class Service {
                public static int limit() {
                    return 1;
                }
            }
            """;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static NopRefactorBizModel wiredModel() {
        return BeanContainer.getBeanByType(NopRefactorBizModel.class);
    }

    private static Path write(String relPath, String content) throws Exception {
        Path file = Path.of("target", "rename-biz-matrix", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static RenameInput input(List<String> paths, String fqn, String path,
                                     Integer offset, String newName) {
        RenameInput input = new RenameInput();
        input.setPaths(paths);
        input.setFqn(fqn);
        input.setPath(path);
        input.setByteOffset(offset);
        input.setNewName(newName);
        return input;
    }

    // ==================== check layer: structured errors ====================

    @Test
    void bothLocatorFormsReject() throws Exception {
        Path file = write("a/Service.java", SERVICE_SOURCE);
        NopRefactorException thrown = assertThrows(NopRefactorException.class,
                () -> wiredModel().previewRename(input(
                        List.of(file.toString()), "a.Service", file.toString(), 40,
                        "renamed")));
        assertTrue(thrown.getMessage().contains("exactly one"), thrown.getMessage());
    }

    @Test
    void neitherLocatorFormRejects() throws Exception {
        Path file = write("a/Service.java", SERVICE_SOURCE);
        assertThrows(NopRefactorException.class,
                () -> wiredModel().previewRename(input(
                        List.of(file.toString()), null, null, null, "renamed")));
    }

    @Test
    void invalidNewNameRejects() throws Exception {
        Path file = write("a/Service.java", SERVICE_SOURCE);
        assertThrows(NopRefactorException.class,
                () -> wiredModel().previewRename(input(
                        List.of(file.toString()), "a.Service", null, null, "not ok")));
    }

    @Test
    void emptyPathsReject() {
        assertThrows(NopRefactorException.class,
                () -> wiredModel().previewRename(input(
                        List.of(), "a.Service", null, null, "renamed")));
    }

    @Test
    void offsetTargetOutsideTheModuleSetRejects() throws Exception {
        Path inModule = write("a/Service.java", SERVICE_SOURCE);
        Path outside = write("other/Outside.java", "class Outside {}\n");
        NopRefactorException thrown = assertThrows(NopRefactorException.class,
                () -> wiredModel().previewRename(input(
                        List.of(inModule.toString()), null, outside.toString(), 3,
                        "renamed")));
        assertTrue(thrown.getMessage().contains("not part of the module file set"),
                thrown.getMessage());
    }

    @Test
    void missingAdapterPrecheckNamesTheBean() {
        // a bypassed-IoC instance has no injected adapter: the face's
        // pre-check must fail with a structured error naming the bean —
        // never an NPE from the request record
        NopRefactorBizModel bare = new NopRefactorBizModel();
        NopRefactorException thrown = assertThrows(NopRefactorException.class,
                () -> bare.previewRename(input(
                        List.of("a/Service.java"), "a.Service", null, null,
                        "renamed")));
        assertTrue(thrown.getMessage().contains("resolver bean"),
                thrown.getMessage());
    }

    // ==================== plan layer: structured refusals ====================

    @Test
    void overloadedMethodConflictSurfacesAsNonApply() throws Exception {
        String source = """
                package a;

                public class Service {
                    public static int limit() {
                        return 1;
                    }

                    public static int limit(String name) {
                        return 2;
                    }
                }
                """;
        Path file = write("conflict/a/Service.java", source);
        long offset = source.substring(0, source.indexOf("int limit()") + 4)
                .getBytes(StandardCharsets.UTF_8).length;

        var result = wiredModel().applyRename(input(
                List.of(file.toString()), null, file.toString(), (int) offset,
                "cap"));

        assertEquals(1, result.nonApplied().size());
        assertEquals(io.nop.refactor.core.NonApply.Reason.CONFLICT,
                result.nonApplied().get(0).reason());
        assertEquals(0, result.stats().editsApplied());
        assertEquals(null, result.verification().symbolIntact(),
                "a refused rename carries no assertion");
        assertEquals(source, Files.readString(file), "the refusal writes nothing");
    }

    // ==================== the positive face + wiring proof ====================

    @Test
    void rawRelativePathRenamesThroughTheNormalizedGrammar() throws Exception {
        // plan 12 adjudication 2: a raw relative path normalizes through the
        // same grammar as the collection, so the offset locator compares
        Path file = write("rel/a/Service.java", SERVICE_SOURCE);
        long offset = SERVICE_SOURCE.substring(0, SERVICE_SOURCE.indexOf("int limit") + 4)
                .getBytes(StandardCharsets.UTF_8).length;

        var result = wiredModel().applyRename(input(
                List.of(file.toString()), null, file.toString(), (int) offset,
                "cap"));
        assertTrue(result.applied(), "the rename lands: " + result);
        assertTrue(Files.readString(file).contains("static int cap()"),
                "the member renamed");
    }

    @Test
    void duplicatePathEntriesDeduplicate() throws Exception {
        Path file = write("dedup/a/Service.java", SERVICE_SOURCE);
        long offset = SERVICE_SOURCE.substring(0, SERVICE_SOURCE.indexOf("int limit") + 4)
                .getBytes(StandardCharsets.UTF_8).length;
        String diskPath = file.toString();

        // the same file twice in the search domain must not double-index
        var result = wiredModel().applyRename(input(
                List.of(diskPath, diskPath), null, diskPath, (int) offset, "cap"));
        assertTrue(result.applied(), "the deduplicated rename lands: " + result);
        assertEquals(1, result.stats().editsApplied(),
                "exactly one declaration span (no double index)");
    }

    @Test
    void identicalInputsProduceIdenticalPayloads() throws Exception {
        // stateless re-execution: the same input (same paths, same bytes —
        // restored between runs) must produce the same payload
        Path file = write("det/a/Service.java", SERVICE_SOURCE);
        long offset = SERVICE_SOURCE.substring(0, SERVICE_SOURCE.indexOf("int limit") + 4)
                .getBytes(StandardCharsets.UTF_8).length;

        var first = wiredModel().previewRename(input(
                List.of(file.toString()), null, file.toString(), (int) offset,
                "cap"));
        Files.write(file, SERVICE_SOURCE.getBytes(StandardCharsets.UTF_8));
        var second = wiredModel().previewRename(input(
                List.of(file.toString()), null, file.toString(), (int) offset,
                "cap"));

        assertEquals(String.valueOf(first), String.valueOf(second),
                "stateless re-execution is deterministic");
    }
}
