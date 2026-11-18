# HITA 整合包一比一移植状态

> 压缩上下文用的权威状态文件。后续会话先读此文件，不依赖聊天记忆。

## 分支

- 分支：`feat/hita-android-shell`
- 基线：已合并 `origin/master` / `v0.6.9`；已发布 `0.7.0`
- HITA_Android Shell 已由预设映射激活，固定 6 tab；视觉仍是预览版

## HITA-L 1.5.3

状态：**完成（同源原生模式，不是独立 Shell）**

实现：

- 宿主与 HITA-L 同源，复用原生 `FragmentTimeLine` / `TimetableFragment` / `NavigationFragment`
- `active_preset == hita-l-1.5.3` 时隐藏插件市场 tab
- HITA-L 系统导航栏为黑色

截图证据（同数据、同 hint、同系统栏）：

| 页面 | 差异像素 |
|---|---|
| 今日 | 0.55% |
| 时间表 | 0.53% |
| 功能中心 | 0.47% |

## HITA_Android 3.1.2 Shell

状态：**五页有真实内容，但助手页不是上游 AgentChat UI；整体未一比一**

| 页面 | 实现 |
|---|---|
| 待办 | 上游 `HomeworkScreen` 408 行 + Store/ViewModel |
| 今日 | 真实数据，主要边界对齐 |
| 时间表 | 真实周网格，跨节绝对定位 |
| 助手 | 宿主 ReAct 聊天页（非上游 961 行 UI） |
| 更多 | 上游结构，真实入口 |

Shell 入口：

- `app/.../shell/PresetShellActivity.kt`
- `MainActivity` 在 onCreate/onResume 检测 `activeShell` 后跳转，市场切换后立即生效
- 6 tab：待办 / 今日 / 时间表 / 助手 / 资讯 / 更多
- “资讯”已接 HOA 资讯与校园通知；页面视觉仍在 M4 继续对齐
- `hita-android-3.1.2` 的 shell 由宿主稳定映射，不依赖线上索引先改字段

## Agent 已移植

- `agent/core`：10 个文件
- `agent/llm`：ChatCompletionModels、ReactPromptBuilder、LlmClient
- `agent/settings`：AI 加密设置
- `agent/model`：ChatSession、ChatMessageEntity、ChatMessageRecord、AgentResourceCard
- `agent/tools`：ReActTool、ReActToolRegistry、ToolHelper
- `agent/tools` 已实现 4 个：
  - `get_timetable`
  - `add_activity`
  - `search_empty_classroom`
  - `search_teacher`
- `ShellChatService`：宿主 ReAct 循环（最大 6 步）
- `AgentChatViewModel`：会话持久化、切换、删除
- `shell/agent/AgentChatScreen.kt`：上游布局、Markdown、AI 设置对话框
- `agent/data`：ChatDatabase、DAO、Store、Repository（Room）

## Agent 未完成

- 上游 `AgentChatFragment` UI 已移植到 `shell/agent/AgentChatScreen.kt`
- 助手页标题/会话栏/欢迎卡与上游误差 1px
- 仍缺：附件选择与上传、资源卡片展示
- 附件选择/上传（会话持久化、切换、删除已接）
- 14 个工具已全部注册：
  - 本地：`get_timetable`、`add_activity`、`search_empty_classroom`、`search_teacher`、`search_timetable`
  - 资源/课程：`search_course`、`get_course_detail`、`search_external_resource`
  - 远程：`web_search`、`rag_search`、`crawl_page`、`crawl_site`、`crawl_status`、`submit_review`
- 限制：`search_external_resource` 只接 HOA（`ResourceDataService`），未接 HITCS/薪火

## API / 产物

- `TimetableService.getEventsDuringSync` 已加
- API AAR 已同步：
  - 模板 `9d9a196`
  - 社区 `2778baa`
- `hita-android-3.1.2` 通过宿主 shell 映射进入 Shell；HITA-L 仍走原生组合

## 下一步顺序

1. 按 M4 逐页对齐 6 个 Shell 页面（当前占位文案已移除）
2. 补助手附件上传与资源卡片交互
3. 重新采集六页截图并验收

## 运行时证据

- 设备：`emulator-5554`
- 注入 `preset_state.xml` 后启动，前台为 `PresetShellActivity`
- Room 库 `databases/hita_agent_chat` 已创建
- `chat_session` 实际存在一行：`新对话`
- 助手页坐标：标题 `[37,117][166,208]`，会话栏 `[64,320][575,383]`，欢迎卡 `[105,520][975,810]`
- 与上游同页截图差异：全屏 `8.05%`，去掉状态栏/导航区 `7.29%`
- 差异主因：附件按钮缺失、字体栅格化、底部 dock 背景
- 远程工具设备测试：`searchCoursesSync("高等数学")`、`braveSearchSync` 通过
- 后端健康检查：`https://api.hita.limpu.cn/health` 返回 `ok`
- 既有仪器测试仍有 2 个失败（`ExampleInstrumentedTest`、未登录成绩测试），与本次无关

## HITA Android 五页截图验收（2026-11-18）

设备：`emulator-5554`，1080x2400，API 36。上游 `cn.limpu.hita` 与本项目同设备同分辨率。

> 待办 3.54% 是裁掉顶部 130px、底部 310px 后的**探索性**内容区比较，**不包含悬浮 dock**，不作为正式验收基线。上游未登录时只显示 5 个 tab（隐藏“资讯”），本项目按产品决策固定 6 个 tab，因此全图比例不能直接比较；正式验收需登录上游或对 dock 做独立区域比较。

| 页面 | 内容区差异 | 结论 |
|---|---|---|
| 待办 | 3.54%（探索值；正式验收需登录上游或独立 dock mask） | 未通过 |
| 今日 | 30.37% | 未通过 |
| 时间表 | 22.63% | 未通过 |
| 助手 | 7.31% | 未通过 |
| 更多 | 28.73% | 未通过 |

### 待办页残差

- 已修：缺“待办”标题、周标签误显示为“星”
- 仍有：标题/日期区位置与上游差约 10px，卡片底色与行距仍不同

### 今日页残差

- 已补“享受当下！”与“今天暂无课程或日程安排”
- 像素差异未下降：字体宽度、卡片阴影与背景仍不同
- 日期标题字号/位置与上游不同

### 时间表页残差

- Shell 顶部为“第12周 / 2026秋季”，上游为“非所选学期 / 默认课表1”
- 网格起点、节次标签（1-12 vs 时间轴）与上游不同
- 课程卡宽度/位置仍有差异

### 助手页残差

- 顶部/欢迎卡/输入区位置已接近
- 字体栅格化、附件按钮缺失、底部 dock 背景仍不同

### 更多页残差

- 已补：分组标题字号/字重、每行左侧圆形图标、右侧箭头
- 仍有：上游 header 有主题切换和抽屉按钮，Shell 只有标题
- 仍有：上游用户卡为未登录态，Shell 当前显示已登录；数据态不同会放大像素差
- 仍有：行高、卡片间距、图标形状与上游不完全一致

结论：Shell 仍未达到逐页一比一，视觉验收未通过；但已能由线上整合包进入 Shell，不再停留在宿主默认壳。
