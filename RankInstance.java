public class RankInstance {
  /**
   * External document ID.
   */
  private String externalId;

  /**
   * Assigned score. For training instance it's relevance score (like 0~2),
   * for testing feature it's determined by SVM-RANK.
   */
  private double score;

  /**
   * Feature vector for an instance.
   */
  private double[] featureVector;

  public RankInstance(String externalId, double score, double[] featureVector) {
    this.externalId = externalId;
    this.score = score;
    this.featureVector = featureVector;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public String getExternalId() {
    return externalId;
  }

  public int featureSize() {
    return featureVector.length;
  }

  public double getFeature(int i) {
    return featureVector[i];
  }

  public void setFeature(int i, double val) {
    featureVector[i] = val;
  }

  /**
   * Formulate the feature string by concatenating index and features.
   *
   * @return Formulated feature string
   */
  public String featureString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= featureVector.length; ++i) {
      sb.append(String.format("%d:%f ", i, featureVector[i - 1]));
    }
    return sb.toString();
  }
}
