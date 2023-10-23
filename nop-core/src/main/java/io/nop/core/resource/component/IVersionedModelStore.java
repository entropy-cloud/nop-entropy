package io.nop.core.resource.component;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResource;

import java.util.List;

public interface IVersionedModelStore<T extends IComponentModel> {
    Long getLatestVersion(String modelName);

    /**
     * 得到模型的所有版本号，从小到到排列
     */
    List<Long> getAllVersions(String modelName);

    IResource getModelResource(String modelName, Long modelVersion);

    T getModel(String modelName, Long modelVersion);

    void removeModelCache(String modelName, Long modelVersion);
}
