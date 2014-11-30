import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.*;
import java.util.*;

/**
 * Utility class for various functions
 */
public class Utility {

  public static final int FEATURE_NUM = 18;

  /**
   * Usage information
   */
  static String usage = "Usage:  java " + System.getProperty("sun.java.command")
          + " paramFile\n\n";

  private static final double NON_EXISTENT_FEATURE = Double.MIN_VALUE;

  public static Map<Integer, String> readQueries(String filePath)
          throws IOException {
    Map<Integer, String> queryStrings = new LinkedHashMap<Integer, String>();
    BufferedReader queryFileReader = null;
    String line;
    try {
      queryFileReader = new BufferedReader(new FileReader(filePath));

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
      fatalError("Error: Read query file failed.");
    } finally {
      assert queryFileReader != null;
      queryFileReader.close();
    }
    return queryStrings;
  }

  public static Map<String, Double> readPageRank(String filePath) throws IOException {
    Map<String, Double> pageRanks = new HashMap<String, Double>();
    BufferedReader pageRankFileReader = null;
    String line;
    try {
      pageRankFileReader = new BufferedReader(new FileReader(filePath));

      while ((line = pageRankFileReader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          break;
        }

        String[] parts = line.split("\\s+");
        // add queryId: queryString to the map
        pageRanks.put(parts[0], Double.parseDouble(parts[1]));
      }
    } catch (Exception e) {
      fatalError("Error: Read page rank file failed.");
    } finally {
      if (pageRankFileReader != null) {
        pageRankFileReader.close();
      }
    }
    return pageRanks;
  }

  public static void readScores(String filePath, Map<Integer, List<RankFeature>> featureMap)
          throws IOException {
    BufferedReader scoreFileReader;
    String line;
    List<Double> scores = new ArrayList<Double>();
    scoreFileReader = new BufferedReader(new FileReader(filePath));
    while ((line = scoreFileReader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        break;
      }
      scores.add(Double.parseDouble(line));
    }

    int scoreIndex = 0;
    for (Map.Entry<Integer, List<RankFeature>> entry : featureMap.entrySet()) {
      for (RankFeature feature : entry.getValue()) {
        feature.setScore(scores.get(scoreIndex++));
      }
    }
  }

  public static boolean[] readFeatureMask(String disableIndex) {
    boolean featureMask[] = new boolean[FEATURE_NUM];
    Arrays.fill(featureMask, true);
    if (disableIndex != null)
      for (String i : disableIndex.split(",")) {
        featureMask[Integer.parseInt(i) - 1] = false;
      }
    return featureMask;
  }

  public static void writeFeatures(String filePath, Map<Integer, List<RankFeature>> featureMap)
          throws IOException {
    BufferedWriter featureWriter = new BufferedWriter(new FileWriter(new File(filePath)));
    for (Map.Entry<Integer, List<RankFeature>> entry : featureMap.entrySet()) {
      int queryId = entry.getKey();
      for (RankFeature feature : entry.getValue()) {
        String s = String.format("%f qid:%d %s# %s\n",
                feature.getScore(),
                queryId,
                feature.featureString(),
                feature.getExternalId());
        featureWriter.write(s);
      }
    }
    featureWriter.close();
  }

  public static void writeRanks(String filePath, Map<Integer, List<RankFeature>> featureMap)
          throws IOException {
    BufferedWriter rankWriter = new BufferedWriter(new FileWriter(new File(filePath)));
    for (Map.Entry<Integer, List<RankFeature>> entry : featureMap.entrySet()) {
      int queryId = entry.getKey();
      int rank = 1;
      for (RankFeature feature : entry.getValue()) {
        String s = String.format("%d Q0 %s %d %.10f run-1\n",
                queryId,
                feature.getExternalId(),
                rank++,
                feature.getScore());
        rankWriter.write(s);
      }
    }
    rankWriter.close();
  }

  public static void rerankFeatuerList(Map<Integer, List<RankFeature>> featureMap) {
    for (List<RankFeature> featureList : featureMap.values()) {
      Collections.sort(featureList, new Comparator<RankFeature>() {
        @Override
        public int compare(RankFeature entry1,
                RankFeature entry2) {
          double score1 = entry1.getScore(), score2 = entry2.getScore();
          if (score1 < score2) {
            return 1;
          } else if (score1 > score2) {
            return -1;
          } else {
            return 0;
          }
        }
      });
    }
  }

  public static void trainClassifier(String execPath, double c, String qrelsFeatureOutputFile,
          String modelOutputFile) throws Exception {
    Process cmdProc = Runtime.getRuntime().exec(
            new String[] { execPath, "-c", String.valueOf(c), qrelsFeatureOutputFile,
                    modelOutputFile });

    // The stdout/stderr consuming code MUST be included.
    // It prevents the OS from running out of output buffer space and stalling.

    // consume stdout and print it out for debugging purposes
    BufferedReader stdoutReader = new BufferedReader(
            new InputStreamReader(cmdProc.getInputStream()));
    String line;
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    // consume stderr and print it for debugging purposes
    BufferedReader stderrReader = new BufferedReader(
            new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }

    // get the return value from the executable. 0 means success, non-zero
    // indicates a problem
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
  }

  public static void runClassifier(String execPath, String qrelsFeatureOutputFile,
          String modelFile, String predictionOutputFile) throws Exception {
    Process cmdProc = Runtime.getRuntime().exec(
            new String[] { execPath, qrelsFeatureOutputFile, modelFile, predictionOutputFile });

    BufferedReader stdoutReader = new BufferedReader(
            new InputStreamReader(cmdProc.getInputStream()));
    String line;
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    BufferedReader stderrReader = new BufferedReader(
            new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
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
  public static int getInternalDocid(String externalId) throws Exception {
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
   * Write an error message and exit.  This can be done in other
   * ways, but I wanted something that takes just one statement so
   * that it is easy to insert checks without cluttering the code.
   *
   * @param message The error message to write before exiting.
   */
  public static void fatalError(String message) {
    System.err.println(message);
    System.exit(1);
  }

  /**
   * Get the external document id for a document specified by an
   * internal document id. If the internal id doesn't exists, returns null.
   *
   * @param iid The internal document id of the document.
   * @throws java.io.IOException
   */
  public static String getExternalDocid(int iid) throws IOException {
    Document d = QryEval.READER.document(iid);
    return d.get("externalId");
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
   * @throws java.io.IOException
   */
  public static void printResults(String queryName, QryResult result) throws IOException {

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
   * @throws java.io.IOException
   */
  public static String[] tokenizeQuery(String query) throws IOException {

    Analyzer.TokenStreamComponents comp = QryEval.ANALYZER
            .createComponents("dummy", new StringReader(query));
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

  /**
   * Print a message indicating the amount of memory used.  The
   * caller can indicate whether garbage collection should be
   * performed, which slows the program but reduces memory usage.
   *
   * @param gc If true, run the garbage collector before reporting.
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

  public static double[] createFeatureVector(String externalId, boolean[] featureMasks,
          String[] queryStems,
          Map<String, Double> pageRanks, RetrievalModelLeToR letorModel)
          throws Exception {
    int docId = getInternalDocid(externalId);
    double features[] = new double[FEATURE_NUM];
    Arrays.fill(features, NON_EXISTENT_FEATURE);

    if (featureMasks.length < FEATURE_NUM) {
      fatalError("Error: wrong size for feature mask when creating features");
    }
    Document d = QryEval.READER.document(docId);
    // feature 1: spam scores
    if (featureMasks[0])
      features[0] = Double.parseDouble(d.get("score"));
    // feature 2: url depth
    String rawUrl = d.get("rawUrl");
    if (featureMasks[1]) {
      features[1] = (double) (rawUrl.length() - rawUrl.replace("/", "").length());
    }
    // feature 3: FromWikipedia score
    if (featureMasks[2]) {
      features[2] = rawUrl.contains("wikipedia.org") ? 1D : 0D;
    }
    // feature 4: PageRank score
    if (featureMasks[3] && pageRanks.containsKey(externalId)) {
      features[3] = pageRanks.get(externalId);
    }

    RetrievalModelBM25 bm25 = letorModel.getBm25Model();
    RetrievalModelIndri indri = letorModel.getIndriModel();

    TermVector doc, docBody = null;
    try {
      doc = new TermVector(docId, "body");
      // keep a record for feature 18
      docBody = doc;

      // feature 5: BM25 score for <q, d_body>
      if (featureMasks[4]) {
        features[4] = bm25.getScore(queryStems, doc);
      }
      // feature 6: Indri score for <q, d_body>
      if (featureMasks[5]) {
        features[5] = indri.getScore(queryStems, doc);
      }
      // feature 7: Term overlap score for <q, d_body>
      if (featureMasks[6]) {
        features[6] = termOverlap(queryStems, doc.stems);
      }
    } catch (IOException ignored) {
    }

    try {
      doc = new TermVector(docId, "title");
      // feature 8: BM25 score for <q, d_title>
      if (featureMasks[7]) {
        features[7] = bm25.getScore(queryStems, doc);
      }
      // feature 9: Indri score for <q, d_title>
      if (featureMasks[8]) {
        features[8] = indri.getScore(queryStems, doc);
      }
      // feature 10: Term overlap score for <q, d_title>
      if (featureMasks[9]) {
        features[9] = termOverlap(queryStems, doc.stems);
      }
    } catch (IOException ignored) {
    }

    try {
      doc = new TermVector(docId, "url");
      // feature 11: BM25 score for <q, d_url>
      if (featureMasks[10])
        features[10] = bm25.getScore(queryStems, doc);
      // feature 12: Indri score for <q, d_url>
      if (featureMasks[11])
        features[11] = indri.getScore(queryStems, doc);
      // feature 13: Term overlap score for <q, d_url>
      if (featureMasks[12])
        features[12] = termOverlap(queryStems, doc.stems);
    } catch (IOException ignored) {
    }

    try {
      doc = new TermVector(docId, "inlink");
      // feature 14: BM25 score for <q, d_inlink>
      if (featureMasks[13])
        features[13] = bm25.getScore(queryStems, doc);
      // feature 15: Indri score for <q, d_inlink>
      if (featureMasks[14])
        features[14] = indri.getScore(queryStems, doc);
      // feature 16: Term overlap score for <q, d_inlink>
      if (featureMasks[15])
        features[15] = termOverlap(queryStems, doc.stems);
      // feature 17: inlink number
      if (featureMasks[16]) {
        features[16] = doc.stemsLength();
      }
    } catch (IOException ignored) {
    }

    // feature 18: ranked boolean score in body field
    RetrievalModelRankedBoolean rankedBoolean = letorModel.getRankedBooleanModel();
    if (featureMasks[17] && docBody != null) {
      features[17] = rankedBoolean.getScore(queryStems, docBody);
    }

    return features;
  }

  private static double termOverlap(String[] queryStems, String[] docStems) {
    int matchedCount = 0;
    Set<String> docStemSet = new HashSet<String>(Arrays.asList(docStems));
    for (String queryStem : queryStems) {
      if (docStemSet.contains(queryStem))
        ++matchedCount;
    }
    return ((double) matchedCount) / queryStems.length;
  }

  public static void normalize(Map<Integer, List<RankFeature>> featureMap) {
    int featureSize = featureMap.values().iterator().next().get(0).featureSize();
    for (Map.Entry<Integer, List<RankFeature>> entry : featureMap.entrySet()) {
      List<RankFeature> featureList = entry.getValue();
      // record min-max
      double recordMinMax[][] = new double[2][featureSize];
      for (int i = 0; i < featureSize; ++i) {
        recordMinMax[0][i] = Double.MAX_VALUE;
        recordMinMax[1][i] = Double.MIN_VALUE;
      }
      for (int i = 0; i < featureSize; ++i) {
        for (RankFeature feature : featureList) {
          double featureValue = feature.getFeature(i);
          // skip non-existent feature when recording
          if (featureValue == NON_EXISTENT_FEATURE)
            continue;
          if (featureValue < recordMinMax[0][i])
            recordMinMax[0][i] = featureValue;
          if (featureValue > recordMinMax[1][i])
            recordMinMax[1][i] = featureValue;
        }
      }

      // do normalization
      for (int i = 0; i < featureSize; ++i) {
        for (RankFeature feature : featureList) {
          double diff = recordMinMax[1][i] - recordMinMax[0][i];
          double featureValue = feature.getFeature(i);

          if (diff == 0 || featureValue == NON_EXISTENT_FEATURE) {
            feature.setFeature(i, 0d);
          }
          else {
            feature.setFeature(i,
                    (featureValue - recordMinMax[0][i]) / diff);
          }
        }
      }
    }
  }
}
