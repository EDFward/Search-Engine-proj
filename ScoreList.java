/**
 *  This class implements the document score list data structure
 *  and provides methods for accessing and manipulating them.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScoreList {

  //  A little utility class to create a <docid, score> object.

  protected class ScoreListEntry {
    private int docid;
    private String externalDocId;

    private double score;

    private ScoreListEntry(int docid, double score) {
      try {
        this.externalDocId = QryEval.getExternalDocid(docid);
      } catch (IOException e) {
        System.err.println("Error: Failed to get external ID");
        e.printStackTrace();
        System.exit(1);
      }
      this.docid = docid;
      this.score = score;
    }
  }

  List<ScoreListEntry> scores = new ArrayList<ScoreListEntry>();

  /**
   * Append a document score to a score list.
   *
   * @param docid An internal document id.
   * @param score The document's score.
   * @return void
   */
  public void add(int docid, double score) {
    scores.add(new ScoreListEntry(docid, score));
  }

  /**
   * Get the n'th document id.
   *
   * @param n The index of the requested document.
   * @return The internal document id.
   */
  public int getDocid(int n) {
    return this.scores.get(n).docid;
  }

  /**
   * Get the score of the n'th document.
   *
   * @param n The index of the requested document score.
   * @return The document's score.
   */
  public double getDocidScore(int n) {
    return this.scores.get(n).score;
  }

  /**
   * Sort the score list by the entry's score in descending order.
   * Truncate the score list if necessary, then break ties using external
   * doc ID.
   * @return void
   */
  public void sortAndTruncate() {
    // sort by scores, and break ties using external id
    Collections.sort(scores, new Comparator<ScoreListEntry>() {
      @Override
      public int compare(ScoreListEntry entry1,
              ScoreListEntry entry2) {
        if (entry1.score < entry2.score) {
          return 1;
        } else if (entry1.score > entry2.score) {
          return -1;
        } else {
          return entry1.externalDocId.compareTo(entry2.externalDocId);
        }
      }
    });
    // truncate if size too big
    if (scores.size() > 100) {
      scores = new ArrayList<ScoreListEntry>(scores.subList(0, 100));
    }
  }
}
