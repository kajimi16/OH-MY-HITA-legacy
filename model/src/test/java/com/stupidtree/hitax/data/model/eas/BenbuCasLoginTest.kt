package com.stupidtree.hitax.data.model.eas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenbuCasLoginTest {

    private fun cleanPage() = BenbuPageSignals(
        title = "学生课表".lowercase(),
        text = "2026-2027学年第一学期 课程表".lowercase(),
        hasScoreTable = false,
        hasLoginForm = false,
        hasExamTable = false,
    )

    // ------------------------------------------------------------ 域名

    @Test
    fun allApplicationHostsUseCurrentVpnDomainAndPort() {
        assertTrue(BenbuVpnHosts.applications.size >= 6)
        BenbuVpnHosts.applications.forEach { (name, host) ->
            assertTrue("$name 应走 https 之外的网关 http 域: $host", host.startsWith("http://"))
            assertTrue("$name 应包含当前网关域名: $host", host.contains(BenbuVpnHosts.VPN_DOMAIN))
            assertTrue("$name 端口应为 1080: $host", host.endsWith(":${BenbuVpnHosts.VPN_PORT}"))
        }
    }

    @Test
    fun fiveBusinessDomainsAreDistinct() {
        val hosts = listOf(
            BenbuVpnHosts.TIMETABLE,
            BenbuVpnHosts.EXPERIMENT,
            BenbuVpnHosts.ELECTRONIC_EXPERIMENT,
            BenbuVpnHosts.GRADUATE,
            BenbuVpnHosts.GRADUATE_COURSE,
        )
        assertEquals(5, hosts.toSet().size)
        assertTrue(hosts.all { it.startsWith("http://") && it.endsWith(":1080") })
    }

    @Test
    fun loginProbesStartAtTeachingCasAndEndAtPortalHome() {
        assertEquals(BenbuVpnHosts.TIMETABLE + "/loginCAS", BenbuVpnHosts.loginProbeUrls.first())
        assertEquals(BenbuVpnHosts.PORTAL + "/portal/home/", BenbuVpnHosts.loginProbeUrls.last())
    }

    @Test
    fun legacyDomainIsOnlyKeptForCompatibility() {
        assertTrue(BenbuCasLogin.TEACHING_HOSTS.contains("jwts-hit-edu-cn.ivpn.hit.edu.cn"))
        assertTrue(BenbuCasLogin.IDENTITY_GATEWAY_HOSTS.contains("ids-hit-edu-cn-s.ivpn.hit.edu.cn"))
    }

    // ------------------------------------------------------------ 请求构造

    @Test
    fun graduateIsDetectedByPasswordSentinel() {
        assertTrue(BenbuCasLogin.isGraduateLogin("graduate"))
        assertFalse(BenbuCasLogin.isGraduateLogin("Graduate"))
        assertFalse(BenbuCasLogin.isGraduateLogin("graduate "))
    }

    @Test
    fun requiredCookiesDifferByStudentType() {
        assertEquals(setOf("JSESSIONID", "HIT"), BenbuCasLogin.requiredCookies(false))
        assertEquals(setOf("JSESSIONID", "sdp_user_token"), BenbuCasLogin.requiredCookies(true))
    }

    @Test
    fun missingCookiesAreSortedAndIgnoreBlankValues() {
        val cookies = mapOf("JSESSIONID" to "", "OTHER" to "x")
        assertEquals(listOf("HIT", "JSESSIONID"), BenbuCasLogin.missingCookies(cookies, false))
        assertEquals(listOf("JSESSIONID", "sdp_user_token"), BenbuCasLogin.missingCookies(emptyMap(), true))
    }

    @Test
    fun missingCookiesTreatsPresentPairAsComplete() {
        assertEquals(emptyList<String>(), BenbuCasLogin.missingCookies(
            mapOf("JSESSIONID" to "abc", "HIT" to "def"), false,
        ))
        assertEquals(emptyList<String>(), BenbuCasLogin.missingCookies(
            mapOf("JSESSIONID" to "abc", "sdp_user_token" to "def", "extra" to "y"), true,
        ))
    }

    @Test
    fun loginCheckUrlTargetsStudentTypeSpecificEndpoint() {
        assertEquals(
            "http://jwts-hit-edu-cn.vpn.hit.edu.cn:1080/kjscx/queryJxlListBySjid?sf_request_type=ajax",
            BenbuCasLogin.loginCheckUrl(false),
        )
        assertEquals(
            "http://yjsgl-hit-edu-cn.vpn.hit.edu.cn:1080/yjsgl/common/getXsJbxx?sf_request_type=ajax",
            BenbuCasLogin.loginCheckUrl(true),
        )
    }

    @Test
    fun loginCheckUrlTrimsTrailingSlashOfOverrideHost() {
        assertEquals(
            "http://host/base/kjscx/queryJxlListBySjid?sf_request_type=ajax",
            BenbuCasLogin.loginCheckUrl(false, timetableHost = "http://host/base/"),
        )
    }

    @Test
    fun loginCheckFormOnlyUndergradSendsBuildingId() {
        assertEquals(mapOf("id" to "1"), BenbuCasLogin.loginCheckForm(false))
        assertTrue(BenbuCasLogin.loginCheckForm(true).isEmpty())
    }

    @Test
    fun loginCheckHeadersAreAjaxForm() {
        val headers = BenbuCasLogin.loginCheckHeaders()
        assertEquals("XMLHttpRequest", headers["X-Requested-With"])
        assertTrue(headers.getValue("Content-Type").startsWith("application/x-www-form-urlencoded"))
    }

    // ------------------------------------------------------------ 成功判定

    @Test
    fun undergradLoginAcceptedOnlyOnCleanPage() {
        assertTrue(BenbuCasLogin.isLoginAccepted(200, "{}", false, cleanPage()))
        assertFalse(BenbuCasLogin.isLoginAccepted(200, "{}", false, cleanPage().copy(title = "登录")))
        assertFalse(BenbuCasLogin.isLoginAccepted(401, "{}", false, cleanPage()))
        assertFalse(BenbuCasLogin.isLoginAccepted(302, "{}", false, cleanPage()))
    }

    @Test
    fun graduateLoginAcceptedRequiresIsSuccessFlag() {
        assertTrue(BenbuCasLogin.isLoginAccepted(200, """{"isSuccess":true}""", true))
        assertTrue(BenbuCasLogin.isLoginAccepted(200, """{"isSuccess" : "true"}""", true))
        assertFalse(BenbuCasLogin.isLoginAccepted(200, """{"isSuccess":false}""", true))
        assertFalse(BenbuCasLogin.isLoginAccepted(200, "not json", true))
        assertFalse(BenbuCasLogin.isLoginAccepted(500, """{"isSuccess":true}""", true))
    }

    @Test
    fun graduateFlagParsingIsCaseInsensitiveAndKeyAnchored() {
        assertTrue(BenbuCasLogin.isGraduateLoginBodyOk("""{"IsSuccess":TRUE}"""))
        assertFalse(BenbuCasLogin.isGraduateLoginBodyOk("""{"xIsSuccess":true}"""))
        assertFalse(BenbuCasLogin.isGraduateLoginBodyOk("""{"isSuccess":"false"}"""))
    }

    @Test
    fun sessionValidityUsesSameExpiryRules() {
        assertTrue(BenbuCasLogin.isSessionAccepted(200, "", cleanPage()))
        assertFalse(BenbuCasLogin.isSessionAccepted(200, "", cleanPage().copy(hasLoginForm = true)))
        assertTrue(BenbuCasLogin.isGraduateSessionAccepted(200, """{"isSuccess":true}"""))
        assertFalse(BenbuCasLogin.isGraduateSessionAccepted(403, """{"isSuccess":true}"""))
    }

    // ------------------------------------------------------------ 失效识别

    @Test
    fun unauthorizedStatusesAreExpired() {
        assertTrue(BenbuCasLogin.isAuthExpired(401, cleanPage()))
        assertTrue(BenbuCasLogin.isAuthExpired(403, cleanPage()))
        assertFalse(BenbuCasLogin.isAuthExpired(200, cleanPage()))
    }

    @Test
    fun jsChallengePageIsExpired() {
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(title = "Just a moment...")))
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(text = "please enable javascript")))
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(title = "attention required")))
    }

    @Test
    fun loginFormWithoutScoreTableIsExpired() {
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(hasLoginForm = true)))
        assertFalse(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(hasLoginForm = true, hasScoreTable = true)))
    }

    @Test
    fun loginTitlesAreExpired() {
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(title = "登录")))
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(title = "统一身份认证")))
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(title = "vpn 门户")))
    }

    @Test
    fun textMentionsAreExpiredUnlessExamTablePresent() {
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(text = "请先登录")))
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(text = "未登录")))
        assertTrue(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(text = "认证失败")))
        assertFalse(BenbuCasLogin.isAuthExpired(200, cleanPage().copy(text = "未登录", hasExamTable = true)))
    }

    @Test
    fun scoreTableProtectsEverythingBelowStatusCheck() {
        assertFalse(BenbuCasLogin.isAuthExpired(200, BenbuPageSignals(
            title = "登录", text = "登录", hasScoreTable = true, hasLoginForm = false, hasExamTable = false,
        )))
        assertTrue(BenbuCasLogin.isAuthExpired(401, BenbuPageSignals(
            title = "登录", text = "登录", hasScoreTable = true, hasLoginForm = false, hasExamTable = false,
        )))
    }

    // ------------------------------------------------------------ 页面识别

    @Test
    fun sessionCollectedOnlyOnTeachingHosts() {
        assertTrue(BenbuCasLogin.canCollectSession("http://jwts-hit-edu-cn.vpn.hit.edu.cn:1080/xswhxx/queryXswhxx"))
        assertTrue(BenbuCasLogin.canCollectSession("https://jwts.hit.edu.cn/xszykb"))
        assertFalse(BenbuCasLogin.canCollectSession("http://i-hit-edu-cn.vpn.hit.edu.cn:1080/portal/home/"))
        assertFalse(BenbuCasLogin.canCollectSession("http://ids-hit-edu-cn-s.vpn.hit.edu.cn/authserver/login"))
        assertFalse(BenbuCasLogin.canCollectSession("ftp://jwts.hit.edu.cn/x"))
        assertFalse(BenbuCasLogin.canCollectSession("http://user@jwts.hit.edu.cn/x"))
    }

    @Test
    fun authserverPathNeverCollected() {
        assertFalse(BenbuCasLogin.canCollectSession("http://jwts-hit-edu-cn.vpn.hit.edu.cn:1080/AuthServer/reauthcheck/x"))
    }

    @Test
    fun authenticatedPageNeedsBothCookiesAndNonLoginPath() {
        val cookies = mapOf("JSESSIONID" to "a", "HIT" to "b")
        assertTrue(BenbuCasLogin.isAuthenticatedPage("http://jwts-hit-edu-cn.vpn.hit.edu.cn:1080/xszykb", cookies))
        assertFalse(BenbuCasLogin.isAuthenticatedPage("http://jwts-hit-edu-cn.vpn.hit.edu.cn:1080/loginCAS", cookies))
        assertFalse(BenbuCasLogin.isAuthenticatedPage("http://jwts.hit.edu.cn/xszykb", mapOf("JSESSIONID" to "a")))
        assertFalse(BenbuCasLogin.isAuthenticatedPage("http://i-hit-edu-cn.vpn.hit.edu.cn:1080/", cookies))
    }

    @Test
    fun jsessionDetectionIsCaseInsensitive() {
        assertTrue(BenbuCasLogin.hasJsessionId(mapOf("jsessionid" to "a")))
        assertFalse(BenbuCasLogin.hasJsessionId(mapOf("JSESSIONID" to " ")))
        assertFalse(BenbuCasLogin.hasJsessionId(mapOf("JSESSIONID" to "")))
    }

    // ------------------------------------------------------------ 凭据填充

    @Test
    fun credentialLoginPageMatchesGatewayCasPath() {
        assertTrue(BenbuCasLogin.matchesCredentialLoginPage(
            "http://ids-hit-edu-cn-s.vpn.hit.edu.cn/authserver/login?service=x",
        ))
        assertTrue(BenbuCasLogin.matchesCredentialLoginPage(
            "http://ids-hit-edu-cn-s.ivpn.hit.edu.cn/authserver/login",
        ))
        assertTrue(BenbuCasLogin.matchesCredentialLoginPage("https://ids.hit.edu.cn/authserver/login"))
        assertFalse(BenbuCasLogin.matchesCredentialLoginPage(
            "https://ids-hit-edu-cn-s.vpn.hit.edu.cn/authserver/login",
        ))
        assertFalse(BenbuCasLogin.matchesCredentialLoginPage(
            "http://ids.hit.edu.cn/authserver/login",
        ))
    }

    @Test
    fun credentialLoginPageRejectsPortUserInfoSubframeAndOtherPaths() {
        assertFalse(BenbuCasLogin.matchesCredentialLoginPage(
            "http://ids-hit-edu-cn-s.vpn.hit.edu.cn:1080/authserver/login",
        ))
        assertFalse(BenbuCasLogin.matchesCredentialLoginPage(
            "http://u@ids-hit-edu-cn-s.vpn.hit.edu.cn/authserver/login",
        ))
        assertFalse(BenbuCasLogin.matchesCredentialLoginPage(
            "http://ids-hit-edu-cn-s.vpn.hit.edu.cn/authserver/login", isMainFrame = false,
        ))
        assertFalse(BenbuCasLogin.matchesCredentialLoginPage(
            "http://ids-hit-edu-cn-s.vpn.hit.edu.cn/authserver/reauthcheck/x",
        ))
        assertFalse(BenbuCasLogin.matchesCredentialLoginPage("http://jwts-hit-edu-cn.vpn.hit.edu.cn:1080/loginCAS"))
    }

    @Test
    fun autofillScriptCarriesBothSelectorsAndClearsSecrets() {
        val script = BenbuCasLogin.autofillScript("2024000000", "secret")
        BenbuCasLogin.USERNAME_SELECTORS.forEach { assertTrue("缺少用户名选择器 $it", script.contains(it)) }
        BenbuCasLogin.PASSWORD_SELECTORS.forEach { assertTrue("缺少密码选择器 $it", script.contains(it)) }
        assertTrue(script.contains("\"2024000000\""))
        assertTrue(script.contains("\"secret\""))
        assertTrue(script.contains("username = '';"))
        assertTrue(script.contains("password = '';"))
    }

    @Test
    fun autofillScriptEscapesDangerousCharacters() {
        val script = BenbuCasLogin.autofillScript("a\"b\\c", "p\nq\tr\u2028s")
        assertTrue(script.contains("""var username = "a\"b\\c";"""))
        assertTrue(script.contains("""var password = "p\nq\tr\u2028s";"""))
        assertFalse(script.contains("p\nq"))
    }

    // ------------------------------------------------------------ HTML 降级提取

    @Test
    fun signalsAreNormalizedRegardlessOfCase() {
        val mixed = BenbuPageSignals(
            title = "Just a Moment...",
            text = "Please Enable\u00A0JavaScript",
            hasScoreTable = false,
            hasLoginForm = false,
            hasExamTable = false,
        )
        assertTrue(mixed.normalizedTitle.startsWith("just a moment"))
        assertTrue(mixed.isJsChallenge)
        assertTrue(BenbuCasLogin.isAuthExpired(200, mixed))
    }

    @Test
    fun htmlFallbackExtractsTitleTextAndMarkers() {
        val html = """
            <html><head><title>统一身份认证</title><style>.x{}</style></head>
            <body><p>请 登录</p><script>var a=1;</script>
            <form action="/authserver/login"><input name="mm"></form></body></html>
        """.trimIndent()
        val signals = BenbuPageSignals.fromHtml(html)
        assertEquals("统一身份认证", signals.title)
        assertTrue(signals.hasLoginForm)
        assertFalse(signals.hasScoreTable)
        assertTrue(signals.isAuthExpiredShadowedBySignals())
    }

    private fun BenbuPageSignals.isAuthExpiredShadowedBySignals(): Boolean =
        BenbuCasLogin.isAuthExpired(200, this)
}