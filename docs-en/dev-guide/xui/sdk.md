# Implementing Frontend Integration with nop-sdk

The frontend project of nop-chaos consolidates all platform-related code within the `@nop-chaos/sdk` module. To introduce AMIS page support in a new project, follow these steps:

* For detailed examples, refer to [nop-for-ruoyi-vue3](https://gitee.com/canonical-entropy/nop-for-ruoyi-vue3) and the [nop-site](https://gitee.com/canonical-entropy/nop-chaos/tree/master/packages/nop-site) module.

* **Minimal example available in** [nop-sdk-demo](https://gitee.com/canonical-entropy/nop-chaos/tree/master/packages/nop-sdk-demo).

## 1. Importing the nop-sdk Module

```
"@nop-chaos/sdk": "file:./nop-sdk",
"react": "^18.0.0",
"react-dom": "^18.0.0",
 "amis": "^3.4.0",
"amis-core": "^3.4.0",
"amis-formula": "^3.4.0",
"amis-ui": "^3.4.0",
```

The [nop-sdk](https://gitee.com/canonical-entropy/nop-chaos/tree/master/nop-sdk) is the compiled result of the `@nop-chaos/sdk` module. Simply copy it into your target project.
`file:./nop-sdk` indicates that the module code resides in the nop-sdk subdirectory within the current directory.

## 2. Implementing the Adapter

In `[src/nop/initNopApp.ts](https://gitee.com/canonical-entropy/nop-chaos/blob/master/packages/nop-site/src/nop/initNopApp.ts)`, provide an implementation for the adapter interface to integrate nop-sdk.

```javascript
// Using elements' components
import 'element-plus/dist/index.css'

// Amis-built-in debugger requires this CSS
import 'amis/lib/helper.css';

import '@fortawesome/fontawesome-free/css/all.css';
import '@fortawesome/fontawesome-free/css/v4-shims.css';
import 'amis/lib/themes/cxd.css';

// Importing amis icons
// import 'amis/sdk/iconfont.css';
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
         * Returns the current authentication token
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

## 3. Calling initNopApp in main.ts

```javascript
import {initNopApp} from '@/nop/initNopApp'

const app = createApp(App)
...
initNopApp(app)

app.mount('#app')
```

## 4. Adding AmisToast Component to the Main Page for Error Notifications

Typically, this component is added in `App.vue`. 


```markdown
# Template Section

<template>
  <ConfigProvider :locale="getAntdLocale">
    <AmisToast theme="cxd"/>
    ...
  </ConfigProvider>
</template>

# Script Section

<script lang="ts" setup>
  import { ConfigProvider } from 'ant-design-vue';
  ...
  import { AmisToast } from '@nop-chaos/sdk'
</script>
```
