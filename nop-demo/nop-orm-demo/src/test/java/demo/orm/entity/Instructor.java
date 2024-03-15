package demo.orm.entity;

import demo.orm.entity._gen._Instructor;
import io.nop.api.core.annotations.biz.BizObjName;

import java.util.List;


@BizObjName("Instructor")
public class Instructor extends _Instructor {

    // NOP 的代码生成目前对复合主键的支持不足
    // 因此这里需要手工编写多对多的辅助函数
    public List<Section> getRelatedSectionList() {
        return (List<Section>) io.nop.orm.support.OrmEntityHelper.getRefProps(
                getTeachings(), Teaching.PROP_NAME_section);
    }

}
