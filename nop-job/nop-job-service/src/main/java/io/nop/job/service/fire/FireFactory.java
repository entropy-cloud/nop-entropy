package io.nop.job.service.fire;

import io.nop.job.dao.entity.NopJobFire;

import java.sql.Timestamp;

public class FireFactory {
    public static void fillBaseFireFields(NopJobFire fire, Timestamp fireTime) {
        fire.setCreatedBy("system");
        fire.setCreateTime(fireTime);
        fire.setUpdatedBy("system");
        fire.setUpdateTime(fireTime);
    }
}
