# web.xlib

The `page.yaml` file can use the tags defined in `web.xlib`, such as `GenPage`, `GenAction`, etc., to dynamically generate AMIS pages.

## LoadPage

Based on the input `page` parameter, load the corresponding JSON page and obtain the JSON object. The `page` parameter may correspond to the `pageId` of a view model in `view` or represent a complete page's virtual path, such as:
- `page=/nop/auth/pages/NopAuthUser/main.page.yaml`
- `page=main`
