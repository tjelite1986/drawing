# Drawing App

Android ritapp byggd med Kotlin och Jetpack Compose.

## Funktioner

### Ritverktyg
- Penna, spray, former, fyll, text, stämpel, eyedropper, markering
- Dodge, burn, smudge, partiklar, radergummi

### Penseltyper
Normal, neon, marker, rainbow, stipple, chalk, watercolor, flat

### Former
Linje, rektangel, cirkel, triangel, stjärna, pil, hexagon

### Lager
- Flera lager med blend modes: multiply, screen, overlay, darken, lighten
- Visa/dölj lager, byt ordning

### Emoji-objekt
- Interaktiva objekt ovanpå lagren
- Flytta, ändra storlek, dupliera, baka in i lager
- Smart guides vid inriktning

### Filter
Kontrast, ljusstyrka, mättnad, oskärpa, skärpa, brus, vinjett, färgton-shift, invertera, gråskala

### Övrigt
- Canvas-texturer: canvas, papper, kraft
- Grid med justerbar storlek
- Ångra/gör om (undo/redo)
- Spara till galleri

## Bygga

```bash
export ANDROID_HOME=/home/thomas/android-sdk
export ANDROID_SDK_ROOT=/home/thomas/android-sdk
./gradlew assembleDebug
```

APK hamnar i `app/build/outputs/apk/debug/app-debug.apk`.

## Krav

- Android API 26+
- Android Studio eller Gradle 8.6+
