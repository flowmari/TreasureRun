package plugin.update;

import java.util.List;

@FunctionalInterface
public interface RemoteReleaseLookup {

  List<String> fetchReleaseTags() throws Exception;
}
