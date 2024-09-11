package io.nop.orm.model;

import java.util.List;

public interface IViewEntityModel extends IPdmElement {
    List<? extends IViewFieldModel> getFields();

    IViewFieldModel getField(String name);

    List<? extends IViewMemberEntityModel> getMemberEntities();
}
