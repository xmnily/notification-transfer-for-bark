中文

# 通知转发到 Bark

一款 Android 应用，可将通知、短信和通话事件以明文方式转发到 iPhone 上的 Bark。

## Screenshots

<table>
  <tr>
    <td align="center">
      <img src="docs/assets/app-setup.png" alt="初始设置" width="220"><br>
      <sub>初始设置</sub>
    </td>
    <td align="center">
      <img src="docs/assets/app-bark-settings.png" alt="Bark 设置" width="220"><br>
      <sub>Bark 设置</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/assets/app-transfer-settings.png" alt="转发设置" width="220"><br>
      <sub>转发设置</sub>
    </td>
    <td align="center">
      <img src="docs/assets/app-excluded-apps.png" alt="排除的应用" width="220"><br>
      <sub>排除的应用</sub>
    </td>
  </tr>
</table>

## 功能特性

- 仅向 `https://api.day.app/push` 转发（明文推送，无加密）
- 通知监听转发，支持按应用排除
- 可过滤持续性通知（常驻通知 / 前台服务通知），避免转发媒体控制、下载进度等噪音
- 微信 / QQ 聊天通知优化：联系人（群名）作为标题、消息内容作为正文，并按会话分组
- 重复通知过滤可整体开关，超时可配置为 1 到 120 秒，默认 5 秒
- 推送使用 expedited 任务立即执行，且不会为了获取图标而阻塞发送
- 短信与通话转发；在无法获得直接权限时回退为读取应用通知
- 按应用名匹配 App Store 公开图标并长期缓存，应用列表直接显示已缓存图标的地址
- 初始设置页提供转发总开关，关闭后所有通知、短信与通话都不再转发，也不会写入历史
- 支持按应用手动设置图标 URL，可单个刷新，也可一键刷新所有应用图标（包括尚未缓存的应用）
- 可将测试通知发送到 Bark（测试通知自带公开图标，便于验证 Bark 是否显示 `icon`），并在应用内查看发送日志
- 历史按全部 / 成功 / 失败 / 被过滤分类，失败与被过滤记录附带原因
- 每条历史记录附带处理日志（可用总开关关闭），包含过滤、图标、完整请求明文与端到端耗时
- 历史永久保留，可在历史页手动一键清除
- 可在最近任务列表中隐藏本应用，不影响后台通知转发
- 将公开图片 URL 转发到 Bark 的 `image` 字段

## 构建

本仓库支持 GitHub Actions 和本地 Gradle 构建。

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

## 在 GitHub 上构建

如果你想在 GitHub 上而不是本地构建 APK：

1. 将本仓库 Fork 到你自己的 GitHub 账号。
2. 打开你 Fork 仓库的 `Actions` 标签页，若 GitHub 提示则启用工作流。
3. 打开 `Android CI` 工作流并点击 `Run workflow`。
4. 运行结束后，下载 `app-debug-apk` 产物。
5. 将该产物中的 APK 安装到你的 Android 设备。

`app-debug-apk` 是基于 Fork 构建时的主要可安装产物，其中包含带时间戳的调试 APK，命名形如 `YYYYMMDD-HHMM-Notification-Transfer-for-Bark.apk`。同时也会上传 `app-release-unsigned-apk`，但它只是未签名的参考构建，需自行添加签名配置后才能安装。

如果你推送到 `main` 分支，GitHub Actions 还会自动创建一个 `Pre-release`，并上传同样的带时间戳调试 APK。自动标签格式为 `apk-YYYYMMDD-HHMM-run<RUN_NUMBER>-a<RUN_ATTEMPT>`。

## 安装

- 本地测试可安装 `app/build/outputs/apk/debug/app-debug.apk`。
- 若在 GitHub Actions 上构建，请从工作流运行结果中下载并安装 `app-debug-apk` 产物。
- 若使用 `main` 分支上的自动 GitHub `Pre-release` 流程，请安装该 Release 附带的带时间戳调试 APK。
- `app-release-unsigned.apk` 为有意未签名版本，需自行添加签名配置后才能安装。

## 关于侧载 APK 的通知访问说明

如果你从外部 APK 安装本应用，Android 可能会阻止通知访问，直到你手动允许“受限设置”。

1. 在应用内打开通知访问提示。
2. 在系统设置中打开“通知转发到 Bark”的应用详情页。
3. 打开右上角三点菜单，允许受限设置。
4. 返回并为该应用开启通知访问权限。

不同 Android 版本或设备厂商的措辞可能略有差异，但流程相同：先允许受限设置，再开启通知访问。

## Bark 设置

本应用以明文方式推送，无需在 Bark 中配置任何加密项。

<p>
  <table>
    <tr>
      <td align="center" valign="top">
        <a href="https://apps.apple.com/app/bark-custom-notifications/id1403753865">
          <img src="https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/27/b3/7f/27b37f52-a029-c5ad-8675-fa0cfb935801/Placeholder.mill/400x400bb-75.jpg" alt="Bark app icon from the App Store listing" width="88">
        </a>
        <br>
        <strong>Bark</strong>
        <br>
        <a href="https://apps.apple.com/app/bark-custom-notifications/id1403753865">
          <img src="https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg" alt="Download on the App Store" height="40">
        </a>
        <br>
        <sub>Bark GitHub 仓库</sub>
        <br>
        <a href="https://github.com/Finb/Bark">https://github.com/Finb/Bark</a>
      </td>
      <td align="center" valign="top">
        <a href="https://apps.apple.com/app/bark-custom-notifications/id1403753865">
          <img src="docs/assets/bark-app-store-qr.svg" alt="在 App Store 打开 Bark 的二维码" width="140">
        </a>
        <br>
        <sub>扫描二维码在 App Store 打开 Bark。</sub>
      </td>
    </tr>
  </table>
</p>

你可以直接把 `https://api.day.app/<你的 Key>` 粘贴到 Android 应用中，也可以只粘贴 Key 本身。扫描上方二维码可在 App Store 打开 Bark。

Bark 是由 Finb 开发的独立 iOS 应用。特别感谢 Bark 及其开发者 Finb 打造并分享了这款 iOS 应用，让本工作流成为可能。

## 图标与图片

- Bark 的 `icon` 和 `image` 需要公开可访问的 URL，本地文件无法被 iOS 端直接读取。
- 解析顺序：手动设置的图标 URL → 未过期的缓存 → 按应用名匹配 App Store 公开图标（`is1-ssl.mzstatic.com`）→ Play 商店网页。
- App Store 的匹配只在应用名与搜索结果吻合时采用，避免张冠李戴；商店里找不到的应用可以手动填写图标 URL。
- 「排除的应用」列表会直接显示每个应用当前使用的图标地址（手动 URL 或已缓存的图标 URL），可长按选中复制。
- 仅当原始通知本身就暴露了公开的 `http` 或 `https` URL 时，才会转发通知图片。
- 若无任何可用公开地址，推送仍会发送，只是不带图标。
- 本仓库不包含 Bark 的 UI 截图或应用美术素材。

### 刷新图标缓存

图标解析成功后会长期缓存，不会因为过期而丢掉已有图标；解析失败只记录尝试时间，1 小时后才会重试。若想立即重新抓取，可在「排除的应用」列表点击某个应用 →「编辑」→「刷新图标」：应用会跳过缓存与失败退避重新匹配（手动设置的图标 URL 不受影响，也不会触发缓存刷新）。若刷新失败，原有的缓存图标会保留。

「排除的应用」页顶部还有「一键刷新所有应用图标」按钮：会依次重新解析所有未手动填写图标 URL 的应用（包括尚未缓存图标的应用），并显示进度与成功/失败数量。

为了不影响推送速度，转发通知时只使用已缓存的图标；若还没有缓存，推送会先以无图标发出，图标会在后台解析并缓存供后续通知使用。

## 持续性通知与聊天通知优化

- **过滤持续性通知**：默认开启。带有 `FLAG_ONGOING_EVENT` / `FLAG_FOREGROUND_SERVICE` 的通知（音乐播放器、下载进度、常驻状态栏等）不会转发。
- **聊天通知优化**：默认开启，仅作用于微信（`com.tencent.mm`）、QQ（`com.tencent.mobileqq`）与 TIM（`com.tencent.tim`）的应用通知。
  - 当标题只是「微信 / QQ」这类通用名称时，会从正文的「发送人：内容」中提取发送人作为标题，正文只保留消息内容。
  - 群聊通知保持群名作为标题，正文保留「发送人：内容」，便于识别。
  - Bark 通知按「应用 · 会话」分组，同一会话的消息会聚合在一起。
- 以上两项均可在「过滤」页单独关闭。

## 重复通知超时

- 「过滤重复通知」是一个总开关，默认开启；关闭后不再做任何重复判断，所有通知都会转发。
- 重复过滤仅对应用通知生效。
- 超时比较的是归一化后的通知内容，而非 `subText` 或分类等噪声。
- 像 LINE 这类会合并通知的应用会复用额外的内容感知 Key，使得概述通知与子通知在描述同一条消息时可合并为一次转发。
- 短信和通话事件仍采用直接投递；仅当事件来自通知回退时，才附加一个较小的回退去重窗口。

## 历史记录与处理日志

- 历史页按「全部历史 / 成功 / 失败 / 被过滤 / 拦截规则」分类；被过滤包括持续性通知、排除的应用、命中拦截规则与重复通知。
- 失败与被过滤的记录会显示原因（例如 HTTP 错误、持续性通知、重复通知窗口）。
- 「历史」页顶部有「记录处理日志」开关，默认开启；关闭后新记录不再附带日志（已有日志保留）。
- 每条记录都可以展开「查看日志」，日志包含：收到通知、过滤原因、重复过滤状态、聊天优化分组、图标来源、请求地址、**完整请求明文**（`device_key`、`title`、`body`、`group`、`level`、`icon`、`image`）、HTTP 状态以及请求耗时和端到端耗时。
- 历史永久保留，不再按条数自动清理；需要清理时点击历史页右上角的「清除历史」，确认后会删除全部记录及日志。
- 关闭「记录处理日志」只影响之后的新记录，已有记录中的日志仍会保留。

## 转发总开关

- 初始设置页（主页）顶部提供「转发总开关」。
- 关闭后通知监听、短信与通话事件都会被直接忽略：不会发送请求，也不会写入历史。
- 重新开启后按各页的开关与过滤规则恢复转发。

## 最近任务

- 「在最近任务中隐藏本应用」开关开启后，本应用会从最近任务列表移除（通过 `ActivityManager.AppTask.setExcludeFromRecents`），不影响后台通知转发；再次打开请从桌面图标进入。

## 权限

本应用会请求：

- 通知访问权限，用于读取应用通知
- 短信权限，用于直接读取短信
- 电话状态与通话记录访问权限，用于直接获取通话事件
- 开机完成广播，用于重启后刷新应用列表

某些设备或安装方式会限制部分短信和通话权限。遇到这种情况时，应用会回退为转发来自默认短信或电话应用的通知。

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=notisfer-dev%2Fnotification-transfer-for-bark&type=Date)](https://www.star-history.com/?repos=notisfer-dev%2Fnotification-transfer-for-bark&type=date&legend=top-left)

## 许可证

本项目基于 MIT 许可证发布，详见 [LICENSE](LICENSE)。
