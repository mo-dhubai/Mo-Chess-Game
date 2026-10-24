package chess.logic

/** Everything needed to know the position and continue the game.
 *
 * @param castlingRooks  start squares of rooks that may still castle (neither rook nor king has moved)
 * @param enPassant      the square a pawn can capture onto en passant, if any
 * @param halfMoveClock  moves since the last capture or pawn move (for the 50-move rule)
 * @param positions      position keys seen so far (for threefold repetition)
 */
case class GameState(board: Board,
                     turn: Side,
                     castlingRooks: Set[Pos],
                     enPassant: Option[Pos],
                     halfMoveClock: Int,
                     moveLog: Vector[Move],
                     positions: List[String])

object GameState {
  val initial: GameState = {
    val start = GameState(
      board = Board.initial,
      turn = White,
      castlingRooks = Set(Pos(0, 0), Pos(7, 0), Pos(0, 7), Pos(7, 7)),
      enPassant = None,
      halfMoveClock = 0,
      moveLog = Vector.empty,
      positions = Nil)
    start.copy(positions = List(Fen.positionKey(start)))
  }
}
