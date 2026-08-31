# vendor/

Vendored (供应商内联) third-party dependencies, committed so that the tool runs
directly after `git clone` with **no `npm/pnpm install` and no build step**.

| package | version | source | why vendored |
|---------|---------|--------|--------------|
| commander | 15.0.0 | https://www.npmjs.com/package/commander | CLI 层唯一依赖；纯 ESM、零传递依赖、无原生二进制 → vendor 零风险 |

## 升级步骤 (revendor)

```bash
cd tools/mission-driver
npm i commander@<new-version> --no-save
rm -rf vendor/commander
cp node_modules/commander/index.js node_modules/commander/LICENSE node_modules/commander/package.json vendor/commander/
cp -r node_modules/commander/lib vendor/commander/
# 更新本表格里的 version，跑 npm test 验证
```

引擎核心保持零依赖；本目录只承载 CLI 层内联依赖。
