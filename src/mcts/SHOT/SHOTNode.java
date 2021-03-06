package mcts.SHOT;

import breakthrough.game.Board;
import framework.MoveList;
import framework.Options;
import framework.util.FastLog;
import mcts.transpos.ShotState;
import mcts.transpos.ShotTransposTable;

import java.util.*;

public class SHOTNode {
    private static final double LOG2 = Math.log(2);
    public static int removeCount = 0;
    private boolean expanded = false, simulated = false;
    private List<SHOTNode> C, S;
    private SHOTNode bestArm;
    private Options options;
    private int player;
    private int[] move;
    private ShotTransposTable tt;
    private long hash;
    private ShotState state;

    public SHOTNode(int player, int[] move, Options options, long hash, ShotTransposTable tt) {
        this.player = player;
        this.move = move;
        this.options = options;
        this.tt = tt;
        this.hash = hash;
        this.state = tt.getState(hash, true);
    }

    /**
     * Run the MCTS algorithm on the given node
     */
    public double SHOT(Board board, int depth, int budget, double[] plStats) {
        if (budget <= 0)
            throw new RuntimeException("Budget is " + budget);
        if (board.hash() != hash)
            throw new RuntimeException("Incorrect hash");
        double result;
        SHOTNode child = null;
        // First add some nodes if required
        if (isLeaf())
            child = expand(board);

        if (child != null) {
            if (solverCheck(child.getValue()))
                return ShotState.INF;
        }

        int s = S.size();
        // Node is terminal
        if (isSolved()) {                           // Solver
            return -getValue();
        } else if (isTerminal()) {                  // No solver
            // A draw
            int winner = board.checkWin();
            // 0: playouts, 1: player1, 2: player2, 3: budgetUsed
            for (int i = 0; i < budget; i++) {
                plStats[0]++;
                plStats[winner]++;
            }
            plStats[3] += budget;
            updateStats(plStats);
            return 0;
        }

        if (budget == 1) {
            result = playOut(board);
            // 0: playouts, 1: player1, 2: player2, 3: budgetUsed
            plStats[0]++;
            plStats[3]++;
            if (result != Board.NONE_WIN)
                plStats[(int) result]++;
            updateStats(plStats);
            return 0;
        }
        // The current node has some unvisited children
        if (getBudgetNode() <= S.size()) {
            for (SHOTNode n : S) {
                if (n.simulated || n.isSolved())
                    continue;
                Board tempBoard = board.clone();
                // Perform play-outs on all unvisited children
                tempBoard.doMove(n.getMove(), options.earlyTerm);
                result = n.playOut(tempBoard);
                //
                double[] pl = {1, 0, 0, 0};
                if (result != Board.NONE_WIN)
                    pl[(int) result]++;
                // 0: playouts, 1: player1, 2: player2, 3: budgetUsed
                plStats[0]++;
                plStats[1] += pl[1];
                plStats[2] += pl[2];
                plStats[3]++;
                // Update the child and current node
                n.updateStats(pl);
                updateStats(pl);
                // Increase the budget spent for the node
                updateBudgetSpent(pl[3]);
                // Don't go over budget
                if (plStats[3] >= budget)
                    return 0;
            }
        }
        // Don't start any rounds if there is only 1 child
        if (S.size() == 1) {
            double[] pl = {0, 0, 0, 0};
            child = S.get(0);
            result = 0;
            if (!child.isSolved()) {
                // :: Recursion
                Board tempBoard = board.clone();
                tempBoard.doMove(child.getMove(), options.earlyTerm);
                result = -child.SHOT(tempBoard, depth + 1, budget, pl);
                // 0: playouts, 1: player1, 2: player2, 3: budgetUsed
                plStats[0] += pl[0];
                plStats[1] += pl[1];
                plStats[2] += pl[2];
                plStats[3] += pl[3];
            }
            if (child.isSolved()) {
                result = child.getValue();
            }
            // The only arm is the best
            bestArm = S.get(0);
            // :: Solver
            if (Math.abs(result) == ShotState.INF)
                solverCheck(result);
            else
                updateStats(pl);
            // Increase the budget spent for the node
            updateBudgetSpent(pl[3]);
            //
            return result;
        }
        //
        int init_s = S.size();

        // Sort S such that the best node is always the first
        if (init_s > 1 && getVisits() > init_s) {
            Collections.sort(S, comparator);

//            // :: UBLB
//            if (options.UBLB) {
//                double ub, lb = S.get(0).getValue() - options.shotC *
//                        Math.sqrt(FastLog.log(getVisits()) / S.get(0).getVisits());
//                for (int i = s - 1; i > 0; i--) {
//                    ub = S.get(i).getValue() + options.shotC *
//                            Math.sqrt(FastLog.log(getVisits()) / S.get(i).getVisits());
//                    if (ub < lb) {
//                        init_s--;
//                        removeCount++;
//                    } else
//                        // All nodes before this one have overlapping bounds with the best
//                        break;
//                }
//                s = init_s;
//            }
        }

        int b = getBudget((int) getBudgetNode(), budget, init_s, init_s);
        // :: Cycle
        do {
            int n = 0, b_s = 0;
            // :: Round
            while (n < s) {
                child = S.get(n++);
                int b_b = 0;                    // This is the actual budget assigned to the child
                double[] pl = {0, 0, 0, 0};    // This will store the results of the recursion per node
                // :: Solver win
                if (!child.isSolved()) {
                    // :: Actual budget
                    int b1 = (int) (b - child.getVisits());
                    if (s == 2 && n == 1 && S.size() > 1)
                        b1 = (int) Math.max(b1, budget - plStats[3] - (b - S.get(1).getVisits()));
                    b_b = Math.min(b1, (int) (budget - plStats[3]));
                    if (b_b <= 0)
                        continue;
                    // :: Recursion
                    Board tempBoard = board.clone();
                    tempBoard.doMove(child.getMove(), options.earlyTerm);
                    result = -child.SHOT(tempBoard, depth + 1, b_b, pl);
                    //
                    plStats[0] += pl[0];
                    plStats[1] += pl[1];
                    plStats[2] += pl[2];
                    plStats[3] += pl[3];
                    // Update the stats of the node based on the playouts
                    updateStats(pl);
                } else
                    // The node is already solved
                    result = child.getValue();

                // :: Solver
                if (Math.abs(result) == ShotState.INF) {
                    if (solverCheck(result)) {   // Returns true if node is solved
                        if (result == ShotState.INF)
                            bestArm = child;
                        // Update the budgetSpent
                        state.incrBudgetSpent(plStats[3]);
                        return result;
                    } else {
                        // Redistribute the unspent budget in the next round
                        b_s += b_b - pl[3];
                    }
                }

                // Make sure we don't go over budget
                if (plStats[3] >= budget)
                    break;
            }

            // Check to see if rounds are being skipped
            double log2n = Math.max(1, Math.ceil(Math.log(s) / LOG2));

            // :: Solver
            if (options.solver) {
                for (Iterator<SHOTNode> iterator = S.iterator(); iterator.hasNext(); ) {
                    SHOTNode node = iterator.next();
                    if (node.isSolved())
                        iterator.remove();
                }
            }
            // :: Removal policy: Sorting
            if (S.size() > 0)
                Collections.sort(S.subList(0, Math.min(s, S.size())), comparator);

            // :: Removal policy: Reduction
            s -= (int) Math.floor(s / 2.);

            // For the solver
            s = Math.min(S.size(), s);
            // :: UBLB
            if (options.UBLB && s > 1 && b > S.size()) {
                double ub, lb = S.get(0).getValue() - options.shotC *
                        Math.sqrt(FastLog.log(getVisits()) / S.get(0).getVisits());
                // int oldS = s;
                for (int i = s - 1; i > 0; i--) {
                    ub = S.get(i).getValue() + options.shotC *
                            Math.sqrt(FastLog.log(getVisits()) / S.get(i).getVisits());
                    if (ub < lb) {
                        s--;
                        removeCount++;
                        //if(s < 2)
                        //    System.err.println("Many nodes removed, s: " + s + " old s: " + oldS);
                    } else
                        // All nodes before this one have overlapping bounds with the best
                        break;
                }
            }

            double log2nDiff = log2n - Math.max(1, Math.ceil(Math.log(s) / LOG2));
            // Determine the budget for the next round
            if (s == 1)
                b += budget - plStats[3]; // Spend the last bit of budget on the final node
            else {
                b += log2nDiff * getBudget((int) getBudgetNode(), budget, s, init_s); // Use the original size of S here
                // Add any skipped budget from this round
                b += Math.ceil(b_s / (double) s);
            }

        } while (plStats[3] < budget);
        // Update the budgetSpent value
        updateBudgetSpent(plStats[3]);
        // :: Final arm selection
        if (!S.isEmpty())
            bestArm = S.get(0);
        return 0;
    }

    private int getBudget(int initVis, int budget, int subS, int totS) {
        return (int) Math.max(1, Math.floor((initVis + budget) / (subS * Math.max(1, Math.ceil(Math.log(totS) / LOG2)))));
    }

    private boolean solverCheck(double result) {
        if (!options.solver)
            return false;
        // (Solver) If one of the children is a win, then I'm a loss for the opponent
        if (result == ShotState.INF) {
            setSolved(false);
            return true;
        } else if (result == -ShotState.INF) {
            boolean allSolved = true;
            // (Solver) Check if all children are a loss
            for (SHOTNode tn : C) {
                // Are all children a loss?
                if (tn.getValue() != result) {
                    allSolved = false;
                }
            }
            // (Solver) If all children lead to a loss for me, then I'm a win for the opponent
            if (allSolved) {
                setSolved(true);
                return true;
            }
        }
        return false;
    }

    private SHOTNode expand(Board board) {
        expanded = true;
        int winner = board.checkWin();
        int nextPlayer = 3 - board.getPlayerToMove();
        // If one of the nodes is a win, we don't have to select
        SHOTNode winNode = null;
        // Generate all moves
        MoveList moves = board.getExpandMoves(null);
        if (S == null)
            S = new LinkedList<SHOTNode>();
        if (C == null)
            C = new LinkedList<SHOTNode>();
        // Board is terminal, don't expand
        if (winner != Board.NONE_WIN)
            return null;
        // Add all moves as children to the current node
        for (int i = 0; i < moves.size(); i++) {
            Board tempBoard = board.clone();
            // If the game is partial observable, we don't want to do the solver part
            tempBoard.doMove(moves.get(i), options.earlyTerm);
            SHOTNode child = new SHOTNode(nextPlayer, moves.get(i), options, tempBoard.hash(), tt);
            if (options.solver && !child.isSolved()) {
                // Check for a winner, (Solver)
                winner = tempBoard.checkWin();
                if (winner == player) {
                    winNode = child;
                    child.setSolved(true);
                } else if (winner == nextPlayer) {
                    child.setSolved(false);
                }
            }
            if (!child.isSolved() && options.nodePriors && child.getVisits() == 0) {
                double npRate = board.npWinrate(player, child.move);
                child.getState().init((int) (npRate * options.npVisits), player, options.npVisits);
            }
            //
            C.add(child);
            S.add(child);
        }
        // If one of the nodes is a win, return it.
        return winNode;
    }

    private final Comparator<SHOTNode> comparator = new Comparator<SHOTNode>() {
        @Override
        public int compare(SHOTNode o1, SHOTNode o2) {
            return Double.compare(o2.getValue(), o1.getValue());
        }
    };

    public static int totalPlayouts = 0;

    private int playOut(Board board) {
        simulated = true;
        totalPlayouts++;
        int winner = board.checkWin(), nMoves = 0;
        int[] move;
        boolean interrupted = false;
        MoveList moves;
        while (winner == Board.NONE_WIN && !interrupted) {
            moves = board.getPlayoutMoves(options.heuristics);
            move = moves.get(Options.r.nextInt(moves.size()));
            board.doMove(move, options.earlyTerm);
            winner = board.checkWin();
            nMoves++;
            if (winner == Board.NONE_WIN && options.earlyTerm && nMoves == options.termDepth)
                interrupted = true;
        }

        if (interrupted) {
            double eval = board.evaluate(player, options.test);
            //System.out.println(eval);
            if (eval > options.etT)
                winner = player;
            else if (eval < -options.etT)
                winner = 3 - player;
        }
        return winner;
    }

    public SHOTNode selectBestMove() {
        // For debugging, print the nodes
        if (options.debug) {
            List<SHOTNode> l = (S.isEmpty()) ? C : S;
            for (SHOTNode t : l) {
                System.out.println(t);
            }
        }
        if (bestArm != null)
            return bestArm;
        // Select from the non-solved arms
        SHOTNode bestChild = null;
        double value;
        double max = Double.NEGATIVE_INFINITY;
        for (SHOTNode t : C) {
            if (t.getValue() == ShotState.INF)
                value = ShotState.INF + Options.r.nextDouble();
            else if (t.getValue() == -ShotState.INF)
                value = -ShotState.INF + t.getVisits() + Options.r.nextDouble();
            else {
                // Select the child with the highest value
                value = t.getValue();
            }
            if (value > max) {
                max = value;
                bestChild = t;
            }
        }
        if (bestChild == null)
            throw new NullPointerException("bestChild is null, root has " + C.size() + " children");
        return bestChild;
    }

    public boolean isLeaf() {
        return C == null || !expanded;
    }

    public boolean isTerminal() {
        return expanded && C != null && C.size() == 0;
    }

    private void setValue(ShotState s) {
        if (state == null)
            state = tt.getState(hash, false);
        state.setValue(s);
    }

    private void updateBudgetSpent(double n) {
        if (state == null)
            state = tt.getState(hash, false);
        state.incrBudgetSpent(n);
    }

    private void updateStats(double[] plStats) {
        if (state == null)
            state = tt.getState(hash, false);
        state.updateStats(plStats[0], plStats[1], plStats[2]);
    }

    private double getBudgetNode() {
        if (state == null)
            state = tt.getState(hash, true);
        if (state == null)
            return 0;
        return state.getBudgetSpent();
    }

    private void setSolved(boolean win) {
        if (state == null)
            state = tt.getState(hash, false);
        if (win)
            state.setSolved(3 - player);
        else
            state.setSolved(player);
    }

    /**
     * @return The value of this node with respect its parent
     */
    private double getValue() {
        if (state == null)
            state = tt.getState(hash, true);
        if (state == null)
            return 0.;
        return state.getMean(3 - player);
    }

    /**
     * @return The number of visits of the transposition
     */
    private double getVisits() {
        if (state == null)
            state = tt.getState(hash, true);
        if (state == null)
            return 0.;
        return state.getVisits();
    }

    private ShotState getState() {
        if (state == null)
            state = tt.getState(hash, false);
        return state;
    }

    private boolean isSolved() {
        return Math.abs(getValue()) == ShotState.INF;
    }

    public int[] getMove() {
        return move;
    }

    @Override
    public String toString() {
        if (state != null) {
            return Board.getMoveString(move) + " " + state.toString();
        } else {
            return move.toString();
        }
    }
}