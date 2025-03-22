# Dynamic Model Code Generation


## Dynamic Model

The `nop-dyn` module provides online model definition. In the `DynCodeGen` class, during initialization, it automatically reads `dyn` configurations and generates `meta` and `biz` definitions in memory.

If you want to skip model generation when initializing, you can configure `nop.dyn.gen-code-when-init` to `false`.

If the system supports multi-tenancy, the same entity name may correspond to different database structures.


## Dynamic Update

Dynamic models allow dynamic modification and updates. After a model is updated, all information derived from the model should also be updated accordingly. Therefore, it's essential to precisely define the scope of model changes.

The model loading uses a responsive dynamic loading generator: when loaded by name, it dynamically generates and parses the model file and caches the result in memory.


### Tenants and Modules

- Each tenant has its own independent model cache managed by `TenantAwareResourceLoadingCache`.
- Each tenant corresponds to a Delta file stored in `ResourceStore`, which is then split by module prefix. Each module's file can be loaded and updated individually.


### Object Splitting

- The object structure is a clear decomposition of business semantics.
- Related models often have tight coupling, so the smallest granularity for updates should be at the object level.
- `meta`, `view`, and `page` are all cacheable for individual entities. Each modification to a single entity's file can be updated accordingly.
- The ORM allows entity referencing, but due to dynamic updates, cross-module references are generally not allowed.


## Publication

- Clicking the "Publish" button invokes `DynCodeGen` in memory to generate the model file.
- The model and its associated files can be exported for module management and usage.
- Before a module is published, you can design and test the model. Publishing only makes it visible externally.
- Distinguish between states: "未部署", "已部署", and "已发布".

