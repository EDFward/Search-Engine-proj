import java.io.IOException;

/**
 * BM25 retrieval model in homework 2.
 *
 * @author junjiah
 */
public class RetrievalModelBM25 extends RetrievalModel {
  public RetrievalModelBM25() {
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
    if (parameterName.equals("k_1")) {
      k_1 = value;
    } else if (parameterName.equals("k_3")) {
      k_3 = value;
    } else if (parameterName.equals(("b"))) {
      b = value;
    } else {
      QryEval.fatalError("Error: Unknown parameter name for retrieval model " +
              "BM25: " +
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
    if (parameterName.equals("k_1")) {
      k_1 = Double.parseDouble(value);
    } else if (parameterName.equals("k_3")) {
      k_3 = Double.parseDouble(value);
    } else if (parameterName.equals(("b"))) {
      b = Double.parseDouble(value);
    } else {
      QryEval.fatalError("Error: Unknown parameter name for retrieval model " +
              "BM25: " +
              parameterName);
    }
    // successfully set up the parameter
    return true;
  }

  /**
   * @return k_1 in BM25
   */
  public double getK_1() {
    return k_1;
  }

  /**
   * @return k_3 in BM25
   */
  public double getK_3() {
    return k_3;
  }

  /**
   * @return B in BM25
   */
  public double getB() {
    return b;
  }

  /**
   * Parameter K1 in BM25 retrieval model.
   */
  private double k_1;

  /**
   * Parameter K3 in BM25 retrieval model.
   */
  private double k_3;

  /**
   * Parameter b in BM25 retrieval model.
   */
  private double b;

  public DocLengthStore getDocLengthStore() {
    return docLengthStore;
  }

  private DocLengthStore docLengthStore;
}
