# The title screen layout

**This directory name is not a choice.** FancyMenu reads layouts from
`config/fancymenu/customization/`, which `LayoutHandler` builds as
`FancyMenu.MOD_DIR + "/customization"`. The first attempt shipped them in
`layouts/` — a reasonable guess, and the only thing in the whole build that was
guessed rather than read out of the jar. The title screen simply did not
change, with nothing in any log, which is this mod's failure mode for
everything.

**The identifier is `title_screen`, not the class name.**
`ScreenIdentifierHandler.getIdentifierOfScreen` resolves a screen's class and
then hands it to `UniversalScreenIdentifierRegistry.getUniversalIdentifierFor`,
so what a screen reports — and therefore what a layout must match — is the
short universal id. `UniversalScreenIdentifierRegistry` maps `title_screen` to
`net.minecraft.client.gui.screens.TitleScreen`; storing the class name matches
nothing, silently.

Paths *inside* a layout are relative to the **game directory** (the instance
root), not to this folder — `GameDirectoryUtils.getAbsoluteGameDirectoryPath`
resolves them, so `config/fancymenu/assets/...` is correct and `assets/...`
would not be.

`title_screen.txt` is hand-authored against FancyMenu 3.9.9's own format, read
out of the jar rather than guessed: the set header and container syntax come
from `PropertiesParser`'s concat templates, the container types from the list
`Layout.deserialize` asks for (`layout-meta`, `menu_background`, `element`,
`vanilla_button`, `deep_element`, `customization`, `layer_group`), and every
property key from the class that reads it.

**A layout is inert until its screen is on the allowlist.** That is a second
file, `../customizablemenus.txt`, and it is keyed by the screen's Java class
name rather than by `title_screen`. Without an entry there this layout loads,
reports itself enabled, matches the screen and is then ignored, with nothing
logged. Only the editor writes that file, so a hand-authored layout ships
without it unless somebody remembers — which is now a test, not a memory.

**What is here:** the background, the title, and all five menu buttons.

An earlier version of this file said the buttons could not be placed from here,
because FancyMenu resolves a vanilla widget's id at runtime and the title screen
had no resolver in the jar. It has one:
`TitleScreenWidgetIdentificationContext` maps each widget's localization key to
a fixed universal id. Those ids are

| Button | Id | Matched on |
|---|---|---|
| Singleplayer | `mc_titlescreen_singleplayer_button` | `menu.singleplayer` |
| Multiplayer | `mc_titlescreen_multiplayer_button` | `menu.multiplayer` |
| Mods | `forge_titlescreen_mods_button` | `fml.menu.mods` |
| Options | `mc_titlescreen_options_button` | `menu.options` |
| Quit | `mc_titlescreen_quit_button` | `menu.quit` |

with `realms`, `language`, `accessibility` and `playdemo` alongside them. The
mods button's id is the Forge one because it is matched on `fml.menu.mods`,
which is the key NeoForge still uses; the Fabric-side `modmenu.title` maps to a
different id that will never appear here.

Realms, language and accessibility are hidden rather than styled — there is no
art for them, and three vanilla-looking buttons under five drawn ones is worse
than three fewer buttons. The Minecraft logo, the splash text and the Realms
notification icons are hidden the same way, by their instance identifiers
(`minecraft_logo_widget`, `minecraft_splash_widget`,
`minecraft_realms_notification_icons_widget`). **The branding line is
deliberately left alone** — the version string bottom-left is worth more than
the tidiness of removing it.

Each drawn button is a `vanilla_button` element, not a custom button: it keeps
the vanilla widget and its action, and only takes a position, a size and two
textures. Nothing has to know how to open the world-select screen because the
button that already knew is the one being retextured. Their labels are not set,
and that is what removes the vanilla text — `updateWidgetLabels` sets the
widget's message to `Component.empty()` when the element's label is null.

**They sit in containers named `vanilla_button`, not in `element` containers**,
and that distinction is the whole difference between working and not.
`Layout.deserialize` collects `vanilla_button` containers into
`serializedVanillaButtonElements`, and that list is what
`buildVanillaButtonElementInstances` binds to the screen's actual widgets;
`element` containers go to `serializedElements` and are never bound to
anything. The first attempt wrote them as `element { element_type =
vanilla_button }`, which parses, builds a real element, and then hides nothing
and draws nothing, with no error anywhere — the vanilla menu simply stayed and
ours never appeared. `element_type` inside the block is redundant beside the
container name and is kept only because it is what FancyMenu itself writes.

## Everything here is sized for a 1920x1080 screen at GUI scale 2

The art is rendered in **screen** pixels — `tools/build-title-art.py` sizes the
title's cap height "as it lands on a 1920-wide screen" — while FancyMenu
positions elements in Minecraft's **GUI** pixels, which on that screen at scale
2 are half as many. So a 305-pixel title placed at width 305 arrives at twice
its intended size, which is exactly how it first shipped.

Every coordinate and size here is therefore half the pixel figure, and every
element carries an auto-sizing baseline saying what that half-size means:

    auto_sizing = true
    auto_sizing_base_screen_width = 1920
    auto_sizing_base_screen_height = 1080
    auto_sizing_base_gui_scale = 2.0
    auto_sizing_base_gui_width = 960
    auto_sizing_base_gui_height = 540

FancyMenu rescales from that baseline to whatever screen and GUI scale a player
actually has, so the layout holds at any resolution and any scale. Without it
the numbers are only right for one setting, and Minecraft's automatic GUI scale
on a 1080p display is 3, not 2 — so the common case would be the wrong one.

**The composition is title left, menu right**, both in the upper band, with the
centred sunrise left clear between them. The menu is right-aligned and holds
that alignment at any window width because it anchors to `top-right` with
`sticky_anchor = true`: that combination positions the element's *right* edge at
`screen width + x`, so one `x = -60` on every button lines all five up 60 GUI
pixels in from the right edge whatever their widths are. With `sticky_anchor`
off the same anchor positions the left edge instead, and five differently-sized
buttons would step raggedly.

## Two flags that look like they help and do the opposite

**`transparent_background` discards a texture, it does not reveal one.**
`ButtonElement.updateWidgetTexture` reads the flag and, when it is set,
overwrites the normal, hover and inactive textures with FancyMenu's
fully-transparent PNG before handing them to the widget. A button with both a
texture and the flag draws nothing at all — which looks exactly like a texture
that failed to load, and is not that.

**An omitted label keeps the vanilla text; an empty label removes it.**
`VanillaWidgetElement.updateWidgetLabels` calls `setCustomLabelFancyMenu` only
when `getLabel()` is non-null, so leaving the property out means Minecraft keeps
drawing "Singleplayer" in white over our art. `label = ` with nothing after it
is a non-null empty string, and that is what blanks it. (The parent
`ButtonElement` does empty the message on null, which is what made the omission
look safe — the vanilla widget subclass overrides that method and does not.)

Both are pinned in `tests/test_fancymenu_layout.py`.

## The two icons this file does not place

The language and accessibility icons are moved by the companion mod
(`TitleScreenIcons`), not from here, and that is not a workaround.

FancyMenu's table lists both of them, under
`mc_titlescreen_language_button` and `mc_titlescreen_accessibility_button`, and
neither matches at runtime — they are the only two widgets on this screen that
do not. When FancyMenu cannot name a widget it identifies it by position
instead: `generateBaseId` concatenates the widget's x and y in GUI coordinates
at the moment it is discovered, and the editor writes that number out as the
instance identifier with nothing to say it is different in kind from
`mc_titlescreen_quit_button`. It is entirely different in kind. It is a captured
constant, right on the machine that captured it and matching nothing anywhere
else, and the failure is invisible — the button just stays where Minecraft put
it.

**No mod is responsible.** Fifteen mods in this pack reference the title screen;
three inject into it (Collective, Create Transmission and FancyMenu itself), and
the two that are not FancyMenu are bare callbacks at the end of `init` that
touch nothing. Create reads both buttons' names, but only to decide which row to
put its own button beside. Nothing re-creates them.

So they are placed in code, from a formula over the live screen size rather than
a number captured at one resolution: centred as a pair against the bottom edge,
clear of the menu column and of the branding lines. `test_fancymenu_layout.py`
now fails on any numeric instance identifier, so a positional id cannot be
committed here by accident again.

## Regenerating the art

`tools/build-title-art.py` renders every PNG here from the two fonts and the
spec at the top of that file. Change a colour or a size there and re-run it;
do not edit the images by hand.
