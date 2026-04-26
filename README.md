# react-native-pick-contact

[![npm version](https://img.shields.io/npm/v/react-native-pick-contact.svg)](https://www.npmjs.com/package/react-native-pick-contact)
[![license](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/idanlevi1/react-native-pick-contact/blob/main/LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/idanlevi1/react-native-pick-contact?style=social)](https://github.com/idanlevi1/react-native-pick-contact)
![platforms](https://img.shields.io/badge/platforms-iOS%20%7C%20Android-lightgrey.svg)
![architecture](https://img.shields.io/badge/architecture-New%20Architecture-blue.svg)

A **zero-permission** contact picker for React Native. Lets the user pick a single contact (name + phone) via the native OS picker — without ever requesting `READ_CONTACTS` or Contacts authorization.

> **Look mom, no permission dialogs! 🚀**

```
  Traditional library                    react-native-pick-contact
  ─────────────────────                  ─────────────────────────
  1. User taps "Pick Contact"            1. User taps "Pick Contact"
  2. 🔒 Permission dialog appears        2. 📱 Native picker opens instantly
  3. 😬 User hesitates / denies          3. User picks a contact
  4. ...or grants full address book       4. ✅ App receives name + phone
  5. App reads entire contact list
  6. App finds the one contact

  Permissions: READ_CONTACTS             Permissions: NONE
  Data exposed: EVERYTHING               Data exposed: 1 contact
```

---

## New Architecture Ready

Many popular contact libraries are **broken on React Native 0.76+** because they rely on the legacy Bridge and haven't migrated to TurboModules.

**react-native-pick-contact** is built from the ground up for the New Architecture:

- **TurboModule** native module (C++ codegen on iOS, Java codegen on Android)
- **Codegen** type-safe specs — no manual bridging, no `NativeModules["..."]` hacks
- Works out of the box with **React Native 0.76+** — just install and go

If you're migrating to the New Architecture and your current contact library broke, this is a drop-in replacement.

---

## Why this library?

Most React Native contact libraries require the `READ_CONTACTS` permission, which gives your app access to the **entire** address book. This is:

- **A privacy concern** — users see a scary permission dialog and may deny it
- **A security risk** — your app has access to data it doesn't need
- **An App Store / Play Store review flag** — reviewers question why you need full contact access

**react-native-pick-contact** takes a different approach:

| | Traditional Libraries | react-native-pick-contact |
|---|---|---|
| Permissions | `READ_CONTACTS` (full address book) | **None** |
| Data access | All contacts, all fields | **One contact, name + phone only** |
| User trust | Permission dialog before use | **System picker, no dialog** |
| Privacy | App can read contacts in background | **Only what user explicitly picks** |

### How it works

- **iOS**: Uses `CNContactPickerViewController` — an out-of-process system UI. Your app never touches the Contacts database directly.
- **Android**: Uses `ActivityResultContracts.PickContact()` — the system contact picker grants a **temporary URI permission** scoped to the single selected contact.

---

## Requirements

- React Native **0.76+** (New Architecture enabled)
- iOS **15.0+**
- Android **minSdk 24+**

---

## Installation

```bash
npm install react-native-pick-contact
```

### iOS

```bash
cd ios && pod install
```

No additional configuration needed. No `Info.plist` keys required.

### Android

No additional configuration needed. No permissions to add to `AndroidManifest.xml`.

---

## Usage

```typescript
import { pickContact } from 'react-native-pick-contact';

async function handlePickContact() {
  const contact = await pickContact();

  if (contact === null) {
    // User cancelled the picker
    return;
  }

  console.log(contact.name);  // "John Appleseed"
  console.log(contact.phone); // "+1 (555) 012-3456"
}
```

---

## API

### `pickContact()`

```typescript
function pickContact(): Promise<Contact | null>;
```

Opens the native OS contact picker. Returns a `Promise` that resolves with:

- A `Contact` object if the user selected a contact
- `null` if the user cancelled

### `Contact`

```typescript
type Contact = {
  name: string;   // Full display name
  phone: string;  // First phone number (formatted)
};
```

### Error codes

| Code | Description |
|------|-------------|
| `E_NO_ACTIVITY` | Android: no active Activity found |
| `E_NO_VIEW_CONTROLLER` | iOS: no root view controller found |
| `E_PICKER_BUSY` | Picker is already open (both platforms) |
| `E_LAUNCH_PICKER` | Failed to launch the system picker |
| `E_CONTACT_RESOLVE` | Failed to read data from the selected contact |
| `E_ACTIVITY_DESTROYED` | Android: Activity destroyed while picker was open |
| `E_MODULE_DEALLOCATED` | iOS: native module was deallocated mid-operation |

---

## Notes

- On iOS, contacts without phone numbers are **grayed out** in the picker (cannot be selected).
- On Android, the selected phone number comes from the contact's data via a temporary URI permission — no manifest permission needed.
- The `phone` field returns the **first** phone number. If the contact has no phone numbers, it returns an empty string.

---

## Android 11+ Package Visibility

Android 11 (API 30) introduced [package visibility filtering](https://developer.android.com/training/package-visibility), which requires apps to declare `<queries>` in their manifest to interact with other apps. **You do not need to add any `<queries>` tags for this library.** The system contact picker is an OS-level component — Android launches it directly and grants a temporary URI permission for the selected contact. No inter-app resolution or manifest declarations are required.

---

## Troubleshooting

### iOS Simulator shows an empty contact list

The iOS Simulator ships with **no contacts by default**. The picker will open but display an empty list.

**Fix:** Import the test contacts file included in this repo by dragging `test-contacts.vcf` onto the Simulator window, or run:

```bash
xcrun simctl openurl booted "file://$(pwd)/test-contacts.vcf"
```

You can also open the Contacts app in the Simulator and add contacts manually. Alternatively, test on a **real device** where your iCloud or local contacts are available.

### Picker opens but immediately closes (iOS)

This can happen if the root view controller is not fully presented yet (e.g., calling `pickContact()` during app launch). Wait until your screen is fully mounted before calling the function.

### `E_NO_ACTIVITY` on Android

This occurs when `pickContact()` is called while no Activity is in the foreground (e.g., from a background task or headless JS). Ensure you only call it from a user-facing screen.

---

## Contributing

Found a bug? Have a feature idea? [Open an issue](https://github.com/idanlevi1/react-native-pick-contact/issues) or submit a PR — contributions are welcome!

If this library saved you from dealing with `READ_CONTACTS` permissions, consider giving it a [star on GitHub](https://github.com/idanlevi1/react-native-pick-contact) — it helps other developers discover the zero-permission approach.

---

## License

MIT
