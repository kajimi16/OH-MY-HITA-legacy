# 插件 Chrome / 使用统计 / 课程池扩展设计（2026-11-19）

## 1. 插件 chrome 统一

目标：`PluginUiActivity` 成为所有 UI 插件的唯一页面外壳。

- 宿主增加 `MaterialToolbar`：返回、插件 `displayName`、右侧无动作；
- 插件内容放入 Toolbar 下方；
- 新插件（公告/深圳/网址）删除自绘返回头，避免双标题；
- 旧插件保留自身内容标题作为页面内小节，宿主 Toolbar 负责返回与插件名；
- 插件自身仍可通过 `host.close()` 关闭。

## 2. 使用统计

### 2.1 隐私与开关

- 默认关闭；
- 首次启动显示一次询问，允许/拒绝均可；
- 用户可在「更多 → 使用统计」重新开关；
- 提供「清除使用统计」：清空队列并删除匿名安装 ID。

### 2.2 上报字段（白名单）

只允许：`install_id`、`app_version`、`sdk`、`event`、`screen`、`plugin_id`、
`timestamp`、`count`。禁止账号、学号、姓名、课程内容、自由文本。

事件白名单：

```
app_open, screen_view, plugin_open, plugin_install,
plugin_market_view, timetable_open, today_open, more_open
```

### 2.3 队列与上传

- Room 表 `usage_event`；
- WorkManager 唯一周期任务 `usage_upload`，每 6 小时或队列 ≥ 20 条时触发；
- `POST https://api.hita.limpu.cn/api/usage`，JSON batch；
- 失败保留队列，指数退避由 WorkManager 管理；
- 服务端返回聚合数据用于市场展示。

### 2.4 市场人数

- 索引 `uiPlugins` 支持可选 `downloads`、`activeUsers`；
- 市场卡片显示“下载 N · 活跃 M”；未知不显示；
- 客户端上报 `plugin_install` / `plugin_open`，服务端聚合后回写索引/统计接口。

## 3. 深圳课程池扩展

已有数据源：`EASRepository.getAvailableCourses`、`selectCourse`。
本轮新增：

- `CourseCatalogService.selectCourse(...)`；
- 课程卡“选课”按钮（先确认）；
- 失败/未登录状态提示。

以下仍依赖未移植的 `EASShenzhenRepository`：
选课任务、附件、历史挂科率、教师检索。设计上列为 Phase 2，不在本轮伪造。

## 4. 索引更新

- 本地 `market/index.json` 增加 HITA-L / HITA-Android 的 `presets`；
- HITA-Android refs 加入 announcement/community；
- uiPlugins 增加可选人数字段；
- 线上 registry 仍需同内容发布后才能自动安装。

## 5. 验收

- 所有 UI 插件打开时有统一返回栏；
- 默认统计关闭，首次询问；开启后队列有事件，关闭后不上传；清除有效；
- 市场卡片在索引有人数时显示；
- 课程池选课按钮可触发请求，未登录有明确提示；
- 构建、测试、lint 通过。


## 6. 当前实现状态（2026-11-19）

已完成：

- `PluginUiActivity` 统一 Toolbar（返回、插件名）；公告/深圳/网址移除自绘标题；
- `UsageStatsStore` 默认关闭、首次询问；
- Room `usage_events.db` + `UsageUploadWorker`（`/api/usage` 批量上传）；
- 事件白名单、匿名 install id、清除队列/install id；
- Shell 页面、插件打开/安装、市场打开事件；
- 市场 `downloads`/`activeUsers` 可选字段与卡片展示；
- `CourseCatalogService.selectCourse` 与课程卡选课按钮；
- 课程卡“详情”弹窗：课程简介/中英文教学大纲附件名称（`EASource` 现有 Web 会话）；
- `ShenzhenCourseAttachment` 所需字段映射到 `TermSubject.externalCourseId/taskNumber`；
- `server/usage/` Cloudflare Worker 参考实现（`POST /api/usage`、
  `GET /api/market/stats`）与 D1 schema；
- 本地 `market/index.json` 加入 presets，HITA-Android refs 加入两个新插件。

仍缺：

- HITA 后端 `/api/usage` 的实际域名部署与聚合（仓库已有 Worker 参考实现，尚未上线）；
- 深圳课程池历史挂科率/教师检索/选课任务（参考工程注明历史挂科率受教务隐私限制，
  选课任务需完整 `EASShenzhenRepository` 移植）；
- 线上 registry/preset 发布（本地索引已更新，远端仍需同步）；
- HITA-L 原生页面的统计询问与插件 chrome 未覆盖（本轮针对 HITA-Android Shell/PluginUiActivity）。
