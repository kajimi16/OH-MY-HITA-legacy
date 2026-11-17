package com.stupidtree.hitax.data.model.eas

/**
 * 校区 → 域名与通知源映射（多校区移植的地基）。
 *
 * 域名取自 HITA_Android v3.1.2 的 BenbuEASWebSource / WeihaiEASWebSource：
 * - 本部全部走 `*-hit-edu-cn.vpn.hit.edu.cn` 这类 VPN 域，需 VPN 隧道与额外凭据；
 * - 威海走 `webvpn.hitwh.edu.cn` 且路径经编码。
 * 因此 `apiBase` 对非深圳暂时为 null：在协议层未移植前，不允许拼出请求。
 */
data class CampusEndpoints(
    val campus: EASToken.Campus,
    /** App API 基址；null 表示该校区 API 协议尚未移植。 */
    val apiBase: String?,
    /** Web 教务基址。 */
    val webBase: String,
    /** 通知源标识，与 CampusNoticeRepository 的按校区选源一致。 */
    val noticeSource: String,
    /** 教务能力是否已可在整合包内使用。 */
    val easUsable: Boolean,
) {
    companion object {
        private val SHENZHEN = CampusEndpoints(
            campus = EASToken.Campus.SHENZHEN,
            apiBase = "https://mjw.hitsz.edu.cn/incoSpringBoot",
            webBase = "https://jw.hitsz.edu.cn",
            noticeSource = "shenzhen",
            easUsable = true,
        )

        private val BENBU = CampusEndpoints(
            campus = EASToken.Campus.BENBU,
            apiBase = null,
            webBase = "http://jwts-hit-edu-cn.vpn.hit.edu.cn",
            noticeSource = "benbu",
            easUsable = false,
        )

        private val WEIHAI = CampusEndpoints(
            campus = EASToken.Campus.WEIHAI,
            apiBase = null,
            webBase = "https://webvpn.hitwh.edu.cn",
            noticeSource = "weihai",
            easUsable = false,
        )

        fun of(campus: EASToken.Campus): CampusEndpoints = when (campus) {
            EASToken.Campus.SHENZHEN -> SHENZHEN
            EASToken.Campus.BENBU -> BENBU
            EASToken.Campus.WEIHAI -> WEIHAI
        }
    }
}
