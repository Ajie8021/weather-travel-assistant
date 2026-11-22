# 后台管理原型公开链接与截图索引

## 1. 公开预览链接

**公开预览：** [打开后台管理原型](https://dynamic-griffin-0aeafa.netlify.app/login)

入口页面：`login.html`  
原型类型：纯 HTML + CSS 静态页面  
截图目录：[admin-ui-design](admin-ui-design/)

> 以下 6 张截图均为真实全页截图，页面内同时包含正常数据区以及空态、失败态预览区，全部使用真实原型截图，不使用占位图片。

## 2. 页面与截图索引

| 页面名称 | 对应 HTML 文件名 | 截图文件名 | 包含的状态 |
|---|---|---|---|
| 管理员登录 | `login.html` | [login.png](admin-screenshots/login.png) | 正常、空态、失败态 |
| 用户 / 会话记录 | `users.html` | [users.png](admin-screenshots/users.png) | 正常、空态、失败态 |
| 行程记录 | `plans.html` | [plans.png](admin-screenshots/plans.png) | 正常、空态、失败态 |
| 行程详情 | `plan-detail.html` | [plan-detail.png](admin-screenshots/plan-detail.png) | 正常、空态、失败态 |
| 异常与 API 日志 | `logs.html` | [logs.png](admin-screenshots/logs.png) | 正常、空态、失败态 |
| 反馈管理 | `feedback.html` | [feedback.png](admin-screenshots/feedback.png) | 正常、空态、失败态 |

## 3. 空态截图明细

| 页面名称 | 截图文件名 | 空态文案 |
|---|---|---|
| 管理员登录 | [login.png](admin-screenshots/login.png) | “初始空态”：请输入管理员账号和密码；未填写必填项时提示补全 |
| 用户 / 会话记录 | [users.png](admin-screenshots/users.png) | “暂无用户数据”及重置筛选引导 |
| 行程记录 | [plans.png](admin-screenshots/plans.png) | “暂无行程记录”及筛选引导 |
| 行程详情 | [plan-detail.png](admin-screenshots/plan-detail.png) | “暂无修改记录”及返回行程列表引导 |
| 异常与 API 日志 | [logs.png](admin-screenshots/logs.png) | “暂无异常或 API 日志”及重置筛选引导 |
| 反馈管理 | [feedback.png](admin-screenshots/feedback.png) | “暂无反馈”及重置筛选引导 |

## 4. 失败态截图明细

| 页面名称 | 截图文件名 | 失败提示 |
|---|---|---|
| 管理员登录 | [login.png](admin-screenshots/login.png) | `ADMIN_AUTH_FAILED`：账号或密码错误，请重新输入 |
| 用户 / 会话记录 | [users.png](admin-screenshots/users.png) | `ADMIN_DATA_LOAD_FAILED`：用户列表加载失败，请稍后重试 |
| 行程记录 | [plans.png](admin-screenshots/plans.png) | `ADMIN_DATA_LOAD_FAILED`：行程记录加载失败，请稍后重试 |
| 行程详情 | [plan-detail.png](admin-screenshots/plan-detail.png) | `ADMIN_DATA_LOAD_FAILED`：冲突详情加载失败，基础行程信息仍保留 |
| 异常与 API 日志 | [logs.png](admin-screenshots/logs.png) | `ADMIN_DATA_LOAD_FAILED`：日志或日志详情加载失败，请稍后重试 |
| 反馈管理 | [feedback.png](admin-screenshots/feedback.png) | `ADMIN_DATA_LOAD_FAILED`：反馈列表加载失败，请稍后重试 |

## 5. 正常态截图清单

以下 6 张截图均包含对应页面的正常态内容：

1. [login.png](admin-screenshots/login.png)：登录表单、账号/密码/验证码输入及按钮。
2. [users.png](admin-screenshots/users.png)：用户列表与用户关联的会话记录。
3. [plans.png](admin-screenshots/plans.png)：行程列表及筛选条件。
4. [plan-detail.png](admin-screenshots/plan-detail.png)：行程基础信息、日程、景点、冲突和修改记录。
5. [logs.png](admin-screenshots/logs.png)：日志列表与日志详情预览。
6. [feedback.png](admin-screenshots/feedback.png)：反馈列表、详情和处理区域。

## 6. 文件核对

截图目录中实际存在的文件：

- `admin-screenshots/login.png`
- `admin-screenshots/users.png`
- `admin-screenshots/plans.png`
- `admin-screenshots/plan-detail.png`
- `admin-screenshots/logs.png`
- `admin-screenshots/feedback.png`

以上文件名与本地 `docs/prototype/admin-ui-design/` 目录中的真实文件名一致。