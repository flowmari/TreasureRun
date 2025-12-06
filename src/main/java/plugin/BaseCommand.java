package plugin;

import java.io.IOException;
import java.io.InputStream;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * コマンドを実行して動かすプラグイン処理の基底クラスです。
 * MyBatisの初期化および管理もここで行います。
 */
public abstract class BaseCommand implements CommandExecutor {

  protected SqlSessionFactory sqlSessionFactory;  // MyBatisセッションファクトリ

  /**
   * コンストラクタではMyBatisの初期化は行わない。
   * 派生クラスで必要なタイミングでinitializeMyBatis()を呼ぶ。
   */
  public BaseCommand() {
    // initializeMyBatis() は呼ばない
  }

  /**
   * MyBatisの初期化処理。mybatis-config.xmlをリソースから読み込みます。
   * 派生クラスが必要に応じて呼び出してください。
   */
  protected void initializeMyBatis() {
    String resource = "mybatis-config.xml";
    InputStream inputStream = null;
    try {
      inputStream = Resources.getResourceAsStream(resource);
      if (inputStream == null) {
        System.err.println("[BaseCommand] MyBatis設定ファイルが見つかりません: " + resource);
        return;
      }
      this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
      System.out.println("[BaseCommand] MyBatis SqlSessionFactory初期化成功");
    } catch (IOException e) {
      System.err.println("[BaseCommand] MyBatis初期化エラー: " + e.getMessage());
      e.printStackTrace();
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException ignored) {}
      }
    }
  }

  /**
   * MyBatisの初期化済みかを返します。
   */
  public boolean isMyBatisInitialized() {
    return sqlSessionFactory != null;
  }

  /**
   * MyBatisを再初期化します。緊急時に呼び出し可能。
   */
  public void reinitializeMyBatis() {
    System.out.println("[BaseCommand] MyBatisを再初期化します。");
    initializeMyBatis();
  }

  /**
   * MyBatisのSqlSessionFactoryを取得します。
   */
  public SqlSessionFactory getSqlSessionFactory() {
    return this.sqlSessionFactory;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (sender instanceof Player player) {
      return onExecutePlayerCommand(player, command, label, args);
    } else {
      return onExecuteNPCCommand(sender, command, label, args);
    }
  }

  /**
   * コマンド実行者がプレイヤーだった場合に実行する処理を実装してください。
   */
  public abstract boolean onExecutePlayerCommand(Player player, Command command, String label, String[] args);

  /**
   * コマンド実行者がプレイヤー以外だった場合に実行する処理を実装してください。
   */
  public abstract boolean onExecuteNPCCommand(CommandSender sender, Command command, String label, String[] args);

  /**
   * 旧形式プレイヤーコマンド実行用メソッド。必要に応じてオーバーライド。
   */
  public boolean onExecutePlayerCommand(Player player) {
    return false;
  }

  /**
   * 旧形式プレイヤー以外コマンド実行用メソッド。必要に応じてオーバーライド。
   */
  public boolean onExecuteNPCCommand(CommandSender sender) {
    return false;
  }

}