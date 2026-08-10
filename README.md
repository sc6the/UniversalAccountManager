# Universal Account Manager v2

## Preview

<img width="620" height="294" alt="{4923C944-8142-4D60-9738-D9E80454ACA0}" src="https://github.com/user-attachments/assets/b07857f6-2576-41b6-b49d-c5dadf43a475" />


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
