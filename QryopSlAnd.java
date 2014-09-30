/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.Collections;
import java.util.ListIterator;

public class QryopSlAnd extends QryopSl {

  /**
   * It is convenient for the constructor to accept a variable number
   * of arguments. Thus new QryopSlAnd (arg1, arg2, arg3, ...).
   *
   * @param q A query argument (a query operator).
   */
  public QryopSlAnd(Qryop... q) {
    Collections.addAll(this.args, q);
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param a q The query argument (query operator) to append.
   * @return void
   */
  public void add(Qryop a) {
    this.args.add(a);
  }

  /**
   * Evaluates the query operator, including any child operators and
   * returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return evaluateBoolean(r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return evaluateRankedBoolean(r);
    } else if (r instanceof RetrievalModelIndri) {
      return evaluateIndri(r);
    }

    return null;
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
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    if (r instanceof RetrievalModelIndri) {
      double defaultScore = 0;
      for (Qryop arg : args) {
        // they must be QryopSl, so downcast.
        // since they are in log scale, sum them up
        defaultScore += ((QryopSl) arg).getDefaultScore(r, docid);
      }
      // normalization from query terms
      return defaultScore / args.size();
    }

    return 0.0;
  }

  /**
   * Return a string version of this query operator.
   *
   * @return The string version of this query operator.
   */
  public String toString() {

    String result = "";

    for (Qryop arg : this.args) {
      result += arg.toString() + " ";
    }

    return ("#AND( " + result + ")");
  }

  private QryResult evaluateBoolean(RetrievalModel r) throws IOException {

    //  Initialization

    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    // put daat-ptr with shortest score list in first place. O(n)
    final ListIterator<DaaTPtr> itr = this.daatPtrs.listIterator();
    DaaTPtr min = itr.next();
    int minIndex = 0;
    while (itr.hasNext()) {
      final DaaTPtr curr = itr.next();
      if (curr.scoreList.scores.size() < min.scoreList.scores.size()) {
        min = curr;
        minIndex = itr.previousIndex();
      }
    }
    Collections.swap(this.daatPtrs, 0, minIndex);

    DaaTPtr ptr0 = this.daatPtrs.get(0);

    EVALUATEDOCUMENTS:
    for (; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc++) {

      int ptr0Docid = ptr0.scoreList.getDocid(ptr0.nextDoc);
      double docScore = 1.0;

      //  Do the other query arguments have the ptr0Docid?

      for (int j = 1; j < this.daatPtrs.size(); j++) {

        DaaTPtr ptrj = this.daatPtrs.get(j);

        while (true) {
          if (ptrj.nextDoc >= ptrj.scoreList.scores.size()) {
            break EVALUATEDOCUMENTS;     // No more docs can match
          } else if (ptrj.scoreList.getDocid(ptrj.nextDoc) > ptr0Docid) {
            continue EVALUATEDOCUMENTS;  // The ptr0docid can't match.
          } else if (ptrj.scoreList.getDocid(ptrj.nextDoc) < ptr0Docid) {
            ptrj.nextDoc++;              // Not yet at the right doc.
          } else {
            break;                       // ptrj matches ptr0Docid
          }
        }
      }
      //  The ptr0Docid matched all query arguments, so save it.
      result.docScores.add(ptr0Docid, docScore);
    }
    freeDaaTPtrs();
    return result;
  }

  private QryResult evaluateRankedBoolean(RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    // put daat-ptr with shortest score list in first place. O(n)
    final ListIterator<DaaTPtr> itr = this.daatPtrs.listIterator();
    DaaTPtr min = itr.next();
    int minIndex = 0;
    while (itr.hasNext()) {
      final DaaTPtr curr = itr.next();
      if (curr.scoreList.scores.size() < min.scoreList.scores.size()) {
        min = curr;
        minIndex = itr.previousIndex();
      }
    }
    Collections.swap(this.daatPtrs, 0, minIndex);

    DaaTPtr ptr0 = this.daatPtrs.get(0);

    // min the scores
    TRAVERSE_DOC_IN_PTR0:
    for (; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc++) {
      int ptr0Docid = ptr0.scoreList.getDocid(ptr0.nextDoc);
      double currScore = ptr0.scoreList.getDocidScore(ptr0.nextDoc);

      for (int j = 1; j < this.daatPtrs.size(); ++j) {
        DaaTPtr ptrj = this.daatPtrs.get(j);

        while (true) {
          if (ptrj.nextDoc >= ptrj.scoreList.scores.size()) {
            break TRAVERSE_DOC_IN_PTR0;     // No more docs can match
          } else if (ptrj.scoreList.getDocid(ptrj.nextDoc) > ptr0Docid) {
            continue TRAVERSE_DOC_IN_PTR0;  // The ptr0docid can't match.
          } else if (ptrj.scoreList.getDocid(ptrj.nextDoc) < ptr0Docid) {
            ptrj.nextDoc++;                 // Not yet at the right doc.
          } else {
            break;                          // ptrj matches ptr0Docid
          }
        }

        double ptrjScore = ptrj.scoreList.getDocidScore(ptrj.nextDoc);
        if (ptrjScore < currScore) {
          currScore = ptrjScore;
        }
      }
      result.docScores.add(ptr0Docid, currScore);
    }
    freeDaaTPtrs();
    return result;
  }

  private QryResult evaluateIndri(RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    int minDocId;
    // iterate all daat ptrs and find the smallest doc ID,
    // and record scores accordingly
    while ((minDocId = getSmallestCurrentDocid()) != Integer.MAX_VALUE) {
      double docScore = 0;

      for (int i = 0; i < daatPtrs.size(); ++i) {
        DaaTPtr dp = daatPtrs.get(i);

        // compare doc id and do records
        int currDocId = dp.nextDoc >= dp.scoreList.scores.size() ?
                0 : dp.scoreList.getDocid(dp.nextDoc);
        if (currDocId != minDocId) {
          docScore += ((QryopSl) args.get(i)).getDefaultScore(r, minDocId);
        } else {
          docScore += dp.scoreList.getDocidScore(dp.nextDoc++);
        }
      }
      // normalize docScore with query term number
      result.docScores.add(minDocId, docScore / daatPtrs.size());
    }
    freeDaaTPtrs();

    return result;
  }

  /**
   * Return the smallest unexamined docid from the DaaTPtrs or
   * Integer.MAX_VALUE
   *
   * @return The smallest internal document id. Return MAX_VALUE
   * if all DaaT pointers have been traversed.
   */
  private int getSmallestCurrentDocid() {

    int nextDocid = Integer.MAX_VALUE;

    for (DaaTPtr ptri : this.daatPtrs) {
      // already gone through
      if (ptri.nextDoc >= ptri.scoreList.scores.size()) {
        continue;
      }

      int docid = ptri.scoreList.getDocid(ptri.nextDoc);
      if (nextDocid > docid) {
        nextDocid = docid;
      }
    }

    return nextDocid;
  }
}