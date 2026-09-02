---
description: Transformer un objectif de haut niveau en tâches, puis les exécuter
argument-hint: <objectif de haut niveau>
---

# /goal — point d'entrée du Harness

Objectif reçu : **$ARGUMENTS**

Si aucun objectif n'a été fourni, ne rien inventer : afficher les Goals `TODO`
de `TASKS.md` et demander lequel entreprendre.

```
Goal → Analyse → Décomposition → Plan → TASKS.md
     → Exécution → Validation → Documentation
```

---

## Étape 1 — Comprendre le contexte

Lire, dans cet ordre, **avant de toucher au code** :

1. `AGENTS.md` — les règles de travail
2. `SPECS.md` — ce que l'application doit faire
3. `ARCHITECTURE.md` — comment elle est conçue, et **§9 : ce qui existe vraiment**
4. `TASKS.md` — où le travail s'est arrêté

Si l'objectif touche à l'API FreshRSS — authentification, pagination, articles,
statut lu, marquage, erreurs — lire **aussi** `docs/freshrss-api.md`, en
particulier sa §6 (points incertains).

Lire ensuite **uniquement** les fichiers de code nécessaires à l'objectif. Ne
pas parcourir le dépôt entier.

Ne modifier aucun fichier à cette étape.

---

## Étape 2 — Vérifier les dépendances

Établir, en s'appuyant sur ce qui vient d'être lu :

- ce qui **existe déjà** — ARCHITECTURE.md §9 le dit, le code le confirme ;
- ce qui **manque** ;
- quelles tâches de `TASKS.md` sont concernées ;
- quelles contraintes d'architecture s'appliquent ;
- quels tests existent déjà ;
- quelles décisions de `SPECS.md` §8 ou `docs/freshrss-api.md` §6 ce Goal doit
  trancher.

**Ne pas recréer une fonctionnalité existante.**

### Goal déjà présent ?

Comparer l'objectif aux Goals de `TASKS.md`.

- **Identique ou très proche d'un Goal existant** → ne pas créer de doublon.
  L'annoncer, et proposer de **reprendre** le Goal existant à sa première tâche
  non terminée.
- **Recouvrement partiel** → le dire, et proposer soit d'étendre le Goal
  existant, soit d'en créer un nouveau qui en dépend.
- **Nouveau** → lui attribuer le prochain identifiant `GOAL-0XX` libre.

Les identifiants sont **stables** : ne jamais renuméroter.

---

## Étape 3 — Décomposer

Transformer l'objectif en tâches **suffisamment petites pour être exécutées et
validées indépendamment**. Chaque tâche doit correspondre à un changement réel
et vérifiable.

Une bonne tâche nomme ce qu'elle produit :

```
[ ] Traduire les codes HTTP en erreurs de domaine (400/401/503, corps texte brut)
[ ] Tester la pagination au MockEngine, curseur invalide compris
```

Une mauvaise tâche est un domaine, pas un changement :

```
[ ] Faire l'API
[ ] Faire l'interface
[ ] Finir l'authentification
```

Règles de découpage :

- l'étude de l'API **précède** l'implémentation, et produit une mise à jour de
  `docs/freshrss-api.md` (AGENTS.md §3) ;
- les modèles de `:domain` **précèdent** les couches qui les consomment ;
- les tests ne sont **pas** des tâches séparées du code qu'ils couvrent, sauf
  lorsqu'ils portent sur un comportement à part entière (une campagne de cas
  limites, par exemple) ;
- toute tâche touchant à l'interface entraîne une tâche de captures Roborazzi ;
- la dernière tâche met à jour `ARCHITECTURE.md` §9 et, s'il y a lieu,
  `SPECS.md` §8.

Numéroter `GOAL-0XX-T01`, `T02`, …

---

## Étape 4 — Présenter le plan

Avant toute modification de code, afficher :

```
Goal:
GOAL-0XX — <titre>

Plan:
1. ...
2. ...
3. ...

Fichiers principaux concernés:
- ...
- ...

Décisions à trancher:
- ... (SPECS.md §8 n°X / docs/freshrss-api.md §6 n°Y)

Validation:
- ./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
- ... (verifyRoborazziDebug si l'interface change)
```

Puis **inscrire le Goal et ses tâches dans `TASKS.md`** — table de vue
d'ensemble comprise — et commencer l'exécution.

### Autonomie

Le Harness privilégie l'autonomie. Ne poser une question que si la réponse ne
peut raisonnablement pas être déduite de `SPECS.md`, `ARCHITECTURE.md`,
`AGENTS.md`, de l'état du code ou des conventions du projet.

Les quatre cas d'arrêt sont ceux d'`AGENTS.md` §1.2, et seulement ceux-là.

---

## Étape 5 — Exécuter

Puis, pour chaque tâche, dans l'ordre :

1. prendre la première tâche non terminée ;
2. la passer à `[-]` dans `TASKS.md` ;
3. l'implémenter — **elle seule** ;
4. écrire ses tests dans le même incrément ;
5. lancer `/verify` et **constater la sortie réelle** ;
6. corriger jusqu'à ce qu'elle passe ;
7. mettre à jour la documentation impactée (AGENTS.md §6) ;
8. passer la tâche à `[x]`, committer (AGENTS.md §7) ;
9. tâche suivante.

Ne jamais modifier massivement le dépôt sans validation intermédiaire.

**Rappel** : `code écrit ≠ tâche terminée`. Une tâche dont la vérification
échoue reste `[-]`, ou passe à `[!]` avec sa raison écrite.

---

## Étape 6 — Clore

Quand toutes les tâches sont `[x]` :

- passer le Goal à `[x]` dans la table de vue d'ensemble ;
- mettre à jour la « Phase courante » et la « Prochaine tâche » de `TASKS.md` ;
- **archiver** : déplacer le détail du Goal en fin de `TASKS.archive.md` (le
  créer à la première clôture), ne laisser dans `TASKS.md` que sa ligne de la
  table — c'est ce qui garde la mémoire lisible à chaque session ;
- inscrire les dettes ouvertes par le Goal comme **tâches**, pas comme remarques ;
- produire un rapport : ce qui a été fait, les décisions prises et leur raison,
  ce qui reste ouvert.

**Ne pas enchaîner sur le Goal suivant sans y être invité.**
