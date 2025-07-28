package io.nop.javaparser.delta;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DeltaJavaMerger {
    public static DeltaJavaMerger INSTANCE = new DeltaJavaMerger();

    protected DeltaClassMerger classMerger = DeltaClassMerger.INSTANCE;
    protected DeltaEnumMerger enumMerger = DeltaEnumMerger.INSTANCE;
    protected DeltaAnnotationMerger annotationMerger = DeltaAnnotationMerger.INSTANCE;

    /**
     * 合并两个Java源文件，冲突时总是使用sourceB的内容
     *
     * @param sourceA 源文件A内容
     * @param sourceB 源文件B内容（优先）
     * @return 合并后的Java代码
     */
    public String merge(String sourceA, String sourceB) {
        CompilationUnit cuA = parseOrThrow(sourceA);
        CompilationUnit cuB = parseOrThrow(sourceB);

        CompilationUnit merged = mergeCompilationUnits(cuA, cuB);
        return merged.toString();
    }

    private CompilationUnit parseOrThrow(String source) {
        ParseResult<CompilationUnit> result = new JavaParser().parse(source);
        if (!result.isSuccessful() || !result.getResult().isPresent()) {
            throw new IllegalArgumentException("Invalid Java source: " + result.getProblems());
        }
        return result.getResult().get();
    }

    private CompilationUnit mergeCompilationUnits(CompilationUnit cuA, CompilationUnit cuB) {
        CompilationUnit merged = new CompilationUnit();

        // 包声明 - 优先B
        cuB.getPackageDeclaration().ifPresent(pkg ->
                merged.setPackageDeclaration(pkg.clone()));
        if (!merged.getPackageDeclaration().isPresent()) {
            cuA.getPackageDeclaration().ifPresent(pkg ->
                    merged.setPackageDeclaration(pkg.clone()));
        }

        // 导入 - 合并去重
        // 正确合并导入声明
        Set<ImportDeclaration> imports = new LinkedHashSet<>();

        // 添加A的所有导入
        cuA.getImports().forEach(imp ->
                imports.add(new ImportDeclaration(imp.getName(), imp.isStatic(), imp.isAsterisk())));

        // 添加B的所有导入（自动去重）
        cuB.getImports().forEach(imp ->
                imports.add(new ImportDeclaration(imp.getName(), imp.isStatic(), imp.isAsterisk())));

        // 添加到合并后的单元
        imports.forEach(merged::addImport);
        // 类型声明 - 合并处理
        mergeTypeDeclarations(merged, cuA, cuB);

        return merged;
    }

    private void mergeTypeDeclarations(CompilationUnit merged,
                                       CompilationUnit cuA,
                                       CompilationUnit cuB) {
        Map<String, TypeDeclaration<?>> aTypes = collectTypeDeclarations(cuA);
        Map<String, TypeDeclaration<?>> bTypes = collectTypeDeclarations(cuB);

        // 所有类型名（保持顺序）
        Set<String> allTypes = new LinkedHashSet<>();
        allTypes.addAll(aTypes.keySet());
        allTypes.addAll(bTypes.keySet());

        for (String typeName : allTypes) {
            TypeDeclaration<?> aType = aTypes.get(typeName);
            TypeDeclaration<?> bType = bTypes.get(typeName);

            if (aType != null && bType != null) {
                // 同名类型合并
                merged.addType(mergeTypes(aType, bType));
            } else {
                // 只存在一个版本，优先B
                TypeDeclaration<?> toAdd = (bType != null) ? bType.clone() : aType.clone();
                merged.addType(toAdd);
            }
        }
    }

    private TypeDeclaration<?> mergeTypes(TypeDeclaration<?> a, TypeDeclaration<?> b) {
        if (!a.getClass().equals(b.getClass())) {
            return b.clone();
        }

        if (a instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration aDecl = (ClassOrInterfaceDeclaration) a;
            ClassOrInterfaceDeclaration bDecl = (ClassOrInterfaceDeclaration) b;
            if (aDecl.isInterface() != bDecl.isInterface()) {
                return b.clone();
            }
            return classMerger.merge(aDecl, bDecl);
        } else if (a instanceof EnumDeclaration) {
            return enumMerger.merge((EnumDeclaration) a, (EnumDeclaration) b);
        } else if (a instanceof AnnotationDeclaration) {
            return annotationMerger.merge((AnnotationDeclaration) a, (AnnotationDeclaration) b);
        } else {
            return b.clone();
        }
    }

    private Map<String, TypeDeclaration<?>> collectTypeDeclarations(CompilationUnit cu) {
        return cu.getTypes().stream()
                .collect(Collectors.toMap(
                        TypeDeclaration::getNameAsString,
                        t -> t,
                        (t1, t2) -> t2, // 如果有重复，保留后者
                        LinkedHashMap::new)); // 保持原始顺序
    }
}