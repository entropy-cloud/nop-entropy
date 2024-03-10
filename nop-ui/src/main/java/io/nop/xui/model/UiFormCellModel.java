/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui.model;

import io.nop.xui.model._gen._UiFormCellModel;

public class UiFormCellModel extends _UiFormCellModel {
    public UiFormCellModel() {

    }

    public void init(){
        validate();
    }

    public void validate() {
        UiRefViewModel view = getView();
        if (view != null)
            view.validate();
    }
}
