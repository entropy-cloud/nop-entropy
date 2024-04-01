# 通过nop-sdk实现前端集成

nop-chaos前端项目中所有nop平台相关的代码都集中在`@nop-chaos/sdk`模块中。如果要在新的项目中引入AMIS页面支持，只需要执行以下步骤

* 具体示例可以参见 [nop-for-ruoyi-vue3](https://gitee.com/canonical-entropy/nop-for-ruoyi-vue3)和[nop-site](https://gitee.com/canonical-entropy/nop-chaos/tree/master/packages/nop-site)模块.

* **最小化的示例参见[nop-sdk-demo](https://gitee.com/canonical-entropy/nop-chaos/tree/master/packages/nop-sdk-demo)**

## 1. 引入nop-sdk模块

```
"@nop-chaos/sdk": "file:./nop-sdk",
"react": "^18.0.0",
"react-dom": "^18.0.0",
 "amis": "^3.4.0",
"amis-core": "^3.4.0",
"amis-formula": "^3.4.0",
"amis-ui": "^3.4.0",
```

[nop-sdk](https://gitee.com/canonical-entropy/nop-chaos/tree/master/nop-sdk)是`@nop-chaos/sdk`模块打包后的结果。直接将它拷贝到目标工程中。
`file:./nop-sdk`表示模块代码在当前目录的nop-sdk子目录中。

## 2. 实现adapter

在[src/nop/initNopApp.ts](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-site/src/nop/initNopApp.ts)文件中为适配器接口提供实现，实现nop-sdk集成。

```javascript
// 调试器使用了element的组件
import 'element-plus/dist/index.css'

// Amis内置的调试器需要这里的css
import 'amis/lib/helper.css';

import '@fortawesome/fontawesome-free/css/all.css';
import '@fortawesome/fontawesome-free/css/v4-shims.css';
import 'amis/lib/themes/cxd.css';

//import 'amis/sdk/iconfont.css';
import 'amis-ui/lib/locale/en-US';
import 'amis-ui/lib/locale/zh-CN';

import '@nop-chaos/sdk/lib/style.css'

...

function initAdapter(app: App) {
    registerAdapter({

        useLocale(): string {
            return currentLocale.value
        },

        /**
         * 返回当前的认证token
         */
        useAuthToken(): string {
            return getToken()
        },

        setAuthToken(token?: string) {
            useUserStore().setToken(token)
        },
        ...
    })
}

export function initNopApp(app: App) {
    initAdapter(app)

    app.component("XuiPage", XuiPage)
    ...
}
```

## 3. 在main.ts中加入对initNopApp的调用

```javascript

import {initNopApp} from '@/nop/initNopApp'

const app = createApp(App)
...
initNopApp(app)

app.mount('#app')
```

## 4. 在主页面上增加AmisToast控件，用于弹出错误消息

一般在App.vue页面中增加控件

```javascript
<template>
  <ConfigProvider :locale="getAntdLocale">
    <AmisToast theme="cxd"/>
   ...
  </ConfigProvider>
</template>

<script lang="ts" setup>
  import { ConfigProvider } from 'ant-design-vue';
  ...
  import {AmisToast} from '@nop-chaos/sdk'
</script>
```
