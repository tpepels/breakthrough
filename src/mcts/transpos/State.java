package mcts.transpos;

import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.text.DecimalFormat;

public class State {

    private static final DecimalFormat df2 = new DecimalFormat("###,##0.000");
    public static final int REG_PLAYER = 1;

    public static float INF = 999999;
    public long hash;
    public int visits = 0, lastVisit = 0, imValue = Integer.MIN_VALUE;
    private float sum;
    public short solvedPlayer = 0;
    public boolean visited = false;
    public SimpleRegression simpleRegression;
    //
    public State next = null;

    public State(long hash) {
        this.hash = hash;
    }

    public void updateStats(double score, boolean regression) {
        visited = true;
        if (solvedPlayer != 0)
            throw new RuntimeException("updateStats called on solved position!");
        sum += score;
        this.visits++;

        if (regression) {
            // Only create a regression model if there are some visits
            if (simpleRegression == null || visits % 1000 == 0) {
                simpleRegression = new SimpleRegression();
            }

            simpleRegression.addData(visits, getMean(REG_PLAYER));
        }
    }

    public double getRegressionValue(int steps, int player) {
        if(player == REG_PLAYER)
            return simpleRegression.predict(visits + steps);
        else
            return -1 * simpleRegression.predict(visits + steps);
    }

    public void init(int wins, int visits) {
        if (solvedPlayer != 0)
            throw new RuntimeException("updateStats called on solved position!");
        sum += wins;
        this.visits += visits;
    }

    public float getMean(int player) {
        visited = true;
        if (solvedPlayer == 0) { // Position is not solved, return mean
            if (visits > 0)
                return sum / visits;
            else
                return 0;
        } else    // Position is solved, return inf
            return (player == solvedPlayer) ? INF : -INF;
    }

    public void setImValue(int imValue) {
        this.imValue = imValue;
    }

    public void setSolved(int player) {
        visited = true;
        if (solvedPlayer > 0 && player != solvedPlayer)
            throw new RuntimeException("setSolved with different player!");
        this.solvedPlayer = (short) player;
    }

    public int getVisits() {
        return visits;
    }

    public String toString() {
        if (solvedPlayer == 0)
            if (imValue > Integer.MIN_VALUE)
                return df2.format(getMean(1)) + "\tn:" + visits + "\tim: " + imValue;
            else
                return df2.format(getMean(1)) + "\tn:" + visits;
        else
            return "solved win P" + solvedPlayer;
    }
}
