package com.zhy20.teleprompter.data.fake

import com.zhy20.teleprompter.core.model.PlaybackSettings
import com.zhy20.teleprompter.core.model.ChineseSpeechDurationEstimator
import com.zhy20.teleprompter.core.model.CountdownOption
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
        Script(
            id = "4",
            title = "超长多段落滚动测试",
            plainTextPreview = "这是用于验证真实排版高度、稳定自动滚动和结束位置的长台本……",
            content = content(
                listOf(ScriptSpan("这是用于验证自动滚动的第一段。画面开始时，第一行应位于正文区底部，并从台本开头向上滚动。")),
                listOf(ScriptSpan("第二段继续补充足够的正文，让内容高度明确超过一个完整播放视口。滚动速度应由有效播放时间决定，而不是由设备刷新率或帧数决定。")),
                listOf(ScriptSpan("第三段用于观察暂停行为。暂停以后，文字位置、已用时间和语义进度都应冻结；立即恢复时不应出现跳动。")),
                listOf(ScriptSpan("第四段用于观察倒计时恢复。三秒倒计时期间正文保持不动，倒计时结束以后从完全相同的位置继续。")),
                listOf(ScriptSpan("第五段用于观察变速。降低或提高倍率以后，当前位置保持连续，已用时间不清零，剩余时间根据新速度重新计算。")),
                listOf(ScriptSpan("第六段用于观察手动进度调整。拖动滑块或在中央区域上下滑动时，自动滚动暂时冻结，松手以后从新位置继续。")),
                listOf(ScriptSpan("第七段用于检查提词线和提词条互斥。横线模式只有红线，提词条模式只有半透明红色区域，关闭模式不显示辅助元素。")),
                listOf(ScriptSpan("最后一段用于检查完成状态。到达终点后，最后一行停在正文下三分之一处，进度保持百分之百，页面不会自动退出或回到开头。")),
            ),
            folderId = null,
            wordCount = 1_650,
            normalEstimatedDurationSeconds = 90,
            lastModifiedAt = 1_785_730_000_000,
            playbackSettings = defaultPlaybackSettings.copy(countdown = CountdownOption.Off),
        ),
        Script(
            id = "5",
            title = "粗体斜体下划线测试",
            plainTextPreview = "普通文字、粗体、斜体和下划线应在镜像与滚动期间保持格式……",
            content = content(
                listOf(
                    ScriptSpan("普通文字用于建立基线。"),
                    ScriptSpan("这一段是粗体。", bold = true),
                    ScriptSpan("接下来是斜体。", italic = true),
                    ScriptSpan("最后是下划线。", underline = true),
                ),
                listOf(ScriptSpan("镜像开启时只翻转这一组台本文字，顶部时间、进度、红色提词辅助和控制按钮都保持正常方向。")),
            ),
            folderId = null,
            wordCount = 180,
            normalEstimatedDurationSeconds = 45,
            lastModifiedAt = 1_785_731_000_000,
            playbackSettings = defaultPlaybackSettings.copy(mirrorEnabled = true),
        ),
        Script(
            id = "6",
            title = "850 字语速预计测试",
            plainTextPreview = "用于验证标准中文语速的 850 个等效字符台本。",
            content = content(listOf(ScriptSpan("字".repeat(850)))),
            folderId = null,
            wordCount = 850,
            normalEstimatedDurationSeconds = 200,
            lastModifiedAt = 1_785_732_000_000,
            playbackSettings = defaultPlaybackSettings.copy(countdown = CountdownOption.Off),
        ),
    ).map { script ->
        script.copy(
            wordCount = script.content.plainText().count { !it.isWhitespace() },
            normalEstimatedDurationSeconds = ChineseSpeechDurationEstimator.estimate(script.content),
        )
    }
}
