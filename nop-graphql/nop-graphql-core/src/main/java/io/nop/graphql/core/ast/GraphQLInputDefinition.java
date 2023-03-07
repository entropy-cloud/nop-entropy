/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.ast._gen._GraphQLInputDefinition;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;

public class GraphQLInputDefinition extends _GraphQLInputDefinition {
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
}
