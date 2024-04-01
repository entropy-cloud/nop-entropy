# 界面框架

## 菜单构建

在`src/store/modules/permission.ts`中通过getMenuList函数动态加载菜单，然后调用
`src/router/helper/routeHelper.ts`中的transformObjToRoute函数将菜单返回结果转化为vue route

修改`src/settings/projectSettings.ts`中的permissionMode为BACK，从后台加载菜单
