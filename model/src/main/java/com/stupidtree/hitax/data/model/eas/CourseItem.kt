package com.stupidtree.hitax.data.model.eas

import com.google.gson.Gson

/**
 * 导入课表时使用：总课表里的课程条目
 */
class CourseItem {
    var code: String? = null
    var name: String? = null
    var weeks: MutableList<Int> = mutableListOf()
    var teacher: String? = null
    var classroom: String? = null
    var dow = -1
    var begin = -1
    var last = -1

    /** 实验课用具体时间（`HH:mm`），普通课为 null；对应 begin/last 为 -1。 */
    var startTime: String? = null
    var endTime: String? = null


    override fun toString(): String {
        return Gson().toJson(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CourseItem

        if (name != other.name) return false
        if (weeks != other.weeks) return false
        if (teacher != other.teacher) return false
        if (classroom != other.classroom) return false
        if (dow != other.dow) return false
        if (begin != other.begin) return false
        if (last != other.last) return false
        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + weeks.hashCode()
        result = 31 * result + (teacher?.hashCode() ?: 0)
        result = 31 * result + (classroom?.hashCode() ?: 0)
        result = 31 * result + dow
        result = 31 * result + begin
        result = 31 * result + last
        result = 31 * result + (startTime?.hashCode() ?: 0)
        result = 31 * result + (endTime?.hashCode() ?: 0)
        return result
    }
}
