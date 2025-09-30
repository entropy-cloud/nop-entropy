# Application Optimization

## Simplify the Debugging Process

* Each module has an auto-generated app module, such as nop-wf-app. You can launch this module directly to debug a single module. There’s no need to integrate all modules for debugging.
* During feature development, prefer verifying functionality via unit tests to avoid having to start the entire application every time.

## Optimize Application Startup Time

* If `nop.web.validate-page-model` is unset or set to false, page validation at startup is skipped, bypassing parsing checks for view.xml and page.yaml files.
* If `nop.web.page-validation-thread-count` is set to a value greater than 1 and `nop.web.validate-page-model` is true, validation will run with multiple threads.
* If `nop.orm.db-differ.auto-upgrade-database` is unset or set to false, database upgrade checks at startup are skipped.
* If `nop.orm.init-database-schema` is unset or set to false, automatic table creation at startup is skipped.
* If `nop.auth.login.allow-create-default-user` is unset or set to false, startup will not check whether at least one user exists in the database.
* If `nop.web.auto-load-dynamic-file` is unset or set to false, xjs will not be automatically translated to js files at startup.
* If `nop.graphql.eager-init-biz-object` is set to false, meta files will not be parsed immediately to create BizObjects at startup; they will be created lazily when the object is accessed.
* If `nop.ioc.app-beans-container.concurrent-start` is set to true, the bean container initialization runs on a thread pool during startup, without blocking the main thread.

## Optimize Application Runtime Performance

* If `nop.debug` is set to false, no output will be produced under the dump directory.
* If `nop.core.component.resource-cache.check-changed` is set to false, resource files will not be checked for changes and cached parsing results will not be auto-updated.
* Set the log level to info to reduce log output.

## Enable HTTP/2
Quarkus server-side configuration `quarkus.http.http2=true`
Spring server-side configuration `server.http2.enabled=true`
HttpClient client-side configuration `nop.http.client.http2=true`


## View Statistics

Metrics are exposed via Prometheus. Under the quarks framework, use `/q/metrics`
to view statistics. Under the springboot framework, use `/actuator/prometheus` to view statistics.

Use stat links to view fine-grained internal statistics of the Nop platform

1. `/r/DevStat__jdbcSqlStats` View the execution time, execution count, and time-range distribution for each SQL statement
2. `/r/DevStat__rpcServerStats` View the execution time, execution count, and time-range distribution for each backend service function
3. `/r/DevStat__rpcClientStats` View the execution time, execution count, and time-range distribution for each RPC client call
4. `/r/DevStat__resetStats` Reset all statistics

<!-- SOURCE_MD5:e7ed7d6f9ea28e0ed0feb04589e3d10e-->
