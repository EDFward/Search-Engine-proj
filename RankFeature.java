import java.util.List;

public class RankFeature {
  private int rank;
  private List<Double> featureVector;

  public RankFeature(int rank, List<Double> featureVector) {
    this.rank = rank;
    this.featureVector = featureVector;
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
}
