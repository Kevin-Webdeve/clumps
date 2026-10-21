# OverTake (OT)

OverTake is a client-only Fabric mod for Minecraft 1.21.11. It provides configurable combat-readiness indicators, one-at-a-time hotbar switching, mace fall-window feedback, optional automatic mace attacks, a configurable HUD, and diagnostic state. Press **O** (rebindable under Controls) to toggle the mod. It never sends fabricated packets: hotbar changes use the inventory's selected-slot setter, and automatic attacks call the vanilla client interaction manager followed by the normal main-hand swing.

## Quick start

1. Install Fabric Loader for Minecraft 1.21.11 and add Fabric API to the `mods` folder.
2. Download the versioned JAR from GitHub Releases (or run `.\gradlew.bat build` and use the matching `build\libs\Clumps-fabric-1.21.11-<version>.jar`), then launch the game.
3. Press **O** to enable OverTake, or **K** to open its in-game settings. Both keys can be rebound under **Options → Controls**.
4. Choose a profile in the settings screen or run `/ot profile pvp`. Use `/ot status` for a summary and `/ot help` for commands.

Settings made in the screen are saved to `config\overtake.json`. Use its **Utility keybinds** submenu to rebind Pearl, Stun, Crystal, Minecart, and Healing Potion actions; command aliases remain available for these and less common options.

## Compatibility

The project is configured for Minecraft **1.21.11**, Fabric Loader **0.19.5**, Fabric API **0.141.6+1.21.11**, Java **21** (class-file version 65 and declared minimum runtime), Fabric Loom **1.18-SNAPSHOT** (resolved as 1.18.4), and Gradle wrapper **9.7.1**. The clean build was verified with JDK **26.0.2.1** and emits Java 21 bytecode; a JDK 21 runtime was not available in the build environment. Run the build with the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

Copy the matching `Clumps-fabric-1.21.11-<version>.jar` to the Minecraft `mods` folder along with Fabric Loader and Fabric API. The artifact version is generated from `gradle.properties`.

## Commands

Use client command `/ot`:

```text
/ot on|off|toggle|status|help|config|reload
/ot debug on|off
/ot profile default|pvp|practice|sword|axe|smp|crystal|pot|uhc|cart|mace|spear|vanilla|tnt
/ot sword triggerbot on|off
/ot sword sprint_reset on|off
/ot crystal keybind "key" | status
/ot cart keybind "key" | status
/ot pot keybind "key" | status
/ot switch on|off|status
/ot switch priority mace sword axe
/ot switch restore on|off
/ot switch delay <0-100>
/ot switch manual_override on|off
/ot mace on|off|status
/ot mace auto on|off
/ot mace triggerbot on|off (alias for auto)
/ot mace mode manual|assist
/ot mace threshold <2-20>
/ot mace range <1-16>
/ot mace delay <0-100>
/ot pearl ["keybind"] (for example "G", "mouse4", or "F1"; omit the key to display full status)
/ot pearl delay <0-20> (Wind Charge delay after the predicted aim aligns, in client ticks)
/ot stun keybind "key" (for example "R", "mouse4", or "F1")
/ot stun target crosshair|nearest|both
/ot stun mode auto|mace|sword
/ot stun reach <1-6>
/ot stun cone <1-180>
/ot stun fall_check on|off
/ot stun message on|off
/ot sp [on|off|status]
/ot sp delay <0-5000>
/ot hud on|off
/ot hud sound on|off
/ot hud position <x> <y>
/ot hud scale <0.5-3>
```

`/ot status` displays all module, keybind, delay, HUD, and item-availability status in a single-column list. `/ot switch status`, `/ot mace status`, `/ot sp status`, `/ot stun status`, `/ot pearl`, and `/ot pearl delay` remain as aliases that show the same full status. Settings are stored in `config\overtake.json`, separated by profile. `/ot profile` accepts `default`, `pvp`, `practice`, `sword`, `axe`, `smp`, `crystal`, `pot`, `uhc`, `cart`, `mace`, `spear`, `vanilla`, or `tnt`; `diamond`/`netherite` alias `smp`, `nodebuff`/`nethpot` alias `pot`, and `trident` aliases `spear`. Each game-mode profile has its own saved settings. New game-mode profiles target players by default; the Axe profile prefers axe switching, and the Mace profile enables mace assistance. The Sword profile enables its triggerbot and S/W sprint reset by default; turn either off with `/ot sword triggerbot off` or `/ot sword sprint_reset off`. The mace triggerbot remains off by default. Crystal, Cart, and Pot actions use configurable press-to-activate keys (C, V, and H by default), only while the matching profile is selected. Switching profiles cancels a pending weapon switch. Invalid values are clamped to safe ranges; malformed files are logged and replaced with defaults. `/ot off` cancels pending switching without performing a restore action.

The active profile's `targetType` accepts `living` (default) or `players`. `maceSlot`, `swordSlot`, and `axeSlot` use hotbar positions 1-9; `0` selects the first matching item automatically. `switchDelay` and `maceDelay` are measured in client ticks. Edit the JSON directly and run `/ot reload` to apply changes.

## Behavior and limitations

- Weapon switching is the centralized one-action scheduler: each request has a mace/melee identifier and priority, is revalidated every client tick, expires if it cannot complete within its delay window, and verifies the selected slot after switching. Switching runs before the tick's attack input so the selected weapon is used for that tick's attack; vanilla's interaction manager synchronizes the selected slot before the attack packet. Manual input can cancel or defer a transaction, and normal melee switching requires attack input and a valid crosshair target. The Axe profile prefers axes; other profiles follow their saved weapon priority. Mace assistance selects a mace only inside the configured falling window.
- While a spear is selected, entity targeting uses a block-occluded 4.5-block client raycast, so entities near the edge are detected by their hitbox rather than their center. Auto-mace retains the spear's captured 4.5-block detection range through the hotbar switch. Other target checks retain their existing configured ranges. This changes client target detection only; actual attacks still use vanilla/server reach validation and are not extended by the raycast.
- The mace triggerbot is off by default. Enable it with `/ot mace triggerbot on` (or `/ot mace auto on`); with switching and mace assistance enabled, it selects a mace for a living crosshair target while descending after the configured fall-distance threshold (default 2 blocks), then attacks as soon as the mace is selected without waiting for full weapon cooldown. It does not target entities outside the crosshair or repeat attacks during the same fall. Early attacks may deal reduced damage under Minecraft's combat mechanics; server latency and server tick rate also affect the result.
- Sword profile automatically attacks a valid crosshair target when vanilla attack cooldown is full and a sword is selected. When sprinting forward, the optional-by-setting S/W reset briefly taps back then forward after a triggerbot hit; both sword options can be toggled with `/ot sword triggerbot` and `/ot sword sprint_reset`.
- Crystal profile's C key (customize with `/ot crystal keybind "key"`) places an End Crystal on an aimed-at obsidian/bedrock block, charges an aimed-at Respawn Anchor with Glowstone, or activates a charged anchor only in dimensions where anchors explode. Cart profile's V key selects/uses a TNT Minecart only when a Bow/Crossbow is held and the crosshair is on a rail. Pot profile's H key selects and throws a splash Healing/Strong Healing Potion; aim down to heal yourself. All three bindings are configurable with `/ot crystal keybind "key"`, `/ot cart keybind "key"`, and `/ot pot keybind "key"`. These actions require `/ot on`, the matching profile, and the needed items in hotbar/offhand; server rules and vanilla interaction validation still apply.
- Spear Lunge is enabled separately with `/ot sp on` and defaults to a 30 ms spear-switch delay. `/ot sp status` reports whether it is enabled and its current delay; `/ot sp delay <0-5000>` accepts any whole-millisecond value in that range. In manual mode, click attack while an empty hotbar slot, wind charge, or potion is selected; keep a Lunge-enchanted spear in the hotbar. A zero delay attacks immediately after switching; nonzero delays attack on the first client tick after the delay expires. The client uses Minecraft's native spear attack and does not fabricate packets or override server rules.
- Press the configurable stun key (default G; mouse buttons 2-5 are supported, but left click is not) to perform the client-side axe/follow-up sequence. It selects and attacks with the axe first, then switches to the follow-up weapon on the next client tick, so both selected slots are synchronized through vanilla gameplay. It targets the crosshair or a nearby target in a 60-degree aim cone and defaults to mace follow-up when available. `/ot stun target`, `/ot stun mode`, `/ot stun reach`, `/ot stun cone`, and `/ot stun fall_check` configure its behavior.
- Ender Pearl catch is bound to P by default. Configure it with `/ot pearl "G"` (letters, digits, F1-F12, and mouse buttons 2-5 are accepted case-insensitively); `/ot pearl` displays the current binding and Wind Charge delay. The delay defaults to 2 client ticks after predicted aim alignment and is adjustable with `/ot pearl delay <0-20>`; zero throws at the first aligned tick. One press throws a pearl in the player's current look direction, then follows Async Locator's tick-based gravity/drag and ping-adjusted catch prediction, sensitivity-stepped aim, and Wind Charge fallback timing. The aim is applied only while sending the use action, leaving the camera and normal movement unchanged. Both items must be in the hotbar or offhand. The attempt is cancelled after 60 ticks, if the player changes hotbar slots, or when another active feature takes over. Vanilla cooldowns and server validation still apply; prediction cannot guarantee a catch on every server.
- Shield and totem availability remain read-only reminders; OverTake does not automatically use these items.
- The HUD shows master/module state, selected and preferred weapons, target distance, readiness, transaction state, utility-item availability, and optional tick-processing cost. Position and scale are configurable; the sound-alert setting cues the rising edge of mace readiness.
- Press **K** (rebindable under Controls) for the in-game settings screen. It provides quick access to profiles, module toggles, target type, mace mode/threshold, switch delay, and pearl Wind Charge delay; all changes save immediately.

## Reproducible manual test checklist

1. Test with desired items already selected, missing from the hotbar, and moved to another hotbar slot.
2. Hold attack on a living crosshair target and check that melee selection respects configured sword/axe priority and switch delay.
3. Change slots manually during a pending switch with manual override on and off; verify selection state before and after.
4. Move the target out of range, stop attacking, die, disconnect, and change worlds during a pending switch. Verify cancellation and optional restoration.
5. Disable the master system during a pending switch; verify that no further switch occurs.
6. Check airborne mace feedback with and without a hotbar mace, at range boundaries, and with different threshold/delay values. Verify the triggerbot stays off by default, then enable it and confirm it attacks only a living crosshair target once per fall after the threshold as soon as the mace is selected; do not interpret an attack attempt as a guaranteed full-damage hit.
7. Change profile and HUD settings, restart the client, and verify persistence. Test malformed JSON recovery by backing up then editing `config\overtake.json`.
8. Repeat combat and utility-item checks at low, typical, and high server latency; test pearl fallback and timeout behavior, server cooldowns, and item moves between hotbar/offhand during an attempt.
9. Exercise Crystal, Cart, Pot, Spear Lunge, Stun, and Pearl with both required items present and missing, across relevant dimensions and server restrictions.
10. Enable debug logging and compare HUD tick cost at normal and reduced frame rates. Client processing time does not include server or network latency.

## Development

Source is split into common and client source sets. All combat logic, commands, config access, and HUD rendering are client-only. Run `.\gradlew.bat test` for focused unit tests and `.\gradlew.bat build` for the full build. Build artifacts are produced under `build\libs`; publishing a GitHub release uploads the versioned mod JAR automatically.
