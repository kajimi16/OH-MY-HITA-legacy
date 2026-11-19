package com.stupidtree.hitax.data.model.eas

/**
 * 校区能力门控：协议层未移植的校区，在入口处明确拒绝，
 * 避免把本部/威海账号的请求打到深圳域名上拿到错误数据。
 */
object CampusGate {

    fun unsupportedMessage(campus: EASToken.Campus): String? =
        if (CampusEndpoints.of(campus).easUsable) null
        else "该校区教务尚未移植（${label(campus)}）"

    fun label(campus: EASToken.Campus): String = when (campus) {
        EASToken.Campus.SHENZHEN -> "深圳"
        EASToken.Campus.BENBU -> "本部"
        EASToken.Campus.WEIHAI -> "威海"
    }
}
