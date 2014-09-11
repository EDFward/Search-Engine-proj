import java.io.IOException;
import java.util.*;

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

    // iterate all daat ptrs and find the smallest docid,
    // and record scores accordingly
    while (this.daatPtrs.size() > 0)
    {
      // record daat ptrs with min docid; advance their nextdoc later
      List<DaaTPtr> minDaatPtr = new ArrayList<DaaTPtr>();
      int minDocId = Integer.MAX_VALUE;

      Iterator<DaaTPtr> iter = this.daatPtrs.iterator();
      while (iter.hasNext()) {
        DaaTPtr dp = iter.next();
        // remove this daat ptr if all docs have been traversed
        if (dp.nextDoc >= dp.scoreList.scores.size()) {
          iter.remove();
          continue;
        }

        // compare doc id and do records
        int currDocId = dp.scoreList.getDocid(dp.nextDoc);
        if (currDocId < minDocId) {
          minDocId = currDocId;
          minDaatPtr.clear();
          minDaatPtr.add(dp);
        } else if (currDocId == minDocId)
          minDaatPtr.add(dp);
      }
      // ignore if no more daatPtr
      if (minDocId != Integer.MAX_VALUE)
        result.docScores.add(minDocId, 1.0);
      // advance minDaatPtr's nextdoc since their doc have been processed
      for (DaaTPtr dp : minDaatPtr)
        dp.nextDoc++;
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

    // iterate all daat ptrs and find the smallest docid,
    // and record scores accordingly
    while (this.daatPtrs.size() > 0)
    {
      // record daat ptrs with min docid; advance their nextdoc later
      List<DaaTPtr> minDaatPtr = new ArrayList<DaaTPtr>();
      int minDocId = Integer.MAX_VALUE;
      double maxScore = 0;

      Iterator<DaaTPtr> iter = this.daatPtrs.iterator();
      while (iter.hasNext()) {
        DaaTPtr dp = iter.next();
        // remove this daat ptr if all docs have been traversed
        if (dp.nextDoc >= dp.scoreList.scores.size()) {
          iter.remove();
          continue;
        }

        // compare doc id and do records
        int currDocId = dp.scoreList.getDocid(dp.nextDoc);
        if (currDocId < minDocId) {
          minDocId = currDocId;
          minDaatPtr.clear();
          minDaatPtr.add(dp);
          maxScore = dp.scoreList.getDocidScore(dp.nextDoc);
        } else if (currDocId == minDocId) {
          minDaatPtr.add(dp);
          double currScore = dp.scoreList.getDocidScore(dp.nextDoc);
          if (currScore > maxScore)
            maxScore = currScore;
        }
      }
      // ignore if no more daatPtr
      if (minDocId != Integer.MAX_VALUE)
        result.docScores.add(minDocId, maxScore);
      // advance minDaatPtr's nextdoc since their doc have been processed
      for (DaaTPtr dp : minDaatPtr)
        dp.nextDoc++;
    }
    freeDaaTPtrs();
    return result;
  }
}
