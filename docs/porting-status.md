# HITA 整合包一比一移植状态

> 压缩上下文用的权威状态文件。后续会话先读此文件，不依赖聊天记忆。

## 分支

- 分支：`feat/hita-android-shell`
- 基线：`master` / `0.6.2`，未合并、未发布
- HITA_Android Shell 未接入线上预设

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
- `MainActivity` 检测 `PresetStateStore.activeShell()` 后跳转
- 未写入线上 `hita-android-3.1.2` 预设

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
- `AgentChatViewModel`：宿主聊天状态
- `agent/data`：ChatDatabase、DAO、Store、Repository（Room）

## Agent 未完成

- 上游 `AgentChatFragment` 961 行 UI
- 上游 `AgentChatViewModel` 的会话切换/删除/附件
- 剩余 10 个工具：
  - `search_timetable`
  - `search_course`
  - `get_course_detail`
  - `search_external_resource`
  - `web_search`
  - `rag_search`
  - `crawl_page`
  - `crawl_site`
  - `crawl_status`
  - `submit_review`

## API / 产物

- `TimetableService.getEventsDuringSync` 已加
- API AAR 已同步：
  - 模板 `9d9a196`
  - 社区 `2778baa`
- 线上预设仍走旧插件组合

## 下一步顺序

1. 接 `AgentChatViewModel` 到 Room Repository
2. 移植上游 AgentChat UI
3. 补剩余 10 个工具
4. 五页截图验收
5. 才考虑把 `hita-android-3.1.2` 切到 Shell
