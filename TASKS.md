# TASKS.md — Feuille de route et avancement réel

Mémoire persistante du projet. Un agent qui arrive doit pouvoir lire ce seul
fichier et comprendre **où le travail s'est arrêté**.

Documents liés : [AGENTS.md](./AGENTS.md) (les règles) ·
[SPECS.md](./SPECS.md) (le quoi) · [ARCHITECTURE.md](./ARCHITECTURE.md) (le
comment).

---

## Conventions

| Marque | État |
|---|---|
| `[ ]` | TODO — pas commencé |
| `[-]` | IN PROGRESS — commencé, **jamais supposé terminé** |
| `[x]` | DONE — code **et** tests **et** vérification constatée |
| `[!]` | BLOCKED — la raison est écrite juste en dessous |

Identifiants : `GOAL-00X` pour un Goal, `GOAL-00X-TYY` pour une tâche. Ils sont
**stables** : une tâche abandonnée est barrée, jamais renumérotée. Les messages
de commit les référencent (AGENTS.md §7).

Rappel (AGENTS.md §1.1) : `code écrit ≠ tâche terminée`.

---

## Phase courante

**Phase 0 — Harness** ✅ terminée
**Phase 1 — API FreshRSS** — prochaine, non commencée

Prochaine tâche : `GOAL-002-T01`.

---

## Vue d'ensemble

| Goal | Titre | État |
|---|---|---|
| GOAL-001 | Harness et initialisation | `[x]` |
| GOAL-002 | Authentification FreshRSS | `[ ]` |
| GOAL-003 | Récupération paginée des articles | `[ ]` |
| GOAL-004 | Cache local et résilience réseau | `[ ]` |
| GOAL-005 | Mélange des sources | `[ ]` |
| GOAL-006 | Flux Discover — interface | `[ ]` |
| GOAL-007 | Marquage automatique comme lu | `[ ]` |
| GOAL-008 | Synchronisation du statut lu | `[ ]` |
| GOAL-009 | Tirer-pour-rafraîchir | `[ ]` |
| GOAL-010 | Ouverture de l'article d'origine | `[ ]` |
| GOAL-011 | Écran de réglages | `[ ]` |

Seul **GOAL-002** est découpé en tâches ci-dessous. Les suivants le seront par
`/goal` au moment de les entreprendre : les découper maintenant reviendrait à
décider sans connaître l'état du code (AGENTS.md §2, « ne pas anticiper »).

---

## GOAL-001 — Harness et initialisation

**Statut : DONE**

Mise en place du dépôt, de sa documentation et des commandes de pilotage. Aucune
fonctionnalité applicative.

- [x] `GOAL-001-T01` Analyser le dépôt et le template
- [x] `GOAL-001-T02` Cloner le template et retirer sa logique métier
- [x] `GOAL-001-T03` Renommer projet, paquet et identifiants
- [x] `GOAL-001-T04` Étudier la documentation FreshRSS et la source `greader.php`
- [x] `GOAL-001-T05` Rédiger `docs/freshrss-api.md`
- [x] `GOAL-001-T06` Rédiger `SPECS.md`
- [x] `GOAL-001-T07` Rédiger `ARCHITECTURE.md`
- [x] `GOAL-001-T08` Rédiger `AGENTS.md`
- [x] `GOAL-001-T09` Rédiger `TASKS.md`, `CONTRIBUTING.md`, `README.md`
- [x] `GOAL-001-T10` Créer `/goal`, `/task`, `/status`, `/verify`
- [x] `GOAL-001-T11` Ossature exécutable : thème, navigation, écran d'attente
- [x] `GOAL-001-T12` Chaîne Roborazzi éprouvée, références enregistrées
- [x] `GOAL-001-T13` Vérification complète passée et constatée

### Décisions prises

| Décision | Raison |
|---|---|
| Template `c4software/tailscale-auto-rules` | Architecture Clean éprouvée, ktlint/detekt/kover/Roborazzi/CI déjà câblés |
| Hilt et Room conservés (au lieu de Koin et SQLDelight) | Infrastructure du template déjà éprouvée ; migrer aurait coûté la Phase 0 sans gain |
| Ktor retenu pour HTTP | Aucun client HTTP dans le template : ajout franc, sans conflit |
| Room retiré d'`app/build.gradle.kts` | Une base sans entité ne compile pas ; réappliqué par GOAL-004 |
| `PlaceholderScreen` | Rend l'ossature exécutable et vérifiable sans anticiper les écrans |

### Dettes ouvertes par ce Goal

- [x] `GOAL-001-T14` ~~Le garde-fou de couverture est vide.~~ **Levé par
      `GOAL-002-T02`** : `koverVerify` mesure désormais réellement, et a
      immédiatement échoué à 86,2 % sur les premiers modèles.
- [ ] `GOAL-001-T15` **Retirer `PlaceholderScreen`** lorsque les deux
      destinations auront leur écran réel (GOAL-006 et GOAL-011).
- [ ] `GOAL-001-T16` **Icône de l'application** : celle du template est encore en
      place.
- [ ] `GOAL-001-T17` **Lint Android désactivé sur les sources de test**
      (`ignoreTestSources = true`, hérité). AGP 9.3.1 plante sur ses propres
      composants d'analyse Kotlin. À réactiver dès qu'une version corrige.
- [ ] `GOAL-001-T18` **Robolectric simule l'API 35** alors que `targetSdk` vaut
      37 : aucune image n'existe pour 37. À relever dès que possible.
- [ ] `GOAL-001-T19` **CI désactivée sur `push`** (`branches: [never]`, hérité du
      template). À réactiver quand un dépôt distant existera.

---

## GOAL-002 — Authentification FreshRSS

**Statut : TODO**

Permettre à l'utilisateur de connecter l'application à son serveur FreshRSS et
de conserver sa session. Couvre SPECS.md §3.

Référence obligatoire : [docs/freshrss-api.md §2](./docs/freshrss-api.md).
Rappel AGENTS.md §3 : ne jamais inventer le comportement d'un point d'entrée.

- [!] `GOAL-002-T01` Constater `ClientLogin` contre un serveur réel — forme
      exacte de la réponse, codes d'erreur, comportement API désactivée — et
      mettre à jour `docs/freshrss-api.md`
      > **Bloqué : aucun serveur FreshRSS accessible depuis l'environnement de
      > développement.** Le contrat de `ClientLogin` a été établi par lecture de
      > `p/api/greader.php` (docs/freshrss-api.md §2), ce qui est suffisant pour
      > implémenter et tester au `MockEngine`. Ce qui reste non constaté :
      > la réponse d'un serveur dont l'API est désactivée, et le comportement
      > d'un reverse-proxy qui filtrerait l'en-tête `Authorization`.
      > **Le Goal se poursuit** — bloquer ici ne livrerait rien, alors que la
      > source fait foi sur la forme des requêtes et des réponses. La tâche est
      > à reprendre dès qu'une instance est disponible ; `/check/compatibility`
      > est le premier appel à passer.
- [x] `GOAL-002-T02` Modèles de `:domain` : `ServerAddress`, `Credentials`,
      `AuthToken`, type d'erreur scellé couvrant les cinq causes de SPECS.md §3.3
- [ ] `GOAL-002-T03` Normalisation de l'adresse saisie (schéma implicite, dérivation
      de `…/api/greader.php`, `http://` toléré et signalé) — pur, testé exhaustivement
- [ ] `GOAL-002-T04` Câbler Ktor dans `app/build.gradle.kts` (moteur OkHttp,
      `ContentNegotiation` restreint aux `2xx`, journalisation sans secrets)
- [ ] `GOAL-002-T05` `FreshRssApi.clientLogin()` — réponse en texte brut, paires
      `clé=valeur`
- [ ] `GOAL-002-T06` Traduction des codes HTTP en erreurs de domaine
      (`400/401/503`, corps en texte brut)
- [ ] `GOAL-002-T07` `AuthRepository` : interface dans `:domain`, implémentation
      dans `:app/data`
- [ ] `GOAL-002-T08` Stockage chiffré du jeton et du mot de passe API
      (DataStore adossé au keystore) — jamais journalisés
- [ ] `GOAL-002-T09` Récupération et conservation du jeton de modification `T`
- [ ] `GOAL-002-T10` Tests de la couche API au `MockEngine` : succès, chaque
      code d'erreur, réponse tronquée, réponse JSON là où du texte est attendu
- [ ] `GOAL-002-T11` Tests du repository, stockage chiffré compris
- [ ] `GOAL-002-T12` `LoginViewModel` et son `UiState`
- [ ] `GOAL-002-T13` Écran de connexion, avec l'explication du mot de passe API
      (SPECS.md §3.2) et un message distinct par cause d'échec
- [ ] `GOAL-002-T14` Redirection sur `401` vers l'écran de connexion, adresse et
      identifiant préservés
- [ ] `GOAL-002-T15` Captures Roborazzi de l'écran de connexion, clair et sombre,
      états d'erreur compris — **et regardées**
- [ ] `GOAL-002-T16` Reconstater `koverVerify` sur `:domain` (lève
      `GOAL-001-T14`)
- [ ] `GOAL-002-T17` Mettre à jour `ARCHITECTURE.md` §9 et `SPECS.md` §8

---

## GOAL-003 — Récupération paginée des articles

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §4.1 et §4.4. Point délicat : le curseur `continuation` est
relatif et non positionnel, et un curseur invalide provoque une **répétition
silencieuse de la première page** — voir docs/freshrss-api.md §3.5 et
ARCHITECTURE.md §4.1.

Tranche aussi SPECS.md §8 question 1 (taille de page).

---

## GOAL-004 — Cache local et résilience réseau

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §5. Réapplique Room (ARCHITECTURE.md §5.2) : plugin,
dépendances, `schemaDirectory`, schémas versionnés.

Tranche SPECS.md §8 question 3 (seuil de purge).

---

## GOAL-005 — Mélange des sources

**Statut : TODO** — à découper par `/goal`

Cœur de l'application (SPECS.md §4.2). Fonction **pure** dans `:domain`,
éprouvée exhaustivement : pas de monotonie de source, récence respectée,
déterminisme, continuité entre les pages.

Tranche SPECS.md §8 question 2.

---

## GOAL-006 — Flux Discover — interface

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §4.3 et §4.4. Liste paresseuse, clés stables, chargement
anticipé, fin de flux explicite. Retire le `PlaceholderScreen` de la destination
Discover (`GOAL-001-T15`).

Tranche SPECS.md §8 questions 5 et 6.

---

## GOAL-007 — Marquage automatique comme lu

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §4.5. Point technique le plus délicat du projet : mesurer la
proportion affichée **et** la durée continue de visibilité de chaque élément.
Seuils nommés et injectés, `Clock` pour le temps.

---

## GOAL-008 — Synchronisation du statut lu

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §4.5 (envoi par lots, optimiste, rejeu). S'appuie sur
`edit-tag` — voir docs/freshrss-api.md §4.1, dont le traitement par lot via `i`
répété.

Tranche SPECS.md §8 question 4.

---

## GOAL-009 — Tirer-pour-rafraîchir

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §4.6. Contrainte forte : préserver la position de lecture et ne
pas réordonner l'existant.

---

## GOAL-010 — Ouverture de l'article d'origine

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §4.7. *Custom Tab*, marquage à l'ouverture, article sans lien
non cliquable.

---

## GOAL-011 — Écran de réglages

**Statut : TODO** — à découper par `/goal`

Couvre SPECS.md §6. Retire le `PlaceholderScreen` de la destination Réglages
(`GOAL-001-T15`).

---

## Points bloqués

Aucun.

---

## Questions ouvertes

Les décisions fonctionnelles différées sont listées dans [SPECS.md §8](./SPECS.md).
Les incertitudes sur l'API distante sont listées dans
[docs/freshrss-api.md §6](./docs/freshrss-api.md). Chacune est tranchée par le
Goal qui la rencontre, puis **inscrite** — jamais laissée implicite dans le code.
