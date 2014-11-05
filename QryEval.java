/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.*;

public class QryEval {

  /**
   * Document length reader.
   */
  public static DocLengthStore LENGTH_STORE;

  public static IndexReader READER;

  //  The index file reader is accessible via a global variable. This
  //  isn't great programming style, but the alternative is for every
  //  query operator to store or pass this value, which creates its
  //  own headaches.

  public static EnglishAnalyzerConfigurable analyzer =
          new EnglishAnalyzerConfigurable(Version.LUCENE_43);

  //  Create and configure an English analyzer that will be used for
  //  query parsing.

  static String usage = "Usage:  java " + System.getProperty("sun.java.command")
          + " paramFile\n\n";

  static {
    analyzer.setLowercase(true);
    analyzer.setStopwordRemoval(true);
    analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   * @param args The only argument is the path to the parameter file.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception { // TODO: simplify main function
    // when everything begins
    final long startTime = System.currentTimeMillis();

    // must supply parameter file
    if (args.length < 1) {
      System.err.println(usage);
      System.exit(1);
    }

    // read in the parameter file; one parameter per line in format of key=value
    Map<String, String> params = new HashMap<String, String>();
    Scanner scan = new Scanner(new File(args[0]));
    String line;
    do {
      line = scan.nextLine();
      String[] pair = line.split("=");
      params.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    scan.close();

    // parameters required for this example to run
    if (!params.containsKey("indexPath")) {
      fatalError("Error: Parameter 'indexPath' was missing.");
    }

    // open the index and LENGTH_STORE
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));
    try {
      LENGTH_STORE = new DocLengthStore(QryEval.READER);
    } catch (IOException e) {
      fatalError("Error: DocLengthStore initialization error");
    }

    if (READER == null) {
      fatalError(usage);
    }

    // define the retrieval model from parameter file
    RetrievalModel model = null;
    try {
      model = (RetrievalModel) Class.forName(
              "RetrievalModel" + params.get("retrievalAlgorithm")).newInstance();
    } catch (Exception e) {
      fatalError("Error: Failed to load specified retrieval model.");
    }

    // open query input file and read queries
    Map<Integer, String> queryStrings = new LinkedHashMap<Integer, String>();
    BufferedReader queryFileReader = null;
    try {
      queryFileReader = new BufferedReader(new FileReader(params.get("queryFilePath")));

      while ((line = queryFileReader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          break;
        }

        String[] parts = line.split(":", 2);
        // add queryId: queryString to the map
        queryStrings.put(Integer.parseInt(parts[0]), parts[1]);
      }
    } catch (Exception e) {
      fatalError("Error: Read/Evaluate query file failed.");
    } finally {
      assert queryFileReader != null;
      queryFileReader.close();
    }

    // evaluate and create the trec_eval output.
    BufferedWriter rankWriter = null, queryWriter = null;
    boolean fb = params.containsKey("fb") && (params.get("fb").equalsIgnoreCase("true"));
    if (fb) {
      queryWriter = new BufferedWriter(
              new FileWriter(new File(params.get("fbExpansionQueryFile"))));
    }
    try {
      rankWriter = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));
      // add default query operator depending on retrieval model
      // and set parameters accordingly
      Qryop defaultQryop;
      if (model instanceof RetrievalModelBM25) {
        model.setParameter("k_1", params.get("BM25:k_1"));
        model.setParameter("k_3", params.get("BM25:k_3"));
        model.setParameter("b", params.get("BM25:b"));
        defaultQryop = new QryopSlSum();
      } else if (model instanceof RetrievalModelIndri) {
        model.setParameter("mu", params.get("Indri:mu"));
        model.setParameter("lambda", params.get("Indri:lambda"));
        defaultQryop = new QryopSlAnd();
      } else {
        // no parameter to read
        defaultQryop = new QryopSlOr();
      }

      for (Map.Entry<Integer, String> entry : queryStrings.entrySet()) {
        int queryId = entry.getKey();
        defaultQryop.clear();
        // parse the query, then evaluate
        Qryop parsedQuery = parseQuery(entry.getValue(), defaultQryop);
        QryResult result = parsedQuery.evaluate(model);
        // sort and truncate to 100 if necessary
        result.docScores.sortAndTruncate();

        /**
         * If relevance feedback is specified, re-evaluate the query
         */
        if (fb) {
          Qryop expandedQuery;
          int fbDocs = 0;
          int fbTerms = 0;
          int fbMu = 0;
          double fbOrigWeight = 0;
          try {
            fbDocs = Integer.parseInt(params.get("fbDocs"));
            fbTerms = Integer.parseInt(params.get("fbTerms"));
            fbMu = Integer.parseInt(params.get("fbMu"));
            fbOrigWeight = Double.parseDouble(params.get("fbOrigWeight"));
          } catch (Exception e) {
            e.printStackTrace();
            fatalError("ERROR: Parsing FB parameters error!");
          }
          if (params.containsKey("fbInitialRankingFile")) {
            String fbInitialRankingFile = params.get("fbInitialRankingFile");
            expandedQuery = expandQuery(fbInitialRankingFile, fbDocs, fbTerms, fbMu);
          } else {
            expandedQuery = expandQuery(result, fbDocs, fbTerms, fbMu);
          }

          if (queryWriter != null) {
            queryWriter.write(queryId + ": " + expandedQuery + "\n");
          }

          QryopSlWeightedAnd combinedQuery = new QryopSlWeightedAnd();
          combinedQuery.add(fbOrigWeight);
          combinedQuery.add(parsedQuery);
          combinedQuery.add(1 - fbOrigWeight);
          combinedQuery.add(expandedQuery);
          result = combinedQuery.evaluate(model);
          result.docScores.sortAndTruncate();
        }

        // write to evaluation file
        if (result.docScores.scores.size() < 1) {
          rankWriter.write(queryId + " Q0 dummy 1 0 run-1\n");
        } else {
          for (int j = 0; j < result.docScores.scores.size(); ++j) {
            String s = String.format("%d Q0 %s %d %.10f run-1\n",
                    queryId,                                        // query id
                    getExternalDocid(result.docScores.getDocid(j)), // external id
                    j + 1,                                          // rank
                    result.docScores.getDocidScore(j));
            rankWriter.write(s);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      fatalError("Error: Evaluation failed.");
    } finally {
      if (rankWriter != null) {
        rankWriter.close();
      }
      if (queryWriter != null) {
        queryWriter.close();
      }
    }

    // print evaluation time
    final long endTime = System.currentTimeMillis();
    System.out.println("Total evaluation time: " + (endTime - startTime) / 1000.0 + " seconds");
    printMemoryUsage(false);
  }

  /**
   * Expand query from already evaluated query results.
   *
   * @param evaluatedResult Already evaluated result
   * @param fbDocs          Number of feedback documents
   * @param fbTerms         Number of feedback terms
   * @param fbMu            Parameter mu for p(t|d)
   * @return Expaneded query from previously evaluated query result
   */
  private static Qryop expandQuery(QryResult evaluatedResult, int fbDocs, int fbTerms, int fbMu)
          throws IOException {
    Map<String, Double> termScore = new HashMap<String, Double>();
    Map<String, Double> ctfProb = new HashMap<String, Double>();
    List<TermVector> termVectorList = new ArrayList<TermVector>(fbDocs);
    List<Double> scoreList = new ArrayList<Double>(fbDocs);
    List<Long> lengthList = new ArrayList<Long>(fbDocs);
    long fieldLength = READER.getSumTotalTermFreq("body");

    for (int i = 0; i < fbDocs && i < evaluatedResult.docScores.scores.size(); ++i) {
      int internalId = evaluatedResult.docScores.getDocid(i);
      // my score is in log space
      Double score = Math.exp(evaluatedResult.docScores.getDocidScore(i));

      // record length, score and term vector separately
      lengthList.add(LENGTH_STORE.getDocLength("body", internalId));
      scoreList.add(score);
      TermVector termVector = new TermVector(internalId, "body");
      termVectorList.add(termVector);

      // add term term string to score map, and record MLE of p(t|c)
      for (int j = 1; j < termVector.stemsLength(); ++j) {
        String termString = termVector.stemString(j);
        // ignore terms with comma and period
        if (termString.contains(",") || termString.contains(".")) {
          continue;
        }
        termScore.put(termString, 0d);
        ctfProb.put(termString, ((double) termVector.totalStemFreq(j)) / fieldLength);
      }
    }

    return buildExpandedQuery(fbTerms, fbMu, termScore, ctfProb, termVectorList, scoreList,
            lengthList);
  }

  /**
   * Expand query from a ranking file.
   *
   * @param fbInitialRankingFile Randing file
   * @param fbDocs               Number of feedback documents
   * @param fbTerms              Number of feedback terms
   * @param fbMu                 Parameter mu for p(t|d)
   * @return Expanded query based on ranking file
   * @throws IOException
   */
  private static Qryop expandQuery(String fbInitialRankingFile, int fbDocs, int fbTerms,
          int fbMu)
          throws Exception {
    BufferedReader rankFileReader;
    String line;
    rankFileReader = new BufferedReader(new FileReader(fbInitialRankingFile));

    Map<String, Double> termScore = new HashMap<String, Double>();
    Map<String, Double> ctfProb = new HashMap<String, Double>();
    List<TermVector> termVectorList = new ArrayList<TermVector>(fbDocs);
    List<Double> scoreList = new ArrayList<Double>(fbDocs);
    List<Long> lengthList = new ArrayList<Long>(fbDocs);
    long fieldLength = READER.getSumTotalTermFreq("body");
    int lineCount = 0;

    while ((lineCount++ < fbDocs) && ((line = rankFileReader.readLine()) != null)) {
      line = line.trim();
      if (line.isEmpty()) {
        break;
      }
      String[] parts = line.split("\\s+");
      String externalId = parts[2];
      int internalId = getInternalDocid(externalId);
      Double score = Double.parseDouble(parts[4]);
      if (score < 0) { // normalize score if in log space
        score = Math.exp(score);
      }

      // record length, score and term vector separately
      lengthList.add(LENGTH_STORE.getDocLength("body", internalId));
      scoreList.add(score);
      TermVector termVector = new TermVector(internalId, "body");
      termVectorList.add(termVector);

      // add term term string to score map, and record MLE of p(t|c)
      for (int i = 1; i < termVector.stemsLength(); ++i) {
        String termString = termVector.stemString(i);
        // ignore terms with comma and period
        if (termString.contains(",") || termString.contains(".")) {
          continue;
        }
        termScore.put(termString, 0d);
        ctfProb.put(termString, ((double) termVector.totalStemFreq(i)) / fieldLength);
      }
    }
    rankFileReader.close();

    return buildExpandedQuery(fbTerms, fbMu, termScore, ctfProb, termVectorList, scoreList,
            lengthList);
  }

  /**
   * Build weighted AND query operator for query expansion from acquired information.
   *
   * @param fbTerms        Number of feedback terms
   * @param fbMu           Parameter mu for p(t|d)
   * @param termScore      A map of scores for every term
   * @param ctfProb        A map of p(t|c) for every term
   * @param termVectorList A list of term vectors
   * @param scoreList      A list of document scores
   * @param lengthList     A list of document length
   * @return The final weighted AND query operator
   */
  private static Qryop buildExpandedQuery(int fbTerms, int fbMu, Map<String, Double> termScore,
          Map<String, Double> ctfProb, List<TermVector> termVectorList, List<Double> scoreList,
          List<Long> lengthList) {
    for (int i = 0; i < termVectorList.size(); ++i) {
      TermVector termVector = termVectorList.get(i);
      // read term freq for this particular document
      Map<String, Integer> termFreq = new HashMap<String, Integer>();
      for (int j = 1; j < termVector.stemsLength(); ++j) {
        termFreq.put(termVector.stemString(j), termVector.stemFreq(j));
      }
      long length = lengthList.get(i);
      double score = scoreList.get(i);

      // update score p(t|I) for each term, even if it's not present in one document
      for (String term : termScore.keySet()) {
        double currentScore = termScore.get(term);
        int tf = 0;
        if (termFreq.containsKey(term)) {
          tf = termFreq.get(term);
        }
        double tGivenC = ctfProb.get(term);
        currentScore += (tf + fbMu * tGivenC) / (length + fbMu) * score * Math.log(1 / tGivenC);
        termScore.put(term, currentScore);
      }
    }

    // sort termScore by their score
    List<Map.Entry<String, Double>> sortedTermScore = new ArrayList<Map.Entry<String, Double>>(
            termScore.entrySet());
    Collections.sort(sortedTermScore, new Comparator<Map.Entry<String, Double>>() {
      @Override
      public int compare(Map.Entry<String, Double> e1, Map.Entry<String, Double> e2) {
        // we need descending order
        return e2.getValue().compareTo(e1.getValue());
      }
    });

    // build the final query!
    QryopSlWeightedAnd expandedQuery = new QryopSlWeightedAnd();
    int termLimit = Math.min(fbTerms, sortedTermScore.size());
    for (int i = 0; i < termLimit; ++i) {
      Map.Entry<String, Double> entry = sortedTermScore.get(i);
      expandedQuery.add(entry.getValue());
      expandedQuery.add(new QryopIlTerm(entry.getKey(), "body"));
    }
    return expandedQuery;
  }

  /**
   * Write an error message and exit.  This can be done in other
   * ways, but I wanted something that takes just one statement so
   * that it is easy to insert checks without cluttering the code.
   *
   * @param message The error message to write before exiting.
   * @return void
   */
  static void fatalError(String message) {
    System.err.println(message);
    System.exit(1);
  }

  /**
   * Get the external document id for a document specified by an
   * internal document id. If the internal id doesn't exists, returns null.
   *
   * @param iid The internal document id of the document.
   * @throws IOException
   */
  static String getExternalDocid(int iid) throws IOException {
    Document d = QryEval.READER.document(iid);
    return d.get("externalId");
  }

  /**
   * Finds the internal document id for a document specified by its
   * external id, e.g. clueweb09-enwp00-88-09710.  If no such
   * document exists, it throws an exception.
   *
   * @param externalId The external document id of a document.s
   * @return An internal doc id suitable for finding document vectors etc.
   * @throws Exception
   */
  static int getInternalDocid(String externalId) throws Exception {
    Query q = new TermQuery(new Term("externalId", externalId));

    IndexSearcher searcher = new IndexSearcher(QryEval.READER);
    TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
    searcher.search(q, collector);
    ScoreDoc[] hits = collector.topDocs().scoreDocs;

    if (hits.length < 1) {
      throw new Exception("External id not found.");
    } else {
      return hits[0].doc;
    }
  }

  /**
   * parseQuery converts a query string into a query tree.
   *
   * @param qString      A string containing a query.
   * @param defaultQryop Default query operator to be pushed on stack at first.
   * @return A query tree
   * @throws IOException
   */
  static Qryop parseQuery(String qString, Qryop defaultQryop) throws IOException {

    Qryop currentOp = defaultQryop;
    Stack<Qryop> stack = new Stack<Qryop>();
    stack.push(currentOp);

    // Add a default query operator to an unstructured query. This
    // is a tiny bit easier if unnecessary whitespace is removed.

    qString = qString.trim();

    // Tokenize the query.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token;

    // Each pass of the loop processes one token. To improve
    // efficiency and clarity, the query operator on the top of the
    // stack is also stored in currentOp.

    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();

      if (token.matches("[ ,(\t\n\r]")) {
        // Ignore most delimiters.
      } else if (token.equalsIgnoreCase("#and")) {
        currentOp = new QryopSlAnd();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
        currentOp = new QryopIlSyn();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")) {
        currentOp = new QryopSlOr();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#sum")) {
        currentOp = new QryopSlSum();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#wand")) {
        currentOp = new QryopSlWeightedAnd();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#wsum")) {
        currentOp = new QryopSlWeightedSum();
        stack.push(currentOp);
      } else if (token.toLowerCase().startsWith("#near")) {
        try {
          int nearArg = Integer.parseInt(token.split("/")[1]);
          currentOp = new QryopIlNear(nearArg);
          stack.push(currentOp);
        } catch (NumberFormatException e) {
          e.printStackTrace();
          fatalError("Error: Wrong format for NEAR argument.");
        }
      } else if (token.toLowerCase().startsWith("#window")) {
        try {
          int windowArg = Integer.parseInt(token.split("/")[1]);
          currentOp = new QryopIlWindow(windowArg);
          stack.push(currentOp);
        } catch (NumberFormatException e) {
          e.printStackTrace();
          fatalError("Error: Wrong format for WINDOW argument.");
        }
      } else if (token.startsWith(")")) {
        /*
          Finish current query operator.
          If the current query operator is not an argument to
          another query operator (i.e., the stack is empty when it
          is removed), we're done (assuming correct syntax - see
          below). Otherwise, add the current operator as an
          argument to the higher-level operator, and shift
          processing back to the higher-level operator.
        */
        stack.pop();
        if (stack.empty()) {
          break;
        }
        Qryop arg = currentOp;
        currentOp = stack.peek();
        currentOp.add(arg);
      } else {
        // check if current query operator needs to accept weights,
        // otherwise parse the token normally
        if (currentOp instanceof QryopSlWeighted &&
                ((QryopSlWeighted) currentOp).isAcceptWeight()) {
          double weight = Double.parseDouble(token);
          ((QryopSlWeighted) currentOp).add(weight);
          continue;
        }

        String tokenField = "body";
        if (token.contains(".")) { // if token contains field info
          String[] parts = token.split("\\.", 2);
          tokenField = parts[1];
          token = parts[0];
        }

        String[] tokenizeResult = tokenizeQuery(token);
        if (tokenizeResult.length != 0) {
          assert currentOp != null;
          currentOp.add(new QryopIlTerm(tokenizeResult[0], tokenField));
        } else {
          // discard the corresponding weight for term if currentOp is QryopSlWeighted
          if (currentOp instanceof QryopSlWeighted) {
            ((QryopSlWeighted) currentOp).discardLastWeight();
          }
        }
      }
    }

    // A broken structured query can leave unprocessed tokens on the
    // stack, so check for that.

    if (tokens.hasMoreTokens()) {
      System.err.println("Error:  Query syntax is incorrect.  " + qString);
      return null;
    }

    return currentOp;
  }

  /**
   * Print a message indicating the amount of memory used.  The
   * caller can indicate whether garbage collection should be
   * performed, which slows the program but reduces memory usage.
   *
   * @param gc If true, run the garbage collector before reporting.
   * @return void
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc) {
      runtime.gc();
    }

    System.out.println("Memory used:  " +
            ((runtime.totalMemory() - runtime.freeMemory()) /
                    (1024L * 1024L)) + " MB");
  }

  /**
   * Print the query results.
   * <p/>
   * THIS IS NOT THE CORRECT OUTPUT FORMAT.  YOU MUST CHANGE THIS
   * METHOD SO THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK
   * PAGE, WHICH IS:
   * <p/>
   * QueryID Q0 DocID Rank Score RunID
   *
   * @param queryName Original query.
   * @param result    Result object generated by {@link Qryop#evaluate}.
   * @throws IOException
   */
  static void printResults(String queryName, QryResult result) throws IOException {

    System.out.println(queryName + ":  ");
    if (result.docScores.scores.size() < 1) {
      System.out.println("\tNo results.");
    } else {
      for (int i = 0; i < result.docScores.scores.size(); i++) {
        System.out.println("\t" + i + ":  "
                + getExternalDocid(result.docScores.getDocid(i)) + ", "
                + result.docScores.getDocidScore(i));
      }
    }
  }

  /**
   * Given a query string, returns the terms one at a time with stopwords
   * removed and the terms stemmed using the Krovetz stemmer.
   * <p/>
   * Use this method to process raw query terms.
   *
   * @param query String containing query
   * @return Array of query tokens
   * @throws IOException
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();
    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }
    return tokens.toArray(new String[tokens.size()]);
  }
}
