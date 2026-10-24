package chess.logic

/** The rules of chess as pure functions: a state goes in, an answer (or a new state) comes out. */
object Rules {

  val FiftyMoveLimit: Int = 100   // 50 moves by each player = 100 half moves
  val RepetitionLimit: Int = 3
  private val KingStartCol = 4

  // ===== Legal moves =====

  def legalMoves(state: GameState): Seq[Move] =
    candidateMoves(state).filterNot(move => leavesOwnKingInCheck(state, move))

  def legalMovesFrom(state: GameState, from: Pos): Seq[Move] =
    legalMoves(state).filter(_.from == from)

  def isInCheck(board: Board, side: Side): Boolean =
    board.kingPos(side).exists(king => isAttacked(board, king, side.opponent))

  def isAttacked(board: Board, target: Pos, by: Side): Boolean =
    board.piecesOf(by).exists { case (p, piece) => piece.kind.attacks(p, by, board).contains(target) }

  private def leavesOwnKingInCheck(state: GameState, move: Move): Boolean =
    isInCheck(applyMove(state, move).board, state.turn)

  /** Moves that follow how each piece moves, but may still leave the own king in check. */
  private def candidateMoves(state: GameState): Seq[Move] = {
    val side = state.turn
    val board = state.board

    def isOwnPiece(p: Pos): Boolean = board(p).exists(_.side == side)

    val normalMoves = board.piecesOf(side).flatMap {
      case (from, Piece(_, Pawn)) => pawnMoves(state, from)
      case (from, piece) =>
        piece.kind.attacks(from, side, board).filterNot(isOwnPiece).map(to => Move(from, to))
    }
    normalMoves ++ castlingMoves(state)
  }

  private def pawnMoves(state: GameState, from: Pos): Seq[Move] = {
    val side = state.turn
    val board = state.board
    val oneStep = from + Pos(0, side.forward)
    val twoSteps = oneStep + Pos(0, side.forward)

    val pushes =
      if (!oneStep.isOnBoard || !board.isEmpty(oneStep)) Nil
      else if (from.row == side.pawnStartRow && board.isEmpty(twoSteps)) List(oneStep, twoSteps)
      else List(oneStep)

    def canCaptureOn(p: Pos): Boolean =
      board(p).exists(_.side == side.opponent) || state.enPassant.contains(p)

    val captures = Pawn.attacks(from, side, board).filter(canCaptureOn)

    // reaching the last rank always promotes to a queen
    def toMove(to: Pos): Move =
      if (to.row == side.opponent.homeRow) Move(from, to, Some(Queen)) else Move(from, to)

    (pushes ++ captures).map(toMove)
  }

  private def castlingMoves(state: GameState): Seq[Move] = {
    val side = state.turn
    val board = state.board
    val row = side.homeRow
    val kingStart = Pos(KingStartCol, row)

    def canCastle(rookCol: Int, kingTargetCol: Int): Boolean = {
      val squaresBetween = (math.min(rookCol, KingStartCol) + 1 until math.max(rookCol, KingStartCol)).map(Pos(_, row))
      val kingPath = Seq(KingStartCol, (KingStartCol + kingTargetCol) / 2, kingTargetCol).map(Pos(_, row))

      state.castlingRooks.contains(Pos(rookCol, row)) &&
        board(kingStart).contains(Piece(side, King)) &&
        squaresBetween.forall(board.isEmpty) &&
        kingPath.forall(p => !isAttacked(board, p, side.opponent))  // includes "not castling out of check"
    }

    val kingSide = (7, 6)   // (rook column, king target column)
    val queenSide = (0, 2)
    Seq(kingSide, queenSide).collect {
      case (rookCol, kingTargetCol) if canCastle(rookCol, kingTargetCol) => Move(kingStart, Pos(kingTargetCol, row))
    }
  }

  // ===== Making a move =====

  /** Returns the state after `move`. Assumes the move is legal. */
  def applyMove(state: GameState, move: Move): GameState = {
    val Move(from, to, promotion) = move
    val board = state.board
    val side = state.turn
    val movingKind = board(from).map(_.kind)

    val isPawnMove = movingKind.contains(Pawn)
    val isEnPassant = isPawnMove && state.enPassant.contains(to) && from.col != to.col
    val isCapture = !board.isEmpty(to) || isEnPassant
    val isCastling = movingKind.contains(King) && (to.col - from.col).abs == 2

    // each special rule is a small Board => Board step; we chain the ones that apply
    val movePiece: Board => Board = _.move(from, to)
    val promote: Board => Board = b => promotion.fold(b)(kind => b.place(to, Piece(side, kind)))
    val removeEnPassantPawn: Board => Board = b => if (isEnPassant) b.remove(Pos(to.col, from.row)) else b
    val moveCastlingRook: Board => Board = b =>
      if (!isCastling) b
      else if (to.col > from.col) b.move(Pos(7, from.row), Pos(to.col - 1, from.row))
      else b.move(Pos(0, from.row), Pos(to.col + 1, from.row))

    val newBoard = (movePiece andThen promote andThen removeEnPassantPawn andThen moveCastlingRook)(board)

    val newEnPassant =
      if (isPawnMove && (to.row - from.row).abs == 2) Some(Pos(from.col, (from.row + to.row) / 2))
      else None

    // a king move loses both castling rights; touching a rook corner (moving or capturing) loses that one
    val ownRookCorners = Set(Pos(0, side.homeRow), Pos(7, side.homeRow))
    val lostRights = (if (movingKind.contains(King)) ownRookCorners else Set.empty[Pos]) + from + to
    val newCastlingRooks = state.castlingRooks -- lostRights

    val afterMove = state.copy(
      board = newBoard,
      turn = side.opponent,
      castlingRooks = newCastlingRooks,
      enPassant = newEnPassant,
      halfMoveClock = if (isPawnMove || isCapture) 0 else state.halfMoveClock + 1,
      moveLog = state.moveLog :+ move)
    afterMove.copy(positions = Fen.positionKey(afterMove) :: state.positions)
  }

  // ===== Game result =====

  def status(state: GameState): GameStatus = {
    val noMovesLeft = legalMoves(state).isEmpty
    val inCheck = isInCheck(state.board, state.turn)

    if (noMovesLeft && inCheck) Checkmate(winner = state.turn.opponent)
    else if (noMovesLeft) Draw("stalemate")
    else if (hasInsufficientMaterial(state.board)) Draw("insufficient material")
    else if (state.halfMoveClock >= FiftyMoveLimit) Draw("50-move rule")
    else if (state.positions.count(_ == state.positions.head) >= RepetitionLimit) Draw("threefold repetition")
    else InProgress
  }

  /** King vs king, or king + one bishop/knight vs king: nobody can ever checkmate. */
  private def hasInsufficientMaterial(board: Board): Boolean = {
    val nonKingPieces = board.squares.values.map(_.kind).filter(_ != King).toList
    nonKingPieces match {
      case Nil                         => true
      case List(Bishop) | List(Knight) => true
      case _                           => false
    }
  }
}
