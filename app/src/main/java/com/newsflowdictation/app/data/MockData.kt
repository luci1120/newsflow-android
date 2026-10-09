package com.newsflowdictation.app.data

/**
 * Fallback demo data, used only when no lesson JSON is bundled in
 * assets/lessons/. Real lessons come from tools/transcribe.py and are loaded
 * by [SegmentRepository].
 */
object MockData {

    fun getMockNews(): List<NewsItem> = listOf(
        NewsItem(
            id = "demo_1",
            title = "CNN 10 — sample lesson (offline fallback)",
            source = "CNN10",
            youtubeVideoId = "tX6Om_7V9OM",
            thumbnailUrl = "https://img.youtube.com/vi/tX6Om_7V9OM/mqdefault.jpg",
            durationSecs = 607,
            publishedAt = "",
            description = "Bundled fallback so the app is usable before any lesson JSON is added.",
            segments = listOf(
                NewsSegment(
                    index = 0, startMs = 11180, endMs = 17700,
                    transcript = "Wake up, wake up, wake up, it is the first of the month.",
                ),
                NewsSegment(
                    index = 1, startMs = 18000, endMs = 20220,
                    transcript = "This is CNN 10. Let's lock in.",
                ),
                NewsSegment(
                    index = 2, startMs = 20600, endMs = 25200,
                    transcript = "We begin at the White House in a key meeting.",
                ),
                NewsSegment(
                    index = 3, startMs = 26020, endMs = 35800,
                    transcript = "Some of the biggest names in artificial intelligence gathered this week.",
                ),
                NewsSegment(
                    index = 4, startMs = 35800, endMs = 46180,
                    transcript = "Top executives from OpenAI, Anthropic, Meta, and other major players signed a commitment.",
                ),
            ),
        ),
    )

    /**
     * Local dictionary for instant translation (Tier 1).
     */
    val localDictionary: Map<String, WordEntry> = mapOf(
        "president" to WordEntry(
            "president", "/ˈprezɪdənt/",
            mapOf("zh" to "总统", "es" to "presidente", "vi" to "tổng thống", "ja" to "大統領", "ko" to "대통령", "pt" to "presidente"),
            listOf("The president addressed the nation last night.", "She met with the president of the company."),
        ),
        "congress" to WordEntry(
            "congress", "/ˈkɒŋɡres/",
            mapOf("zh" to "国会", "es" to "congreso", "vi" to "quốc hội", "ja" to "議会", "ko" to "의회", "pt" to "congresso"),
            listOf("Congress passed the new bill yesterday.", "The congress will vote on the budget next week."),
        ),
        "election" to WordEntry(
            "election", "/ɪˈlekʃn/",
            mapOf("zh" to "选举", "es" to "elección", "vi" to "cuộc bầu cử", "ja" to "選挙", "ko" to "선거", "pt" to "eleição"),
            listOf("The election results will be announced tonight.", "Voters will go to the polls for the election in November."),
        ),
        "economy" to WordEntry(
            "economy", "/ɪˈkɒnəmi/",
            mapOf("zh" to "经济", "es" to "economía", "vi" to "kinh tế", "ja" to "経済", "ko" to "경제", "pt" to "economia"),
            listOf("The economy is showing signs of recovery.", "Inflation remains a concern for the broader economy."),
        ),
        "weather" to WordEntry(
            "weather", "/ˈweðər/",
            mapOf("zh" to "天气", "es" to "clima", "vi" to "thời tiết", "ja" to "天気", "ko" to "날씨", "pt" to "clima"),
            listOf("The weather forecast predicts rain tomorrow.", "Severe weather is expected across the region."),
        ),
        "health" to WordEntry(
            "health", "/helθ/",
            mapOf("zh" to "健康", "es" to "salud", "vi" to "sức khỏe", "ja" to "健康", "ko" to "건강", "pt" to "saúde"),
            listOf("Public health officials urge vaccination.", "Mental health awareness has grown significantly."),
        ),
        "police" to WordEntry(
            "police", "/pəˈliːs/",
            mapOf("zh" to "警察", "es" to "policía", "vi" to "cảnh sát", "ja" to "警察", "ko" to "경찰", "pt" to "polícia"),
            listOf("Police are investigating the incident.", "Local police responded to the emergency call."),
        ),
        "community" to WordEntry(
            "community", "/kəˈmjuːnəti/",
            mapOf("zh" to "社区", "es" to "comunidad", "vi" to "cộng đồng", "ja" to "コミュニティ", "ko" to "커뮤니티", "pt" to "comunidade"),
            listOf("The community came together to support the family.", "Local communities are affected by the new policy."),
        ),
        "commitment" to WordEntry(
            "commitment", "/kəˈmɪtmənt/",
            mapOf("zh" to "承诺", "es" to "compromiso", "vi" to "cam kết", "ja" to "約束", "ko" to "약속", "pt" to "compromisso"),
            listOf("They signed a commitment to improve safety.", "Her commitment to the project never wavered."),
        ),
        "executive" to WordEntry(
            "executive", "/ɪɡˈzekjətɪv/",
            mapOf("zh" to "高管", "es" to "ejecutivo", "vi" to "giám đốc điều hành", "ja" to "幹部", "ko" to "임원", "pt" to "executivo"),
            listOf("Top executives met at the summit.", "The executive approved the new plan."),
        ),
    )
}
