package io.github.canjiemo.base.myjdbc.metadata;


import io.github.canjiemo.base.myjdbc.error.MyJdbcErrorCode;
import io.github.canjiemo.mycommon.IdGen;
import io.github.canjiemo.mycommon.exception.BusinessException;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.ConvertUtils;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Data
@Accessors(chain = true)
@Slf4j
public class TableInfo {
    private String tableName;
    private Field pkField;
    private String pkFieldName;
    private String pkColumnName;
    private String delColumnName;
    private String delFieldName;
    private int delValue;
    private Class<?> clazz;
    private List<Field> fieldList;

    /** INSERT 时需自动填充的审计字段（CREATE_TIME/UPDATE_TIME/CREATE_BY/UPDATE_BY）。启动时由 TableInfoBuilder 缓存，运行时只读。 */
    private List<Field> auditInsertFields = Collections.emptyList();

    /** UPDATE 时需自动填充的审计字段（UPDATE_TIME/UPDATE_BY）。启动时由 TableInfoBuilder 缓存，运行时只读。 */
    private List<Field> auditUpdateFields = Collections.emptyList();


    public Field getFieldByName(String fieldName){
        Optional<Field> field = this.fieldList.stream().filter(f -> f.getName().equals(fieldName)).findFirst();
        if(field.isPresent()) return field.get();
        String className = this.clazz == null ? "unknown" : this.clazz.getName();
        log.error("未找到实体字段: class={}, field={}", className, fieldName);
        throw new BusinessException(MyJdbcErrorCode.CONFIG_ERROR.userMessage());
    }

    public void setPkValue(Object obj, Object value){
        try {
            PropertyDescriptor propertyDescriptor = getPkPropertyDescriptor(obj);
            Method writeMethod = propertyDescriptor.getWriteMethod();
            if (writeMethod == null) {
                throw pkConfigError("写入主键", obj, null);
            }
            writeMethod.invoke(obj, value);
        }catch (Exception e){
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw pkConfigError("写入主键", obj, e);
        }
    }

    public void setPkValue(Object obj){
        try {
            Object value = ConvertUtils.convert(IdGen.get().nextId(), this.pkField.getType());
            PropertyDescriptor propertyDescriptor = getPkPropertyDescriptor(obj);
            Method writeMethod = propertyDescriptor.getWriteMethod();
            if (writeMethod == null) {
                throw pkConfigError("生成并写入主键", obj, null);
            }
            writeMethod.invoke(obj, value);
        }catch (Exception e){
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw pkConfigError("生成并写入主键", obj, e);
        }
    }

    public Object getPkValue(Object obj){
        try {
            PropertyDescriptor propertyDescriptor = getPkPropertyDescriptor(obj);
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod == null) {
                throw pkConfigError("读取主键", obj, null);
            }
            return readMethod.invoke(obj);
        }catch (Exception e){
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw pkConfigError("读取主键", obj, e);
        }
    }

    private PropertyDescriptor getPkPropertyDescriptor(Object obj) {
        if (obj == null || this.pkField == null) {
            throw pkConfigError("解析主键属性", obj, null);
        }
        PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(obj.getClass(), this.pkField.getName());
        if (propertyDescriptor == null) {
            throw pkConfigError("解析主键属性", obj, null);
        }
        return propertyDescriptor;
    }

    private BusinessException pkConfigError(String action, Object obj, Exception e) {
        String className = obj == null ? "null" : obj.getClass().getName();
        String pkName = this.pkField == null ? "null" : this.pkField.getName();
        if (e == null) {
            log.error("实体主键访问失败: action={}, class={}, pkField={}", action, className, pkName);
        } else {
            log.error("实体主键访问失败: action={}, class={}, pkField={}", action, className, pkName, e);
        }
        return new BusinessException(MyJdbcErrorCode.CONFIG_ERROR.userMessage());
    }
}
