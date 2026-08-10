# Universal Account Manager Fixed

This project overlays maintained classes onto the original Universal Account
Manager 1.7 jar and bundles Mixin for a standalone Forge 1.8.9 installation.

## Implemented

- Microsoft browser login
- Automatic refresh of stored Microsoft refresh-token accounts at game startup
- A main-screen Buy Accounts entry with a LocalTS provider page
- LocalTS refresh-token/cookie categories, color-coded stock, live refresh,
  balance lookup, quantity discounts, explicit purchase confirmation, order
  polling, and automatic account import
- Import Previous Purchases scans packaged LocalTS orders and remembers
  successfully imported item IDs to prevent duplicate work
- Cookie and Minecraft access-token login from the original mod

## Build

Use Java 8 and run:

```powershell
.\gradlew.bat clean repairedJar
```

The standalone mod is written to
`build/libs/UniversalAccountManager-2.10.jar`.

LocalTS credentials are never entered into the mod. The login/API-docs button
opens LocalTS in the system browser, and the API key pasted into the mod is kept
in memory only. Every purchase displays the exact product, quantity, and credit
total in a confirmation dialog before the purchase endpoint is called.
