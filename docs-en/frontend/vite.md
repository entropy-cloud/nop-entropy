
# Vite

## Multiple Modules

The amis versions referenced across multiple modules must be consistent; otherwise, during debugging and development, multiple versions of the package may be loaded.

## turbo

1. Add a postinstall configuration to the root project's package.json. pnpm i will automatically execute the postinstall script.

```
"postinstall": "turbo run stub",
```

2. In the pipeline definition of turbo.json, add a stub configuration. The stub can be an empty object.

```json
{
  "pipeline": {
    "stub": {
    }
  }
}
```

3. Configure stub in projects such as internal/vite-config.

```
  "scripts": {
    "stub": "pnpm unbuild --stub"
  },
```

<!-- SOURCE_MD5:8bafd65971338625e404395fb1df1daa-->
