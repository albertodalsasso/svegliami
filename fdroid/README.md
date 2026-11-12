# Pubblicare Svegliami su F-Droid

`it.svegliami.yml` è il file di metadati da proporre nel repository [fdroiddata](https://gitlab.com/fdroid/fdroiddata). Descrizione, icona e screenshot F-Droid li legge da `fastlane/metadata/android/`, in italiano (`it-IT`) e inglese (`en-US`).

## Come funziona
- F-Droid compila l'app dal codice del tag `v1.0`, senza le chiavi del repository, e la firma con la propria chiave.
- La versione di F-Droid è quella scritta in `app/build.gradle.kts` (`versionCode = 1`, `versionName = "1.0"`). Le build di GitHub Actions usano invece il numero progressivo della build, per Google Play.
- La versione F-Droid **non si installa sopra** l'APK di GitHub né sopra quella del Play Store, perché le firme sono diverse. Per passare da una all'altra bisogna disinstallare l'app, e si perdono sveglie e storico.

## Richiesta di inserimento (una volta sola)
1. Crea un account su https://gitlab.com.
2. Apri https://gitlab.com/fdroid/fdroiddata e premi **Fork**.
3. Nel tuo fork crea un branch `it.svegliami`. Aggiungi il file `metadata/it.svegliami.yml` con il contenuto di `fdroid/it.svegliami.yml`. Si fa anche dal browser, con **+ → New file**.
4. Apri una **merge request** verso `fdroid/fdroiddata`, scegli il modello **App inclusion** e compila la checklist:
   - licenza GPL-3.0-or-later;
   - nessuna libreria proprietaria e nessun tracciamento: solo AndroidX e Material Components;
   - nessuna funzione anti-feature: l'unico servizio online è Open-Meteo, API aperta senza chiave.
5. La pipeline di GitLab prova subito a compilare l'app. Se fallisce, il log dice perché e i revisori di solito suggeriscono la correzione.
6. Rispondi ai commenti dei revisori. I tempi vanno da qualche settimana a qualche mese.

## Nuove versioni dopo l'inserimento
1. In `app/build.gradle.kts` aumenta `versionCode` (2, 3, …) e `versionName` ("1.1", …).
2. Aggiungi le note in `fastlane/metadata/android/it-IT/changelogs/<versionCode>.txt` e `en-US/changelogs/<versionCode>.txt`.
3. Fai il commit e crea il tag, per esempio `git tag v1.1 && git push origin v1.1`.

F-Droid controlla i tag `vX.Y` e compila la nuova versione da solo (`AutoUpdateMode`), di solito in qualche giorno.
