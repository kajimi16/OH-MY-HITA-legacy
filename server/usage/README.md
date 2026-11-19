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
