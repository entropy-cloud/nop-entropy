package io.nop.kernel.cli;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.FileResource;
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
        File vfsDir = getVfsDir();
        setTestConfig(CoreConfigs.CFG_RESOURCE_DIR_OVERRIDE_VFS, FileHelper.getAbsolutePath(vfsDir));
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    public static File getVfsDir() {
        File projectDir = MavenDirHelper.projectDir(TestMappingBasedMarkdownParser.class);
        File vfsDir = new File(projectDir, "demo/_vfs");
        return vfsDir;
    }

    @Test
    public void testGenerator() {
        forceStackTrace();
        IRecordMappingManager mappingManager = new RecordMappingManagerImpl();

        IResource ormModelFile = new FileResource(new File(getVfsDir(), "../model/demo.orm.xml"));

        Object bean = ResourceComponentManager.instance().loadComponentModel(ormModelFile.getPath());
        System.out.println(JsonTool.serialize(bean, true));

        RecordMappingConfig config = mappingManager.getRecordMappingConfig("orm.OrmModel_to_Md");
        String text = new MappingBasedMarkdownGenerator(config, bean).generateText(XLang.newEvalScope());
        System.out.println(text);

        FileHelper.writeText(new File(getVfsDir(), "../model/demo.orm.md"), text, null);
    }

    @Test
    public void testParse() {
        forceStackTrace();

        IResource ormModelFile = new FileResource(new File(getVfsDir(), "../model/demo.orm.md"));

        Object bean = ResourceComponentManager.instance().loadComponentModel(ormModelFile.getPath());
        System.out.println(JsonTool.serialize(bean, true));
    }
}
