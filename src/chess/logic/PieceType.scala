package chess.logic

import scala.annotation.tailrec

/** Every piece type knows which squares it attacks (dynamic dispatch instead of a big match).
 *  Special moves (pawn pushes, castling, en passant) live in [[Rules]]. */
sealed abstract class PieceType(val letter: Char, val symbol: String) {
  def attacks(from: Pos, side: Side, board: Board): Seq[Pos]
}

/** Pieces that slide in a direction until something blocks them. */
sealed abstract class Slider(letter: Char, symbol: String, directions: Seq[Pos])
  extends PieceType(letter, symbol) {

  final def attacks(from: Pos, side: Side, board: Board): Seq[Pos] =
    directions.flatMap(direction => ray(from, direction, board))

  // all squares along one direction, up to and including the first occupied square
  private def ray(from: Pos, direction: Pos, board: Board): List[Pos] = {
    @tailrec
    def walk(current: Pos, acc: List[Pos]): List[Pos] = {
      val next = current + direction
      if (!next.isOnBoard) acc
      else if (board.isEmpty(next)) walk(next, next :: acc)
      else next :: acc
    }
    walk(from, Nil)
  }
}

/** Pieces that jump to a fixed set of offsets. */
sealed abstract class Leaper(letter: Char, symbol: String, offsets: Seq[Pos])
  extends PieceType(letter, symbol) {

  final def attacks(from: Pos, side: Side, board: Board): Seq[Pos] =
    offsets.map(from + _).filter(_.isOnBoard)
}

object Directions {
  val Straight: Seq[Pos] = Seq(Pos(1, 0), Pos(-1, 0), Pos(0, 1), Pos(0, -1))
  val Diagonal: Seq[Pos] = Seq(Pos(1, 1), Pos(1, -1), Pos(-1, 1), Pos(-1, -1))
  val All: Seq[Pos] = Straight ++ Diagonal
  val KnightJumps: Seq[Pos] =
    for (dx <- Seq(-2, -1, 1, 2); dy <- Seq(-2, -1, 1, 2) if dx.abs != dy.abs) yield Pos(dx, dy)
}

case object King   extends Leaper('K', "♚", Directions.All)
case object Knight extends Leaper('N', "♞", Directions.KnightJumps)
case object Queen  extends Slider('Q', "♛", Directions.All)
case object Rook   extends Slider('R', "♜", Directions.Straight)
case object Bishop extends Slider('B', "♝", Directions.Diagonal)

case object Pawn extends PieceType('P', "♟") {
  // a pawn only attacks diagonally forward
  def attacks(from: Pos, side: Side, board: Board): Seq[Pos] =
    Seq(-1, 1).map(dx => from + Pos(dx, side.forward)).filter(_.isOnBoard)
}

object PieceType {
  val all: Seq[PieceType] = Seq(King, Queen, Rook, Bishop, Knight, Pawn)

  def fromLetter(letter: Char): Option[PieceType] = all.find(_.letter == letter.toUpper)
}
