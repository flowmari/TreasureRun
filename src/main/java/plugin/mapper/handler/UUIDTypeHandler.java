package plugin.mapper.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.UUID;

// これでUUIDを文字列でやり取り
public class UUIDTypeHandler extends BaseTypeHandler<UUID> {
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, parameter.toString());
  }

  @Override
  public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
    String value = rs.getString(columnName);
    return value != null ? UUID.fromString(value) : null;
  }

  @Override
  public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String value = rs.getString(columnIndex);
    return value != null ? UUID.fromString(value) : null;
  }

  @Override
  public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    String value = cs.getString(columnIndex);
    return value != null ? UUID.fromString(value) : null;
  }
}
