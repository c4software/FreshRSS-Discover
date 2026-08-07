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
**Phase 1 — API FreshRSS** ✅ terminée (GOAL-002, GOAL-003)
**Phase 2 — Flux Discover** — GOAL-004 à GOAL-007 en cours, menés en parallèle

Quatre Goals sont conduits simultanément parce que leurs surfaces ne se
recouvrent pas : le mélange et la détection de lecture sont des fonctions pures
de `:domain`, le cache vit dans `data/local/room`, l'écran dans
`presentation/discover`. Chacun est livrable et vérifiable seul ; l'assemblage
— brancher le cache dans le dépôt, la détection de lecture dans l'écran — est
une étape à part, volontairement séquentielle.

---

## Vue d'ensemble

| Goal | Titre | État |
|---|---|---|
| GOAL-001 | Harness et initialisation | `[x]` |
| GOAL-002 | Authentification FreshRSS | `[x]` |
| GOAL-003 | Récupération paginée des articles | `[x]` |
| GOAL-004 | Cache local et résilience réseau | `[-]` |
| GOAL-005 | Mélange des sources | `[-]` |
| GOAL-006 | Flux Discover — interface | `[-]` |
| GOAL-007 | Marquage automatique comme lu | `[-]` |
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
- [x] `GOAL-001-T15` ~~Retirer `PlaceholderScreen`~~ **Levé** : les deux
      destinations ont leur écran réel, l'écran d'attente et ses chaînes ont été
      supprimés.
- [ ] `GOAL-001-T16` **Icône de l'application** : celle du template est encore en
      place.
- [x] `GOAL-001-T21` ~~ktlint ne vérifie aucune source Kotlin de `:app`~~
      **Levé par `detekt-formatting`**, qui embarque les règles ktlint dans
      Detekt. Le garde-fou n'était pas décoratif : il a immédiatement révélé
      **22 violations**, dont quatre imports morts laissés par le refactor
      `AuthResult` → `Outcome`. Le constat d'origine :
      > Constaté : `./gradlew :app:tasks --all` ne montre que
      > `ktlintKotlinScriptCheck` — les fichiers `.kts`. `:domain`, lui, a bien
      > `ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck` et
      > `ktlintTestFixturesSourceSetCheck`. Le greffon ktlint-gradle 12.1.1 ne
      > découvre pas les jeux de sources Android d'AGP 9.
      >
      > **Conséquence : la commande de vérification d'AGENTS.md §5 est
      > partiellement vide depuis l'origine**, exactement comme l'était le
      > garde-fou de couverture en Phase 0. Preuve : dans `LoginScreen.kt`,
      > `LinearProgressIndicator` est importé avant `Icon` — un ordre que ktlint
      > refuse, et qui a survécu à toutes les vérifications.
      >
      > Detekt, lui, couvre bien `:app` : le formatage n'est donc pas totalement
      > sans surveillance, mais les règles de style de ktlint n'y sont pas
      > appliquées. Piste retenue : ajouter `detekt-formatting`, qui embarque les
      > règles ktlint dans Detekt, plutôt que de tenter de faire découvrir les
      > jeux de sources au greffon.
      > La correction a été différée jusqu'à la fin des travaux parallèles :
      > modifier les fichiers Gradle sous des agents en cours leur aurait fait
      > voir des violations apparues en cours de route.
- [ ] `GOAL-001-T17` **Lint Android désactivé sur les sources de test**
      (`ignoreTestSources = true`, hérité). AGP 9.3.1 plante sur ses propres
      composants d'analyse Kotlin. À réactiver dès qu'une version corrige.
- [ ] `GOAL-001-T18` **Robolectric simule l'API 35** alors que `targetSdk` vaut
      37 : aucune image n'existe pour 37. À relever dès que possible.
- [!] `GOAL-001-T19` **CI volontairement neutralisée sur `push`**
      (`branches: [never]`).
      > Décision de l'auteur : chaque exécution consomme du crédit de build, et
      > la vérification locale est exactement la même commande. Le déclencheur
      > `pull_request` reste actif — il consomme lui aussi, et se neutralise de
      > la même façon si besoin. Ce n'est pas une dette technique mais un
      > arbitrage assumé : la garantie repose entièrement sur AGENTS.md §5, dont
      > la sortie doit être **constatée** avant chaque commit.

---

## GOAL-002 — Authentification FreshRSS

**Statut : DONE**

Permettre à l'utilisateur de connecter l'application à son serveur FreshRSS et
de conserver sa session. Couvre SPECS.md §3.

Référence obligatoire : [docs/freshrss-api.md §2](./docs/freshrss-api.md).
Rappel AGENTS.md §3 : ne jamais inventer le comportement d'un point d'entrée.

- [x] `GOAL-002-T01` Constater `ClientLogin` contre un serveur réel — forme
      exacte de la réponse, codes d'erreur, comportement API désactivée — et
      mettre à jour `docs/freshrss-api.md`
      > Constaté contre `https://demo.freshrss.org/` le 2026-08-07. A **corrigé
      > une erreur de lecture de la source** : un utilisateur inconnu répond
      > `401` et non `400`, donc « inconnu » et « mauvais mot de passe » sont
      > indistinguables — ce qui est le comportement souhaitable. Autres
      > constats : sonde de reconnaissance `GET` nu → `OK` (une chaîne de requête
      > la casse), `check/compatibility` répond toujours `200` et exige un
      > en-tête `Authorization` dans sa propre requête, un chemin inconnu répond
      > `401` et non `404`.
      > **Reste non constaté** — le serveur de démonstration n'a pas de mot de
      > passe API exploitable : la réponse de succès de `ClientLogin` et le `503`
      > d'une API désactivée. Suivis en `docs/freshrss-api.md` §6, points 7 et 8.
- [x] `GOAL-002-T02` Modèles de `:domain` : `ServerAddress`, `Credentials`,
      `AuthToken`, type d'erreur scellé couvrant les cinq causes de SPECS.md §3.3
- [x] `GOAL-002-T03` `ServerAddress` et `AuthSession` : normalisation de l'adresse
      saisie (schéma implicite, dérivation de `…/api/greader.php`, `http://` toléré
      et signalé) — pure, testée exhaustivement
- [x] `GOAL-002-T04` Câbler Ktor dans `app/build.gradle.kts` (moteur OkHttp,
      négociation de contenu limitée à `application/json`, journalisation sans
      secrets) et fournir le client par Hilt
- [x] `GOAL-002-T05` `FreshRssApi` : sonde de reconnaissance, sonde de
      transmission de l'en-tête, `clientLogin()` — réponse en texte brut, paires
      `clé=valeur`
- [x] `GOAL-002-T06` Traduction des codes HTTP en erreurs de domaine
      (`400/401/404/503`, corps en texte brut, connectivité pour distinguer
      « hors ligne » de « injoignable »)
- [x] `GOAL-002-T07` `AuthRepository` : interface dans `:domain`, implémentation
      dans `:app/data`, plus `NetworkAvailability`.
      **A ajouté une sixième cause à SPECS.md §3.3** : en-tête `Authorization`
      supprimé par un reverse-proxy.
- [x] `GOAL-002-T08` Stockage chiffré du jeton (DataStore adossé au keystore) —
      jamais journalisé. **Traité avant T07**, dont le dépôt s'appuie dessus.
      **A modifié SPECS.md §3.4** : le mot de passe API n'est plus enregistré du
      tout, le jeton n'expirant pas.
- [ ] ~~`GOAL-002-T09` Récupération et conservation du jeton de modification `T`~~
      **Reporté à GOAL-008** (synchronisation du statut lu). Le jeton `T` ne sert
      qu'aux opérations modifiantes : le récupérer ici produirait un appel dont
      personne n'a l'usage, et du code mort jusqu'à GOAL-008 (AGENTS.md §2).
      `AuthSession` le porte déjà, en option, et `SessionStore` sait le
      conserver — ce qu'un test couvre.
- [x] `GOAL-002-T10` Tests de la couche API au `MockEngine` : succès, chaque
      code d'erreur, réponse tronquée, réponse JSON là où du texte est attendu.
      Écrits dans le même incrément que le code qu'ils couvrent (AGENTS.md §4),
      donc livrés par T04, T05 et T06 plutôt qu'en une passe séparée.
- [x] `GOAL-002-T11` Tests du repository, stockage chiffré compris — livrés par
      T07 et T08, même raison.
- [x] `GOAL-002-T12` `LoginViewModel` et son `UiState`
- [x] `GOAL-002-T13` Écran de connexion, avec l'explication du mot de passe API
      (SPECS.md §3.2) et un message distinct par cause d'échec
- [x] `GOAL-002-T14` Aiguillage racine par la présence d'une session ; jeton
      refusé → écran de connexion prérempli de l'adresse et de l'identifiant
- [x] `GOAL-002-T15` Tests d'écran, et captures Roborazzi de la connexion en
      clair et sombre — vide, remplie, en cours, en erreur. **Regardées** : elles
      ont révélé un indicateur de progression quasi invisible dans un bouton
      désactivé.
- [x] `GOAL-002-T16` Reconstater `koverVerify` sur `:domain` (lève
      `GOAL-001-T14`) — fait dès T02 : le seuil a réellement échoué à 86,2 %.
- [x] `GOAL-002-T17` Mettre à jour `ARCHITECTURE.md` §9 et `SPECS.md`

### Décisions prises

| Décision | Raison |
|---|---|
| Le mot de passe API n'est pas enregistré | Le jeton n'expire pas : le conserver suffit. **A modifié SPECS.md §3.4** |
| Sixième cause d'échec : en-tête `Authorization` non transmis | Sans elle, un reverse-proxy fautif ferait accuser les identifiants. **A modifié SPECS.md §3.3** |
| Chiffrement AES/GCM écrit à la main | `androidx.security:security-crypto` est déprécié (AGENTS.md §2) |
| `SecretCipher` abstrait | Robolectric ne simule pas `AndroidKeyStore` ; sans lui, persistance et effacement seraient inéprouvables |
| Sonde de reconnaissance **avant** l'envoi des identifiants | Une faute de frappe enverrait sinon le mot de passe à un serveur tiers |
| Sonde de transmission d'en-tête **après** l'obtention du jeton | Plus tôt : un aller-retour gaspillé par tentative. Plus tard : une session vouée à boucler sur des 401 |
| `invalidateSession()` distinct de `signOut()` | Un jeton refusé conserve adresse et identifiant ; une déconnexion efface tout |

### Dettes ouvertes par ce Goal

- [ ] `GOAL-002-T18` **`KeystoreSecretCipher` n'est couvert par aucun test** —
      Robolectric ne simule pas `AndroidKeyStore`. À éprouver sur appareil, ou
      par un test instrumenté, avant toute publication.
- [x] `GOAL-002-T19` ~~Deux points de l'API non constatés~~ **Levé.** La réponse
      de succès de `ClientLogin` et le `503` d'une API désactivée ont été
      observés sur une instance personnelle.
      > Le second a **corrigé une erreur de documentation** : la sonde de
      > reconnaissance répond `OK` et `200` **même API désactivée**, le
      > court-circuit qui la sert étant placé avant la vérification
      > `api_enabled`. Écrire « tous les points d'entrée répondent 503 » était
      > faux. L'implémentation était déjà correcte — c'est `ClientLogin` qui
      > révèle le `503` — mais pour une raison qui n'était pas écrite ; un test
      > la verrouille désormais.
- [x] `GOAL-002-T20` ~~Aucun appel authentifié n'existe encore~~ **Levé par
      `GOAL-003-T06`** : la lecture du flux est le premier appel authentifié, et
      un `401` y efface bien les jetons tout en conservant le rappel de saisie.

---

## GOAL-003 — Récupération paginée des articles

**Statut : DONE**

Couvre SPECS.md §4.1 et §4.4. Point délicat : le curseur `continuation` est
relatif et non positionnel, et un curseur invalide provoque une **répétition
silencieuse de la première page** — voir docs/freshrss-api.md §3.5 et
ARCHITECTURE.md §4.2.

Tranche SPECS.md §8 question 1 (taille de page).

- [x] `GOAL-003-T01` Généraliser `AuthResult` en `Outcome<T, E>` — l'échec des
      articles est le deuxième cas d'usage, donc le moment prévu par AGENTS.md §2
      pour créer l'abstraction, pas avant
- [x] `GOAL-003-T02` Modèles de `:domain` : `Article`, `ArticleId`, `FeedRef`,
      `PageCursor`, `ArticlePage`, `FeedError`
- [x] `GOAL-003-T03` DTO de `stream/contents` et désérialisation — champs
      facultatifs, unités de temps hétérogènes, `categories` porteur de l'état lu
- [x] `GOAL-003-T04` Conversion DTO → domaine : identifiant hexadécimal vers
      décimal, extraction de l'illustration, article sans lien exploitable
- [x] `GOAL-003-T05` `FreshRssApi.streamContents()` — en-tête d'autorisation,
      `n`, `c`, `xt`, et l'absence de `continuation` comme seul signal de fin
- [x] `GOAL-003-T06` `ArticleRepository` : interface `:domain`, implémentation
      `:app/data`, et `401` → `invalidateSession()` (lève `GOAL-002-T20`)
- [x] `GOAL-003-T07` Trancher la taille de page et l'inscrire dans SPECS.md §8 —
      **40**, et la question 6 (illustration) tranchée au passage. Une septième
      question s'est ouverte : le serveur ne tronque pas utilement le résumé.
- [x] `GOAL-003-T08` Mettre à jour `ARCHITECTURE.md` §9

---

## GOAL-004 — Cache local et résilience réseau

**Statut : IN PROGRESS** — la persistance est livrée, la lecture reste à câbler

Couvre SPECS.md §5.

- [x] `GOAL-004-T01` Réappliquer Room : plugin, dépendances, `schemaDirectory`,
      schéma versionné `app/schemas/…/1.json`
- [x] `GOAL-004-T02` `ArticleEntity`, `ArticleDao`, `AppDatabase`, `DatabaseModule`
- [x] `GOAL-004-T03` `ArticleCache` : `save`, `observeArticles`, `clear`,
      `purgeReadOlderThan` — 12 tests sur base en mémoire
- [x] `GOAL-004-T04` Câbler l'écriture : chaque page récupérée est déposée au
      cache, et la déconnexion le vide (SPECS.md §3.5)
- [ ] `GOAL-004-T05` Câbler la **lecture** : afficher le cache immédiatement au
      lancement, avant toute requête (SPECS.md §5.1). Demande d'étendre
      l'interface `ArticleRepository`, ce qui n'était pas possible tant que
      l'écran Discover était écrit en parallèle
- [ ] `GOAL-004-T06` Repli sur le cache hors ligne (SPECS.md §5.2) : le flux
      reste consultable, l'état est signalé sans être alarmant
- [ ] `GOAL-004-T07` Déclencher la purge et trancher son seuil (SPECS.md §8
      question 3) — `purgeReadOlderThan` n'est appelée nulle part
- [ ] `GOAL-004-T08` File des marquages en attente (ARCHITECTURE.md §5.1) :
      seconde entité et migration en version 2

### Décisions prises

| Décision | Raison |
|---|---|
| L'état lu local ne recule jamais | Un marquage parti hors ligne n'est transmis qu'au retour du réseau ; jusque-là le serveur décrit l'article comme non lu. L'écraser ferait **réapparaître ce que l'utilisateur vient de lire** — la régression la plus visible qu'un cache puisse produire. « Lu » se propage, « non lu » non |
| Purge sur l'ancienneté **dans le cache**, pas sur la date de publication | Sinon un vieil article qu'on vient d'ouvrir disparaîtrait dans la seconde, alors qu'il est encore à l'écran |
| Titre du flux dupliqué par ligne, pas de table de flux | Un seul lecteur : l'abstraction arrive avec son deuxième usage (AGENTS.md §2) |

---

## GOAL-005 — Mélange des sources

**Statut : IN PROGRESS** — la fonction est livrée, elle n'est appelée par personne

Cœur de l'application (SPECS.md §4.2). Tranche SPECS.md §8 question 2.

- [x] `GOAL-005-T01` `interleaveBySource(articles, previousTail)` — fonction pure,
      14 tests, 100 % de couverture
- [x] `GOAL-005-T02` Inscrire dans SPECS.md §4.2 l'arbitrage entre les règles 1
      et 2, que la spécification ne tranchait pas
- [ ] `GOAL-005-T03` **Appliquer le mélange au flux réel.** La fonction n'est
      appelée nulle part : c'est du code mort tant que le dépôt ou le ViewModel
      ne s'en sert pas (AGENTS.md §2)
- [ ] `GOAL-005-T04` Cas du rafraîchissement (SPECS.md §4.6) : le mélange ne doit
      porter que sur les **nouveaux** articles, l'ancienne tête servant de
      `previousTail`. La signature actuelle ne couvre pas ce cas

### Décisions prises

| Décision | Raison |
|---|---|
| La récence l'emporte sur la répartition des sources | Les deux règles sont structurellement incompatibles au-delà d'une certaine amplitude, et SPECS.md ne disait pas laquelle gagne |
| Borne de sept positions, exprimée en **rangs** et non en durée | Un seuil temporel se comporterait très différemment sur un flux qui publie trois articles par jour et sur un qui en publie trois cents. La borne en rangs est la même partout, et c'est elle que l'utilisateur perçoit |
| Fenêtre glissante plutôt que blocs fixes | Des blocs laisseraient la monotonie réapparaître à chaque jonction |

---

## GOAL-006 — Flux Discover — interface

**Statut : IN PROGRESS** — l'écran est livré, les illustrations manquent

Couvre SPECS.md §4.3 et §4.4.

- [x] `GOAL-006-T01` `DiscoverUiState`, `DiscoverPhase`, `DiscoverViewModel` :
      chargement anticipé, accumulation des pages, `loadMore` idempotent
- [x] `GOAL-006-T02` `DiscoverScreen` : liste paresseuse, clés stables,
      carte d'article, fin de flux explicite, article sans lien non cliquable
- [x] `GOAL-006-T03` Date relative et écourtement de l'extrait — fonctions pures
      testées, le temps venant de `Clock`
- [x] `GOAL-006-T04` Brancher l'écran sur la destination Discover
- [x] `GOAL-006-T05` Tests d'écran et de ViewModel, plus dix captures Roborazzi
      (flux, vide, chargement, erreur, fin) — **regardées** : aucun défaut visuel
- [x] `GOAL-006-T06` **Illustrations affichées** (Coil), rapport d'aspect stable,
      échec de chargement qui referme la carte, et contraste du réservé corrigé —
      il était strictement invisible en thème clair (ratio 1,00). Énoncé initial : Un `TODO(GOAL-006)` subsiste
      dans `DiscoverScreen` : aucune bibliothèque de chargement d'images n'est au
      projet, l'emplacement est réservé mais reste gris. Demande une dépendance
      (Coil), donc une modification des fichiers Gradle. À traiter en même temps :
      transporter `imageUrl` jusqu'à `ArticleUiModel`, poser une
      `contentDescription` — volontairement absente tant que rien n'est montré —
      et **corriger le contraste du réservé en thème clair**, constaté sur
      `discover-flux-clair.png` : `surfaceVariant` sur un conteneur de carte quasi
      identique le rend presque invisible, alors qu'il se distingue nettement en
      sombre.
- [ ] `GOAL-006-T07` Appliquer `interleaveBySource` au flux affiché — lève
      `GOAL-005-T03`
- [ ] `GOAL-006-T08` Mesurer la visibilité et alimenter `ReadDetector` — lève
      `GOAL-007-T03` et `GOAL-007-T04`

### Décisions prises

| Décision | Raison |
|---|---|
| Articles et phase de chargement **séparés** dans l'état | SPECS.md §4.4 exige qu'un échec de page suivante ne vide pas l'affichage, ce qui serait impossible si la liste ne vivait que dans le cas « chargé » d'un type scellé |
| Cinq phases distinctes plutôt que des booléens croisés | Deux drapeaux indépendants autoriseraient l'état ambigu « ni en cours, ni fini, ni en erreur », c'est-à-dire exactement la liste qui cesse de s'allonger sans rien dire |
| `SessionExpired` n'affiche **rien** mais arrête les demandes | L'écran va disparaître ; sans arrêt explicite, le défilement réclamerait une page à chaque image jusqu'à la bascule |
| « Flux vide » distingué de « fin de flux » | « Vous avez tout lu » sous une liste vide n'explique rien |

---

## GOAL-007 — Marquage automatique comme lu

**Statut : IN PROGRESS** — la décision est livrée, la mesure ne l'est pas

Couvre SPECS.md §4.5.

- [x] `GOAL-007-T01` `ReadDetector` : double seuil surface + durée continue,
      seuils injectés, `Clock` pour le temps — 18 tests, 100 % de couverture
- [x] `GOAL-007-T02` Lever les deux ambiguïtés de SPECS.md §4.5 que
      l'implémentation a révélées
- [ ] `GOAL-007-T03` **Mesurer réellement la visibilité** dans la liste Discover
      et alimenter le détecteur. C'est la moitié difficile de ce Goal
- [ ] `GOAL-007-T04` **Émettre une observation périodique quand la liste est
      immobile.** Sans cela, un article resté dix secondes à l'écran ne sera
      jamais marqué lu : la durée ne s'écoule pas toute seule
- [ ] `GOAL-007-T05` Relier le détecteur au marquage optimiste et au cache

### Décisions prises

| Décision | Raison |
|---|---|
| Les deux seuils sont **inclusifs** | SPECS.md dit « au moins » ; et 0,6 n'est pas représentable exactement en binaire — un seuil exclusif rendrait la règle dépendante de l'arrondi fait par l'interface |
| Les articles déjà signalés sont retenus pour la vie du détecteur | C'est le prix de la garantie « jamais signalé deux fois ». Le coût est borné par ce que l'utilisateur a lu, pas par le nombre d'observations |

---

## GOAL-008 — Synchronisation du statut lu

**Statut : IN PROGRESS** — le socle est livré, rien n'est orchestré

- [x] `GOAL-008-T01` `FreshRssApi.modificationToken()` et `markAsRead()` —
      12 tests, dont l'identifiant non signé
- [x] `GOAL-008-T02` `PendingMarkEntity`, `PendingMarkDao`, `PendingMarkQueue`,
      et migration réelle `AppDatabase` 1 → 2 — 11 tests, migration comprise
- [x] `GOAL-008-T03` **`addMigrations(MIGRATION_1_2)` déclarée** dans
      `DatabaseModule`, avec `providePendingMarkDao`. Sans elle, tout appareil
      déjà en version 1 aurait planté au premier accès — invisible aux tests,
      qui construisent la base en mémoire, donc toujours à la version courante
- [ ] `GOAL-008-T04` Orchestrer : `enqueue` → jeton → `markAsRead` →
      `acknowledge`, plus le rejeu au démarrage
- [ ] `GOAL-008-T05` Sur `401` pendant `markAsRead`, redemander **une fois** le
      jeton de modification avant de conclure à une perte de session
- [ ] `GOAL-008-T06` Vider la file à la déconnexion, là où le cache l'est déjà
- [ ] `GOAL-008-T07` Trancher la taille de lot et le délai de regroupement
      (SPECS.md §8 question 4)

### Décisions prises

| Décision | Raison |
|---|---|
| `OnConflictStrategy.IGNORE` plutôt que `REPLACE` | Le dédoublonnage préserve la date de mise en file d'origine. Avec `REPLACE`, un article fréquemment revu verrait son horodatage repoussé et **pourrait ne jamais atteindre la tête de file** |
| Tri sur `(date, identifiant)` et non sur la seule date | Sans second critère, une transmission partielle pourrait retomber en boucle sur le même lot |
| `acknowledge` distincte de `pending` | Retirer avant confirmation perdrait le marquage sur un échec réseau — précisément ce que la file existe pour empêcher |
| Migration réelle, pas de `fallbackToDestructiveMigration` | Une migration destructive viderait le cache et **les marquages non transmis** de tout utilisateur existant |
| La longueur du jeton n'est pas validée | Un jeton refusé se signale par un `401`, pas par sa taille |

> ⚠️ **Piège identifié d'avance.** Un identifiant d'article dépassant
> `Long.MAX_VALUE` est conservé sous forme de bits, donc **négatif** en Kotlin.
> Le reformater avec `toString()` enverrait `-1` au serveur : c'est
> `java.lang.Long.toUnsignedString` qu'il faut employer pour le paramètre `i`
> d'`edit-tag`. Constaté en écrivant les tests de conversion (GOAL-003-T04).

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

**Statut : TODO**

Couvre SPECS.md §4.7.

- [x] `GOAL-010-T01` `ArticleOpener` : onglet personnalisé, filtrage des schémas,
      absence de navigateur traitée — 17 tests
- [x] `GOAL-010-T02` Ouvreur branché : `ArticleUiModel` transporte désormais
      `url`, et `isOpenable` en est dérivé par défaut
- [ ] `GOAL-010-T03` Marquer l'article comme lu à l'ouverture, quelle que soit
      sa visibilité passée (SPECS.md §4.7) — décision de ViewModel
- [ ] `GOAL-010-T04` Message explicite si l'ouverture échoue hors ligne
      (SPECS.md §5.2), et si aucun navigateur n'est installé — `ArticleOpener`
      distingue déjà `Ignored` de `NoBrowser`, personne ne lit encore ce retour

### Décisions prises

| Décision | Raison |
|---|---|
| Seuls `http` et `https` sont ouverts | Le lien d'un flux RSS est du **contenu tiers non maîtrisé**. Laisser passer `intent:`, `javascript:` ou `file:` reviendrait à laisser un serveur distant décider de ce que fait le téléphone |
| Aucune préconnexion, aucun `warmup`, aucune session liée | SPECS.md §7.4 : l'ouverture est une action **de l'utilisateur**. Un préchargement serait une requête sortante qu'il n'a pas demandée. Prix payé et assumé : ouverture un peu moins rapide |
| L'ouvreur revalide l'URL que l'écran a déjà filtrée | Il ne fait pas confiance à son appelant : la garantie doit tenir même si un futur écran oublie le filtre |
| Barre d'onglet en `surface`, pas `primary` | L'onglet prolonge l'écran qu'il recouvre |

---

## GOAL-011 — Écran de réglages

**Statut : IN PROGRESS** — l'écran existe, une seule action y est branchée

Couvre SPECS.md §6.

- [x] `GOAL-011-T01` `SettingsUiState`, `SettingsViewModel`, `SettingsScreen`,
      `SettingsTestTags` — 18 tests, 4 captures **regardées**
- [x] `GOAL-011-T02` Déconnexion avec confirmation (SPECS.md §3.5) : les deux
      issues sont testées, l'annulation n'appelle pas `signOut()`
- [x] `GOAL-011-T03` Écran branché sur la destination Réglages, dernier
      `PlaceholderScreen` retiré (lève `GOAL-001-T15`)
- [ ] `GOAL-011-T04` **Persister les seuils de marquage.** Aucun stockage de
      réglages n'existe : les valeurs affichées **recopient** les défauts privés
      de `ReadDetector`, et rien n'empêche les deux déclarations de diverger
- [ ] `GOAL-011-T05` Mesurer la taille du cache et brancher la purge manuelle —
      le bouton est volontairement **désactivé plutôt qu'absent**, pour que la
      fonctionnalité soit annoncée
- [x] `GOAL-011-T06` **Licence choisie : MIT.** `LICENSE` ajouté à la racine,
      l'écran de réglages affiche « Licence MIT ». L'agent avait refusé d'en
      inventer une et affichait « Non encore déterminée » — c'était la bonne
      conduite : la licence est une décision d'auteur, pas un détail à combler

### Décisions prises

| Décision | Raison |
|---|---|
| Les seuils sont affichés mais non modifiables | Les rendre modifiables sans stockage donnerait un réglage qui ne survit pas à la fermeture — pire qu'un réglage absent |
| Le bouton de purge est désactivé, pas caché | Annoncer la fonctionnalité vaut mieux que la faire découvrir plus tard ; la phrase au-dessus explique pourquoi il ne répond pas |
| L'unité de conversion est faite dans le ViewModel | `0.6f → 60 %` et `1000 ms → 1 s` sont des calculs : AGENTS.md §9 les interdit dans un Composable |

---

## Points bloqués

Aucun.

---

## Questions ouvertes

Les décisions fonctionnelles différées sont listées dans [SPECS.md §8](./SPECS.md).
Les incertitudes sur l'API distante sont listées dans
[docs/freshrss-api.md §6](./docs/freshrss-api.md). Chacune est tranchée par le
Goal qui la rencontre, puis **inscrite** — jamais laissée implicite dans le code.
