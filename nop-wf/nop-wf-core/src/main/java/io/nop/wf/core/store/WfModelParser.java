/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.WfModel;
import io.nop.xlang.xdsl.DslModelParser;

public class WfModelParser {
    public static WfModel parseWorkflowModel(IResource resource) {
        WfModel wfModel = (WfModel) new DslModelParser(NopWfCoreConstants.XDEF_PATH_WF).parseFromResource(resource);
        return wfModel;
    }

    public static WfModel parseWorkflowNode(XNode node) {
        WfModel wfModel = (WfModel) new DslModelParser(NopWfCoreConstants.XDEF_PATH_WF).parseFromNode(node);
        return wfModel;
    }

}
