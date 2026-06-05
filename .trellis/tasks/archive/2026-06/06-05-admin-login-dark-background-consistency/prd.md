# 修复 Admin 登录页暗色模式首屏背景一致性

## Goal

修复 Admin 未登录态登录页在 dark 模式首屏顶部出现亮色/白色区域的问题，使登录页背景、文字、输入区域和整体首屏视觉与当前 UI preference theme 保持一致。

这是上一轮“前端主题和语言切换基线”的直接残留尾项，不开启新功能方向。

## Task Classification

- Type: Simple Task
- Area: frontend visual consistency
- Scope: Admin unauthenticated/login state and UI preference theme frame
- Hotfix vs structural: small structural UI baseline fix

选择 small structural UI baseline fix 的原因：

- 问题不是单个按钮颜色错误，而是登录首屏背景归属不完整。
- 应让外层 frame、未登录分支容器和 Ant Design token 同源，而不是只给 dark 模式添加局部硬编码补丁。
- 不引入新的主题状态、持久化 key 或第二套样式系统。

## Background

上一轮主题/语言基线已完成并记录：

- `frontend/src/app/providers/UIPreferenceProvider.tsx` 新增 frontend-only UI preference provider。
- 默认 theme 为 `dark`，默认 locale 为 `zh-CN`。
- 允许持久化 key 仅为 `sangui-admin-theme` 和 `sangui-admin-locale`。
- Ant Design `ConfigProvider` 已按 theme mode 使用 dark/default algorithm。
- 用户人工验收后发现残留视觉问题：Admin 用户 ID 登录页在 dark 模式下顶部仍有一块白色区域。

当前代码研究显示：

- `UIPreferenceProvider` 外层 frame 使用 `minHeight: '100vh'`，背景根据 `themeMode` 写为 `#141414` 或 `#f5f5f5`。
- `AdminShell` 未登录分支使用 `margin: '120px auto'`，其垂直 margin 可能使页面总高度超过外层 `minHeight`，超出区域暴露默认 `body` 白底。
- `frontend/src/styles/index.css` 中 `body` 只有 margin/font，`#root` 只有 `min-height: 100vh`，没有主题背景兜底。
- 登录分支当前没有显式使用 `theme.useToken()` 给容器/文本/输入区域建立 token 化背景边界。

## Requirements

- 登录页 dark 模式首屏和滚动溢出区域不得出现突兀白块。
- 登录页 light 模式仍保持正常的浅色背景和可读文本。
- 登录页应使用 Ant Design token 或现有 `themeMode` 派生的主题状态；优先使用 token，避免只有 dark 分支硬编码。
- `UIPreferenceProvider` 外层 frame 必须覆盖未登录页的可视高度和可能的垂直溢出区域。
- `AdminShell` 未登录分支应避免通过大额外边距制造 body/root 背景暴露；建议使用全高 flex/grid 布局或 token 化 wrapper。
- 保持当前 Admin 用户 ID 登录流程、校验规则、i18n key 和文案不变。
- 保持当前 UI preference 持久化边界不变，只允许使用已有 `sangui-admin-theme` 和 `sangui-admin-locale`。

## Non-Goals / Forbidden Scope

- 不修改后端 Java。
- 不修改任何 `/api/*` 或 `/v1/*` endpoint。
- 不修改 API payload、response DTO、frontend API types 或 backend VO/DTO。
- 不修改数据库 schema、migration、Redis、Docker、部署配置。
- 不新增权限、认证、登录持久化或真实 session 机制。
- 不新增 localStorage/sessionStorage key。
- 不持久化 admin user ID、API key、request log、runtime evidence、prompt 或任何服务端数据。
- 不新增 i18n key，除非实现端发现登录页仍有未纳入字典的硬编码文案；当前研究未发现必须新增。
- 不扩大到全站视觉重构、组件库替换、自动化视觉测试框架或新页面。

## API / Command / Payload Contract

Not applicable. This task must not change API routes, commands, payload fields, DTOs, VO fields, backend errors, database columns, or environment variables.

Validation commands required after implementation:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Manual smoke:

```text
Open Admin frontend login page.
Verify dark mode first-screen top/background has no bright white block.
Toggle to light mode and verify the login page remains visually normal/readable.
Toggle back to dark mode and verify the issue does not return.
```

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Dark mode default, unauthenticated login page | No white/bright strip at top or around first viewport; text/input/button remain readable | Browser visual check on login page |
| Light mode, unauthenticated login page | Shallow light background remains normal; text/input/button remain readable | Browser visual check after theme toggle |
| Invalid stored theme value | Existing provider behavior resets to default dark; no new fallback behavior added | Existing `loadTheme()` contract remains intact |
| Admin user ID empty/invalid | Existing validation message and disabled/connect behavior remain unchanged | Login form behavior unchanged |
| Admin user ID valid | Existing transition into Admin shell remains unchanged | Shell opens existing default page |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | Fresh load with default dark theme shows a fully dark/token-consistent first viewport; no body/root white background is visible even around the login panel; login works as before. |
| Base | User switches to light theme, refreshes or returns to the login page, and sees the intended light theme without dark-only hardcoded artifacts. |
| Bad | Fix only paints a nested login card while the page top/body remains white, hardcodes dark-only colors that break light mode, adds new persisted keys, changes login state semantics, or touches backend/API/DB files. |

## Relevant Specs

- `.trellis/spec/frontend/index.md`: frontend pre-development checklist and admin console boundary.
- `.trellis/spec/frontend/directory-structure.md`: keep layout/provider changes in existing frontend structure and avoid new generic feature spread.
- `.trellis/spec/frontend/component-guidelines.md`: use UI library tokens/components, keep admin UI compact and clear, avoid decorative/marketing layout.
- `.trellis/spec/frontend/state-management.md`: UI preference provider contract, default dark theme, allowed persisted keys, and secret/runtime-evidence storage prohibition.
- `.trellis/spec/frontend/quality-guidelines.md`: visual design, accessibility, secret safety, and completion expectations.
- `.trellis/spec/guides/index.md`: shared thinking triggers; confirms no cross-layer trigger for this visual-only task.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: reuse existing theme/provider/shell patterns instead of duplicating theme mechanisms.
- `.trellis/spec/sangui-rag-gateway.md`: product boundary; Admin console remains a lightweight configuration UI for the RAG gateway.

## Code Patterns Found

- `frontend/src/app/providers/UIPreferenceProvider.tsx`: owns `themeMode`, `locale`, Ant Design `ConfigProvider`, and outer app frame.
- `frontend/src/components/layout/AdminShell.tsx`: owns unauthenticated Admin user ID entry branch, authenticated shell layout, theme toggle, locale select, and `theme.useToken()`.
- `frontend/src/styles/index.css`: global body/root baseline; currently no theme-aware background.
- Existing validation commands live in `frontend/package.json`: `typecheck` and `build`; there is no `lint` script.

## Files Likely To Modify

- `frontend/src/app/providers/UIPreferenceProvider.tsx`
  - Ensure app frame background is token/theme-consistent and covers at least full viewport plus content overflow.
  - Consider using `theme.token` values through Ant Design theme config or a token-aware inner component if necessary.
- `frontend/src/components/layout/AdminShell.tsx`
  - Replace unauthenticated branch's margin-based layout with full-height tokenized layout.
  - Use `theme.useToken()` for login surface background/text spacing where appropriate.
  - Preserve login behavior and existing i18n keys.
- `frontend/src/styles/index.css`
  - Optional only if needed: ensure `html`, `body`, and `#root` height/background do not expose default white outside the React frame.
  - Do not add a second source of truth for theme colors.

## Implementation Approach

1. Inspect whether the white strip is caused by login branch vertical margin, root/body background, or both.
2. Prefer a single theme-owner fix:
   - Make provider frame or login wrapper occupy `minHeight: '100vh'` without relying on large vertical margins.
   - Use Ant Design token values (`token.colorBgLayout`, `token.colorBgContainer`, `token.colorText`, etc.) for surfaces.
3. Keep login UI compact and operational:
   - centered form area is acceptable,
   - no hero page,
   - no nested cards or decorative background.
4. Verify light/dark visually and with frontend typecheck/build.

## Required Tests

Automated:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Manual:

- Open the frontend login page unauthenticated in dark mode and confirm no white/bright block at the top or around viewport edges.
- Toggle to light mode and confirm layout/readability remains normal.
- Toggle back to dark mode and confirm the fix persists.
- Enter a valid Admin user ID and confirm navigation into the shell still works.

## Planning Self-Check

- Acceptance criteria defined: yes.
- Forbidden modification scope defined: yes.
- Expected implementation files listed: yes.
- Required tests listed: yes.
- Specific guideline files read, not only indexes: yes.
- Open questions requiring user confirmation: no.
- API / DB / frontend types / DTO alignment required: no; explicitly forbidden for this task.
