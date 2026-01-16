package io.nop.sys.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.core.split.TestCaseJsonDataSplitter;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

@NopTestConfig(localDb = true,initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCaseDataSplitter extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testSplit(){
        File dir = new File(getTargetDir(),"test");

        TestCaseJsonDataSplitter splitter = new TestCaseJsonDataSplitter(daoProvider);
        Map<String,Object> data = attachmentBean("test-case.json", Map.class);
        splitter.splitToDir(data, dir);
    }
}
