# SUSPECT CLASSIFICATION PASS 01

## Why this pass matters
- Separate live-path player-facing work from debug / SQL / legacy noise
- Decide the next batch from evidence, not from guesswork
- Keep the recruiter-facing story strong: CI-backed rollout with safe fallback strategy

## Referenced key inventory (snapshot)
```text
===== referenced key inventory =====
[src/main/java/plugin/CheckTreasureEmeraldCommand.java]
  command.checkTreasureEmerald.playersOnly
[src/main/java/plugin/CraftSpecialEmeraldCommand.java]
  command.craftSpecialEmerald.needDiamonds
  command.craftSpecialEmerald.playersOnly
  command.craftSpecialEmerald.success
[src/main/java/plugin/LangCommand.java]
  command.lang.emptyCode
  command.lang.guiNotReady
  command.lang.list.gui
  command.lang.list.title
  command.lang.list.usage
  command.lang.resetDone
[src/main/java/plugin/LanguageSelectGui.java]
  command.lang.guiCancelledGameMenu
  command.lang.guiCancelledGameStart
  command.lang.guiTooManyShown
[src/main/java/plugin/QuoteFavoriteBookClickListener.java]
  command.quoteFavorite.latestNotSaved
  command.quoteFavorite.latestSaved
  favorites.empty.noFav
  favorites.title
  favorites.toc.howtoShift
[src/main/java/plugin/StageCleanupCommand.java]
  command.stageCleanup.managerNotReady
  command.stageCleanup.playersOnly
[src/main/java/plugin/TreasureItemFactory.java]
  items.specialEmerald.displayName
  items.specialEmerald.loreCrafted
  items.specialEmerald.loreSpecial
[src/main/java/plugin/TreasureRunStartCommand.java]
  command.gameStart.playersOnly
  command.gameStart.worldNotFound
[src/main/java/plugin/quote/QuoteFavoriteCommand.java]
  command.quoteFavorite.bookOpenFailed
  command.quoteFavorite.help.book
  command.quoteFavorite.help.bookOther
  command.quoteFavorite.help.bookSuccess
  command.quoteFavorite.help.bookTimeup
  command.quoteFavorite.help.bookToc
  command.quoteFavorite.help.latest
  command.quoteFavorite.help.list
  command.quoteFavorite.help.remove
  command.quoteFavorite.help.reread
  command.quoteFavorite.help.rereadBook
  command.quoteFavorite.help.rereadTitle
  command.quoteFavorite.help.title
  command.quoteFavorite.latestNotSaved
  command.quoteFavorite.latestSaved
  command.quoteFavorite.listHeader
  command.quoteFavorite.listSeparator
  command.quoteFavorite.openBookFailed
  command.quoteFavorite.playersOnly
  command.quoteFavorite.removeIdNumber
  command.quoteFavorite.removeNotFound
  command.quoteFavorite.removeSuccess
  command.quoteFavorite.removeUsage
  command.quoteFavorite.repositoryNotReady
  command.quoteFavorite.rereadNoQuotes
  command.quoteFavorite.unknownSubcommand
[src/main/java/plugin/quote/QuoteFavoriteShortcutListener.java]
  command.quoteFavorite.repositoryNotReady
  command.quoteFavorite.shortcutNotSaved
  command.quoteFavorite.shortcutSaved
```

## Player-facing candidate (259)
### asl_gloss.yml
- L190:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L191:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L192:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L255:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L256:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L284:   players_only_10ed3cbc: Players only.
- L286:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L288:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L289:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L290:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L291:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L293:   favorites_latest_20_e23c073b: '★ FAVORITES (latest 20):'
- L294:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L295:   id_must_be_a_number_5661bf80: ID must be a number.
- L298:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L303:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L304:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### de.yml
- L197:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L198:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L199:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L262:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L263:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L291:   players_only_10ed3cbc: Players only.
- L293:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L295:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L296:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L297:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L298:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L301:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L302:   id_must_be_a_number_5661bf80: ID must be a number.
- L305:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L310:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L311:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### en.yml
- L196:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L197:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L198:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L261:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L262:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L290:   players_only_10ed3cbc: Players only.
- L292:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L294:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L295:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L296:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L297:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L299:   favorites_latest_20_e23c073b: '★ Favorites (latest 20):'
- L300:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L301:   id_must_be_a_number_5661bf80: ID must be a number.
- L304:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L309:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L310:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### es.yml
- L198:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L199:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L200:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L263:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L264:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L292:   players_only_10ed3cbc: Players only.
- L294:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L296:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L297:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L298:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L299:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L302:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L303:   id_must_be_a_number_5661bf80: ID must be a number.
- L306:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L311:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L312:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### fi.yml
- L196:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L197:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L198:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L261:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L262:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L290:   players_only_10ed3cbc: Players only.
- L292:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L294:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L295:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L296:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L297:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L300:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L301:   id_must_be_a_number_5661bf80: ID must be a number.
- L304:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L309:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L310:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### fr.yml
- L197:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L198:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L199:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L262:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L263:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L291:   players_only_10ed3cbc: Players only.
- L293:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L295:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L296:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L297:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L298:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L301:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L302:   id_must_be_a_number_5661bf80: ID must be a number.
- L305:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L310:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L311:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### hi.yml
- L276:     time_s_up_87859973: Time's up.
- L277:     run_complete_46d920e5: Run complete.
- L303:     run_complete_d258363d: Run Complete!
- L305:     time_s_up_7441e686: TIME'S UP!
- L314:     language_gui_aed4f8fc: Language GUI आरंभ नहीं हुई।

### is.yml
- L196:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L197:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L198:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L261:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L262:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L290:   players_only_10ed3cbc: Players only.
- L292:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L294:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L295:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L296:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L297:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L300:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L301:   id_must_be_a_number_5661bf80: ID must be a number.
- L304:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L309:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L310:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### it.yml
- L198:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L199:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L200:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L263:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L264:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L292:   players_only_10ed3cbc: Players only.
- L294:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L296:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L297:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L298:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L299:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L302:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L303:   id_must_be_a_number_5661bf80: ID must be a number.
- L306:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L311:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L312:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### ja.yml
- L189:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L190:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L191:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L254:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L255:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L283:   players_only_10ed3cbc: Players only.
- L285:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L287:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L288:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L289:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L290:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L293:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L294:   id_must_be_a_number_5661bf80: ID must be a number.
- L297:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L302:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L303:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### ko.yml
- L190:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L191:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L192:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L255:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L256:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L284:   players_only_10ed3cbc: Players only.
- L286:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L288:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L289:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L290:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L291:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L294:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L295:   id_must_be_a_number_5661bf80: ID must be a number.
- L298:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L303:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L304:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### la.yml
- L196:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L197:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L198:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L261:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L262:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L290:   players_only_10ed3cbc: Players only.
- L292:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L294:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L295:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L296:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L297:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L300:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L301:   id_must_be_a_number_5661bf80: ID must be a number.
- L304:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L309:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L310:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### lzh.yml
- L270:     time_s_up_87859973: Time's up.
- L271:     run_complete_46d920e5: Run complete.
- L296:     run_complete_d258363d: Run Complete!
- L298:     time_s_up_7441e686: TIME'S UP!

### nl.yml
- L196:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L197:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L198:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L261:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L262:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L290:   players_only_10ed3cbc: Players only.
- L292:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L294:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L295:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L296:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L297:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L300:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L301:   id_must_be_a_number_5661bf80: ID must be a number.
- L304:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L309:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L310:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### pt.yml
- L278:     time_s_up_87859973: Time's up.
- L279:     run_complete_46d920e5: Run complete.
- L305:     run_complete_d258363d: Run Complete!
- L307:     time_s_up_7441e686: TIME'S UP!

### ru.yml
- L195:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L196:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L197:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L260:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L261:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L289:   players_only_10ed3cbc: Players only.
- L291:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L293:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L294:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L295:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L296:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L299:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L300:   id_must_be_a_number_5661bf80: ID must be a number.
- L303:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L308:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L309:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### sa.yml
- L194:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L195:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L196:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L259:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L260:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L288:   players_only_10ed3cbc: Players only.
- L290:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L292:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L293:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L294:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L295:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L298:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L299:   id_must_be_a_number_5661bf80: ID must be a number.
- L302:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L307:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L308:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### sv.yml
- L195:     time_s_up_c5b3ef74: §c§lTIME'S UP!
- L196:     treasures_collected_ca7eab31: '§7Treasures collected: §e'
- L197:     try_a_different_route_next_t_721a4d8b: §7Try a different route next time — or
- L260:     favorite_saved_sneak_rightcl_7a73eb4f: ★ Favorite saved! (Sneak+RightClick)
- L261:     favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet
- L289:   players_only_10ed3cbc: Players only.
- L291:   quotefavorite_latest_save_la_8e4bdc46: /quoteFavorite latest  - save latest quote
- L293:   quotefavorite_list_show_favo_4ebb13c4: /quoteFavorite list    - show favorites
- L294:   quotefavorite_remove_id_remo_a6fdb289: /quoteFavorite remove <id> - remove favorite
- L295:   favorite_saved_bb0cd8a8: ★ Favorite saved!
- L296:   favorite_not_saved_maybe_no__8d52a118: Favorite not saved. (maybe no logs yet /
- L299:   usage_quotefavorite_remove_i_5324156c: 'Usage: /quoteFavorite remove <id>'
- L300:   id_must_be_a_number_5661bf80: ID must be a number.
- L303:   unknown_subcommand_use_quote_f79bf3b5: 'Unknown subcommand. Use: /quoteFavorite'
- L308:   rank_1_2_3_demo_d87f40da: '使い方: /rank <1|2|3|demo>'
- L309:   rank_1_2_3_demo_4f3e3084: '数字で入力してください: /rank <1|2|3|demo>'

### zh_tw.yml
- L270:     time_s_up_87859973: Time's up.
- L271:     run_complete_46d920e5: Run complete.
- L296:     run_complete_d258363d: Run Complete!
- L298:     time_s_up_7441e686: TIME'S UP!

## Legacy or review (796)
### asl_gloss.yml
- L127:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L129:     text_e096c9a4: '&6特製エメラルド'
- L131:     items_current_0629a198: items current=
- L132:     cursor_7fabae3f: / cursor=
- L137:     shift_53e51de9: ', shift='
- L148:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L164:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L178:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L179:     count_int_not_null_660eadd8: count INT NOT NULL)
- L181:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L182:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L184:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L185:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L186:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L187:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L188:     text_cc353737: 全ての宝物を発見しました！
- L189:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L252:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L258:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L261:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 20 more

### de.yml
- L134:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L136:     text_e096c9a4: '&6特製エメラルド'
- L138:     items_current_0629a198: items current=
- L139:     cursor_7fabae3f: / cursor=
- L144:     shift_53e51de9: ', shift='
- L155:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L171:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L185:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L186:     count_int_not_null_660eadd8: count INT NOT NULL)
- L188:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L189:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L191:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L192:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L193:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L194:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L195:     text_cc353737: 全ての宝物を発見しました！
- L196:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L259:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L265:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L268:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### en.yml
- L133:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L135:     text_e096c9a4: '&6特製エメラルド'
- L137:     items_current_0629a198: items current=
- L138:     cursor_7fabae3f: / cursor=
- L143:     shift_53e51de9: ', shift='
- L154:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L170:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L184:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L185:     count_int_not_null_660eadd8: count INT NOT NULL)
- L187:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L188:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L190:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L191:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L192:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L193:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L194:     text_cc353737: 全ての宝物を発見しました！
- L195:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L258:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L264:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L267:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 20 more

### es.yml
- L135:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L137:     text_e096c9a4: '&6特製エメラルド'
- L139:     items_current_0629a198: items current=
- L140:     cursor_7fabae3f: / cursor=
- L145:     shift_53e51de9: ', shift='
- L156:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L172:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L186:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L187:     count_int_not_null_660eadd8: count INT NOT NULL)
- L189:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L190:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L192:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L193:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L194:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L195:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L196:     text_cc353737: 全ての宝物を発見しました！
- L197:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L260:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L266:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L269:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### fi.yml
- L133:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L135:     text_e096c9a4: '&6特製エメラルド'
- L137:     items_current_0629a198: items current=
- L138:     cursor_7fabae3f: / cursor=
- L143:     shift_53e51de9: ', shift='
- L154:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L170:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L184:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L185:     count_int_not_null_660eadd8: count INT NOT NULL)
- L187:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L188:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L190:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L191:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L192:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L193:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L194:     text_cc353737: 全ての宝物を発見しました！
- L195:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L258:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L264:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L267:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### fr.yml
- L134:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L136:     text_e096c9a4: '&6特製エメラルド'
- L138:     items_current_0629a198: items current=
- L139:     cursor_7fabae3f: / cursor=
- L144:     shift_53e51de9: ', shift='
- L155:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L171:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L185:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L186:     count_int_not_null_660eadd8: count INT NOT NULL)
- L188:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L189:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L191:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L192:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L193:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L194:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L195:     text_cc353737: 全ての宝物を発見しました！
- L196:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L259:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L265:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L268:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### hi.yml
- L234:     playerscore_b91d4cd3: 'PlayerScore सम्मिलन विफल:'
- L235:     messages_yml_not_loaded_d56b1cd3: (messages.yml not loaded)
- L236:     translation_missing_77031543: '(अनुवाद नहीं मिला:'
- L238:     click_to_select_aac1569c: चुनने के लिए क्लिक करें
- L239:     treasurerun_lang_config_yml__2dde4625: '[TreasureRun][Lang] config.yml में ''language:''
- L242:     treasurerun_lang_default_60887a83: '[TreasureRun][Lang] default='
- L246:     treasurerun_lang_material_la_bc70a401: '[TreasureRun][Lang] अमान्य Material: lang='
- L247:     treasurerun_lang_language_ic_4bc2362d: '[TreasureRun][Lang] ''language.iconMaterial:''
- L266:     light_fell_evenly_and_every__e50aee87: 'Light fell evenly, ,
- L271:     the_wind_fell_silent_and_eve_a96036c1: 'The wind fell silent.
- L280:     playerlanguagestore_failed_t_c4999402: '[PlayerLanguageStore] Failed to create/load
- L282:     playerlanguagestore_failed_t_b6478ec8: '[PlayerLanguageStore] Failed to save player_languages.yml:'
- L288:     treasurerunmultichestplugin_4499e25c: '🌈 TreasureRunMultiChestPlugin: 起動 🌈'
- L289:     treasurerun_gamestagemanager_3d9e7f5a: '[TreasureRun] GameStageManager event registered!'
- L290:     treasurerunmultichestplugin_a507471a: ✅ TreasureRunMultiChestPlugin が正常に起動しました！
- L291:     treasurerunmultichestplugin_c2111711: '🔻 TreasureRunMultiChestPlugin: 無効化'
- L292:     language_30e61e02: भाषा / Language
- L293:     text_4f7e7cfa: नेदराइट इंगॉट प्राप्त!
- L294:     text_1a6173d3: हीरा प्राप्त!
- L295:     text_b6568a8d: सोने का सेब प्राप्त!
- ... and 25 more

### is.yml
- L133:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L135:     text_e096c9a4: '&6特製エメラルド'
- L137:     items_current_0629a198: items current=
- L138:     cursor_7fabae3f: / cursor=
- L143:     shift_53e51de9: ', shift='
- L154:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L170:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L184:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L185:     count_int_not_null_660eadd8: count INT NOT NULL)
- L187:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L188:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L190:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L191:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L192:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L193:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L194:     text_cc353737: 全ての宝物を発見しました！
- L195:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L258:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L264:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L267:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### it.yml
- L135:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L137:     text_e096c9a4: '&6特製エメラルド'
- L139:     items_current_0629a198: items current=
- L140:     cursor_7fabae3f: / cursor=
- L145:     shift_53e51de9: ', shift='
- L156:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L172:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L186:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L187:     count_int_not_null_660eadd8: count INT NOT NULL)
- L189:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L190:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L192:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L193:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L194:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L195:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L196:     text_cc353737: 全ての宝物を発見しました！
- L197:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L260:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L266:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L269:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### ja.yml
- L126:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L128:     text_e096c9a4: '&6特製エメラルド'
- L130:     items_current_0629a198: items current=
- L131:     cursor_7fabae3f: / cursor=
- L136:     shift_53e51de9: ', shift='
- L147:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L163:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L177:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L178:     count_int_not_null_660eadd8: count INT NOT NULL)
- L180:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L181:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L183:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L184:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L185:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L186:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L187:     text_cc353737: 全ての宝物を発見しました！
- L188:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L251:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L257:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L260:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### ko.yml
- L127:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L129:     text_e096c9a4: '&6特製エメラルド'
- L131:     items_current_0629a198: items current=
- L132:     cursor_7fabae3f: / cursor=
- L137:     shift_53e51de9: ', shift='
- L148:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L164:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L178:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L179:     count_int_not_null_660eadd8: count INT NOT NULL)
- L181:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L182:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L184:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L185:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L186:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L187:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L188:     text_cc353737: 全ての宝物を発見しました！
- L189:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L252:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L258:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L261:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### la.yml
- L133:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L135:     text_e096c9a4: '&6特製エメラルド'
- L137:     items_current_0629a198: items current=
- L138:     cursor_7fabae3f: / cursor=
- L143:     shift_53e51de9: ', shift='
- L154:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L170:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L184:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L185:     count_int_not_null_660eadd8: count INT NOT NULL)
- L187:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L188:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L190:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L191:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L192:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L193:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L194:     text_cc353737: 全ての宝物を発見しました！
- L195:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L258:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L264:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L267:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### lzh.yml
- L231:     playerscore_b91d4cd3: 'PlayerScore 插入失敗:'
- L232:     messages_yml_not_loaded_d56b1cd3: (messages.yml not loaded)
- L233:     translation_missing_77031543: '(譯文缺失:'
- L235:     click_to_select_aac1569c: 點擊以選
- L236:     treasurerun_lang_config_yml__2dde4625: '[TreasureRun][Lang] config.yml 無 ''language:''
- L239:     treasurerun_lang_default_60887a83: '[TreasureRun][Lang] default='
- L243:     treasurerun_lang_material_la_bc70a401: '[TreasureRun][Lang] 無效 Material: lang='
- L244:     treasurerun_lang_language_ic_4bc2362d: '[TreasureRun][Lang] 無 ''language.iconMaterial:''
- L260:     light_fell_evenly_and_every__e50aee87: 'Light fell evenly, ,
- L265:     the_wind_fell_silent_and_eve_a96036c1: 'The wind fell silent.
- L274:     playerlanguagestore_failed_t_c4999402: '[PlayerLanguageStore] Failed to create/load
- L276:     playerlanguagestore_failed_t_b6478ec8: '[PlayerLanguageStore] Failed to save player_languages.yml:'
- L282:     treasurerunmultichestplugin_4499e25c: '🌈 TreasureRunMultiChestPlugin: 起動 🌈'
- L283:     treasurerun_gamestagemanager_3d9e7f5a: '[TreasureRun] GameStageManager event registered!'
- L284:     treasurerunmultichestplugin_a507471a: ✅ TreasureRunMultiChestPlugin が正常に起動しました！
- L285:     treasurerunmultichestplugin_c2111711: '🔻 TreasureRunMultiChestPlugin: 無効化'
- L286:     language_30e61e02: 語言 / Language
- L287:     text_4f7e7cfa: 得下界合金錠！
- L288:     text_1a6173d3: 得鑽石！
- L289:     text_b6568a8d: 得金蘋果！
- ... and 26 more

### nl.yml
- L133:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L135:     text_e096c9a4: '&6特製エメラルド'
- L137:     items_current_0629a198: items current=
- L138:     cursor_7fabae3f: / cursor=
- L143:     shift_53e51de9: ', shift='
- L154:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L170:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L184:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L185:     count_int_not_null_660eadd8: count INT NOT NULL)
- L187:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L188:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L190:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L191:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L192:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L193:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L194:     text_cc353737: 全ての宝物を発見しました！
- L195:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L258:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L264:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L267:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### pt.yml
- L233:     playerscore_b91d4cd3: 'Falha ao inserir PlayerScore:'
- L234:     messages_yml_not_loaded_d56b1cd3: (messages.yml not loaded)
- L235:     translation_missing_77031543: '(Tradução ausente:'
- L237:     click_to_select_aac1569c: Clique para selecionar
- L238:     treasurerun_lang_config_yml__2dde4625: '[TreasureRun][Lang] Seção ''language:''
- L241:     treasurerun_lang_default_60887a83: '[TreasureRun][Lang] default='
- L245:     treasurerun_lang_material_la_bc70a401: '[TreasureRun][Lang] Material inválido:
- L247:     treasurerun_lang_language_ic_4bc2362d: '[TreasureRun][Lang] Seção ''language.iconMaterial:''
- L268:     light_fell_evenly_and_every__e50aee87: 'Light fell evenly, ,
- L273:     the_wind_fell_silent_and_eve_a96036c1: 'The wind fell silent.
- L282:     playerlanguagestore_failed_t_c4999402: '[PlayerLanguageStore] Failed to create/load
- L284:     playerlanguagestore_failed_t_b6478ec8: '[PlayerLanguageStore] Failed to save player_languages.yml:'
- L290:     treasurerunmultichestplugin_4499e25c: '🌈 TreasureRunMultiChestPlugin: 起動 🌈'
- L291:     treasurerun_gamestagemanager_3d9e7f5a: '[TreasureRun] GameStageManager event registered!'
- L292:     treasurerunmultichestplugin_a507471a: ✅ TreasureRunMultiChestPlugin が正常に起動しました！
- L293:     treasurerunmultichestplugin_c2111711: '🔻 TreasureRunMultiChestPlugin: 無効化'
- L294:     language_30e61e02: Idioma / Language
- L295:     text_4f7e7cfa: Lingote de Netherite obtido!
- L296:     text_1a6173d3: Diamante obtido!
- L297:     text_b6568a8d: Maçã Dourada obtida!
- ... and 26 more

### ru.yml
- L132:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L134:     text_e096c9a4: '&6特製エメラルド'
- L136:     items_current_0629a198: items current=
- L137:     cursor_7fabae3f: / cursor=
- L142:     shift_53e51de9: ', shift='
- L153:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L169:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L183:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L184:     count_int_not_null_660eadd8: count INT NOT NULL)
- L186:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L187:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L189:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L190:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L191:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L192:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L193:     text_cc353737: 全ての宝物を発見しました！
- L194:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L257:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L263:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L266:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### sa.yml
- L131:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L133:     text_e096c9a4: '&6特製エメラルド'
- L135:     items_current_0629a198: items current=
- L136:     cursor_7fabae3f: / cursor=
- L141:     shift_53e51de9: ', shift='
- L152:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L168:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L182:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L183:     count_int_not_null_660eadd8: count INT NOT NULL)
- L185:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L186:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L188:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L189:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L190:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L191:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L192:     text_cc353737: 全ての宝物を発見しました！
- L193:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L256:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L262:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L265:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### sv.yml
- L132:     backup_cfe9e6cf: 🌊 Backup 海探索で海を検出しました
- L134:     text_e096c9a4: '&6特製エメラルド'
- L136:     items_current_0629a198: items current=
- L137:     cursor_7fabae3f: / cursor=
- L142:     shift_53e51de9: ', shift='
- L153:     ingredients_snapshot_98cb38c3: ingredients snapshot
- L169:     k_llun_r_dj_pinu_16494877: '&6&lKöllun úr djúpinu'
- L183:     player_name_varchar_50_prima_79b8331b: player_name VARCHAR(50) PRIMARY KEY,
- L184:     count_int_not_null_660eadd8: count INT NOT NULL)
- L186:     reset_treasure_count_for_pla_96b152d2: 'Reset treasure count for player:'
- L187:     dj_onalltreasurescollected_a871afdd: DJイベントはすでに実行中のため、onAllTreasuresCollected
- L189:     on_duplicate_key_update_coun_a1543779: ON DUPLICATE KEY UPDATE count = ?
- L190:     failed_to_save_treasure_coun_9d94c0cc: 'Failed to save treasure count:'
- L191:     dj_triggerultimatedjevent_eb01ecae: DJイベントはすでに実行中のため、triggerUltimateDJEvent をスキップしました。
- L192:     treasure_complete_8360fbe9: 🎵 Treasure Complete! 🎵
- L193:     text_cc353737: 全ての宝物を発見しました！
- L194:     congratulations_38fd1594: §6Congratulations! 全ての宝物を見つけました！
- L257:     treasurerun_favorites_templa_a0a4bf2e: '[TreasureRun] favorites_templates.yml
- L263:     quotefavoritestore_reloaded__3ba623a3: '[QuoteFavoriteStore] Reloaded DB: host='
- L266:     quotefavoritestore_exists_fa_7d08adba: '[QuoteFavoriteStore] exists failed:'
- ... and 21 more

### zh_tw.yml
- L231:     playerscore_b91d4cd3: 'PlayerScore 插入失敗:'
- L232:     messages_yml_not_loaded_d56b1cd3: (messages.yml not loaded)
- L233:     translation_missing_77031543: '(找不到翻譯:'
- L235:     click_to_select_aac1569c: 點擊選擇
- L236:     treasurerun_lang_config_yml__2dde4625: '[TreasureRun][Lang] config.yml 中找不到 ''language:''
- L239:     treasurerun_lang_default_60887a83: '[TreasureRun][Lang] default='
- L243:     treasurerun_lang_material_la_bc70a401: '[TreasureRun][Lang] 無效的 Material: lang='
- L244:     treasurerun_lang_language_ic_4bc2362d: '[TreasureRun][Lang] 找不到 ''language.iconMaterial:''
- L260:     light_fell_evenly_and_every__e50aee87: 'Light fell evenly, ,
- L265:     the_wind_fell_silent_and_eve_a96036c1: 'The wind fell silent.
- L274:     playerlanguagestore_failed_t_c4999402: '[PlayerLanguageStore] Failed to create/load
- L276:     playerlanguagestore_failed_t_b6478ec8: '[PlayerLanguageStore] Failed to save player_languages.yml:'
- L282:     treasurerunmultichestplugin_4499e25c: '🌈 TreasureRunMultiChestPlugin: 起動 🌈'
- L283:     treasurerun_gamestagemanager_3d9e7f5a: '[TreasureRun] GameStageManager event registered!'
- L284:     treasurerunmultichestplugin_a507471a: ✅ TreasureRunMultiChestPlugin が正常に起動しました！
- L285:     treasurerunmultichestplugin_c2111711: '🔻 TreasureRunMultiChestPlugin: 無効化'
- L286:     language_30e61e02: 語言 / Language
- L287:     text_4f7e7cfa: 獲得下界合金錠！
- L288:     text_1a6173d3: 獲得鑽石！
- L289:     text_b6568a8d: 獲得金蘋果！
- ... and 26 more

## Debug / internal (375)
### asl_gloss.yml
- L130:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L133:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L134:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L135:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L138:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L140:     merchant_view_title_daf2a73a: merchant view title=
- L142:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L143:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L144:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L145:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L147:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L149:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L151:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L153:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L154:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L155:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L156:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L158:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L161:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L163:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### de.yml
- L137:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L140:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L141:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L142:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L145:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L147:     merchant_view_title_daf2a73a: merchant view title=
- L149:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L150:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L151:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L152:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L154:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L156:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L158:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L160:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L161:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L162:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L163:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L165:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L168:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L170:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### en.yml
- L136:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L139:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L140:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L141:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L144:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L146:     merchant_view_title_daf2a73a: merchant view title=
- L148:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L149:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L150:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L151:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L153:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L155:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L157:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L159:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L160:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L161:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L162:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L164:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L167:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L169:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### es.yml
- L138:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L141:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L142:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L143:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L146:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L148:     merchant_view_title_daf2a73a: merchant view title=
- L150:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L151:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L152:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L153:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L155:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L157:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L159:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L161:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L162:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L163:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L164:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L166:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L169:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L171:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### fi.yml
- L136:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L139:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L140:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L141:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L144:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L146:     merchant_view_title_daf2a73a: merchant view title=
- L148:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L149:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L150:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L151:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L153:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L155:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L157:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L159:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L160:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L161:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L162:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L164:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L167:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L169:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### fr.yml
- L137:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L140:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L141:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L142:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L145:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L147:     merchant_view_title_daf2a73a: merchant view title=
- L149:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L150:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L151:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L152:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L154:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L156:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L158:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L160:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L161:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L162:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L163:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L165:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L168:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L170:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### is.yml
- L136:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L139:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L140:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L141:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L144:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L146:     merchant_view_title_daf2a73a: merchant view title=
- L148:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L149:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L150:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L151:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L153:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L155:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L157:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L159:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L160:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L161:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L162:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L164:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L167:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L169:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### it.yml
- L138:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L141:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L142:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L143:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L146:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L148:     merchant_view_title_daf2a73a: merchant view title=
- L150:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L151:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L152:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L153:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L155:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L157:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L159:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L161:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L162:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L163:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L164:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L166:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L169:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L171:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### ja.yml
- L129:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L132:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L133:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L134:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L137:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L139:     merchant_view_title_daf2a73a: merchant view title=
- L141:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L142:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L143:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L144:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L146:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L148:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L150:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L152:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L153:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L154:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L155:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L157:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L160:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L162:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### ko.yml
- L130:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L133:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L134:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L135:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L138:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L140:     merchant_view_title_daf2a73a: merchant view title=
- L142:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L143:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L144:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L145:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L147:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L149:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L151:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L153:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L154:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L155:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L156:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L158:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L161:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L163:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### la.yml
- L136:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L139:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L140:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L141:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L144:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L146:     merchant_view_title_daf2a73a: merchant view title=
- L148:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L149:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L150:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L151:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L153:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L155:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L157:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L159:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L160:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L161:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L162:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L164:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L167:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L169:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### nl.yml
- L136:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L139:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L140:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L141:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L144:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L146:     merchant_view_title_daf2a73a: merchant view title=
- L148:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L149:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L150:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L151:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L153:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L155:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L157:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L159:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L160:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L161:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L162:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L164:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L167:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L169:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### ru.yml
- L135:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L138:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L139:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L140:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L143:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L145:     merchant_view_title_daf2a73a: merchant view title=
- L147:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L148:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L149:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L150:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L152:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L154:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L156:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L158:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L159:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L160:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L161:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L163:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L166:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L168:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### sa.yml
- L134:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L137:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L138:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L139:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L142:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L144:     merchant_view_title_daf2a73a: merchant view title=
- L146:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L147:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L148:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L149:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L151:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L153:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L155:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L157:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L158:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L159:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L160:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L162:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L165:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L167:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

### sv.yml
- L135:     inventoryclickevent_fired_8520aa1c: InventoryClickEvent fired
- L138:     return_whoclicked_is_not_pla_a7675648: 'RETURN: whoClicked is not Player'
- L139:     return_topinventory_is_not_m_9936e6de: 'RETURN: topInventory is not MERCHANT'
- L140:     return_not_result_slot_expec_9eb8c2c8: 'RETURN: not result slot. expected rawSlot=2
- L143:     return_topinventory_is_merch_fa2f5d98: 'RETURN: topInventory is MERCHANT but not
- L145:     merchant_view_title_daf2a73a: merchant view title=
- L147:     return_not_treasure_shop_tit_8e65ad3d: 'RETURN: not Treasure Shop title. plainTitle='
- L148:     return_current_item_is_null_0f27c9fd: 'RETURN: current item is null'
- L149:     return_current_item_is_air_76b5e623: 'RETURN: current item is AIR'
- L150:     return_current_item_is_not_g_d1d65218: 'RETURN: current item is not GOLDEN_APPLE.
- L152:     return_game_is_not_running_048d7edb: 'RETURN: game is not running'
- L154:     return_ingredient_check_fail_673860d5: 'RETURN: ingredient check failed (need
- L156:     ok_passed_all_checks_schedul_11e17905: 'OK: passed all checks -> scheduling effect
- L158:     run_runtasklater_executed_96d7e049: 'RUN: runTaskLater executed'
- L159:     return_later_game_is_not_run_9cc569aa: 'RETURN(LATER): game is not running'
- L160:     return_later_player_is_offli_863a8cdc: 'RETURN(LATER): player is offline'
- L161:     return_later_openinventory_t_7b809b57: 'RETURN(LATER): openInventory/topInventory
- L163:     return_later_topinventory_is_f8714cf5: 'RETURN(LATER): topInventory is not MERCHANT.
- L166:     return_later_not_treasure_sh_fcce2bb9: 'RETURN(LATER): not Treasure Shop title.
- L168:     ok_later_playing_effects_now_b80a2e0e: 'OK(LATER): playing effects now'
- ... and 5 more

## SQL / technical (259)
### asl_gloss.yml
- L174:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L175:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L176:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L180:     failed_to_create_table_68199f98: 'Failed to create table:'
- L254:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L259:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L262:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L264:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L266:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L268:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L271:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L272:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L274:     where_player_uuid_ef617232: WHERE player_uuid=?

### de.yml
- L181:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L182:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L183:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L187:     failed_to_create_table_68199f98: 'Failed to create table:'
- L261:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L266:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L269:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L271:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L273:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L275:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L278:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L279:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L281:     where_player_uuid_ef617232: WHERE player_uuid=?

### en.yml
- L180:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L181:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L182:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L186:     failed_to_create_table_68199f98: 'Failed to create table:'
- L260:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L265:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L268:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L270:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L272:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L274:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L277:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L278:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L280:     where_player_uuid_ef617232: WHERE player_uuid=?

### es.yml
- L182:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L183:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L184:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L188:     failed_to_create_table_68199f98: 'Failed to create table:'
- L262:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L267:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L270:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L272:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L274:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L276:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L279:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L280:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L282:     where_player_uuid_ef617232: WHERE player_uuid=?

### fi.yml
- L180:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L181:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L182:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L186:     failed_to_create_table_68199f98: 'Failed to create table:'
- L260:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L265:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L268:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L270:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L272:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L274:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L277:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L278:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L280:     where_player_uuid_ef617232: WHERE player_uuid=?

### fr.yml
- L181:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L182:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L183:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L187:     failed_to_create_table_68199f98: 'Failed to create table:'
- L261:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L266:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L269:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L271:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L273:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L275:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L278:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L279:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L281:     where_player_uuid_ef617232: WHERE player_uuid=?

### hi.yml
- L251:     enemydown_mysql_jdbc_mybatis_6b5c6f68: '[EnemyDown] MySQL से जुड़े（JDBC + MyBatis）'
- L252:     enemydown_mysql_6cf3e29b: '[EnemyDown] MySQL कनेक्शन विफल:'
- L253:     enemydown_mysql_a18990c3: '[EnemyDown] MySQL कनेक्शन बंद।'
- L254:     enemydown_mysql_e097a73e: '[EnemyDown] MySQL डिस्कनेक्ट विफल:'
- L255:     mysqlmanager_player_score_d92afff4: '[MySQLManager] player_score तालिका जाँची/बनाई
- L257:     mysqlmanager_player_score_21665c62: '[MySQLManager] player_score तालिका बनाने
- L259:     mysqlmanager_mysql_951100b9: '[MySQLManager] MySQL कनेक्शन परीक्षण सफल ✅'
- L260:     mysqlmanager_player_score_a4968d00: '[MySQLManager] player_score तालिका की पंक्तियाँ:'
- L261:     mysqlmanager_mysql_1763d390: '[MySQLManager] MySQL कनेक्ट नहीं ❌'
- L262:     mysqlmanager_player_score_2b5c8721: '[MySQLManager] player_score तालिका मौजूद
- L264:     enemydown_mysql_bc514e56: '[EnemyDown] MySQL जुड़ा हुआ है।'
- L265:     enemydown_mysql_93b23ef9: '[EnemyDown] MySQL जुड़ा नहीं है।'
- L283:     proverb_logged_to_mysql_prov_c13b5e4e: 'Proverb logged to MySQL: proverb_logs'
- L284:     failed_to_log_proverb_to_mys_2bb4c5c3: 'Failed to log proverb to MySQL: proverb_logs'
- L285:     loaded_proverb_logs_from_mys_b5b0f8e0: 'Loaded proverb logs from MySQL: proverb_logs'
- L286:     failed_to_load_proverb_logs__6246be38: 'Failed to load proverb logs from MySQL:

### is.yml
- L180:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L181:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L182:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L186:     failed_to_create_table_68199f98: 'Failed to create table:'
- L260:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L265:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L268:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L270:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L272:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L274:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L277:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L278:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L280:     where_player_uuid_ef617232: WHERE player_uuid=?

### it.yml
- L182:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L183:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L184:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L188:     failed_to_create_table_68199f98: 'Failed to create table:'
- L262:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L267:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L270:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L272:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L274:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L276:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L279:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L280:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L282:     where_player_uuid_ef617232: WHERE player_uuid=?

### ja.yml
- L173:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L174:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L175:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L179:     failed_to_create_table_68199f98: 'Failed to create table:'
- L253:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L258:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L261:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L263:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L265:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L267:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L270:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L271:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L273:     where_player_uuid_ef617232: WHERE player_uuid=?

### ko.yml
- L174:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L175:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L176:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L180:     failed_to_create_table_68199f98: 'Failed to create table:'
- L254:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L259:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L262:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L264:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L266:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L268:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L271:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L272:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L274:     where_player_uuid_ef617232: WHERE player_uuid=?

### la.yml
- L180:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L181:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L182:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L186:     failed_to_create_table_68199f98: 'Failed to create table:'
- L260:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L265:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L268:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L270:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L272:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L274:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L277:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L278:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L280:     where_player_uuid_ef617232: WHERE player_uuid=?

### lzh.yml
- L248:     enemydown_mysql_jdbc_mybatis_6b5c6f68: '[EnemyDown] 已連MySQL（JDBC + MyBatis）'
- L249:     enemydown_mysql_6cf3e29b: '[EnemyDown] MySQL連接失敗:'
- L250:     enemydown_mysql_a18990c3: '[EnemyDown] MySQL已斷。'
- L251:     enemydown_mysql_e097a73e: '[EnemyDown] MySQL斷接失敗:'
- L252:     mysqlmanager_player_score_d92afff4: '[MySQLManager] player_score 表已確認 ✅'
- L253:     mysqlmanager_player_score_21665c62: '[MySQLManager] player_score 表建立失敗 ❌:'
- L254:     mysqlmanager_mysql_951100b9: '[MySQLManager] MySQL 測試成功 ✅'
- L255:     mysqlmanager_player_score_a4968d00: '[MySQLManager] player_score 表行數:'
- L256:     mysqlmanager_mysql_1763d390: '[MySQLManager] MySQL 未連 ❌'
- L257:     mysqlmanager_player_score_2b5c8721: '[MySQLManager] player_score 表或不存 ❌'
- L258:     enemydown_mysql_bc514e56: '[EnemyDown] MySQL 已連。'
- L259:     enemydown_mysql_93b23ef9: '[EnemyDown] MySQL 未連。'
- L277:     proverb_logged_to_mysql_prov_c13b5e4e: 'Proverb logged to MySQL: proverb_logs'
- L278:     failed_to_log_proverb_to_mys_2bb4c5c3: 'Failed to log proverb to MySQL: proverb_logs'
- L279:     loaded_proverb_logs_from_mys_b5b0f8e0: 'Loaded proverb logs from MySQL: proverb_logs'
- L280:     failed_to_load_proverb_logs__6246be38: 'Failed to load proverb logs from MySQL:

### nl.yml
- L180:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L181:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L182:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L186:     failed_to_create_table_68199f98: 'Failed to create table:'
- L260:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L265:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L268:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L270:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L272:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L274:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L277:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L278:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L280:     where_player_uuid_ef617232: WHERE player_uuid=?

### pt.yml
- L251:     enemydown_mysql_jdbc_mybatis_6b5c6f68: '[EnemyDown] Conectado ao MySQL（JDBC +
- L253:     enemydown_mysql_6cf3e29b: '[EnemyDown] Falha na conexão MySQL:'
- L254:     enemydown_mysql_a18990c3: '[EnemyDown] Conexão MySQL encerrada.'
- L255:     enemydown_mysql_e097a73e: '[EnemyDown] Falha ao desconectar MySQL:'
- L256:     mysqlmanager_player_score_d92afff4: '[MySQLManager] Tabela player_score verificada/criada
- L258:     mysqlmanager_player_score_21665c62: '[MySQLManager] Falha ao criar tabela player_score
- L260:     mysqlmanager_mysql_951100b9: '[MySQLManager] Teste de conexão MySQL bem-sucedido
- L262:     mysqlmanager_player_score_a4968d00: '[MySQLManager] Linhas na tabela player_score:'
- L263:     mysqlmanager_mysql_1763d390: '[MySQLManager] MySQL não conectado ❌'
- L264:     mysqlmanager_player_score_2b5c8721: '[MySQLManager] Tabela player_score pode não
- L266:     enemydown_mysql_bc514e56: '[EnemyDown] MySQL está conectado.'
- L267:     enemydown_mysql_93b23ef9: '[EnemyDown] MySQL não está conectado.'
- L285:     proverb_logged_to_mysql_prov_c13b5e4e: 'Proverb logged to MySQL: proverb_logs'
- L286:     failed_to_log_proverb_to_mys_2bb4c5c3: 'Failed to log proverb to MySQL: proverb_logs'
- L287:     loaded_proverb_logs_from_mys_b5b0f8e0: 'Loaded proverb logs from MySQL: proverb_logs'
- L288:     failed_to_load_proverb_logs__6246be38: 'Failed to load proverb logs from MySQL:

### ru.yml
- L179:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L180:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L181:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L185:     failed_to_create_table_68199f98: 'Failed to create table:'
- L259:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L264:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L267:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L269:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L271:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L273:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L276:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L277:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L279:     where_player_uuid_ef617232: WHERE player_uuid=?

### sa.yml
- L178:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L179:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L180:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L184:     failed_to_create_table_68199f98: 'Failed to create table:'
- L258:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L263:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L266:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L268:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L270:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L272:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L275:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L276:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L278:     where_player_uuid_ef617232: WHERE player_uuid=?

### sv.yml
- L179:     mysql_connection_established_47452dfd: MySQL connection established successfully!
- L180:     failed_to_connect_to_mysql_95e4d50a: 'Failed to connect to MySQL:'
- L181:     create_table_if_not_exists_p_136a7833: CREATE TABLE IF NOT EXISTS player_treasure_count
- L185:     failed_to_create_table_68199f98: 'Failed to create table:'
- L259:     mysql_repository_not_ready_72df0af1: MySQL / Repository not ready.
- L264:     where_player_uuid_and_quote__36106f85: WHERE player_uuid=? AND quote_hash=? LIMIT
- L267:     player_uuid_quote_hash_outco_49f06b31: (player_uuid, quote_hash, outcome, difficulty,
- L269:     values_643f4d2f: VALUES (?, ?, ?, ?, ?, ?)
- L271:     where_player_uuid_and_quote__81bcaee5: WHERE player_uuid=? AND quote_hash=?
- L273:     where_player_uuid_and_id_f4a80773: WHERE player_uuid=? AND id=?
- L276:     where_player_uuid_53558ea9: WHERE player_uuid=?
- L277:     order_by_created_at_desc_lim_f22988c0: ORDER BY created_at DESC LIMIT ?
- L279:     where_player_uuid_ef617232: WHERE player_uuid=?

### zh_tw.yml
- L248:     enemydown_mysql_jdbc_mybatis_6b5c6f68: '[EnemyDown] 已連接至MySQL（JDBC + MyBatis）'
- L249:     enemydown_mysql_6cf3e29b: '[EnemyDown] MySQL連接失敗:'
- L250:     enemydown_mysql_a18990c3: '[EnemyDown] MySQL連接已斷開。'
- L251:     enemydown_mysql_e097a73e: '[EnemyDown] MySQL斷開失敗:'
- L252:     mysqlmanager_player_score_d92afff4: '[MySQLManager] player_score 資料表已確認/建立 ✅'
- L253:     mysqlmanager_player_score_21665c62: '[MySQLManager] player_score 資料表建立失敗 ❌:'
- L254:     mysqlmanager_mysql_951100b9: '[MySQLManager] MySQL 連接測試成功 ✅'
- L255:     mysqlmanager_player_score_a4968d00: '[MySQLManager] player_score 資料表行數:'
- L256:     mysqlmanager_mysql_1763d390: '[MySQLManager] MySQL 未連接 ❌'
- L257:     mysqlmanager_player_score_2b5c8721: '[MySQLManager] player_score 資料表可能不存在 ❌'
- L258:     enemydown_mysql_bc514e56: '[EnemyDown] MySQL 已連接。'
- L259:     enemydown_mysql_93b23ef9: '[EnemyDown] MySQL 未連接。'
- L277:     proverb_logged_to_mysql_prov_c13b5e4e: 'Proverb logged to MySQL: proverb_logs'
- L278:     failed_to_log_proverb_to_mys_2bb4c5c3: 'Failed to log proverb to MySQL: proverb_logs'
- L279:     loaded_proverb_logs_from_mys_b5b0f8e0: 'Loaded proverb logs from MySQL: proverb_logs'
- L280:     failed_to_load_proverb_logs__6246be38: 'Failed to load proverb logs from MySQL:

## Intentionally non-localized (75)
### asl_gloss.yml
- L128:     treasure_shop_4b968fbd: Treasure Shop
- L141:     treasure_shop_2574a694: treasure shop
- L165:     trade_complete_4a4b1d6f: Trade complete!
- L166:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L168:     rainbow_wolf_b3cc6257: Rainbow Wolf

### de.yml
- L135:     treasure_shop_4b968fbd: Treasure Shop
- L148:     treasure_shop_2574a694: treasure shop
- L172:     trade_complete_4a4b1d6f: Trade complete!
- L173:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L175:     rainbow_wolf_b3cc6257: Rainbow Wolf

### en.yml
- L134:     treasure_shop_4b968fbd: Treasure Shop
- L147:     treasure_shop_2574a694: treasure shop
- L171:     trade_complete_4a4b1d6f: Trade complete!
- L172:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L174:     rainbow_wolf_b3cc6257: Rainbow Wolf

### es.yml
- L136:     treasure_shop_4b968fbd: Treasure Shop
- L149:     treasure_shop_2574a694: treasure shop
- L173:     trade_complete_4a4b1d6f: Trade complete!
- L174:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L176:     rainbow_wolf_b3cc6257: Rainbow Wolf

### fi.yml
- L134:     treasure_shop_4b968fbd: Treasure Shop
- L147:     treasure_shop_2574a694: treasure shop
- L171:     trade_complete_4a4b1d6f: Trade complete!
- L172:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L174:     rainbow_wolf_b3cc6257: Rainbow Wolf

### fr.yml
- L135:     treasure_shop_4b968fbd: Treasure Shop
- L148:     treasure_shop_2574a694: treasure shop
- L172:     trade_complete_4a4b1d6f: Trade complete!
- L173:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L175:     rainbow_wolf_b3cc6257: Rainbow Wolf

### is.yml
- L134:     treasure_shop_4b968fbd: Treasure Shop
- L147:     treasure_shop_2574a694: treasure shop
- L171:     trade_complete_4a4b1d6f: Trade complete!
- L172:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L174:     rainbow_wolf_b3cc6257: Rainbow Wolf

### it.yml
- L136:     treasure_shop_4b968fbd: Treasure Shop
- L149:     treasure_shop_2574a694: treasure shop
- L173:     trade_complete_4a4b1d6f: Trade complete!
- L174:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L176:     rainbow_wolf_b3cc6257: Rainbow Wolf

### ja.yml
- L127:     treasure_shop_4b968fbd: Treasure Shop
- L140:     treasure_shop_2574a694: treasure shop
- L164:     trade_complete_4a4b1d6f: Trade complete!
- L165:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L167:     rainbow_wolf_b3cc6257: Rainbow Wolf

### ko.yml
- L128:     treasure_shop_4b968fbd: Treasure Shop
- L141:     treasure_shop_2574a694: treasure shop
- L165:     trade_complete_4a4b1d6f: Trade complete!
- L166:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L168:     rainbow_wolf_b3cc6257: Rainbow Wolf

### la.yml
- L134:     treasure_shop_4b968fbd: Treasure Shop
- L147:     treasure_shop_2574a694: treasure shop
- L171:     trade_complete_4a4b1d6f: Trade complete!
- L172:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L174:     rainbow_wolf_b3cc6257: Rainbow Wolf

### nl.yml
- L134:     treasure_shop_4b968fbd: Treasure Shop
- L147:     treasure_shop_2574a694: treasure shop
- L171:     trade_complete_4a4b1d6f: Trade complete!
- L172:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L174:     rainbow_wolf_b3cc6257: Rainbow Wolf

### ru.yml
- L133:     treasure_shop_4b968fbd: Treasure Shop
- L146:     treasure_shop_2574a694: treasure shop
- L170:     trade_complete_4a4b1d6f: Trade complete!
- L171:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L173:     rainbow_wolf_b3cc6257: Rainbow Wolf

### sa.yml
- L132:     treasure_shop_4b968fbd: Treasure Shop
- L145:     treasure_shop_2574a694: treasure shop
- L169:     trade_complete_4a4b1d6f: Trade complete!
- L170:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L172:     rainbow_wolf_b3cc6257: Rainbow Wolf

### sv.yml
- L133:     treasure_shop_4b968fbd: Treasure Shop
- L146:     treasure_shop_2574a694: treasure shop
- L170:     trade_complete_4a4b1d6f: Trade complete!
- L171:     a_hidden_power_awakens_7620bc4d: A hidden power awakens…
- L173:     rainbow_wolf_b3cc6257: Rainbow Wolf
