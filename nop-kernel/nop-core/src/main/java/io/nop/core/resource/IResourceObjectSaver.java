package io.nop.core.resource;

/**
 * 与IResourceObjectLoader对偶的保存接口，用于IResource和模型对象之间的双向转换。
 *
 * @param <T> 模型对象类型
 */
public interface IResourceObjectSaver<T> {
    void saveObjectToResource(IResource resource, T obj);
}