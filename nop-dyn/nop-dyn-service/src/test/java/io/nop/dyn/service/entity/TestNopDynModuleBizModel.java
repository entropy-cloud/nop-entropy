package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestNopDynModuleBizModel extends JunitBaseTestCase {

    @Inject
    NopDynModuleBizModel bizModel;

    @Inject
    IOrmTemplate ormTemplate;

    @Test
    public void testGenerateByAI() {
        String ormText = attachmentText("test.orm.xml");

        ormTemplate.runInSession(() -> {
            bizModel.generateByAI(ormText);
        });
    }
}
