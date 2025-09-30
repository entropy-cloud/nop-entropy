# Model File Dependency Tracking

1. Each time a model object is retrieved from `ResourceLoadingCache`, if the cache has expired or has not yet been populated, the model loading interface `IResourceObjectLoader` will be invoked within the `collectDependsTo` context.
2. DependencyManager establishes a `DependencyStack` in a ThreadLocal to track dependencies among model files.
3. Each model file corresponds to a `ResourceDependencySet`. During parsing, a new `DependencySet` is created and pushed onto the top of the `DependencyStack`. The `DependencySet` has a monotonically increasing version.
4. If there is already a DependencySet on the top of the current DependencyStack, a dependency relationship between model files is established and registered into the existing DependencySet's depends collection.
5. The global dependencyMap records the latest `DependencySet` for each model file, which is used to determine whether the cache has expired.

## Determining Cache Invalidation
1. The `ResourceCacheEntry` records the `DependencySet` corresponding to the model.
2. Recursively check whether the file timestamps in the `DependencySet` are inconsistent with the current model file's timestamp. If they differ, an update is required.
<!-- SOURCE_MD5:f59730d6e33876e221a81f6f820b5207-->
