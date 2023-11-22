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
        return path.substring(0, path.lastIndexOf('/'));
    }
}
