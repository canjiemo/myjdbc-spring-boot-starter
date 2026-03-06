package io.github.canjiemo.base.myjdbc.inheritedentity;

import io.github.canjiemo.base.myjdbc.annotation.MyTable;

@MyTable(value = "inherited_user")
public class InheritedPkUser extends AbstractBaseEntity {

    private String username;
    private Integer deleteFlag;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(Integer deleteFlag) {
        this.deleteFlag = deleteFlag;
    }
}
