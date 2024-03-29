package nl.knaw.huygens.timbuctoo.remote.rs.view;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import nl.knaw.huygens.timbuctoo.remote.rs.discover.ResultIndex;
import nl.knaw.huygens.timbuctoo.remote.rs.discover.ResultIndexPivot;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for viewing a ResultIndex as a tree.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public class TreeBase {
  private List<TreeResultView> roots;

  public TreeBase(ResultIndex resultIndex) {
    init(resultIndex, new Interpreter());
  }

  public TreeBase(ResultIndex resultIndex, Interpreter interpreter) {
    init(resultIndex, interpreter);
  }

  private void init(ResultIndex resultIndex, Interpreter interpreter) {
    ResultIndexPivot pivot = new ResultIndexPivot(resultIndex);

    roots = pivot.listRsRootResultsByLevel(3).stream()
      .map(rsRootResult -> new TreeResultView(rsRootResult, interpreter))
      .collect(Collectors.toList());
  }

  public List<TreeResultView> getRoots() {
    return roots;
  }
}
