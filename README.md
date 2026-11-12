# Svegliami

Sveglia Android che **si anticipa se piove**.

Esempio: sveglia alle 07:00 con anticipo di 30 minuti.
1. Alle **06:10** l'app scarica le previsioni da Open-Meteo per la tua località.
2. Se tra le **06:30 e le 07:00** è prevista pioggia (≥ 0,1 mm nei 15 minuti, oppure probabilità ≥ soglia impostata), aggiunge una sveglia alle **06:30**.
3. Se suona quella delle 06:30, la sveglia delle 07:00 di quel giorno viene annullata. Se non piove, suona normalmente alle 07:00.

La sveglia normale è **sempre** programmata. Se meteo o rete non funzionano, suoni comunque all'orario normale.

## Come aprirlo
1. Apri questa cartella con **Android Studio** (Ladybug o più recente) e lascia completare la sincronizzazione Gradle.
2. Collega il telefono con il debug USB attivo e premi ▶ Run.
3. Nell'app scegli l'orario, i giorni e l'anticipo, poi imposta la località:
   - **📍 Usa la mia posizione (GPS)**: rileva la posizione una volta sola. Aspetta il GPS fino a 20 secondi, poi usa la posizione da rete.
   - **Aggiorna la posizione automaticamente**: prima di ogni controllo meteo usa l'ultima posizione nota del telefono. Richiede il permesso di posizione «Consenti sempre». La posizione viene aggiornata anche a ogni apertura dell'app.
   - oppure cerca una città per nome. Scegliendola, l'aggiornamento automatico si disattiva.

   Infine premi **Salva**.
4. Concedi i permessi segnalati in alto, compresa l'esclusione dall'ottimizzazione della batteria: serve perché il controllo notturno abbia la rete.

Il bottone «Controlla il meteo adesso» mostra cosa deciderebbe l'app per la prossima sveglia.

## Struttura
| File | Ruolo |
|---|---|
| `RainDecider.kt` | Logica pura: piove nella finestra? (con unit test) |
| `WeatherClient.kt` | Chiamate a Open-Meteo (geocoding + previsioni, senza API key) |
| `Scheduler.kt` | Programmazione di AlarmManager (normale, anticipata, controllo, snooze) |
| `WeatherCheck.kt` | Controllo meteo con nuovi tentativi ogni 5 minuti e cache come ripiego |
| `AlarmService.kt` / `AlarmActivity.kt` | Suoneria, vibrazione e schermata a tutto schermo |
| `LocationHelper.kt` | Posizione GPS/rete, nome della località (Geocoder), aggiornamento in background |
| `Receivers.kt` | Receiver per la sveglia, per il controllo meteo e per il riavvio |

## Scaricare l'APK sul telefono
A ogni push su `main`, GitHub Actions compila l'app e pubblica l'APK con un link fisso:
https://github.com/albertodalsasso/svegliami/releases/download/apk/svegliami.apk
(il repository è privato: apri il link dal browser del telefono dopo aver fatto l'accesso a GitHub).
