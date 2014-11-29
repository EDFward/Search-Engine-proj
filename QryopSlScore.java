/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

public class QryopSlScore extends QryopSl {

  /**
   * Records the data to calculate default score for Indri
   * query operation after evaluation
   */
  private double ctfProb = -1;

  /**
   * Denotes the field of the original inverted list. Since
   * #Score only covers QryopIl with one particular field,
   * it can be recorded during evaluation.
   */
  private String field;

  /**
   * Construct a new SCORE operator.  The SCORE operator accepts just
   * one argument.
   *
   * @param q The query operator argument.
   */
  public QryopSlScore(Qryop q) {
    this.args.add(q);
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param a The query argument to append.
   */
  public void add(Qryop a) {
    this.args.add(a);
  }

  /*
   *  Calculate the default score for a document that does not match
   *  the query argument.  This score is 0 for many retrieval models,
   *  but not all retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @param docid The internal id of the document that needs a default score.
   *  @return The default score.
   */
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

    if (r instanceof RetrievalModelIndri) {
      if (ctfProb == -1) {
        Utility.fatalError("Error: default score parameters not set up.");
      }
      DocLengthStore docLengthStore = QryEval.LENGTH_STORE;
      int mu = ((RetrievalModelIndri) r).getMu();
      double lambda = ((RetrievalModelIndri) r).getLambda();
      long doclen = docLengthStore.getDocLength(field, (int) docid);
      double ctfParam1 = mu * ctfProb;
      double ctfParam2 = (1 - lambda) * ctfProb;

      return Math.log(lambda * ctfParam1 / (doclen + mu) + ctfParam2);
    }

    return 0.0;
  }

  /**
   * Evaluate the query operator.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return (evaluateBoolean(r));
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return evaluateRankedBoolean(r);
    } else if (r instanceof RetrievalModelBM25) {
      return evaluateBM25(r);
    } else if (r instanceof RetrievalModelIndri) {
      return evaluateIndri(r);
    }

    return null;
  }

  /**
   * Return a string version of this query operator.
   *
   * @return The string version of this query operator.
   */
  public String toString() {

    String result = "";

    for (Qryop arg : this.args) {
      result += (arg.toString() + " ");
    }

    return ("#SCORE( " + result + ")");
  }

  private QryResult evaluateBoolean(RetrievalModel r) throws IOException {

    // Evaluate the query argument.

    QryResult result = args.get(0).evaluate(r);

    // Each pass of the loop computes a score for one document. Note:
    // If the evaluate operation above returned a score list (which is
    // very possible), this loop gets skipped.

    for (int i = 0; i < result.invertedList.df; i++) {

      // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY.
      // Unranked Boolean. All matching documents get a score of 1.0.

      result.docScores.add(result.invertedList.postings.get(i).docid,
              (float) 1.0);
    }

    // The SCORE operator should not return a populated inverted list.
    // If there is one, replace it with an empty inverted list.

    if (result.invertedList.df > 0) {
      result.invertedList = new InvList();
    }

    return result;
  }

  private QryResult evaluateRankedBoolean(RetrievalModel r) throws IOException {
    QryResult result = args.get(0).evaluate(r);

    // Each pass of the loop computes a score for one document. Note:
    // If the evaluate operation above returned a score list (which is
    // very possible), this loop gets skipped.

    for (int i = 0; i < result.invertedList.df; i++) {
      int docId = result.invertedList.postings.get(i).docid,
              tf = result.invertedList.getTf(i);
      result.docScores.add(docId, tf);
    }

    if (result.invertedList.df > 0) {
      result.invertedList = new InvList();
    }

    return result;
  }

  private QryResult evaluateBM25(RetrievalModel r) throws IOException {
    QryResult result = args.get(0).evaluate(r);

    // necessary info to calculate BM 25 scores
    double k_1 = ((RetrievalModelBM25) r).getK_1(),
            b = ((RetrievalModelBM25) r).getB();
    DocLengthStore docLengthStore = QryEval.LENGTH_STORE;
    String field = result.invertedList.field;
    double avgDocLen = QryEval.READER.getSumTotalTermFreq(field) /
            (float) QryEval.READER.getDocCount(field);

    // idf
    int df = result.invertedList.df;
    double idf = Math.log((QryEval.READER.getDocCount(field) - df + 0.5) / (df + 0.5));

    for (int i = 0; i < df; ++i) {
      int docid = result.invertedList.postings.get(i).docid;
      long doclen = docLengthStore.getDocLength(field, docid);
      int tf = result.invertedList.getTf(i);
      double normTf = tf / (tf + k_1 * (1 - b + b * doclen / avgDocLen));
      // store the score! (without user weight)
      result.docScores.add(docid, idf * normTf);
    }

    if (result.invertedList.df > 0) {
      result.invertedList = new InvList();
    }

    return result;

  }

  private QryResult evaluateIndri(RetrievalModel r) throws IOException {
    QryResult result = args.get(0).evaluate(r);

    // necessary info to calculate query likelihood
    int mu = ((RetrievalModelIndri) r).getMu();
    double lambda = ((RetrievalModelIndri) r).getLambda();
    DocLengthStore docLengthStore = QryEval.LENGTH_STORE;
    int df = result.invertedList.df;
    field = result.invertedList.field;
    // calculate the 2 parameters in query likelihood calculation
    ctfProb = ((double) result.invertedList.ctf) / QryEval.READER.getSumTotalTermFreq(field);
    double ctfParam1 = mu * ctfProb;
    double ctfParam2 = (1 - lambda) * ctfProb;

    for (int i = 0; i < df; ++i) {
      int docid = result.invertedList.postings.get(i).docid;
      long doclen = docLengthStore.getDocLength(field, docid);
      int tf = result.invertedList.getTf(i);
      double logScaleScore = Math.log(lambda * (tf + ctfParam1) / (doclen + mu) + ctfParam2);
      // store the scores!
      result.docScores.add(docid, logScaleScore);
    }

    if (result.invertedList.df > 0) {
      result.invertedList = new InvList();
    }
    return result;
  }
}
