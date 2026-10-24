# Mo Chess ♟️

Bonus assignment for Object Oriented Functional Programming (OOFP).
By Mohamed Aldhubai.

For the bonus assignment I made a chess game in Scala. I tried to keep the same structure we used in the Snake and Tetris
assignments (a `logic` package for the rules and a `game` package for drawing with Processing), and I tried to use as many
of the concepts from the course as made sense, without forcing them in.

You can play against a friend on the same computer, or against an AI: ChatGPT, Claude, Gemini or DeepSeek. The AI part
calls the real APIs, so you need an API key for the one you want to play against (more about that below).

Source code: https://github.com/mo-dhubai/Mo-Chess-Game

Demo: https://drive.google.com/file/d/1mEpVDXd6Bkc4tdehfAXiIv1pTijRVZ9w/view?usp=sharing

## How to run it

You need Java (I used Java 23) and that's it, Gradle downloads everything else.

```
./gradlew run      # start the game
./gradlew test     # run my tests
```

Or open the `bonus-assignment` folder in IntelliJ and run `chess.game.ChessGame`, same as with Tetris.

## How to play

1. The game starts with **"Welcome to Mo Chess"**. Click *Continue*.
2. Type your name and press Enter.
3. Choose **Play with a friend** or **Play against AI**.
   - Friend: your friend types their name and plays Black.
   - AI: pick ChatGPT, Claude, Gemini or DeepSeek.
4. You are always White. Click one of your pieces and green dots show where it can go. Click a dot to move.
5. When the game ends you see `Game over: Player <name> wins` (if the AI wins, its name is shown) or a draw message.
   Then you can play again or go back to the menu.

All the normal chess rules are in the game:

- check and checkmate (you can't make a move that leaves your own king in check)
- castling (both sides, and not out of, through or into check)
- en passant
- promotion (to keep it simple, a pawn always becomes a queen)
- draws: stalemate, insufficient material, the 50-move rule and threefold repetition

## Playing against the AI

Every AI button needs its API key set as an environment variable **before** you start the game. If the key is missing, the
button is grey and tells you which variable to set.

| Opponent | API key variable    | Model I use by default |
|----------|---------------------|------------------------|
| ChatGPT  | `OPENAI_API_KEY`    | `gpt-5.5`              |
| Claude   | `ANTHROPIC_API_KEY` | `claude-opus-5-5`      |
| Gemini   | `GEMINI_API_KEY`    | `gemini-3.6-flash`     |
| DeepSeek | `DEEPSEEK_API_KEY`  | `deepseek-v4-flash`    |

For example:

```
export ANTHROPIC_API_KEY=your-key-here
./gradlew run
```

In IntelliJ you can put the key under *Run configuration → Environment variables*. If a model name gets outdated, you
can change it without touching the code with `OPENAI_MODEL`, `CLAUDE_MODEL`, `GEMINI_MODEL` or `DEEPSEEK_MODEL`.

**Warning:** every AI move is a real (paid) API call, and it can take a few seconds. While the AI is thinking, the status
bar says "<model> is thinking...".

### How the AI picks a move

The models are chat models, not chess engines, so they sometimes answer with moves that aren't legal. I solved this like this:

1. I send the model the position (as a FEN string), the moves played so far, and the list of **all legal moves**.
2. I look for the first legal move in its answer (so "I think **e2e4** is best" still works).
3. If there is no legal move in the answer, I ask again and tell it what was wrong. It gets 3 tries.
4. If it still fails, or the internet/API gives an error, the game plays a random legal move and shows a message
   under the board, so the game never gets stuck.

The API call runs in a `Future`, so the window doesn't freeze while waiting.

## Project structure

```
src/
  chess/logic/      the chess rules, no graphics at all
    Side.scala        White and Black
    Pos.scala         a square on the board (e.g. "e4")
    PieceType.scala   King, Queen, Rook, Bishop, Knight, Pawn and how they attack
    Piece.scala       a piece = side + type
    Board.scala       immutable board (Map from square to piece)
    Move.scala        a move like e2e4 or e7e8q
    GameState.scala   everything about the current position
    GameStatus.scala  InProgress, Checkmate or Draw
    Rules.scala       legal moves, making a move, checkmate/draw detection
    Fen.scala         turns the position into a FEN string for the AI
    ChessLogic.scala  the current game + the selected square (like TetrisLogic)
  chess/ai/         talking to the AI models
    AiModel.scala       the AiModel trait + ChatGPT, DeepSeek and Gemini
    ClaudeModel.scala   Claude, using the official Anthropic Java SDK
    HttpJson.scala      small helper to send JSON over HTTP
    AiMoveChooser.scala asks the model and makes sure the move is legal
  chess/game/       the window (Processing)
    ChessGame.scala   menus, drawing the board, mouse and keyboard
    Screen.scala, Player.scala, Button.scala
  engine/           the drawing helpers from the course projects (not changed)
test/chess/
  RulesTests.scala         tests for the rules
  AiMoveChooserTests.scala tests for the AI part with a fake model (no internet needed)
```

## Course concepts I used

- **Immutability and pure functions:** `Board` and `GameState` are immutable case classes. `Rules.applyMove` doesn't
  change anything, it returns a new state. The only `var`s are in `ChessLogic` and `ChessGame`, the same way it was in Tetris.
- **Sealed traits and pattern matching:** `Side`, `GameStatus`, `Screen` and `Player` are sealed, so the compiler warns me
  if I forget a case in a `match`.
- **Subtype polymorphism and dynamic dispatch:** every `PieceType` has its own `attacks` method, so there is no big
  match on the piece type. Same for `AiModel.ask`: the game doesn't care which AI it's talking to.
- **Inheritance and abstract classes:** `Slider` (rook, bishop, queen) and `Leaper` (king, knight) share code, and
  ChatGPT and DeepSeek use the same `OpenAiCompatibleModel` class because their APIs work the same way.
- **Higher-order functions:** `map`, `filter`, `exists`, `forall`, `foldLeft`, `collect`, `flatMap` are used everywhere.
- **Function composition:** in `Rules.applyMove` every special rule (promotion, en passant, castling) is a small
  `Board => Board` function, and I chain them with `andThen`.
- **Tail recursion:** `Slider.ray` (walking along a line until something blocks it) and the retry loop in
  `AiMoveChooser`, both with `@tailrec`.
- **Local functions and closures:** for example in `ChessGame.drawBoard`, where the local functions use values
  computed once at the start.
- **Functions as values:** every `Button` stores its `onClick` action as a function.
- **Option and Try** instead of `null` and exceptions, e.g. `Board.apply` returns `Option[Piece]`.
- **Lazy evaluation:** the Claude client is a `lazy val`, so it's only created if you actually play against Claude.
- **Readable code:** no magic numbers (constants are in the `ChessGame` companion object), small functions and names
  that say what they do.

## Testing

I wrote two test files (15 tests in total, all passing):

- **Perft tests:** perft counts all possible move sequences from a position up to a certain depth. There are well-known
  correct numbers for this (e.g. 8902 after 3 moves from the start), so if my numbers match, the move generator
  is almost certainly correct. I tested the start position and two tricky positions ("Kiwipete" and an endgame) that
  check castling, en passant, pins and checks.
- Tests for checkmate (fool's mate), stalemate, insufficient material, threefold repetition, promotion and castling.
- Tests for the AI part with a **fake model** that gives prepared answers: a chatty answer, an illegal move, too many
  wrong answers, an API error and a promotion written without the "q".

## Problems I ran into

- **Unicode chess pieces:** the default Processing font doesn't have the chess symbols. I load a font that has them
  ("DejaVu Sans" on Linux, "Segoe UI Symbol" on Windows, "Apple Symbols" on Mac). If none of them is found, the game
  draws letters (K, Q, R, ...) instead.
- **Text in buttons was too high:** `drawTextCentered` from `GameBase` moves the text up a bit, so I wrote a small
  `drawLabel` that centres the text exactly.
- **Speed:** checking the game status needs all legal moves, and `draw()` runs many times a second, so I only
  calculate the status after each move and save it in `ChessLogic`.
- **ScalaTest didn't run at first:** Gradle found 0 tests until I added `@RunWith(classOf[JUnitRunner])` to the test classes.

## Limitations

- A pawn always promotes to a queen (no choosing a knight, rook or bishop).
- You always play White, and the board is never flipped.
- The AI is only as good as the model. Chat models are not chess engines, so don't expect grandmaster moves.
- There is no undo, save, clock or resign button.

## AI usage:
I used Claude(Anthropic) as: 
- Tutor: Brainstorm and break down the full assignment into small stages and keep tracking my progress until the end.
- Tester: make the test files namely `AiMoveChooserTests.scala` and `RulesTests.scala`. Both test files helped me to detect hidden bugs and fix them. 
- Programming API keys: helping me programming all the files in folder `ai`, namely `AiModel`, `AiMoveChooser`, `ClaudeModel`, `HttpJson`.
  since this part is not given in the OOFP course, and I have prior knowledge in programming API keys and linking them to the game.
- Formatter: Format, fix all the grammar mistakes, and build the tables in this file `README.md`
