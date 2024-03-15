package demo.orm.entity;

import demo.orm.entity._gen.SectionPkBuilder;
import demo.orm.entity._gen._Section;
import io.nop.api.core.annotations.biz.BizObjName;

import java.util.List;


@BizObjName("Section")
public class Section extends _Section {


    public static SectionPkBuilder newPk() {
        return new SectionPkBuilder();
    }

    public List<Instructor> getRelatedInstructorList() {
        return (List<Instructor>) io.nop.orm.support.OrmEntityHelper.getRefProps(
                getTeachings(), Teaching.PROP_NAME_instructor);
    }

}
