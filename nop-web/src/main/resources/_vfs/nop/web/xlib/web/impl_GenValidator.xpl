<grou>
    <input-number name="fatalSeverity" required="@:true"/>

    <input-table name="checks" required="@:true" addable="@:true"
                 editable="@:true" removable="@:true"
                 draggable="@:true">
        <columns>
            <input-text name="id" required="true"/>
            <input-text name="errorCode" required="true"/>
            <input-text name="errorDescription"/>
        </columns>
    </input-table>
</grou>