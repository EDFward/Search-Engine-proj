import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class QryopSlOr extends QryopSl {

  /**
   * It is convenient for the constructor to accept a variable number
   * of arguments. Thus new QryopSlOr (arg1, arg2, arg3, ...).
   *
   * @param q A query argument (a query operator).
   */
  public QryopSlOr(Qryop... q) {
    for (int i = 0; i < q.length; i++) {
      this.args.add(q[i]);
    }
  }

  @Override
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    return 0.0;
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param {q} q The query argument (query operator) to append.
   * @return void
   * @throws IOException
   */
  @Override
  public void add(Qryop q) throws IOException {
    this.args.add(q);
  }

  /**
   * Evaluates the query operator, including any child operators and
   * returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  @Override
  public QryResult evaluate(RetrievalModel r) throws IOException {
    if (r instanceof RetrievalModelUnrankedBoolean) {
      return (evaluateBoolean(r));
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return evaluateRankedBoolean(r);
    }

    return null;
  }

  /**
   *  Return a string version of this query operator.
   *  @return The string version of this query operator.
   */
  @Override
  public String toString() {
    String result = new String();

    for (Qryop arg : this.args) {
      result += arg.toString() + " ";
    }

    return ("#OR( " + result + ")");
  }

  /**
   * Evaluates the query operator for boolean retrieval models,
   * including any child operators and returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  private QryResult evaluateBoolean(RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    QryResult result = new QryResult();
    // only a set is necessary
    Set<Integer> docidSet = new HashSet<Integer>();

    for (DaaTPtr p : this.daatPtrs) {
      int docSize = p.scoreList.scores.size();
      for (int i = 0; i < docSize; ++i) {
        docidSet.add(p.scoreList.getDocid(i));
      }
    }

    for (int docid : docidSet) {
      result.docScores.add(docid, 1.0);
    }

    freeDaaTPtrs();
    return result;
  }

  /**
   * Evaluates the query operator for ranked boolean retrieval models,
   * including any child operators and returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  private QryResult evaluateRankedBoolean(RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    QryResult result = new QryResult();
    // use hash map to store the scores
    Map<Integer, Double> docIdScoreMap = new HashMap<Integer, Double>();

    for (DaaTPtr p : this.daatPtrs) {
      int docSize = p.scoreList.scores.size();
      for (int i = 0; i < docSize; ++i) {
        int docId = p.scoreList.getDocid(i);
        double docScore = p.scoreList.getDocidScore(i);

        if (!docIdScoreMap.containsKey(docId)) {
          docIdScoreMap.put(docId, docScore);
        } else { // update the map if the score is greater
          double currScore = docIdScoreMap.get(docId);
          if (docScore > currScore) {
            docIdScoreMap.put(docId, docScore);
          }
        }
      }
    }
    // put docId - score into the QryResult
    for (Map.Entry<Integer, Double> entry : docIdScoreMap.entrySet()) {
      result.docScores.add(entry.getKey(), entry.getValue());
    }

    freeDaaTPtrs();
    return result;
  }
}
