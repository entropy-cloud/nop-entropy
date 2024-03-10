/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.loop;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 提供对嵌套循环处理过程的抽象。事先定义好多个循环变量以及它们之间的关系，然后就可以自由组合这些循环变量的先后顺序，得到不同的循环过程。 比如对于p1 -> p2 -> p3这样一个三级循环,
 * 循环{p3}/{p2}具体执行时是先得到所有p3, 然后对于每个p3查找它的父循环变量p2。 而循环{p2}/{p3}则是先展开到p2, 然后根据每个p2再展开得到p3.
 */
public interface INestedLoop extends Iterable<INestedLoopVar>, INestedLoopSupport {
    Stream<INestedLoopVar> stream();

    INestedLoop loopForVar(String varName);

    Map<String, Object> getGlobalVars();

    default List<Object> toList() {
        return stream().map(INestedLoopVar::getVarValue).collect(Collectors.toList());
    }
}