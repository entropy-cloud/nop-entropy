# Understanding React's Essence Through React Hooks

The post-jQuery front-end revolution was initiated by AngularJS. Its initial idea was to replicate back-end architectural techniques on the front end. A core back-end technique is the so-called template technology (template). It can be described by the formula

```
html = template(vars)
```

This is a particularly intuitive idea: a template is just a regular function that concatenates input variable information (with no special requirements) into a string (with no special structure). This model completely avoids the traditional object-oriented problem of scattered state management and is essentially a functional solution.

React’s pattern is essentially a domain-structured improvement on the template rendering model

```
vdom = render(viewModel)
```

vdom is a browser-oriented domain model, while viewModel is a page display model built on business-domain concepts. render serves as a portal between these two model worlds.

Traditionally, when we program, we have to know the UI model. The infrastructure we rely on is the browser’s built-in DOM structure and the event bubbling mechanism: we always attach listeners to DOM nodes, always obtain specific UI elements, and then pull the data we need from those elements. In React’s pattern, we first build a model in JS, which contains specific domain knowledge. Operations within the domain are more direct and can leverage the various abstraction mechanisms provided by the programming language. Typically, in the jQuery era we frequently used forms like $el.find(".title") to dynamically find the required element, whereas in a JS model we generally locate the data directly via the this.title property. In fact, our dependence on a loosely coupled event mechanism has been greatly reduced, especially since we generally no longer need to handle event bubbling with unclear business meaning. Redux and Vuex can be seen, in a sense, as domain-oriented message buses: they typically dispatch directly to specific listeners, and the listener entry points are no longer some generic, business-agnostic Event object, but concrete domain state objects (state) and business parameters (param).

In the new paradigm, constructing and managing the viewModel becomes an independent concern. UI components no longer interact directly; their associations are implicitly expressed through their shared dependence on JS objects.

```
component <--> js <--> component
```

If we rewrite the form a bit, React’s essence becomes clearer:

```
viewModel => vdom
```

The render function can be seen as an information pipeline that pulls domain data from the viewModel and transports it into the vdom world.

However, we know that the front end and back end differ fundamentally: the front end focuses on interactivity, whereas the back end emphasizes one-way execution. Therefore, we need a new concept, reactive, with which we can rewrite the formula as

```
(props, @reactive state) => vdom
```

The render function is a hard-won information pipeline; discarding it after a single use would be wasteful—why not reuse it? By introducing state variables with reactivity and stipulating a global reactive rule—“whenever state changes for any reason, automatically re-trigger the local render function to re-execute”—we successfully elevate the render function, perfectly embedding microscopic interactivity into a macroscopic information-flow scenario.

For many years, React meandered without finding a technical expression that best fits the above formula, essentially constrained by object-oriented thinking, always trying to carry the OO tail along. Only when the Hooks mechanism burst onto the scene and decisively broke with history did we see React as it was always meant to be:

```javascript
import React, { useState, useEffect } from 'react';

function FriendStatus(props) {
  const [isOnline, setIsOnline] = useState(null);

  useEffect(() => {
    function handleStatusChange(status) {
      setIsOnline(status.isOnline);
    }

    ChatAPI.subscribeToFriendStatus(props.friend.id, handleStatusChange);

    // Return a function to perform additional cleanup:
    return function cleanup() {
      ChatAPI.unsubscribeFromFriendStatus(props.friend.id, handleStatusChange);
    };
  });

  if (isOnline === null) {
    return 'Loading...';
  }
  return isOnline ? 'Online' : 'Offline';
}
```

Why do Hooks restrict calls to the top level, disallowing calls inside loops, conditionals, or nested functions? Because they were originally meant to be written in the parameter section; they simply lack a dedicated position due to syntax limitations.

Looking back, the development of modern framework technology can be seen as a history of rebellion against the traditional OO concept of encapsulation. OO emphasizes that objects come first, then properties and methods, and that you must obtain this before doing anything. Modern frameworks emphasize global rules and direct expression—why detour through a this pointer for everything? Compare React’s earlier class components:

```javascript
class FriendStatus extends React.Component {
  constructor(props) {
    super(props);
    this.state = { isOnline: null };
    this.handleStatusChange = this.handleStatusChange.bind(this);
  }

  componentDidMount() {
    ChatAPI.subscribeToFriendStatus(
      this.props.friend.id,
      this.handleStatusChange
    );
  }

  componentWillUnmount() {
    ChatAPI.unsubscribeFromFriendStatus(
      this.props.friend.id,
      this.handleStatusChange
    );
  }

  handleStatusChange(status) {
    this.setState({
      isOnline: status.isOnline
    });
  }

  render() {
    if (this.state.isOnline === null) {
      return 'Loading...';
    }
    return this.state.isOnline ? 'Online' : 'Offline';
  }
}
```

The true core function is render; the others are peripheral support functions. These functions interact indirectly through the this pointer. If we ponder this carefully, a question arises: why must so-called lifecycle functions belong to the component object—are they knowledge limited to a specific object? Isn’t the timing of their triggers a form of global knowledge? The useEffect function deeply understands this: it becomes a static function, hooking directly into the global execution engine and using function closures to pass information among multiple lifecycle callbacks—without having to concoct a this pointer to carry along.

For a long time, OO languages have had three standard ways to pass information: parameters (param), global variables (global), and member variables (this). However, when faced with complex domain models, we often need to express implicit background knowledge within a local scope—custom, domain-tied context variables—which should not be passed explicitly. Therefore, the core data-driven formula can be improved to

```javascript
   (props, @reactive state, @implicit context) => vdom
```

In React Hooks, the concept of implicit context also has a corresponding technical form: context is located via type-based lookup, which is akin to an implicit import mechanism.

```javascript
const user = useContext(CurrentUser);
const notifications = useContext(Notifications);
```

Does the emergence of React Hooks mean OO components will fade away? Not necessarily. Tradition is powerful, and vibrant cultures are inclusive. Even before the Hooks concept, the Vue ecosystem had already solved the corresponding problem through metaprogramming: code blocks declared together at compile time can be split and attached to the various slots of the component object via metaprogramming mechanisms. As a runtime, object orientation poses no problem at all.

Question: “As a runtime, object orientation poses no problem at all.” How should we understand this?
Answer: At the framework runtime level—for example, in Vue 3.0—on the surface the watch function no longer needs to be bound to an object, but a hidden currentInstance object actually exists, equivalent to a concealed this pointer. A great deal of information remains bound to and organized around the this pointer, which is essentially no different from general OO practice.

## Front-End Programming Model

From today’s perspective, the front-end programming model looks roughly like this:

![](react/state-view-action.png)

The render function generates the view object based on the data model. Interactions on the view object trigger actions; actions modify the data object, and the reactive driving mechanism then automatically triggers the rendering process from the data object to the view object. Under the hood, with the virtual DOM’s diff algorithm, the actual Delta of the view model to update is computed to minimize the scope of the real view updates and improve performance.

In practice, the programming model is more complex because we are dealing with a complex application rather than a single component, so we inevitably decompose it via divide and conquer:

![](react/state-view-action-tree.png)

Thus, a core organizational challenge in the front end is how to connect the three trees: StateTree, ViewTree, and ActionTree.

Traditional component programming is essentially centered on the Component Tree: all state data and operational behavior are attached to the Component object. The mainstream approach today is to manage state separately, with Actions and related state variables together forming the so-called Store. Theoretically, this organization is not the only option—ActionTree can also be managed independently—provided we have a natural mechanism to bind it to the StateTree. Ideally, we could adopt the following approach:

```
<input name="myField"/>
<span>{{state}} </span>
<button label="submit" v-on:click="submitForm" />
```

1. Display and input controls have a name attribute, used to pull variables from the current node of the StateTree, and if modified can automatically update back to the StateTree.

2. Expressions can pull variable information from the currently visible StateTree node. The visibility control here is similar to lexical scope in general programming languages, essentially a closure-based variable access mechanism constructed by the front-end framework for the application layer. (useXXX-style solutions are essentially an application-layer custom-built closure mechanism.)

3. User interactions bind directly to specific action functions. Locating action functions can mirror the data variable lookup mechanism, searching upward from the current Action Scope until a handler with the specified name is found.

<!-- SOURCE_MD5:fe084df23412e651cd5284ce0df124f2-->
