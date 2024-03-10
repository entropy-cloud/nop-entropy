/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;

import java.util.HashMap;
import java.util.Map;

import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OLD_LOC;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DUPLICATE_OBJ_DEF;

public class GraphQLDocumentHelper {
    public static Map<String, GraphQLObjectDefinition> parseObjectDefinitions(IResource resource) {
        GraphQLDocument doc = new GraphQLDocumentParser().parseFromResource(resource);
        return getObjectDefinitions(doc);
    }

    public static Map<String, GraphQLObjectDefinition> getObjectDefinitions(GraphQLDocument doc) {
        Map<String, GraphQLObjectDefinition> defs = new HashMap<>();
        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLObjectDefinition) {
                GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) def;
                GraphQLObjectDefinition oldDef = defs.putIfAbsent(objDef.getName(), objDef);
                if (oldDef != null) {
                    if (objDef.getExtension()) {
                        oldDef.merge(objDef, true);
                    } else {
                        throw new NopException(ERR_GRAPHQL_DUPLICATE_OBJ_DEF).source(def)
                                .param(ARG_OBJ_NAME, objDef.getName()).param(ARG_OLD_LOC, oldDef.getLocation());
                    }
                }
                objDef.init();
            }
        }
        return defs;
    }
}
