<c:unit xmlns:c="c" xmlns:thisLib="thisLib" xmlns:xpl="xpl">
    <picker valueField="id" labelField="${objMeta?.displayProp || 'id'}">
        <c:include src="grid_crud.xpl"/>
    </picker>
</c:unit>
