package io.nop.xui.graph_designer._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.graph_designer.GraphDesignerModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [6:2:0:0]/nop/schema/designer/graph-designer.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GraphDesignerModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actions
     * 
     */
    private KeyedList<io.nop.xui.model.IUiActionModel> _actions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: codeGenLib
     * 
     */
    private java.lang.String _codeGenLib ;
    
    /**
     *  
     * xml name: edges
     * 
     */
    private KeyedList<io.nop.xui.graph_designer.GraphDesignerEdgeModel> _edges = KeyedList.emptyList();
    
    /**
     *  编辑器类型
     * xml name: editorType
     * 比如flow-builder对应于类似钉钉的审批流编辑
     */
    private java.lang.String _editorType ;
    
    /**
     *  
     * xml name: forms
     * 
     */
    private KeyedList<io.nop.xui.model.UiFormModel> _forms = KeyedList.emptyList();
    
    /**
     *  
     * xml name: nodes
     * 
     */
    private KeyedList<io.nop.xui.graph_designer.GraphDesignerNodeModel> _nodes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: script
     * 
     */
    private java.lang.String _script ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private java.lang.String _style ;
    
    /**
     *  
     * xml name: zoom
     * 
     */
    private io.nop.xui.graph_designer.GraphDesignerZoomModel _zoom ;
    
    /**
     * 
     * xml name: actions
     *  
     */
    
    public java.util.List<io.nop.xui.model.IUiActionModel> getActions(){
      return _actions;
    }

    
    public void setActions(java.util.List<io.nop.xui.model.IUiActionModel> value){
        checkAllowChange();
        
        this._actions = KeyedList.fromList(value, io.nop.xui.model.IUiActionModel::getId);
           
    }

    
    public java.util.Set<String> keySet_actions(){
        return this._actions.keySet();
    }

    public boolean hasActions(){
        return !this._actions.isEmpty();
    }
    
    /**
     * 
     * xml name: codeGenLib
     *  
     */
    
    public java.lang.String getCodeGenLib(){
      return _codeGenLib;
    }

    
    public void setCodeGenLib(java.lang.String value){
        checkAllowChange();
        
        this._codeGenLib = value;
           
    }

    
    /**
     * 
     * xml name: edges
     *  
     */
    
    public java.util.List<io.nop.xui.graph_designer.GraphDesignerEdgeModel> getEdges(){
      return _edges;
    }

    
    public void setEdges(java.util.List<io.nop.xui.graph_designer.GraphDesignerEdgeModel> value){
        checkAllowChange();
        
        this._edges = KeyedList.fromList(value, io.nop.xui.graph_designer.GraphDesignerEdgeModel::getName);
           
    }

    
    public io.nop.xui.graph_designer.GraphDesignerEdgeModel getEdge(String name){
        return this._edges.getByKey(name);
    }

    public boolean hasEdge(String name){
        return this._edges.containsKey(name);
    }

    public void addEdge(io.nop.xui.graph_designer.GraphDesignerEdgeModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.graph_designer.GraphDesignerEdgeModel> list = this.getEdges();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.graph_designer.GraphDesignerEdgeModel::getName);
            setEdges(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_edges(){
        return this._edges.keySet();
    }

    public boolean hasEdges(){
        return !this._edges.isEmpty();
    }
    
    /**
     * 编辑器类型
     * xml name: editorType
     *  比如flow-builder对应于类似钉钉的审批流编辑
     */
    
    public java.lang.String getEditorType(){
      return _editorType;
    }

    
    public void setEditorType(java.lang.String value){
        checkAllowChange();
        
        this._editorType = value;
           
    }

    
    /**
     * 
     * xml name: forms
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiFormModel> getForms(){
      return _forms;
    }

    
    public void setForms(java.util.List<io.nop.xui.model.UiFormModel> value){
        checkAllowChange();
        
        this._forms = KeyedList.fromList(value, io.nop.xui.model.UiFormModel::getId);
           
    }

    
    public io.nop.xui.model.UiFormModel getForm(String name){
        return this._forms.getByKey(name);
    }

    public boolean hasForm(String name){
        return this._forms.containsKey(name);
    }

    public void addForm(io.nop.xui.model.UiFormModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiFormModel> list = this.getForms();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiFormModel::getId);
            setForms(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_forms(){
        return this._forms.keySet();
    }

    public boolean hasForms(){
        return !this._forms.isEmpty();
    }
    
    /**
     * 
     * xml name: nodes
     *  
     */
    
    public java.util.List<io.nop.xui.graph_designer.GraphDesignerNodeModel> getNodes(){
      return _nodes;
    }

    
    public void setNodes(java.util.List<io.nop.xui.graph_designer.GraphDesignerNodeModel> value){
        checkAllowChange();
        
        this._nodes = KeyedList.fromList(value, io.nop.xui.graph_designer.GraphDesignerNodeModel::getName);
           
    }

    
    public io.nop.xui.graph_designer.GraphDesignerNodeModel getNode(String name){
        return this._nodes.getByKey(name);
    }

    public boolean hasNode(String name){
        return this._nodes.containsKey(name);
    }

    public void addNode(io.nop.xui.graph_designer.GraphDesignerNodeModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.graph_designer.GraphDesignerNodeModel> list = this.getNodes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.graph_designer.GraphDesignerNodeModel::getName);
            setNodes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_nodes(){
        return this._nodes.keySet();
    }

    public boolean hasNodes(){
        return !this._nodes.isEmpty();
    }
    
    /**
     * 
     * xml name: script
     *  
     */
    
    public java.lang.String getScript(){
      return _script;
    }

    
    public void setScript(java.lang.String value){
        checkAllowChange();
        
        this._script = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  
     */
    
    public java.lang.String getStyle(){
      return _style;
    }

    
    public void setStyle(java.lang.String value){
        checkAllowChange();
        
        this._style = value;
           
    }

    
    /**
     * 
     * xml name: zoom
     *  
     */
    
    public io.nop.xui.graph_designer.GraphDesignerZoomModel getZoom(){
      return _zoom;
    }

    
    public void setZoom(io.nop.xui.graph_designer.GraphDesignerZoomModel value){
        checkAllowChange();
        
        this._zoom = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._actions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actions);
            
           this._edges = io.nop.api.core.util.FreezeHelper.deepFreeze(this._edges);
            
           this._forms = io.nop.api.core.util.FreezeHelper.deepFreeze(this._forms);
            
           this._nodes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._nodes);
            
           this._zoom = io.nop.api.core.util.FreezeHelper.deepFreeze(this._zoom);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("actions",this.getActions());
        out.putNotNull("codeGenLib",this.getCodeGenLib());
        out.putNotNull("edges",this.getEdges());
        out.putNotNull("editorType",this.getEditorType());
        out.putNotNull("forms",this.getForms());
        out.putNotNull("nodes",this.getNodes());
        out.putNotNull("script",this.getScript());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("zoom",this.getZoom());
    }

    public GraphDesignerModel cloneInstance(){
        GraphDesignerModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GraphDesignerModel instance){
        super.copyTo(instance);
        
        instance.setActions(this.getActions());
        instance.setCodeGenLib(this.getCodeGenLib());
        instance.setEdges(this.getEdges());
        instance.setEditorType(this.getEditorType());
        instance.setForms(this.getForms());
        instance.setNodes(this.getNodes());
        instance.setScript(this.getScript());
        instance.setStyle(this.getStyle());
        instance.setZoom(this.getZoom());
    }

    protected GraphDesignerModel newInstance(){
        return (GraphDesignerModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
