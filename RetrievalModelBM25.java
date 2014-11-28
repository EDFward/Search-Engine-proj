import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * BM25 retrieval model in homework 2.
 *
 * @author junjiah
 */
public class RetrievalModelBM25 extends RetrievalModel {

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

  public double getScore(String[] queryStems, int internalDocId, String field) throws IOException {
    TermVector doc = new TermVector(internalDocId, field);
    double totalScore = 0;
    double avgDocLen = QryEval.READER.getSumTotalTermFreq(field) /
            (float) QryEval.READER.getDocCount(field);
    long docLen = QryEval.LENGTH_STORE.getDocLength(field, internalDocId);
    long docCount = QryEval.READER.getDocCount(field);

    // qtf is 1
    double qtf = 1;
    double userWeight = (k_3 + 1) * qtf / (k_3 + qtf);

    Set<String> queryStemSet = new HashSet<String>(Arrays.asList(queryStems));
    for (int i = 0; i < doc.stemsLength(); ++i) {
      String stem = doc.stemString(i);
      if (queryStemSet.contains(stem)) {
        int df = doc.stemDf(i);
        double idf = Math.log((docCount - df + 0.5) / (df + 0.5));
        int tf = doc.stemFreq(i);
        double normTf = tf / (tf + k_1 * (1 - b + b * docLen / avgDocLen));
        totalScore += normTf * idf * userWeight;
      }
    }
    return totalScore;
  }
}
