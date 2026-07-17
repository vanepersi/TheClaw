# TheClaw

Two-player claw machine arcade plugin for Paper **26.1.2**.

One player is the **joystick operator** (moves the claw with hotbar controls). The other is the **claw** behind the glass (presses Grab to clamp onto prizes). The operator must guide a held prize to the drop chute — and just like a real machine, grabs often slip.

## Play

```
/claw join <arena> [operator|claw|any]
/claw leave
/claw role <operator|claw|any>
/claw arenas
/claw points
```

## Setup

```
/clawadmin create <name>
/clawadmin setoperator <arena>   # joystick booth
/clawadmin setclaw <arena>       # start position inside the machine
/clawadmin setlobby <arena>
/clawadmin setdrop <arena>       # prize drop opening
/clawadmin setboundsa <arena>    # play-area corner A
/clawadmin setboundsb <arena>    # play-area corner B
/clawadmin addprize <arena>      # stand on each prize spawn
/clawadmin preview <arena>
```

## Custom models

In `config.yml`, set `item-model`, `custom-model-data`, or `itemsadder-id` under `prize`, `grab`, and `controls.*`. Soft-depends on ItemsAdder / ModelEngine resource packs.

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
./gradlew build
```

Output: `build/libs/TheClaw-1.0.0.jar`
