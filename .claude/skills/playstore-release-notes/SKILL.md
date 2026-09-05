---
name: playstore-release-notes
description: "Generates and translates Google Play Store update/release notes into the 5 supported app languages (en-US, de-DE, es-ES, ru-RU, zh-CN) in XML format and saves directly to playstore_release_notes.txt."
---

# /playstore-release-notes - Play Store Release Notes Localizer

This skill automates formatting and translating Play Store "What's New" release notes into all 5 languages supported by the PocketCraft Android app.

---

## 1. Supported Locales

PocketCraft supports exactly 5 language tags:
1. `en-US` - English (United States)
2. `de-DE` - German (Germany)
3. `es-ES` - Spanish (Spain / Latin America)
4. `ru-RU` - Russian
5. `zh-CN` - Chinese (Simplified)

---

## 2. Constraints & Formatting Rules

> [!WARNING]
> **500-Character Strict Limit**
> Google Play Console rejects release note entries exceeding **500 characters** per language tag. Keep bullet points concise, impactful, and punchy.

### Required Output Format
Return the localized release notes wrapped in XML-style locale tags:

```xml
<en-US>
- Added native Bedrock (PowerNukkitX) server support
- Fixed Bedrock Floodgate authentication keys & ViaVersion downloads
- Improved relay ping stability and server startup UI progress
- General performance and battery optimizations
</en-US>
<de-DE>
- Native Bedrock-Serverunterstützung (PowerNukkitX) hinzugefügt
- Fehler bei Floodgate-Authentifizierung und ViaVersion behoben
- Relay-Ping-Stabilität und Serverstart-Fortschritt verbessert
- Allgemeine Leistungs- und Akkuoptimierungen
</de-DE>
<es-ES>
- Añadido soporte nativo para servidores Bedrock (PowerNukkitX)
- Corrección en autenticación de Floodgate y descargas de ViaVersion
- Mayor estabilidad en el ping del relay y progreso de inicio
- Optimizaciones de rendimiento y batería
</es-ES>
<ru-RU>
- Добавлена поддержка серверов Bedrock (PowerNukkitX)
- Исправлена авторизация Floodgate и загрузка ViaVersion
- Повышена стабильность пинга реле и синхронизация запуска
- Оптимизация производительности и энергопотребления
</ru-RU>
<zh-CN>
- 新增原生基岩版 (PowerNukkitX) 服务器支持
- 修复 Floodgate 身份验证密钥与 ViaVersion 自动下载
- 提升中继 Ping 稳定性并同步启动进度界面
- 通用性能与电池续航优化
</zh-CN>
```

---

## 3. Execution Workflow

When the user asks to generate or update release notes:
1. Parse the user's release highlights, bug fixes, or recent git commits (`git log -n 5 --oneline`).
2. Draft crisp English (`en-US`) bullet points ensuring under 500 characters total.
3. Translate accurately into `de-DE`, `es-ES`, `ru-RU`, and `zh-CN`.
4. Output the complete XML-tagged block.
5. Save/overwrite the result directly to [`playstore_release_notes.txt`](file:///home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/playstore_release_notes.txt) in the project root.
