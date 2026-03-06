package io.github.canjiemo.base.myjdbc.inheritedentity;

import io.github.canjiemo.base.myjdbc.MyTableEntity;

public abstract class AbstractBaseEntity implements MyTableEntity {

    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
