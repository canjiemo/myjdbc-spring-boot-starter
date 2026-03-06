package io.github.canjiemo.base.myjdbc.metadata;

import io.github.mocanjie.base.mycommon.exception.BusinessException;
import io.github.canjiemo.base.myjdbc.error.MyJdbcErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("TableInfo 主键访问测试")
class TableInfoTest {

    @Test
    @DisplayName("缺少主键 setter 时抛出配置异常")
    void setPkValueShouldFailWhenSetterMissing() throws NoSuchFieldException {
        TableInfo tableInfo = new TableInfo()
                .setClazz(NoSetterEntity.class)
                .setPkField(NoSetterEntity.class.getDeclaredField("id"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tableInfo.setPkValue(new NoSetterEntity(), 1L));

        assertEquals(MyJdbcErrorCode.CONFIG_ERROR.userMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("缺少主键 getter 时抛出配置异常")
    void getPkValueShouldFailWhenGetterMissing() throws NoSuchFieldException {
        TableInfo tableInfo = new TableInfo()
                .setClazz(NoGetterEntity.class)
                .setPkField(NoGetterEntity.class.getDeclaredField("id"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tableInfo.getPkValue(new NoGetterEntity()));

        assertEquals(MyJdbcErrorCode.CONFIG_ERROR.userMessage(), ex.getMessage());
    }

    static final class NoSetterEntity {
        private Long id;

        public Long getId() {
            return id;
        }
    }

    static final class NoGetterEntity {
        private Long id;

        public void setId(Long id) {
            this.id = id;
        }
    }
}
