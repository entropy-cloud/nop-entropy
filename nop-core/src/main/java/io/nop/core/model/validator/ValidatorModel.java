/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.validator;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.validator._gen._ValidatorModel;

@DataBean
public class ValidatorModel extends _ValidatorModel {

    public XNode toNode() {
        XNode node = XNode.make("validator");
        if (getErrorCode() != null)
            node.setAttr("errorCode", getErrorCode());
        if (getErrorParams() != null && !getErrorParams().isEmpty()) {
            node.setAttr("errorParams", StringHelper.encodeStringMap(getErrorParams(), '=', ','));
        }
        if (getSeverity() != 0)
            node.setAttr("severity", getSeverity());

        if (getChecks() != null) {
            for (ValidatorCheckModel check : getChecks()) {
                node.appendChild(check.toNode());
            }
        }

        if (getCondition() != null) {
            XNode conditionN = getCondition().cloneInstance();
            conditionN.setTagName("condition");
            node.appendChild(conditionN);
        }

        return node;
    }
}