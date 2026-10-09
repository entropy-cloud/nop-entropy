package io.nop.biz.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.biz.BizConstants;
import io.nop.biz.model.BizLoaderModel;
import io.nop.biz.model.BizModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2.6-007 修复回归（plan 2378 R1.12 Phase 2）：
 * xbiz.xdef BizLoaderModel 新增 &lt;auth&gt; 子元素后，带 &lt;auth&gt; 的 &lt;loader&gt; 可解析，
 * 且 BizModelToGraphQLDefinition.toBuilder 把 loaderModel.auth 装配到 GraphQLFieldDefinition。
 * 无 &lt;auth&gt; 的 loader 保持 null（不补缺省）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestXbizLoaderAuthAssembly extends JunitBaseTestCase {

    private static final String PATH = "/nop/test/model/TestLoaderAuth/TestLoaderAuth.xbiz";

    @Test
    public void testLoaderAuthParsesAndAssembles() {
        BizModel model = parseModel();

        BizLoaderModel authed = model.getLoader("authedField");
        assertNotNull(authed, "authedField loader must parse");
        assertNotNull(authed.getAuth(), "loader <auth> must parse into BizLoaderModel.auth");
        assertNotNull(authed.getAuth().getRoles());
        assertTrue(authed.getAuth().getRoles().contains("admin"), "loader auth roles should contain admin");
        assertEquals("TestLoaderAuth:authedField", authed.getAuth().getPermissions().toString(),
                "loader auth permissions must mirror the xbiz declaration");

        GraphQLFieldDefinition field = BizModelToGraphQLDefinition.INSTANCE.toBuilder("TestLoaderAuth", authed,
                new TypeRegistry());
        assertNotNull(field.getAuth(), "toBuilder must assemble loaderModel.auth onto the field (P1-2.6-007)");
    }

    @Test
    public void testLoaderWithoutAuthStaysNull() {
        BizModel model = parseModel();

        BizLoaderModel open = model.getLoader("openField");
        assertNotNull(open, "openField loader must parse");
        assertNull(open.getAuth(), "loader without <auth> must keep auth=null (no default backfill, plan 2378)");

        GraphQLFieldDefinition field = BizModelToGraphQLDefinition.INSTANCE.toBuilder("TestLoaderAuth", open,
                new TypeRegistry());
        assertNull(field.getAuth(), "toBuilder must not synthesize auth for loaders without declaration");
    }

    private BizModel parseModel() {
        IResource resource = VirtualFileSystem.instance().getResource(PATH);
        assertTrue(resource.exists(), "fixture must exist: " + PATH);
        return (BizModel) new DslModelParser(BizConstants.XDEF_BIZ).parseFromResource(resource, true);
    }
}
