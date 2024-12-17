# 虚拟文件系统

Nop平台通过VirtualFileSystem来统一管理系统中的资源文件。启动时，会自动扫描classpath下\_vfs目录下所有的文件路径，把它们收集到一起构成虚拟文件系统。

1. 如果启动后新建或者删除文件，则VirtualFileSystem并不会自动识别到，需要调用VirtualFileSystem.instance().refresh(true)
   来更新文件缓存。

2. 启动时当前工作目录下的\_vfs目录也会加入到虚拟文件系统，而且它会覆盖**classpath下同路径名的文件**。

3. 多个jar包中不允许存在相同虚拟路径的文件，但是当前工作目录下的\_vfs目录可以覆盖jar包中的同路径名的文件。

## Delta路径

在虚拟文件系统中，定义了一个特殊的路径模式/\_delta/{deltaName}/，例如/\_vfs/\_delta/default/xxx。
如果存在delta目录，则会根据指定的nop.core.vfs.delta-layer-ids配置(缺省为default)，构成多个delta层，高层的delta会自动覆盖低层的delta。

例如nop.core.vfs.delta-layer-ids=product,default，则对于如下虚拟文件布局

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

* VirtualFileSystem.instance().getResource("/nop/auth/xxx")获取文件时，会先查找/\_vfs/product/nop/auth/xxx，如果不存在，会继续查找
  /\_vfs/default/nop/auth/xxx。如果仍然不存在，才会访问基础的/nop/auth/xxx文件。

* VirtualFileSystem.instance().getChildren("/nop/auth/xxx")会合并多个delta层获取到的所有文件，返回合并后的集合。

* 在delta目录下，模型文件可以通过根节点上的`x:extends="super"`配置来表示继承下一个delta层的文件，这样不用写全路径，便于多个delta层混合使用。

## 模块系统

虚拟文件系统将前两层目录识别为模块ID,例如/\_vfs/nop/auth对应于模块
nop/auth。一般情况下我们定义Excel模型文件时也采用模块名为基础，例如nop-auth.orm.xlsx。

但是也不是所有的两层目录都被识别成模块。需要在目录下增加\_module文件，这是一个空文件，仅仅用于占位。例如/_vfs/nop/auth/_
module。

* ModuleManager负责查找所有模块。

## Data名字空间
data名字空间下的路径根据`nop.core.resource.store.data-root-dir`配置路径来解析，根路径缺省为`/data`。
比如说`data:/a/b.txt`实际对应的文件路径为`./data/a/b.txt`。

## 相关配置项

|名称|缺省值|说明|
|---|---|---|
|nop.core.vfs.delta-layer-ids|default|指定差量文件系统的Delta层,例如\_platform,product,app。对应/\_delta/product等虚拟路径|
||||
||||
