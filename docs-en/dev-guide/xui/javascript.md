# Compatibility of XScript with JavaScript

## All Objects are Java Objects

XScript syntax is similar to JavaScript, but it does not support JavaScript's built-in objects such as Array, Set, and Map.

If strictly compliant with the ECMAScript object standards, it will lead to incompatibility with Java. The current syntax design prioritizes Java compatibility to avoid object conversion, so
- Array syntax `[1,2,3]` returns an ArrayList.
- Object syntax `{a:1,b:2}` returns a LinkedHashMap (LinkedHashMap was chosen over HashMap to maintain the order of keys and the order of insertion).

Therefore, all objects used in XScript are Java objects, and all methods within them are Java methods. For List, some functions have been extended (see ListFunctions.java), and standard functions like push, pop, slice can be used, but functions such as add are not supported. The basic functions are implemented using Java object methods.

In XScript, `new Set()` can be called, but it only creates a LinkedHashSet.

## No Distinction Between undefined and null

The design of undefined in JavaScript is incompatible with Java, so the `===` operator has been removed. Only the null semantics have been defined; undefined semantics have been discarded.
