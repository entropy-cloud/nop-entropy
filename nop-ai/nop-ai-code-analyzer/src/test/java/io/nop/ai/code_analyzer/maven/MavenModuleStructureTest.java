package io.nop.ai.code_analyzer.maven;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class MavenModuleStructureTest extends BaseTestCase {
    private static final Logger LOG = LoggerFactory.getLogger(MavenModuleStructureTest.class);

    @Test
    public void testLoad() {
        MavenModuleStructure structure = new MavenModuleStructure();
        structure.load(new File(getModuleDir(), "../.."));
        structure.simplifyDependencies();
        LOG.info(structure.toString());
    }
}
