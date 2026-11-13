# Architettura attuale

## Stato

Svegliami resta un'app Kotlin nativa con interfaccia Views/XML, AppCompat e Material Components. Il package applicativo è `it.svegliami`; minSdk 26 e target/compile SDK 36. Non è ancora una migrazione Compose.

La Home e le activity `Sleep`, `Insights`, `Diario` e `Impostazioni` condividono la bottom navigation. Ogni destinazione è al momento una Activity Android distinta. Il monitoraggio permette ora di selezionare comodino (microfono) o materasso (accelerometro). I vecchi valori di preferenza `COMBINED` vengono letti come materasso; i record storici che li contengono non vengono riscritti.

## Confini attuali

- `Prefs`: configurazione sveglie, località, cache meteo e modalità di monitoraggio in SharedPreferences.
- `Scheduler`, `WeatherCheck`, `WeatherClient`, `RainDecider`: programmazione Android, controllo Open-Meteo e fallback della sveglia normale.
- `SleepService`, `SleepAnalyzer`, `sensors/SensorFusion`: acquisizione e stime di attività/sonno. La fusione resta nel codice per compatibilità, ma non è selezionabile dalla UI.
- `SleepStore`: archivio JSON legacy delle sessioni, ancora fonte di lettura delle schermate.
- `storage`: schema Room e import iniziale delle sessioni JSON; Room non è ancora la fonte autorevole delle schermate.
- `analysis`: calcolo dello score e aggregazioni statistiche.
- `MainActivity`, `SleepActivity`, `InsightsActivity`, `JournalActivity`, `SettingsActivity`: interfaccia e schermate attuali.

## Principi per l'evoluzione

1. La sveglia normale resta programmata indipendentemente da rete, audio e sensori.
2. Conservare JSON e preferenze legacy finché le migrazioni non sono verificate su dati reali.
3. Separare dati misurati, stime algoritmiche e importazioni esterne.
4. Non presentare fasi del sonno come misure cliniche; REM non è disponibile.
5. Audio locale e nessun upload automatico. Registrazione su file e classificazione non sono ancora implementate.
6. Aggiungere impostazioni solo quando il controllo produce un effetto reale.

Per l'audit del codice preesistente e il confronto dei repository consultare [`docs/architecture-v2.md`](docs/architecture-v2.md), [`TECHNICAL_AUDIT.md`](TECHNICAL_AUDIT.md) e [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md).
