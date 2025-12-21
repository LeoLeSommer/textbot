# AGENT INSTRUCTIONS - TextBot Localization

This file contains critical guidelines for AI assistants working on this project to maintain the translation and localization system.

## 1. UI Strings & Labels

**NEVER** use hardcoded strings in the UI. All user-facing text must be defined in string resources.

### Resource Files

- **Default (English)**: `app/src/main/res/values/strings.xml`
- **French**: `app/src/main/res/values-fr/strings.xml`

### Usage in Code

- **Jetpack Compose**: Use `stringResource(R.string.your_string_id)`.
- **Activities/Fragments**: Use `getString(R.string.your_string_id)`.

## 2. Date and Time Formatting

**NEVER** use fixed `SimpleDateFormat` patterns like `"HH:mm"` or `"dd/MM/yyyy"`. Date and time formats must be locale-aware.

### Best Practice

Use `java.text.DateFormat` constants to ensure the OS handles the correct format (12/24 hour clock, date order, month names) for the user's locale.

```kotlin
// Example for Time
val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())

// Example for Date & Time
val dateTimeFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
```

## 3. Adding New Languages

If a new language needs to be added, ensure a new `values-<lang>` directory is created with a corresponding `strings.xml`.
