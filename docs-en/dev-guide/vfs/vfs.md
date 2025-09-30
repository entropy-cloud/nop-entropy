
# Virtual File System

The Nop platform uses the VirtualFileSystem to centrally manage resource files in the system. At startup, it automatically scans all file paths under the \_vfs directory on the classpath and aggregates them into a virtual file system.

1. If files are created or deleted after startup, the VirtualFileSystem will not automatically detect them; you need to call VirtualFileSystem.instance().refresh(true) to update the file cache.

2. At startup, the \_vfs directory under the current working directory is also added to the virtual file system, and it will override the **files with the same path name under the classpath**.

3. Multiple JAR packages are not allowed to contain files with the same virtual path, but the \_vfs directory under the current working directory can override same-path files inside JAR packages.

## Delta Paths

In the virtual file system, a special path pattern /\_delta/{deltaName}/ is defined, for example /\_vfs/\_delta/default/xxx.
If a delta directory exists, multiple delta layers will be formed according to the configured nop.core.vfs.delta-layer-ids (default is default). Higher delta layers automatically override lower ones.

For example, with nop.core.vfs.delta-layer-ids=product,default, for the following virtual file layout

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

* When fetching a file via VirtualFileSystem.instance().getResource("/nop/auth/xxx"), it will first look for /\_vfs/product/nop/auth/xxx. If it does not exist, it will continue to look for
  /\_vfs/default/nop/auth/xxx. If it still does not exist, it will then access the base /nop/auth/xxx file.

* VirtualFileSystem.instance().getChildren("/nop/auth/xxx") merges all files obtained from multiple delta layers and returns the merged collection.

* Under a delta directory, model files can set x:extends="super" on the root node to indicate inheriting the file from the next lower delta layer; this avoids writing the full path and facilitates mixing multiple delta layers.

## Module System

The virtual file system recognizes the first two path segments as the module ID. For example, /\_vfs/nop/auth corresponds to the module
nop/auth. In general, when we define Excel model files we also base them on the module name, e.g., nop-auth.orm.xlsx.

However, not all two-level directories are recognized as modules. You need to add a \_module file under the directory; this is an empty file used only as a placeholder. For example, /_vfs/nop/auth/_ module.

* ModuleManager is responsible for discovering all modules.

## Data Namespace
Paths under the data namespace are resolved according to the nop.core.resource.store.data-root-dir configuration; the root path defaults to `/data`.
For example, `data:/a/b.txt` actually corresponds to the file path `./data/a/b.txt`.

## Related Configuration Items

|Name|Default Value|Description|
|---|---|---|
|nop.core.vfs.delta-layer-ids|default|Specifies the Delta layers of the delta file system, e.g., \_platform,product,app. Corresponds to virtual paths such as /\_delta/product|
||||
|||| 

<!-- SOURCE_MD5:341736408b86ca4c019407701372915c-->
