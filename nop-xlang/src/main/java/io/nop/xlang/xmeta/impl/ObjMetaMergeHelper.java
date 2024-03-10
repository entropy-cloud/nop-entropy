/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.KeyedList;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaNode;

import java.util.List;
import java.util.Objects;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_REF_PROP;
import static io.nop.xlang.XLangErrors.ERR_XDEF_PROP_NOT_ALLOW_OVERRIDE;

public class ObjMetaMergeHelper {

    public static void mergeRefSchema(ISchemaNode schema) {
        ISchema refSchema = schema.getRefSchema();
        if (refSchema != null) {
            if (schema instanceof SchemaImpl) {
                ObjMetaMergeHelper.mergeRef((SchemaImpl) schema, refSchema);
            } else {
                ObjMetaMergeHelper.mergeRefObj((IObjSchemaImpl) schema, refSchema);
            }
        }
    }

    private static void mergeRefObj(IObjSchemaImpl schema, ISchema refSchema) {
        List<? extends IObjPropMeta> refProps = refSchema.getProps();
        if (refProps != null) {
            KeyedList<IObjPropMeta> props = new KeyedList<>(IObjPropMeta::key);
            props.addAll(refProps);
            if (schema.getProps() != null) {
                for (IObjPropMeta refProp : schema.getProps()) {
                    IObjPropMeta old = props.getByKey(refProp.getName());
                    if (old != null) {
                        if (!Objects.equals(old.getType(), refProp.getType()))
                            throw new NopException(ERR_XDEF_PROP_NOT_ALLOW_OVERRIDE).source(schema)
                                    .param(ARG_REF_PROP, refProp).param(ARG_PROP_NAME, refProp.getName());
                    }
                    props.add(refProp);
                }
            }
            schema.setProps((KeyedList) props);
        }

        if (schema.getUnknownTagSchema() == null)
            schema.setUnknownTagSchema(refSchema.getUnknownTagSchema());

        if (schema.getUnknownAttrSchema() == null)
            schema.setUnknownAttrSchema(refSchema.getUnknownAttrSchema());

        if (schema.getName() != null) {
            if (schema.getExtendsType() == null && refSchema.getType() != null) {
                schema.setExtendsType(refSchema.getType());
            }
        }

        if (schema.getMinProperties() == null && refSchema.getMinProperties() != null)
            schema.setMinProperties(refSchema.getMinProperties());

        if (schema.getMaxProperties() == null && refSchema.getMaxProperties() != null)
            schema.setMaxProperties(refSchema.getMaxProperties());

        if (schema.getUniqueProp() == null && refSchema.getUniqueProp() != null)
            schema.setUniqueProp(refSchema.getUniqueProp());

        if (schema.getDisplayName() == null && refSchema.getDisplayName() != null)
            schema.setDisplayName(refSchema.getDisplayName());

        if (schema.getDescription() == null && refSchema.getDescription() != null)
            schema.setDescription(refSchema.getDescription());

        if (schema.getType() == null && refSchema.getType() != null)
            schema.setType(refSchema.getType());

        if (schema.getDomain() == null && refSchema.getDomain() != null)
            schema.setDomain(refSchema.getDomain());

        if (schema.getStdDomain() == null && refSchema.getStdDomain() != null)
            schema.setStdDomain(refSchema.getStdDomain());

        if (schema.getValidator() == null && refSchema.getValidator() != null)
            schema.setValidator(refSchema.getValidator());
    }

    private static void mergeRef(SchemaImpl schema, ISchema refSchema) {
        if (schema.getDict() == null)
            schema.setDict(refSchema.getDict());

        if (schema.getPrecision() == null)
            schema.setPrecision(refSchema.getPrecision());

        if (schema.getScale() == null)
            schema.setScale(refSchema.getScale());

        if (schema.getPattern() == null)
            schema.setPattern(refSchema.getPattern());

        if (schema.getMin() == null)
            schema.setMin(refSchema.getMin());

        if (schema.getMax() == null)
            schema.setMax(refSchema.getMax());

        if (schema.getExcludeMin() == null)
            schema.setExcludeMin(refSchema.getExcludeMin());

        if (schema.getExcludeMax() == null)
            schema.setExcludeMax(refSchema.getExcludeMax());

        if (schema.getMinLength() == null)
            schema.setMinLength(refSchema.getMinLength());

        if (schema.getMaxLength() == null)
            schema.setMaxLength(refSchema.getMaxLength());

        if (schema.getMultipleOf() == null)
            schema.setMultipleOf(refSchema.getMultipleOf());

        if (schema.getMinItems() == null)
            schema.setMinItems(refSchema.getMinItems());

        if (schema.getMaxItems() == null) {
            schema.setMaxItems(refSchema.getMaxItems());
        }

        if (schema.getKeyProp() == null)
            schema.setKeyProp(refSchema.getKeyProp());

        if (schema.getOrderProp() == null)
            schema.setOrderProp(refSchema.getOrderProp());

        if (schema.getItemSchema() == null)
            schema.setItemSchema(refSchema.getItemSchema());

        mergeRefObj(schema, refSchema);
    }
}
