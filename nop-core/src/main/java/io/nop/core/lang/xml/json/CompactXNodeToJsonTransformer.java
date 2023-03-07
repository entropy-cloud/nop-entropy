/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.json;

import io.nop.core.lang.xml.IXNodeToObjectTransformer;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectJObjectHandler;

/**
 * 识别j:list和j:key设置，将XNode转换为json对象。这里采用的转换规则比{@link StdXNodeToJsonTransformer}复杂,
 * 且不能保证双向的可逆性，即从json再次转换回XNode后可能结果与原先的XNode不一致。复杂的地方在于识别哪些节点需要被转换为列表结构。 具体转换规则为:
 * <ol>
 * <li>1. 如果没有属性且没有子节点，则转换为普通的值</li>
 * <li>2. 如果标签名为_且没有属性，则转换为普通的值</li>
 * <li>3. 如果标记了j:list="true"，则转换为列表对象</li>
 * <li>4. 其他对象转换为Map，属性名和子节点名都作为Map的key</li>
 * <li>5. 标签名如果不是_，也不是Map的key,则转换为type属性</li>
 * </ol>
 *
 * <pre>
 *     <root a="1" b="@:3.2" c="true">
 *         <description>xxx</description>
 *         <options j:list="true">
 *            <_ label="A" value="a" />
 *            <option label="B" value="b" />
 *         </options>
 *         <items>
 *             <_>1</_>
 *             <_ name="a">
 *         </body>
 *         <buttons j:list="true">
 *             <button name="a" />
 *         </buttons>
 *     </root>
 * </pre>
 * <p>
 * 转换为json对象：
 *
 * <pre>
 *     {
 *         type: "root",
 *         a: 1,
 *         b: 3.2
 *         c: true,
 *         description: "xxx",
 *         options:[
 *            { label: "A", "value": "a" },
 *            { type: "option" , label: "B", value: "b" }
 *         ],
 *         items: [1, { name: "a"}],
 *         buttons:[
 *            { type: "button", name: "a"}
 *         ]
 *     }
 * </pre>
 */
public class CompactXNodeToJsonTransformer implements IXNodeToObjectTransformer {

    @Override
    public Object transformToObject(XNode node) {
        CollectJObjectHandler handler = new CollectJObjectHandler();
        node.process(handler);
        return handler.getResult();
    }
}