//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.UnionTypeDef;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _UnionTypeDef extends io.nop.xlang.ast.StructuredTypeDef {
    
    protected java.util.List<io.nop.xlang.ast.NamedTypeNode> types;
    

    public _UnionTypeDef(){
    }

    
    public java.util.List<io.nop.xlang.ast.NamedTypeNode> getTypes(){
        return types;
    }

    public void setTypes(java.util.List<io.nop.xlang.ast.NamedTypeNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.types = value;
    }
    
    public java.util.List<io.nop.xlang.ast.NamedTypeNode> makeTypes(){
        java.util.List<io.nop.xlang.ast.NamedTypeNode> list = getTypes();
        if(list == null){
            list = new java.util.ArrayList<>();
            setTypes(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("types",getTypes());
       
    }


    public UnionTypeDef newInstance(){
      return new UnionTypeDef();
    }

    @Override
    public UnionTypeDef deepClone(){
       UnionTypeDef ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setNotNull(notNull);
            
                if(types != null){
                  
                          java.util.List<io.nop.xlang.ast.NamedTypeNode> copy_types = new java.util.ArrayList<>(types.size());
                          for(io.nop.xlang.ast.NamedTypeNode item: types){
                              copy_types.add(item.deepClone());
                          }
                          ret.setTypes(copy_types);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(types != null){
               for(io.nop.xlang.ast.NamedTypeNode child: types){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(types != null){
               for(io.nop.xlang.ast.NamedTypeNode child: types){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.types != null){
               int index = this.types.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.NamedTypeNode> list = this.replaceInList(this.types,index,newChild);
                   this.setTypes(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.types != null){
               int index = this.types.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.NamedTypeNode> list = this.removeInList(this.types,index);
                   this.setTypes(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    UnionTypeDef other = (UnionTypeDef)node;
    
                if(!isValueEquivalent(this.notNull,other.getNotNull())){
                   return false;
                }
            
            if(isListEquivalent(this.types,other.getTypes())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.UnionTypeDef;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("notNull", notNull);
                
                    if(types != null){
                      
                              if(!types.isEmpty())
                                json.put("types", types);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                types = io.nop.api.core.util.FreezeHelper.freezeList(types,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
