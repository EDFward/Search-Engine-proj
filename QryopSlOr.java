import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class QryopSlOr extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new QryopSlOr (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlOr(Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
  }

  @Override
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    return 0.0;
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param {q} q The query argument (query operator) to append.
   *  @return void
   *  @throws IOException
   */
  @Override
  public void add(Qryop q) throws IOException {
    this.args.add(q);
  }

  /**
   *  Evaluates the query operator, including any child operators and
   *  returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  @Override
  public QryResult evaluate(RetrievalModel r) throws IOException {
    if (r instanceof RetrievalModelUnrankedBoolean)
      return (evaluateBoolean (r));

    return null;
  }

  @Override
  public String toString() {
    String result = new String ();

    for (Qryop arg : this.args)
      result += arg.toString() + " ";

    return ("#OR( " + result + ")");
  }

  /**
   *  Evaluates the query operator for boolean retrieval models,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  private QryResult evaluateBoolean (RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    QryResult result = new QryResult();
    Set<Integer> docidSet = new TreeSet<Integer>();

    for (DaaTPtr p : this.daatPtrs) {
      int docSize = p.scoreList.scores.size();
      for (int i = 0; i < docSize; ++i)
        docidSet.add(p.scoreList.getDocid(i));
    }

    for (int docid : docidSet)
      result.docScores.add(docid, 1.0);

    freeDaaTPtrs();
    return result;
  }
}
