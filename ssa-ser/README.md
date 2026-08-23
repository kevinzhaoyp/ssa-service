# SSA-Ser 前端对接与第三方登录（前端开发说明）

本文档为前端团队提供基于 Vue 的实现说明，包含：
- 前端整体架构与契约
- Vue 实现要点与示例代码片段
- 微信三方登录（移动与扫码）接入说明
- 第三方应用（SSO）对接说明
- 安全、测试与部署建议

---

快速清单（Checklist）
- [ ] 确认后端行为：后端作为 OAuth2 客户端（向微信、GitHub 等获取用户信息）并负责颁发应用会话（推荐 httpOnly cookie 或短期 JWT）。
- [ ] 前端栈：Vue 3 + Vite + vue-router + Pinia + axios。
- [ ] 实现页面：登录页、回调页、用户信息页、错误页。
- [ ] 配置环境变量（API 地址、回调地址等）。
- [ ] 验证：OAuth2 重定向、回调处理、受保护接口访问。

---

一、总体架构与约定

- 前端（SPA）负责显示登录入口并发起第三方登录流程（通常通过重定向到后端的授权入口）；
- 后端（已有 ssa-ser）负责与第三方 OAuth 提供者通信（code -> access_token -> userinfo），在 DB 中查找/创建用户，并颁发应用会话；
- 推荐会话策略：后端设置 `Secure, httpOnly` 的会话 cookie（浏览器端 JS 无法读取），或后端返回短期 JWT 给前端（如果前端必须持有），但要注意 XSS 风险。

约定的后端端点（与后端保持一致）：
- GET /oauth2/authorization/{provider} — 发起授权（前端直接跳转到该 URL）
- GET /login/oauth2/code/{provider} — OAuth 回调（后端处理）
- GET /api/me — 返回当前登录用户信息（基于 cookie 或 Authorization: Bearer <token>）

---

二、前端技术栈建议

- Vue 3 + Vite
- 路由：vue-router
- 状态管理：Pinia
- HTTP：axios
- UI：Element Plus / Ant Design Vue / 任意组件库

环境变量（示例）

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080
VITE_APP_REDIRECT_URI=http://localhost:3000/oauth2/callback
```

---

三、页面与组件结构建议

- pages/
  - Login.vue — 登录页，含社交登录按钮
  - OAuthCallback.vue — 回调页，确认登录状态并跳转
  - UserProfile.vue — 显示用户信息
  - Error.vue — 错误展示
- components/
  - SocialLoginButtons.vue — 各三方登录按钮
  - WechatQrModal.vue — PC 扫码二维码弹窗
- stores/auth.js — Pinia 管理用户会话

---

四、关键前端实现细节与示例

1) 登录按钮（最简单、安全的做法：前端把浏览器重定向到后端授权入口）

```js
// Login.vue - methods
loginWithWechat() {
  window.location.href = `${import.meta.env.VITE_API_BASE_URL}/oauth2/authorization/wechat`;
}
```

2) 回调页（推荐后端设置 httpOnly cookie）：前端在回调页只需请求 `/api/me` 来确认是否登录，然后存储用户信息并跳转到首页。

```js
// OAuthCallback.vue (mounted)
axios.get(`${import.meta.env.VITE_API_BASE_URL}/api/me`, { withCredentials: true })
  .then(res => {
    authStore.setUser(res.data);
    router.replace('/');
  })
  .catch(() => router.replace('/login'));
```

备注：如果后端选择把 token 放到 URL（不推荐），前端需要尽快读取并移除 URL 中的 token，然后将其放到内存或发送给后端换取 httpOnly cookie。

3) Pinia 简单示例

```js
// stores/auth.js
import { defineStore } from 'pinia';
export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null, token: null }),
  actions: {
    setUser(u) { this.user = u; },
    setToken(t) { this.token = t; }
  }
});
```

4) axios 配置（如使用 Bearer token）

```js
axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;
axios.defaults.withCredentials = true; // 允许发送 httpOnly cookie
// 若使用 token 存在于 store
axios.interceptors.request.use(config => {
  const token = authStore.token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

---

五、微信三方登录（开发与对接说明）

微信登录常见场景：移动 H5 授权与 PC 扫码登录。

A) 预备工作（在微信平台）
- 在「微信公众平台」或「微信开放平台」注册应用，拿到 `AppID` 与 `AppSecret`；
- 在平台中配置授权回调 URL（Redirect URI），例如：`https://your-backend.example.com/login/oauth2/code/wechat`；
- 根据接入类型决定 scope（snsapi_userinfo 或 snsapi_base）。

B) 移动 H5（授权跳转）流程
- 前端：直接跳转到后端 `/oauth2/authorization/wechat`；
- 后端：构造微信授权 URL（包含 state），重定向到微信授权页面；
- 用户授权后，微信回调后端回调 URL，后端用 code 换取 access_token，并获取用户信息，最后创建/更新用户并设置会话（推荐 httpOnly cookie），随后重定向回前端应用。

C) PC 扫码登录流程（高层）
- 前端请求后端接口获取微信二维码 URL 或图片；
- 后端返回二维码地址，前端展示给用户扫码；
- 用户扫码并确认后，微信回调后端；
- 后端完成登录（code -> token -> userinfo），并可通过轮询或 websocket 通知前端扫码已登录，或在成功后将页面重定向到包含临时凭证的 URL（注意安全）。

D) 后端重要点（回调处理）
- 校验 `state` 参数以防 CSRF；
- 使用 `code` 请求微信 access_token：
  - `GET https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code`
- 若 `scope=snsapi_userinfo`，再请求用户信息：
  - `GET https://api.weixin.qq.com/sns/userinfo?access_token=ACCESS_TOKEN&openid=OPENID&lang=zh_CN`
- 在应用 DB 中查找 `provider=wechat && provider_id=openId` 的用户，存在则更新，不存在则注册；
- 生成会话并重定向到前端。

---

六、作为 SSO 授权方（第三方应用对接）

若你的系统要作为授权服务器供第三方应用使用，需实现标准 OAuth2 授权码流程：

1) 第三方注册流程
- 收集第三方信息：应用名、回调 URL 列表、联系人信息；
- 分配 `client_id` 与 `client_secret`（server side 保密）；

2) 必需的 endpoints
- 授权端点：`GET /oauth/authorize`（用户登录与同意）
- Token 端点：`POST /oauth/token`（第三方服务用 code 换取 access_token）
- 资源端点：`GET /api/userinfo`（验证 token 并返回用户信息）
- 可选：刷新 token、撤销 token、客户端管理页面

3) 推荐策略
- 强烈使用 HTTPS；
- access_token 有短期有效期（例如 1 小时）；
- 支持 refresh_token 并可撤销；
- 对 public clients 支持 PKCE；
- 使用 state 防止 CSRF；
- 回调 URL 精确匹配（避免 open redirect）。

---

七、安全建议（要点）

- state 必须在授权请求与回调中验证；
- 优先使用 httpOnly, Secure 的 cookie 保存会话；
- 避免把敏感 token 放在 URL 查询字符串；
- 对前端：避免在 localStorage 中长久存放 token（XSS 风险）；
- 对第三方接入：client_secret 不应出现在前端或公共仓库；
- 在服务器端对 refresh_token 和撤销做监控与黑名单策略。

---

八、测试要点（QA）

- 授权跳转是否能正确到微信/Github 等授权页面（检查 302）；
- 回调携带有效 code，后端能用 code 成功获取 access_token 与 userinfo；
- 登录成功后，前端能访问 `/api/me` 并拿到正确用户信息；
- 尝试拒绝授权和无效 code 场景，验证错误处理和前端显示；
- PC 扫码场景需测试二维码展示、扫码、轮询/通知机制。

---

九、部署与运行（快速命令）

前端（若使用 Vite）示例：

```bash
# 开发
npm install
npm run dev

# 打包
npm run build
```

后端（ssa-ser）示例：

```bash
# 构建并运行 (在本地环境已安装 Maven)
mvn -DskipTests -pl ssa-ser package
mvn -pl ssa-ser spring-boot:run -Dspring-boot.run.profiles=dev
```

---

十、后续可选实现（我可以帮助完成）

- 在前端仓库中创建 `Login.vue`、`OAuthCallback.vue` 的样板实现（包含 Pinia 状态管理与 axios 拦截）；
- 在 `ssa-ser` 中实现完整的微信 OAuth 后端流程，并在回调中设置 httpOnly cookie；
- 为 ssa-ser 实现一个轻量的第三方应用管理界面（client 注册、回调管理、撤销 token）；
- 编写端到端测试脚本（Selenium / Playwright）验证 OAuth 路径。

---

如果你希望我把这份 README 放到其它路径（比如仓库根目录 `README-frontend.md`），或需要我立即在前端代码里生成示例组件，请回复我你要的选项（例如：把样板组件放在 `frontend/` 里，或实现后端微信 OAuth）。

