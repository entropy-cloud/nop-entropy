package io.nop.auth.web.page;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NopTestConfig(localDb = true)
@NopTestProperty(name = "nop.core.vfs.delta-layer-ids", value = "test")
public class TestDeltaView extends JunitBaseTestCase {

    @Test
    public void testDelta() {
        Object viewModel = ResourceComponentManager.instance()
                .loadComponentModel("/nop/auth/pages/NopAuthRole/NopAuthRole.view.xml");

        assertNotNull(viewModel);

        String objMetaPath = (String) BeanTool.instance().getProperty(viewModel, "objMeta");
        assertEquals("/nop/auth/model/NopAuthRole/NopAuthRole.xmeta", objMetaPath);

        IObjMeta objMeta = SchemaLoader.loadXMeta(objMetaPath);
        assertNotNull(objMeta);
        assertNotNull(objMeta.getProp("roleUsers"),
                "roleUsers prop should exist from delta/test layer");
    }
}
