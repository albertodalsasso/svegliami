# Audit tecnico

## Baseline

- Branch previsto: `Svegliami_2.0`; applicazione Android Kotlin, Java 17, minSdk 26, compile/target SDK 36.
- UI Views/XML con Material Components; non usa Compose né Vico.
- `Prefs` mantiene sveglie/località/cache meteo in SharedPreferences e migra la vecchia sveglia singola.
- `SleepStore` persiste ancora le sessioni nel JSON `filesDir/sleep_sessions.json` (limite attuale 30); Room 2.8.5 definisce entità e importa i dati in background, ma non è ancora la fonte di lettura delle schermate.
- `Scheduler` mantiene la sveglia normale e pianifica separatamente il controllo meteo; `WeatherCheck` usa Open-Meteo/cache e non dovrebbe rimuovere il fallback normale in caso di errore.
- `SleepService` è avviato esplicitamente dall'utente e analizza accelerometro oppure audio a finestre. Non salva PCM né produce clip riproducibili.
- Le fasi storiche sono stime euristiche; REM e validazione clinica assenti.
- Sleep Score spiega le componenti disponibili; la regolarità usa un proxy. Diario e statistiche utilizzano dati locali esistenti.

## Funzionalità e maturità

| Area | Stato attuale |
|---|---|
| Sveglia base, giorni, suoneria, AlarmManager | Presente; da verificare su device/Doze e permessi esatti |
| Previsioni Open-Meteo e anticipo per pioggia | Presente, con fallback cache/orario normale |
| Tracking comodino/materasso | Presente come stima locale; prestazioni non validate su device diversi |
| Tracking combinato | Rimosso: un sensore per volta, con ripiego sull'accelerometro se il microfono manca |
| Room | Schema, repository e import JSON iniziale; lettura UI ancora JSON |
| Score, diario, Insights | Implementazione iniziale su sessioni disponibili |
| Impostazioni | Pagina iniziale; selezione tracking e link alla località funzionanti; account non disponibile |
| Registrazione eventi/continua, classificatore, Sound Timeline | Non implementati |
| Morning Report, calendario mensile, Health Connect, cloud sync | Non implementati |

## Algoritmi

L'analisi corrente aggrega segnali di movimento/rumore in punteggi e assegna stadi euristici. Una notte quasi tutta classificata come profonda è un rischio noto; più granularità grafica non dimostra maggiore accuratezza. Il prossimo confronto deve conservare input grezzi/feature compatibili, versionare algoritmo e confrontare copertura, transizioni, risvegli e accordo con annotazioni di riferimento. Nessuna delle implementazioni presenti è validata clinicamente.

## Baseline di compilazione

Il repository non contiene Gradle Wrapper o `local.properties`. La baseline precedente è stata eseguita con Gradle 8.14.3 dalla cache locale, JDK Android Studio e Android SDK locale. Dopo ogni modifica eseguire build e unit test usando l'ambiente Android configurato; i test non sostituiscono la verifica su dispositivo.

Audit esterno e licenze: [`docs/architecture-v2.md`](docs/architecture-v2.md) e [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).
