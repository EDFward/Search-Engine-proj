import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Indri retrieval model in homework2
 *
 * @author junjiah
 */
public class RetrievalModelIndri extends RetrievalModel {

  /**
   * Parameter mu in query likelihood calculation.
   */
  private int mu;

  /**
   * Parameter lambda in query likelihood calculation.
   */
  private double lambda;

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
      Utility.fatalError("Error: Unknown parameter name for retrieval model " +
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
      Utility.fatalError("Error: Unknown parameter name for retrieval model " +
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

  public double getScore(String[] queryStems, TermVector doc) throws IOException {
    double totalScore = 0;
    String field = doc.getField();
    long docLen = QryEval.LENGTH_STORE.getDocLength(field, doc.getDocId());
    long totalTermFreq = QryEval.READER.getSumTotalTermFreq(field);

    Map<String, Integer> docStemMap = new HashMap<String, Integer>(doc.stemsLength());
    for (int i = 0; i < doc.stemsLength(); ++i)
      docStemMap.put(doc.stemString(i), i);

    int noMatchCount = 0, tf;
    long ctf;
    for (String queryStem : queryStems) {
      if (!docStemMap.containsKey(queryStem)) {
        ++noMatchCount;
        tf = 0;
        ctf = QryEval.READER.totalTermFreq(new Term(field, new BytesRef(queryStem)));
      } else {
        int i = docStemMap.get(queryStem);
        tf = doc.stemFreq(i);
        ctf = doc.totalStemFreq(i);
      }
      double ctfProb = ((double) ctf) / totalTermFreq;
      totalScore *= lambda * (tf + mu * ctfProb) / (docLen + mu) + (1 - lambda) * ctfProb;
    }

    if (noMatchCount == queryStems.length)
      return 0;
    else
      return Math.pow(totalScore, queryStems.length);
  }
}
