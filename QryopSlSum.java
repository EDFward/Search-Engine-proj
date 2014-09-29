import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * #SUM operator for BM25 retrieval model.
 *
 * @author junjiah
 */
public class QryopSlSum extends QryopSl {
  @Override
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    return 0;
  }

  /**
   * Appends an argument to the list of query operator arguments.
   *
   * @param q@return void
   * @throws java.io.IOException
   */
  @Override
  public void add(Qryop q) throws IOException {
    args.add(q);
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
    if (!(r instanceof RetrievalModelBM25)) {
      System.err.println("Error: #SUM only supports BM25 model");
      System.exit(-1);
    }

    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    // iterate all daat ptrs and find the smallest docid,
    // and record scores accordingly
    while (this.daatPtrs.size() > 0)
    {
      // record daat ptrs with min docid; advance their nextdoc later
      List<DaaTPtr> minDaatPtr = new ArrayList<DaaTPtr>();
      int minDocId = Integer.MAX_VALUE;
      double termScore = 0;

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
          termScore = dp.scoreList.getDocidScore(dp.nextDoc);
        } else if (currDocId == minDocId) {
          minDaatPtr.add(dp);
          termScore += dp.scoreList.getDocidScore(dp.nextDoc);
        }
      }
      // ignore if no more daatPtr
      if (minDocId != Integer.MAX_VALUE)
        result.docScores.add(minDocId, termScore);
      // advance minDaatPtr's nextdoc since their doc have been processed
      for (DaaTPtr dp : minDaatPtr)
        dp.nextDoc++;
    }
    freeDaaTPtrs();
    return result;
  }

  /**
   *  Return a string version of this query operator.
   *  @return The string version of this query operator.
   */
  @Override
  public String toString() {
    String result = new String();
    for (int i = 0; i < this.args.size(); i++) {
      result += this.args.get(i).toString() + " ";
    }
    return ("#SUM( " + result + ")");
  }
}
