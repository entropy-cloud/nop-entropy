package io.nop.rpc.cluster;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.2-C8-001/CL-017（plan 2559 裁定 G 同 diff 勘正）：beans 绑定勘正钉——
 * nopServerChooser_roundRobin 原误绑 nopLoadBalance_random（假保护即缺陷），
 * 勘正为 nopLoadBalance_roundRobin；活跃计数 adapter 新 bean 引用落入装配。
 */
public class TestClusterRpcBeansBindings {

    private String beansXml() throws Exception {
        InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("_vfs/nop/rpc/beans/rpc-cluster-defaults.beans.xml");
        if (in == null)
            in = TestClusterRpcBeansBindings.class.getResourceAsStream("/_vfs/nop/rpc/beans/rpc-cluster-defaults.beans.xml");
        assertTrue(in != null, "rpc-cluster-defaults.beans.xml must be on the classpath");
        try (InputStream stream = in) {
            byte[] bytes = stream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void roundRobinChooserBindsRoundRobinLoadBalance() throws Exception {
        String xml = beansXml();
        int idPos = xml.indexOf("nopServerChooser_roundRobin");
        assertTrue(idPos > 0, "roundRobin chooser bean must exist");
        int endPos = xml.indexOf("</bean>", idPos);
        String beanBody = xml.substring(idPos, endPos);

        assertTrue(beanBody.contains("nopLoadBalance_roundRobin"),
                "CL-017: nopServerChooser_roundRobin must bind nopLoadBalance_roundRobin");
        assertFalse(beanBody.contains("nopLoadBalance_random"),
                "CL-017: the mis-binding to nopLoadBalance_random must be removed");
    }

    @Test
    public void activeCountAdapterWiredIntoAssembly() throws Exception {
        String xml = beansXml();
        assertTrue(
                xml.contains("<property name=\"loadBalanceAdapter\" ref=\"nopServiceLoadBalanceAdapter\"/>"),
                "C8-001 裁定 G(c): loadBalanceAdapter bean reference must be wired into the assembly");
    }
}
