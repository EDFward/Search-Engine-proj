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

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class QryEval {

  /**
   * Document length reader.
   */
  public static DocLengthStore LENGTH_STORE;

  /**
   * Lucene index reader.
   */
  public static IndexReader READER;

  /**
   * Create and configure an English analyzer that will be used for
   * query parsing.
   */
  public static EnglishAnalyzerConfigurable ANALYZER =
          new EnglishAnalyzerConfigurable(Version.LUCENE_43);

  private static RetrievalModel model;

  /**
   * Static initializer.
   */
  static {
    ANALYZER.setLowercase(true);
    ANALYZER.setStopwordRemoval(true);
    ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   * @param args The only argument is the path to the parameter file.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    // when everything begins
    final long startTime = System.currentTimeMillis();

    // must supply parameter file
    if (args.length < 1) {
      System.err.println(Utility.usage);
      System.exit(1);
    }

    // read in the parameter file; one parameter per line in format of key=value
    Map<String, String> params = evaluateParams(args[0]);

    // read PageRank file
    Map<Integer, Double> pageRanks = Utility.readPageRank(params.get("letor:pageRankFile"));

    Map<Integer, String> trainingQueries = Utility.readQueries(
            params.get("letor:trainingQueryFile"));
    Map<Integer, List<RankFeature>> trainingRelevance = new HashMap<Integer, List<RankFeature>>();

    final boolean defaultFeatureMask[] = new boolean[18];
    Arrays.fill(defaultFeatureMask, true);

    // build training data
    String line;
    BufferedReader rankFileReader = new BufferedReader(
            new FileReader(params.get("letor:trainingQrelsFile")));
    while ((line = rankFileReader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        break;
      }
      String[] parts = line.split("\\s+");
      int queryId = Integer.parseInt(parts[0]);
      String externalId = parts[2];
      int score = Integer.parseInt(parts[3]);

      String queryStems[] = Utility.tokenizeQuery(trainingQueries.get(queryId));

      // update document-relevance
      List<RankFeature> featureList;
      if (!trainingRelevance.containsKey(queryId)) {
        featureList = new ArrayList<RankFeature>();
        trainingRelevance.put(queryId, featureList);
      } else {
        featureList = trainingRelevance.get(queryId);
      }
      List<Double> featureVector = Utility.createFeatureVector(externalId, defaultFeatureMask,
              queryStems, pageRanks, (RetrievalModelLeToR) model);
      featureList.add(new RankFeature(externalId, score, featureVector));
    }

    // normalize training data
    Utility.normalize(trainingRelevance);

    // write features to file
    Utility.writeFeatures(params.get("letor:trainingFeatureVectorsFile"), trainingRelevance);

    // begin training!
    Utility.trainClassifier(params.get("letor:svmRankLearnPath"),
            Double.parseDouble(params.get("letor:svmRankParamC")),
            params.get("letor:trainingFeatureVectorsFile"), params.get("letor:svmRankModelFile"));

    // read test queries and evaluate
    Map<Integer, String> testQueries = Utility.readQueries(params.get("queryFilePath"));
    Map<Integer, List<RankFeature>> testingRelevance = new LinkedHashMap<Integer, List<RankFeature>>();
    try {
      // add default query operator depending on retrieval model
      // and set parameters accordingly
      Qryop defaultQryop;
      QryResult result;

      if (model instanceof RetrievalModelBM25 || model instanceof RetrievalModelLeToR) {
        defaultQryop = new QryopSlSum();
      } else if (model instanceof RetrievalModelIndri) {
        defaultQryop = new QryopSlAnd();
      } else {
        defaultQryop = new QryopSlOr();
      }

      for (Map.Entry<Integer, String> entry : testQueries.entrySet()) {
        int queryId = entry.getKey();
        defaultQryop.clear();
        // parse the query, then evaluate
        String query = entry.getValue();
        String queryStems[] = Utility.tokenizeQuery(query);
        Qryop parsedQuery = parseQuery(query, defaultQryop);
        // one simple run of evaluation
        result = parsedQuery.evaluate(model);
        result.docScores.sortAndTruncate();
        for (int i = 0; i < result.docScores.scores.size(); ++i) {
          // update document-relevance
          List<RankFeature> featureList;
          if (!testingRelevance.containsKey(queryId)) {
            featureList = new ArrayList<RankFeature>();
            testingRelevance.put(queryId, featureList);
          } else {
            featureList = testingRelevance.get(queryId);
          }
          String externalDocid = Utility.getExternalDocid(result.docScores.getDocid(i));
          List<Double> featureVector = Utility
                  .createFeatureVector(externalDocid, defaultFeatureMask, queryStems, pageRanks,
                          (RetrievalModelLeToR) model);
          featureList.add(new RankFeature(externalDocid, 0D, featureVector));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      Utility.fatalError("Error: Evaluation failed.");
    }

    // normalize test data
    Utility.normalize(testingRelevance);
    // write features to file
    Utility.writeFeatures(params.get("letor:testingFeatureVectorsFile"), testingRelevance);

    // generate svm-rank scores
    Utility.runClassifier(params.get("letor:svmRankClassifyPath"),
            params.get("letor:testingFeatureVectorsFile"), params.get("letor:svmRankModelFile"),
            params.get("letor:testingFeatureVectorsFile"));

    // read scores into testing data
    Utility.readScores(params.get("letor:testingDocumentScores"), testingRelevance);

    // re-rank
    Utility.rerankFeatuerList(testingRelevance);

    // write ranks as trec_eval format
    Utility.writeRanks(params.get("trecEvalOutputPath"), testingRelevance);

    // print evaluation time
    final long endTime = System.currentTimeMillis();
    System.out.println("Total evaluation time: " + (endTime - startTime) / 1000.0 + " seconds");
    Utility.printMemoryUsage(false);
  }

  /**
   * parseQuery converts a query string into a query tree.
   *
   * @param qString      A string containing a query.
   * @param defaultQryop Default query operator to be pushed on stack at first.
   * @return A query tree
   * @throws IOException
   */
  private static Qryop parseQuery(String qString, Qryop defaultQryop) throws IOException {

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
          Utility.fatalError("Error: Wrong format for NEAR argument.");
        }
      } else if (token.toLowerCase().startsWith("#window")) {
        try {
          int windowArg = Integer.parseInt(token.split("/")[1]);
          currentOp = new QryopIlWindow(windowArg);
          stack.push(currentOp);
        } catch (NumberFormatException e) {
          e.printStackTrace();
          Utility.fatalError("Error: Wrong format for WINDOW argument.");
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

        String[] tokenizeResult = Utility.tokenizeQuery(token);
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

  private static Map<String, String> evaluateParams(String parammeterFilePath) throws IOException {
    Map<String, String> params = new HashMap<String, String>();
    Scanner scan = new Scanner(new File(parammeterFilePath));
    String line;
    do {
      line = scan.nextLine();
      String[] pair = line.split("=");
      params.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    scan.close();

    // parameters required for this example to run
    if (!params.containsKey("indexPath")) {
      Utility.fatalError("Error: Parameter 'indexPath' was missing.");
    }
    // open the index and LENGTH_STORE
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));
    if (READER == null) {
      Utility.fatalError(Utility.usage);
    }
    try {
      LENGTH_STORE = new DocLengthStore(READER);
    } catch (IOException e) {
      Utility.fatalError("Error: DocLengthStore initialization error");
    }

    // define the retrieval model from parameter file
    try {
      String modelName = params.get("retrievalAlgorithm");
      if (modelName.equalsIgnoreCase("letor"))
        modelName = "LeToR";
      model = (RetrievalModel) Class.forName(
              "RetrievalModel" + modelName).newInstance();
    } catch (Exception e) {
      Utility.fatalError("Error: Failed to load specified retrieval model.");
    }

    if (model instanceof RetrievalModelBM25) {
      model.setParameter("k_1", params.get("BM25:k_1"));
      model.setParameter("k_3", params.get("BM25:k_3"));
      model.setParameter("b", params.get("BM25:b"));
    } else if (model instanceof RetrievalModelIndri) {
      model.setParameter("mu", params.get("Indri:mu"));
      model.setParameter("lambda", params.get("Indri:lambda"));
    } else if (model instanceof RetrievalModelLeToR) {
      model.setParameter("k_1", params.get("BM25:k_1"));
      model.setParameter("k_3", params.get("BM25:k_3"));
      model.setParameter("b", params.get("BM25:b"));
      model.setParameter("mu", params.get("Indri:mu"));
      model.setParameter("lambda", params.get("Indri:lambda"));
    }

    return params;
  }
}