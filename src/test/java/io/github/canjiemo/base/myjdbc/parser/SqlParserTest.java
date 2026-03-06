package io.github.canjiemo.base.myjdbc.parser;

import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.utils.MyReflectionUtils;
import io.github.canjiemo.mycommon.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SqlParser 写 SQL 生成测试")
class SqlParserTest {

    @Test
    @DisplayName("UPDATE 语句不应更新主键列")
    void updateSqlShouldExcludePrimaryKeyFromSetClause() throws Exception {
        NormalEntity entity = new NormalEntity();
        entity.setId(1L);
        entity.setUsername("alice");

        String sql = SqlParser.getUpdateSql(tableInfoFor(NormalEntity.class), entity, true);

        assertEquals("UPDATE test_user SET username=:username WHERE id=:id", sql);
    }

    @Test
    @DisplayName("INSERT 读取字段失败时应快速失败")
    void insertSqlShouldFailFastWhenGetterMissing() throws Exception {
        MissingGetterEntity entity = new MissingGetterEntity();
        entity.setId(1L);
        entity.setUsername("alice");

        assertThrows(BusinessException.class,
                () -> SqlParser.getInsertSql(tableInfoFor(MissingGetterEntity.class), entity, true));
    }

    private static TableInfo tableInfoFor(Class<?> entityClass) throws Exception {
        return new TableInfo()
                .setTableName("test_user")
                .setClazz(entityClass)
                .setPkField(MyReflectionUtils.findFieldInHierarchy(entityClass, "id"))
                .setPkFieldName("id")
                .setPkColumnName("id")
                .setDelFieldName("deleteFlag")
                .setDelColumnName("delete_flag")
                .setFieldList(MyReflectionUtils.getFieldList(entityClass))
                .setDelValue(1);
    }

    static class NormalEntity implements MyTableEntity {
        private Long id;
        private String username;
        private Integer deleteFlag;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

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

    static class MissingGetterEntity implements MyTableEntity {
        private Long id;
        private String username;
        private Integer deleteFlag;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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
}
