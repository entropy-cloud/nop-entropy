
# Extracting a Subset of Properties from a Type

To declare a type that picks properties "a" and "b" from type A and makes property "b" nullable, use the Pick utility type and override "b" to include null. Here is the sample code:

```typescript
type A = { a: number, b: string, c: boolean };

type ExtractAB = Pick<A, 'a'> & { b: string | null };

// Example usage
const obj: ExtractAB = {
  a: 1,
  b: null // b can be null
};
```

In the code above, we first use Pick to select property "a" from A, then define "b" as string | null. Finally, we combine them to produce a new type, ExtractAB.

Now, ExtractAB includes only property "a" and the nullable property "b". Other properties are omitted.

Note: Partial makes properties optional, not nullable. If you want "b" to be optional instead of nullable, you can write:
```typescript
type ExtractABOptional = Pick<A, 'a'> & Partial<Pick<A, 'b'>>;
```

<!-- SOURCE_MD5:2eff7350e4eee3ae10430fec51042d5f-->
