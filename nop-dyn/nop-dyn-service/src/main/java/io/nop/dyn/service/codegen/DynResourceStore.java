package io.nop.dyn.service.codegen;

import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DynResourceStore implements IResourceStore {
    // volatile（I-01 COW 配套）：InMemoryCodeCache 生成点以写时复制重建 merged 后经此字段
    // 整体替换——VFS/外部持有者持有的是本 store 实例，内层替换必须原子可见
    private volatile IResourceStore store;
    private final Consumer<String> prepare;

    public DynResourceStore(IResourceStore store, Consumer<String> prepare) {
        this.store = store;
        this.prepare = prepare;
    }

    /**
     * 写时复制发布支撑（I-01）：仅由 InMemoryCodeCache 在 synchronized 写点调用。
     */
    void setStore(IResourceStore store) {
        this.store = store;
    }

    /**
     * MA-02（C2-003）：命中读无锁——懒生成 miss 才进入 DCL 临界区，高频命中不再为
     * 锁内 DB I/O + XPL 生成排队（生成期窗口语义登记 nop-dyn.md）。锁内 prepare 可能经
     * COW 替换 this.store，替换后的复读必须经 volatile 字段读取。
     */
    @Override
    public IResource getResource(String path, boolean returnNullIfNotExists) {
        IResource resource = store.getResource(path, true);
        if (resource != null) {
            return resource;
        }
        synchronized (this) {
            resource = store.getResource(path, true);
            if (resource != null)
                return resource;
            prepare.accept(path);
            return store.getResource(path, returnNullIfNotExists);
        }
    }

    @Override
    public List<? extends IResource> getChildren(String path) {
        return store.getChildren(path);
    }

    @Override
    public boolean supportSave(String path) {
        return store.supportSave(path);
    }

    @Override
    public String saveResource(String path, IResource resource, IStepProgressListener listener, Map<String, Object> options) {
        return store.saveResource(path, resource, listener, options);
    }
}
