package chess.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.{BetaOutputConfig, MessageCreateParams}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/** Claude, called through the official Anthropic Java SDK. */
class ClaudeModel extends AiModel {
  val name = "Claude"
  val apiKeyEnvVar = "ANTHROPIC_API_KEY"
  val modelEnvVar = "CLAUDE_MODEL"
  val defaultModelId = "claude-opus-5-5"

  private val MaxTokens = 16000L
  // if a safety check ever refuses the request, the API retries it on a suitable fallback model
  private val FallbackBeta = "server-side-fallback-2026-07-01"

  // lazy: the client is only built the first time Claude is actually asked something
  private lazy val client: AnthropicClient = AnthropicOkHttpClient.fromEnv()

  def ask(prompt: String): String = {
    val params = MessageCreateParams.builder()
      .model(modelId)
      .maxTokens(MaxTokens)
      // choosing one chess move is a quick task: low effort keeps the game snappy
      .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
      .addBeta(FallbackBeta)
      .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
      .addUserMessage(prompt)
      .build()

    val response = client.beta().messages().create(params)
    response.content().asScala.flatMap(_.text().toScala).map(_.text()).mkString
  }
}
