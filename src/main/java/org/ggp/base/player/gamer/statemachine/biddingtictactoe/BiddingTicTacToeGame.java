package org.ggp.base.player.gamer.statemachine.biddingtictactoe;

import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.List;

/**
 * Created by Amata on 3/2/2016 AD.
 */
public abstract class BiddingTicTacToeGame extends BaseGamePlayer {
    private StateMachine theMachine;
    private MachineState theState;
    private Role role;

    private final int NUM_ROW = 3;
    private final int NUM_COL = 3;
    private char[][] board = null;
    private boolean tiebreaker = false;
    private boolean bidding = true;
    private int myCoins = 0;
    private int yourCoins = 0;
    private char myRole;
    private char yourRole;


    private void initBoard() {
        System.out.println("initBoard");
        board = new char[NUM_COL][NUM_ROW];
        for (int i = 0; i < NUM_COL; i++)
            for (int j = 0; j < NUM_ROW; j++)
                board[i][j] = 'b';
    }

    public char getMyRole() {
        return myRole;
    }

    private void initMyRole() {
        System.out.println("initMyRole");
        if (role.toString() == "x") {
            myRole = 'x';
            yourRole = 'o';
        } else {
            myRole = 'o';
            yourRole = 'x';
        }
    }

    public boolean isBidding() {
        return bidding;
    }

    public int getMyCoins() {
        return myCoins;
    }

    public int getYourCoins() {
        return yourCoins;
    }

    public boolean hasTiebreaker() {
        return tiebreaker;
    }

    private void updateBoard(String state) {
        // updates a board
        // example: ( true ( cell 3 2 b ) )
        int startIndex, row, col;
        startIndex = state.lastIndexOf('l');
        row = Character.getNumericValue(state.charAt(startIndex + 2)) - 1;
        col = Character.getNumericValue(state.charAt(startIndex + 4)) - 1;
        board[col][row] = state.charAt(startIndex + 6);
    }

    private void updateTiebreaker(String state) {
        // updates tiebreaker
        // example: ( true ( tiebreaker x ) )
        if ((state.contains("x") && getMyRole() == 'x') || (state.contains("o") && getMyRole() == 'o'))
            tiebreaker = true;
        else
            tiebreaker = false;
    }

    private void updateCoins(String state) {
        // updates coins
        // example: ( true ( coins x 6 ) )
        int len = "coins x ".length();
        if (state.contains("x") && myRole == 'x') {
            myCoins = Character.getNumericValue(state.charAt(state.indexOf("coins x ") + len));
        } else if (state.contains("x") && myRole == 'o') {
            yourCoins = Character.getNumericValue(state.charAt(state.indexOf("coins x ") + len));
        } else if (state.contains("o") && myRole == 'x') {
            yourCoins = Character.getNumericValue(state.charAt(state.indexOf("coins o ") + len));
        } else if (state.contains("o") && myRole == 'o') {
            myCoins = Character.getNumericValue(state.charAt(state.indexOf("coins o ") + len));
        }
    }

    /*
     * Gets a mark by given a position on a board (row, col)
     * x, o, and b(blank)
     */
    public char board(int col, int row) {
        if (board == null)
            initBoard();
        return board[col - 1][row - 1];
    }

    @Override
    public void init(StateMachine stateMachine, Role theRole) {
        theMachine = stateMachine;
        role = theRole;
        initBoard();
        initMyRole();
    }

    @Override
    public void updateGameState(MachineState machineState) {
        theState = machineState;
        String state;
        for (GdlSentence sentence : machineState.getContents()) {
            state = sentence.toString();
            //System.out.println("state = " + state);
            if (state.contains("cell")) {
                updateBoard(state);
                //System.out.println("updateBoard");
            } else if (state.contains("coins")) {
                updateCoins(state);
                //System.out.println("updateCoins");
            } else if (state.contains("tiebreaker")) {
                updateTiebreaker(state);
                // System.out.println("updateTiebreaker");
            } else if (state.contains("bidding")) {
                bidding = true;
                // System.out.println("bidding");
            } else if (state.contains("control")) {
                bidding = false;
                // System.out.println("control");
                // System.out.println("state = " + state);

            }
        }
    }

    protected Move bid(int coins, boolean useTiebreaker) throws MoveDefinitionException {
        if (coins > getMyCoins())
            coins = getMyCoins();
        else if (coins < 0)
            coins = 0;

        List<Move> moves = theMachine.getLegalMoves(theState, role);
        for (Move move : moves) {
            //( bid 2 with_tiebreaker )
            //( bid 2 no_tiebreaker )
            System.out.println("bid = " + move.toString());
            String moveString = move.toString();
            int coinsOfThisMove = Character.getNumericValue(move.toString().charAt(moveString.indexOf("bid") + "bid ".length()));
            System.out.println("coinsOfThisMove = " + coinsOfThisMove);
            if (hasTiebreaker() && useTiebreaker && moveString.contains("with_tiebreaker") && coinsOfThisMove == coins) {
                System.out.println("move = " + move.toString());
                return move;
            } else if (hasTiebreaker() && !useTiebreaker && moveString.contains("no_tiebreaker") && coinsOfThisMove == coins) {
                System.out.println("move = " + move.toString());
                return move;
            } else if (!hasTiebreaker() && moveString.contains("no_tiebreaker") && coinsOfThisMove == coins) {
                System.out.println("move = " + move.toString());
                return move;
            }
        }

        System.out.println("bid invalid, pick first bid instead");
        return moves.get(0);
    }

    /**
     * Marks 'x' or 'o' on a board by given row and col.
     * No move return if enter invalid row or col.
     */
    protected Move mark(int row, int col) throws MoveDefinitionException {
        List<Move> moves = theMachine.getLegalMoves(theState, role);
        for (Move move : moves) {
            //move.getContents().toSentence().getName()
            //System.out.println("move = " + move.toString());
            // format:  ( mark col row )
            // example: ( mark 1 3 )
            if (move.toString().charAt(0) == '(' &&
                    row == Character.getNumericValue(move.toString().charAt(7)) &&
                    col == Character.getNumericValue(move.toString().charAt(9)))
                return move;
        }

        System.out.println("move invalid, pick first move instead");
        return moves.get(0);
    }

}
