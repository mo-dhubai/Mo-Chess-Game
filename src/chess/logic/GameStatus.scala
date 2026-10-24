package chess.logic

sealed trait GameStatus
case object InProgress extends GameStatus
case class Checkmate(winner: Side) extends GameStatus
case class Draw(reason: String) extends GameStatus
