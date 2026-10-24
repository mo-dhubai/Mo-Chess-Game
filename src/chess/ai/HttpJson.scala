package chess.ai

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.time.Duration

/** A tiny helper for POSTing JSON to an AI provider and reading the JSON answer. */
object HttpJson {

  private val ConnectTimeout = Duration.ofSeconds(10)
  private val ResponseTimeout = Duration.ofSeconds(120)  // reasoning models can take a while
  private val MaxErrorLength = 200

  private val client = HttpClient.newBuilder().connectTimeout(ConnectTimeout).build()
  private val mapper = new ObjectMapper()

  /** Turns text into a JSON string literal, with quotes and escaping, e.g. he said "hi" -> "he said \"hi\"". */
  def quote(text: String): String = mapper.writeValueAsString(text)

  def post(url: String, headers: Map[String, String], jsonBody: String): JsonNode = {
    val baseRequest = HttpRequest.newBuilder(URI.create(url))
      .timeout(ResponseTimeout)
      .header("Content-Type", "application/json")
      .POST(BodyPublishers.ofString(jsonBody))
    val request = headers.foldLeft(baseRequest) { case (builder, (name, value)) => builder.header(name, value) }.build()

    val response = client.send(request, BodyHandlers.ofString())
    if (response.statusCode() / 100 != 2)
      throw new RuntimeException(s"HTTP ${response.statusCode()}: ${response.body().take(MaxErrorLength)}")
    mapper.readTree(response.body())
  }
}
