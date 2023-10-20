/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.batch.task;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;

import java.util.List;
import java.util.Map;

@DataBean
public class BizImportConfig {
    private int threadCount;

    private TreeBean filter;

    private List<String> keyPropNames;

    private int batchSize;

    private boolean allowAdd;

    private boolean allowUpdate;

    private FieldSelectionBean selection;

}
