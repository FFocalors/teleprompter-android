package com.zhy20.teleprompter.core.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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

fun ScriptContent.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    RichTextDocument.toAnnotatedSegments(this@toAnnotatedString).forEach { (text, styles) ->
        val start = length
        append(text)
        if (styles.isNotEmpty()) {
            addStyle(
                SpanStyle(
                    fontWeight = if (ScriptSpanStyle.Bold in styles) FontWeight.Bold else null,
                    fontStyle = if (ScriptSpanStyle.Italic in styles) FontStyle.Italic else null,
                    textDecoration = if (ScriptSpanStyle.Underline in styles) TextDecoration.Underline else null,
                ),
                start,
                length,
            )
        }
    }
}

@Composable
fun RichScriptText(
    document: ScriptContent,
    modifier: Modifier = Modifier,
    color: Color = AppColors.TextPrimary,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    Text(
        text = document.toAnnotatedString(),
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}
