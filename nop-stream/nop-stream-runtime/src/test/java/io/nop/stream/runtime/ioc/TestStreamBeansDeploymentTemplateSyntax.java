/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ioc;

import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.model.BeansModel;
import io.nop.xlang.xdsl.DslModelParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text-level schema compliance verification for the deployment templates embedded in
 * nop-stream's beans.xml files.
 *
 * <p>The deployment templates (multi-JVM scaffold for {@code StreamControlRpcServer} /
 * {@code StreamControlRpcProxyFactory} / {@code RpcDistributedExecutor}) live inside
 * XML comments, so the container never loads them and beans.xdef validation never
 * reaches the template text. This test closes that gap (plan guide #24-adjacent): it
 * extracts each template fragment from the comment block, wraps it in a
 * {@code <beans>} root with the required namespace declarations, and parses it as a
 * beans.xdef document — any schema violation (e.g. the historical
 * {@code ioc:configMethod} / {@code ioc:bean="true"} attributes) fails the parse.
 *
 * <p>Honest limitation: this is text-level (syntax + schema) verification, NOT
 * container instantiation. The template references deployment-specific beans
 * ({@code taskManager_node0}, {@code nopDaoProvider}) that only exist in an app
 * assembly, so container-level loading is out of scope by design.
 */
class TestStreamBeansDeploymentTemplateSyntax {

    private static final Pattern IOC_CONFIG_METHOD = Pattern.compile("ioc:configMethod");
    private static final Pattern IOC_BEAN = Pattern.compile("ioc:bean(?!-)");

    @BeforeAll
    static void init() {
        // VFS + xdef registries run below INITIALIZER_PRIORITY_IOC; stopping there
        // avoids triggering nop-dao datasource config (same rationale as
        // TestStreamModuleDiscovery / TestStreamControlRpcBootstrap).
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void controlRpcDeploymentTemplateIsSchemaCompliant() {
        String fileText = readBeansFile("/nop/stream/beans/stream-control-rpc.beans.xml");
        assertFalse(IOC_CONFIG_METHOD.matcher(fileText).find(),
                "stream-control-rpc.beans.xml must not contain ioc:configMethod (whole file, incl. comments)");
        assertFalse(IOC_BEAN.matcher(fileText).find(),
                "stream-control-rpc.beans.xml must not contain ioc:bean (whole file, incl. comments)");

        String fragment = extractTemplateFragment(fileText, "Deployment scaffold");
        // StreamControlRpcServer is constructor-only (serviceName, serviceInterface,
        // serviceImpl, messageService, topic) — 5 constructor-args must express it.
        assertTrue(fragment.contains("<constructor-arg index=\"0\""),
                "server bean must use <constructor-arg> for serviceName");
        assertTrue(fragment.contains("<constructor-arg index=\"1\""),
                "server bean must use <constructor-arg> for serviceInterface");
        assertTrue(fragment.contains("<constructor-arg index=\"2\""),
                "server bean must use <constructor-arg> for serviceImpl");
        assertTrue(fragment.contains("<constructor-arg index=\"3\""),
                "server bean must use <constructor-arg> for messageService");
        assertTrue(fragment.contains("<constructor-arg index=\"4\""),
                "server bean must use <constructor-arg> for topic");
        assertFalse(fragment.contains("<property name=\"serviceName\""),
                "constructor-only property must not be property-injected: serviceName");
        assertFalse(fragment.contains("<property name=\"serviceInterface\""),
                "constructor-only property must not be property-injected: serviceInterface");
        assertFalse(fragment.contains("<property name=\"messageService\""),
                "constructor-only property must not be property-injected: messageService");
        // StreamControlRpcProxyFactory is constructor-only with 4 args.
        assertTrue(fragment.contains("StreamControlRpcProxyFactory"),
                "proxy bean must be present in the deployment scaffold");
        assertFalse(fragment.contains("ioc:configMethod"),
                "template fragment must not contain ioc:configMethod");
        assertFalse(IOC_BEAN.matcher(fragment).find(),
                "template fragment must not contain ioc:bean");

        parseAsBeansDocument(fragment);
    }

    @Test
    void dataPlaneDeploymentTemplateIsSchemaCompliant() {
        String fileText = readBeansFile("/nop/stream/beans/stream-data-plane.beans.xml");
        assertFalse(IOC_CONFIG_METHOD.matcher(fileText).find(),
                "stream-data-plane.beans.xml must not contain ioc:configMethod (whole file, incl. comments)");
        assertFalse(IOC_BEAN.matcher(fileText).find(),
                "stream-data-plane.beans.xml must not contain ioc:bean (whole file, incl. comments)");

        String fragment = extractTemplateFragment(fileText, "Deployment template");
        // RpcDistributedExecutor's messageService is a constructor parameter
        // (private final, no setter) — property injection was the historical break.
        assertTrue(fragment.contains("<constructor-arg index=\"0\" ref=\"nopStreamMessageService\"/>"),
                "RpcDistributedExecutor must receive messageService via <constructor-arg index=\"0\">");
        assertFalse(fragment.contains("<property name=\"messageService\""),
                "constructor-only property must not be property-injected: messageService");
        // ioc:bean="true" was the historical break; nested <bean> is the schema form.
        assertTrue(fragment.contains("<property name=\"dataPlaneWireCodec\">")
                        && fragment.contains("<bean class=\"io.nop.stream.runtime.transport.SysDaoWireCodec\"/>"),
                "dataPlaneWireCodec must be wired via a nested <bean>");
        assertFalse(IOC_BEAN.matcher(fragment).find(),
                "template fragment must not contain ioc:bean");

        parseAsBeansDocument(fragment);
    }

    @Test
    void parseFailsOnUnknownIocAttribute() {
        // Negative control: the schema validation must actually be active. A fragment
        // carrying the historical ioc:configMethod attribute must fail to parse.
        String broken = "<bean id=\"x\" class=\"io.nop.stream.runtime.rpc.StreamControlRpcServer\""
                + " ioc:configMethod=\"start\">"
                + "<constructor-arg index=\"0\" value=\"svc\"/></bean>";
        assertThrows(Exception.class, () -> parseAsBeansDocument(broken),
                "beans.xdef validation must reject unknown ioc: attributes (ioc:configMethod)");
    }

    private static String readBeansFile(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        assertNotNull(resource, "beans resource must exist: " + vfsPath);
        String text = resource.readText();
        assertNotNull(text, "beans resource must be readable: " + vfsPath);
        return text;
    }

    /**
     * Extracts the XML bean fragment from the deployment-template comment block:
     * from the first {@code <bean} to the last {@code </bean>} inside the comment
     * identified by {@code marker}. Prose text before/after the beans is dropped.
     */
    private static String extractTemplateFragment(String fileText, String marker) {
        int commentStart = fileText.indexOf(marker);
        assertTrue(commentStart >= 0, "deployment template comment must contain marker: " + marker);
        int commentBegin = fileText.lastIndexOf("<!--", commentStart);
        int commentEnd = fileText.indexOf("-->", commentStart);
        assertTrue(commentBegin >= 0 && commentEnd > commentBegin,
                "deployment template must be inside an XML comment");
        String commentBody = fileText.substring(commentBegin + "<!--".length(), commentEnd);

        int firstBean = commentBody.indexOf("<bean");
        int lastBeanEnd = commentBody.lastIndexOf("</bean>");
        assertTrue(firstBean >= 0 && lastBeanEnd > firstBean,
                "deployment template comment must contain at least one <bean> fragment");
        return commentBody.substring(firstBean, lastBeanEnd + "</bean>".length());
    }

    /**
     * Wraps the template fragment in a {@code <beans>} root with the namespace
     * declarations of a real beans.xdef document and parses it. Any schema violation
     * (unknown attribute / child, bad type) throws; success proves the template text
     * is beans.xdef-compliant.
     */
    private static BeansModel parseAsBeansDocument(String fragment) {
        String wrapped = "<beans x:schema=\"/nop/schema/beans.xdef\""
                + " xmlns:x=\"/nop/schema/xdsl.xdef\" xmlns:ioc=\"ioc\">"
                + fragment + "</beans>";
        XNode node = XNode.parse(wrapped);
        Object parsed = new DslModelParser().parseFromNode(node);
        assertNotNull(parsed, "wrapped template must parse to a BeansModel");
        assertTrue(parsed instanceof BeansModel,
                "wrapped template must parse to a BeansModel, got " + parsed.getClass());
        return (BeansModel) parsed;
    }
}
