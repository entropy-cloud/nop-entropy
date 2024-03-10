/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.ast._gen._GraphQLInputDefinition;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;

import java.util.BitSet;

import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_PROP_ID;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_PROP_ID_CONFLICT;

public class GraphQLInputDefinition extends _GraphQLInputDefinition implements IGraphQLObjectDefinition {
    public String getFieldsSource() {
        GraphQLSourcePrinter printer = new GraphQLSourcePrinter();
        try {
            for (GraphQLInputFieldDefinition field : getFields()) {
                printer.getOut().indent();
                printer.visit(field);
            }
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return printer.toString();
    }

    public void initPropId() {
        BitSet propIds = new BitSet();
        int count = 0;
        for (GraphQLInputFieldDefinition field : fields) {
            int propId = field.getPropIdFromMeta();
            if (propId > 0) {
                if (propIds.get(propId))
                    throw new NopException(ERR_GRAPHQL_FIELD_PROP_ID_CONFLICT)
                            .param(ARG_FIELD_NAME, field.getName())
                            .param(ARG_PROP_ID, propId)
                            .param(ARG_OBJ_TYPE, getName());
                propIds.set(propId);
                count++;
                field.setPropId(propId);
            }
        }

        if (count == fields.size())
            return;

        int nextPropId = 1;
        for (GraphQLInputFieldDefinition field : fields) {
            int propId = field.getPropId();
            if (propId <= 0) {
                nextPropId = CollectionHelper.nextFreeIndex(propIds, nextPropId);
                field.setPropId(nextPropId++);
            }
        }
    }
}
