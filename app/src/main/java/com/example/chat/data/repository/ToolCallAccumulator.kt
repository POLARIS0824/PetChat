package com.example.chat.data.repository

import com.example.chat.model.FunctionCall
import com.example.chat.model.StreamEvent
import com.example.chat.model.ToolCall

class ToolCallAccumulator {
    private val toolCalls = mutableMapOf<Int, MutableToolCall>()

    data class MutableToolCall(
        var id: String = "",
        var functionName: String = "",
        val arguments: StringBuilder = StringBuilder()
    )

    fun apply(event: StreamEvent.ToolCallDeltaEvent) {
        val entry = toolCalls.getOrPut(event.index) { MutableToolCall() }
        if (event.id != null) entry.id = event.id
        if (event.functionName != null) entry.functionName = event.functionName
        if (event.argumentsDelta != null) entry.arguments.append(event.argumentsDelta)
    }

    fun toToolCalls(): List<ToolCall> = toolCalls.values.map {
        ToolCall(
            id = it.id.takeIf { id -> id.isNotEmpty() },
            function = FunctionCall(
                name = it.functionName.takeIf { n -> n.isNotEmpty() },
                arguments = it.arguments.toString().takeIf { a -> a.isNotEmpty() }
            )
        )
    }

    fun hasPendingCalls(): Boolean = toolCalls.isNotEmpty()

    fun clear() = toolCalls.clear()
}
