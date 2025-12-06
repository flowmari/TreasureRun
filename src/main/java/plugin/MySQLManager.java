package plugin;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import plugin.mapper.PlayerScoreMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class MySQLManager implements CommandExecutor {

  private final JavaPlugin plugin;
  private Connection connection;
  private SqlSessionFactory sqlSessionFactory;

  public MySQLManager(JavaPlugin plugin) {
    this.plugin = plugin;
    this.plugin.getCommand("dbstatus").setExecutor(this); // コマンド登録
  }

  public boolean connect() {
    String host = plugin.getConfig().getString("mysql.host");
    int port = plugin.getConfig().getInt("mysql.port");
    String database = plugin.getConfig().getString("mysql.database");
    String user = plugin.getConfig().getString("mysql.user");
    String password = plugin.getConfig().getString("mysql.password");

    String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC";

    try {
      // JDBC接続（MyBatis外用）
      connection = DriverManager.getConnection(url, user, password);

      // ✅ MyBatis用データソース
      PooledDataSource dataSource = new PooledDataSource();
      dataSource.setDriver("com.mysql.cj.jdbc.Driver");
      dataSource.setUrl(url);
      dataSource.setUsername(user);
      dataSource.setPassword(password);

      // ✅ MyBatisアノテーション設定とマッパー登録
      Configuration config = new Configuration();
      config.setMapUnderscoreToCamelCase(true);
      config.addMapper(PlayerScoreMapper.class);

      sqlSessionFactory = new SqlSessionFactoryBuilder().build(config);
      sqlSessionFactory.getConfiguration().setEnvironment(
          new org.apache.ibatis.mapping.Environment("development", null, dataSource)
      );

      Bukkit.getLogger().info("[EnemyDown] MySQL に接続しました（JDBC + MyBatis）");

      // ✅ 接続成功後にテーブルを自動作成
      initializeDatabase();

      return true;
    } catch (SQLException e) {
      Bukkit.getLogger().warning("[EnemyDown] MySQL接続に失敗しました: " + e.getMessage());
      return false;
    }
  }

  public boolean isConnected() {
    try {
      return connection != null && !connection.isClosed();
    } catch (SQLException e) {
      return false;
    }
  }

  public void disconnect() {
    if (isConnected()) {
      try {
        connection.close();
        Bukkit.getLogger().info("[EnemyDown] MySQL接続を切断しました。");
      } catch (SQLException e) {
        Bukkit.getLogger().warning("[EnemyDown] MySQL切断に失敗しました: " + e.getMessage());
      }
    }
  }

  public Connection getConnection() {
    return connection;
  }

  public SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }

  // ============================
  // ✅ player_score テーブル自動作成メソッド
  // ============================
  public void initializeDatabase() {
    try (Statement stmt = connection.createStatement()) {
      String sql = """
          CREATE TABLE IF NOT EXISTS player_score (
              id INT AUTO_INCREMENT PRIMARY KEY,
              player_name VARCHAR(50) NOT NULL,
              score INT NOT NULL DEFAULT 0,
              difficulty VARCHAR(20),
              registered_dt DATETIME DEFAULT CURRENT_TIMESTAMP,
              registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
              game_time BIGINT DEFAULT 0,
              uuid CHAR(36) NOT NULL UNIQUE,
              name VARCHAR(50),
              kill_count INT DEFAULT 0
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
          """;
      stmt.executeUpdate(sql);
      plugin.getLogger().info("[MySQLManager] player_score テーブルを確認・作成しました ✅");
    } catch (SQLException e) {
      plugin.getLogger().warning("[MySQLManager] player_score テーブル作成に失敗しました ❌: " + e.getMessage());
    }
  }

  // ============================
  // ✅ MySQL 接続テスト用メソッド（player_score対応）
  // ============================
  public void testDatabaseConnection() {
    try {
      if (isConnected()) {
        plugin.getLogger().info("[MySQLManager] MySQL 接続テスト成功 ✅");

        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM player_score;");
        if (rs.next()) {
          plugin.getLogger().info("[MySQLManager] player_score テーブルの行数: " + rs.getInt("cnt"));
        }
        rs.close();
        stmt.close();
      } else {
        plugin.getLogger().warning("[MySQLManager] MySQL 接続できていません ❌");
      }
    } catch (SQLException e) {
      e.printStackTrace();
      plugin.getLogger().warning("[MySQLManager] player_score テーブルが存在しない可能性があります ❌");
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("dbstatus")) {
      if (isConnected()) {
        sender.sendMessage(ChatColor.GREEN + "[EnemyDown] MySQL は接続されています。");
      } else {
        sender.sendMessage(ChatColor.RED + "[EnemyDown] MySQL は接続されていません。");
      }
      return true;
    }
    return false;
  }
}