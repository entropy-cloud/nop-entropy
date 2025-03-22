# Frontend Architecture Design

The overall design concept is to establish a micro-nuclear architecture on the frontend, providing a set of public plugin registration mechanisms that can support various low-code engines such as AMIS and OpenTiny.

## 1. Overall Structure

1. The `packages` directory contains basic SDK implementations and establishes the fundamental micro-nuclear architecture.
2. Plugins are integrated through a plugin mechanism, with AMIS being introduced as a plugin.
3. Shells provide the overall program framework, including frontend menus, frame pages, and authentication, etc.
4. Applications (apps) integrate shells, plugins, and packages to build comprehensive applications. Since the entire system has adopted micro-nuclear technology, apps primarily serve as glue modules with relatively simple functions. Nop-site provides an example of such a glued application.

## 2. Core Module Functions

1. The `nop-core` module defines basic Adapter interfaces and Registry interfaces. These are independent of Vue and React frameworks.
2. Modules like `nop-vue-core`, `nop-react-core`, and `nop-vue-react` provide integration support for Vue and React, along with helper functions for mixed use cases.
3. `nop-graph-designer` provides an abstract graphical designer using React for implementation, with specific flowchart and property-editing functionalities introduced as plugins.

## Plugin Design and Dynamic Loading

```
Plugin = Module + PluginFunctions
```

Depending on a page's required modules, those modules can be pre-loaded.

1. A general module management mechanism is established to manage plugin mechanisms. Each plugin corresponds to an entry point in the module.
2. Currently, SystemJs is used for module loading.
3. Dynamic updates can be handled using HMR (Hot Module Replacement), although this is not yet necessary for the simple frontend of the Nop platform. HMR may be added later.
4. The basic unit of dynamic loading is the `Module`.

## Store

```
Store = State + Methods + Scope
```

Based on Zustand, a unified architecture support is added to the store.

The role of Store replaces event handling. Native Event logic belongs to the lower-level interaction abstraction layer. In the abstract business layer, UI-specific concepts like Events are not used; instead, only business-related state and processing functions are handled by the Store.

1. The Store itself is a Module.
2. The `createStore` function in the Module is used to create a Store instance.
3. Introducing a StoreModule node automatically creates a Store and stores it in the Scope context, similar to variable scoping. On the framework level, the `useContext` mechanism is utilized to provide the Store.
4. Store propagation no longer requires passing props through multiple levels; it can be implicitly transmitted as implicit context.
5. The constructor of the Store primarily accepts inherited parts from the parent Store and initial data sets.
6. The Store uses an immutable data set, making its information more predictable in terms of derivation. It should be possible to enhance Zustand with Vue 3 reactivity.

## Schema

```
component = compile(schema);
```

The schema is structured in JSON format for editing purposes. During runtime, the `compile` function converts the schema into a virtual DOM.

## Render

```
render(name, schema, options, {props, store});
```

The render function generates the virtual DOM tree based on the component name and schema.

1. The `type` property is mapped to the component definition using the `resolve` function.
2. For asynchronous loading, an `AsyncWrapperComponent` can be returned if needed.

## Framework Support

1. `xui:schema-type`
2. `xui:import`
3. `xui:store-lib`, `xui:store-init-data`, `xui:store-inherit`
4. `xui:component-lib`

## Dynamic Loading of Controls

Controls provide atomic semantics. Control libraries can be dynamically loaded and locally applied.

1. The `componentLib` parameter specifies the control library path, which is loaded from the backend using SystemJS format.
2. Timestamps are used to prevent duplicate loading. The URL is automatically updated with a timestamp to avoid cache issues.
3. Loaded controls are directly placed into the Context object for use by components.


### Dynamic Loading of State Libraries

Based on `storeLib`, load state libraries. Create a `store` using the `createStore` function and register it to the `Context`.

### Schema Organization

Schemas are organized within the control library, using JSON format for expression.

Nodes can serve as lifecycle controls, used for creating components.

Libraries are loaded based on page granularity. After loading the schema, scan all libraries dynamically before loading them. Once completed, create a `Page` object based on the schema.

### Schema Rendering

When rendering schemas, additional intermediate nodes may be inserted to adapt to low-code frameworks and Nop contexts.

`xui:import` functions do not require initialization, but `store` does, requiring additional handling.

`useStore(path)` and `useContext(StoreType)` are essentially the same in nature.

---

### Code Snippets

```javascript
Editor(data)
```

```javascript
Editor = GenericEditor<Components, Store>
```

```javascript
Components = Loader(componentLib)
Store = Loader(storeLib)
```

```javascript
storeLib = Generator<EditorModel>
componentLib = Generator<EditorModel>
```

```javascript
eventHandler = store.method
data = useStore(state => selector(state))
```

---
