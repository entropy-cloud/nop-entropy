# XScript and JavaScript Compatibility

## All Objects Are Java Objects

XScript syntax is similar to JavaScript, but it does not support JavaScript built-in objects such as Array, Set, Map.

Strictly adhering to ECMAScript object standards would lead to incompatibility with Java. The current syntax design prioritizes Java compatibility and avoids object conversions, so the array syntax `[1,2,3]` returns an ArrayList, while the object syntax `{a:1,b:2}` returns a LinkedHashMap (LinkedHashMap is chosen instead of HashMap to preserve key order consistent with insertion order).

Therefore, the objects used in XScript are all Java objects, and their methods are Java methods. Lists receive special handling: several functions are provided for List (see ListFunctions.java), allowing the use of push, pop, slice, etc. However, original List methods like add are not removed. In essence, these functions are implemented by invoking methods on Java objects.

In XScript you can call `new Set()`, but that simply creates a LinkedHashSet object.

## No Distinction Between undefined and null

The concept of undefined is incompatible with Java, so the `===` operator is removed, and the semantics of undefined are eliminated; only the semantics of null are defined.
<!-- SOURCE_MD5:5c0c9dd4e27093bec1e61ec397d98870-->
