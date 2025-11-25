package io.nop.kernel.cli;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.RecordMappingContext;
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
    public void testSerialize() {
        IRecordMappingManager mappingManager = new RecordMappingManagerImpl();

        Object bean = ResourceComponentManager.instance().loadComponentModel("/test/demo.orm.xml");

        IRecordMapping mapping = mappingManager.getRecordMapping("orm.OrmModel_to_Md");
        Object md = mapping.map(bean, new RecordMappingContext());

        RecordMappingConfig config = mappingManager.getRecordMappingConfig("orm.Md_to_OrmModel");
        String text = new MappingBasedMarkdownGenerator(config, md).generateText(XLang.newEvalScope());
        System.out.println(text);

        FileHelper.writeText(getTargetFile("test/demo.orm.md"), text, null);
    }
}
