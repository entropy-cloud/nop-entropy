
# Frontend Integration via nop-sdk

All nop platform-related code in the nop-chaos frontend project is centralized in the `@nop-chaos/sdk` module. To add AMIS page support to a new project, simply perform the following steps.

* For concrete examples, see [nop-for-ruoyi-vue3](https://gitee.com/canonical-entropy/nop-for-ruoyi-vue3) and the [nop-site](https://gitee.com/canonical-entropy/nop-chaos/tree/master/packages/nop-site) module.

* **For a minimal example, see [nop-sdk-demo](https://gitee.com/canonical-entropy/nop-chaos/tree/master/packages/nop-sdk-demo)**

## 1. Bring in the nop-sdk module

```
"@nop-chaos/sdk": "file:./nop-sdk",
"react": "^18.0.0",
"react-dom": "^18.0.0",
 "amis": "^3.4.0",
"amis-core": "^3.4.0",
"amis-formula": "^3.4.0",
"amis-ui": "^3.4.0",
```

[nop-sdk](https://gitee.com/canonical-entropy/nop-chaos/tree/master/nop-sdk) is the packaged output of the `@nop-chaos/sdk` module. Copy it directly into the target project.  
`file:./nop-sdk` indicates the module code resides in the nop-sdk subdirectory of the current directory.

## 2. Implement the adapter

Provide implementations for the adapter interface in the [src/nop/initNopApp.ts](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-site/src/nop/initNopApp.ts) file to integrate nop-sdk.

```javascript
// The debugger uses components from Element Plus
import 'element-plus/dist/index.css'

// Amis’s built-in debugger requires these CSS files
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
         * Return the current authentication token
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

## 3. Add a call to initNopApp in main.ts

```javascript

import {initNopApp} from '@/nop/initNopApp'

const app = createApp(App)
...
initNopApp(app)

app.mount('#app')
```

## 4. Add the AmisToast component on the main page to show error messages

Typically add the component in the App.vue page.

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

<!-- SOURCE_MD5:ceb7090c990f774613605be3bdc61cee-->
