package plugin;

import java.util.List;
import java.io.InputStream;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import plugin.mapper.PlayerScoreMapper;
import plugin.mapper.data.PlayerScore;

public class MyPlugin extends JavaPlugin {

  private static SqlSessionFactory sqlSessionFactory;

  // ✅ JDBC接続情報を追加
  private static final String url = "jdbc:mysql://localhost:3306/sample_db";
  private static final String user = "root";
  private static final String password = "pass123";

  @Override
  public void onEnable() {
    getLogger().info("MyPluginが有効になりました。MyBatisを初期化します。");

    try {
      String resource = "mybatis-config.xml";
      InputStream inputStream = Resources.getResourceAsStream(resource);
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

      // Mapperを明示的に登録（BukkitでのClassLoader対策）
      sqlSessionFactory.getConfiguration().addMapper(PlayerScoreMapper.class);

      getLogger().info("MyBatisの初期化に成功しました。");
    } catch (Exception e) {
      e.printStackTrace();
      getLogger().severe("MyBatisの初期化に失敗しました: " + e.getMessage());
    }
  }

  @Override
  public void onDisable() {
    getLogger().info("MyPluginが無効になりました。");
  }

  public static SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    // ======= /score コマンド処理 =======
    if (command.getName().equalsIgnoreCase("score")) {
      if (!(sender instanceof Player)) {
        sender.sendMessage("このコマンドはプレイヤーのみ使用できます。");
        return true;
      }

      Player player = (Player) sender;
      String playerName = player.getName();

      try (SqlSession session = sqlSessionFactory.openSession()) {
        PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);
        PlayerScore data = mapper.selectPlayer(playerName);

        if (data == null) {
          data = new PlayerScore();
          data.setName(playerName);
          data.setScore(0);
          mapper.insertPlayerScore(data);
          session.commit();
        }

        player.sendMessage("あなたのスコア: " + data.getScore() + " 点");
      } catch (Exception e) {
        e.printStackTrace();
        sender.sendMessage("スコアを取得中にエラーが発生しました。");
      }

      return true;
    }

    // ======= /enemydown コマンド処理 =======
    if (command.getName().equalsIgnoreCase("enemydown")) {
      if (!(sender instanceof Player player)) {
        sender.sendMessage("このコマンドはプレイヤーのみ使用できます。");
        return true;
      }

      if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
          PlayerScoreMapper mapper = session.getMapper(PlayerScoreMapper.class);
          List<PlayerScore> scores = mapper.selectAll();

          if (scores.isEmpty()) {
            player.sendMessage("スコアが登録されていません。");
            return true;
          }

          player.sendMessage("=== EnemyDown スコア一覧 ===");
          for (PlayerScore score : scores) {
            // ✅ スコア表示処理
            player.sendMessage(score.getPlayerName() + " - スコア: " + score.getScore());
          }
        } catch (Exception e) {
          e.printStackTrace();
          sender.sendMessage("スコア一覧の取得中にエラーが発生しました。");
        }
        return true;
      }

      player.sendMessage("使い方: /enemydown list");
      return true;
    }

    return false;
  }
}