<!--
  Merci pour votre contribution. Les critères ci-dessous reprennent
  CONTRIBUTING.md ; ils ne sont pas décoratifs, la CI vérifie les trois
  premiers.
-->

## Ce que fait cette PR

<!-- Une ou deux phrases. Le *pourquoi* plutôt que le *quoi* : le diff dit déjà quoi. -->

Tâches couvertes : <!-- GOAL-00X-TYY, … -->

## Vérifications

- [ ] `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug` passe,
      et **sa sortie a été constatée**
- [ ] Chaque comportement ajouté est couvert par un test
- [ ] `:domain` reste exempt de toute dépendance Android
- [ ] Aucun code mort, aucun `TODO` sans tâche dans `TASKS.md`
- [ ] Les tâches couvertes sont cochées dans `TASKS.md`
- [ ] La documentation impactée est à jour, `ARCHITECTURE.md` §9 comprise

## Si cette PR touche à l'interface

> ⚠️ La CI **ne vérifie pas** le rendu visuel — trop coûteux par PR. Ces points
> sont donc à votre charge, personne ne les rattrapera.

- [ ] `./gradlew :app:verifyRoborazziDebug` a été lancé
- [ ] Les références réenregistrées ont été **regardées**, pas seulement acceptées
- [ ] Le thème sombre a été vérifié, pas seulement le clair

## Si cette PR touche à l'API FreshRSS

- [ ] Le comportement utilisé a été **constaté**, pas supposé — source lue, ou
      serveur réel interrogé
- [ ] `docs/freshrss-api.md` reflète ce qui a été constaté, sa §6 comprise
- [ ] Aucun détail de l'API (jeton, `continuation`, en-tête, identifiant
      hexadécimal) ne franchit la couche `data`
- [ ] Les réponses d'erreur en texte brut sont traitées comme telles
- [ ] Aucun secret n'apparaît dans un appel de journalisation
