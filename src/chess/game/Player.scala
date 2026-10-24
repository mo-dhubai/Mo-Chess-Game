package chess.game

import chess.ai.AiModel

sealed trait Player {
  def name: String
}

case class HumanPlayer(name: String) extends Player

case class AiPlayer(model: AiModel) extends Player {
  def name: String = model.name
}
