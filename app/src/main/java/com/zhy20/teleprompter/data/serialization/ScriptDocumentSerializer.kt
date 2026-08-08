package com.zhy20.teleprompter.data.serialization

import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptDocument
import com.zhy20.teleprompter.core.model.ScriptSpan
import com.zhy20.teleprompter.core.model.ScriptSpanStyle
import java.util.logging.Level
import java.util.logging.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes a [ScriptDocument] to the versioned JSON persisted in the Room database and
 * decodes it back. Malformed JSON or an unknown schema version degrades safely to an
 * empty document instead of failing the caller.
 */
object ScriptDocumentSerializer {
    const val SchemaVersion = 1
    private val logger = Logger.getLogger("ScriptDocumentSerializer")

    fun emptyDocument(): ScriptDocument = ScriptContent(
        listOf(ScriptBlock.Paragraph(id = "paragraph-0", spans = listOf(ScriptSpan("")))),
    )

    fun encode(document: ScriptDocument): String = JSONObject().apply {
        put("schemaVersion", SchemaVersion)
        put("paragraphs", JSONArray().apply {
            document.blocks.forEach { block ->
                val paragraph = block as ScriptBlock.Paragraph
                put(JSONObject().apply {
                    put("id", paragraph.id)
                    put("spans", JSONArray().apply {
                        paragraph.spans.forEach { span ->
                            put(JSONObject().apply {
                                put("text", span.text)
                                put("bold", span.bold)
                                put("italic", span.italic)
                                put("underline", span.underline)
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun decode(json: String): ScriptDocument = runCatching {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == SchemaVersion) { "Unsupported ScriptDocument schema" }
        val paragraphsJson = root.getJSONArray("paragraphs")
        val paragraphs = buildList {
            for (paragraphIndex in 0 until paragraphsJson.length()) {
                val paragraph = paragraphsJson.getJSONObject(paragraphIndex)
                val spansJson = paragraph.optJSONArray("spans") ?: JSONArray()
                val spans = buildList {
                    for (spanIndex in 0 until spansJson.length()) {
                        val span = spansJson.getJSONObject(spanIndex)
                        add(
                            ScriptSpan(
                                text = span.optString("text", ""),
                                styles = buildSet {
                                    if (span.optBoolean("bold")) add(ScriptSpanStyle.Bold)
                                    if (span.optBoolean("italic")) add(ScriptSpanStyle.Italic)
                                    if (span.optBoolean("underline")) add(ScriptSpanStyle.Underline)
                                },
                            ),
                        )
                    }
                }
                add(
                    ScriptBlock.Paragraph(
                        id = paragraph.optString("id").ifBlank { "paragraph-$paragraphIndex" },
                        spans = spans.ifEmpty { listOf(ScriptSpan("")) },
                    ),
                )
            }
        }
        ScriptContent(paragraphs.ifEmpty { emptyDocument().blocks })
    }.getOrElse { error ->
        logger.log(Level.WARNING, "Invalid ScriptDocument JSON; using an empty document", error)
        emptyDocument()
    }
}
