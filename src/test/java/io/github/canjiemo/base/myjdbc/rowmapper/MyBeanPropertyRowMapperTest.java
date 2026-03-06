package io.github.canjiemo.base.myjdbc.rowmapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MyBeanPropertyRowMapper DTO 映射测试")
class MyBeanPropertyRowMapperTest {

    @Test
    @DisplayName("非 @MyTable DTO 应直接按 Bean 属性建立列映射")
    void shouldMapPlainDtoPropertiesWithoutTableMetadata() throws Exception {
        MyBeanPropertyRowMapper<RoleProjection> rowMapper = new MyBeanPropertyRowMapper<>(RoleProjection.class);

        Map<String, PropertyDescriptor> mappedFields = readMappedFields(rowMapper);

        assertNotNull(mappedFields.get("id"));
        assertNotNull(mappedFields.get("role_name"));
        assertNotNull(mappedFields.get("create_time"));
        assertEquals("roleName", mappedFields.get("role_name").getName());
        assertEquals("createTime", mappedFields.get("create_time").getName());
        assertTrue(mappedFields.containsKey("rolename"));
        assertTrue(mappedFields.containsKey("createtime"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PropertyDescriptor> readMappedFields(MyBeanPropertyRowMapper<?> rowMapper) throws Exception {
        Field field = MyBeanPropertyRowMapper.class.getDeclaredField("mappedFields");
        field.setAccessible(true);
        return (Map<String, PropertyDescriptor>) field.get(rowMapper);
    }

    static class RoleProjection {
        private Long id;
        private String roleName;
        private LocalDateTime createTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }
}
