# PROMPT.md — Prompt d'initialisation (figé)

> **Ce fichier ne se met pas à jour.** Il conserve l'intention initiale du
> projet, telle qu'elle a été formulée avant que la moindre ligne soit écrite.
>
> Il a servi une seule fois, pour créer le Harness (Phase 0). Ensuite, les
> agents travaillent à partir de [AGENTS.md](./AGENTS.md), [SPECS.md](./SPECS.md),
> [ARCHITECTURE.md](./ARCHITECTURE.md) et [TASKS.md](./TASKS.md).
>
> **Lorsqu'une règle applicable a remplacé ce texte sur un point, c'est
> [AGENTS.md](./AGENTS.md) qui fait foi.** Les écarts constatés sont recensés en
> fin de fichier.

---

## Rôle

Tu es l'agent responsable de l'**initialisation du projet FreshRSS Discover et
de son Harness de développement pour Claude Code**.

Cette étape constitue la **Phase 0 — Harness / Initialisation**.

Le but n'est pas uniquement de créer l'application, mais de mettre en place un
environnement permettant ensuite à plusieurs agents Claude Code de développer le
projet progressivement, de manière autonome et reproductible.

Le Harness doit permettre de transformer un objectif de haut niveau en une série
de tâches exécutables, validées et traçables.

**Ne commence pas l'implémentation complète de l'application pendant cette
étape.**

## 1. Projet

**FreshRSS Discover** est une application Android native servant de client pour
un serveur FreshRSS.

L'application récupère les articles RSS de l'utilisateur via l'API FreshRSS et
les présente dans un flux vertical inspiré du principe de **Google Discover /
Google Feed**.

L'expérience recherchée est :

```
FreshRSS → Articles des différents flux → Mélange des sources
→ Flux vertical continu → L'utilisateur fait défiler
→ Les articles suffisamment visibles deviennent lus
→ De nouveaux articles sont chargés
```

Fonctionnalités principales prévues : connexion à un serveur FreshRSS ;
authentification via l'API ; récupération des abonnements ; récupération des
articles ; mélange des articles provenant des différents flux ; flux vertical
infini ; pagination ; marquage automatique comme lu lorsqu'un article est
suffisamment visible ; synchronisation du statut lu ; *Pull to Refresh* ;
récupération des nouveaux articles ; ouverture de l'article original ; cache
local et résilience réseau ; interface Android moderne.

## 2. Documentation FreshRSS

La documentation officielle concernant l'accès mobile constitue la référence :
<https://freshrss.github.io/FreshRSS/fr/users/06_Mobile_access.html>

L'intégration doit utiliser l'**API compatible Google Reader**, à l'adresse
`https://<serveur>/api/greader.php`.

`POST /api/greader.php/accounts/ClientLogin` authentifie. Le mot de passe
utilisé doit être le **mot de passe API**, distinct du mot de passe principal.
Les requêtes authentifiées utilisent `Authorization: GoogleLogin auth=<auth>`.
`GET /api/greader.php/reader/api/0/token` fournit le jeton de modification.

Points d'entrée à étudier précisément avant implémentation :

```
GET /reader/api/0/subscription/list?output=json
GET /reader/api/0/unread-count?output=json
GET /reader/api/0/tag/list?output=json
GET /reader/api/0/stream/contents/reading-list
```

La documentation doit être consultée avant toute décision concernant :
authentification, pagination, récupération des articles, récupération des
nouveaux articles, statut lu, marquage comme lu, jeton de modification, gestion
des erreurs.

**Ne jamais inventer le comportement d'un point d'entrée.** Les points non
documentés doivent être identifiés comme tels et validés avant implémentation.

## 3. Structure du dépôt

```
PROMPT.md · SPECS.md · AGENTS.md · ARCHITECTURE.md
TASKS.md · CONTRIBUTING.md · README.md

.claude/commands/{goal,task,status,verify}.md
```

Les fichiers racine constituent la mémoire persistante du projet ; les commandes
Claude Code constituent l'interface permettant de la piloter.

## 4. Rôle des fichiers

| Fichier | Rôle |
|---|---|
| `PROMPT.md` | Prompt d'initialisation, utilisé une seule fois |
| `SPECS.md` | Source de vérité fonctionnelle — **ce que l'application doit faire** |
| `AGENTS.md` | Source de vérité des règles — **comment les agents doivent travailler** |
| `ARCHITECTURE.md` | Source de vérité technique — **comment l'application est conçue** |
| `TASKS.md` | État du travail — **ce qui doit être fait, est en cours, est terminé** |
| `CONTRIBUTING.md` | Comment contribuer |
| `README.md` | Documentation générale |

## 5 à 8. Le Harness et la commande `/goal`

`/goal` reçoit un objectif de haut niveau et le transforme en tâches réalisables :

```
Goal → Analyse → Décomposition → Plan → TASKS.md
     → Exécution → Validation → Documentation
```

**Étape 1 — Comprendre le contexte.** Lire obligatoirement `AGENTS.md`,
`SPECS.md`, `ARCHITECTURE.md`, `TASKS.md`, puis uniquement les fichiers de code
nécessaires. Ne pas modifier le code immédiatement.

**Étape 2 — Vérifier les dépendances.** Déterminer ce qui existe déjà, ce qui
manque, les tâches concernées, les contraintes d'architecture, les tests
existants. Ne pas recréer une fonctionnalité existante.

**Étape 3 — Décomposer.** Transformer l'objectif en tâches suffisamment petites
pour être exécutées et validées indépendamment. Éviter les tâches vagues du type
« Faire l'API », « Faire l'interface », « Finir l'authentification ».

**Étape 4 — Plan avant exécution.** Présenter Goal, plan, fichiers concernés et
validation, puis commencer. Le Harness privilégie l'autonomie : ne poser une
question que si la décision ne peut pas être déduite de `SPECS.md`,
`ARCHITECTURE.md`, `AGENTS.md`, de l'état du code ou des conventions.

## 9 à 12. Exécution, TASKS.md, identifiants

Une fois le plan établi : prendre la première tâche non terminée, l'implémenter,
lancer les validations, corriger, marquer terminée, passer à la suivante. Ne pas
modifier massivement le dépôt sans validation intermédiaire.

États : `[ ]` TODO · `[-]` IN PROGRESS · `[x]` DONE · `[!]` BLOCKED.

Chaque Goal a un identifiant stable (`GOAL-001`, `GOAL-002`, …), chaque tâche
également (`GOAL-002-T01`), afin d'être référençable dans les commits.

Avant de créer un Goal, `/goal` vérifie `TASKS.md` : si un objectif identique ou
très proche existe, ne pas créer de doublon — proposer de reprendre l'existant.

## 13 à 15. Les autres commandes

- **`/task [ID]`** — travailler une tâche précise ; sans identifiant,
  sélectionner la prochaine tâche pertinente.
- **`/status`** — vue synthétique dérivée de `TASKS.md` **et de l'état réel du
  dépôt**.
- **`/verify`** — compiler, tester, analyser, vérifier les fichiers importants,
  les changements Git, les erreurs évidentes, et que les tâches déclarées `DONE`
  sont réellement validées. Résultat en `PASS` / `WARN` / `FAIL`. Une tâche dont
  la validation échoue n'est pas `DONE`.

## 16. Règle fondamentale

Le Harness ne doit jamais considérer que `code écrit = tâche terminée`.

```
code écrit → tests → validation → documentation → TASKS.md = DONE
```

## 17. Reprise après interruption

Le système doit permettre à un agent d'être interrompu à tout moment. Au
redémarrage : lire `AGENTS.md`, lire `TASKS.md`, identifier les tâches
`IN PROGRESS`, vérifier l'état réel du code, reprendre la tâche.

**Ne jamais considérer automatiquement une tâche `IN PROGRESS` comme terminée.**

## 18. Détection des incohérences

`TASKS.md` dit `DONE` mais le code ne compile pas ; `TASKS.md` dit `TODO` mais
la fonctionnalité existe ; `ARCHITECTURE.md` décrit A mais le code fait B.

Dans ces cas : identifier l'incohérence, ne pas la masquer, corriger le côté qui
a tort, signaler la décision dans le rapport.

## 19. Mise à jour de la documentation

Nouvelle fonctionnalité → `SPECS.md`, `ARCHITECTURE.md`, `TASKS.md`.
Changement architectural → `ARCHITECTURE.md` **obligatoirement**.
Nouvelle règle → `AGENTS.md`. Procédure de contribution → `CONTRIBUTING.md`.

## 20. Goals spécialisés FreshRSS

Pour un Goal touchant à l'API : consulter `SPECS.md`, la section FreshRSS de
`ARCHITECTURE.md`, la documentation officielle FreshRSS, vérifier
l'implémentation actuelle, déterminer les points d'entrée nécessaires,
identifier les paramètres réellement supportés, implémenter, tester, documenter.

**Ne jamais déduire le comportement de l'API uniquement à partir d'une
implémentation existante.**

## 21. Architecture du client FreshRSS

```
UI → ViewModel → Use Case → Repository → FreshRssApi → HTTP → FreshRSS
```

Les détails suivants restent confinés à la couche FreshRSS : `ClientLogin`,
`Auth`, jeton de modification, en-têtes, points d'entrée, formats de réponse,
gestion des erreurs HTTP spécifiques.

## 22 à 23. Phase 0 et sa checklist

Créer les sept fichiers racine et les quatre commandes ; documenter l'API
FreshRSS à partir de sa documentation officielle ; identifier les points
nécessitant une validation ; vérifier la cohérence des documents et du Harness.

## 24. Ce qui ne doit PAS être fait pendant cette phase

Ne pas implémenter : authentification FreshRSS, récupération des articles,
pagination, flux Discover, marquage automatique comme lu, synchronisation des
statuts, *Pull to Refresh*, cache local, écran Settings.

Le Harness doit être prêt à les accueillir ; ils seront réalisés par les Goals
suivants.

## 25. Critère de réussite

La Phase 0 est réussie lorsqu'un nouvel agent Claude Code peut arriver dans le
dépôt, exécuter `/status` puis `/goal Implémenter l'authentification FreshRSS`,
et obtenir automatiquement : analyse du contexte → plan → décomposition en
tâches → mise à jour de `TASKS.md` → implémentation → tests → validation →
documentation → Goal terminé, **sans avoir besoin qu'on lui redonne l'ensemble
du contexte du projet**.

## 26. Fin de l'initialisation

Lorsque la Phase 0 est terminée, **ne pas commencer automatiquement la
Phase 1**. Fournir un rapport indiquant : les fichiers créés, les fichiers
modifiés, l'architecture retenue, la documentation FreshRSS étudiée, les
commandes créées, les Goals initialement définis, les points bloquants ou
décisions restantes.

Le dépôt doit être laissé dans un état propre et directement exploitable par le
Harness.

---

## Écarts assumés

Ce que la réalisation a fait autrement que ce texte, et pourquoi. La règle
applicable est celle d'[AGENTS.md](./AGENTS.md).

| Point du prompt | Ce qui a été fait | Raison |
|---|---|---|
| §9, §16 : validation à chaque étape | Les tâches s'enchaînent sans demander de validation ; l'arrêt est régi par AGENTS.md §1.2 | Une validation par tâche rendrait l'avance autonome impossible. La granularité de relecture est le **commit**, qui reste réversible |
| §2 : la documentation officielle comme référence | La documentation établit l'usage, mais **`p/api/greader.php` fait foi** sur les paramètres et la forme des réponses | La documentation officielle ne détaille ni les paramètres de pagination ni le JSON renvoyé. S'y limiter aurait obligé à deviner — ce que §2 interdit par ailleurs |
| §3 : structure du dépôt | Ajout de `docs/freshrss-api.md` et de `CLAUDE.md` | Le relevé de l'API est trop volumineux pour `ARCHITECTURE.md` et se met à jour à un autre rythme |
| §24 : ne rien implémenter | Une ossature exécutable a été livrée : thème, navigation, `PlaceholderScreen`, chaîne Roborazzi | Sans elle, `/verify` n'aurait rien à vérifier et le Harness serait invérifiable. Aucune fonctionnalité de la liste §24 n'a été écrite |
| §22 : structure documentaire seule | Le dépôt part d'un template Android existant, dépouillé de sa logique métier | Fournir une architecture, un outillage de qualité et une CI éprouvés plutôt que reconstruits |
