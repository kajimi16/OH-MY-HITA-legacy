# /api/usage 与插件人数统计 Worker

客户端实现：

- `UsageAnalytics`：Room 队列；
- `UsageUploadWorker`：`POST https://api.hita.limpu.cn/api/usage`；
- `UsageStatsStore`：默认关闭、首次询问、可清除；
- 市场卡片支持索引/接口返回的 `downloads`、`activeUsers`。

部署（Cloudflare Workers + D1）：

```bash
wrangler d1 create hita-usage
wrangler d1 execute hita-usage --file=server/usage/schema.sql
# 在 wrangler.toml 配置 binding DB = hita-usage
wrangler deploy
```

路由需与现有 HITA API 同域；客户端不会上传账号、学号、姓名、课程内容或自由文本。

## 部署到自己的设备/账号

不需要 HITA 工作组权限。把 Worker 部署到自己的 Cloudflare 账号，或把同一逻辑放到自己的
HTTPS 服务器即可。

```bash
# 自己的 Worker 域名，客户端会请求：
#   https://usage.example.com/api/usage
#   https://usage.example.com/api/market/stats?ids=...
./gradlew :app:assembleDebug \
  -PusageBaseUrl=https://usage.example.com \
  -PpluginIndexUrl=https://your-host.example.com/registry/index.json
```

也可以直接安装构建出的 APK 到自己手机；不依赖 HITA 账号或同一工作组。

注意：

- 生产必须 HTTPS；Android 默认不允许明文 HTTP；
- 自建索引中的插件 URL 必须是你自己可访问的 HTTPS 地址，并更新 sha256；
- 不要把自己的服务伪装成 `api.hita.limpu.cn`，除非你确实拥有该域名/授权；
- 本仓库的公告邮箱、更新地址、预设来源仍是上游项目值，发布自己的分支时应一并替换。
