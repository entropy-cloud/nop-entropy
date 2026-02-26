package io.nop.xlang.xdef;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXDefMergeLoader {
    private static final String OUTPUT_DIR = "target/merge-xdefs";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMergeAllXDefs() {
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        XDefMergeOptions options = XDefMergeOptions.forMetaModel();
        XDefMergeLoader loader = new XDefMergeLoader(options);

        Collection<? extends IResource> files = VirtualFileSystem.instance().getAllResources("/nop/schema", ".xdef");
        assertTrue(files.size() > 50, "Should find at least 50 xdef files");

        int successCount = 0;
        int failCount = 0;

        for (IResource file : files) {
            String path = file.getStdPath();
            try {
                loader.loadFromResource(file);

                XNode merged = loader.loadFromResource(file);
                assertNotNull(merged, "Merged node should not be null for " + path);

                String outputPath = OUTPUT_DIR + path.substring("/nop/schema".length());
                File outFile = new File(outputPath);
                File parentDir = outFile.getParentFile();
                if (!parentDir.exists()) {
                    parentDir.mkdirs();
                }

                merged.saveToResource(new FileResource(outFile), "UTF-8");
                successCount++;

                System.out.println("Merged: " + path + " -> " + outputPath + 
                    " (refs: " + loader.getLoadedPaths().size() + 
                    ", defines: " + loader.getCollectedDefines().size() + ")");
            } catch (Exception e) {
                failCount++;
                System.err.println("Failed to merge: " + path + " - " + e.getMessage());
            }
        }

        System.out.println("\n=== Merge Summary ===");
        System.out.println("Total files: " + files.size());
        System.out.println("Success: " + successCount);
        System.out.println("Failed: " + failCount);
        System.out.println("Output directory: " + outputDir.getAbsolutePath());

        assertTrue(successCount > 0, "At least some files should merge successfully");
    }

    @Test
    public void testInlineMode() {
        XDefMergeOptions options = XDefMergeOptions.forAi();
        XDefMergeLoader loader = new XDefMergeLoader(options);

        XNode merged = loader.loadFromPath("/nop/schema/orm/orm.xdef");
        assertNotNull(merged);

        String outputPath = OUTPUT_DIR + "/inline/orm.xml";
        File outFile = new File(outputPath);
        File parentDir = outFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        merged.saveToResource(new FileResource(outFile), "UTF-8");

        System.out.println("Inline mode output: " + outputPath);
    }

    @Test
    public void testDefineMode() {
        XDefMergeOptions options = XDefMergeOptions.forMetaModel();
        XDefMergeLoader loader = new XDefMergeLoader(options);

        XNode merged = loader.loadFromPath("/nop/schema/beans.xdef");
        assertNotNull(merged);

        String outputPath = OUTPUT_DIR + "/define/beans.xml";
        File outFile = new File(outputPath);
        File parentDir = outFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        merged.saveToResource(new FileResource(outFile), "UTF-8");

        System.out.println("Define mode output: " + outputPath);
        System.out.println("Collected defines: " + loader.getCollectedDefines().keySet());
    }
}
