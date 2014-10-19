import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class QryopIlWindow extends QryopIl {

  public QryopIlWindow(int distance) {
    this.distance = distance;
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param q The query argument (query operator) to append.
   * @return void
   * @throws java.io.IOException
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
    allocDaaTPtrs(r);
    syntaxCheckArgResults(this.daatPtrs);

    QryResult result = new QryResult();
    result.invertedList.field = this.daatPtrs.get(0).invList.field;
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
            continue ITERATE_DOCS;  // This ptr0docid can't match.
          } else if (ptrj.invList.getDocid(ptrj.nextDoc) < ptr0Docid) {
            ptrj.nextDoc++;         // Not yet at the right doc.
          } else {
            break;                  // ptrj matches ptr0Docid
          }
        }
      }

      // iterate locations to find the suitable window
      List<Integer> positions = new ArrayList<Integer>();
      int[] daatPtrPos = new int[this.daatPtrs.size()];

      LOOP_FOR_WINDOW:
      while (true) {
        int minLocDaaTIndex = 0;
        int minLoc = Integer.MAX_VALUE, maxLoc = Integer.MIN_VALUE;

        for (int i = 0; i < this.daatPtrs.size(); ++i) {
          DaaTPtr dp = this.daatPtrs.get(i);
          Vector<Integer> postings = dp.invList.postings.get(dp.nextDoc).positions;
          // check if already iterated through this posting,
          // if yes, this doc is done, break
          if (daatPtrPos[i] >= postings.size()) {
            break LOOP_FOR_WINDOW;
          }
          // otherwise, update min/max term location
          int loc = postings.get(daatPtrPos[i]);
          if (loc < minLoc) {
            minLoc = loc;
            minLocDaaTIndex = i;
          }
          if (loc > maxLoc) {
            maxLoc = loc;
          }
        }
        // check window size
        int windowSize = 1 + maxLoc - minLoc;
        if (windowSize > distance) {
          // not good, only advance loc pointer of smallest one
          ++daatPtrPos[minLocDaaTIndex];
        } else {
          // record maxLoc
          positions.add(maxLoc);
          // advance location pointer for all
          for (int j = 0; j < daatPtrPos.length; ++j) {
            ++daatPtrPos[j];
          }
        }
      }

      if (!positions.isEmpty()) {
        result.invertedList.appendPosting(ptr0Docid, positions);
      }
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

    return "#WINODOW/" + distance + "( " + result + ")";
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

  /**
   * The only parameter for near operator, indicating the size of the window.
   */
  private final int distance;
}
