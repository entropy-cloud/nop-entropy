# UI Framework

## Menu Construction

In `src/store/modules/permission.ts`, dynamically load menus via the getMenuList function, then call
the transformObjToRoute function in `src/router/helper/routeHelper.ts` to convert the menu response into Vue routes.

Set permissionMode in `src/settings/projectSettings.ts` to BACK to load menus from the backend.
<!-- SOURCE_MD5:cc4611e355d4ab0c67481202649c1a55-->
