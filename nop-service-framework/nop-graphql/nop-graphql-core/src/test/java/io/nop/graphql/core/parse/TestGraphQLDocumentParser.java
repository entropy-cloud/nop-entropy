/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.parse;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
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

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_PARSE_UNSUPPORTED_INLINE_FRAGMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        new GraphQLSelectionResolver(engine, null,10).resolveSelection(doc);
        GraphQLOperation op = doc.getOperation();
        assertFalse(op.isExceedDepth(10));

        FieldSelectionBean selectionBean = new SelectionBeanBuilder(new HashMap<>()).buildSelectionBean(op.getName(),
                op.getSelectionSet(), new HashMap<>());
        System.out.println(JsonTool.serialize(selectionBean, true));
        assertEquals(attachmentJsonText("test-fragment-selection.json"), JsonTool.serialize(selectionBean, true));

        GraphQLSourcePrinter printer = new GraphQLSourcePrinter();
        printer.print(doc);
        System.out.println(printer);
    }

    /**
     * inline fragment（... on Type）不受支持："on"被误当作fragment名后产生与根因无关的解析错误。
     * 修复后在fragment名位置识别on关键字并抛出带明确错误码的异常。
     */
    @Test
    public void testInlineFragmentRejectedWithClearError() {
        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        String gql = "query { a { ... on B { c } } }";

        NopException err = assertThrows(NopException.class, () -> parser.parseFromText(null, gql));
        assertEquals(ERR_GRAPHQL_PARSE_UNSUPPORTED_INLINE_FRAGMENT.getErrorCode(), err.getErrorCode());
    }
}
