# Virtual File System

The Nop platform uses the **VirtualFileSystem** to uniformly manage resource files within the system. During startup, it automatically scans all files under the `_vfs` directory in the classpath and aggregates them into the virtual file system.

1. If files are added or deleted after startup, the **VirtualFileSystem** will not automatically detect these changes and will need to be explicitly refreshed by calling `VirtualFileSystem.instance().refresh(true)` to update the file cache.

2. At startup, the current working directory's `_vfs` directory is also added to the virtual file system, potentially overriding files with the same path in the classpath.

3. It is not allowed for multiple jar files to have the same virtual path. However, the current working directory's `_vfs` directory can override a jar file's virtual path.

## Delta Path

The **VirtualFileSystem** defines a special path pattern `/_delta/{deltaName}/`, such as `/_vfs/_delta/default/xxx`. If a delta directory exists, it will be configured based on the specified `nop.core.vfs.delta-layer-ids` property (default if not set), creating multiple layers of deltas. Higher-level deltas will automatically override lower-level deltas.

For example, setting `nop.core.vfs.delta-layer-ids=product,default` will create a delta structure like:

```
/_vfs/_delta
        /product
            /nop
            ...
        /default
            /nop
            ...
    /nop
    ...
```

When retrieving files via `VirtualFileSystem.instance().getResource("/nop/auth/xxx")`, it first checks `/_vfs/product/nop/auth/xxx`. If not found, it continues to check `/_vfs/default/nop/auth/xxx`. If still not found, it finally accesses the base file at `/nop/auth/xxx`.

When calling `VirtualFileSystem.instance().getChildren("/nop/auth/xxx")`, it merges all files from multiple delta layers into a single collection and returns the combined result.

## Module System

The **VirtualFileSystem** identifies the first two directories as module IDs, such as `/_vfs/nop/auth` corresponding to the module `nop/auth`. By default, when defining an Excel model, we also use the module name as the base, e.g., `nop-auth.orm.xlsx`.

However, not all two-level directories are identified as modules. A `_module` file must be present in the directory to mark it as a module. For example, `/_vfs/nop/auth/_module`. The **ModuleManager** is responsible for finding all modules.

## Data Name Space

The data name space resolves paths based on the `nop.core.resource.store.data-root-dir` configuration (default `/data`). For example, `data:/a/b.txt` maps to `./data/a/b.txt`.

## Related Configuration Items

| Parameter       | Default Value | Description                                                                 |
|-----------------|---------------|-----------------------------------------------------------------------------|
| nop.core.vfs.delta-layer-ids | default      | Specifies the delta layer configuration, e.g., `_delta/product, _delta/default`. Corresponds to paths like `/_delta/product` and `/_delta/default`. |
||||
||||