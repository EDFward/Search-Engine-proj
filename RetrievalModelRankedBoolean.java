/**
 * Created by hejunjia1911 on 9/4/14.
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
            "UnrankedBoolean: " +
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
            "UnrankedBoolean: " +
            parameterName);
    return false;
  }
}
