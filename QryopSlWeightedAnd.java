import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QryopSlWeightedAnd extends QryopSlAnd {

  public QryopSlWeightedAnd() {
    this.weights = new ArrayList<Double>();
  }

  /**
   * Appends an argument to the list of query operator arguments along with its weight.
   *
   * @param a q The query argument (query operator) to append.
   * @param weight The weight of the query argument.
   * @return void
   */
  public void add(Qryop a, double weight) {
    this.args.add(a);
    this.weights.add(weight);
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
        defaultScore += ((QryopSl) arg).getDefaultScore(r, docid) * weights.get(i);
      }

      // normalize score with total weights
      double totalWeights = 0;
      for (double w : weights)
        totalWeights += w;

      return defaultScore / totalWeights;
    }

    System.err.println("Warning: WAND only supports Indri.");
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
      QryEval.fatalError("Error: WAND Only supports Indri.");
    }

    int minDocId;
    // iterate all daat ptrs and find the smallest doc ID,
    // and record scores accordingly
    while ((minDocId = getSmallestCurrentDocid()) != Integer.MAX_VALUE) {
      double docScore = 0;

      for (int i = 0; i < daatPtrs.size(); ++i) {
        DaaTPtr dp = daatPtrs.get(i);

        // compare doc id and add scores with corresponding weights
        int currDocId = dp.nextDoc >= dp.scoreList.scores.size() ?
                0 : dp.scoreList.getDocid(dp.nextDoc);
        if (currDocId != minDocId) {
          docScore += ((QryopSl) args.get(i)).getDefaultScore(r, minDocId) * weights.get(i);
        } else {
          docScore += dp.scoreList.getDocidScore(dp.nextDoc++) * weights.get(i);
        }
      }
      // normalize docScore with total weights
      double totalWeights = 0;
      for (double w : weights)
        totalWeights += w;

      result.docScores.add(minDocId, docScore / totalWeights);
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

    return ("#WAND( " + result + ")");
  }

  /**
   * Weights of the query arguments.
   */
  private final List<Double> weights;
}
