package dev.yakitori.barkforwarder.data.model

enum class HistoryStatus(val label: String) {
    SUCCESS("成功"),
    FAILED("失败"),
    FILTERED("已过滤"),
    ;

    companion object {
        fun fromStored(value: String?): HistoryStatus =
            entries.firstOrNull { it.name == value } ?: SUCCESS
    }
}
