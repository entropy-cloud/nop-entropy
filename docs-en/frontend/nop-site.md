# Interface Framework

## Menu Construction

In `src/store/modules/permission.ts`, the `getMenuList` function is used to dynamically load the menu. Then, the `transformObjToRoute` function in `src/router/helper/routeHelper.ts` is called to convert the menu result into a Vue route.

Modify `src/settings/projectSettings.ts` to set `permissionMode` as "BACK" to load the menu from the backend.