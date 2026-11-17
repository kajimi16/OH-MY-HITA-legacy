package com.stupidtree.hitax.data.model.eas

/**
 * 本部（哈尔滨）零信任网关下的应用域清单。
 *
 * 取自 HITA_Android v3.1.2 `BenbuEASWebSource` 与 `WebViewLoginActivity.CampusUrls`：
 * 本部教务不是单一域名，而是网关下的一批 `*-hit-edu-cn.vpn.hit.edu.cn:1080` 应用域，
 * 每个业务（教务/实验/电子实验/研究生/研究生选课）各自一个子域，路径前缀不同。
 *
 * 2026-10 学校把网关域名从 `ivpn.hit.edu.cn` 换成 `vpn.hit.edu.cn`，别名与端口不变；
 * 旧域名仍可能被解析到，故 [LEGACY_VPN_DOMAIN] 保留用于登录页判定兼容。
 *
 * 注意：这些地址只有连上学校零信任网关（aTrust/VPN）后可达，本模块只负责拼串与判定，
 * 不承担连通性。
 */
object BenbuVpnHosts {

    /** 当前网关域名。 */
    const val VPN_DOMAIN = "vpn.hit.edu.cn"

    /** 2026-10 之前的网关域名，仅用于兼容旧登录页判定。 */
    const val LEGACY_VPN_DOMAIN = "ivpn.hit.edu.cn"

    /** 网关应用端口，所有应用域统一。 */
    const val VPN_PORT = 1080

    /** 教务主站（课表/成绩/考试/空教室/学籍）。 */
    const val TIMETABLE = "http://jwts-hit-edu-cn.$VPN_DOMAIN:$VPN_PORT"

    /** 网关门户，登录入口落点。 */
    const val PORTAL = "http://i-hit-edu-cn.$VPN_DOMAIN:$VPN_PORT"

    /** 实验教学（sjjx）。 */
    const val EXPERIMENT = "http://sjjx-hit-edu-cn.$VPN_DOMAIN:$VPN_PORT"

    /** 电子实验中心（eelabinfo），需要额外的 VcTchToken。 */
    const val ELECTRONIC_EXPERIMENT = "http://eelabinfo-hit-edu-cn.$VPN_DOMAIN:$VPN_PORT"

    /** 研究生管理（yjsgl）。 */
    const val GRADUATE = "http://yjsgl-hit-edu-cn.$VPN_DOMAIN:$VPN_PORT"

    /** 研究生选课（gcourse）。 */
    const val GRADUATE_COURSE = "http://gcourse-hit-edu-cn.$VPN_DOMAIN:$VPN_PORT"

    /** 六个应用域，按业务名索引，供请求拼接与日志定位使用。 */
    val applications: Map<String, String> = linkedMapOf(
        "timetable" to TIMETABLE,
        "portal" to PORTAL,
        "experiment" to EXPERIMENT,
        "electronicExperiment" to ELECTRONIC_EXPERIMENT,
        "graduate" to GRADUATE,
        "graduateCourse" to GRADUATE_COURSE,
    )

    /** 教务主站的 CAS 登录入口。 */
    const val TIMETABLE_LOGIN_PATH = "/loginCAS"

    /** 登录成功后用于校验会话的探针地址（按上游顺序：教务 → 门户首页）。 */
    val loginProbeUrls: List<String> = listOf(
        TIMETABLE + TIMETABLE_LOGIN_PATH,
        PORTAL + "/",
        PORTAL + "/portal/home/",
    )

    /** 统一身份认证（CAS）网关主机；本部只有一个。 */
    const val IDENTITY_GATEWAY_HOST = "ids-hit-edu-cn-s.$VPN_DOMAIN"

    /** 旧的 CAS 网关主机，登录页同源判定需要兼容。 */
    const val LEGACY_IDENTITY_GATEWAY_HOST = "ids-hit-edu-cn-s.$LEGACY_VPN_DOMAIN"

    /** 校内直连的 CAS 主机（无需网关时）。 */
    const val IDENTITY_HOST = "ids.hit.edu.cn"
}