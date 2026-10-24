package chess.ai

import scala.jdk.CollectionConverters._

/** An AI chat model that can be asked a question and answers with text.
 *  Each provider overrides `ask`, so the game never needs to know which provider it talks to. */
trait AiModel {
  def name: String          // shown in the game, e.g. "Claude"
  def apiKeyEnvVar: String  // environment variable holding the API key
  def modelEnvVar: String   // environment variable to override the model id
  def defaultModelId: String

  /** Sends `prompt` to the model and returns its reply. Throws if the request fails. */
  def ask(prompt: String): String

  final def modelId: String = envVar(modelEnvVar).getOrElse(defaultModelId)

  final def apiKey: Option[String] = envVar(apiKeyEnvVar)

  final def isConfigured: Boolean = apiKey.isDefined

  private def envVar(variable: String): Option[String] = sys.env.get(variable).map(_.trim).filter(_.nonEmpty)
}

/** ChatGPT and DeepSeek share the same "chat completions" API, so they share this class. */
class OpenAiCompatibleModel(val name: String,
                            val apiKeyEnvVar: String,
                            val modelEnvVar: String,
                            val defaultModelId: String,
                            endpoint: String) extends AiModel {

  def ask(prompt: String): String = {
    val body =
      s"""{"model": ${HttpJson.quote(modelId)},
         | "messages": [{"role": "user", "content": ${HttpJson.quote(prompt)}}]}""".stripMargin
    val headers = Map("Authorization" -> s"Bearer ${apiKey.getOrElse("")}")
    HttpJson.post(endpoint, headers, body).at("/choices/0/message/content").asText()
  }
}

class GeminiModel extends AiModel {
  val name = "Gemini"
  val apiKeyEnvVar = "GEMINI_API_KEY"
  val modelEnvVar = "GEMINI_MODEL"
  val defaultModelId = "gemini-3.6-flash"

  def ask(prompt: String): String = {
    val url = s"https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
    val body = s"""{"contents": [{"parts": [{"text": ${HttpJson.quote(prompt)}}]}]}"""
    val headers = Map("x-goog-api-key" -> apiKey.getOrElse(""))
    val parts = HttpJson.post(url, headers, body).at("/candidates/0/content/parts")
    parts.elements().asScala.map(_.path("text").asText("")).mkString
  }
}

object AiModel {
  val ChatGPT: AiModel = new OpenAiCompatibleModel(
    name = "ChatGPT",
    apiKeyEnvVar = "OPENAI_API_KEY",
    modelEnvVar = "OPENAI_MODEL",
    defaultModelId = "gpt-5.5",
    endpoint = "https://api.openai.com/v1/chat/completions")

  val DeepSeek: AiModel = new OpenAiCompatibleModel(
    name = "DeepSeek",
    apiKeyEnvVar = "DEEPSEEK_API_KEY",
    modelEnvVar = "DEEPSEEK_MODEL",
    defaultModelId = "deepseek-v4-flash",
    endpoint = "https://api.deepseek.com/chat/completions")

  val Claude: AiModel = new ClaudeModel
  val Gemini: AiModel = new GeminiModel

  val all: Seq[AiModel] = Seq(ChatGPT, Claude, Gemini, DeepSeek)
}
