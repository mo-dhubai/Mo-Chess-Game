package chess.logic

/** An immutable board: every "change" returns a new Board. */
case class Board(squares: Map[Pos, Piece]) {
  def apply(p: Pos): Option[Piece] = squares.get(p)

  def isEmpty(p: Pos): Boolean = !squares.contains(p)

  def place(p: Pos, piece: Piece): Board = Board(squares + (p -> piece))

  def remove(p: Pos): Board = Board(squares - p)

  def move(from: Pos, to: Pos): Board =
    squares.get(from).fold(this)(piece => remove(from).place(to, piece))

  def piecesOf(side: Side): Seq[(Pos, Piece)] = squares.toSeq.filter { case (_, piece) => piece.side == side }

  def kingPos(side: Side): Option[Pos] =
    squares.collectFirst { case (p, Piece(`side`, King)) => p }
}

object Board {
  private val BackRank: Seq[PieceType] = Seq(Rook, Knight, Bishop, Queen, King, Bishop, Knight, Rook)

  val initial: Board = {
    def rowsFor(side: Side): Seq[(Pos, Piece)] = {
      val backRank = BackRank.zipWithIndex.map { case (kind, col) => Pos(col, side.homeRow) -> Piece(side, kind) }
      val pawns = (0 until Pos.BoardSize).map(col => Pos(col, side.pawnStartRow) -> Piece(side, Pawn))
      backRank ++ pawns
    }
    Board((rowsFor(White) ++ rowsFor(Black)).toMap)
  }
}
