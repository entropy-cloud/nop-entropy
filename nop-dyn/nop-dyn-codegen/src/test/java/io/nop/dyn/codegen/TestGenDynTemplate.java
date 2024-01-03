package io.nop.dyn.codegen;

import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestGenDynTemplate extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGenTemplate() {
        XCodeGenerator gen = new XCodeGenerator(FileHelper.getFileUrl(this.getModuleDir()), FileHelper.getFileUrl(this.getTargetFile("gen-dyn")));
        IEvalScope scope = XLang.newEvalScope();
        gen.renderModel("../model/nop-dyn.orm.xlsx", "/nop/templates/dyn", "/", scope);
    }
}
