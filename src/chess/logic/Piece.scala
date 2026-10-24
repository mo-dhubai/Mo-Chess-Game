package chess.logic

case class Piece(side: Side, kind: PieceType) {
  /** FEN letter: upper case for White, lower case for Black. */
  def fenLetter: Char = if (side == White) kind.letter else kind.letter.toLower
}
