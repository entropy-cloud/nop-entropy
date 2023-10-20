/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.stream.cep.model;

import io.nop.stream.cep.model._gen._CepPatternGroupModel;

public class CepPatternGroupModel extends _CepPatternGroupModel implements ICepPatternGroupModel {
    public CepPatternGroupModel() {

    }

    public String getType() {
        return "group";
    }
}
