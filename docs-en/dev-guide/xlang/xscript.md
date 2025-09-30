# XScript

XScript is a scripting language with syntax similar to TypeScript. In XPL, you can include XScript via the `<c:script>` tag. XScript adopts a subset of TypeScript syntax.

See the grammar definition file [XLangParser.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/antlr/XLangParser.g4)

## Features removed from JavaScript syntax

1. The class definition part and everything related to `prototype` are removed. To simplify interoperation with Java, only types that already exist in Java are allowed; creating new types is not supported.
2. Only type declarations compatible with Java are allowed; more complex TypeScript type declarations are not supported.
3. `undefined` is removed; only `null` is used.
4. The `generator` and `async` syntax is removed.
5. The `import` syntax is modified: only importing classes and tag libraries is supported; importing other xscript files is currently not supported.
6. All `===`-related syntax is removed, and type coercion by `==` is disallowed.
7. Assignment inside expressions is not allowed; for example, `while((x=f() != 0))` is not permitted.

## Syntax features added compared to JavaScript

1. Compile-time expressions.
   Use `#{expr}` to denote macro expressions executed at compile time; their results become part of the abstract syntax tree. For example

```xlang
 // Execute a compile-time expression
  let x = #{ a.f(3) }
```

2. Executing XPL tags

```
<c:script>
  // Execute an xpl tag
  let y = xpl('my:MyTag',{a:1,b:x+3})
</c:script>
```

`xpl` is a macro function that supports three invocation forms.

```
result = xpl `<my:MyTag a='${1}' b='${x+3}' />`
result = xpl('my:MyTag',{a:1,b:x+3})
result = xpl('my:MyTag',1, x+3)
```

The third form requires the parameter order to be the same as defined in the tag library.

3. Invoking extension methods. You can register extension object methods for Java objects,
   and register static functions on helper classes as extension functions.

```javascript
    ReflectionManager.instance().registerHelperMethods(List.class, ListFunctions.class, null);
    ReflectionManager.instance().registerHelperMethods(String.class, StringHelper.class, "$");
    ReflectionManager.instance().registerHelperMethods(LocalDate.class, DateHelper.class, "$");
```

The platform has built-in extension methods defined on `ListFunctions` for `List`, and extension methods defined on `StringHelper` for `String`. Therefore, you can use the following syntax in XScript:

```javascript
   str.$capitalize()  // Equivalent to calling StringHelper.capitalize(str);
```

To avoid conflicts with method names already defined on Java classes, extension methods are generally registered with a `$` prefix.
`ListFunctions` adds methods such as `push`/`pop` from JavaScript's `Array` object to `List`. To keep as close as possible to JavaScript syntax, these particular extension methods do not use the
`$` prefix.

4. Security restrictions. All variable names prefixed with `$` are reserved for system variables; you cannot declare or assign variables with a `$` prefix in XScript. Access to sensitive objects such as `System`,
   `Class` is prohibited.

## Global variables

The global variables and functions available in XScript are managed by the `EvalGlobalRegistry` class. Currently, it primarily registers the methods defined in the `GlobalFunctions` class.

In debug mode, you can query all registered global variables and functions via frontend REST requests:

1. `/r/DevDoc__globalFunctions`
2. `/r/DevDoc__globalVars`

For additional debugging information, see [debug.md](../debug.md)

| Variable       | Description                                 |
|---------------|---------------------------------------------|
| $context      | Corresponds to ContextProvider.currentContext() |
| $scope        | The current runtime IEvalScope              |
| $out          | The current runtime IEvalOutput             |
| $beanProvider | The IBeanProvider associated with the current runtime IEvalScope |
| $evalRt       | The current runtime EvalRuntime             |
| $             | Corresponds to the Guard class              |
| $JSON         | Corresponds to the JsonTool class           |
| $Math         | Corresponds to the MathHelper class         |
| $String       | Corresponds to the StringHelper class       |
| $Date         | Corresponds to the DateHelper class         |
| _             | Corresponds to the Underscore class         |
| $config       | Corresponds to the AppConfig class          |

## Extension properties
By implementing the extension interfaces IPropGetMissingHook and IPropSetMissingHook, dynamic entity properties can be accessed in script code or expression language in the same way as ordinary properties.

```typescript
entity.extField = 3;

// Equivalent to
entity.prop_set('extField',3);
```

## Context-specific variables

* `codeGenerator`: type `XCodeGenerator`, available in code generation templates under the `precompile` directory
* `__dsl_root`: type `XNode`, available in metaprogramming phases such as `x:gen-extends` and `x:post-extends`

## Implicit scope passing


In XLang expressions, an implicit mechanism is provided for passing IEvalScope.

```
    @EvalMethod
    public static ExcelImage QRCODE(IEvalScope scope) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        ExpandedCell cell = xptRt.getCell();

        QrcodeOptions options = new QrcodeOptions();
        cell.getModel().readExtProps("qr:", true, options);
        ...
        return image;
    }
```

If a function is annotated with `@EvalMethod`, its first parameter must be IEvalScope. When called within an expression, the runtime scope of the expression is automatically passed in. Through the scope you can access other variables in the context.

For example, for the above static function in ReportFunctions, when calling it in XScript you only need `QRCODE()`. The scope parameter is passed implicitly; there is no need to pass it explicitly.


## JS compatibility

XScript can be viewed as Java with JavaScript syntax. It uses Java objects and libraries, so in many ways it is not compatible with JavaScript.

### Global objects
XScript does not have global objects such as JSON or Object. All global object names start with `$`, e.g., `$JSON`, `$Math`, `$Date`, etc.

### Collection functions
* Via extension functions in ListFunctions, Java's List objects are augmented with methods from JavaScript's Array, such as `push/pop/shift/unshift/includes/some/reduceRight/slice/splice`, etc.
* Functions like `forEach/map` only support a single parameter, using the methods defined on Java Collections. JavaScript's map and forEach both have two parameters and can access the element index. XScript adds `map2/forEach2`, whose semantics are similar to JavaScript.

<!-- SOURCE_MD5:ec37ad888310cc9f494b7b3f05388eb5-->
