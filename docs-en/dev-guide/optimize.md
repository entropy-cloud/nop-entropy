# Nop Platform Optimization Configuration

## Configuration Variables
In development mode, many dynamic checks are performed, and all models are automatically validated at startup. Turning off these switches can speed up startup and improve the speed of cache retrieval at runtime.

* nop.core.component.resource-cache.check-changed: Set to false to disable dynamic modification checks.
  Once a model is loaded into the cache, it will not automatically become invalid unless explicitly removed.
* nop.web.validate-page-model: Set to false to skip validating all page.yaml files at startup.

## Performance Metrics

Use functions such as `/DevStat__jdbcStats` and `/DevStat__rpcServerStats` to view backend invocation timing statistics.

## Dev Mode
When Quarkus is started in an IDE, Dev mode is automatically enabled. Dev mode enables Live Reload, automatically checks whether class files and other resources have been modified, and will restart the container upon detecting changes; this impacts runtime performance.
You can set `quarkus.live-reload.enabled` to false to disable this feature.
<!-- SOURCE_MD5:fae3d04b096196257434102c7b66a72b-->
