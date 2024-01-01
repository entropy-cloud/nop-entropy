package io.nop.core.resource.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;

import java.util.Set;

/**
 * DefaultVirtualFileSystem内部实现时使用的接口。
 */
public interface IDeltaResourceStore extends IResourceStore {
    Set<String> getClassPathFiles();

    IResource getSuperResource(String path, boolean returnNullIfNotExists);

    IResource getRawResource(String path);
}
