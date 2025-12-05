package io.nop.converter;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JunitBaseTestCase extends BaseTestCase {
    protected TestInfo testInfo;

    @BeforeEach
    public void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    @BeforeAll
    public static void initialize(){
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy(){
        CoreInitialization.destroy();
    }

    File getCaseDataDir() {
        String dir = testInfo.getTestClass().orElseThrow().getName().replace('.', '/');
        dir += "/" + testInfo.getTestMethod().orElseThrow().getName();
        return new File(getCasesDir(), dir);
    }

    File getOutputFile(String fileName) {
        return new File(getCaseDataDir(), "output/" + fileName);
    }

    public IResource inputResource(String path) {
        File file = new File(getCaseDataDir(), "input/" + path);
        return new FileResource(file);
    }

    protected void outputText(String fileName, String text) {
        String data = FileHelper.readText(getOutputFile(fileName), null);
        assertEquals(data, text);
    }
}
