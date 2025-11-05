<c:unit>
    <c:script><![CDATA[
        import io.nop.xlang.xmeta.SchemaLoader;

        const objMeta = SchemaLoader.loadXMeta(path);

        function gen_view_grids(propMeta, schema, paths){
           if(schema.objSchema){
              for(let prop of schema.props){
                paths.push(prop.name);
                gen_view_grids(prop, prop.schema,paths)
                paths.pop()
              }
           }else if(schema.listSchema){
              xpl('thisLib:GenGrid', {schema: schema.itemSchema, gridId: paths.join('/') })
              paths.push(propMeta.childName || propMeta.name + '-item');
              gen_view_grids(propMeta, schema.itemSchema,paths)
              paths.pop()
           }
        }

        function gen_view_forms(propMeta, schema, paths){
           if(schema.objSchema){
              xpl('thisLib:GenForm', {schema: schema, formId: paths.join('/') || schema.xmlName || 'main' })
              for(let prop of schema.props){
                paths.push(prop.name);
                gen_view_forms(prop, prop.schema,paths)
                paths.pop()
              }
           }else if(schema.listSchema){
              paths.push(propMeta.childName || propMeta.name + '-item');
              gen_view_forms(propMeta, schema.itemSchema,paths)
              paths.pop()
           }
        }

    ]]></c:script>

    <view>
        <grids>
            <c:script>
                gen_view_grids(null, objMeta,[])
            </c:script>
        </grids>

        <forms>
            <c:script>
                gen_view_forms(null,objMeta,[])
            </c:script>
        </forms>
    </view>
</c:unit>