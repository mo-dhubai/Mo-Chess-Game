package chess.logic

/** Holds the current game and the square the user has selected.
 *  Like TetrisLogic, the only mutable parts are `var`s that get replaced by new immutable values. */
class ChessLogic {

  var currState: GameState = GameState.initial
  var selected: Option[Pos] = None
  // cached, because computing the status needs every legal move and draw() runs many times a second
  var status: GameStatus = InProgress

  def turn: Side = currState.turn

  def isGameOver: Boolean = status != InProgress

  def lastMove: Option[Move] = currState.moveLog.lastOption

  def isInCheck: Boolean = Rules.isInCheck(currState.board, turn)

  /** Squares the selected piece can legally move to (shown as hints on the board). */
  def selectedTargets: Seq[Pos] =
    selected.toSeq.flatMap(from => Rules.legalMovesFrom(currState, from).map(_.to))

  /** A click either selects one of our pieces, or moves the selected piece to the clicked square. */
  def clickSquare(p: Pos): Unit = {
    val moveToClicked = selected.flatMap(from => Rules.legalMovesFrom(currState, from).find(_.to == p))
    moveToClicked match {
      case Some(move) => makeMove(move)
      case None       => selected = Some(p).filter(isOwnPiece)
    }
  }

  def makeMove(move: Move): Unit =
    if (!isGameOver && Rules.legalMoves(currState).contains(move)) {
      currState = Rules.applyMove(currState, move)
      status = Rules.status(currState)
      selected = None
    }

  private def isOwnPiece(p: Pos): Boolean = currState.board(p).exists(_.side == turn)
}
