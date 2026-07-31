package io.nop.ai.coder.service;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.rpc.model.ApiServiceModel;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAiApiModel extends JunitBaseTestCase {

    private XNode loadDemoApiNode() {
        File file = new File(getModuleDir(), "demo/resources/app/demo/model/ai-gen.api.xml");
        assertTrue(file.exists(), "demo api model should exist: " + file);
        return XNodeParser.instance().parseFromResource(new FileResource(file));
    }

    private AiApiModel buildDemoApiModel() {
        XNode node = loadDemoApiNode();
        return AiApiModel.buildFromApiNode(node);
    }

    @Test
    public void testEnforceServicePostfix() {
        XNode node = loadDemoApiNode();
        XNode services = node.childByTag("services");
        assertEquals("DailyMenu", services.getChildren().get(0).attrText("name"));

        AiApiModel model = AiApiModel.buildFromApiNode(node, true);

        assertEquals("DailyMenuService", services.getChildren().get(0).attrText("name"));
        assertNotNull(model);
    }

    @Test
    public void testServiceNames() {
        AiApiModel model = buildDemoApiModel();
        List<String> names = model.getServiceNames();
        assertTrue(names.contains("DailyMenuService"), "service names: " + names);
        assertTrue(names.contains("InOutStockService"), "service names: " + names);
    }

    @Test
    public void testGetServiceMethodModel() {
        AiApiModel model = buildDemoApiModel();
        ApiServiceModel service = model.getServiceModel("DailyMenuService");
        assertNotNull(service);
        assertEquals("每日菜单", service.getDisplayName());

        ApiMethodModel method = model.getServiceMethodModel("DailyMenuService", "bindToSettlementDate");
        assertNotNull(method);
        assertEquals("BindSettlementDateRequest", method.getRequestMessage());
        assertTrue(method.isMutation());
    }

    @Test
    public void testGetMethodJava() {
        AiApiModel model = buildDemoApiModel();
        String java = model.getMethodJava("DailyMenuService", "bindToSettlementDate");

        assertTrue(java.contains("interface DailyMenuService{"));
        assertTrue(java.contains("BindSettlementDateResponse bindToSettlementDate(@RequestBean BindSettlementDateRequest request);"));
        assertTrue(java.contains("@BizMutation"));
        assertTrue(java.contains("class BindSettlementDateRequest{"));
        assertTrue(java.contains("class BindSettlementDateResponse{"));
    }

    @Test
    public void testGetMethodInfos() {
        AiApiModel model = buildDemoApiModel();
        List<MethodInfo> infos = model.getMethodInfos();
        assertTrue(infos.stream().anyMatch(info ->
                info.getServiceName().equals("DailyMenuService") && info.getMethodName().equals("bindToSettlementDate")));
    }

    @Test
    public void testGetServiceNode() {
        AiApiModel model = buildDemoApiModel();
        XNode serviceNode = model.getServiceNode("DailyMenuService");
        assertNotNull(serviceNode);
        assertEquals("DailyMenuService", serviceNode.attrText("name"));

        XNode methodNode = model.getServiceMethodNode("InOutStockService", "lock");
        assertNotNull(methodNode);
        assertEquals("lock", methodNode.attrText("name"));

        assertNull(model.getServiceNode("NotExistService"));
    }
}
