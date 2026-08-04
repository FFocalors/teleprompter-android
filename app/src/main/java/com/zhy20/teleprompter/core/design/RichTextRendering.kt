package com.zhy20.teleprompter.core.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.zhy20.teleprompter.core.model.RichTextDocument
import com.zhy20.teleprompter.core.model.PlaybackTextAlignment
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptSpanStyle

fun PlaybackTextAlignment.toComposeTextAlign(): TextAlign = when (this) {
    PlaybackTextAlignment.Start -> TextAlign.Start
    PlaybackTextAlignment.Center -> TextAlign.Center
    PlaybackTextAlignment.End -> TextAlign.End
}

/**
 * The single ScriptDocument -> Compose mapping used by the editor, preview, player and remote.
 * Explicit normal values prevent a bold parent TextStyle from leaking into ordinary spans.
 */
object ScriptAnnotatedStringMapper {
    fun map(document: ScriptContent): AnnotatedString = buildAnnotatedString {
        RichTextDocument.toAnnotatedSegments(document).forEach { (text, styles) ->
            val start = length
            append(text)
            addStyle(
                SpanStyle(
                    fontWeight = if (ScriptSpanStyle.Bold in styles) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (ScriptSpanStyle.Italic in styles) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (ScriptSpanStyle.Underline in styles) TextDecoration.Underline else TextDecoration.None,
                ),
                start,
                length,
            )
        }
    }
}

fun ScriptContent.toAnnotatedString(): AnnotatedString = ScriptAnnotatedStringMapper.map(this)

@Composable
fun RichScriptText(
    document: ScriptContent,
    modifier: Modifier = Modifier,
    color: Color = AppColors.TextPrimary,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = document.toAnnotatedString(),
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        onTextLayout = onTextLayout,
    )
}
