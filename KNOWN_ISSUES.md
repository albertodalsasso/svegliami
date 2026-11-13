# Problemi noti e limiti

- L'interfaccia resta Views/XML con Activity distinte; la navigazione non conserva ancora lo stato/scroll di ogni sezione come una navigazione Compose a destinazione singola.
- La schermata Impostazioni è un primo pannello funzionale, non il centro impostazioni completo. Account, tema selezionabile, retention audio e profili di trasporto non sono implementati.
- Il flusso Buonanotte non ha ancora una schermata notturna dedicata e il lifecycle/process death va verificato su dispositivi reali.
- Il monitoraggio audio calcola caratteristiche mentre acquisisce; non salva frammenti, non classifica parlato/tosse/risate e non riproduce audio.
- Le fasi di sonno sono stime euristiche da segnali limitati. Non c'è REM validata né ground truth; Sleep Score non è una valutazione medica.
- Room importa il JSON legacy, ma le schermate continuano a leggere/scrivere tramite `SleepStore`. La migrazione non è ancora conclusa come fonte dati autorevole.
- Le statistiche disponibili sono iniziali; non ci sono calendario mensile, confronti 7/30 giorni completi o correlazioni abitudini/sonno.
- Il meteo cambia l'anticipo in base alle regole originali; profili bici/bus/auto/piedi e scelta automatica del mezzo non sono ancora implementati.
- Health Connect, autenticazione, sincronizzazione e backup cloud non sono implementati.
- Nessun test reale su più telefoni, versioni Android, Doze, riavvio, battery saver o permessi revocati è documentato.
- È presente uno stato legacy `COMBINED` nei modelli/servizio e nei record storici. La UI non lo offre; la preferenza salvata viene normalizzata a `MATTRESS` per evitare un cambio distruttivo dei dati storici.
