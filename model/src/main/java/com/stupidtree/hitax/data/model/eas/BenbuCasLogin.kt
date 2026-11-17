package com.stupidtree.hitax.data.model.eas

import java.net.URI

/**
 * 本部（哈尔滨）CAS 登录的纯逻辑：请求构造、会话判定、失效识别。
 *
 * 全部输入都是字符串/状态码/URL，不发起网络、不依赖 jsoup，便于单测。
 * 语义严格对齐 HITA_Android v3.1.2：
 * - `BenbuEASWebSource.login / loginCheck / isAuthExpiredResponse`
 * - `WebViewLoginActivity`（`BENBU_REQUIRED_COOKIES`、`CampusUrls`）
 * - `WebLoginSuccessPolicy`（`benbuTeachingHosts` / `canCollectBenbuSession`）
 * - `EasWebCredentialPolicy`（CAS 登录页同源与自动填充）
 *
 * 与上游的差异只有一处有意为之：上游用 jsoup 直接从 HTML 里取 title/text/表格信号，
 * 这里把这些信号作为 [BenbuPageSignals] 显式传入，判定表本身逐条照搬。
 */
object BenbuCasLogin {

    /** 本科生会话必需的 cookie（`JSESSIONID` 会话 + `HIT` 网关票据）。 */
    val UNDERGRAD_COOKIES = setOf("JSESSIONID", "HIT")

    /** 研究生会话必需的 cookie（`sdp_user_token` 代替 `HIT`）。 */
    val GRAD_COOKIES = setOf("JSESSIONID", "sdp_user_token")

    /** 本部登录时密码字段被借用来标记研究生身份（上游约定）。 */
    const val GRAD_PASSWORD_SENTINEL = "graduate"

    /** 允许收集教务会话的主机：cas 登录后的回跳落点。 */
    val TEACHING_HOSTS = setOf(
        "jwts.hit.edu.cn",
        "jwts-hit-edu-cn.${BenbuVpnHosts.VPN_DOMAIN}",
        "jwts-hit-edu-cn.${BenbuVpnHosts.LEGACY_VPN_DOMAIN}",
    )

    /** 唯一身份认证网关主机（http，仅网关内可达）。 */
    val IDENTITY_GATEWAY_HOSTS = setOf(
        BenbuVpnHosts.IDENTITY_GATEWAY_HOST,
        BenbuVpnHosts.LEGACY_IDENTITY_GATEWAY_HOST,
    )

    /** CAS 登录页路径，自动填充只在这里注入凭据。 */
    const val CAS_LOGIN_PATH = "/authserver/login"

    // ---------------------------------------------------------------- 身份

    /** 上游用 `password == "graduate"` 表示研究生账号。 */
    fun isGraduateLogin(password: String): Boolean = password == GRAD_PASSWORD_SENTINEL

    // ---------------------------------------------------------------- 请求构造

    fun requiredCookies(isGraduate: Boolean): Set<String> =
        if (isGraduate) GRAD_COOKIES else UNDERGRAD_COOKIES

    /**
     * 缺失的必需 cookie，按字母序返回，便于提示文案稳定。
     * 与上游一致：key 精确匹配（区分大小写）。
     */
    fun missingCookies(cookies: Map<String, String>, isGraduate: Boolean): List<String> =
        requiredCookies(isGraduate)
            .filter { cookies[it].isNullOrBlank() }
            .sorted()

    /**
     * 登录校验请求地址。
     * - 本科：教务主站实时查询教学楼列表（`POST /kjscx/queryJxlListBySjid`）
     * - 研究生：研究生管理站查询学籍（`POST /yjsgl/common/getXsJbxx`）
     */
    fun loginCheckUrl(
        isGraduate: Boolean,
        timetableHost: String = BenbuVpnHosts.TIMETABLE,
        graduateHost: String = BenbuVpnHosts.GRADUATE,
    ): String = if (isGraduate) {
        graduateHost.trimEnd('/') + "/yjsgl/common/getXsJbxx?sf_request_type=ajax"
    } else {
        timetableHost.trimEnd('/') + "/kjscx/queryJxlListBySjid?sf_request_type=ajax"
    }

    /** 登录校验请求体：本科固定带 `id=1`（教学楼查询参数），研究生不带参数。 */
    fun loginCheckForm(isGraduate: Boolean): Map<String, String> =
        if (isGraduate) emptyMap() else mapOf("id" to "1")

    /** 登录校验请求头，与上游一致。 */
    fun loginCheckHeaders(): Map<String, String> = linkedMapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest",
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
    )

    // ---------------------------------------------------------------- 成功判定

    /** 研究生登录响应里的成功标志（`{"isSuccess":true}`，也接受字符串 `"true"`）。 */
    fun isGraduateLoginBodyOk(body: String): Boolean {
        val matched = Regex(""""isSuccess"\s*:\s*("?)(true|false)\1""", RegexOption.IGNORE_CASE)
            .find(body) ?: return false
        return matched.groupValues[2].equals("true", ignoreCase = true)
    }

    /**
     * 登录是否成功。
     *
     * 上游判定（`BenbuEASWebSource.login`）：
     * - 研究生：HTTP 200 且 `isSuccess == true`，不看 HTML 失效特征；
     * - 本科生：HTTP 200 且响应不是 CAS/VPN 登录页。
     *
     * 本科生这条最容易被写错：CAS 回跳后仍是 HTTP 200，只看状态码会把过期 cookie
     * 当成登录成功并持久化。
     */
    fun isLoginAccepted(
        statusCode: Int,
        body: String,
        isGraduate: Boolean,
        signals: BenbuPageSignals = BenbuPageSignals.fromHtml(body),
    ): Boolean {
        if (statusCode != 200) return false
        if (isGraduate) return isGraduateLoginBodyOk(body)
        return !isAuthExpired(statusCode, signals)
    }

    /** 本科会话是否仍有效（`loginCheck`：GET 学籍页且非登录页）。 */
    fun isSessionAccepted(
        statusCode: Int,
        body: String,
        signals: BenbuPageSignals = BenbuPageSignals.fromHtml(body),
    ): Boolean = statusCode == 200 && !isAuthExpired(statusCode, signals)

    /** 研究生会话是否仍有效（`loginCheck` 研究生分支）。 */
    fun isGraduateSessionAccepted(statusCode: Int, body: String): Boolean =
        statusCode == 200 && isGraduateLoginBodyOk(body)

    // ---------------------------------------------------------------- 失效识别

    /**
     * 是否命中登录失效页，判定表逐条照搬上游 `isAuthExpiredResponse`。
     */
    fun isAuthExpired(statusCode: Int, signals: BenbuPageSignals): Boolean {
        if (statusCode == 401 || statusCode == 403) return true
        if (signals.isJsChallenge) return true
        if (signals.hasLoginForm && !signals.hasScoreTable) return true
        if (!signals.hasScoreTable) {
            if (signals.normalizedTitle.contains("登录") ||
                signals.normalizedTitle.contains("统一身份认证") ||
                signals.normalizedTitle.contains("vpn")
            ) {
                return true
            }
            if (!signals.hasExamTable &&
                (signals.normalizedText.contains("登录") ||
                    signals.normalizedText.contains("未登录") ||
                    signals.normalizedText.contains("认证"))
            ) {
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------- 会话收集

    /**
     * 是否允许在该页面上收集教务会话。
     * 身份认证/MFA 页面与 VPN 门户页上的 cookie 不能当作教务会话（上游同款防线）。
     */
    fun canCollectSession(url: String): Boolean {
        val uri = parse(url) ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        if (uri.userInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        if (host !in TEACHING_HOSTS) return false
        return !uri.path.orEmpty().contains("authserver", ignoreCase = true)
    }

    /**
     * 是否已进入教务主页（可结束登录流程）。
     * 条件：可收集会话 + 路径不含 login + `JSESSIONID` 与 `HIT` 都已就位。
     */
    fun isAuthenticatedPage(url: String, cookies: Map<String, String>): Boolean {
        if (!canCollectSession(url)) return false
        val path = parse(url)?.path.orEmpty()
        if (path.contains("login", ignoreCase = true)) return false
        return !cookies["JSESSIONID"].isNullOrBlank() && !cookies["HIT"].isNullOrBlank()
    }

    /**
     * 大小写不敏感地判断 cookies 中是否存在非空 `JSESSIONID`。
     * 网关不同节点下 key 大小写并不统一，严格 equals 会漏判。
     */
    fun hasJsessionId(cookies: Map<String, String>): Boolean =
        cookies.any { (key, value) -> key.equals("JSESSIONID", ignoreCase = true) && value.isNotBlank() }

    // ---------------------------------------------------------------- 凭据填充

    /**
     * 是否是需要自动填充凭据的 CAS 登录页。
     * 上游只认主框架、无端口、无 userinfo 的 `.../authserver/login`，且网关主机必须走 http。
     */
    fun matchesCredentialLoginPage(url: String, isMainFrame: Boolean = true): Boolean {
        if (!isMainFrame) return false
        val uri = parse(url) ?: return false
        if (uri.port != -1) return false
        if (uri.userInfo != null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val approved = when (host) {
            in IDENTITY_GATEWAY_HOSTS -> scheme == "http"
            BenbuVpnHosts.IDENTITY_HOST -> scheme == "https"
            else -> return false
        }
        if (!approved) return false
        return uri.path.orEmpty().lowercase().trimEnd('/').endsWith(CAS_LOGIN_PATH)
    }

    /** 用户名输入框选择器，按优先级排列（上游 autofill 脚本同款）。 */
    val USERNAME_SELECTORS = listOf(
        """input[autocomplete="username"]""",
        """input[name*="user" i]""",
        """input[id*="user" i]""",
        """input[type="text"]""",
    )

    /** 密码输入框选择器。 */
    val PASSWORD_SELECTORS = listOf(
        """input[autocomplete="current-password"]""",
        """input[type="password"]""",
    )

    /**
     * 构造自动填充脚本。凭据以 JSON 字符串字面量传入并做转义，
     * 脚本执行完即清空局部变量，避免凭据留在页面上下文。
     */
    fun autofillScript(username: String, password: String): String {
        val userJson = username.toJavaScriptString()
        val passJson = password.toJavaScriptString()
        return """
            (function() {
              var username = $userJson;
              var password = $passJson;
              function firstVisible(selector) {
                var fields = document.querySelectorAll(selector);
                for (var i = 0; i < fields.length; i++) {
                  var field = fields[i];
                  if (!field.disabled && (field.offsetWidth || field.offsetHeight || field.getClientRects().length)) return field;
                }
                return null;
              }
              var user = firstVisible('${USERNAME_SELECTORS.joinToString(", ")}');
              var pass = firstVisible('${PASSWORD_SELECTORS.joinToString(", ")}');
              if (!user || !pass) return false;
              var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
              setter.call(user, username);
              setter.call(pass, password);
              [user, pass].forEach(function(input) {
                input.dispatchEvent(new Event('input', {bubbles:true}));
                input.dispatchEvent(new Event('change', {bubbles:true}));
              });
              username = '';
              password = '';
              return true;
            })();
        """.trimIndent()
    }

    private fun String.toJavaScriptString(): String = buildString {
        append('"')
        this@toJavaScriptString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(char)
            }
        }
        append('"')
    }

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()
}

/**
 * 判定登录页所需的 HTML 信号。
 *
 * 上游用 jsoup 现场提取；这里改成显式字段，判定逻辑因此可以纯函数化并逐条断言。
 */
data class BenbuPageSignals(
    val title: String,
    val text: String,
    val hasScoreTable: Boolean,
    val hasLoginForm: Boolean,
    val hasExamTable: Boolean,
) {
    /** 标题归一化为小写，判定一律用它，调用方无需预处理大小写。 */
    val normalizedTitle: String
        get() = title.lowercase()

    /** 正文归一化：小写 + 空白折叠，与上游 `body().text()` 的处理一致。 */
    val normalizedText: String
        get() = text.lowercase().replace(REGEX_SPACES, " ")

    /** 网关的 JS 挑战页（"Just a moment..."）等同于登录失效。 */
    val isJsChallenge: Boolean
        get() = normalizedText.contains("your browser does not support javascript") ||
            normalizedText.contains("javascript is disabled in your browser") ||
            normalizedText.contains("please enable javascript") ||
            normalizedText.contains("enable javascript to continue") ||
            normalizedTitle.contains("just a moment") ||
            normalizedTitle.contains("attention required")

    companion object {
        private val REGEX_SPACES = Regex("\\s+")

        /**
         * 从原始 HTML 做**最保守**的降级提取：只取 `<title>`、去标签正文、以及几个已知表格/表单标记。
         * 仅用于拿不到 DOM 时的兜底；正式路径应由调用方用 jsoup 提取信号后传入。
         */
        fun fromHtml(html: String): BenbuPageSignals {
            val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1).orEmpty()
            val text = html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return BenbuPageSignals(
                title = title,
                text = text,
                hasScoreTable = Regex("""<table[^>]*class="[^"]*bot_line""", RegexOption.IGNORE_CASE).containsMatchIn(html),
                hasLoginForm = Regex("""<input[^>]*name="(mm|password)"""", RegexOption.IGNORE_CASE).containsMatchIn(html) ||
                    Regex("""<form[^>]*action="[^"]*(login|authentication)""", RegexOption.IGNORE_CASE).containsMatchIn(html),
                hasExamTable = Regex("""<table[^>]*id="[^"]*ks""", RegexOption.IGNORE_CASE).containsMatchIn(html),
            )
        }
    }
}