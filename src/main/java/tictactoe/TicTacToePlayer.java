package tictactoe;

import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

import java.util.List;

public abstract class TicTacToePlayer extends BaseGamePlayer {
    private StateMachine theMachine;
    private MachineState theState;
    private Role role;

    protected final int NUM_ROW = 3;
    protected final int NUM_COL = 3;
    protected static final char BLANK = 'b';
    protected static final char X = 'x';
    protected static final char O = 'o';

    private char[][] board = null;
    private char mySymbol;
    private char opponentSymbol;

    private void initBoard() {
        board = new char[NUM_COL][NUM_ROW];
        for (int i = 0; i < NUM_COL; i++)
            for (int j = 0; j < NUM_ROW; j++)
                board[i][j] = 'b';
    }

    /*
     * Returns a role 'x' or 'o'
     */
    public char getMySymbol() {
        return mySymbol;
    }

    public char getOpponentSymbol() {
        return opponentSymbol;
    }

    private void initMyRole() {
        System.out.println(role.toString());
        if (role.toString() == "xplayer") {
            mySymbol = 'x';
            opponentSymbol = 'o';
        } else {
            mySymbol = 'o';
            opponentSymbol = 'x';
        }
    }

    private void updateBoard(GdlSentence sentence) {
        // updates a board
        // example sentence: ( true ( cell 3 2 b ) )
        // 'true' is name, 'cell 3 2 b' is body of sentence '( true ( cell 3 2 b ) )'
        // 'cell' is name, '3 2 b' is body of sentence 'cell 3 2 b'

        List<GdlTerm> terms = sentence.getBody().get(0).toSentence().getBody();
        int col = Integer.parseInt(terms.get(0).toString());
        int row = Integer.parseInt(terms.get(1).toString());
        char symbol = terms.get(2).toString().charAt(0);
        board[col - 1][row - 1] = symbol;
    }

    /*
     * Returns char at a position (column, row) on the grid.
     * 'b' = blank, 'x' and 'o'
     */
    public char board(int col, int row) {
        if (board == null) {
            System.out.println("board is null");
            initBoard();
        }
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
            if (state.contains("cell")) updateBoard(sentence);
        }
    }

    /**
     * Marks 'x' or 'o' on the grid and returns Move object required by GGP.
     */
    public Move mark(int col, int row) throws MoveDefinitionException {
        List<Move> moves = theMachine.getLegalMoves(theState, role);

        for (Move move : moves) {
            // format:  ( mark col row )
            // example: ( mark 1 3 )
            if (move.getContents().toSentence().getName().getValue().equals("mark")) {
                int thisCol = Integer.parseInt(move.getContents().toSentence().getBody().get(0).toString());
                int thisRow = Integer.parseInt(move.getContents().toSentence().getBody().get(1).toString());

                if (col == thisCol && row == thisRow) {
                    //System.out.println("col = " + col + ", row = " + row);
                    return move;
                }
            }
        }

        return moves.get(0);
    }

}





