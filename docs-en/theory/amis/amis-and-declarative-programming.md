# Further Discussion on Baidu AMIS Framework and Declarative Programming

In my previous article [Why the Baidu AMIS Framework is an Excellent Design](https://zhuanlan.zhihu.com/p/599773955), someone raised a question: Is it necessary to use the [Api对象](https://aisuda.bce.baidu.com/amis/zh-CN/docs/types/api) defined in AMIS? Can we achieve the same results using traditional event listeners without introducing an additional concept?

To address this question, let's look at a concrete example:

```json
{
    "type": "form",
    "body": [
        {
            "type": "select",
            "name": "a",
            "options": [ ... ]
        },
        {
            "type": "select",
            "name": "b",
            "source": {
                "method": "post",
                "url": "/get-related-options",
                "data": {
                    "value": "${a}"
                }
            }
        }
    ]
}
```

In the above example, the first select control has a `name` attribute set to `a`, indicating that its selected value corresponds to the variable `a` in the context. The select control can be seen as both an observer and a modifier of this variable. The second select control's `source` property corresponds to an [Api类型的对象](https://aisuda.bce.baidu.com/amis/zh-CN/docs/types/api), which listens for changes to the variable `a` through data binding. When `a` changes, it automatically performs an AJAX call to retrieve new options.

In this example, both event binding and event handling for the select control are implicit. We do not see explicit triggers for events, nor do we directly intervene in the event handling process. Does this mean we lose fine-grained control over the program logic?

Actually, looking at the evolution of frontend frameworks will help us understand that this is not something to worry about. We have been moving away from granular low-level control with each framework's evolution. During jQuery's popularity era, we frequently manipulated the DOM object directly, calling various methods to access and modify child nodes, attributes, etc. For instance, to implement CRUD operations on a list, we would listen for the `click` event of corresponding buttons and update the DOM accordingly. However, in modern frameworks like Vue and React, **the DOM object's presence has been reduced to an extremely low level**. The virtual DOM is no longer an independently valuable entity but merely a representation of our template (JSX) or similar structures. Based on this virtualization concept, we only need to write a `render` function for the current state data. When the state data changes (add, modify, delete), the framework will **automatically derive** and update the UI logic.

By abandoning explicit DOM manipulation and fine-grained control over DOM updates, we gain a more compact way of expressing business logic and can even migrate this logic to non-browser environments using React Native technology.

> Many data-driven frameworks are now enhancing their **observability**, allowing us to monitor data changes through `watch`, `subscribe` mechanisms, and even supporting history tracking and time travel. The key distinction here is that we are not merely watching irrelevant component events but directly observing business-relevant data changes or meaningful action events.

## Declarative vs. Imperative

The evolution of frontend frameworks can be viewed as a continuous shift from **imperative programming** to **declarative programming**.

In imperative programming, we explicitly define each step in the execution process. Each line of code is an instruction telling the system what to do next. For example:

```javascript
function multiplyNumbers(a, b) {
    return a * b;
}
```

Here, the function explicitly defines how to multiply two numbers by performing the multiplication operation.

In declarative programming, we focus on what needs to be done rather than how to do it. The framework takes responsibility for managing the details of the implementation. For example:

```vue
<script>
  data() {
    return { a: 1 };
  }
</script>

<template>
  <div>{{ a }}</div>
</template>
```

Here, we declare that we want `a` to be displayed, and the framework automatically handles fetching, updating, and rendering based on this declaration.

Because declarative programming only concerns itself with the end points (what needs to be achieved), it naturally introduces opportunities for optimization. For instance, in frameworks like Vue and React, the virtual DOM is used to optimize rendering performance by deriving only the necessary changes from one render cycle to the next. This approach ensures that we do not perform unnecessary DOM manipulations, which can be especially beneficial for large applications.

In contrast, imperative programming often requires us to manually access and modify the DOM object, which can lead to inefficient layouts in browsers due to frequent re-renders and redundant calculations.

By adopting declarative programming, we trade away explicit control over certain aspects of our application's behavior but gain significant benefits in terms of code simplicity, performance, and scalability. This shift has been particularly impactful with the rise of React Native, allowing declarative patterns to extend beyond the browser environment.

```markdown

## reactive programming concepts: states and paths

Reactive programming focuses on the final state's impact, not the intermediate steps. The precise execution steps and order are often ignored, allowing for optimizations like caching and deferred processing.

For example, during event triggering, a state variable may be modified multiple times. However, only the final modification needs to be reflected in the UI. Previous modifications can be safely ignored.

A state is the endpoint of a path and represents the surface (surface) of the path. When examining the state space composed of business states, we often find that the number of relevant states is far fewer than the number of possible state transition paths (similar to dimensionality reduction).

For instance, if a data binding expression only uses the state variable `a`, then regardless of which state transition path leads to `a=1`, the related computations will yield identical results.



In physics, during initial learning, we study Newtonian mechanics using force concepts. We focus on precise paths and the forces acting at each point in the path. However, at a more advanced level, we use energy and action quantity concepts to find the minimal action paths that describe real-world processes.



In AMIS (a low-code frontend framework), we strive to leverage reactive data binding mechanisms for information transmission, minimizing reliance on imperative code as much as possible. This approach maximizes the proportion of declarative descriptions in the UI.

Careful analysis reveals that after fully adopting reactive data binding, the importance of component objects naturally diminishes (similar to DOM objects). At the implementation level, we can often omit specific component structures, even if the underlying system supports them.

For example, in the Nop platform, during AMIS JSON loading, we identify and execute compile-time XPL tags dynamically. These tags generate AMIS descriptions at runtime. This component can be seen as a pure function component existing only at compile time, not at runtime.



In AMIS, the [Api object](https://aisuda.bce.baidu.com/amis/zh-CN/docs/types/api) represents the natural evolution of the reactive data binding mechanism.

In Vue 3.0, property values for controls can be of three types:
1. Fixed values
2. Variable values
3. Ref references (Reactive in concept; Ref in implementation)

Ref references act as a data pipeline: construction (input and holding Ref) and usage (passing new values through Ref to external observers). To maximize the value of this pipeline, we should enrich its source and support further processing of the pipeline's data.

The [computed](https://vue3js.cn/reactivity/computed.html) is a special Ref that no longer represents a simple, mutable value. Instead, it encapsulates a function whose return value is tracked for reactivity. From a data perspective, the function has become a reactive value with automatic updates.

All places where data is used can be supplied with a computed value, ensuring that all inputs, processing, and outputs are business-related state data, not component-specific objects or properties.

AMIS JSON descriptions are input to the lower-level engine as metadata. They do not require corresponding components at runtime (unlike traditional UI components). We can decompose complex components into atomic components at compile time, requiring only basic support for specific atomic components at runtime.

For example, in the Nop platform, AMIS JSON is loaded and processed, identifying compile-time XPL tags to generate dynamic descriptions. This component behaves like a pure function component, existing only during compilation, not at runtime.

The use of Api objects allows us to express business logic without involving Component concepts. All inputs, processing, and outputs are related to state data, not UI components or their properties.

AMIS JSON descriptions do not require corresponding components at runtime. We can decompose complex components into atomic components at compile time, requiring only basic support for specific atomic components at runtime.

For instance, in the Nop platform, during AMIS JSON loading, we identify and execute compile-time XPL tags dynamically. These tags generate AMIS descriptions at runtime. This component can be seen as a pure function component existing only at compile time, not at runtime.



The [Api object](https://aisuda.bce.baidu.com/amis/zh-CN/docs/types/api) in AMIS represents the natural evolution of the reactive data binding mechanism.

In Vue 3.0, property values for controls can be of three types:
1. Fixed values
2. Variable values
3. Ref references (Reactive in concept; Ref in implementation)

Ref references act as a data pipeline: construction (input and holding Ref) and usage (passing new values through Ref to external observers). To maximize the value of this pipeline, we should enrich its source and support further processing of the pipeline's data.

The [computed](https://vue3js.cn/reactivity/computed.html) is a special Ref that no longer represents a simple, mutable value. Instead, it encapsulates a function whose return value is tracked for reactivity. From a data perspective, the function has become a reactive value with automatic updates.

All places where data is used can be supplied with a computed value, ensuring that all inputs, processing, and outputs are business-related state data, not component-specific objects or properties.
```


The computed property is an encapsulation of a synchronous function's reference. The AMIS API object can be considered an encapsulation of a remote async function's reference.

On this basis, we can further encapsulate stream data, which is the Service Container in AMIS.


## Service Container

- The Service Container can encapsulate a WebSocket connection, automatically updating the current value whenever backend data is received.
- It can also periodically poll the API, updating the current value upon receiving data.

> From a personal perspective, if we start from scratch, the concepts of Service and API in AMIS can be unified, and the functionality of loading Schema can be separated from the Service Container.



For a declarative programming perspective, the AMIS framework's introduction of the API object is entirely reasonable and natural.

