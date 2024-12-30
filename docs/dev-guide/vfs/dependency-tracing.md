# 模型文件依赖追踪

1. 每次从`ResourceLoadingCache`获取模型对象时，如果缓存已失效或者尚未缓存，就会在`collectDependsTo`环境中调用模型加载接口`IResourceObjectLoader`。
2. DependencyManager会在ThreadLocal中建立`DependencyStack`，用于跟踪模型文件之间的依赖关系。
3. 每个模型文件都对应一个`ResourceDependencySet`，解析文件的时候会新建一个`DependencySet`放到`DependencyStack`顶部，DependencySet具有一个单调递增的version。
4. 如果当前DependencyStack顶部已经存在DependencySet，则建立模型文件之间的依赖关系，注册到已存在DependencySet的depends集合中。
5. 全局dependencyMap中记录每个模型文件最新的DependencySet，用于判断缓存是否失效。

## 判断缓存失效
1. ResourceCacheEntry中会记录模型对应的DependencySet
2. 递归检查DependencySet中的文件时间戳是否与当前模型文件的时间戳不一致，如果不一致，则说明需要更新。

