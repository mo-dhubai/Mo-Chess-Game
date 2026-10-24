package chess.game

import chess.ai.{AiDecision, AiModel, AiMoveChooser}
import chess.game.ChessGame._
import chess.logic._
import engine.GameBase
import engine.graphics.{Color, Point, Rectangle}
import processing.core.{PApplet, PConstants, PFont}
import processing.event.{KeyEvent, MouseEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class ChessGame extends GameBase {

  var screen: Screen = WelcomeScreen
  var typedName: String = ""
  var playerName: String = ""

  var logic: ChessLogic = new ChessLogic
  var whitePlayer: Player = HumanPlayer("")
  var blackPlayer: Player = HumanPlayer("")
  var pendingAiMove: Option[Future[AiDecision]] = None
  var notice: Option[String] = None  // extra info for the user, e.g. when an AI fell back to a random move

  // filled in setup(); None means no font with chess symbols was found, so letters are drawn instead
  var symbolFont: Option[PFont] = None

  val screenArea: Rectangle = Rectangle(Point(0, 0), WindowWidth.toFloat, WindowHeight.toFloat)
  val boardArea: Rectangle = Rectangle(Point(0, 0), BoardPixels, BoardPixels)
  val statusArea: Rectangle = Rectangle(boardArea.leftDown, BoardPixels, StatusBarHeight)

  // ===== Processing callbacks =====

  override def settings(): Unit = {
    pixelDensity(displayDensity())
    size(WindowWidth, WindowHeight)
  }

  override def setup(): Unit = {
    surface.setTitle("Mo Chess")
    symbolFont = FontCandidates.find(PFont.list().contains).map(createFont(_, PieceSize))
    symbolFont.foreach(textFont)
    text("", 0, 0)  // fonts load lazily; doing it now avoids lag on the first real text
  }

  override def draw(): Unit = {
    setBackground(BackgroundColor)
    noStroke()
    screen match {
      case WelcomeScreen    => drawTitle("Welcome to Mo Chess")
      case NameScreen       => drawNameInput("Enter your name")
      case ModeScreen       => drawTitle(s"Hi $playerName! Who do you want to play?")
      case FriendNameScreen => drawNameInput("Enter your friend's name (plays Black)")
      case AiChoiceScreen   => drawTitle("Choose your AI opponent")
      case GameScreen       => updateAiTurn(); drawGameScreen()
    }
    buttons.foreach(drawButton)
  }

  override def mousePressed(event: MouseEvent): Unit = {
    val click = Point(event.getX.toFloat, event.getY.toFloat)
    buttons.find(button => button.enabled && button.area.contains(click)) match {
      case Some(button)                  => button.onClick()
      case None if screen == GameScreen => clickBoard(click)
      case None                          => ()
    }
  }

  override def keyPressed(event: KeyEvent): Unit = screen match {
    case NameScreen | FriendNameScreen => editName(event.getKey)
    case _                             => ()
  }

  // ===== Menus =====

  /** The buttons of the current screen. Each button carries its own action as a function value. */
  def buttons: Seq[Button] = screen match {
    case WelcomeScreen =>
      buttonColumn(Seq(("Continue", () => goToNameInput(NameScreen), true)))
    case NameScreen | FriendNameScreen =>
      buttonColumn(Seq(("Continue", () => submitName(), typedName.trim.nonEmpty)))
    case ModeScreen =>
      buttonColumn(Seq(
        ("Play with a friend", () => goToNameInput(FriendNameScreen), true),
        ("Play against AI", () => screen = AiChoiceScreen, true)))
    case AiChoiceScreen =>
      val modelButtons = AiModel.all.map(model => (aiButtonLabel(model), () => startGame(AiPlayer(model)), model.isConfigured))
      buttonColumn(modelButtons :+ (("Back", () => screen = ModeScreen, true)))
    case GameScreen if logic.isGameOver =>
      buttonColumn(Seq(
        ("Play again", () => startGame(blackPlayer), true),
        ("Main menu", () => screen = ModeScreen, true)), firstTop = GameOverButtonsTop)
    case GameScreen => Nil
  }

  private def aiButtonLabel(model: AiModel): String =
    if (model.isConfigured) model.name else s"${model.name} (set ${model.apiKeyEnvVar})"

  /** Stacks buttons vertically, centred horizontally. */
  private def buttonColumn(specs: Seq[(String, () => Unit, Boolean)], firstTop: Float = FirstButtonTop): Seq[Button] =
    specs.zipWithIndex.map { case ((label, action, enabled), index) =>
      val leftUp = Point(screenArea.centerX - ButtonWidth / 2, firstTop + index * (ButtonHeight + ButtonGap))
      Button(label, Rectangle(leftUp, ButtonWidth, ButtonHeight), action, enabled)
    }

  private def goToNameInput(nameScreen: Screen): Unit = {
    typedName = ""
    screen = nameScreen
  }

  private def editName(key: Char): Unit = key match {
    case '\n' | '\r' => submitName()
    case '\b'        => typedName = typedName.dropRight(1)
    case c if (c.isLetterOrDigit || c == ' ') && typedName.length < MaxNameLength => typedName += c
    case _ => ()
  }

  private def submitName(): Unit = {
    val name = typedName.trim
    if (name.nonEmpty) screen match {
      case NameScreen =>
        playerName = name
        screen = ModeScreen
      case FriendNameScreen => startGame(HumanPlayer(name))
      case _                => ()
    }
  }

  // ===== Playing =====

  /** The user always plays White; the opponent (friend or AI) plays Black. */
  private def startGame(opponent: Player): Unit = {
    whitePlayer = HumanPlayer(playerName)
    blackPlayer = opponent
    logic = new ChessLogic
    pendingAiMove = None  // forget an answer still on its way from a previous game
    notice = None
    screen = GameScreen
  }

  def playerFor(side: Side): Player = if (side == White) whitePlayer else blackPlayer

  def currentPlayer: Player = playerFor(logic.turn)

  private def clickBoard(click: Point): Unit = currentPlayer match {
    case HumanPlayer(_) if !logic.isGameOver => squareAt(click).foreach(logic.clickSquare)
    case _                                   => ()
  }

  private def squareAt(click: Point): Option[Pos] =
    if (boardArea.contains(click)) Some(Pos((click.x / CellSize).toInt, (click.y / CellSize).toInt))
    else None

  /** When it's the AI's turn: start asking it, or play its move once the answer has arrived. */
  private def updateAiTurn(): Unit = currentPlayer match {
    case AiPlayer(model) if !logic.isGameOver =>
      pendingAiMove match {
        case None         => pendingAiMove = Some(AiMoveChooser.chooseMove(model, logic.currState))
        case Some(future) => future.value.foreach(playAiAnswer)
      }
    case _ => ()
  }

  private def playAiAnswer(answer: Try[AiDecision]): Unit = {
    answer match {
      case Success(decision) =>
        logic.makeMove(decision.move)
        notice = decision.notice
      case Failure(error) =>
        notice = Some(s"AI failed: ${error.getMessage}")
    }
    pendingAiMove = None
  }

  // ===== Drawing =====

  private def drawTitle(title: String): Unit = {
    setFillColor(Color.White)
    drawTextCentered(title, TitleSize, Point(screenArea.centerX, TitleY))
  }

  private def drawNameInput(title: String): Unit = {
    drawTitle(title)
    val box = Rectangle(Point(screenArea.centerX - ButtonWidth / 2, NameBoxTop), ButtonWidth, ButtonHeight)
    setFillColor(Color.White)
    drawRectangle(box)
    val cursor = if (frameCount / CursorBlinkFrames % 2 == 0) "_" else " "
    setFillColor(Color.Black)
    drawLabel(typedName + cursor, TextSize, box.center, withShadow = false)
  }

  private def drawButton(button: Button): Unit = {
    setFillColor(if (button.enabled) ButtonColor else DisabledButtonColor)
    drawRectangle(button.area)
    setFillColor(Color.White)
    drawLabel(button.label, TextSize, button.area.center)
  }

  /** Draws text centred exactly on `center` (GameBase.drawTextCentered draws it a bit higher). */
  private def drawLabel(label: String, size: Float, center: Point, withShadow: Boolean = true): Unit = {
    textAlign(PConstants.CENTER, PConstants.CENTER)
    textSize(size)
    drawText(label, center, withShadow)
  }

  private def drawGameScreen(): Unit = {
    drawBoard()
    drawStatusBar()
    if (logic.isGameOver) drawGameOverOverlay()
  }

  private def drawBoard(): Unit = {
    // computed once per frame, then used by the local functions below
    val targets = logic.selectedTargets
    val lastMoveSquares = logic.lastMove.toSeq.flatMap(move => Seq(move.from, move.to))
    val kingInCheck = if (logic.isInCheck) logic.currState.board.kingPos(logic.turn) else None

    def squareColor(p: Pos): Color = {
      val base = if ((p.col + p.row) % 2 == 0) LightSquareColor else DarkSquareColor
      if (kingInCheck.contains(p)) CheckColor
      else if (logic.selected.contains(p)) SelectedColor
      else if (lastMoveSquares.contains(p)) base.interpolate(LastMoveTint, SelectedColor)
      else base
    }

    for (p <- Pos.all) {
      val area = squareArea(p)
      setFillColor(squareColor(p))
      drawRectangle(area)
      logic.currState.board(p).foreach(drawPiece(_, area))
      if (targets.contains(p)) {
        setFillColor(MoveHintColor)
        drawEllipse(area.grow(MoveHintSize))
      }
    }
  }

  private def squareArea(p: Pos): Rectangle =
    Rectangle(Point(p.col * CellSize, p.row * CellSize), CellSize, CellSize)

  private def drawPiece(piece: Piece, area: Rectangle): Unit = {
    val (fillColor, outlineColor) =
      if (piece.side == White) (Color.White, Color.Black) else (Color.Black, Color.White)
    val label = if (symbolFont.isDefined) piece.kind.symbol else piece.kind.letter.toString
    val position = Point(area.centerX, area.centerY - PieceSize * PieceVerticalShift)

    textAlign(PConstants.CENTER, PConstants.CENTER)
    textSize(PieceSize)
    drawTextShadow(label, position, outlineColor, PieceOutline)
    setFillColor(fillColor)
    drawText(label, position, withShadow = false)
  }

  private def drawStatusBar(): Unit = {
    val player = currentPlayer
    val checkText = if (logic.isInCheck && !logic.isGameOver) "  Check!" else ""
    val turnText = player match {
      case _ if logic.isGameOver                => "Game over"
      case AiPlayer(_) if pendingAiMove.isDefined => s"${player.name} is thinking..."
      case _                                    => s"${player.name}'s turn (${logic.turn.name})"
    }
    val vsText = s"${whitePlayer.name} (White) vs ${blackPlayer.name} (Black)"

    setFillColor(Color.White)
    drawTextCentered(turnText + checkText, TextSize, Point(statusArea.centerX, statusArea.top + StatusLineHeight))
    setFillColor(NoticeColor)
    drawTextCentered(notice.getOrElse(vsText), SmallTextSize, Point(statusArea.centerX, statusArea.top + 2 * StatusLineHeight))
  }

  private def drawGameOverOverlay(): Unit = {
    val message = logic.status match {
      case Checkmate(winner) => s"Game over: Player ${playerFor(winner).name} wins"
      case Draw(reason)      => s"Game over: Draw ($reason)"
      case InProgress        => ""
    }
    setFillColor(OverlayColor)
    drawRectangle(boardArea)
    setFillColor(Color.White)
    drawTextCentered(message, TitleSize, Point(boardArea.centerX, GameOverTextY))
  }
}

object ChessGame {

  val CellSize: Float = 70
  val BoardPixels: Float = CellSize * Pos.BoardSize
  val StatusBarHeight: Float = 70
  val StatusLineHeight: Float = StatusBarHeight / 3
  val WindowWidth: Int = BoardPixels.toInt
  val WindowHeight: Int = (BoardPixels + StatusBarHeight).toInt

  val TitleSize: Float = 26
  val TextSize: Float = 20
  val SmallTextSize: Float = 14
  val PieceSize: Float = 52
  val PieceVerticalShift: Float = 0.1f  // glyphs sit a bit low when centred, so lift them slightly
  val PieceOutline: Float = 1.5f
  val MoveHintSize: Float = 0.3f        // hint dot size, as a fraction of a square
  val LastMoveTint: Float = 0.35f

  val TitleY: Float = 150
  val NameBoxTop: Float = 220
  val FirstButtonTop: Float = 300
  val GameOverTextY: Float = 220
  val GameOverButtonsTop: Float = 270
  val ButtonWidth: Float = 420
  val ButtonHeight: Float = 50
  val ButtonGap: Float = 15

  val MaxNameLength: Int = 16
  val CursorBlinkFrames: Int = 30

  // fonts that contain the chess symbols (Linux, Windows, macOS)
  val FontCandidates: Seq[String] = Seq("DejaVu Sans", "Segoe UI Symbol", "Apple Symbols", "FreeSerif")

  val BackgroundColor: Color = Color(40, 40, 40)
  val LightSquareColor: Color = Color(240, 217, 181)
  val DarkSquareColor: Color = Color(181, 136, 99)
  val SelectedColor: Color = Color(246, 246, 105)
  val CheckColor: Color = Color(230, 70, 70)
  val MoveHintColor: Color = Color(40, 120, 40, 160)
  val ButtonColor: Color = Color(70, 110, 160)
  val DisabledButtonColor: Color = Color.Gray
  val NoticeColor: Color = Color(200, 200, 200)
  val OverlayColor: Color = Color(0, 0, 0, 170)

  def main(args: Array[String]): Unit = {
    PApplet.main("chess.game.ChessGame")
  }
}
