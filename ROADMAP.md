# Roadmap Svegliami 2.0

Questa roadmap descrive il lavoro rimanente; una voce non è completata solo perché esiste una schermata.

## Stato del percorso

- [x] Audit iniziale, baseline e studio tecnico/licenze (dettagli in `docs/architecture-v2.md`).
- [x] Fondazioni: Room schema/import iniziale, score spiegabile, diario e aggregazioni Insights.
- [x] Navigazione a cinque destinazioni; schermata Impostazioni iniziale per modalità tracking e informazioni reali.
- [ ] Rifinitura UI responsive, transizioni e stati vuoti; schermata Buonanotte dedicata.
- [ ] Morning Report e calendario navigabile collegati ai dati reali.
- [ ] Sound Timeline, clip locali, player, retention e cifratura.
- [ ] Trasporto adattivo e Smart Wake con vincolo hard sull'orario limite.
- [ ] Migrazione verificata a Room come fonte autorevole, con fallback/rollback sicuri.
- [ ] Health Connect opzionale con provenienza dati esplicita.
- [ ] Login/sincronizzazione facoltativi, solo dopo una decisione di backend e privacy.
- [ ] Verifica su dispositivi Android reali, batteria, Doze, riavvio e permessi.

## Ordine di lavoro

1. Rendere affidabile la navigazione e mantenere una sola selezione tracking visibile (comodino o materasso).
2. Completare schermata notturna e lifecycle del servizio, senza legare la sveglia al tracking.
3. Definire il contratto dati di report/calendario e consolidare la migrazione Room.
4. Implementare audio solo con consenso, limiti di memoria/spazio, cancellazione e test di interruzione.
5. Migliorare l'algoritmo con valutazione comparativa su dati etichettati; non inferire REM senza validazione.
6. Estendere allarme e trasporto mantenendo sempre un allarme di fallback indipendente.

Non si pubblicano funzionalità simulate come complete. Nessun push è incluso in questa roadmap.
