package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaModelChangedEventBiz;
import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;

/**
 * 元数据变更事件 BizModel（架构基线 §2.8 / 设计 10 / plan 2026-07-17-0228-1）：
 * 基线 CRUD（{@link CrudBizModel}）自动暴露 GraphQL findPage/get/save/delete，
 * 支撑事件历史的 query 消费路径（审计 / 下游拉取）。
 *
 * <p>事件行由 {@code MetaModelChangedEventPublisher}（service 层 IoC bean）在写路径持久化成功后写入，
 * 本 BizModel 不直接负责事件生成（避免持久化与事件发布耦合在同一入口）。
 */
@BizModel("NopMetaModelChangedEvent")
public class NopMetaModelChangedEventBizModel extends CrudBizModel<NopMetaModelChangedEvent>
        implements INopMetaModelChangedEventBiz {
    public NopMetaModelChangedEventBizModel() {
        setEntityName(NopMetaModelChangedEvent.class.getName());
    }
}
