package io.nop.javaparser.delta;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DeltaEnumMerger {
    public static DeltaEnumMerger INSTANCE = new DeltaEnumMerger();

    public EnumDeclaration merge(EnumDeclaration a, EnumDeclaration b) {
        EnumDeclaration merged = new EnumDeclaration();
        merged.setName(a.getNameAsString());

        mergeClassDeclaration(merged, a, b);
        mergeEnumConstants(merged, a, b);
        mergeMembers(merged, a, b);

        return merged;
    }

    private void mergeClassDeclaration(EnumDeclaration merged,
                                       EnumDeclaration a,
                                       EnumDeclaration b) {
        DeltaMergeHelper.mergeComment(merged, a, b);
        DeltaMergeHelper.mergeAnnotations(merged, a, b);

        // 合并修饰符 - 取并集
        merged.setModifiers(a.getModifiers());
        b.getModifiers().forEach(modifier -> {
            merged.addModifier(modifier.getKeyword());
        });

        // 合并实现的接口
        DeltaMergeHelper.mergeImplementedTypes(merged, a.getImplementedTypes(), b.getImplementedTypes());
    }

    private void mergeEnumConstants(EnumDeclaration merged,
                                    EnumDeclaration a,
                                    EnumDeclaration b) {
        Map<String, EnumConstantDeclaration> aConstants = a.getEntries().stream()
                .collect(Collectors.toMap(
                        EnumConstantDeclaration::getNameAsString,
                        e -> e,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));

        Map<String, EnumConstantDeclaration> bConstants = b.getEntries().stream()
                .collect(Collectors.toMap(
                        EnumConstantDeclaration::getNameAsString,
                        e -> e,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));

        aConstants.forEach((name, aConstant) -> {
            EnumConstantDeclaration constantToAdd = bConstants.containsKey(name)
                    ? bConstants.get(name).clone()
                    : aConstant.clone();
            merged.addEntry(constantToAdd);
            bConstants.remove(name);
        });

        bConstants.values().forEach(bConstant -> {
            merged.addEntry(bConstant.clone());
        });
    }

    private void mergeMembers(EnumDeclaration merged,
                              EnumDeclaration a,
                              EnumDeclaration b) {
        Map<String, BodyDeclaration<?>> aFields = a.getMembers().stream()
                .filter(m -> m instanceof FieldDeclaration)
                .collect(Collectors.toMap(
                        m -> ((FieldDeclaration) m).getVariable(0).getNameAsString(),
                        m -> m));

        Map<String, BodyDeclaration<?>> bFields = b.getMembers().stream()
                .filter(m -> m instanceof FieldDeclaration)
                .collect(Collectors.toMap(
                        m -> ((FieldDeclaration) m).getVariable(0).getNameAsString(),
                        m -> m));

        Map<String, BodyDeclaration<?>> aMethods = a.getMembers().stream()
                .filter(m -> m instanceof MethodDeclaration)
                .collect(Collectors.toMap(
                        m -> DeltaMergeHelper.getMethodSignature((MethodDeclaration) m),
                        m -> m));

        Map<String, BodyDeclaration<?>> bMethods = b.getMembers().stream()
                .filter(m -> m instanceof MethodDeclaration)
                .collect(Collectors.toMap(
                        m -> DeltaMergeHelper.getMethodSignature((MethodDeclaration) m),
                        m -> m));

        aFields.forEach((name, field) -> {
            if (!bFields.containsKey(name)) {
                merged.addMember(field.clone());
            }
        });
        bFields.values().forEach(field -> {
            merged.addMember(field.clone());
        });

        aMethods.forEach((sig, method) -> {
            if (!bMethods.containsKey(sig)) {
                merged.addMember(method.clone());
            }
        });
        bMethods.values().forEach(method -> {
            merged.addMember(method.clone());
        });
    }
}