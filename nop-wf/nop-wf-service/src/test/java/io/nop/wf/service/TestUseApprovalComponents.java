package io.nop.wf.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.wf.core.biz.IApprovableBiz;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * use-approval 各组件的集成测试：
 * 1. IApprovableBiz default 方法 fast-fail 抛异常
 * 2. approval-support.xbiz 存在且含 5 个非空 mutation
 * 3. wf-approval.xlib 存在且含 notifyResult 标签
 * 4. approve-status.dict.yaml 字典可加载
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestUseApprovalComponents extends JunitAutoTestCase {

    @Test
    public void testApprovableBizDefaultMethodsThrow() {
        IApprovableBiz<Object> biz = new IApprovableBiz<Object>() {};
        IServiceContext ctx = new ServiceContextImpl();
        assertThrows(UnsupportedOperationException.class, () -> biz.submitForApproval("1", ctx));
        assertThrows(UnsupportedOperationException.class, () -> biz.withdrawApproval("1", ctx));
        assertThrows(UnsupportedOperationException.class, () -> biz.approve("1", ctx));
        assertThrows(UnsupportedOperationException.class, () -> biz.reject("1", ctx));
        assertThrows(UnsupportedOperationException.class, () -> biz.reverseApprove("1", ctx));
    }

    @Test
    public void testApproveStatusDictExists() {
        IResource r = VirtualFileSystem.instance().getResource("/dict/wf/approve-status.dict.yaml");
        assertNotNull(r);
        assertTrue(r.exists());
        String text = ResourceHelper.readText(r);
        assertTrue(text.contains("valueType: string"));
        assertTrue(text.contains("UNSUBMITTED"));
        assertTrue(text.contains("SUBMITTED"));
        assertTrue(text.contains("APPROVED"));
        assertTrue(text.contains("REJECTED"));
    }

    @Test
    public void testApprovalSupportXbizHasFiveMutations() {
        XNode node = XNode.load("/nop/wf/base/approval-support.xbiz");
        assertNotNull(node);
        assertEquals("biz", node.getTagName());

        XNode actions = node.childByTag("actions");
        assertNotNull(actions);

        List<XNode> mutations = actions.getChildren().stream()
                .filter(c -> "mutation".equals(c.getTagName()))
                .collect(Collectors.toList());
        assertEquals(5, mutations.size());

        List<String> names = mutations.stream().map(c -> c.attrText("name")).collect(Collectors.toList());
        assertTrue(names.containsAll(List.of("submitForApproval", "withdrawApproval", "approve", "reject", "reverseApprove")));

        for (XNode m : mutations) {
            XNode source = m.childByTag("source");
            assertNotNull(source, "mutation " + m.attrText("name") + " must have source");
            // source body may contain child elements (c:script), use xml() for full text
            String fullText = source.xml();
            assertTrue(fullText.length() > 50,
                    "source of " + m.attrText("name") + " must be non-trivial, length=" + fullText.length());
        }

        XNode submit = mutations.stream()
                .filter(c -> "submitForApproval".equals(c.attrText("name")))
                .findFirst().orElse(null);
        assertNotNull(submit);
        assertTrue(submit.childByTag("source").xml().contains("nopWorkflowManager"));
    }

    @Test
    public void testWfApprovalXlibHasNotifyResult() {
        XNode node = XNode.load("/nop/wf/xlib/wf-approval.xlib");
        assertNotNull(node);
        assertEquals("lib", node.getTagName());

        XNode tags = node.childByTag("tags");
        assertNotNull(tags);

        // xlib tag name IS the tag itself, not an attribute
        XNode notifyResult = tags.getChildren().stream()
                .filter(c -> "notifyResult".equals(c.getTagName()))
                .findFirst().orElse(null);
        assertNotNull(notifyResult, "notifyResult tag should exist. tags: "
                + tags.getChildren().stream().map(XNode::getTagName).collect(Collectors.toList()));
        assertTrue(notifyResult.childByTag("source").xml().contains("nopBizObjectManager"));
    }
}
