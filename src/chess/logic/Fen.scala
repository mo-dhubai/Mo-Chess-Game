package chess.logic

/** Converts a state to FEN, the standard text format for a chess position.
 *  The AI models receive the position in this format. */
object Fen {

  def of(state: GameState): String =
    s"${positionKey(state)} ${state.halfMoveClock} ${fullMoveNumber(state)}"

  /** The FEN without move counters: equal keys mean the same position (for repetition). */
  def positionKey(state: GameState): String =
    Seq(placement(state.board), turnLetter(state.turn), castling(state.castlingRooks),
      state.enPassant.map(_.name).getOrElse("-")).mkString(" ")

  private def placement(board: Board): String =
    Pos.all.grouped(Pos.BoardSize).map(rowToFen(board, _)).mkString("/")

  // a row of FEN: pieces are letters, runs of empty squares are counted, e.g. "rnb1kbnr" or "4P3"
  private def rowToFen(board: Board, row: Seq[Pos]): String = {
    val (text, emptyRun) = row.foldLeft(("", 0)) { case ((acc, empties), p) =>
      board(p) match {
        case Some(piece) => (acc + emptyCount(empties) + piece.fenLetter, 0)
        case None        => (acc, empties + 1)
      }
    }
    text + emptyCount(emptyRun)
  }

  private def emptyCount(n: Int): String = if (n == 0) "" else n.toString

  private def turnLetter(side: Side): String = if (side == White) "w" else "b"

  private def castling(rooks: Set[Pos]): String = {
    val rights = Seq(Pos(7, 7) -> "K", Pos(0, 7) -> "Q", Pos(7, 0) -> "k", Pos(0, 0) -> "q")
      .collect { case (rook, letter) if rooks.contains(rook) => letter }
    if (rights.isEmpty) "-" else rights.mkString
  }

  private def fullMoveNumber(state: GameState): Int = state.moveLog.length / 2 + 1
}
