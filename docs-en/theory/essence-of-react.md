# Understanding React's Essence Through React Hooks

In the post-JQuery era, the front-end revolution was initiated by AngularJs, with its initial idea being to replicate backend architectural patterns in the frontend. The backend's core technology was called template technology (template). This concept can be described succinctly with a simple formula:

```
html = template(vars)
```

The intuition behind this idea is clear: templates are treated as ordinary functions that take input variables and generate output strings without any particular structure. This model eliminates the need to handle traditional object-oriented state management, essentially offering a functional approach.

React's approach can be seen as an improvement over the template rendering model:

```
vdom = render(viewModel)
```

Here, `vdom` refers to a domain model focused on the browser, while `viewModel` is a display model based on business domain concepts. The `render` function acts as a bridge between these two models, serving as a gateway connecting the domain and the display.

Traditionally, programming involved understanding interface models. The infrastructure relied on the browser's built-in DOM structure and the bubbling event mechanism, which continuously listened to DOM nodes, specifically targeting individual controls, and then extracted the necessary data. In React's approach, we first create models in JavaScript, which encapsulate domain knowledge and allow for more direct operations within the domain. This reduces the reliance on jQuery-style frequent DOM queries, such as `$.find(".title")`, and instead uses properties like `this.title` for direct access.

The dependency on a weakly coupled event handling mechanism has significantly decreased, with unclear business logic in bubbling events becoming a rare occurrence. Redux and Vuex can be seen as domain-specific message buses that dispatch messages directly to listeners without vague business context.

In the new paradigm, constructing and managing `viewModel` becomes an independent issue. UI components no longer interact directly; their relationship is mediated through shared JavaScript objects (JS objects), leading to implicit expressions:

```
component <--> js <--> component
```

By restructuring the view with this approach, React's essence becomes clearer:

```
viewModel => vdom
```

Here, `render` functions act as information pipelines, taking `viewModel` data and sending it into the VDOM world. However, we must remember that front-end and back-end have inherent differences: front-end focuses on interactions, while back-end emphasizes unidirectional execution. This leads us to the concept of reactivity (reactive), which allows us to express the above formula as:

```
(props, @reactive state) => vdom
```

The `render` function is a rare gem in this setup, as it represents an information pipeline connecting `viewModel` to VDOM. While its use might seem wasteful if employed once, its value lies in its ability to be reused and extended, enabling responsive behavior through reactive state variables: "Whenever the state changes for any reason, trigger the render function to update the VDOM."

This approach perfectly integrates microscopic interactions into a broader information flow, making React's true nature more apparent. Until the arrival of React Hooks, we were confined by object-oriented thinking, often trailing behind with unnecessary tail parts. React Hooks not only simplified but also liberated us from these constraints.

React has evolved over many years without finding a perfect fit for its core concept until React Hooks came along, leading to an overdue resolution in the realm of frontend development. This marks the moment when React's true essence emerged, revealing its potential and aligning it with modern development trends.
```markdown
#Hooks Limitations

Hooks can only be called inside the body of a functional component. They cannot be used within loops, conditional statements, or nested functions. This is because they should not be executed more than once during the rendering phase.

Modern frameworks represent a rebellion against traditional object-oriented encapsulation. Object-oriented programming emphasizes objects before properties and methods, whereas modern frameworks emphasize global rules and direct expression. The reason hooks are tied to specific components is that their execution relies on the component's lifecycle.
```

```markdown
#Class Component Comparison

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

#Core Functionality

The core functionality of a component often revolves around state management and side effects. While `render` is the primary function that defines what to display, other functions handle setup and cleanup tasks.

Modern hooks like `useEffect` are designed to manage these side effects more cleanly. Instead of relying on `this`, they use closures to keep track of subscriptions or cleanup operations. This approach allows for more flexible and readable code structures.

In traditional object-oriented programming, information flow typically follows three main paths: parameters (params), global variables (globals), and member variables (this). However, in complex domain models, sometimes you need to express certain local context information that shouldn't be explicitly passed around. This is where modern frameworks excel by providing a way to manage this through context variables (context) rather than explicit propagation.

#Modern Framework Advantages

The key innovation of modern frameworks lies in their ability to abstract away the complexities of object-oriented programming while maintaining some of its benefits. Instead of relying on `this`, developers can express logic in a more declarative and global way, which often leads to cleaner and more predictable code.

For example:
```javascript
(props, @reactive state, @implicit context) => vdom
```


In React Hooks, the concept of **implicit context** has been addressed with a corresponding technical form. The context positioning mechanism is determined by type, functioning similarly to an import mechanism for implicit variables.

```javascript
const user = useContext(CurrentUser);
const notifications = useContext(Notifications);
```

The introduction of React Hooks does not signify the decline of object-oriented components. Traditional strengths are powerful, and vibrant cultures are inherently inclusive. We resolved the corresponding issues in the Vue 3.0 framework using a meta programming approach: declaring code blocks together at compile time allows for modularization via meta programming mechanisms, attaching various slots to the component object. Runtime-wise, object-oriented programming remains seamless.

**Question:** "As a runtime, object-oriented programming has no issues whatsoever." How is this understood?
**Answer:** At the framework level, like Vue 3.0, the runtime appears similar to traditional OOP, where watch functions no longer need to bind to objects. However, a current instance object (currentInstance) exists, effectively acting as an implicit this pointer. In reality, most data remains bound to this pointer, mirroring typical OOP practices. Thus, from a structural standpoint, there is no difference.

---


## Frontend Programming Model

From the current perspective:

1. The **render function** generates the view object based on the data model.
2. Interactions in the view object trigger **action functions**, which modify the data object.
3. The rendering process is driven by a responsive driving mechanism, automatically updating the view object based on differences calculated using the Virtual DOM's Diff algorithm.

The programming model becomes more complex due to the nature of the application—multiple components rather than a single one. A divide-and-conquer approach is necessary for effective management.

![State-View-Action Tree](react/state-view-action-tree.png)

The core organizational challenge lies in integrating **StateTree**, **ViewTree**, and **ActionTree** into a cohesive structure.

Traditionally, programming was dominated by the Component Tree, with all state and action data linked to the Component object. Modern approaches isolate state data into a dedicated Store, while Actions and related state variables collectively form the Store. While this organizational method isn't unique, it requires a natural mechanism to bind StateTree and ActionTree.

In an ideal scenario:

```html
<input name="myField"/>
<span>{{state}}</span>
<button label="submit" v-on:click="submitForm" />
```

1. The input control displays the `myField` state.
2. State updates automatically when the input changes, reflecting in the StateTree.
3. The `submitForm` function is bound to the button's click event, mirroring typical data access patterns.

This setup allows for:
- Direct binding of user interactions to action functions via the StateScope.
- Access to variables through the lexical scope mechanism, akin to programming languages like JavaScript.

