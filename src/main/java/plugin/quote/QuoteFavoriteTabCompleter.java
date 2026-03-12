package plugin.quote;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QuoteFavoriteTabCompleter implements TabCompleter {

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

    if (args.length == 1) {
      return filter(Arrays.asList("help", "latest", "list", "remove", "reread", "book"), args[0]);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("reread")) {
      return filter(Arrays.asList("chat", "title", "book"), args[1]);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
      return filter(Arrays.asList("full", "toc", "success", "timeup", "other"), args[1]);
    }

    return Collections.emptyList();
  }

  private List<String> filter(List<String> all, String prefix) {
    if (prefix == null || prefix.isEmpty()) return all;
    String p = prefix.toLowerCase();
    List<String> out = new ArrayList<>();
    for (String s : all) {
      if (s.toLowerCase().startsWith(p)) out.add(s);
    }
    return out;
  }
}