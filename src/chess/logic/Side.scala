package chess.logic

/** The two sides of a chess game. Each side knows its own direction and back rank,
 *  so the rules never need to ask "if (side == White) ... else ...". */
sealed abstract class Side(val name: String) {
  def opponent: Side
  def forward: Int  // row direction this side's pawns move in (row 0 is the top of the board)
  def homeRow: Int  // the back rank where this side's king starts

  def pawnStartRow: Int = homeRow + forward
}

case object White extends Side("White") {
  def opponent: Side = Black
  val forward: Int = -1
  val homeRow: Int = 7
}

case object Black extends Side("Black") {
  def opponent: Side = White
  val forward: Int = 1
  val homeRow: Int = 0
}
