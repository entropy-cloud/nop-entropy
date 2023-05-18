/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.utils;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.core.reflect.bean.BeanTool;

public class RpcTaskHelper {
    public static ApiResponse<TaskStatusBean> toTaskStatusResponse(ApiResponse<?> response) {
        Object data = response.getData();
        if (data != null) {
            data = BeanTool.castBeanToType(response.getData(), TaskStatusBean.class);
            ((ApiResponse) response).setData(data);
        }
        return (ApiResponse<TaskStatusBean>) response;
    }
}