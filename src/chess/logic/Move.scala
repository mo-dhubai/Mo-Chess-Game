package chess.logic

/** A move in "UCI" notation, e.g. e2e4 or e7e8q (promotion). */
case class Move(from: Pos, to: Pos, promotion: Option[PieceType] = None) {
  def uci: String = from.name + to.name + promotion.map(_.letter.toLower).getOrElse("")
}
