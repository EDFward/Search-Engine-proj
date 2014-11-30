public class RankFeature {
  private String externalId;

  private double score;

  private double[] featureVector;

  public RankFeature(String externalId, double score, double[] featureVector) {
    this.externalId = externalId;
    this.score = score;
    this.featureVector = featureVector;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public double getScore() {
    return score;
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

  public String featureString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= featureVector.length; ++i) {
      sb.append(String.format("%d:%f ", i, featureVector[i-1]));
    }
    return sb.toString();
  }
}
