import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Ranked Boolean retrieval model in homework 1.
 *
 * @author junjiah
 */
public class RetrievalModelRankedBoolean extends RetrievalModel {

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName
   * @param value
   * @return Always false because this retrieval model has no parameters.
   */
  @Override
  public boolean setParameter(String parameterName, double value) {
    System.err.println("Error: Unknown parameter name for retrieval model " +
            "RankedBoolean: " +
            parameterName);
    return false;
  }

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName
   * @param value
   * @return Always false because this retrieval model has no parameters.
   */
  @Override
  public boolean setParameter(String parameterName, String value) {
    System.err.println("Error: Unknown parameter name for retrieval model " +
            "RankedBoolean: " +
            parameterName);
    return false;
  }

  public double getScore(String[] queryStems, TermVector doc) throws IOException {
    int maxScore = 0;

    Set<String> queryStemSet = new HashSet<String>(Arrays.asList(queryStems));
    for (int i = 0; i < doc.stemsLength(); ++i) {
      if (queryStemSet.contains(doc.stemString(i))) {
        int tf = doc.stemFreq(i);
        if (tf > maxScore)
          maxScore = tf;
      }
    }
    return maxScore;
  }
}
