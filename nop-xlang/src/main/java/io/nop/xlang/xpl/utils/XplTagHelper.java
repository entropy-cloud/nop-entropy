/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagAttribute;

import java.util.HashMap;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_ATTR_IS_MANDATORY;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_TAG_ATTR;

public class XplTagHelper {
    public static Map<String, Object> prepareTagArgs(IXplTag tag, Map<String, Object> args, IEvalScope scope) {
        args.forEach((name, value) -> {
            if (tag.getAttr(name) == null)
                throw new NopException(ERR_XPL_UNKNOWN_TAG_ATTR)
                        .source(tag)
                        .param(ARG_TAG_NAME, tag.getTagName())
                        .param(ARG_ATTR_NAME, name)
                        .param(ARG_ALLOWED_NAMES, tag.getAttrNames());
        });

        Map<String, Object> ret = args;
        for (IXplTagAttribute attr : tag.getAttrs()) {
            if (attr.isImplicit()) {
                if (!args.containsKey(attr.getName())) {
                    Object value = scope.getValue(attr.getName());
                    if (value == null && !scope.containsValue(attr.getName()))
                        throw new NopException(XLangErrors.ERR_XPL_TAG_NO_IMPLICIT_ATTR)
                                .source(tag)
                                .param(ARG_TAG_NAME, tag.getTagName())
                                .param(ARG_ATTR_NAME, attr.getName());
                    if (ret == args)
                        ret = new HashMap<>(args);
                    ret.put(attr.getName(), value);
                }
            }

            if (attr.isMandatory()) {
                Object value = ret.get(attr.getName());
                if (StringHelper.isEmptyObject(value))
                    throw new NopException(ERR_XPL_TAG_ATTR_IS_MANDATORY)
                            .source(tag)
                            .param(ARG_TAG_NAME, tag.getTagName())
                            .param(ARG_ATTR_NAME, attr.getName());
            }
        }
        return ret;
    }

    public static Object[] buildTagArgValues(IXplTag tag, Map<String, Object> args, IEvalScope scope) {
//        args.forEach((name, value) -> {
//            if (tag.getAttr(name) == null)
//                throw new NopException(ERR_XPL_UNKNOWN_TAG_ATTR)
//                        .source(tag)
//                        .param(ARG_TAG_NAME, tag.getTagName())
//                        .param(ARG_ATTR_NAME, name)
//                        .param(ARG_ALLOWED_NAMES, tag.getAttrNames());
//        });

        IFunctionModel fnModel = tag.getFunctionModel();
        Object[] argValues = new Object[fnModel.getArgCount()];


        for (int i = 0, n = fnModel.getArgCount(); i < n; i++) {
            IFunctionArgument arg = fnModel.getArgs().get(i);
            String name = arg.getName();
            IXplTagAttribute attr = tag.getAttr(name);
            if (attr == null) {
                argValues[i] = args.get(name);
                continue;
            }

            if (attr.isImplicit()) {
                if (!args.containsKey(attr.getName())) {
                    Object value = scope.getValue(attr.getName());
                    if (value == null && !scope.containsValue(attr.getName()))
                        throw new NopException(XLangErrors.ERR_XPL_TAG_NO_IMPLICIT_ATTR)
                                .source(tag)
                                .param(ARG_TAG_NAME, tag.getTagName())
                                .param(ARG_ATTR_NAME, attr.getName());
                    argValues[i] = value;
                } else {
                    argValues[i] = args.get(name);
                }
            } else {
                Object value = args.get(name);
                if (attr.isMandatory()) {
                    if (StringHelper.isEmptyObject(value))
                        throw new NopException(ERR_XPL_TAG_ATTR_IS_MANDATORY)
                                .source(tag)
                                .param(ARG_TAG_NAME, tag.getTagName())
                                .param(ARG_ATTR_NAME, attr.getName());
                }
                argValues[i] = value;
            }
        }
        return argValues;
    }
}
