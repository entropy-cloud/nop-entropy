package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.ModelClassConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/model-class.xdef <p>
 * 模型按"级别/类"组织：每个模型类 = 逻辑路由组，包含一组候选（备选模型 + 账号组合）。
 * 请求携带的模型映射到某模型类后，在该类候选集内游走——不是全局一组账号（设计 §3.3）。
 * 候选集可跨 provider（引用 {provider}.llm.xml 账号链）、跨账号（<accounts> 账号链）、跨模型
 * （同类候选可含多个 model）。
 * opt-in：配置文件 _vfs/nop/ai/llm/_default.model-class.xml。缺省（无文件）= 无路由组 =
 * 零回归（沿用既有单 provider 行为）。请求 model 未归属任何类（成员清单未命中）同样解析为
 * "无路由组"（零回归）。未知 accountRef（引用的账号 id 不在 {provider}.llm.xml <accounts> 中）
 * 与未知 provider 在解析期 fail-fast（不静默吞——Minimum Rules #24）。
 * 候选展开语义（解析期，LlmConfigHelper 家族）：
 * - accountRef 未配置：候选展开为"主账号 + 该 provider 有序账号链"（主账号在前）；
 * - accountRef 配置：只展开为该账号（未知 id 解析期 fail-fast）；
 * - model 未配置：使用该 provider 的 defaultModel。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ModelClassConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: modelClasses
     * 
     */
    private KeyedList<io.nop.ai.core.model.ModelClassModel> _modelClasses = KeyedList.emptyList();
    
    /**
     * 
     * xml name: modelClasses
     *  
     */
    
    public java.util.List<io.nop.ai.core.model.ModelClassModel> getModelClasses(){
      return _modelClasses;
    }

    
    public void setModelClasses(java.util.List<io.nop.ai.core.model.ModelClassModel> value){
        checkAllowChange();
        
        this._modelClasses = KeyedList.fromList(value, io.nop.ai.core.model.ModelClassModel::getId);
           
    }

    
    public io.nop.ai.core.model.ModelClassModel getModelClass(String name){
        return this._modelClasses.getByKey(name);
    }

    public boolean hasModelClass(String name){
        return this._modelClasses.containsKey(name);
    }

    public void addModelClass(io.nop.ai.core.model.ModelClassModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.ModelClassModel> list = this.getModelClasses();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.ModelClassModel::getId);
            setModelClasses(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_modelClasses(){
        return this._modelClasses.keySet();
    }

    public boolean hasModelClasses(){
        return !this._modelClasses.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._modelClasses = io.nop.api.core.util.FreezeHelper.deepFreeze(this._modelClasses);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("modelClasses",this.getModelClasses());
    }

    public ModelClassConfig cloneInstance(){
        ModelClassConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ModelClassConfig instance){
        super.copyTo(instance);
        
        instance.setModelClasses(this.getModelClasses());
    }

    protected ModelClassConfig newInstance(){
        return (ModelClassConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
