package plugin;

import java.io.InputStream;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import plugin.mapper.PlayerScoreMapper;
import plugin.mapper.data.PlayerScore;

/**
 * DB接続やそれに付随する登録や更新処理を行うクラスです。
 */
public class DBUtils {

  private SqlSessionFactory sqlSessionFactory;

  public DBUtils() {
    try {
      InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");
      this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
      this.sqlSessionFactory.getConfiguration().addMapper(PlayerScoreMapper.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public SqlSessionFactory getSqlSessionFactory() {
    return this.sqlSessionFactory;
  }

  /**
   * PlayerScoreをデータベースに登録する。
   */
  public void insertPlayerScore(PlayerScore playerScore) {
    try (SqlSession session = sqlSessionFactory.openSession(true)) {
      PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);
      mapper.insert(playerScore);  // insertがPlayerScoreMapperに定義されている前提
    } catch (Exception e) {
      System.err.println("PlayerScoreの挿入に失敗しました: " + e.getMessage());
    }
  }
}