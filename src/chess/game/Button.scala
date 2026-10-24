package chess.game

import engine.graphics.Rectangle

/** A clickable area with a label. `onClick` is a function value that runs when it is clicked. */
case class Button(label: String, area: Rectangle, onClick: () => Unit, enabled: Boolean = true)
