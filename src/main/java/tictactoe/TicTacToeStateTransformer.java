package tictactoe;

import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

import java.util.List;

public class TicTacToeStateTransformer  {
    private StateMachine stateMachine;
    private MachineState state;
    private Role role;
    private char[][] board = null;

    public TicTacToeStateTransformer(StateMachine stateMachine, Role role) {
        this.stateMachine = stateMachine;
        this.role = role;
        initBoard();
    }

    private void initBoard() {
        int NUM_ROW = 3;
        int NUM_COL = 3;
        board = new char[NUM_COL][NUM_ROW];
        for (int i = 0; i < NUM_COL; i++)
            for (int j = 0; j < NUM_ROW; j++)
                board[i][j] = 'b';
    }

    public char getMyRole() {
        if (role.toString() == "xplayer")
            return 'x';
        return 'o';
    }

    public char[][] getBoard() {
        return board;
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

    public void updateGameState(MachineState currentState) {
        this.state = currentState;
        String state;
        for (GdlSentence sentence : currentState.getContents()) {
            state = sentence.toString();
            if (state.contains("cell")) updateBoard(sentence);
        }
    }

    /*
     * Transforms TicTacToeMove to GGP Move
     */
    public Move createMove(TicTacToeMove ticTacToeMove) throws MoveDefinitionException {
        List<Move> moves = this.stateMachine.getLegalMoves(this.state, role);

        for (Move move : moves) {
            // format:  ( mark col row )
            // example: ( mark 1 3 )
            if (move.getContents().toSentence().getName().getValue().equals("mark")) {
                int thisCol = Integer.parseInt(move.getContents().toSentence().getBody().get(0).toString());
                int thisRow = Integer.parseInt(move.getContents().toSentence().getBody().get(1).toString());

                if (ticTacToeMove.col + 1 == thisCol && ticTacToeMove.row + 1 == thisRow) {
                    return move;
                }
            }
        }

        return moves.get(0);
    }

}





