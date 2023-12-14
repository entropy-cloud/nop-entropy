//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.ClassDefinition;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ClassDefinition extends io.nop.xlang.ast.DecoratedDeclaration {
    
    protected java.util.List<io.nop.xlang.ast.ClassDefinition> classDefinitions;
    
    protected io.nop.xlang.ast.XLangClassKind classKind;
    
    protected io.nop.xlang.ast.ParameterizedTypeNode extendsType;
    
    protected java.util.List<io.nop.xlang.ast.FieldDeclaration> fields;
    
    protected java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> implementTypes;
    
    protected java.util.List<io.nop.xlang.ast.FunctionDeclaration> methods;
    
    protected io.nop.xlang.ast.Identifier name;
    
    protected java.util.List<io.nop.xlang.ast.TypeParameterNode> typeParams;
    

    public _ClassDefinition(){
    }

    
    public java.util.List<io.nop.xlang.ast.ClassDefinition> getClassDefinitions(){
        return classDefinitions;
    }

    public void setClassDefinitions(java.util.List<io.nop.xlang.ast.ClassDefinition> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.classDefinitions = value;
    }
    
    public java.util.List<io.nop.xlang.ast.ClassDefinition> makeClassDefinitions(){
        java.util.List<io.nop.xlang.ast.ClassDefinition> list = getClassDefinitions();
        if(list == null){
            list = new java.util.ArrayList<>();
            setClassDefinitions(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.XLangClassKind getClassKind(){
        return classKind;
    }

    public void setClassKind(io.nop.xlang.ast.XLangClassKind value){
        checkAllowChange();
        
        this.classKind = value;
    }
    
    public io.nop.xlang.ast.ParameterizedTypeNode getExtendsType(){
        return extendsType;
    }

    public void setExtendsType(io.nop.xlang.ast.ParameterizedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.extendsType = value;
    }
    
    public java.util.List<io.nop.xlang.ast.FieldDeclaration> getFields(){
        return fields;
    }

    public void setFields(java.util.List<io.nop.xlang.ast.FieldDeclaration> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.fields = value;
    }
    
    public java.util.List<io.nop.xlang.ast.FieldDeclaration> makeFields(){
        java.util.List<io.nop.xlang.ast.FieldDeclaration> list = getFields();
        if(list == null){
            list = new java.util.ArrayList<>();
            setFields(list);
        }
        return list;
    }
    
    public java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> getImplementTypes(){
        return implementTypes;
    }

    public void setImplementTypes(java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.implementTypes = value;
    }
    
    public java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> makeImplementTypes(){
        java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> list = getImplementTypes();
        if(list == null){
            list = new java.util.ArrayList<>();
            setImplementTypes(list);
        }
        return list;
    }
    
    public java.util.List<io.nop.xlang.ast.FunctionDeclaration> getMethods(){
        return methods;
    }

    public void setMethods(java.util.List<io.nop.xlang.ast.FunctionDeclaration> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.methods = value;
    }
    
    public java.util.List<io.nop.xlang.ast.FunctionDeclaration> makeMethods(){
        java.util.List<io.nop.xlang.ast.FunctionDeclaration> list = getMethods();
        if(list == null){
            list = new java.util.ArrayList<>();
            setMethods(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.Identifier getName(){
        return name;
    }

    public void setName(io.nop.xlang.ast.Identifier value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.name = value;
    }
    
    public java.util.List<io.nop.xlang.ast.TypeParameterNode> getTypeParams(){
        return typeParams;
    }

    public void setTypeParams(java.util.List<io.nop.xlang.ast.TypeParameterNode> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.typeParams = value;
    }
    
    public java.util.List<io.nop.xlang.ast.TypeParameterNode> makeTypeParams(){
        java.util.List<io.nop.xlang.ast.TypeParameterNode> list = getTypeParams();
        if(list == null){
            list = new java.util.ArrayList<>();
            setTypeParams(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("classDefinitions",getClassDefinitions());
       
          checkMandatory("classKind",getClassKind());
       
          checkMandatory("fields",getFields());
       
          checkMandatory("methods",getMethods());
       
          checkMandatory("name",getName());
       
    }


    public ClassDefinition newInstance(){
      return new ClassDefinition();
    }

    @Override
    public ClassDefinition deepClone(){
       ClassDefinition ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(decorators != null){
                  
                          ret.setDecorators(decorators.deepClone());
                      
                }
            
                if(name != null){
                  
                          ret.setName(name.deepClone());
                      
                }
            
                if(classKind != null){
                  
                          ret.setClassKind(classKind);
                      
                }
            
                if(typeParams != null){
                  
                          java.util.List<io.nop.xlang.ast.TypeParameterNode> copy_typeParams = new java.util.ArrayList<>(typeParams.size());
                          for(io.nop.xlang.ast.TypeParameterNode item: typeParams){
                              copy_typeParams.add(item.deepClone());
                          }
                          ret.setTypeParams(copy_typeParams);
                      
                }
            
                if(extendsType != null){
                  
                          ret.setExtendsType(extendsType.deepClone());
                      
                }
            
                if(implementTypes != null){
                  
                          java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> copy_implementTypes = new java.util.ArrayList<>(implementTypes.size());
                          for(io.nop.xlang.ast.ParameterizedTypeNode item: implementTypes){
                              copy_implementTypes.add(item.deepClone());
                          }
                          ret.setImplementTypes(copy_implementTypes);
                      
                }
            
                if(fields != null){
                  
                          java.util.List<io.nop.xlang.ast.FieldDeclaration> copy_fields = new java.util.ArrayList<>(fields.size());
                          for(io.nop.xlang.ast.FieldDeclaration item: fields){
                              copy_fields.add(item.deepClone());
                          }
                          ret.setFields(copy_fields);
                      
                }
            
                if(methods != null){
                  
                          java.util.List<io.nop.xlang.ast.FunctionDeclaration> copy_methods = new java.util.ArrayList<>(methods.size());
                          for(io.nop.xlang.ast.FunctionDeclaration item: methods){
                              copy_methods.add(item.deepClone());
                          }
                          ret.setMethods(copy_methods);
                      
                }
            
                if(classDefinitions != null){
                  
                          java.util.List<io.nop.xlang.ast.ClassDefinition> copy_classDefinitions = new java.util.ArrayList<>(classDefinitions.size());
                          for(io.nop.xlang.ast.ClassDefinition item: classDefinitions){
                              copy_classDefinitions.add(item.deepClone());
                          }
                          ret.setClassDefinitions(copy_classDefinitions);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(decorators != null)
                processor.accept(decorators);
        
            if(name != null)
                processor.accept(name);
        
            if(typeParams != null){
               for(io.nop.xlang.ast.TypeParameterNode child: typeParams){
                    processor.accept(child);
                }
            }
            if(extendsType != null)
                processor.accept(extendsType);
        
            if(implementTypes != null){
               for(io.nop.xlang.ast.ParameterizedTypeNode child: implementTypes){
                    processor.accept(child);
                }
            }
            if(fields != null){
               for(io.nop.xlang.ast.FieldDeclaration child: fields){
                    processor.accept(child);
                }
            }
            if(methods != null){
               for(io.nop.xlang.ast.FunctionDeclaration child: methods){
                    processor.accept(child);
                }
            }
            if(classDefinitions != null){
               for(io.nop.xlang.ast.ClassDefinition child: classDefinitions){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(decorators != null && processor.apply(decorators) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(name != null && processor.apply(name) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(typeParams != null){
               for(io.nop.xlang.ast.TypeParameterNode child: typeParams){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(extendsType != null && processor.apply(extendsType) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(implementTypes != null){
               for(io.nop.xlang.ast.ParameterizedTypeNode child: implementTypes){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(fields != null){
               for(io.nop.xlang.ast.FieldDeclaration child: fields){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(methods != null){
               for(io.nop.xlang.ast.FunctionDeclaration child: methods){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(classDefinitions != null){
               for(io.nop.xlang.ast.ClassDefinition child: classDefinitions){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.decorators == oldChild){
               this.setDecorators((io.nop.xlang.ast.Decorators)newChild);
               return true;
            }
        
            if(this.name == oldChild){
               this.setName((io.nop.xlang.ast.Identifier)newChild);
               return true;
            }
        
            if(this.typeParams != null){
               int index = this.typeParams.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.TypeParameterNode> list = this.replaceInList(this.typeParams,index,newChild);
                   this.setTypeParams(list);
                   return true;
               }
            }
            if(this.extendsType == oldChild){
               this.setExtendsType((io.nop.xlang.ast.ParameterizedTypeNode)newChild);
               return true;
            }
        
            if(this.implementTypes != null){
               int index = this.implementTypes.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> list = this.replaceInList(this.implementTypes,index,newChild);
                   this.setImplementTypes(list);
                   return true;
               }
            }
            if(this.fields != null){
               int index = this.fields.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.FieldDeclaration> list = this.replaceInList(this.fields,index,newChild);
                   this.setFields(list);
                   return true;
               }
            }
            if(this.methods != null){
               int index = this.methods.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.FunctionDeclaration> list = this.replaceInList(this.methods,index,newChild);
                   this.setMethods(list);
                   return true;
               }
            }
            if(this.classDefinitions != null){
               int index = this.classDefinitions.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ClassDefinition> list = this.replaceInList(this.classDefinitions,index,newChild);
                   this.setClassDefinitions(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.decorators == child){
                this.setDecorators(null);
                return true;
            }
        
            if(this.name == child){
                this.setName(null);
                return true;
            }
        
            if(this.typeParams != null){
               int index = this.typeParams.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.TypeParameterNode> list = this.removeInList(this.typeParams,index);
                   this.setTypeParams(list);
                   return true;
               }
            }
            if(this.extendsType == child){
                this.setExtendsType(null);
                return true;
            }
        
            if(this.implementTypes != null){
               int index = this.implementTypes.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ParameterizedTypeNode> list = this.removeInList(this.implementTypes,index);
                   this.setImplementTypes(list);
                   return true;
               }
            }
            if(this.fields != null){
               int index = this.fields.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.FieldDeclaration> list = this.removeInList(this.fields,index);
                   this.setFields(list);
                   return true;
               }
            }
            if(this.methods != null){
               int index = this.methods.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.FunctionDeclaration> list = this.removeInList(this.methods,index);
                   this.setMethods(list);
                   return true;
               }
            }
            if(this.classDefinitions != null){
               int index = this.classDefinitions.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.ClassDefinition> list = this.removeInList(this.classDefinitions,index);
                   this.setClassDefinitions(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    ClassDefinition other = (ClassDefinition)node;
    
            if(!isNodeEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
        
            if(!isNodeEquivalent(this.name,other.getName())){
               return false;
            }
        
                if(!isValueEquivalent(this.classKind,other.getClassKind())){
                   return false;
                }
            
            if(isListEquivalent(this.typeParams,other.getTypeParams())){
               return false;
            }
            if(!isNodeEquivalent(this.extendsType,other.getExtendsType())){
               return false;
            }
        
            if(isListEquivalent(this.implementTypes,other.getImplementTypes())){
               return false;
            }
            if(isListEquivalent(this.fields,other.getFields())){
               return false;
            }
            if(isListEquivalent(this.methods,other.getMethods())){
               return false;
            }
            if(isListEquivalent(this.classDefinitions,other.getClassDefinitions())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.ClassDefinition;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              json.put("decorators", decorators);
                          
                    }
                
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(classKind != null){
                      
                              json.put("classKind", classKind);
                          
                    }
                
                    if(typeParams != null){
                      
                              if(!typeParams.isEmpty())
                                json.put("typeParams", typeParams);
                          
                    }
                
                    if(extendsType != null){
                      
                              json.put("extendsType", extendsType);
                          
                    }
                
                    if(implementTypes != null){
                      
                              if(!implementTypes.isEmpty())
                                json.put("implementTypes", implementTypes);
                          
                    }
                
                    if(fields != null){
                      
                              if(!fields.isEmpty())
                                json.put("fields", fields);
                          
                    }
                
                    if(methods != null){
                      
                              if(!methods.isEmpty())
                                json.put("methods", methods);
                          
                    }
                
                    if(classDefinitions != null){
                      
                              if(!classDefinitions.isEmpty())
                                json.put("classDefinitions", classDefinitions);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(decorators != null)
                    decorators.freeze(cascade);
                if(name != null)
                    name.freeze(cascade);
                typeParams = io.nop.api.core.util.FreezeHelper.freezeList(typeParams,cascade);         
                if(extendsType != null)
                    extendsType.freeze(cascade);
                implementTypes = io.nop.api.core.util.FreezeHelper.freezeList(implementTypes,cascade);         
                fields = io.nop.api.core.util.FreezeHelper.freezeList(fields,cascade);         
                methods = io.nop.api.core.util.FreezeHelper.freezeList(methods,cascade);         
                classDefinitions = io.nop.api.core.util.FreezeHelper.freezeList(classDefinitions,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
