# Vite

## 多模块

多个模块中引入的amis版本需要一致，否则可能在调试开发时会出现多个版本的包被加载。

## turbo

1. 在根项目的package.json中增加postinstall配置。 pnpm i会自动执行postinstall脚本

```
"postinstall": "turbo run stub",
```

2. turbo.json的pipeline定义中要增加stub配置。stub对应空对象即可

```json
{
  "pipeline": {
    "stub": {
    }
  }
}
```

3. internal/vite-config等项目中配置stub

```
  "scripts": {
    "stub": "pnpm unbuild --stub"
  },
```
