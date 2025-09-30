
# 1. Questions About the Delta-based Inference Pipeline

![](../theory/nop/delta-pipeline.png)

Question: **Why does each layer design information for other layers to use? Is it the Delta part?**

Answer: The information each layer infers for the next layer is merely optional; every layer has its own unique concerns and abstractions, and this information is not required to all exist in the previous layer. This approach ensures that each layer is both independent and interrelated. Independence means final control resides in the current layer, without losing any capability. You can start from the current layer and completely ignore information inferred by the previous layer. There are two reasons for embedding other-layer information in each layer: 1. There are inherent relationships between layers. For example, if a type in the data layer is an integer, the outer UI layer should display an integer input control; this information should be preserved across layers. 2. To simplify unified configuration, some UI-layer information, although not used by the data layer, can be temporarily stored in the data layer. For example, in orm.xml, a field can configure `ui:show` to control on which CRUD pages the field is displayed. `ui:show` is an extension attribute and not part of the ORM model, but it is stored in the ORM model as an extension attribute; during the inference process it is first propagated to the XMeta model as an extension attribute, and only takes effect when inferred to the XView layer. The `ui:show` attribute can be configured in the Excel model.

Question: Is there documentation for the storage rules of extension attributes like `ui:show`, or is it customizable, meaning users must additionally specify which attributes can be stored at higher layers?

Answer: Users can freely customize and additionally choose which attributes can be placed at higher layers, but how they propagate downward is controlled by their own code generator. All nodes of all DSLs in the Nop platform, by default, allow storing extension attributes. Entity objects in NopORM likewise allow storing arbitrary extension attributes. The Nop platform always adopts a (data, metadata) paired design, ensuring that at the finest granularity, near the information itself, there is always a place to store Delta information.

Question: The diagram is unidirectional. Is it possible for it to be bidirectional? In theory, some data from front-end interaction experience, such as the configuration of WeChat’s Discover page or keyboard shortcuts, may also need to be saved to the database.

Answer: We need to distinguish between compile-time and runtime. Essentially, from DataModel to UiModel, this is a compile-time information inference pipeline. At runtime, the runtime information transmission pipeline established after inference is naturally bidirectional. From a broader perspective, compared with the unidirectional data flow (data-driven) in the frontend Redux framework, you can regard this inference pipeline as a compile-time unidirectional model-driven approach. If the UI layer introduces new requirements, it may prompt changes starting from the foundational data model and then propagate to the UI model. If it does not involve storing data, it may also prompt changes in higher-layer models.

<!-- SOURCE_MD5:97ff6225684ea9d059a4ce0f0b3a0ea2-->
