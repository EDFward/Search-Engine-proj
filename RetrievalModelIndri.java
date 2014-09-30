import java.io.IOException;

/**
 * Indri retrieval model in homework2
 *
 * @author junjiah
 */
public class RetrievalModelIndri extends RetrievalModel {

  /**
   * Initialize the document length reader for future evaluation.
   */
  public RetrievalModelIndri() {
    try {
      docLengthStore = new DocLengthStore(QryEval.READER);
    } catch (IOException e) {
      QryEval.fatalError("Error: DocLengthStore initialization error");
    }
  }

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
    } else if (parameterName.equals("lambda")) {
      lambda = value;
    } else {
      QryEval.fatalError("Error: Unknown parameter name for retrieval model " +
              "RankedBoolean: " +
              parameterName);
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
    if (parameterName.equals("mu")) {
      mu = Integer.parseInt(value);
    } else if (parameterName.equals("lambda")) {
      lambda = Double.parseDouble(value);
    } else {
      QryEval.fatalError("Error: Unknown parameter name for retrieval model " +
              "RankedBoolean: " +
              parameterName);
    }
    // successfully set up the parameter
    return true;
  }

  /**
   * @return lambda in two-state smoothing
   */
  public double getLambda() {
    return lambda;
  }

  /**
   * @return mu in two-state smoothing
   */
  public int getMu() {
    return mu;
  }

  /**
   * @return document length reader
   */
  public DocLengthStore getDocLengthStore() {
    return docLengthStore;
  }

  /**
   * Parameter mu in query likelihood calculation.
   */
  private int mu;

  /**
   * Parameter lambda in query likelihood calculation.
   */
  private double lambda;

  /**
   * Document length reader.
   */
  private DocLengthStore docLengthStore;
}
