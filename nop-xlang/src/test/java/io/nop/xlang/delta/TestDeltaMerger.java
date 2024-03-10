/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.delta;

import io.nop.api.core.json.JSON;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.json.CompactJsonToXNodeTransformer;
import io.nop.core.lang.xml.json.CompactXNodeToJsonTransformer;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDeltaMerger extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void mergeJson() {
        IResource page = attachmentResource("page1.json");
        Map<String, Object> map = JsonTool.loadDeltaBean(page, Map.class, null);
        System.out.println(JSON.serialize(map, true));
        assertEquals(attachmentJsonText("page1-result.json"), JSON.serialize(map, true));
    }

    @Test
    public void mergeXml() {
        IResource page = attachmentResource("page2.xpage");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(page).getNode();
        System.out.println(node.xml());
        assertEquals(attachmentXmlText("page2-result.xpage"), node.xml());

        new XDslValidator(XDslKeys.DEFAULT).clean(node);
        Object value = new CompactXNodeToJsonTransformer().transformToObject(node);
        System.out.println(JsonTool.serialize(value, true));

        assertEquals(attachmentJsonText("page2-result.json"), JsonTool.serialize(value, true));
    }

    @Test
    public void compactXmlToJson() {
        IResource page = attachmentResource("page2-result.xpage");
        XNode node = XNodeParser.instance().parseFromResource(page);
        Object json = new CompactXNodeToJsonTransformer().transformToObject(node);
        System.out.println(JSON.serialize(json, true));

        XNode transformed = new CompactJsonToXNodeTransformer().transformToXNode(json);
        transformed.dump();
        Object json2 = new CompactXNodeToJsonTransformer().transformToObject(transformed);
        XNode node2 = new CompactJsonToXNodeTransformer().transformToXNode(json2);
        System.out.println(node2.xml());
        assertEquals(node2.xml(), transformed.xml());
    }

    @Test
    public void testPrototype() {
        IResource page = attachmentResource("page3.xform");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(page).getNode();
        System.out.println(node.xml());
        assertEquals(attachmentXmlText("page3-result.xform"), node.xml());
    }

    @Test
    public void testOverridePrototype() {
        IResource page = attachmentResource("page4.xform");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(page).getNode();
        System.out.println(node.xml());
        assertEquals(attachmentXmlText("page4-result.xform"), node.xml());
    }

    @Test
    public void testMergeList() {
        IResource resource = attachmentResource("test_merge.xml");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
    }

    @Test
    public void testGenExtends() {
        IResource resource = attachmentResource("test_gen_extends.xml");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
        assertEquals(attachmentXmlText("test_gen_extends.result.xml"), node.xml());
    }

    @Test
    public void testInheritAbstract() {
        IResource resource = attachmentResource("test_inherit_abstract.xml");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
        assertEquals(attachmentXmlText("test_inherit_abstract.result.xml"), node.xml());
    }

    @Test
    public void testNopAuthUser() {
        IResource resource = attachmentResource("NopAuthUser.xbiz");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
        assertEquals(attachmentXmlText("NopAuthUser.result.xml"), node.xml());
    }

    /**
     * join节点的body-type是list，合并的时候需要特殊识别处理
     */
    @Test
    public void testMergeJoin() {
        IResource resource = attachmentResource("test-merge-join.orm.xml");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
    }

    @Test
    public void testMergeSuper(){
        IResource resource = attachmentResource("merge/PageExt.xpage");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
        assertEquals(attachmentXmlText("merge/PageExt.result.xml"),node.xml());
    }

    @Test
    public void testMergeOverride(){
        IResource resource = attachmentResource("merge/PageExt2.xpage");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
        assertEquals(attachmentXmlText("merge/PageExt2.result.xml"),node.xml());
    }

    @Test
    public void testMergeNoId(){
        IResource resource = attachmentResource("merge2/page6Current.xpage");
        XNode node = DslNodeLoader.INSTANCE.loadFromResource(resource).getNode();
        node.dump();
        assertEquals(attachmentXmlText("merge2/page6.result.xml"),node.xml());
    }
}