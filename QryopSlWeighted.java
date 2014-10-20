import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QryopSlWeighted extends QryopSlAnd {

  public QryopSlWeighted() {
    // the first argument should be a weight
    this.acceptWeight = true;
    this.weights = new ArrayList<Double>();
  }

  /**
   * Appends an argument to the list of query operator arguments.
   *
   * @param a      The query argument (query operator) to append.
   * @return void
   */
  public void add(Qryop a) {
    this.args.add(a);
    // flip the flag to accept weight
    this.acceptWeight = true;
  }

  /**
   * Appends the weight to weight list.
   *
   * @param weight The weight of the query argument.
   * @return void
   */
  public void add(double weight) {
    this.weights.add(weight);
    // flip the flag to accept query operator
    this.acceptWeight = false;
  }

  /**
   * Get the indicator for accepting arguments.
   * @return
   */
  public boolean isAcceptWeight() {
    return acceptWeight;
  }

  /**
   * Discard last recorded weight.
   */
  public void discardLastWeight() {
    this.weights.remove(this.weights.size() - 1);
    // meanwhile flip the accepting flag
    acceptWeight = true;
  }

  /**
   * Weights of the query arguments.
   */
  protected final List<Double> weights;

  /**
   * Boolean flag indicating whether current operator should accept weight or query operator.
   */
  protected boolean acceptWeight;
}

