import java.util.List;

public class RankFeature {
  private String externalId;

  private int rank;

  private List<Double> featureVector;

  public RankFeature(String externalId, int rank, List<Double> featureVector) {
    this.externalId = externalId;
    this.rank = rank;
    this.featureVector = featureVector;
  }

  public int getRank() {
    return rank;
  }

  public String getExternalId() {
    return externalId;
  }

  public int featureSize() {
    return featureVector.size();
  }

  public double getFeature(int i) {
    return featureVector.get(i);
  }

  public void setFeature(int i, double val) {
    featureVector.set(i, val);
  }

  public String featureString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= featureVector.size(); ++i) {
      sb.append(String.format("%d:%f ", i, featureVector.get(i - 1)));
    }
    return sb.toString();
  }
}
