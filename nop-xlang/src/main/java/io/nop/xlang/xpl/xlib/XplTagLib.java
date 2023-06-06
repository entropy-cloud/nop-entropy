/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.xlib;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.FreezeHelper;
import io.nop.api.core.util.INeedInit;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib._gen._XplTagLib;

import java.util.List;

public class XplTagLib extends _XplTagLib implements IXplTagLib, INeedInit {

    private List<ImportAsDeclaration> importExprs;
    private String defaultNamespace;

    @Override
    public void freeze(boolean cascade) {
        super.freeze(cascade);
        if (cascade) {
            importExprs = FreezeHelper.freezeList(importExprs, true);
        }
    }

    @Override
    public List<ImportAsDeclaration> getImportExprs() {
        return importExprs;
    }

    public void setImportExprs(List<ImportAsDeclaration> importExprs) {
        checkAllowChange();
        this.importExprs = importExprs;
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    public void setDefaultNamespace(String defaultNamespace) {
        checkAllowChange();
        this.defaultNamespace = defaultNamespace;
    }

    @Override
    public void init() {
        if (ConvertHelper.toFalsy(prop_get("ext:local"))) {
            String ns = XplLibHelper.getNamespaceFromLibPath(resourcePath());
            setDefaultNamespace(ns);
        }

        for (XplTag tag : getTags().values()) {
            tag.setTagFuncName('<' + resourcePath() + '#' + tag.getTagName() + '>');
            if (tag.getOutputMode() == null)
                tag.setOutputMode(XLangOutputMode.none);

            for (XplTagSlot slot : tag.getSlots()) {
                slot.setSlotFuncName("<" + resourcePath() + '#' + tag.getTagName() + "/slot[" + slot.getName() + "]>");
            }

            tag.setTagCompiler(new XplLibTagCompiler(this, tag));
            tag.init();
        }
    }
}