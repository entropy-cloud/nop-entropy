# Vite

## Multi-Module

Multiple modules should have consistent AMIS versions; otherwise, multiple package versions may be loaded during debugging.

## Turbo

1. Add a postinstall configuration in the root project's package.json. Using `pnpm i` will automatically execute the postinstall script.

```json
"postinstall": "turbo run stub",
```

2. Add a stub configuration to the pipeline definition in `turbo.json`. An empty object is sufficient for the stub.

```json
{
  "pipeline": {
    "stub": {
    }
  }
}
```

3. Configure stubs in the `internal/vite-config` project. The following script should be added:

```json
"scripts": {
  "stub": "pnpm unbuild --stub"
},
```
