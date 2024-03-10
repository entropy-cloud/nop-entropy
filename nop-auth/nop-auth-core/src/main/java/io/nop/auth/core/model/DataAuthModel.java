/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.model;

import io.nop.auth.core.model._gen._DataAuthModel;

public class DataAuthModel extends _DataAuthModel {
    public DataAuthModel() {

    }

    public void sort(){
        for(ObjDataAuthModel objModel: this.getObjs()){
            objModel.sort();
        }
    }
}
