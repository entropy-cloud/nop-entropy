/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.annotations.core.Ordered;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.core.reflect.IFunctionModel;
import io.nop.graphql.core.ast._gen._GraphQLObjectDefinition;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;
import io.nop.xlang.xmeta.IObjMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.api.core.util.IOrdered.NORMAL_PRIORITY;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OLD_LOC;
import static io.nop.graphql.core.GraphQLErrors.ARG_OLD_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_EXTEND_TYPE_MISMATCH;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_FETCHER_IS_ALREADY_DEFINED;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_NO_TYPE;

public class GraphQLObjectDefinition extends _GraphQLObjectDefinition implements INeedInit {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLObjectDefinition.class);

    private Map<String, GraphQLFieldDefinition> fieldsMap;

    private IObjMeta objMeta;

    public IObjMeta getObjMeta() {
        return objMeta;
    }

    public void setObjMeta(IObjMeta objMeta) {
        this.objMeta = objMeta;
    }

    @Override
    public void init() {
        fieldsMap = new HashMap<>();
        if (fields == null)
            fields = new ArrayList<>();

        for (GraphQLFieldDefinition field : fields) {
            fieldsMap.put(field.getName(), field);
        }
    }

    public void removeFieldsNotInMeta() {
        if (objMeta != null) {
            List<GraphQLFieldDefinition> removed = null;
            for (GraphQLFieldDefinition field : getFields()) {
                if (!objMeta.hasProp(field.getName())) {
                    if (fieldsMap != null) {
                        fieldsMap.remove(field.getName());
                    }
                    if (removed == null)
                        removed = new ArrayList<>();
                    removed.add(field);
                }
            }
            if (removed != null) {
                for (GraphQLFieldDefinition field : removed) {
                    getFields().remove(field);
                }
            }
        }
    }

    @Override
    public void setFields(List<GraphQLFieldDefinition> value) {
        super.setFields(value);
        fieldsMap = null;
    }

    public Set<String> getFieldNames() {
        return fieldsMap.keySet();
    }

    public String getFieldsSource() {
        GraphQLSourcePrinter printer = new GraphQLSourcePrinter();
        try {
            for (GraphQLFieldDefinition field : getFields()) {
                printer.getOut().indent();
                printer.visit(field);
            }
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return printer.toString();
    }

    public GraphQLFieldDefinition getField(String name) {
        if (fieldsMap == null)
            this.init();
        return fieldsMap.get(name);
    }

    public void merge(GraphQLObjectDefinition def) {
        for (GraphQLFieldDefinition field : def.getFields()) {
            mergeField(field);
        }
    }

    @Override
    public void validate() {
        for (GraphQLFieldDefinition field : getFields()) {
            if (field.getType() == null) {
                throw new NopException(ERR_GRAPHQL_FIELD_NO_TYPE).source(field).param(ARG_OBJ_NAME, getName())
                        .param(ARG_FIELD_NAME, field.getName());
            }
        }
    }

    public void addField(GraphQLFieldDefinition field) {
        fields.add(field);
        if (fieldsMap != null) {
            fieldsMap.put(field.getName(), field);
        }
    }

    public void mergeField(GraphQLFieldDefinition field) {
        if (fieldsMap == null)
            this.init();

        GraphQLFieldDefinition old = fieldsMap.get(field.getName());
        if (old == null) {
            // 如果objMeta存在，则所有字段必须都在meta中定义
            if (objMeta == null || field.getOperationType() != null || objMeta.hasProp(field.getName())) {
                fieldsMap.put(field.getName(), field);
                this.fields.add(field);
            }
        } else {
            if (old.getFunctionModel() != null && field.getFunctionModel() != null) {
                int p1 = getPriority(old.getFunctionModel());
                int p2 = getPriority(field.getFunctionModel());
                if (p1 < p2) {
                    // 原先的优先级更高，则舍弃当前的方法
                    LOG.info("nop.graphql.ignore-method-with-lower-priority:old={},new={}", old.getFunctionModel(),
                            field.getFunctionModel());
                    return;
                }

                if (p1 > p2) {
                    LOG.info("nop.graphql.replace-method-with-higher-priority:old={},new={}", old.getFunctionModel(),
                            field.getFunctionModel());
                    return;
                }

                throw new NopException(ERR_GRAPHQL_FIELD_FETCHER_IS_ALREADY_DEFINED).source(field)
                        .param(ARG_OBJ_NAME, getName()).param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_OLD_LOC, old.getLocation());
            }

            if (old.getFetcher() != null && field.getFetcher() != null
                    && old.getFetcher() != BeanPropertyFetcher.INSTANCE)
                throw new NopException(ERR_GRAPHQL_FIELD_FETCHER_IS_ALREADY_DEFINED).source(field)
                        .param(ARG_OBJ_NAME, getName()).param(ARG_FIELD_NAME, field.getName())
                        .param(ARG_OLD_LOC, old.getLocation());

            if (old.getFetcher() == null || old.getFetcher() == BeanPropertyFetcher.INSTANCE) {
                old.setFetcher(field.getFetcher());
            }

            if (old.getMakerCheckerMeta() == null)
                old.setMakerCheckerMeta(field.getMakerCheckerMeta());

            if (old.getServiceAction() == null) {
                old.setServiceAction(field.getServiceAction());
            }

            if (old.getPropMeta() == null) {
                old.setPropMeta(field.getPropMeta());
            }

            if (old.getAuth() == null) {
                old.setAuth(field.getAuth());
            }

            // old或者field任意一个标记为lazy则认为是lazy的
            if(field.isLazy())
                old.setLazy(field.isLazy());

            if (old.getType() == null) {
                old.setType(field.getType());
            } else if (field.getType() != null) {
                GraphQLType mergedType = old.getType().mergeType(field.getType());
                if (mergedType == null) {
                    throw new NopException(ERR_GRAPHQL_FIELD_EXTEND_TYPE_MISMATCH).param(ARG_OBJ_NAME, getName())
                            .param(ARG_FIELD_NAME, field.getName()).param(ARG_TYPE, field.getType())
                            .param(ARG_OLD_TYPE, old.getType());
                } else {
                    if (mergedType.getASTParent() != null && mergedType.getASTParent() != field)
                        mergedType = mergedType.deepClone();
                    field.setType(mergedType);
                }
            }
        }
    }

    int getPriority(IFunctionModel fn) {
        Ordered priority = fn.getAnnotation(Ordered.class);
        return priority == null ? NORMAL_PRIORITY : priority.value();
    }
}