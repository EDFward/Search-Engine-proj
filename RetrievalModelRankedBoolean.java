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
}
