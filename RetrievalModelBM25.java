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
      Utility.fatalError("Error: Unknown parameter name for retrieval model " +
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
      Utility.fatalError("Error: Unknown parameter name for retrieval model " +
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
   * Get score between a query and a document.
   *
   * @param queryStems Query words
   * @param doc        Document term vector
   * @return BM25 score between the query and the document
   * @throws IOException
   */
  public double getScore(String[] queryStems, TermVector doc) throws IOException {
    double totalScore = 0;
    String field = doc.getField();
    long docCount = QryEval.READER.numDocs();
    double avgDocLen = ((double) QryEval.READER.getSumTotalTermFreq(field)) /
            QryEval.READER.getDocCount(field);
    long docLen = QryEval.LENGTH_STORE.getDocLength(field, doc.getDocId());

    Set<String> queryStemSet = new HashSet<String>(Arrays.asList(queryStems));
    for (int i = 0; i < doc.stemsLength(); ++i) {
      if (queryStemSet.contains(doc.stemString(i))) {
        int df = doc.stemDf(i);
        double idf = Math.log((docCount - df + 0.5) / (df + 0.5));
        int tf = doc.stemFreq(i);
        double normTf = tf / (tf + k_1 * (1 - b + b * docLen / avgDocLen));
        // user weight is always 1
        totalScore += normTf * idf;
      }
    }
    return totalScore;
  }
}
