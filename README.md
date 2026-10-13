# BrainPal

Een Android-app (Kotlin, Jetpack Compose) die dagelijks korte nieuwsberichten toont over onderwerpen die je zelf kiest.
Het nieuws wordt op de telefoon opgehaald uit openbare RSS-feeds. Geen account en geen server nodig.

- Onderwerpen: nieuws, tech, AI, wetenschap, ruimte, economie, sport, cultuur, gezondheid, gaming, opmerkelijk
- Leesmodus: artikelen lezen zonder advertenties, in de app zelf
- 👍 en 👎 leren de app wat je leuk vindt
- Meldingen bij nieuw nieuws
- Updates via GitHub: de app controleert of er een nieuwere release is en installeert die op verzoek

## Installeren

Download `BrainPal.apk` bij de nieuwste [Release](../../releases/latest) en open het bestand op je telefoon.
Android vraagt eenmalig toestemming om apps van buiten de Play Store te installeren.

## Een nieuwe versie uitbrengen

1. Verhoog `versionCode` en `versionName` in `app/build.gradle.kts`.
2. Bouw: `gradlew assembleRelease` (de Gradle-wrapper zit in de repo) (de sleutel staat in `keystore.properties`, dat staat niet in git).
3. Maak een release met een tag die gelijk is aan het versienummer, met het apk-bestand erbij:

   ```
   gh release create v1.2 app/build/outputs/apk/release/app-release.apk#BrainPal.apk --title "BrainPal 1.2" --notes "Wat is er nieuw"
   ```

De app vergelijkt de tag van de nieuwste release met zijn eigen versienummer en toont een melding als de release nieuwer is.

## Let op

Elke versie moet met **dezelfde sleutel** zijn ondertekend, anders weigert Android de update.
Bewaar de map met de sleutel (`brainpal-release.jks` en `keystore.properties`) veilig en op een tweede plek.
