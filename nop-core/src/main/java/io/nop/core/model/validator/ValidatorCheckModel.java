/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.validator;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.validator._gen._ValidatorCheckModel;

@DataBean
public class ValidatorCheckModel extends _ValidatorCheckModel {
    public XNode toNode() {
        XNode node = XNode.make("check");
        node.setAttr("id", getId());
        node.setAttr("errorCode", getErrorCode());
        if (getErrorParams() != null && !getErrorParams().isEmpty()) {
            node.setAttr("errorParams", StringHelper.encodeStringMap(getErrorParams(), '=', ','));
        }
        if (getSeverity() != 0)
            node.setAttr("severity", getSeverity());

        if (getCondition() != null) {
            XNode conditionN = getCondition();
            node.appendChildren(conditionN.cloneChildren());
        }
        return node;
    }

}
