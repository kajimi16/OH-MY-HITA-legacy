package com.stupidtree.hitax.data.model.eas

import java.util.*

class EASToken {

    enum class TYPE { UNDERGRAD, GRAD }

    /** 校区：移植 HITA_Android 3.1.2 的 EASToken.Campus，缺省深圳以兼容旧凭据。 */
    enum class Campus {
        SHENZHEN,
        BENBU,
        WEIHAI;

        companion object {
            fun from(raw: String?): Campus = when (raw?.trim()?.uppercase()) {
                "BENBU" -> BENBU
                "WEIHAI" -> WEIHAI
                else -> SHENZHEN
            }
        }
    }

    // --- 新 API (mjw.hitsz.edu.cn/incoSpringBoot) bearer token 认证 ---
    var accessToken: String? = null
    var refreshToken: String? = null
    // route / JSESSIONID 由 Jsoup session 自动管理，同时存一份供跨请求复用
    var cookies = HashMap<String, String>()
    /** 深圳 Web 教务会话 cookie，与 API cookies 分离。 */
    var webCookies = HashMap<String, String>()
    /** 深圳 Web 会话基址：直连或 jw 代理。 */
    var webBaseUrl: String? = null

    var username: String? = null
    var password: String? = null
    var name: String? = null
    var stutype: TYPE = TYPE.UNDERGRAD // 培养类型，1本科生，其他研究生
    var campus: Campus = Campus.SHENZHEN
    var picture: String? = null //照片
    var id: String? = null //学生id
    var stuId: String? = null //学号
    var school: String? = null // 学院
    var major: String? = null //专业
    var grade: String? = null //年级
    var sfxsx: String? = null
    var email: String? = null //邮箱
    var phone: String? = null //电话

    fun getStudentType(): String {
        return if (stutype == TYPE.UNDERGRAD) "1" else "2"
    }

    fun isLogin(): Boolean {
        return !accessToken.isNullOrEmpty()
    }

    override fun toString(): String {
        return "EASToken(accessToken=${accessToken?.take(10)}..., username=$username, name=$name, stutype=${getStudentType()}, stuId=$stuId, school=$school)"
    }


}