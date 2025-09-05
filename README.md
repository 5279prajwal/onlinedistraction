# onlinedistractin
public class GAME {
    public GAME() {
    }

    public static void main(String[] args) {
        new TicTacToe();
    }
}
public class TicTacToe {
    int BoardWidth = 600;
    int BoardHeight = 650;
    JFrame frame = new JFrame();
    JLabel textLabel = new JLabel();
    JPanel textPanel = new JPanel();
    JPanel BoardPanel = new JPanel();
    JButton[][] board = new JButton[3][3];
    String playerX = "X";
    String playerO = "O";
    String currentplayer;
    boolean gameOver;
    int turns;

    TicTacToe() {
        this.currentplayer = this.playerX;
        this.gameOver = false;
        this.turns = 0;
        this.frame.setVisible(true);
        this.frame.setSize(this.BoardWidth, this.BoardHeight);
        this.frame.setLocationRelativeTo((Component)null);
        this.frame.setResizable(false);
        this.frame.setDefaultCloseOperation(3);
        this.frame.setLayout(new BorderLayout());
        this.textLabel.setForeground(Color.white);
        this.textLabel.setBackground(Color.darkGray);
        this.textLabel.setFont(new Font("Arial", 1, 50));
        this.textLabel.setText("TIC-TAC-TOE");
        this.textLabel.setOpaque(true);
        this.textLabel.setHorizontalAlignment(0);
        this.textPanel.setLayout(new BorderLayout());
        this.textPanel.add(this.textLabel);
        this.frame.add(this.textPanel, "North");
        this.BoardPanel.setLayout(new GridLayout(3, 3));
        this.BoardPanel.setBackground(Color.cyan);
        this.frame.add(this.BoardPanel);

        for(int i = 0; i < 3; ++i) {
            for(int j = 0; j < 3; ++j) {
                JButton tile = new JButton();
                this.board[i][j] = tile;
                this.BoardPanel.add(tile);
                tile.setFocusable(false);
                tile.setFont(new Font("Arial", 1, 120));
                tile.setBackground(Color.darkGray);
                tile.setForeground(Color.white);
                tile.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (!TicTacToe.this.gameOver) {
                            JButton tile = (JButton)e.getSource();
                            if (tile.getText() == "") {
                                tile.setText(TicTacToe.this.currentplayer);
                                ++TicTacToe.this.turns;
                                TicTacToe.this.checkWinner();
                                if (!TicTacToe.this.gameOver) {
                                    TicTacToe.this.currentplayer = TicTacToe.this.currentplayer == TicTacToe.this.playerX ? TicTacToe.this.playerO : TicTacToe.this.playerX;
                                    TicTacToe.this.textLabel.setText(TicTacToe.this.currentplayer + "'s turn");
                                }
                            }

                        }
                    }
                });
            }
        }

    }

    void setWinner(JButton tile) {
        tile.setForeground(Color.green);
        tile.setBackground(Color.gray);
        this.textLabel.setText(this.currentplayer + " is Winner");
    }

    void setTie(JButton tile) {
        tile.setForeground(Color.orange);
        tile.setBackground(Color.gray);
        this.textLabel.setText("Tie");
    }

    void checkWinner() {
        for(int i = 0; i < 3; ++i) {
            if (this.board[i][0].getText() != "" && this.board[i][0].getText() == this.board[i][1].getText() && this.board[i][1].getText() == this.board[i][2].getText()) {
                for(int j = 0; j < 3; ++j) {
                    this.setWinner(this.board[i][j]);
                }

                this.gameOver = true;
            }
        }

        for(int i = 0; i < 3; ++i) {
            if (this.board[0][i].getText() != "" && this.board[0][i].getText() == this.board[1][i].getText() && this.board[1][i].getText() == this.board[2][i].getText()) {
                for(int j = 0; j < 3; ++j) {
                    this.setWinner(this.board[j][i]);
                }

                this.gameOver = true;
                return;
            }
        }

        if (this.board[0][0].getText() == this.board[1][1].getText() && this.board[1][1].getText() == this.board[2][2].getText() && this.board[0][0].getText() != "") {
            for(int i = 0; i < 3; ++i) {
                this.setWinner(this.board[i][i]);
            }

            this.gameOver = true;
        } else if (this.board[0][2].getText() == this.board[1][1].getText() && this.board[1][1].getText() == this.board[2][0].getText() && this.board[0][2].getText() != "") {
            this.setWinner(this.board[0][2]);
            this.setWinner(this.board[1][1]);
            this.setWinner(this.board[2][0]);
            this.gameOver = true;
        } else if (this.turns == 9) {
            for(int i = 0; i < 3; ++i) {
                for(int j = 0; j < 3; ++j) {
                    this.setTie(this.board[i][j]);
                }
            }

            this.gameOver = true;
        }
    }
}
