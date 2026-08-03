package com.zhy20.teleprompter.data.fake

import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.Script
import com.zhy20.teleprompter.core.model.ScriptBlock
import com.zhy20.teleprompter.core.model.ScriptContent
import com.zhy20.teleprompter.core.model.ScriptFolder
import com.zhy20.teleprompter.core.model.ScriptSpan

object FakeData {
    val defaultPlaybackSettings = PlaybackSettings()

    val folders = listOf(
        ScriptFolder("interview", "采访", 1_785_000_000_000, 1),
        ScriptFolder("news", "新闻活动", 1_785_100_000_000, 1),
    )

    private fun content(vararg paragraphs: List<ScriptSpan>) = ScriptContent(
        paragraphs.mapIndexed { index, spans -> ScriptBlock.Paragraph("p$index", spans) },
    )

    val blankScript = Script(
        id = "new",
        title = "",
        plainTextPreview = "",
        content = content(listOf(ScriptSpan(""))),
        folderId = null,
        wordCount = 0,
        normalEstimatedDurationSeconds = 0,
        lastModifiedAt = 1_785_724_200_000,
        playbackSettings = defaultPlaybackSettings,
    )

    val scripts = listOf(
        Script(
            id = "1",
            title = "校长采访开场",
            plainTextPreview = "欢迎大家来到今天的特别活动。我们有很多内容要涵盖，非常高兴您能参与。首先，让我们来谈谈本季度的关键举措……",
            content = content(
                listOf(ScriptSpan("欢迎大家来到今天的特别活动。我们有很多内容要涵盖，非常高兴您能参与。")),
                listOf(ScriptSpan("首先，让我们来谈谈", bold = false), ScriptSpan("本季度的关键举措", bold = true), ScriptSpan("。学校正在持续改善学习环境，也欢迎大家提出建议。")),
                listOf(ScriptSpan("接下来，我们会介绍三个重点项目，并分享它们给师生带来的变化。")),
            ),
            folderId = "interview",
            wordCount = 850,
            normalEstimatedDurationSeconds = 200,
            lastModifiedAt = 1_785_724_200_000,
            playbackSettings = defaultPlaybackSettings,
        ),
        Script(
            id = "2",
            title = "晚间新闻播报",
            plainTextPreview = "观众朋友们晚上好，欢迎收看今天的晚间新闻。今天的主要内容有：科技创新大会在京开幕……",
            content = content(
                listOf(ScriptSpan("观众朋友们晚上好，欢迎收看今天的晚间新闻。")),
                listOf(ScriptSpan("今天的主要内容有：科技创新大会在京开幕，多项新成果集中发布。")),
            ),
            folderId = "news",
            wordCount = 1200,
            normalEstimatedDurationSeconds = 300,
            lastModifiedAt = 1_785_640_000_000,
            playbackSettings = defaultPlaybackSettings.copy(fontSize = 58),
        ),
        Script(
            id = "3",
            title = "开幕式主持稿",
            plainTextPreview = "尊敬的各位来宾，亲爱的老师、同学们，大家上午好。活动即将开始，请大家保持安静……",
            content = content(listOf(ScriptSpan("尊敬的各位来宾，亲爱的老师、同学们，大家上午好。活动即将开始，请大家保持安静。"))),
            folderId = null,
            wordCount = 620,
            normalEstimatedDurationSeconds = 150,
            lastModifiedAt = 1_785_500_000_000,
            playbackSettings = defaultPlaybackSettings.copy(mirrorEnabled = true),
        ),
    )
}
