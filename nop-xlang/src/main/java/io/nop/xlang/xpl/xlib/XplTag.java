/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.xlib;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagVariable;
import io.nop.xlang.xpl.XplSlotType;
import io.nop.xlang.xpl.xlib._gen._XplTag;

import java.util.HashMap;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_SLOT_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLIB_NODE_SLOT_NOT_SUPPORT_ARG;
import static io.nop.xlang.XLangErrors.ERR_XLIB_NOT_ALLOW_NAMED_SLOT_IF_DEFAULT_IS_USED;
import static io.nop.xlang.XLangErrors.ERR_XLIB_SLOT_NAME_CONFLICT_WITH_ATTR_NAME;

public class XplTag extends _XplTag implements IXplTag {

    private XplLibTagCompiler tagCompiler;
    private Map<String, IXplTagVariable> varsByAttrNames = new HashMap<>();
    private Map<String, IXplTagVariable> varsByVarNames = new HashMap<>();

    private String tagFuncName;

    @Override
    public String getTagFuncName() {
        return tagFuncName;
    }

    public void setTagFuncName(String tagFuncName) {
        checkAllowChange();
        this.tagFuncName = tagFuncName;
    }

    public void init() {
        if (getSlot(XLangConstants.SLOT_DEFAULT) != null) {
            if (getSlots().size() != 1)
                throw new NopException(ERR_XLIB_NOT_ALLOW_NAMED_SLOT_IF_DEFAULT_IS_USED).source(this);
        }

        for (XplTagAttribute attr : getAttrs()) {
            if (attr.getVarName() == null)
                attr.setVarName(StringHelper.xmlNameToVarName(attr.getName()));

            varsByAttrNames.put(attr.getName(), attr);
            varsByVarNames.put(attr.getVarName(), attr);
        }

        for (XplTagSlot slot : getSlots()) {
            if (slot.getVarName() == null) {
                slot.setVarName(XlibConstants.SLOT_VAR_PREFIX + slot.getName());
            }

            IXplTagVariable old = varsByAttrNames.put(slot.getVarName(), slot);
            if (old != null)
                throw new NopException(ERR_XLIB_SLOT_NAME_CONFLICT_WITH_ATTR_NAME).loc(slot.getLocation())
                        .param(ARG_SLOT_NAME, slot.getName()).param(ARG_ATTR_NAME, old.getName());

            old = varsByVarNames.put(slot.getVarName(), slot);
            if (old != null)
                throw new NopException(ERR_XLIB_SLOT_NAME_CONFLICT_WITH_ATTR_NAME).loc(slot.getLocation())
                        .param(ARG_SLOT_NAME, slot.getName()).param(ARG_ATTR_NAME, old.getName());

            if (slot.getSlotType() == XplSlotType.node) {
                if (slot.getAttrs().size() > 0)
                    throw new NopException(ERR_XLIB_NODE_SLOT_NOT_SUPPORT_ARG).source(slot);
            }
        }
    }

    @Override
    public XplLibTagCompiler getTagCompiler() {
        return tagCompiler;
    }

    public void setTagCompiler(XplLibTagCompiler tagCompiler) {
        checkAllowChange();
        this.tagCompiler = tagCompiler;
    }

    @Override
    public IFunctionModel getFunctionModel() {
        return tagCompiler.getFunctionModel(getOutputMode());
    }
}
