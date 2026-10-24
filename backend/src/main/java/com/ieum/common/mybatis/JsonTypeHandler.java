// 주인: BE-B
package com.ieum.common.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * MySQL JSON 컬럼 ↔ Jackson JsonNode 변환.
 * 매퍼 XML에서 typeHandler="com.ieum.common.mybatis.JsonTypeHandler" 로 지정해서 쓴다.
 * (competency_profile.profile, application_document.document 등)
 */
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(value = {JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.OTHER}, includeNullJdbcType = true)
public class JsonTypeHandler extends BaseTypeHandler<JsonNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("JSON 직렬화 실패", e);
        }
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private JsonNode parse(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new SQLException("JSON 파싱 실패", e);
        }
    }
}
