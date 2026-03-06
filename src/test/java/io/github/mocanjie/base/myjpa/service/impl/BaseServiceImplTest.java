package io.github.mocanjie.base.myjpa.service.impl;

import io.github.mocanjie.base.myjpa.MyTableEntity;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("BaseServiceImpl 参数透传测试")
class BaseServiceImplTest {

    @Test
    @DisplayName("queryById(Long) 应保持 Long 类型")
    void queryByIdLongShouldKeepLongType() {
        AtomicReference<Object> capturedId = new AtomicReference<>();
        IBaseDao baseDao = (IBaseDao) Proxy.newProxyInstance(
                IBaseDao.class.getClassLoader(),
                new Class[]{IBaseDao.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) {
                        capturedId.set(args[0]);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });

        TestBaseService service = new TestBaseService();
        service.setBaseDao(baseDao);
        service.queryById(123L, TestEntity.class);

        assertInstanceOf(Long.class, capturedId.get());
        assertEquals(123L, capturedId.get());
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    static final class TestBaseService extends BaseServiceImpl {
        private void setBaseDao(IBaseDao baseDao) {
            this.baseDao = baseDao;
        }
    }

    static final class TestEntity implements MyTableEntity {
    }
}
