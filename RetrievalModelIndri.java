/**
 * Created by junjiah on 9/28/14.
 */
public class RetrievalModelIndri extends RetrievalModel {
  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName The name of the parameter to set.
   * @param value         The parameter's value.
   * @return true if the parameter is set successfully, false otherwise.
   */
  @Override
  public boolean setParameter(String parameterName, double value) {
    if (parameterName.equals("mu")) {
      mu = (int) value;
    }
    else if (parameterName.equals("lambda")) {
      lambda = value;
    }
    else {
      System.err.println("Error: Unknown parameter name for retrieval model " +
              "RankedBoolean: " +
              parameterName);
      return false;
    }
    // successfully set up the parameter
    return true;
  }

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName The name of the parameter to set.
   * @param value         The parameter's value.
   * @return true if the parameter is set successfully, false otherwise.
   */
  @Override
  public boolean setParameter(String parameterName, String value) {
    System.err.println("Error: Unknown parameter name for retrieval model " +
            "RankedBoolean: " +
            parameterName);
    return false;
  }

  /**
   *
   * @return lambda in two-state smoothing
   */
  public double getLambda() {
    return lambda;
  }

  /**
   *
   * @return mu in two-state smoothing
   */
  public int getMu() {
    return mu;
  }

  /**
   * Parameter mu in query likelihood calculation.
   */
  private int mu;

  /**
   * Parameter lambda in query likelihood calculation.
   */
  private double lambda;
}
