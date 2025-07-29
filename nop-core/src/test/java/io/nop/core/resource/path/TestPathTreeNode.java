package io.nop.core.resource.path;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestPathTreeNode extends BaseTestCase {
    @BeforeAll
    public static void initAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Disabled
    @Test
    public void testLoadAll() {
        File dir = new File(getModuleDir(), "..");
        PathTreeNode node = ResourceToPathTreeBuilder.buildFromResource(new FileResource(dir), 100,
                res -> !res.getName().startsWith(".") && !res.getName().equals("target") && !res.getName().equals("_dump")
                        && !res.getName().equals("log")
                        && !res.getPath().contains("src/test/") && !res.getName().startsWith("_") && !res.getName().equals("cases")
                        && !res.getPath().contains("/nop-entropy/docs") && !res.getPath().contains("/nop-entropy/docs-en"));

        FileHelper.writeText(getTargetFile("result.txt"), node.buildTreeString(), null);
    }
}
