/**
 * BM25 retrieval model in homework 2.
 *
 * @author junjiah
 */
public class RetrievalModelLeToR extends RetrievalModel {

  private RetrievalModelBM25 bm25Model;

  private RetrievalModelIndri indriModel;

  private RetrievalModelRankedBoolean rbModel;

  public RetrievalModelLeToR() {
    bm25Model = new RetrievalModelBM25();
    indriModel = new RetrievalModelIndri();
    rbModel = new RetrievalModelRankedBoolean();
  }

  public RetrievalModelBM25 getBm25Model() {
    return bm25Model;
  }

  public RetrievalModelIndri getIndriModel() {
    return indriModel;
  }

  public RetrievalModelRankedBoolean getRankedBooleanModel() {
    return rbModel;
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
    if (parameterName.equals("k_1") ||
            parameterName.equals("k_3") ||
            parameterName.equals(("b"))) {
      bm25Model.setParameter(parameterName, value);
    } else if (parameterName.equals("mu") ||
            parameterName.equals("lambda")) {
      indriModel.setParameter(parameterName, value);
    } else {
      Utility.fatalError("Error: Unknown parameter name for retrieval model " +
              "LeToR: " +
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
    if (parameterName.equals("k_1") ||
            parameterName.equals("k_3") ||
            parameterName.equals(("b"))) {
      bm25Model.setParameter(parameterName, value);
    } else if (parameterName.equals("mu") ||
            parameterName.equals("lambda")) {
      indriModel.setParameter(parameterName, value);
    } else {
      Utility.fatalError("Error: Unknown parameter name for retrieval model " +
              "LeToR: " +
              parameterName);
    }
    // successfully set up the parameter
    return true;
  }
}
