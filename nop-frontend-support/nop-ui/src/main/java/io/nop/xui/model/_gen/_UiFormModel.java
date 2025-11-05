package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiFormModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xui/form.xdef <p>
 * 表单模型
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiFormModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: api
     * 
     */
    private io.nop.xui.model.UiApiModel _api ;
    
    /**
     *  
     * xml name: asyncApi
     * 设置此属性后，表单提交发送保存接口后，还会继续轮询请求该接口，直到返回 finished 属性为 true 才 结束。
     */
    private io.nop.xui.model.UiApiModel _asyncApi ;
    
    /**
     *  
     * xml name: bodyClassName
     * 
     */
    private java.lang.String _bodyClassName ;
    
    /**
     *  
     * xml name: canAccessSuperData
     * 
     */
    private java.lang.Boolean _canAccessSuperData ;
    
    /**
     *  
     * xml name: cells
     * 
     */
    private KeyedList<io.nop.xui.model.UiFormCellModel> _cells = KeyedList.emptyList();
    
    /**
     *  
     * xml name: checkInterval
     * 轮询请求的时间间隔，默认为 3 秒。设置 asyncApi 才有效
     */
    private java.lang.Integer _checkInterval ;
    
    /**
     *  
     * xml name: className
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: data
     * 表单的静态初始化数据
     */
    private java.lang.Object _data ;
    
    /**
     *  
     * xml name: defaultColumnRatio
     * 
     */
    private java.lang.Integer _defaultColumnRatio ;
    
    /**
     *  控件编辑模式
     * xml name: editMode
     * edit:编辑模式, add:新增模式, view:查看模式, query:查询条件。不同的模式下使用的控件可能不同
     */
    private java.lang.String _editMode ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: inheritData
     * 
     */
    private java.lang.Boolean _inheritData ;
    
    /**
     *  
     * xml name: initApi
     * <saveFailed xdef:value="string" />
     */
    private io.nop.xui.model.UiApiModel _initApi ;
    
    /**
     *  
     * xml name: initAsyncApi
     * Form 用来获取初始数据的 api,与 initApi 不同的是，会一直轮询请求该接口，直到返回 finished 属性为 true 才 结束。
     */
    private io.nop.xui.model.UiApiModel _initAsyncApi ;
    
    /**
     *  
     * xml name: initCheckInterval
     * 设置了 initAsyncApi 以后，默认拉取的时间间隔
     */
    private java.lang.Integer _initCheckInterval ;
    
    /**
     *  
     * xml name: initFetch
     * 设置了 initApi 或者 initAsyncApi 后，默认会开始就发请求，设置为 false 后就不会起始就请求接口
     */
    private java.lang.Boolean _initFetch ;
    
    /**
     *  
     * xml name: initFetchOn
     * 用表达式来配置
     */
    private java.lang.String _initFetchOn ;
    
    /**
     *  
     * xml name: interval
     * initApi的调用间隔。Form 支持轮询初始化接口。
     */
    private java.lang.Integer _interval ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: labelAlign
     * 
     */
    private java.lang.String _labelAlign ;
    
    /**
     *  
     * xml name: labelWidth
     * 
     */
    private java.lang.String _labelWidth ;
    
    /**
     *  
     * xml name: layout
     * 
     */
    private io.nop.xlang.xmeta.layout.LayoutModel _layout ;
    
    /**
     *  
     * xml name: layoutControl
     * 如果设置为wizard，则表示采用向导布局来组织页面。如果设置为tabs，则采用标签页来组织页面。
     */
    private java.lang.String _layoutControl ;
    
    /**
     *  
     * xml name: layoutMode
     * 
     */
    private java.lang.String _layoutMode ;
    
    /**
     *  
     * xml name: messages
     * 
     */
    private java.util.Map<java.lang.String,java.lang.String> _messages ;
    
    /**
     *  
     * xml name: objMeta
     * 
     */
    private java.lang.String _objMeta ;
    
    /**
     *  
     * xml name: panelClassName
     * 
     */
    private java.lang.String _panelClassName ;
    
    /**
     *  
     * xml name: persistData
     * 表单默认在重置之后（切换页面、弹框中表单关闭表单），会自动清空掉表单中的所有数据，
     * 如果你想持久化保留当前表单项的数据而不清空它，那么通过 Form 配置 persistData: "xxx"，指定一个 key ，来实现数据持久化保存
     */
    private java.lang.String _persistData ;
    
    /**
     *  
     * xml name: persistDataKeys
     * 
     */
    private java.util.Set<java.lang.String> _persistDataKeys ;
    
    /**
     *  
     * xml name: preventEnterSubmit
     * 表单默认情况下回车就会提交，如果想阻止这个行为，可以设置为false
     */
    private java.lang.Boolean _preventEnterSubmit ;
    
    /**
     *  
     * xml name: promptPageLeave
     * 
     */
    private java.lang.Boolean _promptPageLeave ;
    
    /**
     *  
     * xml name: redirect
     * 
     */
    private java.lang.String _redirect ;
    
    /**
     *  
     * xml name: reload
     * 
     */
    private java.lang.String _reload ;
    
    /**
     *  
     * xml name: renderer
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _renderer ;
    
    /**
     *  
     * xml name: resetAfterSubmit
     * 提交表单成功后，重置当前表单至初始状态
     */
    private java.lang.Boolean _resetAfterSubmit ;
    
    /**
     *  
     * xml name: rules
     * 
     */
    private KeyedList<io.nop.xui.model.UiFormRuleModel> _rules = KeyedList.emptyList();
    
    /**
     *  
     * xml name: selection
     * 
     */
    private io.nop.api.core.beans.FieldSelectionBean _selection ;
    
    /**
     *  
     * xml name: silentPolling
     * 配置刷新时是否显示加载动画
     */
    private java.lang.Boolean _silentPolling ;
    
    /**
     *  
     * xml name: size
     * 
     */
    private java.lang.String _size ;
    
    /**
     *  
     * xml name: stopAutoRefreshWhen
     * 通过表达式来配置停止刷新的条件
     */
    private java.lang.String _stopAutoRefreshWhen ;
    
    /**
     *  
     * xml name: submitOnChange
     * 
     */
    private java.lang.Boolean _submitOnChange ;
    
    /**
     *  
     * xml name: submitOnInit
     * 
     */
    private java.lang.Boolean _submitOnInit ;
    
    /**
     *  
     * xml name: submitText
     * 
     */
    private java.lang.String _submitText ;
    
    /**
     *  
     * xml name: target
     * 
     */
    private java.lang.String _target ;
    
    /**
     *  
     * xml name: title
     * 表单作为弹出页面使用时对应的对话框标题
     */
    private java.lang.String _title ;
    
    /**
     *  
     * xml name: wrapWithPanel
     * 
     */
    private java.lang.Boolean _wrapWithPanel ;
    
    /**
     * 
     * xml name: api
     *  
     */
    
    public io.nop.xui.model.UiApiModel getApi(){
      return _api;
    }

    
    public void setApi(io.nop.xui.model.UiApiModel value){
        checkAllowChange();
        
        this._api = value;
           
    }

    
    /**
     * 
     * xml name: asyncApi
     *  设置此属性后，表单提交发送保存接口后，还会继续轮询请求该接口，直到返回 finished 属性为 true 才 结束。
     */
    
    public io.nop.xui.model.UiApiModel getAsyncApi(){
      return _asyncApi;
    }

    
    public void setAsyncApi(io.nop.xui.model.UiApiModel value){
        checkAllowChange();
        
        this._asyncApi = value;
           
    }

    
    /**
     * 
     * xml name: bodyClassName
     *  
     */
    
    public java.lang.String getBodyClassName(){
      return _bodyClassName;
    }

    
    public void setBodyClassName(java.lang.String value){
        checkAllowChange();
        
        this._bodyClassName = value;
           
    }

    
    /**
     * 
     * xml name: canAccessSuperData
     *  
     */
    
    public java.lang.Boolean getCanAccessSuperData(){
      return _canAccessSuperData;
    }

    
    public void setCanAccessSuperData(java.lang.Boolean value){
        checkAllowChange();
        
        this._canAccessSuperData = value;
           
    }

    
    /**
     * 
     * xml name: cells
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiFormCellModel> getCells(){
      return _cells;
    }

    
    public void setCells(java.util.List<io.nop.xui.model.UiFormCellModel> value){
        checkAllowChange();
        
        this._cells = KeyedList.fromList(value, io.nop.xui.model.UiFormCellModel::getId);
           
    }

    
    public io.nop.xui.model.UiFormCellModel getCell(String name){
        return this._cells.getByKey(name);
    }

    public boolean hasCell(String name){
        return this._cells.containsKey(name);
    }

    public void addCell(io.nop.xui.model.UiFormCellModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiFormCellModel> list = this.getCells();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiFormCellModel::getId);
            setCells(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_cells(){
        return this._cells.keySet();
    }

    public boolean hasCells(){
        return !this._cells.isEmpty();
    }
    
    /**
     * 
     * xml name: checkInterval
     *  轮询请求的时间间隔，默认为 3 秒。设置 asyncApi 才有效
     */
    
    public java.lang.Integer getCheckInterval(){
      return _checkInterval;
    }

    
    public void setCheckInterval(java.lang.Integer value){
        checkAllowChange();
        
        this._checkInterval = value;
           
    }

    
    /**
     * 
     * xml name: className
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
    }

    
    /**
     * 
     * xml name: data
     *  表单的静态初始化数据
     */
    
    public java.lang.Object getData(){
      return _data;
    }

    
    public void setData(java.lang.Object value){
        checkAllowChange();
        
        this._data = value;
           
    }

    
    /**
     * 
     * xml name: defaultColumnRatio
     *  
     */
    
    public java.lang.Integer getDefaultColumnRatio(){
      return _defaultColumnRatio;
    }

    
    public void setDefaultColumnRatio(java.lang.Integer value){
        checkAllowChange();
        
        this._defaultColumnRatio = value;
           
    }

    
    /**
     * 控件编辑模式
     * xml name: editMode
     *  edit:编辑模式, add:新增模式, view:查看模式, query:查询条件。不同的模式下使用的控件可能不同
     */
    
    public java.lang.String getEditMode(){
      return _editMode;
    }

    
    public void setEditMode(java.lang.String value){
        checkAllowChange();
        
        this._editMode = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: inheritData
     *  
     */
    
    public java.lang.Boolean getInheritData(){
      return _inheritData;
    }

    
    public void setInheritData(java.lang.Boolean value){
        checkAllowChange();
        
        this._inheritData = value;
           
    }

    
    /**
     * 
     * xml name: initApi
     *  <saveFailed xdef:value="string" />
     */
    
    public io.nop.xui.model.UiApiModel getInitApi(){
      return _initApi;
    }

    
    public void setInitApi(io.nop.xui.model.UiApiModel value){
        checkAllowChange();
        
        this._initApi = value;
           
    }

    
    /**
     * 
     * xml name: initAsyncApi
     *  Form 用来获取初始数据的 api,与 initApi 不同的是，会一直轮询请求该接口，直到返回 finished 属性为 true 才 结束。
     */
    
    public io.nop.xui.model.UiApiModel getInitAsyncApi(){
      return _initAsyncApi;
    }

    
    public void setInitAsyncApi(io.nop.xui.model.UiApiModel value){
        checkAllowChange();
        
        this._initAsyncApi = value;
           
    }

    
    /**
     * 
     * xml name: initCheckInterval
     *  设置了 initAsyncApi 以后，默认拉取的时间间隔
     */
    
    public java.lang.Integer getInitCheckInterval(){
      return _initCheckInterval;
    }

    
    public void setInitCheckInterval(java.lang.Integer value){
        checkAllowChange();
        
        this._initCheckInterval = value;
           
    }

    
    /**
     * 
     * xml name: initFetch
     *  设置了 initApi 或者 initAsyncApi 后，默认会开始就发请求，设置为 false 后就不会起始就请求接口
     */
    
    public java.lang.Boolean getInitFetch(){
      return _initFetch;
    }

    
    public void setInitFetch(java.lang.Boolean value){
        checkAllowChange();
        
        this._initFetch = value;
           
    }

    
    /**
     * 
     * xml name: initFetchOn
     *  用表达式来配置
     */
    
    public java.lang.String getInitFetchOn(){
      return _initFetchOn;
    }

    
    public void setInitFetchOn(java.lang.String value){
        checkAllowChange();
        
        this._initFetchOn = value;
           
    }

    
    /**
     * 
     * xml name: interval
     *  initApi的调用间隔。Form 支持轮询初始化接口。
     */
    
    public java.lang.Integer getInterval(){
      return _interval;
    }

    
    public void setInterval(java.lang.Integer value){
        checkAllowChange();
        
        this._interval = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: labelAlign
     *  
     */
    
    public java.lang.String getLabelAlign(){
      return _labelAlign;
    }

    
    public void setLabelAlign(java.lang.String value){
        checkAllowChange();
        
        this._labelAlign = value;
           
    }

    
    /**
     * 
     * xml name: labelWidth
     *  
     */
    
    public java.lang.String getLabelWidth(){
      return _labelWidth;
    }

    
    public void setLabelWidth(java.lang.String value){
        checkAllowChange();
        
        this._labelWidth = value;
           
    }

    
    /**
     * 
     * xml name: layout
     *  
     */
    
    public io.nop.xlang.xmeta.layout.LayoutModel getLayout(){
      return _layout;
    }

    
    public void setLayout(io.nop.xlang.xmeta.layout.LayoutModel value){
        checkAllowChange();
        
        this._layout = value;
           
    }

    
    /**
     * 
     * xml name: layoutControl
     *  如果设置为wizard，则表示采用向导布局来组织页面。如果设置为tabs，则采用标签页来组织页面。
     */
    
    public java.lang.String getLayoutControl(){
      return _layoutControl;
    }

    
    public void setLayoutControl(java.lang.String value){
        checkAllowChange();
        
        this._layoutControl = value;
           
    }

    
    /**
     * 
     * xml name: layoutMode
     *  
     */
    
    public java.lang.String getLayoutMode(){
      return _layoutMode;
    }

    
    public void setLayoutMode(java.lang.String value){
        checkAllowChange();
        
        this._layoutMode = value;
           
    }

    
    /**
     * 
     * xml name: messages
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.String> getMessages(){
      return _messages;
    }

    
    public void setMessages(java.util.Map<java.lang.String,java.lang.String> value){
        checkAllowChange();
        
        this._messages = value;
           
    }

    
    public boolean hasMessages(){
        return this._messages != null && !this._messages.isEmpty();
    }
    
    /**
     * 
     * xml name: objMeta
     *  
     */
    
    public java.lang.String getObjMeta(){
      return _objMeta;
    }

    
    public void setObjMeta(java.lang.String value){
        checkAllowChange();
        
        this._objMeta = value;
           
    }

    
    /**
     * 
     * xml name: panelClassName
     *  
     */
    
    public java.lang.String getPanelClassName(){
      return _panelClassName;
    }

    
    public void setPanelClassName(java.lang.String value){
        checkAllowChange();
        
        this._panelClassName = value;
           
    }

    
    /**
     * 
     * xml name: persistData
     *  表单默认在重置之后（切换页面、弹框中表单关闭表单），会自动清空掉表单中的所有数据，
     * 如果你想持久化保留当前表单项的数据而不清空它，那么通过 Form 配置 persistData: "xxx"，指定一个 key ，来实现数据持久化保存
     */
    
    public java.lang.String getPersistData(){
      return _persistData;
    }

    
    public void setPersistData(java.lang.String value){
        checkAllowChange();
        
        this._persistData = value;
           
    }

    
    /**
     * 
     * xml name: persistDataKeys
     *  
     */
    
    public java.util.Set<java.lang.String> getPersistDataKeys(){
      return _persistDataKeys;
    }

    
    public void setPersistDataKeys(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._persistDataKeys = value;
           
    }

    
    /**
     * 
     * xml name: preventEnterSubmit
     *  表单默认情况下回车就会提交，如果想阻止这个行为，可以设置为false
     */
    
    public java.lang.Boolean getPreventEnterSubmit(){
      return _preventEnterSubmit;
    }

    
    public void setPreventEnterSubmit(java.lang.Boolean value){
        checkAllowChange();
        
        this._preventEnterSubmit = value;
           
    }

    
    /**
     * 
     * xml name: promptPageLeave
     *  
     */
    
    public java.lang.Boolean getPromptPageLeave(){
      return _promptPageLeave;
    }

    
    public void setPromptPageLeave(java.lang.Boolean value){
        checkAllowChange();
        
        this._promptPageLeave = value;
           
    }

    
    /**
     * 
     * xml name: redirect
     *  
     */
    
    public java.lang.String getRedirect(){
      return _redirect;
    }

    
    public void setRedirect(java.lang.String value){
        checkAllowChange();
        
        this._redirect = value;
           
    }

    
    /**
     * 
     * xml name: reload
     *  
     */
    
    public java.lang.String getReload(){
      return _reload;
    }

    
    public void setReload(java.lang.String value){
        checkAllowChange();
        
        this._reload = value;
           
    }

    
    /**
     * 
     * xml name: renderer
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getRenderer(){
      return _renderer;
    }

    
    public void setRenderer(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._renderer = value;
           
    }

    
    /**
     * 
     * xml name: resetAfterSubmit
     *  提交表单成功后，重置当前表单至初始状态
     */
    
    public java.lang.Boolean getResetAfterSubmit(){
      return _resetAfterSubmit;
    }

    
    public void setResetAfterSubmit(java.lang.Boolean value){
        checkAllowChange();
        
        this._resetAfterSubmit = value;
           
    }

    
    /**
     * 
     * xml name: rules
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiFormRuleModel> getRules(){
      return _rules;
    }

    
    public void setRules(java.util.List<io.nop.xui.model.UiFormRuleModel> value){
        checkAllowChange();
        
        this._rules = KeyedList.fromList(value, io.nop.xui.model.UiFormRuleModel::getId);
           
    }

    
    public io.nop.xui.model.UiFormRuleModel getRule(String name){
        return this._rules.getByKey(name);
    }

    public boolean hasRule(String name){
        return this._rules.containsKey(name);
    }

    public void addRule(io.nop.xui.model.UiFormRuleModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiFormRuleModel> list = this.getRules();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiFormRuleModel::getId);
            setRules(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_rules(){
        return this._rules.keySet();
    }

    public boolean hasRules(){
        return !this._rules.isEmpty();
    }
    
    /**
     * 
     * xml name: selection
     *  
     */
    
    public io.nop.api.core.beans.FieldSelectionBean getSelection(){
      return _selection;
    }

    
    public void setSelection(io.nop.api.core.beans.FieldSelectionBean value){
        checkAllowChange();
        
        this._selection = value;
           
    }

    
    /**
     * 
     * xml name: silentPolling
     *  配置刷新时是否显示加载动画
     */
    
    public java.lang.Boolean getSilentPolling(){
      return _silentPolling;
    }

    
    public void setSilentPolling(java.lang.Boolean value){
        checkAllowChange();
        
        this._silentPolling = value;
           
    }

    
    /**
     * 
     * xml name: size
     *  
     */
    
    public java.lang.String getSize(){
      return _size;
    }

    
    public void setSize(java.lang.String value){
        checkAllowChange();
        
        this._size = value;
           
    }

    
    /**
     * 
     * xml name: stopAutoRefreshWhen
     *  通过表达式来配置停止刷新的条件
     */
    
    public java.lang.String getStopAutoRefreshWhen(){
      return _stopAutoRefreshWhen;
    }

    
    public void setStopAutoRefreshWhen(java.lang.String value){
        checkAllowChange();
        
        this._stopAutoRefreshWhen = value;
           
    }

    
    /**
     * 
     * xml name: submitOnChange
     *  
     */
    
    public java.lang.Boolean getSubmitOnChange(){
      return _submitOnChange;
    }

    
    public void setSubmitOnChange(java.lang.Boolean value){
        checkAllowChange();
        
        this._submitOnChange = value;
           
    }

    
    /**
     * 
     * xml name: submitOnInit
     *  
     */
    
    public java.lang.Boolean getSubmitOnInit(){
      return _submitOnInit;
    }

    
    public void setSubmitOnInit(java.lang.Boolean value){
        checkAllowChange();
        
        this._submitOnInit = value;
           
    }

    
    /**
     * 
     * xml name: submitText
     *  
     */
    
    public java.lang.String getSubmitText(){
      return _submitText;
    }

    
    public void setSubmitText(java.lang.String value){
        checkAllowChange();
        
        this._submitText = value;
           
    }

    
    /**
     * 
     * xml name: target
     *  
     */
    
    public java.lang.String getTarget(){
      return _target;
    }

    
    public void setTarget(java.lang.String value){
        checkAllowChange();
        
        this._target = value;
           
    }

    
    /**
     * 
     * xml name: title
     *  表单作为弹出页面使用时对应的对话框标题
     */
    
    public java.lang.String getTitle(){
      return _title;
    }

    
    public void setTitle(java.lang.String value){
        checkAllowChange();
        
        this._title = value;
           
    }

    
    /**
     * 
     * xml name: wrapWithPanel
     *  
     */
    
    public java.lang.Boolean getWrapWithPanel(){
      return _wrapWithPanel;
    }

    
    public void setWrapWithPanel(java.lang.Boolean value){
        checkAllowChange();
        
        this._wrapWithPanel = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._api = io.nop.api.core.util.FreezeHelper.deepFreeze(this._api);
            
           this._asyncApi = io.nop.api.core.util.FreezeHelper.deepFreeze(this._asyncApi);
            
           this._cells = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cells);
            
           this._initApi = io.nop.api.core.util.FreezeHelper.deepFreeze(this._initApi);
            
           this._initAsyncApi = io.nop.api.core.util.FreezeHelper.deepFreeze(this._initAsyncApi);
            
           this._messages = io.nop.api.core.util.FreezeHelper.deepFreeze(this._messages);
            
           this._rules = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rules);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("api",this.getApi());
        out.putNotNull("asyncApi",this.getAsyncApi());
        out.putNotNull("bodyClassName",this.getBodyClassName());
        out.putNotNull("canAccessSuperData",this.getCanAccessSuperData());
        out.putNotNull("cells",this.getCells());
        out.putNotNull("checkInterval",this.getCheckInterval());
        out.putNotNull("className",this.getClassName());
        out.putNotNull("data",this.getData());
        out.putNotNull("defaultColumnRatio",this.getDefaultColumnRatio());
        out.putNotNull("editMode",this.getEditMode());
        out.putNotNull("id",this.getId());
        out.putNotNull("inheritData",this.getInheritData());
        out.putNotNull("initApi",this.getInitApi());
        out.putNotNull("initAsyncApi",this.getInitAsyncApi());
        out.putNotNull("initCheckInterval",this.getInitCheckInterval());
        out.putNotNull("initFetch",this.getInitFetch());
        out.putNotNull("initFetchOn",this.getInitFetchOn());
        out.putNotNull("interval",this.getInterval());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("labelAlign",this.getLabelAlign());
        out.putNotNull("labelWidth",this.getLabelWidth());
        out.putNotNull("layout",this.getLayout());
        out.putNotNull("layoutControl",this.getLayoutControl());
        out.putNotNull("layoutMode",this.getLayoutMode());
        out.putNotNull("messages",this.getMessages());
        out.putNotNull("objMeta",this.getObjMeta());
        out.putNotNull("panelClassName",this.getPanelClassName());
        out.putNotNull("persistData",this.getPersistData());
        out.putNotNull("persistDataKeys",this.getPersistDataKeys());
        out.putNotNull("preventEnterSubmit",this.getPreventEnterSubmit());
        out.putNotNull("promptPageLeave",this.getPromptPageLeave());
        out.putNotNull("redirect",this.getRedirect());
        out.putNotNull("reload",this.getReload());
        out.putNotNull("renderer",this.getRenderer());
        out.putNotNull("resetAfterSubmit",this.getResetAfterSubmit());
        out.putNotNull("rules",this.getRules());
        out.putNotNull("selection",this.getSelection());
        out.putNotNull("silentPolling",this.getSilentPolling());
        out.putNotNull("size",this.getSize());
        out.putNotNull("stopAutoRefreshWhen",this.getStopAutoRefreshWhen());
        out.putNotNull("submitOnChange",this.getSubmitOnChange());
        out.putNotNull("submitOnInit",this.getSubmitOnInit());
        out.putNotNull("submitText",this.getSubmitText());
        out.putNotNull("target",this.getTarget());
        out.putNotNull("title",this.getTitle());
        out.putNotNull("wrapWithPanel",this.getWrapWithPanel());
    }

    public UiFormModel cloneInstance(){
        UiFormModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiFormModel instance){
        super.copyTo(instance);
        
        instance.setApi(this.getApi());
        instance.setAsyncApi(this.getAsyncApi());
        instance.setBodyClassName(this.getBodyClassName());
        instance.setCanAccessSuperData(this.getCanAccessSuperData());
        instance.setCells(this.getCells());
        instance.setCheckInterval(this.getCheckInterval());
        instance.setClassName(this.getClassName());
        instance.setData(this.getData());
        instance.setDefaultColumnRatio(this.getDefaultColumnRatio());
        instance.setEditMode(this.getEditMode());
        instance.setId(this.getId());
        instance.setInheritData(this.getInheritData());
        instance.setInitApi(this.getInitApi());
        instance.setInitAsyncApi(this.getInitAsyncApi());
        instance.setInitCheckInterval(this.getInitCheckInterval());
        instance.setInitFetch(this.getInitFetch());
        instance.setInitFetchOn(this.getInitFetchOn());
        instance.setInterval(this.getInterval());
        instance.setLabel(this.getLabel());
        instance.setLabelAlign(this.getLabelAlign());
        instance.setLabelWidth(this.getLabelWidth());
        instance.setLayout(this.getLayout());
        instance.setLayoutControl(this.getLayoutControl());
        instance.setLayoutMode(this.getLayoutMode());
        instance.setMessages(this.getMessages());
        instance.setObjMeta(this.getObjMeta());
        instance.setPanelClassName(this.getPanelClassName());
        instance.setPersistData(this.getPersistData());
        instance.setPersistDataKeys(this.getPersistDataKeys());
        instance.setPreventEnterSubmit(this.getPreventEnterSubmit());
        instance.setPromptPageLeave(this.getPromptPageLeave());
        instance.setRedirect(this.getRedirect());
        instance.setReload(this.getReload());
        instance.setRenderer(this.getRenderer());
        instance.setResetAfterSubmit(this.getResetAfterSubmit());
        instance.setRules(this.getRules());
        instance.setSelection(this.getSelection());
        instance.setSilentPolling(this.getSilentPolling());
        instance.setSize(this.getSize());
        instance.setStopAutoRefreshWhen(this.getStopAutoRefreshWhen());
        instance.setSubmitOnChange(this.getSubmitOnChange());
        instance.setSubmitOnInit(this.getSubmitOnInit());
        instance.setSubmitText(this.getSubmitText());
        instance.setTarget(this.getTarget());
        instance.setTitle(this.getTitle());
        instance.setWrapWithPanel(this.getWrapWithPanel());
    }

    protected UiFormModel newInstance(){
        return (UiFormModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
