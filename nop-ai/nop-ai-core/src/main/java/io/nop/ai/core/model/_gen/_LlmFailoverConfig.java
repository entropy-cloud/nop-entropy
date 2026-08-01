package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmFailoverConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm-failover.xdef <p>
 * 单一全局有序 provider 优先级表（P1→P2→P3）。每条 provider 引用既有 {provider}.llm.xml 单 provider 配置文件。
 * failover 恒向优先级更低的方向游走（只取 primary 之后），故环不可能由声明构造——状态模型最简。
 * opt-in：配置文件 _vfs/nop/ai/llm/_default.llm-failover.xml。缺省（无文件）= 无 provider 链 = 零回归 fail-loud
 * （账号链耗尽仍按今日行为 fail-loud，设计 §6.9）。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmFailoverConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: providers
     * 
     */
    private KeyedList<io.nop.ai.core.model.LlmFailoverProviderModel> _providers = KeyedList.emptyList();
    
    /**
     * 
     * xml name: providers
     *  
     */
    
    public java.util.List<io.nop.ai.core.model.LlmFailoverProviderModel> getProviders(){
      return _providers;
    }

    
    public void setProviders(java.util.List<io.nop.ai.core.model.LlmFailoverProviderModel> value){
        checkAllowChange();
        
        this._providers = KeyedList.fromList(value, io.nop.ai.core.model.LlmFailoverProviderModel::getProvider);
           
    }

    
    public io.nop.ai.core.model.LlmFailoverProviderModel getProvider(String name){
        return this._providers.getByKey(name);
    }

    public boolean hasProvider(String name){
        return this._providers.containsKey(name);
    }

    public void addProvider(io.nop.ai.core.model.LlmFailoverProviderModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.LlmFailoverProviderModel> list = this.getProviders();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.LlmFailoverProviderModel::getProvider);
            setProviders(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_providers(){
        return this._providers.keySet();
    }

    public boolean hasProviders(){
        return !this._providers.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._providers = io.nop.api.core.util.FreezeHelper.deepFreeze(this._providers);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("providers",this.getProviders());
    }

    public LlmFailoverConfig cloneInstance(){
        LlmFailoverConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmFailoverConfig instance){
        super.copyTo(instance);
        
        instance.setProviders(this.getProviders());
    }

    protected LlmFailoverConfig newInstance(){
        return (LlmFailoverConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
