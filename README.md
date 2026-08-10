# Universal Account Manager v2


## Features

- Microsoft browser login
- Hypixel Ban Checker + Duration
- A main-screen Buy Accounts entry with a Localts and Nicealts provider page
- Cookie and Minecraft access-token login from the original mod
- Namechanger and Skinchanger

## Build

Use Java 8 and run:

```powershell
.\gradlew.bat clean repairedJar
```

The standalone mod is written to
`build/libs/UniversalAccountManager-2.10.jar`.

Localts credentials are never entered into the mod. The login/API-docs button
opens Localts in the system browser, and the API key pasted into the mod is kept
in memory only. Every purchase displays the exact product, quantity, and credit
total in a confirmation dialog before the purchase endpoint is called.
