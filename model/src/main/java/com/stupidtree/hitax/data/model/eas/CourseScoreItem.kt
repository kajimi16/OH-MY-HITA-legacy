package com.stupidtree.hitax.data.model.eas

import com.google.gson.Gson

/**
 * 成绩条目：每个对应一条成绩
 */
class CourseScoreItem{
    var finalScores: Int = 0

    /** 原始成绩文本，如 "A-"、"优秀"、"85"。 */
    var finalScoresText: String? = null

    /** 教务把原始数字成绩放在成绩单元格前的注释里；没注释时为 null。 */
    var numericScore: Double? = null

    var credits: Int = 0

    /** 原始学分文本，保留 1.5 这类小数精度（契约层 credits 仍是 Int）。 */
    var creditsText: String? = null
    var hours: Int = 0
    var courseName: String? = null
    var courseCode: String? = null
    var courseProperty: String? = null
    var courseCategory: String? = null
    var schoolName: String? = null
    var assessMethod: String? = null
    var termName: String? = null

    override fun toString(): String {
        return Gson().toJson(this)
    }

}