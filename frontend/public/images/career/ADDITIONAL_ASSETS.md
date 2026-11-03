# 맞춤 공고 추가 이미지 4종

생성 방식: 내장 `image_gen` 도구. 외부 기업의 실제 사진이 아닌 공통 업무 공간 이미지다.
기존 이음 디자인 규칙에 맞춘 자연광·화이트·우드 소재와 블루·라벤더·코랄 포인트를 사용한다.
사람·읽을 수 있는 글자·로고·워터마크를 제외했다. 기본 3:2 원본은 1536×1024px이며 업스케일하지 않았다.

WebP는 원본 크기를 유지해 품질 94로 인코딩했다. PNG 원본은 `originals/`에 보관한다.
카드의 `object-cover` 1.65:1 크롭을 고려한 중앙 구도이며, 새 파일 4종을 준비한 상태다.

| 장면 | 카드용 WebP | PNG 원본 |
| --- | --- | --- |
| 집중 업무 공간 | [workspace-focus.webp](./workspace-focus.webp) | [workspace-focus.png](./originals/workspace-focus.png) |
| 협업 공간 | [workspace-collaboration.webp](./workspace-collaboration.webp) | [workspace-collaboration.png](./originals/workspace-collaboration.png) |
| 디자인 스튜디오 | [workspace-atelier.webp](./workspace-atelier.webp) | [workspace-atelier.png](./originals/workspace-atelier.png) |
| 업무 라운지 | [workspace-lounge.webp](./workspace-lounge.webp) | [workspace-lounge.png](./originals/workspace-lounge.png) |

## 공통 생성 프롬프트

각 장면은 아래 공통 지시와 해당 Scene 지시를 합쳐 별도로 생성했다. 요청한 목표 크기는 3072×2048px이었고, 도구가 반환한 실제 원본은 1536×1024px이다.

Use case: photorealistic-natural. Asset type: high-resolution recruitment-card workspace photograph for the Korean IEUM career platform. Generate ONE standalone landscape 3:2 image, target 3072 x 2048 pixels if available. Premium architectural editorial photography, photorealistic with fine natural material textures, restrained bright composition, clean white space and warm wood, believable well-designed working environment, sophisticated rather than overly staged. Eye-level architectural camera, straight verticals, approximately 35mm lens, balanced exposure, realistic soft daylight and gentle shadows. Keep the central subject legible when cropped to a wider 1.65:1 card and when the image height shrinks on hover. Edge-to-edge photograph. No people, no readable words, no captions, no logos, no branding, no watermarks, no interface, no charts, no collage, no borders, no signs. A generic workspace, never imply it is a real named company's office. No over-saturated colors, heavy bloom, extreme wide-angle distortion, fisheye or glossy CGI rendering.

## workspace-focus

Scene: a quiet contemporary developer focus workspace viewed diagonally across a light oak desk, an elegant cobalt-blue fabric ergonomic chair as the main foreground accent, closed matte silver laptop and understated ceramic task lamp, translucent glass partition with soft architectural lines, pale cool-gray floor, large side windows showing only softly blurred neutral city architecture. A separate small desk in the background adds depth. Color palette: soft white, pale oak, muted sky blue, one deep cobalt accent. Crisp calm late-morning daylight. Distinct new composition with an intimate single-person desk, not a repeat of an open-plan rows-of-desks photograph.

## workspace-collaboration

Scene: an inviting contemporary collaboration room with a beautifully proportioned oval light oak meeting table, four muted lavender upholstered chairs, a single closed unbranded laptop and small plain notebook, soft white pendant lamp, gently curved frosted glass partition, daylight through tall windows. Warm gray acoustic wall panels and restrained greenery far in the background. Color palette: white, light wood, soft lavender and very subtle cobalt blue. Airy and credible, natural early-afternoon lighting, the table and two foreground chairs form the visual center. No screens displaying content and no whiteboards containing writing.

## workspace-atelier

Scene: a refined contemporary product-design studio with a pale oak worktable, textured soft-coral upholstered chair, sculptural matte white dome desk lamp, a neatly placed open sketchbook with completely blank pages and a small group of tactile pastel material samples. Off-white plaster wall with a simple lavender vertical panel, low shelving with plain unmarked books, and one out-of-focus window beyond. Warm natural side light and gentle shadows. Color palette: cream white, light oak, soft terracotta coral and dusty lavender. Tactile fabric, paper and timber details, professionally inhabited but uncluttered; different angle and furniture arrangement from the existing orange studio image.

## workspace-lounge

Scene: a premium yet welcoming office lounge for informal work, a softly curved pale oak communal workbench centered in the composition, two comfortable off-white textured fabric lounge chairs with a small muted-cobalt cushion, a discreet closed silver laptop, floor-to-ceiling windows and sheer light curtains, one tall plant in the distant background and a frosted lavender glass divider. White architectural surfaces with gentle rounded corners. Color palette: ivory, pale wood, subdued blue and a quiet hint of natural green. Clear soft morning light, calm optimistic professional atmosphere; distinctly an office shared workspace rather than a living room, hotel or cafe.

## workspace-focus 마무리 편집

노트북 덮개에 생긴 작은 마크만 내장 도구로 제거했다. 최종 전달 파일은 편집 결과다.

Use case: precise-object-edit. Edit only the supplied workspace photograph. Remove the small dark logo/emblem from the center of the closed silver laptop lid on the foreground desk, replacing it with seamless plain brushed silver metal matching the existing lighting and texture. Ensure all laptop lids in the image are completely blank and unbranded. Preserve everything else: exact composition, camera viewpoint, blue chair, furniture, desk objects, lighting, colors, glass, windows, background and photographic detail. Do not add or remove any other object. Keep the existing 3:2 landscape framing and native image dimensions. No text, logos, symbols or watermark anywhere.
