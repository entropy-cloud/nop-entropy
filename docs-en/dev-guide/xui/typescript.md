# Extracting Sub Fields of Entity Types

To declare a type that represents the definitions of fields "a" and "b" from type `A`, and modify the type of field "b" to be nullable, you can use combinations of the built-in types `"Pick"` and `"Partial"`. Below is an example code:

```typescript
type A = { a: number, b: string, c: boolean };

type ExtractAB = Pick<A, 'a' | 'b'> & Partial<Pick<A, 'b>>;
```

## Example Usage

Here's how you can use the defined type `ExtractAB`:

```typescript
const obj: ExtractAB = {
  a: 1,
  b: null // b is now nullable
};
```

In the above code:
1. First, we use the `"Pick"` type to extract fields "a" and "b" from type `A`.
2. Then, we apply the `"Partial"` type to the extracted result, making field "b" nullable.
3. Finally, we combine these two types using the ampersand operator `&` to create a new type `ExtractAB`.

The resulting type `ExtractAB` will only contain fields "a" and a nullable field "b". Other fields, such as "c", are excluded from this type.
