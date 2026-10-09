# Kalorienbilanz (Android)

Ein lokales Android-MVP zum Erfassen von Mahlzeiten, Tagesverbrauch und Körpermaßen. Die Oberfläche basiert auf Jetpack Compose.

## Funktionen

- Tagesübersicht mit Kalorienaufnahme, manuellem Kalorienverbrauch und Bilanz
- Eigene Lebensmittelliste mit kcal, Kohlenhydraten, Fett und Eiweiß pro Portion
- Tages-, Wochen-, Monats- und Jahresverläufe für Kalorienaufnahme, Verbrauch und Bilanz sowie Gewicht, Taille und Hüfte
- Optionale Mess-Erinnerung im Wochen-, Zwei-Wochen- oder Monatsrhythmus
- Alle Einträge bleiben lokal auf dem Gerät

Die Umrechnung von 7.000 kcal in etwa 1 kg Fett ist nur eine grobe Veranschaulichung und keine Vorhersage der tatsächlichen Gewichtsänderung.

## Garmin Connect und Nährwertdaten

Der Kalorienverbrauch wird im MVP manuell eingetragen. Eine offizielle Garmin-Connect-Anbindung setzt Zugang zum [Garmin Connect Developer Program](https://developer.garmin.com/gc-developer-program/overview/) voraus; eine persönliche API-Anbindung ist daher nicht Teil dieses Startstands. Die Nährwerte können von der Verpackung abgelesen und beim ersten Eintrag selbst erfasst werden. Das Lebensmittel wird anschließend für die Wiederverwendung lokal gespeichert. Barcode-Suche, externe Nährwertdatenbanken und das Auslesen von Verpackungsfotos sind noch nicht enthalten.

## Bauen

Das Projekt mit Android Studio öffnen und Gradle synchronisieren. Erforderlich sind JDK 17 und Android SDK 35. Unter Windows kann der Build mit `.\gradlew.bat assembleDebug` erstellt werden. Anschließend kann `app` auf einem Emulator oder Gerät gestartet werden.
