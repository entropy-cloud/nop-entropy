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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI11 TYPE-face end-to-end proof (plan 11 Phase 1, Minimum Rules
 * #22/#23): renaming a type through the FQN locator rides the shared
 * framework runner across MULTIPLE files — the declaration, the same-package
 * unqualified uses, the exact import and its rewrite, the wildcard-import
 * file, and the unbound file's fully-qualified mention (which needs no
 * import, so skipping it would leave exactly the stale residue the roadmap
 * forbids). Everything outside the rewrite set stays byte-identical.
 */
class TestRenameTypeSecondRung {

    private static final JavaSymbolResolverAdapter ADAPTER = new JavaSymbolResolverAdapter();

    private static final String SERVICE_PATH = "a/Service.java";
    private static final String USER_PATH = "a/User.java";
    private static final String IMPORTER_PATH = "b/Importer.java";
    private static final String WILDCARD_PATH = "c/Wildcard.java";
    private static final String MENTION_PATH = "z/Mention.java";

    private static List<SourceFile> module() {
        return List.of(
                new SourceFile(SERVICE_PATH, """
                        package a;

                        public class Service {
                            public int make() {
                                Service inner = new Service();
                                return 1;
                            }
                        }
                        """),
                new SourceFile(USER_PATH, """
                        package a;

                        public class User {
                            Service make() {
                                return new Service();
                            }
                        }
                        """),
                new SourceFile(IMPORTER_PATH, """
                        package b;

                        import a.Service;

                        public class Importer {
                            Service field;
                            java.util.List<Service> list;
                        }
                        """),
                new SourceFile(WILDCARD_PATH, """
                        package c;

                        import a.*;

                        public class Wildcard {
                            Service w;
                        }
                        """),
                new SourceFile(MENTION_PATH, """
                        package z;

                        public class Mention {
                            a.Service qualified;
                        }
                        """));
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private record Written(Path service, Path user, Path importer, Path wildcard,
                           Path mention) {
    }

    private static Written writeModule(String subdir) throws Exception {
        Path service = write(subdir + "/" + SERVICE_PATH, module().get(0).content());
        Path user = write(subdir + "/" + USER_PATH, module().get(1).content());
        Path importer = write(subdir + "/" + IMPORTER_PATH, module().get(2).content());
        Path wildcard = write(subdir + "/" + WILDCARD_PATH, module().get(3).content());
        Path mention = write(subdir + "/" + MENTION_PATH, module().get(4).content());
        return new Written(service, user, importer, wildcard, mention);
    }

    private static Path write(String relPath, String content) throws Exception {
        Path file = Path.of("target", "rename-type-rung", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static RenameRequest request(Written written) {
        return new RenameRequest(SymbolTarget.ofFqn("a.Service"), "Engine",
                RenameScope.MODULE,
                List.of(new SourceFile(written.service().toString(), module().get(0).content()),
                        new SourceFile(written.user().toString(), module().get(1).content()),
                        new SourceFile(written.importer().toString(), module().get(2).content()),
                        new SourceFile(written.wildcard().toString(), module().get(3).content()),
                        new SourceFile(written.mention().toString(), module().get(4).content())),
                ADAPTER,
                LanguageRegistry.discoverDefaults().resolve("java"),
                new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD));
    }

    @Test
    void typeRenameLandsAcrossFilesWithImportAndMentionSync() throws Exception {
        Written written = writeModule("apply");

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request(written), false);

        assertTrue(result.applied(), "the cross-file rename lands: " + result);
        assertEquals(Boolean.TRUE, result.verification().symbolIntact(),
                "the rewrite count is symmetric across all files");

        // the declaring file: declaration + constructor-less simple use
        assertEquals("""
                package a;

                public class Engine {
                    public int make() {
                        Engine inner = new Engine();
                        return 1;
                    }
                }
                """, Files.readString(written.service()));

        // the same-package file: unqualified uses rename
        assertEquals("""
                package a;

                public class User {
                    Engine make() {
                        return new Engine();
                    }
                }
                """, Files.readString(written.user()));

        // the exact-import file: import rewritten + simple uses renamed
        assertEquals("""
                package b;

                import a.Engine;

                public class Importer {
                    Engine field;
                    java.util.List<Engine> list;
                }
                """, Files.readString(written.importer()));

        // the wildcard-import file: simple use renames, no import to rewrite
        assertEquals("""
                package c;

                import a.*;

                public class Wildcard {
                    Engine w;
                }
                """, Files.readString(written.wildcard()));

        // the unbound file: the fully-qualified mention syncs (no import)
        assertEquals("""
                package z;

                public class Mention {
                    a.Engine qualified;
                }
                """, Files.readString(written.mention()));
    }

    @Test
    void typePreviewIsDeterministicAndWritesNothingOnDryRun() throws Exception {
        Written written = writeModule("preview");

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request(written), true);

        assertEquals(false, result.applied(), "preview is a dry run");
        assertEquals(Boolean.TRUE, result.verification().symbolIntact());
        assertEquals(module().get(0).content(), Files.readString(written.service()),
                "preview wrote nothing");
    }

    @Test
    void staticImportFieldRenameCrossesFiles() throws Exception {
        // plan 11 adjudication 5: an exact static import binds the member —
        // the importer file's unqualified use renames with the declaration
        Path service = write("static/a/Service.java", """
                package a;

                public class Service {
                    public static final int LIMIT = 3;
                }
                """);
        Path importer = write("static/b/Importer.java", """
                package b;

                import static a.Service.LIMIT;

                public class Importer {
                    int cap() {
                        return LIMIT;
                    }
                }
                """);
        RenameRequest request = new RenameRequest(
                SymbolTarget.ofFqn("a.Service.LIMIT"), "CEILING", RenameScope.MODULE,
                List.of(new SourceFile(service.toString(), Files.readString(service)),
                        new SourceFile(importer.toString(), Files.readString(importer))),
                ADAPTER,
                LanguageRegistry.discoverDefaults().resolve("java"),
                new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD));

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, false);

        assertTrue(result.applied(), "the static-import rename lands: " + result);
        assertEquals("""
                package b;

                import static a.Service.CEILING;

                public class Importer {
                    int cap() {
                        return CEILING;
                    }
                }
                """, Files.readString(importer),
                "the static import AND the unqualified use renamed");
        assertEquals(Boolean.TRUE, result.verification().symbolIntact());
    }

    @Test
    void shadowedBoundFileRefusesTheWholeRename() throws Exception {
        // a bound file declaring a same-named variable makes its type uses
        // structurally ambiguous — the whole rename refuses (no partial landing)
        Path service = write("shadow/" + SERVICE_PATH, module().get(0).content());
        Path user = write("shadow/" + USER_PATH, """
                package a;

                public class User {
                    void run() {
                        int Service = 1;
                    }
                    Service make() {
                        return new Service();
                    }
                }
                """);
        RenameRequest request = new RenameRequest(SymbolTarget.ofFqn("a.Service"),
                "Engine", RenameScope.MODULE,
                List.of(new SourceFile(service.toString(), module().get(0).content()),
                        new SourceFile(user.toString(), Files.readString(user))),
                ADAPTER,
                LanguageRegistry.discoverDefaults().resolve("java"),
                new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD));

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                RenameOperation.INSTANCE, request, false);

        assertEquals(1, result.nonApplied().size());
        assertEquals(NonApply.Reason.CONFLICT, result.nonApplied().get(0).reason());
        assertTrue(result.nonApplied().get(0).detail().contains("User"),
                "the refusal names the ambiguous file: "
                        + result.nonApplied().get(0).detail());
        assertEquals(module().get(0).content(), Files.readString(service),
                "a refused rename writes nothing");
    }
}
