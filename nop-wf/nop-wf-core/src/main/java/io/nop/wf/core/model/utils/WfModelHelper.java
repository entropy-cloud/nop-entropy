/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.model.utils;

import io.nop.wf.core.NopWfCoreConstants;

public class WfModelHelper {
    public static String guessWfNameFromFilePath(String path) {
        String basePath;
        if (path.startsWith("/nop/wf/")) {
            basePath = "/nop/wf/";
        } else if (path.startsWith("wf:")) {
            basePath = "wf:";
        } else if (path.startsWith(NopWfCoreConstants.RESOLVE_WF_NS_PREFIX)) {
            basePath = NopWfCoreConstants.RESOLVE_WF_NS_PREFIX;
        } else {
            basePath = "";
        }
        path = path.substring(basePath.length());
        int pos = path.lastIndexOf('/');
        // 去掉前缀后没有目录分隔符则无法推导wfName，显式报错而非substring越界
        if (pos < 0)
            throw new IllegalArgumentException("nop.wf.invalid-wf-file-path:" + path);
        return path.substring(0, pos);
    }
}
