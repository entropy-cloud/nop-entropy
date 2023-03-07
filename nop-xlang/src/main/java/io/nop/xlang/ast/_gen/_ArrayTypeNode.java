//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ArrayTypeNode;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ArrayTypeNode extends io.nop.xlang.ast.NamedTypeNode {
    
    protected io.nop.xlang.ast.NamedTypeNode componentType;
    

    public _ArrayTypeNode(){
    }

    
    public io.nop.xlang.ast.NamedTypeNode getComponentType(){
        return componentType;
    }

    public void setComponentType(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.componentType = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("componentType",getComponentType());
       
    }


    public ArrayTypeNode newInstance(){
      return new ArrayTypeNode();
    }

    @Override
    public ArrayTypeNode deepClone(){
       ArrayTypeNode ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setNotNull(notNull);
            
                if(componentType != null){
                  
                          ret.setComponentType(componentType.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(componentType != null)
                processor.accept(componentType);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(componentType != null && processor.apply(componentType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.componentType == oldChild){
               this.setComponentType((io.nop.xlang.ast.NamedTypeNode)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.componentType == child){
                this.setComponentType(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ArrayTypeNode other = (ArrayTypeNode)node;
    
                if(!isValueEquivalent(this.notNull,other.getNotNull())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.componentType,other.getComponentType())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ArrayTypeNode;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("notNull", notNull);
                
                    if(componentType != null){
                      
                              json.put("componentType", componentType);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(componentType != null)
                    componentType.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
