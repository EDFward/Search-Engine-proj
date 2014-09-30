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

  static String usage = "Usage:  java " + System.getProperty("sun.java.command")
          + " paramFile\n\n";

  //  The index file reader is accessible via a global variable. This
  //  isn't great programming style, but the alternative is for every
  //  query operator to store or pass this value, which creates its
  //  own headaches.

  public static IndexReader READER;

  //  Create and configure an English analyzer that will be used for
  //  query parsing.

  public static EnglishAnalyzerConfigurable analyzer =
          new EnglishAnalyzerConfigurable(Version.LUCENE_43);

  static {
    analyzer.setLowercase(true);
    analyzer.setStopwordRemoval(true);
    analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   * @param args The only argument is the path to the parameter file.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {

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

    // open the index
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));

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
    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));
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
        defaultQryop.clear();
        // parse the query, then evaluate
        QryResult result = parseQuery(entry.getValue(), defaultQryop).evaluate(model);
        // sort and truncate to 100 if necessary
        result.docScores.sortAndTruncate();
        int queryId = entry.getKey();
        // write to evaluation file
        if (result.docScores.scores.size() < 1) {
          writer.write(queryId + " Q0 dummy 1 0 run-1\n");
        } else {
          for (int j = 0; j < result.docScores.scores.size(); ++j) {
            String s = String.format("%d Q0 %s %d %.10f run-1\n",
                    queryId,                                              // query id
                    getExternalDocid(result.docScores.getDocid(j)), // external id
                    j + 1,                                          // rank
                    result.docScores.getDocidScore(j));
            writer.write(s);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      fatalError("Error: evaluation failed.");
    } finally {
      try {
        assert writer != null;
        writer.close();
      } catch (Exception ignored) {
      }
    }

    printMemoryUsage(false);
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
      } else if (token.toLowerCase().startsWith("#near")) {
        try {
          int nearArg = Integer.parseInt(token.split("/")[1]);
          currentOp = new QryopIlNear(nearArg);
          stack.push(currentOp);
        } catch (NumberFormatException e) {
          e.printStackTrace();
          fatalError("Error: wrong format for NEAR argument.");
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
