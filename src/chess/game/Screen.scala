package chess.game

/** The screens of the game, in the order the user sees them. */
sealed trait Screen
case object WelcomeScreen    extends Screen
case object NameScreen       extends Screen  // the user types their name
case object ModeScreen       extends Screen  // play against a friend or an AI
case object FriendNameScreen extends Screen  // the friend types their name
case object AiChoiceScreen   extends Screen  // pick ChatGPT, Claude, Gemini or DeepSeek
case object GameScreen       extends Screen
