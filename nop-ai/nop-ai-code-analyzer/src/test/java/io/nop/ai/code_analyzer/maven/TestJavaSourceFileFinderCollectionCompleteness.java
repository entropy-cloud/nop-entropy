package io.nop.ai.code_analyzer.maven;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * C1-001 (plan 2520): getAllJavaSourceFiles must collect every source file from
 * every module. The pre-fix implementation fed a plain ArrayList from inside a
 * parallelStream forEach, so multi-module workspaces silently lost files
 * (or threw ArrayIndexOutOfBoundsException) under load.
 */
class TestJavaSourceFileFinderCollectionCompleteness {

    private static final int MODULE_COUNT = 12;
    private static final int FILES_PER_MODULE = 40;

    @Test
    void collectsEveryFileFromEveryModuleExactlyOnce() throws Exception {
        Path root = Files.createTempDirectory("cra-r2-collect");
        Set<String> expectedPaths = new HashSet<>();
        List<File> moduleDirs = new ArrayList<>();
        for (int m = 0; m < MODULE_COUNT; m++) {
            Path src = root.resolve("mod" + m + "/src/main/java/com/example");
            Files.createDirectories(src);
            for (int f = 0; f < FILES_PER_MODULE; f++) {
                Path file = src.resolve("Cls" + m + "_" + f + ".java");
                Files.write(file, new byte[]{'c', 'l', 'a', 's', 's'});
                expectedPaths.add(file.toAbsolutePath().toString());
            }
            moduleDirs.add(root.resolve("mod" + m).toFile());
        }

        JavaSourceFileFinder finder = new JavaSourceFileFinder(structureOf(moduleDirs));
        List<File> collected = finder.getAllJavaSourceFiles();

        assertEquals(MODULE_COUNT * FILES_PER_MODULE, collected.size(),
                "collection completeness: every file of every module must be collected, got "
                        + collected.size() + " of " + (MODULE_COUNT * FILES_PER_MODULE)
                        + " — concurrent add on a plain ArrayList loses entries (C1-001)");
        assertEquals(MODULE_COUNT * FILES_PER_MODULE, new HashSet<>(collected).size(),
                "collection completeness: no duplicate entries");
        Set<String> collectedPaths = new HashSet<>();
        for (File file : collected) {
            collectedPaths.add(file.getAbsolutePath());
        }
        assertEquals(expectedPaths, collectedPaths,
                "collected files must match the created files exactly");
    }

    /**
     * The finder only reads {@code getModules()}; register synthetic absolute-path
     * modules without going through pom parsing.
     */
    @SuppressWarnings("unchecked")
    private MavenModuleStructure structureOf(List<File> moduleDirs) throws Exception {
        MavenModuleStructure structure = new MavenModuleStructure();
        Field field = MavenModuleStructure.class.getDeclaredField("modules");
        field.setAccessible(true);
        Map<String, MavenModule> modules = (Map<String, MavenModule>) field.get(structure);
        for (int i = 0; i < moduleDirs.size(); i++) {
            MavenModule module = new MavenModule();
            module.setModulePath(moduleDirs.get(i).getAbsolutePath());
            modules.put("mod" + i, module);
        }
        return structure;
    }
}
