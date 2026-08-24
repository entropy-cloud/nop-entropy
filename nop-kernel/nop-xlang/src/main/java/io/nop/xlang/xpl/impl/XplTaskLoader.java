/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;

import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_PARSE_MISSING_RESOURCE;

public class XplTaskLoader implements IResourceObjectLoader<XplTaskResult> {
    @Override
    public XplTaskResult loadObjectFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        IEvalAction action = XLang.parseXpl(resource, XLangOutputMode.none);
        if (action == null)
            throw new NopException(ERR_COMPONENT_PARSE_MISSING_RESOURCE).param(ARG_RESOURCE_PATH, path);
        Object returnValue = action.invoke(XLang.newEvalScope());
        XplTaskResult ret = new XplTaskResult();
        ret.setReturnValue(returnValue);
        return ret;
    }
}
