package io.github.canjiemo.base.myjdbc.test.entity;

import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.base.myjdbc.annotation.AuditFill;
import io.github.canjiemo.base.myjdbc.annotation.MyField;
import io.github.canjiemo.base.myjdbc.annotation.MyTable;

import java.time.LocalDateTime;

@MyTable(value = "auditable_user", delColumn = "delete_flag", delField = "deleteFlag", delValue = 1)
public class AuditableUser implements MyTableEntity {
    private Long id;
    private String username;
    private Integer deleteFlag;

    @MyField(fill = AuditFill.CREATE_TIME)
    private LocalDateTime createTime;

    @MyField(fill = AuditFill.UPDATE_TIME)
    private LocalDateTime updateTime;

    @MyField(fill = AuditFill.CREATE_BY)
    private Long createBy;

    @MyField(fill = AuditFill.UPDATE_BY)
    private Long updateBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(Integer deleteFlag) { this.deleteFlag = deleteFlag; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
}
