package chess.logic

import chess.logic.Pos.BoardSize

/** A square on the board. col 0 is file 'a', row 0 is rank 8 (the top of the screen). */
case class Pos(col: Int, row: Int) {
  def +(offset: Pos): Pos = Pos(col + offset.col, row + offset.row)

  def isOnBoard: Boolean = col >= 0 && col < BoardSize && row >= 0 && row < BoardSize

  /** Algebraic name, e.g. Pos(4, 6) is "e2". */
  def name: String = s"${('a' + col).toChar}${BoardSize - row}"
}

object Pos {
  val BoardSize = 8

  // scanned from left to right, top to bottom (same order as FEN)
  val all: Seq[Pos] = for (row <- 0 until BoardSize; col <- 0 until BoardSize) yield Pos(col, row)

  /** Parses an algebraic name such as "e4". */
  def fromName(name: String): Option[Pos] = name.toList match {
    case List(file, rank) =>
      Some(Pos(file - 'a', BoardSize - (rank - '0'))).filter(_.isOnBoard)
    case _ => None
  }
}
