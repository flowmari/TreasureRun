package plugin.quote;

public class QuoteFavoriteRow {
  public int id;
  public String outcome; // SUCCESS / TIME_UP / OTHER
  public String quote;
  public String timestamp; // 任意（DB形式そのままでもOK）
}