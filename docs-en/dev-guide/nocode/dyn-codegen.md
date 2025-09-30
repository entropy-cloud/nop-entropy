# Dynamic Model Code Generation

## Dynamic Models

The nop-dyn module provides online model definitions. In the DynCodeGen class, during initialization it automatically reads the dyn configuration and generates meta and biz definitions in memory.

If you want to skip model generation at startup, set `nop.dyn.gen-code-when-init` to false.

If dynamic models also support multi-tenancy, the same entity name may correspond to different database structures.

## Dynamic Updates

Dynamic models allow modifications and updates at runtime; after a model is updated, all information automatically derived from the model must be updated accordingly. Therefore, it is necessary to precisely define the scope affected by model changes.

Model loading uses a reactive dynamic-generation loader: when loading by name, the model file is generated on the fly and then parsed; the parsed result is cached in memory.

### Tenants and Modules

* Each tenant has its own independently managed model cache, implemented by TenantAwareResourceLoadingCache.
* Each tenant corresponds to a Delta file store, which is further partitioned by module prefixes within this ResourceStore; files for each module can be loaded and updated independently.

### Object Partitioning

* The object structure is a business-meaningful decomposition; various models related to a single object often reference each other closely. The object can be used as the smallest update granularity.
* meta, view, and page, etc., can be cached and managed per single entity; each modification only needs to update the files related to that individual object.
* ORM models allow entity references; however, in consideration of dynamic updates, cross-module references are, in principle, avoided.

## Publishing

* Clicking the 【Publish】 button invokes DynCodeGen to generate model files in memory. Models and model files can be exported and managed and used as regular modules.
* Before a module is published, you can design and test the model; publishing merely makes it externally visible. Distinguish statuses such as 【Not Deployed】, 【Deployed】, and 【Published】.
<!-- SOURCE_MD5:38f8bcd06eaf2d43d96a767e335e9525-->
