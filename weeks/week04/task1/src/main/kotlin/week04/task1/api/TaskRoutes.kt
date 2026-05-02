package week04.task1.api

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import week04.task1.config.JsonConfig
import week04.task1.mcp.McpClientBridge
import week04.task1.model.ChatPayload
import week04.task1.model.ChatResponse
import week04.task1.model.LatestTraceResponse
import week04.task1.model.StatusResponse
import week04.task1.model.ToolCallPayload
import week04.task1.model.ToolCallRecord
import week04.task1.model.ToolCallResponse
import week04.task1.model.ToolsResponse
import week04.task1.model.TokenUsage
import week04.task1.model.TraceStep
import week04.task1.model.TurnTrace
import week04.task1.service.extractQuoteCard
import week04.task1.service.LlmDecisionService
import week04.task1.service.normalizeToolResult
import week04.task1.service.synthesizeAnswer
import week04.task1.service.validateToolArguments
import week04.task1.storage.JsonToolHistoryStore
import week04.task1.storage.JsonTraceStore
import week04.task1.ui.buildUiPage

fun Application.configureRoutes(
    traceStore: JsonTraceStore,
    toolHistoryStore: JsonToolHistoryStore,
    mcpClient: McpClientBridge,
) {
    val llmDecisionService = LlmDecisionService()

    install(ContentNegotiation) {
        json(JsonConfig.json)
    }

    routing {
        get("/") {
            call.respondText(buildUiPage(), ContentType.Text.Html)
        }

        get("/api/mcp/status") {
            call.respond(StatusResponse(connected = mcpClient.isConnected(), server = "local stdio coingecko-mcp"))
        }

        get("/api/mcp/tools") {
            val trace = TurnTrace(requestId = "tools-${Instant.now().toEpochMilli()}")
            val tools = mcpClient.listTools(trace)
            traceStore.append(trace)
            call.respond(ToolsResponse(tools = tools, trace = trace))
        }

        post("/api/mcp/tool-call") {
            val payload = call.receive<ToolCallPayload>()
            val trace = TurnTrace(requestId = "tool-${Instant.now().toEpochMilli()}")
            val result = mcpClient.callTool(payload.name, payload.arguments, trace)
            traceStore.append(trace)
            toolHistoryStore.append(
                ToolCallRecord(
                    timestamp = Instant.now().toString(),
                    tool = payload.name,
                    arguments = payload.arguments,
                    response = result,
                ),
            )
            call.respond(ToolCallResponse(result = result, trace = trace))
        }

        post("/api/chat") {
            val payload = call.receive<ChatPayload>()
            val trace = TurnTrace(requestId = "chat-${Instant.now().toEpochMilli()}")
            val decisionResult = llmDecisionService.decide(payload.message)
            val toolDecision = decisionResult.decision
            trace.llmDecision = decisionResult.usage
            var availableTools: List<String> = emptyList()
            trace.steps += TraceStep(
                name = "agent.llm.router.decision",
                latencyMs = decisionResult.latencyMs,
                status = if (toolDecision.shouldUseMcp) "mcp:${decisionResult.provider}" else "no_mcp:${decisionResult.provider}",
            )

            if (toolDecision.shouldUseMcp) {
                val listed = mcpClient.listTools(trace)
                availableTools = listed.map { it.name }
            }

            val schemaValidation = validateToolArguments(toolDecision.name, toolDecision.arguments)
            trace.steps += TraceStep(
                name = "agent.schema.validation",
                latencyMs = 0,
                status = if (schemaValidation.valid) "ok" else "error",
            )

            val toolResult = if (toolDecision.shouldUseMcp && toolDecision.name != null && schemaValidation.valid) {
                val result = mcpClient.callTool(toolDecision.name, toolDecision.arguments, trace)
                toolHistoryStore.append(
                    ToolCallRecord(
                        timestamp = Instant.now().toString(),
                        tool = toolDecision.name,
                        arguments = toolDecision.arguments,
                        response = result,
                    ),
                )
                result
            } else {
                null
            }

            val normalizedToolResult = normalizeToolResult(toolDecision.name, toolResult)
            trace.steps += TraceStep(
                name = "agent.tool.result.normalization",
                latencyMs = 0,
                status = if (normalizedToolResult != null) "ok" else "skip",
            )

            val llmStep = synthesizeAnswer(payload.message, toolDecision, normalizedToolResult)
            trace.steps += llmStep.traceStep
            trace.llmSynthesis = llmStep.usage
            trace.llm = mergeUsage(trace.llmDecision, trace.llmSynthesis)
            traceStore.append(trace)
            val quoteCard = extractQuoteCard(toolDecision.name, toolResult)

            call.respond(
                ChatResponse(
                    reply = llmStep.answer,
                    mcp_used = toolDecision.shouldUseMcp,
                    decision_by = decisionResult.provider,
                    decision_reason = toolDecision.reason,
                    available_tools = availableTools,
                    tool_used = toolDecision.name,
                    schema_validation = schemaValidation,
                    normalized_tool_result = normalizedToolResult,
                    tool_result = toolResult,
                    quote_card = quoteCard,
                    trace = trace,
                ),
            )
        }

        get("/api/trace/latest") {
            call.respond(LatestTraceResponse(trace = traceStore.latest()))
        }

        get("/api/trace/history") {
            call.respond(traceStore.all())
        }
    }
}

private fun mergeUsage(a: TokenUsage?, b: TokenUsage?): TokenUsage? {
    if (a == null && b == null) return null
    val ai = a?.inputTokens ?: 0
    val ao = a?.outputTokens ?: 0
    val bi = b?.inputTokens ?: 0
    val bo = b?.outputTokens ?: 0
    return TokenUsage(
        inputTokens = ai + bi,
        outputTokens = ao + bo,
        totalTokens = ai + ao + bi + bo,
    )
}
