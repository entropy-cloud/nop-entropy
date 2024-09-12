package io.nop.ofbiz.transform;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.Test;

import java.io.File;

@NopTestConfig(localDb = true)
public class TestEntityDefModelToOrmModel extends JunitBaseTestCase {


    @Test
    public void testTransform() throws Exception {
        File dir = getTestResourcesDir();
        File dataDir = new File(dir, "ofbiz/entitydef").getCanonicalFile();
        System.out.println(dataDir);
        // C:\can\nop\nop-entropy\src\test\resources\ofbiz\entitydef
        // C:\can\nop\nop-entropy\nop-ofbiz\src\test\resources\ofbiz\entitydef
        for (File defFile : dataDir.listFiles()) {
            String path = "data/" + StringHelper.fileNameNoExt(defFile.getName()) + ".orm.xml";
            File ormFile = getTargetFile(path);
            EntityDefModelToOrmModel.transformDefFile(defFile, ormFile);
           // DslModelHelper.loadDslModel(new FileResource(ormFile));
        }
    }
}
