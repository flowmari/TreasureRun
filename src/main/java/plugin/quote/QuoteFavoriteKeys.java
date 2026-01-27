package plugin.quote;

public final class QuoteFavoriteKeys {

  private QuoteFavoriteKeys() {}

  // favorites.*
  public static final String TITLE = "favorites.title";
  public static final String REMOVE = "favorites.remove";

  // cover.*
  public static final String COVER_HEAD = "favorites.cover.head";
  public static final String COVER_SUB = "favorites.cover.sub";
  public static final String COVER_BADGE = "favorites.cover.badge";
  public static final String COVER_COUNT_LABEL = "favorites.cover.countLabel";
  public static final String COVER_HINT = "favorites.cover.hint";
  public static final String COVER_OPEN_TOC = "favorites.cover.openToc";
  public static final String COVER_REREAD = "favorites.cover.reread";

  // toc.*
  public static final String TOC_HEAD = "favorites.toc.head";
  public static final String TOC_SUB = "favorites.toc.sub";
  public static final String TOC_HOWTO = "favorites.toc.howto";
  public static final String TOC_HOWTO_SHIFT = "favorites.toc.howtoShift";
  public static final String TOC_CLICKABLE = "favorites.toc.clickable";
  public static final String TOC_REREAD_TITLE = "favorites.toc.rereadTitle";
  public static final String TOC_REREAD = "favorites.toc.reread";
  public static final String TOC_MANAGE = "favorites.toc.manage";

  // empty.*
  public static final String EMPTY_HEAD = "favorites.empty.head";
  public static final String EMPTY_SUB = "favorites.empty.sub";
  public static final String EMPTY_NO_FAV = "favorites.empty.noFav";
  public static final String EMPTY_NO_FAV_SUB = "favorites.empty.noFavSub";
  public static final String EMPTY_SAVE_HOW = "favorites.empty.saveHow";
  public static final String EMPTY_SAVE_HOW_1 = "favorites.empty.saveHow1";
  public static final String EMPTY_SAVE_HOW_2 = "favorites.empty.saveHow2";
  public static final String EMPTY_TRY_NOW = "favorites.empty.tryNow";
  public static final String EMPTY_SAVE_LATEST = "favorites.empty.saveLatest";
  public static final String EMPTY_REREAD = "favorites.empty.reread";
  public static final String EMPTY_REREAD_BOOK = "favorites.empty.rereadBook";

  // chapter.*
  public static final String CHAPTER_EMPTY = "favorites.chapter.empty";
  public static final String CHAPTER_BACK = "favorites.chapter.back";
  public static final String CHAPTER_COUNT = "favorites.chapter.count";
  public static final String CHAPTER_REREAD_BOOK = "favorites.chapter.rereadBook";

  public static final String CHAPTER_SUCCESS_TITLE = "favorites.chapter.successTitle";
  public static final String CHAPTER_SUCCESS_SUB = "favorites.chapter.successSub";
  public static final String CHAPTER_TIMEUP_TITLE = "favorites.chapter.timeupTitle";
  public static final String CHAPTER_TIMEUP_SUB = "favorites.chapter.timeupSub";
  public static final String CHAPTER_OTHER_SUB = "favorites.chapter.otherSub";

  // ✅ 本文テンプレ（19言語で揃える：SUCCESS＝表彰台ログ / TIME_UP＝詩集）
  // ※ favorites.chapter.* に含めて「favorites内で完結」させる（壊れない構造）
  public static final String SUCCESS_TEMPLATE_1 = "favorites.chapter.successTpl1";
  public static final String SUCCESS_TEMPLATE_2 = "favorites.chapter.successTpl2";
  public static final String TIMEUP_TEMPLATE_1  = "favorites.chapter.timeupTpl1";
  public static final String TIMEUP_TEMPLATE_2  = "favorites.chapter.timeupTpl2";
}