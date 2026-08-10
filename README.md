# Universal Account Manager v2

## Preview

<img width="788" height="466" alt="{2322E87E-C145-4B15-8FE9-74B32E0B1559}" src="https://github.com/user-attachments/assets/654573fc-fe2e-42b3-b8dc-8bd8918d3bb8" />
<img width="620" height="294" alt="{4923C944-8142-4D60-9738-D9E80454ACA0}" src="https://github.com/user-attachments/assets/b07857f6-2576-41b6-b49d-c5dadf43a475" />
<img width="695" height="315" alt="{5F327252-7049-4D07-98DC-C9557BDB5A34}" src="https://github.com/user-attachments/assets/2885b87f-d762-417f-9a76-3924d62502a8" />


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
