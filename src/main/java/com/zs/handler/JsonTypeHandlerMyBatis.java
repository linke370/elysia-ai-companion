// File: src/main/java/com/zs/handler/JsonTypeHandlerMyBatis.java
package com.zs.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * MyBatis JSON类型处理器
 * 用于自动将Java对象与MySQL JSON字段转换
 */
@MappedTypes({List.class, Object.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonTypeHandlerMyBatis<T> extends BaseTypeHandler<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> type;

    public JsonTypeHandlerMyBatis(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setString(i, json);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    private T parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON from database", e);
        }
    }
}