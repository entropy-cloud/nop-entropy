# Application Optimization

## Simplify Debugging Process

* Each module has an auto-generate app module, such as `nop-wf-app`, which can be directly started for individual module debugging. No need to integrate all modules together for debugging.
* During general functionality development, prefer using unit tests for verification instead of starting the entire application each time.

## Optimize Application Startup Time

* The configuration parameter `nop.web.validate-page-model` can be left unset or set to `false` to skip page validation during startup. This skips checks on `view.xml` and `page.yaml` files.
* Setting `nop.web.page-validation-thread-count` to a value greater than 1, combined with setting `nop.web.validate-page-model` to `true`, enables multi-threaded validation during startup.
* The parameter `nop.orm.db-differ.auto-upgrade-database` can be left unset or set to `false` to skip database upgrade checks during startup.
* Setting `nop.orm.init-database-schema` to `false` (or leaving it unset) skips the automatic table creation operation during startup.
* Leaving `nop.auth.login.allow-create-default-user` unset or setting it to `false` means that the application will not check if at least one user exists in the database during startup.
* Setting `nop.web.auto-load-dynamic-file` to `false` (or leaving it unset) prevents automatic translation of XJS files to JS files during startup.
* Setting `nop.graphql.eager-init-biz-object` to `false` means that BizObject will not be immediately parsed and created upon startup, but only when a specific object is accessed.
* Setting `nop.ioc.app-beans-container.concurrent-start` to `true` initializes the bean container on a thread pool during startup, without blocking the main thread.

## Optimize Application Runtime Performance

* Setting `nop.debug` to `false` prevents output from being generated into the dump directory.
* Setting `nop.core.component.resource-cache.check-changed` to `false` means that resource files will not be checked for changes, and their cached results will not be updated automatically.
* Setting the log level to `info` reduces the amount of log output.

## View Metrics

Metrics are exposed through Prometheus in the Quarks framework via `/q/metrics`. In Spring Boot, metrics can be accessed via `/actuator/prometheus`.

For detailed statistics:
- In the Nop platform: Use the StatLink for granular statistics.
- SQL-related metrics: Check `/r/DevStat__jdbcSqlStats` for details on each SQL statement's execution time, count, and range.
- Server-related metrics: Use `/r/DevStat__rpcServerStats` to view metrics for each backend service function.
- Client-related metrics: Access `/r/DevStat__rpcClientStats` for client endpoint statistics.

1. `/r/DevStat__jdbcSqlStats`: Displays details about each SQL statement, including execution time, count, and range.
2. `/r/DevStat__rpcServerStats`: Provides metrics for each backend service function, such as execution time, count, and range.
3. `/r/DevStat__rpcClientStats`: Shows statistics on client endpoint calls, including execution time, count, and range.
