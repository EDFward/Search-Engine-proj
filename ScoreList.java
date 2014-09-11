/**
 *  This class implements the document score list data structure
 *  and provides methods for accessing and manipulating them.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.*;

public class ScoreList {

  //  A little utility class to create a <docid, score> object.

  protected class ScoreListEntry {
    private int docid;

    private double score;

    private ScoreListEntry(int docid, double score) {
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
   *
   * @return void
   */
  public void sortAndTruncate() throws IOException {
    /*
     * A hashmap is necessary to avoid duplicate computations of
     * external id, which is very costly. Such hashmap ensures
     * getExternalDocid only gets called once for all docs
     */
    int scoreListSize = scores.size();
    final Map<ScoreListEntry, String> externalIds = new HashMap<ScoreListEntry, String>(
            scoreListSize);
    for (int i = 0; i < scoreListSize; ++i) {
      ScoreListEntry entry = scores.get(i);
      externalIds.put(entry, QryEval.getExternalDocid(entry.docid));
    }

    if (scoreListSize > 100) {
      // keep a min heap to maintain highest score entries
      ScoreListEntry[] heap = new ScoreListEntry[100];
      for (int i = 0; i < 100; ++i) {
        heap[i] = scores.get(i);
      }

      // heapify
      for (int i = heap.length / 2 - 1; i >= 0; --i) {
        sink(heap, i, externalIds);
      }

      for (int i = 100; i < scoreListSize; ++i) {
        ScoreListEntry next = scores.get(i);
        if (next.score < heap[0].score) // no need to consider next
        {
          continue;
        } else { // now have to compare next with heap[0]
          String nextExternalId = QryEval.getExternalDocid(next.docid);
          // decide whether to evict heap[0] or not
          if (next.score == heap[0].score &&
                  nextExternalId.compareTo(externalIds.get(heap[0])) > 0) {
            continue;   // neglect
          }

          // evict and update externalId map
          externalIds.remove(heap[0]);
          heap[0] = next;
          externalIds.put(next, nextExternalId);

          sink(heap, 0, externalIds);
        }
      }
      scores.clear();
      scores.addAll(Arrays.asList(heap));
    }

    Collections.sort(scores, new Comparator<ScoreListEntry>() {
      @Override
      public int compare(ScoreListEntry entry1,
              ScoreListEntry entry2) {
        if (entry1.score < entry2.score) {
          return 1;
        } else if (entry1.score > entry2.score) {
          return -1;
        } else {
          return externalIds.get(entry1).compareTo(externalIds.get(entry2));
        }
      }
    });
  }

  private static void sink(ScoreListEntry[] h, int pos, Map<ScoreListEntry, String> externalIds) {
    // check boundary
    if (pos > h.length / 2 - 1) {
      return;
    }

    int leftPos = pos * 2 + 1,
            rightPos = pos * 2 + 2;

    int minChildPos = leftPos;
    if (rightPos < h.length) {
      if (h[rightPos].score < h[leftPos].score) {
        minChildPos = rightPos;
      } else if (h[rightPos].score == h[leftPos].score) {
        String leftExtId = externalIds.get(h[leftPos]),
                rightExtId = externalIds.get(h[rightPos]);
        if (rightExtId.compareTo(leftExtId) > 0) {
          minChildPos = rightPos;
        }
      }
    }

    if (h[pos].score < h[minChildPos].score ||
            (h[minChildPos].score == h[pos].score &&
                    externalIds.get(h[pos]).compareTo(externalIds.get(h[minChildPos])) > 0)) {
      return; // no need to sink down
    } else {
      // first swap, then continue sinking
      ScoreListEntry tmp = h[pos];
      h[pos] = h[minChildPos];
      h[minChildPos] = tmp;
      sink(h, minChildPos, externalIds);
    }
  }
}
