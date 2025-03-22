# XScript

XScript is a scripting language syntactically similar to TypeScript. In XPL, you can import XScript scripts using the `<c:script>` tag. XScript uses a subset of the TypeScript syntax.

The grammar definition file can be found at [XLangParser.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/antlr/XLangParser.g4).

## Features Removed from JavaScript Syntax

1. Class definitions and related parts, including `prototype`, have been removed. Simplified for Java compatibility, only existing Java types are allowed; new types cannot be created.
2. Only compatible with Java type declarations; complex TypeScript type declarations are not supported.
3. `undefined` has been removed; only `null` is used.
4. The `generator` and `async` syntax have been removed.
5. The `import` syntax has been modified to only support importing classes and libraries; importing other XScript files is currently unsupported.
6. Syntax related to `===` and `==` has been removed, including type conversions using `==`.
7. Assignment statements within expressions are disallowed, such as `while((x=f() != 0))`.

## Added Syntax Features Compared to JavaScript

1. **Compile-Time Expressions**:  
   Use the `${expr}` syntax for compile-time evaluations. The result becomes part of the abstract syntax tree. For example:

```xlang
// Compile-time expression
let x = #{ a.f(3) }
```

2. **Execute XPL Tags**:  
   Execute macro tags using `<c:script>`:

```html
<c:script>
  // Execute XPL tag
  let y = xpl('my:MyTag',{a:1,b:x+3});
</c:script>
```

`xpl` is a macro function supporting three calling forms:

```html
result = xpl `<my:MyTag a='${1}' b='${x+3}' />`
result = xpl('my:MyTag",{a:1,b:x+3})
result = xpl('my:MyTag',1, x+3)
```

The third form requires the parameter order to match that defined in the tag library.

3. **Call Extended Methods**:  
   Register extended methods for Java objects and static functions of helper classes using `ReflectionManager`. For example:

```javascript
// Register extended methods for List
ReflectionManager.instance().registerHelperMethods(List.class, ListFunctions.class, null);
// Register extended methods for String
ReflectionManager.instance().registerHelperMethods(String.class, StringHelper.class, "$");
// Register extended methods for LocalDate
ReflectionManager.instance().registerHelperMethods(LocalDate.class, DateHelper.class, "$");
```

Built-in functions like `str.$capitalize()` are equivalent to `StringHelper.capitalize(str)` in JavaScript. To avoid conflicts with existing Java method names, most extended methods are prefixed with `$`.

4. **Security Restrictions**:  
   Variables starting with `$` are reserved for system use and cannot be declared or modified in XScript scripts. Access to `System`, `Class`, etc., is prohibited.

## Global Variables

Global variables and functions available in XScript are managed by the `EvalGlobalRegistry` class. Currently, methods from the `GlobalFunctions` class are primarily registered.

In debug mode, you can retrieve all registered global variables and functions via frontend REST requests:

1. `/r/DevDoc__globalFunctions`
2. `/r/DevDoc__globalVars`

Additional debugging information is available at [debug.md](../debug.md).


| Variable Name | Description                           |
|---------------|--------------------------------------|
| $context      | Corresponds to `ContextProvider.currentContext()`    |
| $scope        | Current runtime `IEvalScope`                   |
| $out          | Current runtime `IEvalOutput`                  |
| $beanProvider | Current runtime `IEvalScope` associated `IBeanProvider` |
| $evalRt       | Current runtime `EvalRuntime`                  |
| $             | Corresponds to `Guard` class                  |
| $JSON         | Corresponds to `JsonTool` class                |
| $Math         | Corresponds to `MathHelper` class              |
| $String       | Corresponds to `StringHelper` class            |
| $Date         | Corresponds to `DateHelper` class              |
| _             | Corresponds to `Underscore` class               |
| $config       | Corresponds to `AppConfig` class                |


## Extended Properties
Implemented `IPropGetMissingHook` and `IPropSetMissingHook` interfaces. These can be accessed when accessing dynamic entity properties in script or expression language, similar to accessing regular properties.

```typescript
entity.extField = 3;

// Equivalent to
entity.prop_set('extField', 3);
```


## Specific Context Variables

* `codeGenerator`: `XCodeGenerator` type, used in the `precompile` directory's code generation templates
* `__dsl_root`: `XNode` type, used in `x:gen-extends` and `x:post-extends` meta-programming segments


## JS Compatibility
XScript can be considered as a JavaScript-like syntax for Java. Since both objects and libraries are Java-based, there are many incompatibilities with actual JavaScript.


### Global Objects
In XScript, there is no `JSON`, `Object`, etc. All global object names start with `$`, such as `$JSON`, `$Math`, `$Date`, etc.


### Collection Functions
* Functions like `push/pop/shift/unshift/includes/some/reduceRight/slice/splice` are added to Java's List via extensions in `ListFunctions`.
* JavaScript's `map` and `forEach` require only one parameter, using methods defined in Java's `Collection`. XScript adds `map2/forEach2`, which have similar semantics to JavaScript's equivalents.

