/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.store;

import io.nop.core.resource.IResource;
import io.nop.wf.core.model.IWorkflowModel;

import java.util.List;

public interface IWorkflowModelStore {
    String getLatestVersion(String wfName);

    /**
     * 得到工作流模型的所有版本号，从小到到排列
     */
    List<String> getAllVersions(String wfName);

    IResource getModelResource(String wfName, String wfVersion);

    IWorkflowModel getWorkflowModel(String wfName, String wfVersion);

    void removeModelCache(String wfName, String wfVersion);
}