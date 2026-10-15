# OverTake (OT)

OverTake is a client-only Fabric mod for Minecraft 1.21.11. It provides configurable combat-readiness indicators, one-at-a-time hotbar switching, mace fall-window feedback, optional automatic mace attacks, a configurable HUD, and diagnostic state. Press **O** (rebindable under Controls) to toggle the mod. It never sends fabricated packets: hotbar changes use the inventory's selected-slot setter, and automatic attacks call the vanilla client interaction manager followed by the normal main-hand swing.

## Compatibility

The project is configured for Minecraft **1.21.11**, Fabric Loader **0.19.5**, Fabric API **0.141.6+1.21.11**, Java **21** (class-file version 65 and declared minimum runtime), Fabric Loom **1.18-SNAPSHOT** (resolved as 1.18.4), and Gradle wrapper **9.7.1**. The clean build was verified with JDK **26.0.2.1** and emits Java 21 bytecode; a JDK 21 runtime was not available in the build environment. Run the build with the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

Copy `build\libs\overtake-1.0.0.jar` to the Minecraft `mods` folder along with Fabric Loader and Fabric API.

## Commands

Use client command `/ot`:

```text
/ot on|off|toggle|status|help|config|reload
/ot debug on|off
/ot profile default|pvp|practice
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
/ot stun on|off|status
/ot stun mode manual|assist
/ot stun range <1-16>
/ot stun cooldown <0-12000>
/ot hud on|off
/ot hud sound on|off
/ot hud position <x> <y>
/ot hud scale <0.5-3>
```

Settings are stored in `config\overtake.json`, separated by profile. Invalid values are clamped to safe ranges; malformed files are logged and replaced with defaults. `/ot off` cancels pending switching without performing a restore action.

The active profile's `targetType` accepts `living` (default) or `players`. `maceSlot`, `swordSlot`, and `axeSlot` use hotbar positions 1-9; `0` selects the first matching item automatically. `switchDelay` and `maceDelay` are measured in client ticks. Edit the JSON directly and run `/ot reload` to apply changes.

## Behavior and limitations

- Weapon switching is the centralized one-action scheduler: each request has a mace/melee identifier and priority, is revalidated every client tick, expires if it cannot complete within its delay window, and verifies the selected slot after switching. Switching runs before the tick's attack input so the selected weapon is used for that tick's attack; failed selection is throttled before retry. Manual input can cancel or defer a transaction, and normal melee switching requires attack input and a valid crosshair target. Mace assistance selects a mace only inside the configured falling window.
- Target and weapon-switch checks, including mace assistance, are capped at 4.5 blocks with a spear and 3 blocks with other items; configured ranges can lower these limits. Auto mace switches to a mace from any currently selected item when a hotbar mace is available and the other readiness conditions are met. This does not extend Minecraft's actual attack reach.
- With an axe in the hotbar, an attack click while descending after a 2-block fall on a target within 3 blocks is queued, switches to the axe, and attacks once after the axe is selected. While grounded, rising, or before the fall threshold, the click is left to normal game handling. A queued attack is discarded if the fall ends or the target leaves range before the switch completes.
- The mace triggerbot is off by default. Enable it with `/ot mace triggerbot on` (or `/ot mace auto on`); with switching and mace assistance enabled, it selects a mace for a living crosshair target while descending after the configured fall-distance threshold (default 2 blocks), then attacks once the normal attack cooldown is ready. It does not target entities outside the crosshair or repeat attacks during the same fall. Server latency, server tick rate, and game mechanics affect the actual result.
- Stun slam assist is enabled with `/ot stun on` and `/ot stun mode assist`. Click a target that is actively blocking with a shield while descending after the configured 2-block mace threshold; with an axe and mace in the hotbar and the target within 3 blocks, the mod attacks once with the axe, switches to the mace, then attacks once more. Both attacks use normal client/server combat logic; timing and shield-disable behavior depend on game cooldowns and server rules.
- Shield, pearl, and totem availability are read-only reminders; OverTake does not automatically use these items.
- The HUD shows master/module state, selected and preferred weapons, target distance, readiness, transaction state, utility-item availability, and optional tick-processing cost. Position and scale are configurable; the sound-alert setting cues the rising edge of mace readiness.

## Reproducible manual test checklist

1. Test with desired items already selected, missing from the hotbar, and moved to another hotbar slot.
2. Hold attack on a living crosshair target and check that melee selection respects configured sword/axe priority and switch delay.
3. Change slots manually during a pending switch with manual override on and off; verify selection state before and after.
4. Move the target out of range, stop attacking, die, disconnect, and change worlds during a pending switch. Verify cancellation and optional restoration.
5. Disable the master system during a pending switch; verify that no further switch occurs.
6. Check airborne mace feedback with and without a hotbar mace, at range boundaries, and with different threshold/delay values. Verify the triggerbot stays off by default, then enable it and confirm it attacks only a living crosshair target once per fall after the threshold and when the normal attack cooldown is ready; do not interpret readiness as a guaranteed hit.
7. With a shield-blocking target, test stun assist while grounded, rising, below the fall threshold, outside range, and with axe/mace missing; verify no combo occurs. Then test the axe hit and mace follow-up with both items available.
8. Change profile and HUD settings, restart the client, and verify persistence. Test malformed JSON recovery by backing up then editing `config\overtake.json`.
9. Enable debug logging and compare HUD tick cost at normal and reduced frame rates. Client processing time does not include server or network latency.

## Development

Source is split into common and client source sets. All combat logic, commands, config access, and HUD rendering are client-only. Build artifacts are produced under `build\libs`.
