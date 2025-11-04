<c:unit xpl:outputMode="text">package ${objMeta.packageName}._gen;

    import io.nop.core.resource.component.AbstractComponentModel;

    public class ${schema.simpleClassName} extends AbstractComponentModel{
    <c:for var="prop" items="${schema.props}">
        <c:choose>
            <when test="${prop.schema.simpleSet}">

            </when>
            <when test="${prop.schema.simpleValue}">
                private ${prop.schema.type} _${prop.name};

                <c:if test="${prop.description}">
                    /**
                    * ${prop.description.$replace('*/','* /')}
                    */
                </c:if>
                public ${prop.schema.type} ${prop.methodGetName}(){
                return _${prop.name};
                }

                public void ${prop.methodSetName}(${prop.schema.type} value){
                checkAllowChange();
                this._${prop.name} = value;
                }
            </when>
            <when test="${prop}">

            </when>
        </c:choose>
    </c:for>
    }
</c:unit>