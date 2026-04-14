# Drawing App

Android drawing app built with Kotlin and Jetpack Compose.

## Features

### Drawing Tools
- Pen, spray, shapes, fill, text, stamp, eyedropper, selection
- Dodge, burn, smudge, particles, eraser

### Brush Types
Normal, neon, marker, rainbow, stipple, chalk, watercolor, flat

### Shapes
Line, rectangle, circle, triangle, star, arrow, hexagon

### Layers
- Multiple layers with blend modes: multiply, screen, overlay, darken, lighten
- Show/hide layers, reorder

### Emoji Objects
- Interactive objects floating above layers
- Move, resize, duplicate, merge into layer
- Smart guides for alignment

### Filters
Contrast, brightness, saturation, blur, sharpen, noise, vignette, hue shift, invert, grayscale

### Other
- Canvas textures: canvas, paper, kraft
- Grid with adjustable size
- Undo/redo
- Save to gallery

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android API 26+
- Gradle 8.6+
