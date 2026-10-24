package chess

import chess.logic._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RulesTests extends FunSuite {

  /** Builds a state from FEN (test helper only; the game itself never needs to read FEN). */
  def fromFen(fen: String): GameState = {
    val Array(placement, turn, castling, enPassant) = fen.split(" ").take(4)
    val squares = for {
      (rowText, row) <- placement.split("/").toSeq.zipWithIndex
      (letter, col)  <- rowText.flatMap(c => if (c.isDigit) Seq.fill(c - '0')('.') else Seq(c)).zipWithIndex
      kind           <- PieceType.fromLetter(letter)
    } yield Pos(col, row) -> Piece(if (letter.isUpper) White else Black, kind)
    val rooks = Map('K' -> Pos(7, 7), 'Q' -> Pos(0, 7), 'k' -> Pos(7, 0), 'q' -> Pos(0, 0))
    val state = GameState(Board(squares.toMap), if (turn == "w") White else Black,
      castling.flatMap(rooks.get).toSet, Pos.fromName(enPassant), 0, Vector.empty, Nil)
    state.copy(positions = List(Fen.positionKey(state)))
  }

  /** Counts all move sequences of the given length: the standard way to test a move generator. */
  def perft(state: GameState, depth: Int): Long =
    if (depth == 0) 1
    else Rules.legalMoves(state).map(move => perft(Rules.applyMove(state, move), depth - 1)).sum

  def play(state: GameState, ucis: String*): GameState =
    ucis.foldLeft(state)((s, uci) => Rules.applyMove(s, Rules.legalMoves(s).find(_.uci == uci).get))

  test("starting position perft") {
    assert(perft(GameState.initial, 1) == 20)
    assert(perft(GameState.initial, 2) == 400)
    assert(perft(GameState.initial, 3) == 8902)
  }

  test("kiwipete perft (castling, en passant, pins)") {
    val kiwipete = fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -")
    assert(perft(kiwipete, 1) == 48)
    assert(perft(kiwipete, 2) == 2039)
    assert(perft(kiwipete, 3) == 97862)
  }

  test("endgame perft (en passant and checks)") {
    val position3 = fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -")
    assert(perft(position3, 3) == 2812)
    assert(perft(position3, 4) == 43238)
  }

  test("FEN of the starting position") {
    assert(Fen.of(GameState.initial) == "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
  }

  test("fool's mate is checkmate for Black") {
    val state = play(GameState.initial, "f2f3", "e7e5", "g2g4", "d8h4")
    assert(Rules.status(state) == Checkmate(Black))
  }

  test("stalemate is a draw") {
    assert(Rules.status(fromFen("7k/5Q2/6K1/8/8/8/8/8 b - -")) == Draw("stalemate"))
  }

  test("king versus king is a draw") {
    assert(Rules.status(fromFen("7k/8/6K1/8/8/8/8/8 w - -")) == Draw("insufficient material"))
  }

  test("threefold repetition is a draw") {
    val knightsBackAndForth = Seq("g1f3", "g8f6", "f3g1", "f6g8")
    val state = play(GameState.initial, knightsBackAndForth ++ knightsBackAndForth: _*)
    assert(Rules.status(state) == Draw("threefold repetition"))
  }

  test("pawn promotes to a queen") {
    val state = play(fromFen("8/P6k/8/8/8/8/8/K7 w - -"), "a7a8q")
    assert(state.board(Pos.fromName("a8").get).contains(Piece(White, Queen)))
  }

  test("castling moves the rook too") {
    val state = play(fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq -"), "e1g1")
    assert(state.board(Pos.fromName("f1").get).contains(Piece(White, Rook)))
    assert(state.board(Pos.fromName("g1").get).contains(Piece(White, King)))
    assert(!state.castlingRooks.contains(Pos.fromName("a1").get))
  }
}
