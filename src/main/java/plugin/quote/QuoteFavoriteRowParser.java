package plugin.quote;

public class QuoteFavoriteRowParser {

  /**
   * row文字列は想定として
   * 例：
   *   "id: 12\noutcome: SUCCESS\nquote: ...\ntime: 2026-01-23 12:34"
   * のような形を想定（違っても拾えるところだけ拾う）
   */
  public static QuoteFavoriteRow parse(String row) {
    QuoteFavoriteRow r = new QuoteFavoriteRow();
    if (row == null) return r;

    String[] lines = row.split("\n");
    for (String line : lines) {
      String l = line.trim();
      if (l.toLowerCase().startsWith("id")) {
        r.id = safeInt(extractValue(l));
      } else if (l.toLowerCase().contains("outcome")) {
        r.outcome = extractValue(l);
      } else if (l.toLowerCase().startsWith("quote")) {
        r.quote = extractValue(l);
      } else if (l.toLowerCase().startsWith("time") || l.toLowerCase().startsWith("timestamp")) {
        r.timestamp = extractValue(l);
      }
    }

    if (r.outcome == null) r.outcome = "OTHER";
    if (r.quote == null) r.quote = row; // 最悪は全部をquote扱い
    return r;
  }

  private static String extractValue(String line) {
    int idx = line.indexOf(':');
    if (idx < 0) return line;
    return line.substring(idx + 1).trim();
  }

  private static int safeInt(String s) {
    try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
  }
}