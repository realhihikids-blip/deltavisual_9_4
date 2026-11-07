# DeltaVisuals 0.9.4

**RU** · [English below](#english)

Клиентский визуальный мод для Minecraft Java Edition на Fabric. Только отрисовка, звук и интерфейс: без автоматических атак, увеличения дальности,
изменения урона и других преимуществ. **Частицы Minecraft не используются**: все эффекты нарисованы цельной геометрией через собственные `RenderPipeline` / `RenderLayer`,
текстуры генерируются кодом.

## Совместимость

| | |
|---|---|
| Minecraft | 1.21.11 (Java Edition) |
| Fabric Loader | 0.18.4 и новее |
| Fabric API | 0.141.1+1.21.11 и новее для 1.21.11 |
| Java | 21 |
| Сторона | только клиент |

## Функции

* **Target ESP**: 8 режимов (Arcs, Petals, Rings, Mechanical, Lightning, Saturn, Ghosts, Helix), цвет Static / Gradient / Rainbow / Health / Fire / Neon, свечение, «сквозь стены».
* **Trails / Blink**: лента за игроком, сглаживание, сужение и затухание к хвосту; режимы Vertical, Horizontal, Spiral, Wave; настройки ширины и длины.
* **Kill Effect**: Decay, Singularity, Abyss.
* **Hit Effects**: звёзды, черепа, сердца, осколки кристалла, молнии, ударная волна; усиленный вариант при убийстве.
* **Damage Indicators**, **Block Overlay**.
* **Ночное зрение** (Visuals → Night Vision): мир полностью освещён, как с зельем, но только у вас на экране (эффект ставится локально, на сервер ничего не отправляется). Без мерцания и таймера; есть опция скрыть значок эффекта.
* **Custom Sky**: сгенерированное небо (Space, Stormy Sunset, Pink Sunset, Clear Day, Aurora, Black Hole, Sunset Sky, Night Sky, Fantasy Sky, Custom Sky, Gradient Sky, Custom Color Sky), цвета, градиент, облака, звёзды, интенсивность; модуль **Sky Preset Switch** с отдельным хоткеем.
* **Hit Sounds** (категория Combat Visuals): Minecraft Bell, Hit Sound 1–4, собственный звук `config/delta_visuals/sounds/custom.wav`; громкость, высота, задержка, кнопка Play Sound; звук только при удачном ударе.
* **Item Position**: положение, поворот и масштаб предмета в руках отдельно для правой и левой руки и для типов предметов; 23 пресета.
* **HUD**: Info, Armor, Watermark, редактор HUD.
* **Метки** (World → Markers): клавиша (по умолчанию **K**) ставит метку там, где вы стоите; повторное нажатие рядом с меткой удаляет её. Метка по координатам: `/delta mark X Y Z [название]` или кнопка «Метка из буфера обмена» (понимает «100 64 -200» и текст из F3+C). Рисуется световым лучом, кольцом и кристаллом, плюс плашка с названием и расстоянием, которая прилипает к краю экрана, если метка за спиной. Метки видны только вам, хранятся по серверам и измерениям в `config/delta_visuals/markers.json`.
* **Пользователи Delta** (Player → Delta Users): логотип-кристалл рядом с ником игроков, у которых тоже стоит мод, в табе и над головой.
* **Меню** (Right Shift): у каждой функции переключатель, хоткей и настройки; темы; профили; ключи для обмена настройками (Copy Share Key / Load Key From Clipboard).
* **Конфигурация**: JSON-профили в `.minecraft/config/delta_visuals/profiles/`.

## Что нового в 0.9.4

* **Target HUD** (HUD): плашка с тёмным полупрозрачным фоном и скруглёнными углами: слева голова скина, справа ник и «HP: N» (целое число, обновляется в реальном времени). Появляется, когда прицел наведён на игрока, и плавно гаснет. Работает и на невидимых игроков. Двигается в редакторе HUD.
* **Hitbox Fill** (Combat Visuals): контур и полупрозрачная заливка хитбоксов игроков и мобов (отдельные переключатели). Заливка белая, а в зоне удара красная. Дистанция считается как на сервере: от глаз до ближайшей точки хитбокса, радиус берётся из реального радиуса атаки игрока (или задаётся вручную) плюс запас. На границе нет мерцания (гистерезис и плавный переход цвета).
* **Target ESP**: полностью удалён код, связанный с невидимостью (проверки эффекта и опция «Ignore Invisible Players»). Остальная логика прежняя.

## Что нового в 0.9.3

* **Projectile FX** (Combat Visuals): эффект при броске эндер-жемчуга (Rings, Sparks, Vortex) и при выстреле из лука (Flash, Shockwave, Streaks); шлейф за летящим жемчугом и стрелой; **предсказанная траектория** с меткой приземления, пока вы держите жемчуг или натягиваете лук (физика ванили: сопротивление 0,99, гравитация 0,03 / 0,05, учитывается ваше движение). По умолчанию показывается только ваше; режим «Все» включается отдельно. Эффекты гаснут рядом с камерой и у прицела, число одновременных ограничено.
* **Target ESP** больше не метит игроков (и мобов) под эффектом невидимости, в том числе если невидимым стала уже выбранная цель. Опция «Ignore Invisible Players». Ленты **Trails** тоже не рисуются за невидимыми игроками.
* **World Time** (Visuals): время суток только на вашем экране: Fixed (рассвет, утро, полдень, закат, ночь, полночь или своё), Cycle (бегущие сутки с настраиваемой скоростью), Real Clock (по часам компьютера). Рисуется небо с градиентом по времени, солнцем, луной, облаками и звёздами; днём мир осветляется локальным ночным зрением. Реальное освещение сервера не меняется: настоящий день ночью не сделать, а реальную ночь днём осветлить можно.
* **Sky Color** (Visuals): окрашивает ванильное небо (красное, фиолетовое, радужное и т. д.) прозрачным куполом; солнце, луна, звёзды и облака остаются. Режимы Static / Gradient / Rainbow, готовые цвета, сила 5–100%.
* **Air Particles** (World): светлячки, пылинки, снег, угольки, искорки, лепестки. Рисуются геометрией, не частицами Minecraft. В PvP не мешают: пусто вокруг камеры, гаснут у прицела, исчезают в бою (вас ударили или вы ударили) и рядом с другими игроками (невидимые не учитываются).
* Облака ванили теперь скрываются с учётом нескольких модулей сразу (Custom Sky и World Time не мешают друг другу).

## Что нового в 0.9.2

* **Убран режим «Сквозь стены»** из всех модулей (Target ESP, Trails, Kill Effect, Hit Effects, Totem Soul, Block Overlay, Метки). Все эффекты рисуются только как обычные объекты мира.
* **Potion Effects** (HUD): ваши эффекты зелий с уровнем, временем и полоской.
* **Cooldowns** (HUD): перезарядки жемчуга, золотого яблока, щита, тотема, плода хоруса возле прицела. Длительность определяется по скорости спада.
* **Totem Count** (HUD): сколько тотенов осталось (инвентарь и вторая рука).
* **Totem Pops** (HUD): сколько тотенов потратил каждый игрок рядом; сброс по таймеру или при смерти.
* **Zoom** (Controls): плавный зум по удержанию клавиши (по умолчанию C).
* **Screen Animations** (Interface): плавное открытие инвентаря и меню.
* **Item Motion** (World): выброшенные предметы вращаются и покачиваются медленнее.

## Что нового в 0.9.1

* **Душа тотема** (Combat Visuals → Totem Soul). Когда у игрока срабатывает тотем, из него вылетает душа. Пять вариантов: Ghost (призрак), Wisp (огонёк), Spiral, Ascend (дух отделяется от тела), Burst. Настройки: длительность, размер, высота подъёма, цвет, свечение. Режим «PvP Comfort»: душа становится прозрачной возле прицела и возле камеры, ограничено число одновременных душ и дальность. Опции: показывать свои срабатывания, только цели, которых вы били. Кнопка «Предпросмотр» показывает эффект перед вами.
* **Kill Effect.** Модуль больше не отключается навсегда из-за одного сбойного кадра или тика (выключается только после 3 сбоев подряд, в лог пишется причина). В Target ESP добавлена опция «Detect Instant Removal»: если сервер удаляет убитого игрока мгновенно и клиент не успевает увидеть 0 здоровья, убийство всё равно засчитывается.

## Что нового в 0.9.0

* **Метки.** Бинд ставит метку возле вас, команда `/delta mark X Y Z [название]` ставит по координатам. Остальные команды: `/delta mark here|clip|list|clear|remove N`. Настройки: куда ставить по бинду (под ногами / куда смотрю), радиус удаления, максимум меток, цвет, луч, «сквозь стены».
* **Логотип у игроков с Delta Visuals** в табе и над головой. Игровой сервер не сообщает клиентам о модах друг друга, поэтому используется маленький сервер присутствия `presence-server/server.py` (Python, без зависимостей): `python3 server.py`, затем адрес в `config/delta_visuals/presence.json` → `{"url": "http://ВАШ_IP:8787"}`. Клиент раз в 20 секунд отправляет UUID игроков из таба (и свой, если включено «Сообщать другим, что я с модом») и получает, кто из них онлайн с модом. Передаются только UUID. Без сервера работает ручной список: `/delta user add НИК`.
* Логотип в шрифте: `assets/delta_visuals/font/icons.json` + `textures/font/logo.png` (символ U+E000).

## Что нового в 0.8.0

* **Меч не улетает при ударе.** Положение предмета теперь применяется после ванильной анимации замаха, поэтому замах остаётся ванильным. У Item Position безопасные пределы (позиция ±0.7 / −0.6…0.6 / −0.8…0.5, поворот ±90 / ±110 / ±90°, масштаб 0.3–1.6), так что предмет не уходит за экран.
* **Новый интерфейс.** Мягкая тень и тонкая светлая рамка меню, скруглённые углы 12 px, полупрозрачные панели, логотип-кристалл в шапке, аккуратные HUD-панели с акцентной полосой.
* **Язык English / Русский** (Тема → Язык), переключается сразу без перезапуска. Тексты лежат в `assets/delta_visuals/lang/en_us.json` и `ru_ru.json` (ключ = английский текст).
* **Шрифт интерфейса** Carlito (SIL OFL 1.1, поддерживает кириллицу) через font provider: `assets/delta_visuals/font/ui.json` + `carlito-regular.ttf`. Если файла нет или глиф отсутствует, подставляется стандартный шрифт Minecraft. Переключатель: Тема → UI Font. Чтобы взять другой шрифт (например Inter или Roboto), положите TTF в ту же папку и замените имя файла в `ui.json`.
* **Иконка** мода и логотип в меню.
* **Косметика** (категория Cosmetics → модуль Cosmetics): плащ (Classic, Royal, Galaxy, Flame, Frost), крылья (Angel, Dragon, Crystal, Butterfly), шапка (Top Hat, Wizard Hat, Crown, Halo, Cat Ears). Каждый элемент включается отдельно, есть размер, прозрачность и свои цвета. Модели целиком из геометрии, без текстур; видны только вам.

## Что изменилось в 0.7.0

Отдельной версии 6.5 в проекте нет: последняя собранная мной версия это 0.7.0 (до неё была 0.6.0).

* Добавлены Custom Sky, Sky Preset Switch, Hit Sounds (новая категория Combat Visuals) и Item Position.
* В меню: заголовки групп, выбор из списка «чипами» для настроек с большим числом вариантов.
* Миксин `HeldItemRendererMixin` для Item Position (конфиг `delta_visuals.mixins.json`, `required: false`).
* В 0.6.0 были добавлены Hit Effects и более тонкие и короткие Trails (по умолчанию ширина 0.3, длина 12 тиков).

Известные ограничения: туман местности остаётся ванильным (у края дальности прорисовки возможна полоса цвета тумана); пока включён Custom Sky, ванильные облака скрываются видеонастройкой и возвращаются при выключении неба или выходе из игры.

## Установка

1. Установите Fabric Loader для Minecraft 1.21.11 (https://fabricmc.net/use/) и Fabric API.
2. Скопируйте `delta-visuals-0.9.4.jar` в папку модов: `%appdata%\.minecraft\mods` (Windows), `~/Library/Application Support/minecraft/mods` (macOS), `~/.minecraft/mods` (Linux).
3. Запустите игру профилем Fabric 1.21.11 и нажмите **Right Shift**.

## Сборка из исходников

1. Установите JDK 21.
2. В корне проекта выполните: Windows `gradlew.bat build`, macOS / Linux `./gradlew build` (на macOS / Linux один раз `chmod +x gradlew`).
3. Готовый мод: `build/libs/delta-visuals-0.9.4.jar` (файлы `-sources` и `-dev` не нужны).
4. Если в проекте нет `gradle/wrapper/gradle-wrapper.jar`, один раз выполните `gradle wrapper --gradle-version 9.2.0` (нужен установленный Gradle).

Сборка на GitHub: workflow **build** запускается при каждом push; jar лежит в Actions → запуск → Artifacts → `delta-visuals-jar`.
Релиз: тег `v0.9.4` (`git tag v0.9.4 && git push origin v0.9.4`) запускает workflow **release**, который прикладывает jar к GitHub Release.

## Благодарности и лицензия

Благодарность автору(ам) оригинального Delta Visuals за идею и вдохновение. Имя автора укажите здесь сами: в проекте оно нигде не записано.
Код этого проекта написан заново; текстуры и ресурсы чужих пакетов не копировались.

Лицензия: **All Rights Reserved** (как указано в `fabric.mod.json`). Если хотите открытую лицензию (например MIT), замените значение `license` в `src/main/resources/fabric.mod.json` и добавьте файл `LICENSE`.

---

## English

<a id="english"></a>

A client-side visual mod for Minecraft Java Edition (Fabric). Rendering, sound and UI only: no auto-attack, no extra reach, no damage changes, no gameplay advantages.
**No Minecraft particles are used**: every effect is solid geometry drawn through the mod's own `RenderPipeline` / `RenderLayer`, and all visuals are generated in code (no borrowed textures).

## Compatibility

| | |
|---|---|
| Minecraft | 1.21.11 (Java Edition) |
| Fabric Loader | 0.18.4 or newer |
| Fabric API | 0.141.1+1.21.11 or newer for 1.21.11 |
| Java | 21 |
| Side | client only |

## Features

* **Target ESP**: 8 modes (Arcs, Petals, Rings, Mechanical, Lightning, Saturn, Ghosts, Helix), Static / Gradient / Rainbow / Health / Fire / Neon colours, glow, see-through-walls.
* **Trails / Blink**: ribbon behind the player with smoothing, taper and alpha fade; Vertical, Horizontal, Spiral and Wave modes; width and length settings.
* **Kill Effect**: Decay, Singularity, Abyss.
* **Hit Effects**: stars, skulls, hearts, crystal shards, lightning, shockwave; a bigger variant on kills.
* **Damage Indicators**, **Block Overlay**.
* **Night Vision** (Visuals -> Night Vision): the world is fully lit like with the potion, but only on your screen (the effect is applied locally, nothing is sent to the server). No flicker or timer; an option hides the effect icon.
* **Custom Sky**: generated sky (Space, Stormy Sunset, Pink Sunset, Clear Day, Aurora, Black Hole, Sunset Sky, Night Sky, Fantasy Sky, Custom Sky, Gradient Sky, Custom Color Sky), colours, gradient, clouds, stars, intensity; **Sky Preset Switch** module with its own hotkey.
* **Hit Sounds** (Combat Visuals): Minecraft Bell, Hit Sound 1-4, your own `config/delta_visuals/sounds/custom.wav`; volume, pitch, delay, Play Sound button; plays only when a hit really lands.
* **Item Position**: position, rotation and scale of the held item, separately for each hand and item type; 23 presets.
* **HUD**: Info, Armor, Watermark, HUD editor.
* **Markers** (World -> Markers): a key (default **K**) puts a marker where you stand; pressing it next to a marker removes it. By coordinates: `/delta mark X Y Z [name]` or the "Mark From Clipboard" button (understands "100 64 -200" and the F3+C text). Drawn as a light beam, a ring and a crystal, plus a name / distance tag that sticks to the screen edge when the marker is behind you. Only you see them; they are stored per server and dimension in `config/delta_visuals/markers.json`.
* **Delta Users** (Player -> Delta Users): the crystal logo next to the nick of players who also use the mod, in the tab list and above their head.
* **Menu** (Right Shift): every feature has a toggle, a hotkey and settings; themes; profiles; share keys (Copy Share Key / Load Key From Clipboard).
* **Config**: JSON profiles in `.minecraft/config/delta_visuals/profiles/`.

## What's new in 0.9.4

* **Target HUD** (HUD): a dark translucent rounded plate: the skin head on the left, the name and "HP: N" (whole number, live) on the right. Shown while the crosshair is on a player, fades out afterwards. Also works on invisible players. Movable in the HUD editor.
* **Hitbox Fill** (Combat Visuals): hitbox outline and translucent fill for players and mobs (separate switches). White fill, red inside hit range. The distance is measured like the server does it: from your eyes to the closest point of the hitbox, with your real interaction range (or a manual value) plus a margin. No flicker at the border (hysteresis and a smooth colour blend).
* **Target ESP**: all invisibility code removed (the effect checks and the "Ignore Invisible Players" option). The rest of its logic is unchanged.

## What's new in 0.9.3

* **Projectile FX** (Combat Visuals): a burst when an ender pearl is thrown (Rings, Sparks, Vortex) and when a bow is shot (Flash, Shockwave, Streaks); a ribbon behind flying pearls and arrows; a **predicted path** with a landing marker while you hold a pearl or draw a bow (vanilla physics: drag 0.99, gravity 0.03 / 0.05, your own motion is added). Only your own projectiles by default; "Everyone" is a separate option. Effects fade next to the camera and the crosshair and their number is limited.
* **Target ESP** no longer marks players (or mobs) under the Invisibility effect, also when an already chosen target turns invisible. Option "Ignore Invisible Players". **Trails** are not drawn behind invisible players either.
* **World Time** (Visuals): a time of day only on your screen: Fixed (sunrise, morning, noon, sunset, night, midnight or custom), Cycle (a running day with adjustable speed), Real Clock (your computer clock). Draws a sky whose gradient follows the clock, with sun, moon, clouds and stars; by day the world is brightened with the local Night Vision effect. The server's real lighting is not changed: a real day cannot be turned into night, but a real night can be lit like day.
* **Sky Color** (Visuals): tints the vanilla sky (red, purple, rainbow...) with a see-through dome; sun, moon, stars and clouds stay. Static / Gradient / Rainbow, colour presets, strength 5-100%.
* **Air Particles** (World): fireflies, dust, snow, embers, sparkles, petals. Solid geometry, not Minecraft particles. PvP safe: clear around the camera, fades near the crosshair, disappears in a fight (you were hit or you hit someone) and near other players (invisible ones do not count).
* Hiding the vanilla clouds now keeps track of several modules (Custom Sky and World Time do not fight over it).

## What's new in 0.9.2

* **"Through Walls" removed** from every module. Effects are drawn as normal world objects only.
* **Potion Effects**, **Cooldowns**, **Totem Count** and **Totem Pops** HUD elements.
* **Zoom** (hold key, default C), **Screen Animations**, **Item Motion**.

## What's new in 0.9.0

* **Markers.** The bind puts a marker next to you, `/delta mark X Y Z [name]` puts one at coordinates. Other commands: `/delta mark here|clip|list|clear|remove N`. Settings: where the key places it (at my feet / where I look), removal radius, max markers, colour, beam, see-through-walls.
* **Logo next to players who use Delta Visuals**, in the tab list and above their head. A game server does not tell clients about each other's mods, so a small presence server is used, `presence-server/server.py` (Python, no dependencies): run `python3 server.py`, then put its address into `config/delta_visuals/presence.json` as `{"url": "http://YOUR_IP:8787"}`. Every 20 s the client sends the UUIDs of the players in the tab list (and its own if "Announce Me To Other Users" is on) and gets back who is online with the mod. Only UUIDs are sent. Without a server the manual list works: `/delta user add NICK`.
* The logo is a font glyph (U+E000): `assets/delta_visuals/font/icons.json` + `textures/font/logo.png`.

## What's new in 0.8.0

* **The sword no longer flies away on a hit.** The item transform is now applied after the vanilla swing animation, so the swing stays vanilla. Item Position has safe limits (position +-0.7 / -0.6..0.6 / -0.8..0.5, rotation +-90 / +-110 / +-90 deg, scale 0.3-1.6), so the item stays on screen.
* **New interface.** Soft shadow and a thin light border for the menu, 12 px rounded corners, translucent panels, crystal logo in the header, tidy HUD panels with an accent bar.
* **English / Russian language** (Theme -> Language), applied instantly. Texts are in `assets/delta_visuals/lang/en_us.json` and `ru_ru.json` (the key is the English text).
* **Interface font** Carlito (SIL OFL 1.1, Cyrillic) through a font provider: `assets/delta_visuals/font/ui.json` + `carlito-regular.ttf`. If the file or a glyph is missing, the vanilla Minecraft font is used. Toggle: Theme -> UI Font. To use another font (Inter, Roboto...), put the TTF in the same folder and change the file name in `ui.json`.
* **Icon** of the mod and a logo in the menu.
* **Cosmetics** (category Cosmetics -> module Cosmetics): cape (Classic, Royal, Galaxy, Flame, Frost), wings (Angel, Dragon, Crystal, Butterfly), hat (Top Hat, Wizard Hat, Crown, Halo, Cat Ears). Each one has its own toggle, size, opacity and custom colours. Models are pure geometry, no textures; visible only to you.

## What changed in 0.7.0

There is no version 6.5 in this project: the latest version I built is 0.7.0 (0.6.0 came before it).

* Added Custom Sky, Sky Preset Switch, Hit Sounds (new Combat Visuals category) and Item Position.
* Menu: group headers and chip-style pickers for settings with many options.
* `HeldItemRendererMixin` for Item Position (`delta_visuals.mixins.json`, `required: false`).
* 0.6.0 added Hit Effects and thinner, shorter Trails (default width 0.3, length 12 ticks).

Known limits: terrain fog stays vanilla (a band of fog colour may show at the render-distance edge); while Custom Sky is on, vanilla clouds are hidden through the video option and restored when the sky is turned off or the game exits.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11 (https://fabricmc.net/use/) and Fabric API.
2. Copy `delta-visuals-0.9.4.jar` into the mods folder: `%appdata%\.minecraft\mods` (Windows), `~/Library/Application Support/minecraft/mods` (macOS), `~/.minecraft/mods` (Linux).
3. Launch the Fabric 1.21.11 profile and press **Right Shift**.

## Building from source

1. Install JDK 21.
2. In the project root run: Windows `gradlew.bat build`, macOS / Linux `./gradlew build` (run `chmod +x gradlew` once on macOS / Linux).
3. The mod is `build/libs/delta-visuals-0.9.4.jar` (ignore the `-sources` and `-dev` files).
4. If `gradle/wrapper/gradle-wrapper.jar` is missing, run `gradle wrapper --gradle-version 9.2.0` once (needs Gradle installed).

On GitHub: the **build** workflow runs on every push; the jar is under Actions -> run -> Artifacts -> `delta-visuals-jar`.
Release: pushing the tag `v0.9.4` (`git tag v0.9.4 && git push origin v0.9.4`) runs the **release** workflow, which attaches the jar to a GitHub Release.

## Credits and license

Thanks to the author(s) of the original Delta Visuals for the idea and inspiration. Add the author's name here yourself: it is not recorded anywhere in the project.
The code of this project was written from scratch; no textures or assets from other packs were copied.

License: **All Rights Reserved** (as set in `fabric.mod.json`). For an open license (for example MIT), change `license` in `src/main/resources/fabric.mod.json` and add a `LICENSE` file.
