package chess

import chess.ai.{AiModel, AiMoveChooser}
import chess.logic._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class AiMoveChooserTests extends FunSuite {

  /** A fake model that gives the scripted replies in order (no network needed). */
  class ScriptedModel(replies: String*) extends AiModel {
    val name = "Fake"
    val apiKeyEnvVar = "FAKE_KEY"
    val modelEnvVar = "FAKE_MODEL"
    val defaultModelId = "fake"
    private var remaining = replies.toList
    var prompts: List[String] = Nil

    def ask(prompt: String): String = {
      prompts = prompts :+ prompt
      val reply = remaining.head
      remaining = remaining.tail
      reply
    }
  }

  def choose(model: AiModel, state: GameState = GameState.initial) =
    Await.result(AiMoveChooser.chooseMove(model, state), 5.seconds)

  test("reads a move out of a chatty reply") {
    val decision = choose(new ScriptedModel("I think the best move is **e2e4**."))
    assert(decision.move.uci == "e2e4")
    assert(decision.notice.isEmpty)
  }

  test("asks again after an illegal move and tells the model why") {
    val model = new ScriptedModel("e2e5", "g1f3")
    assert(choose(model).move.uci == "g1f3")
    assert(model.prompts(1).contains("was not one of the legal moves"))
  }

  test("plays a random legal move after too many illegal answers") {
    val decision = choose(new ScriptedModel(Seq.fill(AiMoveChooser.MaxAttempts)("resign"): _*))
    assert(Rules.legalMoves(GameState.initial).contains(decision.move))
    assert(decision.notice.exists(_.contains("random move")))
  }

  test("plays a random legal move when the API fails") {
    val failing = new ScriptedModel() { override def ask(prompt: String): String = throw new RuntimeException("HTTP 401") }
    val decision = choose(failing)
    assert(decision.notice.exists(_.contains("HTTP 401")))
  }

  test("accepts a promotion written without the piece letter") {
    val board = Board(Map(Pos.fromName("a7").get -> Piece(White, Pawn),
      Pos.fromName("h7").get -> Piece(Black, King), Pos.fromName("a1").get -> Piece(White, King)))
    val state = GameState.initial.copy(board = board, castlingRooks = Set.empty)
    assert(choose(new ScriptedModel("a7a8"), state).move == Move(Pos.fromName("a7").get, Pos.fromName("a8").get, Some(Queen)))
  }
}
