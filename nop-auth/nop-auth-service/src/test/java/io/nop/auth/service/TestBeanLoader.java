package io.nop.auth.service;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ioc.loader.AppBeanContainerLoader;
import io.nop.xlang.xdsl.DslNodeLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBeanLoader extends JunitBaseTestCase {

    @Test
    public void testEmbeddedBean() {
        IResource resource = attachmentResource("test-dataSources.beans.xml");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();

        node = new AppBeanContainerLoader().loadFromResource("test", resource).toConfigNode();
        node.dump();
        assertEquals(attachmentXmlText("merged.beans.xml"), node.xml());
    }
}
