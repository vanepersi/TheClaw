# TheClaw

Click-to-join two-player claw machine for Paper **26.1.2**.

## How it plays

1. **Click the machine** — first player is the operator  
2. Second clicker is always the **human claw** (scaled + blinded)  
3. Operator stands on the **control pad** and sends direction signals (WASD / scroll)  
4. Claw player sees signals as **titles** and walks with look locked  
5. When time runs out the claw **drops** and a **hotbar number** appears — both must press it  
6. By default, syncing correctly makes the claw **slip** (rigged machine). Toggle with `sync-success-means-grab`.

## Setup

```
/clawadmin create main
/clawadmin setmachine main    # stand on the clickable join block
/clawadmin setpad main        # stand on the control pad block
/clawadmin setclaw main       # inside the glass
/clawadmin setdrop main       # prize opening
/clawadmin setboundsa main
/clawadmin setboundsb main
/clawadmin addprize main      # repeat
/clawadmin preview main
```

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
./gradlew build
```
