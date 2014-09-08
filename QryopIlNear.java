import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QryopIlNear extends QryopIl {

  public QryopIlNear(int distance) {
    this.distance = distance;
  }

  public QryopIlNear(int distance, Qryop... q) {
    this.distance = distance;
    Collections.addAll(this.args, q);
  }

  /**
   * Appends an argument to the list of query operator arguments.
   *
   * @param {q} q The query argument (query operator) to append.
   * @return void
   */
  @Override
  public void add(Qryop q) {
    this.args.add(q);
  }

  @Override
  public QryResult evaluate(RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    syntaxCheckArgResults(this.daatPtrs);

    QryResult result = new QryResult();
    result.invertedList.field = new String(this.daatPtrs.get(0).invList.field);
    DaaTPtr ptr0 = this.daatPtrs.get(0);

    ITERATE_DOCS:
    for (; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc++) {
      int ptr0Docid = ptr0.invList.getDocid(ptr0.nextDoc);

      for (int j = 1; j < this.daatPtrs.size(); ++j) {
        DaaTPtr ptrj = this.daatPtrs.get(j);

        while (true) {
          if (ptrj.nextDoc >= ptrj.invList.postings.size()) {
            break ITERATE_DOCS;     // No more docs can match
          } else if (ptrj.invList.getDocid(ptrj.nextDoc) > ptr0Docid) {
            continue ITERATE_DOCS;  // The ptr0docid can't match.
          } else if (ptrj.invList.getDocid(ptrj.nextDoc) < ptr0Docid) {
            ptrj.nextDoc++;         // Not yet at the right doc.
          } else {
            break;                  // ptrj matches ptr0Docid
          }
        }
      }

      List<Integer> positions = new ArrayList<Integer>();
      // record current position when iterating the same doc for other daat pointers
      int[] daatPtrPos = new int[this.daatPtrs.size()];
      ITERATE_POSTING:
      for (int ptr0Pos : ptr0.invList.postings.get(ptr0.nextDoc).positions) {
        int prevPos = ptr0Pos;
        for (int j = 1; j < this.daatPtrs.size(); ++j) {
          DaaTPtr ptrj = this.daatPtrs.get(j);
          int ptrjPosSize = ptrj.invList.postings.get(ptrj.nextDoc).positions.size();
          for (; daatPtrPos[j] < ptrjPosSize; ++daatPtrPos[j]) {
            int ptrjPos = ptrj.invList.postings.get(ptrj.nextDoc).positions.get(daatPtrPos[j]);
            if (ptrjPos > ptr0Pos) {
              if (ptrjPos - prevPos <= distance) {
                prevPos = ptrjPos;               // find good position in this
                break;                           // doc, process next daatPtr.
              } else {                           // otherwise check next ptr0Pos,
                continue ITERATE_POSTING;        // since this is impossible
              }
            }
            // try ptrjPos until greater than ptr0Pos
          }
        }
        // all docIds have positions matching the requirement, record the pos
        positions.add(ptr0Pos);
      }
      if (!positions.isEmpty()) {
        result.invertedList.appendPosting(ptr0Docid, positions);
      }
    }
    return result;
  }

  @Override
  public String toString() {
    String result = new String();

    for (Qryop arg : this.args) {
      result += arg.toString() + " ";
    }

    return "#NEAR/" + distance + "( " + result + ")";
  }

  /**
   * syntaxCheckArgResults does syntax checking.
   * All daatptr should be QryopIl, and should be in the same field.
   *
   * @param ptrs A list of DaaTPtrs for this query operator.
   * @return True if the syntax is valid, false otherwise.
   */
  private Boolean syntaxCheckArgResults(List<DaaTPtr> ptrs) {

    for (int i = 0; i < this.args.size(); i++) {

      if (!(this.args.get(i) instanceof QryopIl)) {
        QryEval.fatalError("Error:  Invalid argument in " +
                this.toString());
      } else if ((i > 0) &&
              (!ptrs.get(i).invList.field.equals(ptrs.get(0).invList.field))) {
        QryEval.fatalError("Error:  Arguments must be in the same field:  " +
                this.toString());
      }
    }

    return true;
  }

  /*
    *  Calculate the default score for the specified document if it
    *  does not match the query operator.  This score is 0 for many
    *  retrieval models, but not all retrieval models.
    *  @param r A retrieval model that controls how the operator behaves.
    *  @param docid The internal id of the document that needs a default score.
    *  @return The default score.
    */
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    return 0.0;
  }

  private final int distance;
}
