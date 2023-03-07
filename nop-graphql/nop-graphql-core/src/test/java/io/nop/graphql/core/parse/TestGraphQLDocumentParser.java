/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.parse;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.store.DefaultVirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.engine.GraphQLEngine;
import io.nop.graphql.core.engine.GraphQLSelectionResolver;
import io.nop.graphql.core.engine.SelectionBeanBuilder;
import io.nop.graphql.core.schema.BuiltinSchemaLoader;
import io.nop.graphql.core.schema.GraphQLSchema;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestGraphQLDocumentParser extends BaseTestCase {
    @Test
    public void testParse() {
        String gql = "query q($a:string){ a,b,c:x,f(a:$a){b,c}}";
        GraphQLDocument doc = parseQuery(gql);
        System.out.println(JsonTool.serialize(doc, true));
    }

    GraphQLDocument parseQuery(String gql) {
        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        return parser.parseFromText(null, gql);
    }

    @Test
    public void testParseSchema() {
        IResource resource = new ClassPathResource("classpath:_vfs/nop/graphql/base.graphql");
        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        parser.parseFromResource(resource);

        resource = new ClassPathResource("classpath:_vfs/nop/graphql/introspection.graphql");
        GraphQLDocument doc = parser.parseFromResource(resource);

        GraphQLSourcePrinter printer = new GraphQLSourcePrinter();
        printer.print(doc);
        System.out.println(printer);
    }

    @Test
    public void testQueryWithFragment() {
        VirtualFileSystem.registerInstance(new DefaultVirtualFileSystem());

        GraphQLEngine engine = new GraphQLEngine();

        IResource resource = attachmentResource("test-fragment.graphql");
        GraphQLSchema schema = new BuiltinSchemaLoader(null, true).load();
        assertNotNull(schema.getType("Map"));

        engine.setBuiltinSchema(schema);

        GraphQLDocument doc = new GraphQLDocumentParser().parseFromResource(resource);
        new GraphQLSelectionResolver(engine, 10).resolveSelection(doc);
        GraphQLOperation op = doc.getOperation();
        assertFalse(op.isExceedDepth(10));

        FieldSelectionBean selectionBean = new SelectionBeanBuilder(null).buildSelectionBean(op.getName(),
                op.getSelectionSet(), new HashMap<>());
        System.out.println(JsonTool.serialize(selectionBean, true));
        assertEquals(attachmentJsonText("test-fragment-selection.json"), JsonTool.serialize(selectionBean, true));

        GraphQLSourcePrinter printer = new GraphQLSourcePrinter();
        printer.print(doc);
        System.out.println(printer);
    }
}
