# 1. About Delta Pipeline Issues

![Delta Pipeline](../theory/nop/delta-pipeline.png)

**Question:** Why does each layer use information from other layers? Is this related to delta parts?

**Answer:** Each layer only receives derived information from the next lower layer. Each layer has its own unique requirements and abstractions. This ensures that each layer is both independent and interconnected. Independence is reflected in the final control authority residing within each layer, without losing any capability. A layer can completely ignore information derived from higher layers. Each layer may contain information from other layers for two reasons:  
1. Implicit relationships exist between different layers, such as a data layer handling integer types while a UI layer might have input controls that correspond to these integers.  
2. For convenience in configuration, certain UI-related information (e.g., `ui:show`) can temporarily reside in the data layer even if it's not directly used there.

**Question:** Is there documentation or customization options for storing `ui:show` properties? Or is it predefined so that users must define which properties can be placed in higher layers?

**Answer:** Users can customize which properties are allowed in higher layers. The Nop platform allows for flexible configuration of which DSL nodes can store extended properties. For example, `ui:show` is an extended property stored in the ORM model's metadata during generation. This allows it to propagate correctly from the XMeta model down to the UI layer.

**Question:** Is the flow directional (one-way) or bidirectional? From a theoretical standpoint, does data flow from the DataModel to the UiModel through a one-way pipeline?

**Answer:** In theory, the pipeline is unidirectional. Data flows from the DataModel to the UiModel in a specific sequence during generation. However, certain properties like `Delta information` can propagate bi-directionally depending on how they're used.

**Question:** Is the flow directional or bidirectional? From an interaction perspective, does data flow back up to the database?

**Answer:** The pipeline is directional based on the layer's role in the build process. For example, if a change is made in the DataModel during runtime, it might not affect the UIModel. However, certain interactions (like form submissions) can still require data storage.

**Answer:** Runtime behavior depends on whether changes are tracked as deltas or stored directly in the database. If deltas are used, modifications to lower layers may not propagate to higher ones unless explicitly handled.
