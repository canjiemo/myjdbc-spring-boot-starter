package io.github.mocanjie.base.myjpa.metadata;

import io.github.mocanjie.base.mycommon.IdGen;
import io.github.mocanjie.base.mycommon.exception.BusinessException;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.ConvertUtils;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

@Data
@Accessors(chain = true)
@Slf4j
public class TableInfo {
    private static final String USER_SAFE_ERROR_MESSAGE = "数据配置异常，请联系管理员";
    private String tableName;
    private Field pkField;
    private String pkFieldName;
    private String pkColumnName;
    private String delColumnName;
    private String delFieldName;
    private int delValue;
    private Class<?> clazz;
    private List<Field> fieldList;


    public Field getFieldByName(String fieldName){
        Optional<Field> field = this.fieldList.stream().filter(f -> f.getName().equals(fieldName)).findFirst();
        if(field.isPresent()) return field.get();
        String className = this.clazz == null ? "unknown" : this.clazz.getName();
        log.error("未找到实体字段: class={}, field={}", className, fieldName);
        throw new BusinessException(USER_SAFE_ERROR_MESSAGE);
    }

    public void setPkValue(Object obj, Object value){
        try {
            PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(obj.getClass(), this.pkField.getName());
            propertyDescriptor.getWriteMethod().invoke(obj,value);
        }catch (Exception e){
        }
    }

    public void setPkValue(Object obj){
        try {
            Object value = ConvertUtils.convert(IdGen.get().nextId(), this.pkField.getType());
            PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(obj.getClass(), this.pkField.getName());
            propertyDescriptor.getWriteMethod().invoke(obj,value);
        }catch (Exception e){
        }
    }

    public Object getPkValue(Object obj){
        try {
            PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(obj.getClass(), this.pkField.getName());
            return propertyDescriptor.getReadMethod().invoke(obj);
        }catch (Exception e){
            return null;
        }
    }
}
