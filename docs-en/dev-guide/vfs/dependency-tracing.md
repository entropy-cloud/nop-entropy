# Model File Dependency Tracking

1. When retrieving a model object from `ResourceLoadingCache`, if the cache is considered invalid or not yet cached, the `collectDependsTo` environment will call the model loading interface `IResourceObjectLoader`.
2. The `DependencyManager` will use a `ThreadLocal` to maintain a `DependencyStack`, which tracks dependency relationships between model files.
3. Each model file corresponds to a `ResourceDependencySet`. When parsing a file, a new `DependencySet` is created and placed at the top of the `DependencyStack`. This `DependencySet` contains a version field that increments with each change.
4. If a `DependencySet` already exists at the top of the `DependencyStack`, establish dependency relationships between model files and add them to the existing `DependencySet`'s `depends` collection.
5. The global `dependencyMap` keeps track of the latest `DependencySet` for each model file, which is used to determine if the cache is considered invalid.

## Cache Invalidation

1. A `ResourceCacheEntry` will record the corresponding `DependencySet` for a model.
2. Recursively check if the file timestamps in the `DependencySet` match those of the current model file. If they do not match, it means the cache is considered invalid and needs to be updated.