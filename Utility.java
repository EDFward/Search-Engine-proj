import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Utility class for various functions
 */
public class Utility {
  /**
   * Usage information
   */
  static String usage = "Usage:  java " + System.getProperty("sun.java.command")
          + " paramFile\n\n";

  /**
   * Read relevance from training relevance file.
   *
   * @param relevanceFile File path for doc-relevance file
   * @return A map between query id and doc-relevance
   * @throws Exception
   */
  public static Map<Integer, List<int[]>> readRelevance(String relevanceFile) throws Exception {
    String line;
    Map<Integer, List<int[]>> docRevelance = new HashMap<Integer, List<int[]>>();

    BufferedReader rankFileReader = new BufferedReader(new FileReader(relevanceFile));
    while ((line = rankFileReader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        break;
      }
      String[] parts = line.split("\\s+");
      int queryId = Integer.parseInt(parts[0]);
      int internalId = getInternalDocid(parts[2]);
      int score = Integer.parseInt(parts[3]);

      // update document-relevance
      List<int[]> relevancy;
      if (!docRevelance.containsKey(queryId)) {
        relevancy = new ArrayList<int[]>();
        docRevelance.put(queryId, relevancy);
      } else {
        relevancy = docRevelance.get(queryId);
      }
      relevancy.add(new int[] { internalId, score });
    }
    return docRevelance;
  }

  public static Map<Integer, String> readQueries(String queryFile)
          throws IOException {
    Map<Integer, String> queryStrings = new LinkedHashMap<Integer, String>();
    BufferedReader queryFileReader = null;
    String line;
    try {
      queryFileReader = new BufferedReader(new FileReader(queryFile));

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

  public static Map<Integer, Double> readPageRank(String pageRankFile) throws IOException {
    Map<Integer, Double> pageRanks = new HashMap<Integer, Double>();
    BufferedReader pageRankFileReader = null;
    String line;
    try {
      pageRankFileReader = new BufferedReader(new FileReader(pageRankFile));

      while ((line = pageRankFileReader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          break;
        }

        String[] parts = line.split("\\s+");
        // add queryId: queryString to the map
        pageRanks.put(getInternalDocid(parts[0]), Double.parseDouble(parts[1]));
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
  static void fatalError(String message) {
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
  static String getExternalDocid(int iid) throws IOException {
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
   * @throws java.io.IOException
   */
  static String[] tokenizeQuery(String query) throws IOException {

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
  static void printMemoryUsage(boolean gc) {
    Runtime runtime = Runtime.getRuntime();
    if (gc) {
      runtime.gc();
    }
    System.out.println("Memory used:  " +
            ((runtime.totalMemory() - runtime.freeMemory()) /
                    (1024L * 1024L)) + " MB");
  }

  public static List<Double> createFeatureVector(int docId, boolean[] featureMasks,
          String[] queryStems,
          Map<Integer, Double> pageRanks, RetrievalModelLeToR letorModel)
          throws IOException {
    List<Double> features = new ArrayList<Double>();
    if (featureMasks.length < 18) {
      fatalError("Error: wrong size for feature mask when creating features");
    }
    Document d = QryEval.READER.document(docId);
    // feature 1: spam scores
    if (featureMasks[0])
      features.add(Double.parseDouble(d.get("score")));
    // feature 2: url depth
    String rawUrl = d.get("rawUrl");
    if (featureMasks[1]) {
      features.add((double) (rawUrl.length() - rawUrl.replace("/", "").length()));
    }
    // feature 3: FromWikipedia score
    if (featureMasks[2]) {
      features.add(rawUrl.contains("wikipedia.org") ? 1D : 0D);
    }
    // feature 4: PageRank score
    if (featureMasks[3]) {
      features.add(pageRanks.containsKey(docId) ? pageRanks.get(docId) : 0D);
    }

    RetrievalModelBM25 bm25 = letorModel.getBm25Model();
    RetrievalModelIndri indri = letorModel.getIndriModel();

    TermVector doc = new TermVector(docId, "body");
    // feature 5: BM25 score for <q, d_body>
    if (featureMasks[4]) {
      features.add(bm25.getScore(queryStems, doc));
    }
    // feature 6: Indri score for <q, d_body>
    if (featureMasks[5]) {
      features.add(indri.getScore(queryStems, doc));
    }
    // feature 7: Term overlap score for <q, d_body>
    if (featureMasks[6]) {
      features.add(termOverlap(queryStems, doc.stems));
    }

    doc = new TermVector(docId, "title");
    // feature 8: BM25 score for <q, d_title>
    if (featureMasks[7]) {
      features.add(bm25.getScore(queryStems, doc));
    }
    // feature 9: Indri score for <q, d_title>
    if (featureMasks[8]) {
      features.add(indri.getScore(queryStems, doc));
    }
    // feature 10: Term overlap score for <q, d_title>
    if (featureMasks[9]) {
      features.add(termOverlap(queryStems, doc.stems));
    }

    doc = new TermVector(docId, "url");
    // feature 11: BM25 score for <q, d_url>
    if (featureMasks[10])
      features.add(bm25.getScore(queryStems, doc));
    // feature 12: Indri score for <q, d_url>
    if (featureMasks[11])
      features.add(indri.getScore(queryStems, doc));
    // feature 13: Term overlap score for <q, d_url>
    if (featureMasks[12])
      features.add(termOverlap(queryStems, doc.stems));

    doc = new TermVector(docId, "inlink");
    // feature 14: BM25 score for <q, d_url>
    if (featureMasks[13])
      features.add(bm25.getScore(queryStems, doc));
    // feature 15: Indri score for <q, d_url>
    if (featureMasks[14])
      features.add(indri.getScore(queryStems, doc));
    // feature 16: Term overlap score for <q, d_url>
    if (featureMasks[15])
      features.add(termOverlap(queryStems, doc.stems));

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
}
