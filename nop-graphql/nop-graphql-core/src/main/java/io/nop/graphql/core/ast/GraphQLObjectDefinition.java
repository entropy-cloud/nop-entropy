/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.ast._gen._GraphQLObjectDefinition;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.impl.ObjSelectionMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_PROP_ID;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_NO_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_PROP_ID_CONFLICT;

public class GraphQLObjectDefinition extends _GraphQLObjectDefinition implements INeedInit, IGraphQLObjectDefinition {
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

    public void initPropId() {
        BitSet propIds = new BitSet();
        int count = 0;
        for (GraphQLFieldDefinition field : fields) {
            int propId = field.getPropIdFromMeta();
            if (propId > 0) {
                if (propIds.get(propId))
                    throw new NopException(ERR_GRAPHQL_FIELD_PROP_ID_CONFLICT)
                            .param(ARG_FIELD_NAME, field.getName())
                            .param(ARG_PROP_ID, propId)
                            .param(ARG_OBJ_TYPE, getName());
                propIds.set(propId);
                field.setPropId(propId);
                count++;
            }
        }

        if (count == fields.size())
            return;

        int nextPropId = 1;
        for (GraphQLFieldDefinition field : fields) {
            int propId = field.getPropId();
            if (propId <= 0) {
                nextPropId = CollectionHelper.nextFreeIndex(propIds, nextPropId);
                field.setPropId(nextPropId++);
            }
        }
    }

    public void removeFieldsNotInMeta() {
        if (objMeta != null) {
            List<GraphQLFieldDefinition> removed = null;
            for (GraphQLFieldDefinition field : getFields()) {
                // 如果强制要求自动创建，则可以突破meta限制
                if (field.isAutoCreate())
                    continue;

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

    public FieldSelectionBean getSelection(String fragmentName) {
        if (objMeta != null) {
            ObjSelectionMeta selectionMeta = objMeta.getSelection(fragmentName);
            if (selectionMeta != null && selectionMeta.getMapping() != null)
                return selectionMeta.getMapping();
        }
        return null;
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

    public void merge(GraphQLObjectDefinition def, boolean force) {
        if (def.getFields() == null)
            return;
        for (GraphQLFieldDefinition field : def.getFields()) {
            mergeField(field, force);
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
        this.makeFields().add(field);
        if (fieldsMap != null) {
            fieldsMap.put(field.getName(), field);
        }
    }

    public void mergeField(GraphQLFieldDefinition field, boolean replace) {
        if (fieldsMap == null)
            this.init();

        GraphQLFieldDefinition old = fieldsMap.get(field.getName());
        if (old == null) {
            if (field.isAutoCreate()) {
                fieldsMap.put(field.getName(), field);
                this.fields.add(field);
            } else {
                // 如果objMeta存在，则所有字段必须都在meta中定义
                if (objMeta == null || field.getOperationType() != null || objMeta.hasProp(field.getName())) {
                    fieldsMap.put(field.getName(), field);
                    this.fields.add(field);
                }
            }
        } else {
            if (replace) {
                if (field.getFetcher() != null) {
                    if (old.getFetcher() != null && old.getFetcher() != BeanPropertyFetcher.INSTANCE) {
                        LOG.info("nop.graphql.replace-fetcher:field={},old={},new={}", field.getName(),
                                old.getLocation(), field.getLocation());
                    }
                    old.setLocation(field.getLocation());
                    old.setFetcher(field.getFetcher());
                    // serviceAction与fetcher应该是配对的
                    old.setServiceAction(field.getServiceAction());
                }

                if (field.getMakerCheckerMeta() != null)
                    old.setMakerCheckerMeta(field.getMakerCheckerMeta());

                if (field.getServiceAction() != null && field.getFetcher() == null) {
                    old.setServiceAction(field.getServiceAction());
                }

                if (field.getPropMeta() != null) {
                    old.setPropMeta(field.getPropMeta());
                }

                if (field.getBeanPropMeta() != null) {
                    old.setBeanPropMeta(field.getBeanPropMeta());
                }


                if (field.getAuth() != null) {
                    old.setAuth(field.getAuth());
                }

                if (field.getLazy() != null)
                    old.setLazy(field.getLazy());

                if (field.getType() != null) {
                    old.setType(field.getType());
                }

                if (field.getArgsNormalizer() != null) {
                    old.setArgsNormalizer(field.getArgsNormalizer());
                }
            } else {

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

                if (old.getBeanPropMeta() == null)
                    old.setBeanPropMeta(field.getBeanPropMeta());


                if (old.getAuth() == null) {
                    old.setAuth(field.getAuth());
                }

                if (old.getLazy() == null)
                    old.setLazy(field.getLazy());

                if (old.getType() == null) {
                    old.setType(field.getType());
                }


                if (old.getArgsNormalizer() == null) {
                    old.setArgsNormalizer(field.getArgsNormalizer());
                }
            }
        }
    }
}