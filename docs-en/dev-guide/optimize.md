# Optimization Configuration of the Nop Platform


## Configuration Variables
In development mode, many dynamic checks are performed. At startup, all models are automatically validated. Disabling this feature can accelerate startup speed and reduce the time it takes to retrieve data during runtime.

* nop.core.component.resource-cache.check-changed: Configure as false to disable dynamic modification checks.
  Models will not automatically become invalid unless explicitly deleted.
* nop.web.validate-page-model: Set to false to prevent validation of all `page.yaml` files at startup. These files will be loaded without validation.
