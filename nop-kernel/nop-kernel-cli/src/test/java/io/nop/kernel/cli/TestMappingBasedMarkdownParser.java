package io.nop.kernel.cli;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.impl.RecordMappingManagerImpl;
import io.nop.record_mapping.md.MappingBasedMarkdownGenerator;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestMappingBasedMarkdownParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        File projectDir = MavenDirHelper.projectDir(TestMappingBasedMarkdownParser.class);
        File vfsDir = new File(projectDir, "demo/_vfs");
        setTestConfig(CoreConfigs.CFG_RESOURCE_DIR_OVERRIDE_VFS, FileHelper.getAbsolutePath(vfsDir));
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGenerator() {
        forceStackTrace();
        IRecordMappingManager mappingManager = new RecordMappingManagerImpl();

        Object bean = ResourceComponentManager.instance().loadComponentModel("/test/demo.orm.xml");
        System.out.println(JsonTool.serialize(bean, true));

        RecordMappingConfig config = mappingManager.getRecordMappingConfig("orm.OrmModel_to_Md");
        String text = new MappingBasedMarkdownGenerator(config, bean).generateText(XLang.newEvalScope());
        System.out.println(text);

        FileHelper.writeText(getTargetFile("test/demo.orm.md"), text, null);
    }
}
