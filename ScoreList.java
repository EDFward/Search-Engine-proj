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
   * Sort the score list (using a heap) by the entry's score in descending order and
   * break ties using external doc ID.
   *
   * @return void
   */
  public void sortAndTruncate() throws IOException {
    /*
     * A hashmap is necessary to avoid duplicate computations of
     * external id, which is very costly. Such hashmap ensures
     * getExternalDocid only gets called once for every docs
     */
    int scoreListSize = scores.size();
    final Map<ScoreListEntry, String> externalIds = new HashMap<ScoreListEntry, String>();

    if (scoreListSize > 100) {
      // keep a min heap to maintain highest score entries
      ScoreListEntry[] heap = new ScoreListEntry[100];
      for (int i = 0; i < 100; ++i) {
        ScoreListEntry entry = scores.get(i);
        heap[i] = entry;
        externalIds.put(entry, QryEval.getExternalDocid(entry.docid));
      }

      // heapify
      for (int i = heap.length / 2 - 1; i >= 0; --i) {
        sink(heap, i, externalIds);
      }

      // compare each incoming element
      for (int i = 100; i < scoreListSize; ++i) {
        ScoreListEntry next = scores.get(i);
        if (next.score < heap[0].score) // no need to consider
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

          // keep the heap structured
          sink(heap, 0, externalIds);
        }
      }

      // update the scores to keep only 100 entries
      scores.clear();
      scores.addAll(Arrays.asList(heap));
    }
    else {
      // otherwise store the external ID and then directly sort
      for (ScoreListEntry entry : scores) {
        externalIds.put(entry, QryEval.getExternalDocid(entry.docid));
      }
    }

    // now `scores` has elements <= 100, ok to sort (and the external ids
    // are recorded in the map, thus no need to read them again)
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

  /**
   * Sift-down operation for heap structure.
   *
   * @param h           Heap as a fixed length array
   * @param pos         Position in the heap to be sifted down
   * @param externalIds Doc-ExternalId map for comparision
   */
  private static void sink(ScoreListEntry[] h, int pos, Map<ScoreListEntry, String> externalIds) {
    int subRoot = pos;
    while (subRoot * 2 + 1 < h.length) { // until the subroot is a leaf
      int minChild = subRoot * 2 + 1;    // first get left child

      // if right child if smaller than left (score/external Id),
      // let minChild point to it
      if (minChild + 1 < h.length) {
        if (h[minChild + 1].score < h[minChild].score) {
          minChild++;
        } else if (h[minChild].score == h[minChild + 1].score) {
          String leftExtId = externalIds.get(h[minChild]),
                  rightExtId = externalIds.get(h[minChild + 1]);
          if (rightExtId.compareTo(leftExtId) > 0) {
            minChild++;
          }
        }
      }
      // now compare the root and minChild
      if (h[subRoot].score < h[minChild].score ||
              (h[minChild].score == h[subRoot].score &&
                      externalIds.get(h[subRoot]).compareTo(externalIds.get(h[minChild])) > 0)) {
        return; // no need to sink down
      } else {
        // swap, then continue sinking
        ScoreListEntry tmp = h[subRoot];
        h[subRoot] = h[minChild];
        h[minChild] = tmp;
        subRoot = minChild;
      }
    }
  }
}
