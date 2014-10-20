import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QryopSlWeightedSum extends QryopSlWeighted {

  public QryopSlWeightedSum() {
    super();
  }

  /**
   * Calculate the default score for the specified document if it
   * does not match the query operator.  This score is 0 for many
   * retrieval models, but not all retrieval models.
   *
   * @param r     A retrieval model that controls how the operator behaves.
   * @param docid The internal id of the document that needs a default score.
   * @return The default score.
   */
  @Override
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    if (r instanceof RetrievalModelIndri) {
      double defaultScore = 0;
      for (int i = 0; i < this.args.size(); ++i) {
        Qryop arg = this.args.get(i);
        // they must be QryopSl, so downcast.
        // since they are in log scale, sum them up with corresponding weights
        defaultScore += Math.exp(((QryopSl) arg).getDefaultScore(r, docid)) * weights.get(i);
      }

      // normalize score with total weights
      double totalWeights = 0;
      for (double w : weights) {
        totalWeights += w;
      }

      return Math.log(defaultScore / totalWeights);
    }

    System.err.println("Warning: WSUM only supports Indri.");
    return 0.0;
  }

  /**
   * Evaluates the query operator, including any child operators and
   * returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws java.io.IOException
   */
  @Override
  public QryResult evaluate(RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    if (!(r instanceof RetrievalModelIndri)) {
      QryEval.fatalError("Error: WSUM Only supports Indri.");
    }

    int minDocId;
    // iterate all daat ptrs and find the smallest doc ID,
    // and record scores accordingly
    while ((minDocId = getSmallestCurrentDocid()) != Integer.MAX_VALUE) {
      double docScore = 0;

      for (int i = 0; i < daatPtrs.size(); ++i) {
        DaaTPtr dp = daatPtrs.get(i);

        // compare doc id and add scores with corresponding weights,
        // first exp then sum, and finally log
        int currDocId = dp.nextDoc >= dp.scoreList.scores.size() ?
                0 : dp.scoreList.getDocid(dp.nextDoc);
        if (currDocId != minDocId) {
          docScore +=
                  Math.exp(((QryopSl) args.get(i)).getDefaultScore(r, minDocId)) * weights.get(i);
        } else {
          docScore += Math.exp(dp.scoreList.getDocidScore(dp.nextDoc++)) * weights.get(i);
        }
      }
      // normalize docScore with total weights
      double totalWeights = 0;
      for (double w : weights) {
        totalWeights += w;
      }
      // back to logarithm
      result.docScores.add(minDocId, Math.log(docScore / totalWeights));
    }
    freeDaaTPtrs();

    return result;
  }

  @Override
  public String toString() {
    String result = "";

    for (Qryop arg : this.args) {
      result += arg.toString() + " ";
    }

    return ("#WSUM( " + result + ")");
  }
}
