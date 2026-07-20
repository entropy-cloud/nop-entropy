<c:unit xmlns:c="c" xmlns:xpl="xpl">
    <c:script>
        let renderMode = $config.var('nop.web.render-mode', 'amis');
    </c:script>
    <c:if test="${renderMode == 'flux'}">
        <c:print>
            <lib>
                <tags>
                    <GenPage x:override="replace" outputMode="xjson">
                        <attr name="view" mandatory="true" type="String" stdDomain="v-path"/>
                        <attr name="page" mandatory="true" type="String"/>
                        <attr name="forPicker" type="Boolean" optional="true"/>
                        <attr name="fixedProps" optional="true" stdDomain="csv-set"/>
                        <source>
                            <flux-web:GenPage view="${view}" page="${page}"
                                              xpl:lib="/nop/web/xlib/flux-web.xlib"/>
                        </source>
                    </GenPage>

                    <GenForm x:override="replace" outputMode="xjson">
                        <attr name="view" mandatory="true" type="String" stdDomain="v-path"/>
                        <attr name="form" mandatory="true" type="string"/>
                        <attr name="skipForm" optional="true" type="boolean"/>
                        <attr name="fixedProps" optional="true" stdDomain="csv-set"/>
                        <slot name="default" outputMode="xjson">
                            <arg name="objMeta"/>
                            <arg name="formModel"/>
                            <arg name="formSelection"/>
                        </slot>
                        <source>
                            <flux-web:GenForm view="${view}" form="${form}"
                                              xpl:lib="/nop/web/xlib/flux-web.xlib"/>
                        </source>
                    </GenForm>

                    <GenGrid x:override="replace" outputMode="xjson">
                        <attr name="view" mandatory="true" type="String" stdDomain="v-path"/>
                        <attr name="grid" mandatory="true" type="string"/>
                        <attr name="fixedProps" optional="true" stdDomain="csv-set"/>
                        <source>
                            <flux-web:GenGrid view="${view}" grid="${grid}"
                                              xpl:lib="/nop/web/xlib/flux-web.xlib"/>
                        </source>
                    </GenGrid>

                    <GenInputTable x:override="replace">
                        <attr name="view" mandatory="true"/>
                        <attr name="grid" mandatory="true"/>
                        <attr name="editMode" optional="true"/>
                        <attr name="removable" optional="true"/>
                        <attr name="addable" optional="true"/>
                        <attr name="editable" optional="true"/>
                        <source>
                            <flux-web:GenInputTable view="${view}" grid="${grid}"
                                                    xpl:lib="/nop/web/xlib/flux-web.xlib"/>
                        </source>
                    </GenInputTable>

                    <GenTable x:override="replace">
                        <attr name="view" mandatory="true"/>
                        <attr name="grid" mandatory="true"/>
                        <attr name="editMode" optional="true"/>
                        <source>
                            <flux-web:GenTable view="${view}" grid="${grid}"
                                               xpl:lib="/nop/web/xlib/flux-web.xlib"/>
                        </source>
                    </GenTable>
                </tags>
            </lib>
        </c:print>
    </c:if>
</c:unit>
