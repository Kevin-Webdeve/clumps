# OverTake (OT)

OverTake is a client-only Fabric mod for Minecraft 1.21.11. It provides configurable combat-readiness indicators, one-at-a-time hotbar switching, mace fall-window feedback, optional automatic mace attacks, a configurable HUD, and diagnostic state. Press **O** (rebindable under Controls) to toggle the mod. It never sends fabricated packets: hotbar changes use the inventory's selected-slot setter, and automatic attacks call the vanilla client interaction manager followed by the normal main-hand swing.

## Compatibility

The project is configured for Minecraft **1.21.11**, Fabric Loader **0.19.5**, Fabric API **0.141.6+1.21.11**, Java **21** (class-file version 65 and declared minimum runtime), Fabric Loom **1.18-SNAPSHOT** (resolved as 1.18.4), and Gradle wrapper **9.7.1**. The clean build was verified with JDK **26.0.2.1** and emits Java 21 bytecode; a JDK 21 runtime was not available in the build environment. Run the build with the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

Copy `build\libs\Clumps-fabric-1.21.11-29.0.0.jar` to the Minecraft `mods` folder along with Fabric Loader and Fabric API.

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
/ot stun keybind "key" (for example "R", "mouse4", or "F1")
/ot stun target crosshair|nearest|both
/ot stun mode auto|mace|sword
/ot stun reach <1-6>
/ot stun cone <1-180>
/ot stun fall_check on|off
/ot stun message on|off
/ot hud on|off
/ot hud sound on|off
/ot hud position <x> <y>
/ot hud scale <0.5-3>
```

Settings are stored in `config\overtake.json`, separated by profile. Invalid values are clamped to safe ranges; malformed files are logged and replaced with defaults. `/ot off` cancels pending switching without performing a restore action.

The active profile's `targetType` accepts `living` (default) or `players`. `maceSlot`, `swordSlot`, and `axeSlot` use hotbar positions 1-9; `0` selects the first matching item automatically. `switchDelay` and `maceDelay` are measured in client ticks. Edit the JSON directly and run `/ot reload` to apply changes.

## Behavior and limitations

- Weapon switching is the centralized one-action scheduler: each request has a mace/melee identifier and priority, is revalidated every client tick, expires if it cannot complete within its delay window, and verifies the selected slot after switching. Switching runs before the tick's attack input so the selected weapon is used for that tick's attack; vanilla's interaction manager synchronizes the selected slot before the attack packet. Manual input can cancel or defer a transaction, and normal melee switching requires attack input and a valid crosshair target. Normal melee switching follows the configured sword priority and does not automatically switch to an axe, including against blocking targets. Mace assistance selects a mace only inside the configured falling window.
- Target and weapon-switch checks, including mace assistance, are capped at 4.5 blocks with a spear and 3 blocks with other items; configured ranges can lower these limits. Auto mace switches to a mace from any currently selected item when a hotbar mace is available and the other readiness conditions are met. This does not extend Minecraft's actual attack reach.
- The mace triggerbot is off by default. Enable it with `/ot mace triggerbot on` (or `/ot mace auto on`); with switching and mace assistance enabled, it selects a mace for a living crosshair target while descending after the configured fall-distance threshold (default 2 blocks), then attacks as soon as the mace is selected without waiting for full weapon cooldown. It does not target entities outside the crosshair or repeat attacks during the same fall. Early attacks may deal reduced damage under Minecraft's combat mechanics; server latency and server tick rate also affect the result.
- Press the configurable stun key (default G; mouse buttons 2-5 are supported, but left click is not) to perform the client-side axe/follow-up sequence. It selects and attacks with the axe first, then switches to the follow-up weapon on the next client tick, so both selected slots are synchronized through vanilla gameplay. It targets the crosshair or a nearby target in a 60-degree aim cone and defaults to mace follow-up when available. `/ot stun target`, `/ot stun mode`, `/ot stun reach`, `/ot stun cone`, and `/ot stun fall_check` configure its behavior.
- Shield, pearl, and totem availability are read-only reminders; OverTake does not automatically use these items.
- The HUD shows master/module state, selected and preferred weapons, target distance, readiness, transaction state, utility-item availability, and optional tick-processing cost. Position and scale are configurable; the sound-alert setting cues the rising edge of mace readiness.

## Reproducible manual test checklist

1. Test with desired items already selected, missing from the hotbar, and moved to another hotbar slot.
2. Hold attack on a living crosshair target and check that melee selection respects configured sword/axe priority and switch delay.
3. Change slots manually during a pending switch with manual override on and off; verify selection state before and after.
4. Move the target out of range, stop attacking, die, disconnect, and change worlds during a pending switch. Verify cancellation and optional restoration.
5. Disable the master system during a pending switch; verify that no further switch occurs.
6. Check airborne mace feedback with and without a hotbar mace, at range boundaries, and with different threshold/delay values. Verify the triggerbot stays off by default, then enable it and confirm it attacks only a living crosshair target once per fall after the threshold as soon as the mace is selected; do not interpret an attack attempt as a guaranteed full-damage hit.
7. Change profile and HUD settings, restart the client, and verify persistence. Test malformed JSON recovery by backing up then editing `config\overtake.json`.
8. Enable debug logging and compare HUD tick cost at normal and reduced frame rates. Client processing time does not include server or network latency.

## Development

Source is split into common and client source sets. All combat logic, commands, config access, and HUD rendering are client-only. Build artifacts are produced under `build\libs`.
