# 2026-04-01 Server Details Upload + Description + Chunk Speed Update

## User Request
- Make the shown card/dialog match Duolingo vibe.
- In Server Details, replace "Server photo URL" with a Duolingo-style upload button that picks images from phone storage.
- Add a "Server description" field below the server name.
- Remove duplicate options from Settings so these values are not repeated.
- Improve new-server chunk loading/generation speed (new worlds feel slow, while imported backups feel fine).
- Create this markdown before implementation.

## Implementation Plan
1. Restyle world add dialog/card in Duolingo style (rounded-square, stronger hierarchy, Duolingo-style action buttons).
2. Extend Server Details model/state:
   - add server description persistence per world,
   - keep server name + photo persistence,
   - show description under title in home card.
3. Replace photo URL text input with image upload picker:
   - add image picker button in Server Details,
   - copy selected image to app storage,
   - save local URI/path and use it in preview/card.
4. Remove duplicated settings fields likely overlapping with Server Details (description/MOTD-facing controls).
5. Add chunk-generation speed optimization for fresh worlds via startup/server properties tuning.
6. Build and install on connected phone; report result.

## Notes
- Keep Plugins and Resource Packs flows unchanged.
- Keep visuals consistent with existing green Duolingo-like language.
