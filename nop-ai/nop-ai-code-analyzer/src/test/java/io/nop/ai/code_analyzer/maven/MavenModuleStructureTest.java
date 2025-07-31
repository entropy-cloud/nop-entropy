package io.nop.ai.code_analyzer.maven;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;

public class MavenModuleStructureTest extends BaseTestCase {
    @Test
    public void testLoad() {
        MavenModuleStructure structure = new MavenModuleStructure();
        structure.load(new File(getModuleDir(), "../.."));
        structure.simplifyDependencies();
        System.out.println(structure);
    }
}
