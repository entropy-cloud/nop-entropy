# Frontend Architecture Design

The overall design philosophy is to build a micro-kernel architecture on the frontend and provide a common plugin registration mechanism that can support multiple frontend low-code engines such as amis and OpenTiny.

## I. Overall Structure

1. The packages directory provides the basic SDK implementation and establishes the foundational micro-kernel architecture.

2. plugins integrates various low-code technologies via the plugin mechanism, with amis introduced as a plugin.

3. shells provides the overall application framework, including frontend menus, layout pages, and security/authentication.

4. apps integrate the capabilities of shells, plugins, and packages to assemble a complete application. Since the system already adopts a micro-kernel architecture, apps mainly play a glue role and are not complex. nop-site provides an example of such a glue application.

## II. Core Module Capabilities

1. nop-core defines the basic Adapter and Registry interfaces, and its implementation is framework-agnostic with respect to Vue and React.

2. nop-vue-core, nop-react-core, and nop-vue-react provide integration support for Vue and React, along with helper functions for mixed invocation between Vue and React.

3. nop-graph-designer provides an abstract graph designer implemented with React, while concrete flowchart and property editing are introduced as plugins.

## Plugin Design and Dynamic Loading

```
Plugin = Module + PluginFunctions
```

Modules that a single page depends on can be preloaded.

1. Build a plugin management mechanism on top of the general module management system. Each plugin corresponds to a module entry point. Currently, SystemJS is used to load modules.
2. Dynamic module updates can use HMR (Hot Module Replacement). However, given the current simplicity of the Nop platform’s frontend, full refreshes are generally sufficient, so HMR is not needed for now.
3. The basic unit of dynamic loading is a Module. A module can contain multiple components.

## Store

```
Store = State + Methods + Scope
```

Add unified architectural support on top of Zustand.

The Store is intended to replace event handling. Logic that truly requires native Events belongs to the underlying interaction abstraction layer. In the abstracted business layer, UI-specific Event concepts will not be used; only business-related state data and handlers—i.e., the Store object—will be used.

1. A Store is also a Module. The Module includes a createStore function to create the Store.
2. A node that imports a StoreModule will automatically create a Store and place it into the Scope context object, implementing a variable lookup chain similar to lexical scoping. At the framework level, the Store Scope is provided via useContext. Passing the store no longer requires prop drilling and can be delivered as an implicit context.
3. The constructor primarily accepts parts inherited from the parent Store and the initial dataset.
4. Using immutable datasets for the Store simplifies implementation and improves predictability of data derivation. It should be possible to add a Vue 3 reactivity adapter for Zustand.
5. Child nodes can access the Store provided by their parent and directly invoke functions on the store. In general, sibling-to-sibling communication should not be necessary; any shared information can be lifted to the parent.
6. Attribute-binding expression syntax can be used during configuration.

## Schema

```
 component = compile(schema);
```

A JSON-form schema is an edit-oriented representation. Before runtime, it can be compiled into a component via the compile function.

RenderContext provides low-code runtime support and includes two functions:

1. render: directly transforms the schema into a virtual DOM.
2. invokeApi: executes an Api object. Api is not limited to remote calls; various frontend-triggered functions can be represented in the Api configuration format.

## Render

```
render(name, schema, options, {props, store})
```

The render function generates the virtual DOM tree.

1. The type attribute is mapped to a component definition via the resolve function.
2. resolve always returns a Component definition; for asynchronous loading, it can return an AsyncWrapperComponent.

## Framework Support

1. xui:schema-type
2. xui:import
3. xui:store-lib, xui:store-init-data, xui:store-inherit
4. xui:component-lib

## Dynamic Loading

### Dynamic Loading of Component Libraries

Components provide atomic semantics. Component libraries can be dynamically loaded and applied locally.

1. The componentLib parameter is the path to the component library, which is loaded from the backend in SystemJS format.
2. Use a timestamp to prevent duplicate loading. The backend automatically appends a timestamp to the URL in responses.
3. The loaded component set is placed directly into the Context.

### Dynamic Loading of State Libraries

Load the state library specified by storeLib, create the store using its createStore function, and register it into the Context.

### Schema Organization

The Schema is organized on top of the component library and expressed in JSON format.

Nodes can serve as the basis for lifecycle control to manage component creation.

Libraries are loaded at page granularity. After loading the schema, scan all libs and dynamically load them. Once loading is complete, create the Page object based on the schema.

### Schema Rendering

During actual rendering of the Schema, extra intermediary nodes may need to be inserted to adapt to the low-code framework and the Nop context.

Functions imported via xui:import do not need initialization; the store, however, must be constructed and requires extra handling.

useStore(path) and useContext(StoreType) are essentially the same thing.

```
Editor(data)

Editor = GenericEditor<Components,Store>

Components = Loader(componentLib)
Store = Loader(storeLib)

storeLib = Generator<EditorModel>
componentLib = Generator<EditorModel>

eventHandler = store.method

data = useStore(state=> selector(state))

```

<!-- SOURCE_MD5:e1147975e9bd4f931870a911cf84b4e7-->
