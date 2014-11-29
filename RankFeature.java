import java.util.List;

public class RankFeature {
  private int queryId;
  private int rank;
  private List<Double> featureVector;

  public RankFeature(int queryId, int rank, List<Double> featureVector) {
    this.queryId = queryId;
    this.rank = rank;
    this.featureVector = featureVector;
  }
}
