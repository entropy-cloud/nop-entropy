/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.pdm;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPdmParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        IResource resource = attachmentResource("demo.pdm");
        PdmModelParser parser = new PdmModelParser();
        OrmModel ormModel = parser.parseFromResource(resource);

        IObjMeta objMeta = SchemaLoader.loadXMeta("/nop/schema/orm/orm.xdef");
        XNode node = new DslModelToXNodeTransformer(objMeta).transformToXNode(ormModel);
        node.dump();
        assertEquals(attachmentXml("demo.orm.xml").xml(), node.xml());

        String jsonText = JsonTool.stringify(ormModel, null, "  ");
        System.out.println(jsonText);

        Map<String, Object> json = (Map<String, Object>) JsonTool.parse(JsonTool.stringify(ormModel));
        XNode node2 = new DslModelToXNodeTransformer(objMeta).transformToXNode(json);
        node2.dump();
        assertEquals(node2.xml(), node.xml());
    }

    @Test
    public void testRelation(){
        IResource resource = attachmentResource("test-relation.pdm");
        PdmModelParser parser = new PdmModelParser();
        OrmModel ormModel = parser.parseFromResource(resource);

        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/orm/orm.xdef",ormModel);
        node.dump();
        assertEquals(attachmentXml("test-relation.orm.xml").xml(), node.xml());
    }
}
