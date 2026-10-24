package chess.ai

import chess.logic.{Fen, GameState, Move, Rules}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Failure, Random, Success, Try}

/** The move an AI decided on, plus an optional message to show the user (e.g. when it fell back). */
case class AiDecision(move: Move, notice: Option[String])

/** Asks an [[AiModel]] for a move and makes sure the answer is a legal move. */
object AiMoveChooser {

  val MaxAttempts: Int = 3
  private val MaxQuotedReplyLength = 60
  private val MaxErrorLength = 120
  private val UciPattern = "[a-h][1-8][a-h][1-8][qrbn]?".r

  /** Runs in the background so the window keeps drawing while the model thinks.
   *  Never fails: if the model can't give a legal move, a random legal move is played instead. */
  def chooseMove(model: AiModel, state: GameState)(implicit ec: ExecutionContext): Future[AiDecision] =
    Future(blocking(askUntilLegal(model, state)))

  private def askUntilLegal(model: AiModel, state: GameState): AiDecision = {
    val legalMoves = Rules.legalMoves(state)

    def fallback(reason: String): AiDecision =
      AiDecision(legalMoves(Random.nextInt(legalMoves.length)), Some(s"$reason, so a random move was played"))

    @tailrec
    def attempt(attemptNr: Int, feedback: String): AiDecision =
      if (attemptNr > MaxAttempts) fallback(s"${model.name} gave no legal move")
      else Try(model.ask(prompt(state, legalMoves, feedback))) match {
        case Failure(error) => fallback(s"${model.name} error: ${String.valueOf(error.getMessage).take(MaxErrorLength)}")
        case Success(reply) => parseMove(reply, legalMoves) match {
          case Some(move) => AiDecision(move, None)
          case None =>
            val quoted = reply.trim.take(MaxQuotedReplyLength)
            attempt(attemptNr + 1, s"""Your previous answer "$quoted" was not one of the legal moves. Try again.""")
        }
      }

    attempt(attemptNr = 1, feedback = "")
  }

  private def prompt(state: GameState, legalMoves: Seq[Move], feedback: String): String = {
    val movesSoFar = if (state.moveLog.isEmpty) "(none)" else state.moveLog.map(_.uci).mkString(" ")
    s"""You are playing a game of chess as ${state.turn.name}. Play the strongest move you can.
       |Current position (FEN): ${Fen.of(state)}
       |Moves played so far (UCI): $movesSoFar
       |Your legal moves (UCI): ${legalMoves.map(_.uci).mkString(" ")}
       |$feedback
       |Reply with exactly one move from the list of legal moves, written exactly as in the list, and nothing else.""".stripMargin
  }

  /** Finds the first move in the reply that is legal. "e7e8" is accepted for the promotion "e7e8q". */
  private def parseMove(reply: String, legalMoves: Seq[Move]): Option[Move] = {
    def matchesLegalMove(uci: String): Option[Move] =
      legalMoves.find(_.uci == uci).orElse(legalMoves.find(_.uci.take(4) == uci.take(4)))

    UciPattern.findAllIn(reply.toLowerCase).flatMap(matchesLegalMove).nextOption()
  }
}
