package io.nop.javaparser.delta;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DeltaClassMerger {
    public static DeltaClassMerger INSTANCE = new DeltaClassMerger();

    public ClassOrInterfaceDeclaration merge(ClassOrInterfaceDeclaration a,
                                             ClassOrInterfaceDeclaration b) {
        ClassOrInterfaceDeclaration merged = new ClassOrInterfaceDeclaration();
        merged.setName(a.getNameAsString());

        mergeClassDeclaration(merged, a, b);
        mergeFields(merged, a, b);
        mergeMethods(merged, a, b);

        return merged;
    }

    private void mergeClassDeclaration(ClassOrInterfaceDeclaration merged,
                                       ClassOrInterfaceDeclaration a,
                                       ClassOrInterfaceDeclaration b) {
        DeltaMergeHelper.mergeComment(merged, a, b);
        DeltaMergeHelper.mergeAnnotations(merged, a, b);

        // 合并修饰符 - 取并集
        merged.setModifiers(a.getModifiers());
        b.getModifiers().forEach(modifier -> {
            merged.addModifier(modifier.getKeyword());
        });

        // 合并泛型参数 - b优先
        if (!b.getTypeParameters().isEmpty()) {
            merged.setTypeParameters(b.getTypeParameters());
        } else {
            merged.setTypeParameters(a.getTypeParameters());
        }

        // 合并继承关系 - 取并集
        Set<ClassOrInterfaceType> extendedTypes = new LinkedHashSet<>();
        extendedTypes.addAll(a.getExtendedTypes());
        extendedTypes.addAll(b.getExtendedTypes());
        merged.setExtendedTypes(new NodeList<>(extendedTypes));

        // 合并实现接口
        DeltaMergeHelper.mergeImplementedTypes(merged, a.getImplementedTypes(), b.getImplementedTypes());
    }

    private void mergeFields(ClassOrInterfaceDeclaration merged,
                             ClassOrInterfaceDeclaration a,
                             ClassOrInterfaceDeclaration b) {
        Map<String, FieldDeclaration> aFields = getFieldsByName(a);
        Map<String, FieldDeclaration> bFields = getFieldsByName(b);

        Set<String> allFieldNames = new LinkedHashSet<>();
        allFieldNames.addAll(aFields.keySet());
        allFieldNames.addAll(bFields.keySet());

        for (String fieldName : allFieldNames) {
            FieldDeclaration aField = aFields.get(fieldName);
            FieldDeclaration bField = bFields.get(fieldName);

            if (aField != null && bField != null) {
                FieldDeclaration fieldToAdd = bField.clone();
                if (!DeltaMergeHelper.hasNonEmptyComment(bField) &&
                        DeltaMergeHelper.hasNonEmptyComment(aField)) {
                    fieldToAdd = aField.clone();
                }
                DeltaMergeHelper.mergeAnnotations(fieldToAdd, aField, bField);
                merged.addMember(fieldToAdd);
            } else if (aField != null) {
                merged.addMember(aField.clone());
            } else {
                merged.addMember(bField.clone());
            }
        }
    }

    private void mergeMethods(ClassOrInterfaceDeclaration merged,
                              ClassOrInterfaceDeclaration a,
                              ClassOrInterfaceDeclaration b) {
        Map<String, MethodDeclaration> aMethods = getMethodsBySignature(a);
        Map<String, MethodDeclaration> bMethods = getMethodsBySignature(b);

        Set<String> allMethodSignatures = new LinkedHashSet<>();
        allMethodSignatures.addAll(aMethods.keySet());
        allMethodSignatures.addAll(bMethods.keySet());

        for (String signature : allMethodSignatures) {
            MethodDeclaration aMethod = aMethods.get(signature);
            MethodDeclaration bMethod = bMethods.get(signature);

            if (aMethod != null && bMethod != null) {
                MethodDeclaration methodToAdd = bMethod.clone();
                if (!DeltaMergeHelper.hasNonEmptyComment(bMethod) &&
                        DeltaMergeHelper.hasNonEmptyComment(aMethod)) {
                    methodToAdd = aMethod.clone();
                }
                DeltaMergeHelper.mergeAnnotations(methodToAdd, aMethod, bMethod);
                merged.addMember(methodToAdd);
            } else if (aMethod != null) {
                merged.addMember(aMethod.clone());
            } else {
                merged.addMember(bMethod.clone());
            }
        }
    }

    private Map<String, FieldDeclaration> getFieldsByName(ClassOrInterfaceDeclaration clazz) {
        return clazz.getFields().stream()
                .collect(Collectors.toMap(
                        f -> f.getVariable(0).getNameAsString(),
                        f -> f
                ));
    }

    private Map<String, MethodDeclaration> getMethodsBySignature(ClassOrInterfaceDeclaration clazz) {
        return clazz.getMethods().stream()
                .collect(Collectors.toMap(
                        DeltaMergeHelper::getMethodSignature,
                        m -> m
                ));
    }
}