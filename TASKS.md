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
**Phase 2 — Flux Discover** ✅ assemblée et livrée

Treize Goals sur quatorze sont terminés. Ce qui reste tient en cinq points,
constatés un par un le 2026-08-08 et inscrits chacun dans la section de son
Goal — aucun n'appartient à un chantier commun :

| Point | Ce qui manque, constaté |
|---|---|
| `GOAL-012-T05` | Le mode Balayage n'enregistre aucune position : `ReadingPositionViewModel` n'est branché que dans `DiscoverRoute` |
| `GOAL-012-T07` | Bloqué : le balayage horizontal n'est praticable ni au lecteur d'écran ni sans précision du poignet |
| `GOAL-002-T18` | `KeystoreSecretCipher` n'a toujours aucun test propre — seul `AppGraphTest` le traverse |
| `GOAL-001-T17` | `ignoreTestSources = true` toujours en place, AGP plantant sur ses propres composants |
| `GOAL-001-T18` | Robolectric simule l'API 35, `targetSdk` vaut 37 |
| `GOAL-001-T19` | Bloqué par décision d'auteur : CI neutralisée sur `push` |

**Prochaine tâche** : `GOAL-012-T05` — position de lecture partagée entre les
deux modes.

---

## Vue d'ensemble

| Goal | Titre | État |
|---|---|---|
| GOAL-001 | Harness et initialisation | `[x]` |
| GOAL-002 | Authentification FreshRSS | `[x]` |
| GOAL-003 | Récupération paginée des articles | `[x]` |
| GOAL-004 | Cache local et résilience réseau | `[x]` |
| GOAL-005 | Mélange des sources | `[x]` |
| GOAL-006 | Flux Discover — interface | `[x]` |
| GOAL-007 | Marquage automatique comme lu | `[x]` |
| GOAL-008 | Synchronisation du statut lu | `[x]` |
| GOAL-009 | Tirer-pour-rafraîchir | `[x]` |
| GOAL-010 | Ouverture de l'article d'origine | `[x]` |
| GOAL-011 | Écran de réglages | `[x]` |
| GOAL-012 | Vue Balayage, article par article | `[-]` |
| GOAL-013 | Rappel de lecture par notification locale | `[x]` |
| GOAL-014 | Toast d'ancienneté du flux | `[x]` |

L'état porté ici est celui de la section du Goal, qui fait foi. Les Goals sont
découpés en tâches par `/goal` au moment de les entreprendre : les découper
d'avance reviendrait à décider sans connaître l'état du code (AGENTS.md §2,
« ne pas anticiper »).

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
- [x] `GOAL-001-T22` ~~L'application n'a jamais été lancée.~~ **Levé le
      2026-08-07** : installée et exécutée sur un Pixel 10 Pro, connectée à une
      instance FreshRSS réelle. Le parcours complet fonctionne — connexion, flux
      d'articles réels avec illustrations, marquage automatique **transmis au
      serveur** (49 articles en cache dont 11 lus et synchronisés), réglages,
      déconnexion avec confirmation.
      > **Trois défauts que 487 tests et 30 captures n'avaient pas vus** :
      > 1. l'écran de connexion passait **sous la barre d'état**, son titre
      >    chevauché par l'heure. Les captures rendent le Composable isolé, sans
      >    barres système : elles ne pouvaient pas le voir ;
      > 2. son titre était **noir sur noir** en thème sombre, faute de `Surface`
      >    à la racine. Voir ci-dessous, c'est le plus instructif ;
      > 3. l'écran de réglages affiche **deux titres empilés** — « Paramètres »
      >    dans la barre, « Réglages » dans l'écran.
- [x] `GOAL-001-T23` **Le harnais de capture masquait un défaut de production.**
      > Le titre noir sur noir avait déjà été rencontré en Phase 0, sur une
      > capture. Il avait été corrigé **dans le harnais** — un `Surface` ajouté à
      > `ScreenshotTest` — plutôt que dans l'application. Les images sont
      > redevenues correctes pendant que la production restait fautive, et le
      > défaut n'a resurgi qu'à la première exécution réelle, des Goals plus tard.
      >
      > Corrigé à la racine : `MainActivity` enveloppe désormais l'application
      > dans un `Surface`. Le harnais et la production coïncident enfin.
      > La règle est inscrite dans AGENTS.md §4.1 : **quand une capture révèle un
      > défaut, on corrige l'application, jamais le harnais.**
- [x] `GOAL-001-T24` ~~Deux titres empilés dans l'écran de réglages~~ **Levé** :
      le titre d'écran est retiré, la barre du `Scaffold` suffit. Énoncé initial :
      « Paramètres » (barre de titre) et « Réglages » (en-tête d'écran). La barre
      affiche déjà le libellé de la destination — l'en-tête est redondant, et les
      deux mots diffèrent pour désigner la même chose.
      > 487 tests passent, 30 captures sont conformes, et pourtant **aucune
      > exécution réelle n'a eu lieu** : ni sur appareil, ni sur émulateur.
      > Tenté le 2026-08-07, `adb devices` ne renvoie aucun appareil.
      >
      > Ce que les tests ne peuvent pas établir, et qui n'est donc pas établi :
      > l'ouverture réelle de la base Room sur disque, le fonctionnement du
      > chiffrement `AndroidKeyStore` — non couvert par construction, voir
      > `GOAL-002-T18` — l'onglet personnalisé, le chargement d'images par le
      > réseau, et le comportement de la liste au défilement réel, sur lequel
      > repose tout le marquage automatique.
      >
      > **À faire avant toute annonce de fonctionnement** :
      > `./gradlew :app:installDebug` puis
      > `adb shell am start -n fr.vbrosseau.freshrssdiscover/.MainActivity`,
      > avec une instance FreshRSS réelle.
- [x] `GOAL-001-T16` **Icône de l'application** : « le fil », icône adaptative
      dessinée pour l'application — un ruban qui descend en serpentant, plutôt
      que les ondes RSS que porte déjà tout autre lecteur. Fond, calque avant et
      monochrome. Celle du template n'est plus en place.
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
- [x] `GOAL-001-T18` **Robolectric relevé de l'API 35 à 36**, le dernier niveau
      qu'il sait instancier — 37 lève `UnknownSdk`, essayé avant de trancher.
      Un écart d'un niveau subsiste avec `targetSdk`, et il ne se refermera
      qu'avec une version de Robolectric qui porte l'image 37.
      Le rendu bouge un peu au passage : les 48 références ont été
      réenregistrées et **regardées** en comparaison. Seul l'anticrénelage des
      arrondis diffère — curseurs, interrupteur, coins de carte — la mise en
      page, les textes et les couleurs sont inchangés.
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
      > d'une API désactivée. **Constatés depuis** — voir la section « Ce qui a
      > été constaté » de `docs/freshrss-api.md`.
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

**Statut : DONE** — cache lu au lancement et hors ligne, purge et file livrées

Couvre SPECS.md §5.

- [x] `GOAL-004-T01` Réappliquer Room : plugin, dépendances, `schemaDirectory`,
      schéma versionné `app/schemas/…/1.json`
- [x] `GOAL-004-T02` `ArticleEntity`, `ArticleDao`, `AppDatabase`, `DatabaseModule`
- [x] `GOAL-004-T03` `ArticleCache` : `save`, `observeArticles`, `clear`,
      `purgeReadOlderThan` — 12 tests sur base en mémoire
- [x] `GOAL-004-T04` Câbler l'écriture : chaque page récupérée est déposée au
      cache, et la déconnexion le vide (SPECS.md §3.5)
- [x] `GOAL-004-T05` `observeCachedArticles()` : le cache s'affiche avant toute
      requête (SPECS.md §5.1)
- [x] `GOAL-004-T06` Repli hors ligne complet : bandeau discret par-dessus le
      contenu du cache, écran plein cadre réservé au cas **sans aucun article**
- [x] `GOAL-004-T07` **Purge déclenchée, seuil tranché à 7 jours** (SPECS.md §8,
      question 3), une fois par démarrage de processus.
      > ⚠️ **Un défaut sérieux corrigé au passage.** La purge ne testait que
      > « lu **et** assez ancien » : sur un appareil hors ligne plus longtemps
      > que le seuil, elle effaçait un article dont le marquage attendait encore
      > d'être transmis. La file survivait, mais **la mémoire locale du « déjà
      > lu » partait avec la ligne** — elle ne vit nulle part ailleurs. Au
      > rafraîchissement suivant le serveur redécrivait l'article comme non lu,
      > plus rien ne le contredisait, et il **réapparaissait dans le flux**.
      > Corrigé par une exclusion explicite des marquages en attente, et
      > SPECS.md §5.3 dit désormais littéralement pourquoi.
- [x] `GOAL-004-T08` File des marquages en attente (ARCHITECTURE.md §5.1) :
      `PendingMarkEntity`, `PendingMarkDao` et `PendingMarkQueue`, base en
      version 2 avec ses deux schémas versionnés (`app/schemas/1.json` et
      `2.json`). Couverte par `PendingMarkQueueTest`

### Décisions prises

| Décision | Raison |
|---|---|
| L'état lu local ne recule jamais | Un marquage parti hors ligne n'est transmis qu'au retour du réseau ; jusque-là le serveur décrit l'article comme non lu. L'écraser ferait **réapparaître ce que l'utilisateur vient de lire** — la régression la plus visible qu'un cache puisse produire. « Lu » se propage, « non lu » non |
| Purge sur l'ancienneté **dans le cache**, pas sur la date de publication | Sinon un vieil article qu'on vient d'ouvrir disparaîtrait dans la seconde, alors qu'il est encore à l'écran |
| Titre du flux dupliqué par ligne, pas de table de flux | Un seul lecteur : l'abstraction arrive avec son deuxième usage (AGENTS.md §2) |

---

## GOAL-005 — Mélange des sources

**Statut : DONE** — `interleaveBySource` ordonne les pages du serveur et du cache

Cœur de l'application (SPECS.md §4.2). Tranche SPECS.md §8 question 2.

- [x] `GOAL-005-T01` `interleaveBySource(articles, previousTail)` — fonction pure,
      14 tests, 100 % de couverture
- [x] `GOAL-005-T02` Inscrire dans SPECS.md §4.2 l'arbitrage entre les règles 1
      et 2, que la spécification ne tranchait pas
- [x] `GOAL-005-T03` **Mélange appliqué** : `loadPage` et `refresh` rendent
      désormais l'ordre d'affichage. La fonction n'est plus du code mort
- [x] `GOAL-005-T04` Cas du rafraîchissement tranché : `refresh()` mélange la
      première page **entre ses seuls articles** — rien ne la précède — et le
      dédoublonnage revient à l'appelant, seul à savoir ce qui est à l'écran

### Décisions prises

| Décision | Raison |
|---|---|
| La récence l'emporte sur la répartition des sources | Les deux règles sont structurellement incompatibles au-delà d'une certaine amplitude, et SPECS.md ne disait pas laquelle gagne |
| Borne de sept positions, exprimée en **rangs** et non en durée | Un seuil temporel se comporterait très différemment sur un flux qui publie trois articles par jour et sur un qui en publie trois cents. La borne en rangs est la même partout, et c'est elle que l'utilisateur perçoit |
| Fenêtre glissante plutôt que blocs fixes | Des blocs laisseraient la monotonie réapparaître à chaque jonction |

---

## GOAL-006 — Flux Discover — interface

**Statut : DONE** — écran, illustrations et états de chargement livrés

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
- [x] `GOAL-006-T07` Mélange appliqué par le dépôt
- [x] `GOAL-006-T08` Visibilité mesurée et détecteur alimenté

### Décisions prises

| Décision | Raison |
|---|---|
| Articles et phase de chargement **séparés** dans l'état | SPECS.md §4.4 exige qu'un échec de page suivante ne vide pas l'affichage, ce qui serait impossible si la liste ne vivait que dans le cas « chargé » d'un type scellé |
| Cinq phases distinctes plutôt que des booléens croisés | Deux drapeaux indépendants autoriseraient l'état ambigu « ni en cours, ni fini, ni en erreur », c'est-à-dire exactement la liste qui cesse de s'allonger sans rien dire |
| `SessionExpired` n'affiche **rien** mais arrête les demandes | L'écran va disparaître ; sans arrêt explicite, le défilement réclamerait une page à chaque image jusqu'à la bascule |
| « Flux vide » distingué de « fin de flux » | « Vous avez tout lu » sous une liste vide n'explique rien |

---

## GOAL-007 — Marquage automatique comme lu

**Statut : DONE** — la mesure de visibilité alimente le détecteur dans les deux modes

Couvre SPECS.md §4.5.

- [x] `GOAL-007-T01` `ReadDetector` : double seuil surface + durée continue,
      seuils injectés, `Clock` pour le temps — 18 tests, 100 % de couverture
- [x] `GOAL-007-T02` Lever les deux ambiguïtés de SPECS.md §4.5 que
      l'implémentation a révélées
- [x] `GOAL-007-T03` Mesure de la visibilité dans la `LazyColumn` — fonction
      pure `visibleFraction`, 22 tests
- [x] `GOAL-007-T04` Observation périodique à 200 ms, arrêtée hors premier plan
- [x] `GOAL-007-T05` Détecteur relié au marquage optimiste : `markAsRead` puis
      `flush`, et rejeu au démarrage
- [x] `GOAL-007-T06` `onVisibilityChanged` passé depuis `AppNavHost` — la mesure
      s'exécute réellement
- [x] `GOAL-007-T07` `ReadDetector` construit depuis les réglages observés, et
      reconstruit à chaque changement — sans redémarrage

### Décisions prises

| Décision | Raison |
|---|---|
| La fraction se mesure sur `min(hauteur de l'article, hauteur de la fenêtre)` | Un article plus haut que l'écran plafonnerait sinon sous 60 % et ne serait **jamais** marqué lu |
| Cadence d'observation à 200 ms | Le retard maximal vaut une période : le seuil d'une seconde se déclenche entre 1,0 s et 1,2 s. À 16 ms on réveillerait la coroutine 60 fois par seconde pour une règle dont l'unité est la seconde ; à 1 s on pourrait doubler le seuil annoncé |
| Observation liée à `RESUMED`, pas `STARTED` | `STARTED` inclut l'écran derrière une boîte de dialogue : des articles seraient marqués lus sans être lus |
| `onVisibilityChanged` **nullable**, nul par défaut | Armer une boucle périodique sans destinataire brûlerait de la batterie et rendrait les tests de rendu perpétuellement occupés. `null` dit « personne n'écoute », ce qu'un `{}` ne peut pas exprimer |
| `ReadDetector` construit dans le ViewModel, non injecté | Son état est propre à cette liste ; injecté, il survivrait à l'écran et croirait déjà signalés des articles réaffichés |

### Décisions prises

| Décision | Raison |
|---|---|
| Les deux seuils sont **inclusifs** | SPECS.md dit « au moins » ; et 0,6 n'est pas représentable exactement en binaire — un seuil exclusif rendrait la règle dépendante de l'arrondi fait par l'interface |
| Les articles déjà signalés sont retenus pour la vie du détecteur | C'est le prix de la garantie « jamais signalé deux fois ». Le coût est borné par ce que l'utilisateur a lu, pas par le nombre d'observations |

---

## GOAL-008 — Synchronisation du statut lu

**Statut : DONE** — lots, file d'attente, rejeu au démarrage et transmission forcée

- [x] `GOAL-008-T01` `FreshRssApi.modificationToken()` et `markAsRead()` —
      12 tests, dont l'identifiant non signé
- [x] `GOAL-008-T02` `PendingMarkEntity`, `PendingMarkDao`, `PendingMarkQueue`,
      et migration réelle `AppDatabase` 1 → 2 — 11 tests, migration comprise
- [x] `GOAL-008-T03` **`addMigrations(MIGRATION_1_2)` déclarée** dans
      `DatabaseModule`, avec `providePendingMarkDao`. Sans elle, tout appareil
      déjà en version 1 aurait planté au premier accès — invisible aux tests,
      qui construisent la base en mémoire, donc toujours à la version courante
- [x] `GOAL-008-T04` `ReadSyncRepository` : marquage optimiste, lots de 100,
      acquittement après confirmation, rejeu au démarrage — 30 tests
- [x] `GOAL-008-T05` Sur `401`, le jeton de modification est redemandé **une
      seule fois** ; un second `401` conclut à une session perdue **sans vider
      la file**
- [x] `GOAL-008-T06` La déconnexion vide la file, là où le cache l'est déjà
- [x] `GOAL-008-T08` Le marquage local repasse par `ArticleCache` et non par le
      DAO. L'agent avait dû court-circuiter l'enveloppe, faute d'accès en
      écriture dans son périmètre ; il l'a signalé plutôt que de le taire
- [x] `GOAL-008-T07` Taille de lot **100**, fenêtre de regroupement **5 s à
      échéance fixe** (SPECS.md §8, question 4) — 17 tests
- [x] `GOAL-008-T09` Forcer la transmission au passage en arrière-plan :
      `ReadFlushOnBackgroundObserver` appelle `flush()` sur `ON_STOP` — et non
      `ON_PAUSE`, qui se déclenche dès qu'une autre fenêtre passe devant. Sur la
      portée de l'application, pour que la transmission survive à la destruction
      de l'écran. Couvert par `ReadFlushOnBackgroundObserverTest`

### Décisions prises

| Décision | Raison |
|---|---|
| `OnConflictStrategy.IGNORE` plutôt que `REPLACE` | Le dédoublonnage préserve la date de mise en file d'origine. Avec `REPLACE`, un article fréquemment revu verrait son horodatage repoussé et **pourrait ne jamais atteindre la tête de file** |
| Tri sur `(date, identifiant)` et non sur la seule date | Sans second critère, une transmission partielle pourrait retomber en boucle sur le même lot |
| `acknowledge` distincte de `pending` | Retirer avant confirmation perdrait le marquage sur un échec réseau — précisément ce que la file existe pour empêcher |
| Migration réelle, pas de `fallbackToDestructiveMigration` | Une migration destructive viderait le cache et **les marquages non transmis** de tout utilisateur existant |
| La longueur du jeton n'est pas validée | Un jeton refusé se signale par un `401`, pas par sa taille |
| Lot de **100** articles | Par le bas : une page fait 40 articles, un lot plus petit ferait plusieurs requêtes pour une page parcourue. Par le haut : chaque article est un champ `i`, et PHP n'accepte par défaut que 1 000 champs (`max_input_vars`) — au-delà **les champs excédentaires sont ignorés en silence**, et `edit-tag` répond `OK` sans compte-rendu. La perte serait totalement muette |
| Fenêtre de regroupement **fixe**, non glissante | Un défilement continu produit un lot toutes les 200 ms : une fenêtre relançable ne se refermerait **jamais** tant que l'utilisateur lit, et la transmission n'aurait lieu qu'à la fermeture |
| Un `5xx` sur `/token` ne déconnecte pas | Seul un `401` signifie « jeton refusé ». Une panne serveur ferait sinon perdre la session à chaque hoquet |

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

**Statut : DONE** — validé sur appareil

Couvre SPECS.md §4.6.

- [x] `GOAL-009-T01` `ArticleRepository.refresh()` — rend la première page du
      jour, sans toucher au curseur de pagination
- [x] `GOAL-009-T02` Geste de tirage et indicateur — 31 tests, 3 captures
- [x] ~~`GOAL-009-T03` Insertion en tête des seuls inconnus~~ **Remplacé par
      `GOAL-009-T04`** : la spécification a changé à la demande de l'auteur
- [x] `GOAL-009-T04` **Le tirage vide la liste, recharge et remonte en haut**
      (SPECS.md §4.6 réécrit). Insérer en tête préservait la lecture mais rendait
      le geste presque invisible — on tirait, et rien ne semblait se passer
- [x] `GOAL-009-T05` **La position de lecture survit à la fermeture**
      (SPECS.md §5.3, nouvelle section) : `ReadingPositionViewModel` et
      `ReadingPositionStore`. C'est la contrepartie exacte du tirage — une
      fermeture n'est pas une demande de l'utilisateur.
      **Corrigé après essai sur appareil** : la première version ne retenait que
      l'identifiant, or l'article de tête est précisément celui que le marquage
      vient de rendre lu, et le flux ne montre que des non-lus — la reprise ne
      pouvait presque jamais aboutir. La date de publication part désormais avec
      l'identifiant, et `ReadingPosition` reprend au plus proche, ce que §5.3
      demandait déjà
- [x] `GOAL-009-T06` **Validé sur appareil** (Pixel 10 Pro, Android 17) :
      tirage → la liste est vidée, rechargée et remontée en haut ; six écrans de
      défilement puis un `am force-stop` → l'application rouvre exactement sur
      l'article qui était en tête. C'est cet essai qui avait révélé le défaut de
      reprise corrigé en `GOAL-009-T05`

---

## GOAL-010 — Ouverture de l'article d'origine

**Statut : DONE**

Couvre SPECS.md §4.7.

- [x] `GOAL-010-T01` `ArticleOpener` : onglet personnalisé, filtrage des schémas,
      absence de navigateur traitée — 17 tests
- [x] `GOAL-010-T02` Ouvreur branché : `ArticleUiModel` transporte désormais
      `url`, et `isOpenable` en est dérivé par défaut
- [x] `GOAL-010-T03` Article marqué lu à l'ouverture, quelle que soit sa
      visibilité passée
- [x] `GOAL-010-T04` Ouverture refusée hors ligne, avec un avis **acquitté à la
      main** — un message qui s'efface seul se rate. L'article n'est pas marqué

### Décisions prises

| Décision | Raison |
|---|---|
| Seuls `http` et `https` sont ouverts | Le lien d'un flux RSS est du **contenu tiers non maîtrisé**. Laisser passer `intent:`, `javascript:` ou `file:` reviendrait à laisser un serveur distant décider de ce que fait le téléphone |
| Aucune préconnexion, aucun `warmup`, aucune session liée | SPECS.md §7.4 : l'ouverture est une action **de l'utilisateur**. Un préchargement serait une requête sortante qu'il n'a pas demandée. Prix payé et assumé : ouverture un peu moins rapide |
| L'ouvreur revalide l'URL que l'écran a déjà filtrée | Il ne fait pas confiance à son appelant : la garantie doit tenir même si un futur écran oublie le filtre |
| Barre d'onglet en `surface`, pas `primary` | L'onglet prolonge l'écran qu'il recouvre |

---

## GOAL-011 — Écran de réglages

**Statut : DONE** — déconnexion, purge, seuils de lecture, mode d'affichage et rappel

Couvre SPECS.md §6.

- [x] `GOAL-011-T01` `SettingsUiState`, `SettingsViewModel`, `SettingsScreen`,
      `SettingsTestTags` — 18 tests, 4 captures **regardées**
- [x] `GOAL-011-T02` Déconnexion avec confirmation (SPECS.md §3.5) : les deux
      issues sont testées, l'annulation n'appelle pas `signOut()`
- [x] `GOAL-011-T03` Écran branché sur la destination Réglages, dernier
      `PlaceholderScreen` retiré (lève `GOAL-001-T15`)
- [x] `GOAL-011-T04` **Seuils modifiables et persistés**, curseurs à crans,
      bornes validées dans le domaine — 36 tests. La duplication des défauts est
      supprimée : l'affichage observe le dépôt, il ne recopie plus rien
- [x] `GOAL-011-T05` Taille du cache affichée et purge manuelle branchée —
      28 tests. La taille est un **nombre d'articles**, pas des octets : SQLite ne
      rend pas ses pages au système, une purge laisserait les mégaoctets
      inchangés et se lirait comme sans effet
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

## GOAL-012 — Vue Balayage, article par article

**Statut : IN PROGRESS** — le mode existe, se choisit et a été essayé sur
appareil ; `T05` reste ouvert

Couvre SPECS.md §4.8, ajouté à la demande de l'auteur. Un mode de présentation
alternatif : un article en plein écran, balayage horizontal pour passer au
suivant, comme les Stories d'un réseau social.

### Ce qui est déjà tranché

| Point | Décision |
|---|---|
| « Flux suivant » désigne l'**article** suivant | Pas la source ni la catégorie : SPECS.md §1 et §2 excluent toute navigation par flux ou par dossier, et cela reste vrai |
| Les deux modes **coexistent** | L'écran en liste est conservé, avec sa liste paresseuse, son chargement anticipé et sa mesure de visibilité. Le mode se choisit dans les réglages |
| Le contenu est **identique** | Mêmes articles, même mélange, mêmes règles. Seule la présentation change — basculer de mode ne réordonne rien (règle 3 de §4.2) |

### Ce qui reste à concevoir, et qui n'est pas anodin

- [x] `GOAL-012-T01` **La mesure de visibilité change de nature.** Un article
      plein écran est visible à 100 % : le seuil de surface est satisfait
      d'emblée, et la durée décide seule. `ReadDetector` s'applique tel quel,
      mais l'alimentation ne peut pas venir de `LazyListState` — il faut une
      source d'observation propre à ce mode.
      **Le maillon est désormais éprouvé de bout en bout** : quatre tests
      d'écran vérifient que le relevé part, qu'il se répète alors que rien ne
      bouge, qu'il suit le balayage, et qu'il ne s'arme pas sans destinataire.
      Ils ont été validés par mutation — couper l'observation en fait tomber
      trois, la rétablir les repasse. Ni `SwipeViewModelTest` ni
      `SwipeVisibilityTest` ne voyaient ce maillon : le premier suppose qu'on
      lui parle, le second calcule sans que personne l'appelle
- [x] `GOAL-012-T02` **Le chargement anticipé doit survivre au geste.** Demander
      la page suivante avant d'atteindre le dernier article chargé, sans que le
      balayage ne bute
- [x] `GOAL-012-T03` **La fin du flux doit se dire**, comme en mode Liste : un
      balayage qui cesse de répondre est indistinguable d'une panne (§4.4)
- [x] `GOAL-012-T04` **Le retour en arrière ne délit pas.** Revenir sur un
      article lu ne le remet pas en non-lu — le marquage n'est pas réversible
      par un geste de navigation
- [ ] `GOAL-012-T05` **Position partagée entre les deux modes.** Basculer de
      l'un à l'autre doit retrouver le flux au même endroit.
      **Volontairement laissé ouvert, et non oublié.** Le mode Balayage
      n'enregistre aujourd'hui aucune position : en plein écran, l'article
      courant est celui que le marquage vient de rendre lu, et le pagineur
      repartirait donc sur un article absent du flux suivant. La reprise au plus
      proche introduite par `GOAL-009-T05` lève cet obstacle — c'est elle qu'il
      faut désormais brancher ici, plutôt qu'un second mécanisme
- [x] `GOAL-012-T06` Réglage persistant du mode, dans l'écran de réglages (§6)
- [!] `GOAL-012-T07` Accessibilité : un balayage horizontal n'est pas praticable
      par tout le monde. Prévoir une alternative — SPECS.md §7.1 exige que
      l'application reste utilisable, et un geste unique ne le garantit pas.
      **Rouvert.** Les deux boutons « Précédent » / « Suivant » qui répondaient
      à cette tâche ont été retirés à la demande de l'auteur, qui les juge
      superflus une fois l'animation en place. Le balayage horizontal est donc
      redevenu le seul moyen d'avancer — or c'est précisément le geste qu'un
      lecteur d'écran se réserve pour sa propre exploration. Une alternative
      reste à trouver qui n'encombre pas l'écran : action d'accessibilité
      personnalisée, ou appui sur les bords
- [x] `GOAL-012-T08` Captures Roborazzi du mode Balayage, clair et sombre —
      **et regardées** : six images enregistrées. Puis **exécution réelle sur
      appareil** (Pixel 10 Pro, Android 17), qui est ce qui compte : le mode
      Liste avait montré que trois défauts sur trois n'étaient visibles
      qu'ainsi. Le réglage bascule, le mode se relit au démarrage suivant, le
      balayage passe à l'article suivant et « Précédent » s'active alors

- [x] `GOAL-012-T09` **Animation de pile de cartes**, demandée par l'auteur :
      la carte qui part s'incline et s'efface, celle du dessous reste centrée et
      grandit. La géométrie est une fonction pure éprouvée à part
      (`swipeCardTransform`), parce qu'aucune capture ne montre le milieu d'un
      geste. Constaté sur appareil, capture à l'appui, à mi-parcours

- [x] `GOAL-012-T10` **Bouton de rechargement**, demandé par l'auteur, partagé
      par les deux modes : `RefreshButton`. Il est nécessaire en Balayage — il
      n'y a pas de liste à tirer, et un tirage vertical entrerait en concurrence
      avec le geste horizontal — et repris en Liste **en plus** du geste, qui
      n'est pas praticable par tout le monde. Il se change en indicateur pendant
      l'attente plutôt que de se griser ou de disparaître.
      **Posé sur la ligne du titre**, à la demande de l'auteur : superposé au
      flux, il en recouvrait toujours une part — le coin de la première carte en
      Liste, l'illustration en Balayage. La destination affichée le publie donc
      à l'ossature (`FeedRefresh`), qui n'a aucune raison de connaître son
      ViewModel
- [x] `GOAL-012-T11` ~~Le bouton « Ouvrir l'article » est tronqué quand
      l'extrait est long~~ **Mal attribué, et repris en `GOAL-014-T12`.**
      Énoncé initial : sur un article dont l'extrait approche les 1 400
      caractères, le bouton est poussé hors de la carte, vu sur appareil le
      2026-08-08. C'est exact **au repos**, mais le contenu de la carte défile
      (SPECS.md §7.1) : sans bandelette, le bouton revient entièrement à
      l'écran. Il n'y avait donc pas de défaut ici. Ce qui en faisait un, c'est
      la bandelette d'ancienneté posée par-dessus, qui recouvrait la fin du
      contenu défilable — un défaut de GOAL-014, corrigé là-bas.

### Question tranchée

L'extrait était limité à 240 caractères en mode Liste (SPECS.md §8, question 7),
calibré sur trois lignes de carte. En plein écran il monte à **1 400**, coupés sur une
frontière de mot. Le chiffre n'est pas rond par hasard : le résumé médian mesure
1 324 caractères, donc l'article ordinaire est montré en entier, et l'écran en
tient à peu près autant. Pas le contenu entier pour autant — le maximum mesuré
est de 34 777 caractères, et un article que l'on ferait défiler verticalement
entrerait en conflit avec le geste horizontal.

---

## GOAL-013 — Rappel de lecture par notification locale

**Statut : DONE** — validé sur appareil

Couvre SPECS.md §4.9, ajouté à la demande de l'auteur.

Une notification quotidienne rappelle qu'il reste des articles à lire. Elle part
à **l'heure d'ouverture de la veille**, cite des titres réels, et varie sa
formulation d'un jour à l'autre.

### Ce qui a été tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| SPECS.md §2 excluait les notifications | **La spécification change** | C'est une décision d'auteur, pas un contournement. L'exclusion est levée explicitement plutôt que enfreinte en silence (AGENTS.md §1.2) |
| Source des articles cités | Le **cache local**, jamais le réseau | SPECS.md §2 exclut toujours la synchronisation en arrière-plan, et §7.4 veut qu'aucune connexion ne parte sans geste de l'utilisateur. Un rappel qui interrogerait le serveur serait précisément la synchronisation de fond écartée |
| Rien à lire | **Aucune notification** | Un rappel annonçant qu'il n'y a rien à lire est une interruption sans contrepartie, et c'est ce qui fait couper les notifications d'une application |
| Heure retenue quand l'application est ouverte plusieurs fois | La **première** ouverture du jour | C'est le moment où l'utilisateur tend la main vers l'application ; la dernière ouverture retiendrait un passage distrait |
| Choix de la formulation | **Déterministe** sur le numéro du jour | Une reprise après échec rejoue le même jour ; un tirage au hasard donnerait deux messages pour un seul rappel |

### Tâches

- [x] `GOAL-013-T01` **Le domaine décide** : `DailyMinute`, `nextReminderAt`,
      `ReminderTone`, `reminderPlanFor`. Aucune chaîne, aucune horloge, aucun
      fuseau lu — tout est transmis. 17 tests, dont le changement d'heure des
      deux sens et une horloge d'appareil antérieure à l'époque
- [x] `GOAL-013-T02` **Le cache sait dire ce qu'il reste** : lecture des
      articles non lus, sans réseau — filtre fait par SQLite, lecture ponctuelle
      plutôt que `Flow`, mélange des sources appliqué comme au flux. 5 tests sur
      base réelle
- [x] `GOAL-013-T03` **L'heure d'ouverture est retenue** : premier lancement du
      jour enregistré, `DataStore` (`ReminderTimeStore`, 8 tests dont deux
      fuseaux et le changement de jour). C'est cette heure que le rappel validé
      sur appareil a réellement employée
- [x] `GOAL-013-T04` **WorkManager porte le rappel** : `HiltWorker`, travail
      unique, réarmement du lendemain par le travailleur lui-même — sans quoi
      la chaîne s'arrête dès que l'application n'est pas ouverte
- [x] `GOAL-013-T05` **La notification est construite** : canal, formulations en
      ressources, ouverture de l'application au toucher
- [x] `GOAL-013-T06` **La permission est demandée** (`POST_NOTIFICATIONS`,
      API 33+), et son refus n'empêche rien d'autre de fonctionner
- [x] `GOAL-013-T07` **Le rappel se désactive** depuis les réglages (§6) : sous
      API 33 il n'y a aucune permission à retirer, et un rappel qu'on ne peut
      pas éteindre est un défaut
- [x] `GOAL-013-T08` **Un seul rappel à la fois, effacé à l'ouverture** : même
      identifiant de notification d'un jour à l'autre — un nouveau rappel
      remplace le précédent au lieu de s'empiler — et retrait au retour dans
      l'application
- [x] `GOAL-013-T09` **Documentation** : SPECS §2 et §4.9, ARCHITECTURE §9 et
      la carte des paquets, README (la fonctionnalité et le fait qu'elle
      n'appelle rien), TASKS
- [x] `GOAL-013-T10` **Validé sur appareil** (Pixel 10 Pro, Android 17) :
      notification réellement reçue, ton « Un moment pour lire ? », deux titres
      réels et « 119 articles non lus » ; effacée à l'ouverture, constaté à zéro
      enregistrement.
      **Le programmateur a été observé séparément** : après une ouverture, le
      travail apparaît dans `dumpsys jobscheduler` à `+23h59m`, calculé par le
      code réel. Forcer ce travail ne le fait pas partir — WorkManager revérifie
      son propre délai — d'où une variante **locale et non committée** à délai
      court pour voir la notification elle-même.
      **Reste non constaté sur appareil** : l'absence de doublon. Elle tient à
      un identifiant constant et elle est éprouvée en test unitaire, mais ma
      mesure sur appareil comptait des lignes de `dumpsys` et non des
      notifications distinctes — le téléphone s'est déconnecté avant que je
      puisse la refaire proprement

### Ce qui a été corrigé en intégrant

`AppGraphTest` avait dû remplacer le planificateur réel par un double, faute de
`WorkManager` initialisé sous `HiltTestApplication`. Le trou est refermé :
`WorkManagerTestInitHelper` amorce le gestionnaire avant l'injection, et
**toutes** les dépendances de ce test reviennent du graphe réel — un double y
serait un trou, pas une commodité.

### Dette ouverte

Le rappel ne voit que le cache : un article publié depuis la dernière ouverture
n'y est pas, et ne sera donc pas annoncé. C'est le prix assumé de l'absence de
synchronisation en arrière-plan.

---

## GOAL-014 — Toast d'ancienneté du flux

**Statut : DONE** — validé sur appareil

Couvre SPECS.md §4.6, ajouté à la demande de l'auteur.

Le flux ne se synchronise jamais tout seul (SPECS.md §2), et le cache s'affiche
dès le lancement (§5.1) : l'écran d'un flux vieux de dix heures était
indiscernable de celui d'un flux frais. Au-delà de **6 h** sans réponse du
serveur, une bandelette actionnable invite à rafraîchir.

### Ce qui a été tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| Seuil d'ancienneté | **6 h** | Décision d'auteur. Inscrite dans SPECS.md §8 |
| Qui décide | Une fonction pure de `:domain` | Comme `reminderPlanFor` (GOAL-013) : ni horloge, ni chaîne, ni Android dans la règle |
| Qui horodate | Le **dépôt**, sur toute réponse serveur valide, `loadPage` comprise | Deux ViewModels appellent `refresh()` ; horodater côté présentation dupliquerait la règle et laisserait les deux modes diverger. La couche qui a parlé au serveur est la seule à savoir qu'il a répondu |
| Support de stockage | DataStore, clé `feed.last_refresh_at` | Scalaire (ARCHITECTURE.md §5.1 : Room porte les collections, DataStore les scalaires) |
| Hors ligne | **Aucune bandelette** | Le bandeau hors ligne dit déjà pourquoi le flux est ancien. Proposer « Rafraîchir » là où l'appel échouera à coup sûr est une fausse porte, et empilerait deux bandelettes au même endroit de l'écran |
| Acquittement | **En mémoire**, partagé par les deux modes, repéré par l'horodatage acquitté | Local à un ViewModel, il ferait revenir la bandelette à chaque bascule Liste↔Balayage. Comparer les horodatages la fait revenir après un rafraîchissement réussi puis 6 h, sans horloge supplémentaire |
| Effacement | **À la main**, jamais par minuteur | Le dépôt a déjà tranché ainsi pour l'avis d'ouverture hors ligne : « un message qui s'efface tout seul se rate » |
| Commandes de la bandelette | « Rafraîchir » **et** une fermeture | Une seule action imposerait le message à qui n'est pas en état de rafraîchir |

### Tâches

- [x] `GOAL-014-T01` **Le domaine décide de l'ancienneté** : `FeedFreshness`,
      `STALE_FEED_THRESHOLD_MILLIS`, `FeedFreshnessRepository`. Jamais ancien
      sans point de référence ; une horloge qui recule ne rend rien ancien.
      15 tests, dont l'horodatage restauré depuis le futur et l'acquittement
      que le temps rouvre
- [x] `GOAL-014-T02` **L'horodatage est persisté** : `FeedFreshnessStore`,
      `DataStore`, acquittement en mémoire vive et partagé. 7 tests sur un
      DataStore réel, dont l'acquittement fait avant tout rafraîchissement
- [x] `GOAL-014-T03` **Le dépôt enregistre chaque contact serveur réussi**, y
      compris une page valide mais vide — le serveur a répondu. 8 tests, dont
      les quatre échecs qui ne doivent rien noter
- [x] `GOAL-014-T04` **Le mode Liste porte l'avis** : état dérivé, acquittement,
      et le réveil périodique sans lequel le seuil ne serait jamais franchi à
      l'écran. La surveillance est écrite une fois, dans
      `FeedStalenessWatcher`, pour que les deux modes ne divergent pas.
      11 tests, dont le vieillissement sans aucun événement
- [x] `GOAL-014-T05` **Le mode Balayage porte le même avis**, acquittement
      compris — acquitter dans un mode fait taire l'autre. 6 tests, dont
      celui-là précisément
- [x] `GOAL-014-T06` **La bandelette est factorisée** (`FeedNotice`) : elle
      était écrite deux fois, à l'identique, dans les deux écrans. Refactor
      pur — les tests d'écran passent inchangés et `verifyRoborazziDebug` ne
      voit aucun pixel bouger. 5 tests propres au composant
- [x] `GOAL-014-T07` **L'avis s'affiche en mode Liste**, et « Recharger » y
      emprunte exactement le rechargement existant. 5 tests d'écran, dont
      celui qui constate qu'une seule bandelette occupe le bas de l'écran
- [x] `GOAL-014-T08` **L'avis s'affiche en mode Balayage**, sans masquer la
      commande d'ouverture de l'article — mesuré, et pas seulement supposé.
      5 tests d'écran, mêmes chaînes qu'en mode Liste
- [x] `GOAL-014-T09` **Captures Roborazzi** : la bandelette sur une carte et sur
      une illustration plein écran ne se jugent pas au même endroit. Quatre
      références, **regardées** : les deux commandes tiennent côte à côte sans
      replier le message, le contraste passe dans les deux thèmes, et « Ouvrir
      l'article » n'est pas recouvert
- [x] `GOAL-014-T10` **Documentation** : SPECS §4.6 et §8 question 9,
      ARCHITECTURE §5.1 et §9.6, README, TASKS
- [x] `GOAL-014-T11` **Constaté sur appareil** (Pixel 10 Pro, Android 17,
      2026-08-08). Protocole : la date écrite par le dépôt a été reculée de 7 h
      dans le DataStore, et l'adresse du serveur pointée sur `192.0.2.1`
      (TEST-NET, sans route) — c'est le **seul** montage qui réunit les deux
      conditions de l'avis, un flux ancien et un appareil qui n'est pas hors
      ligne.
      **Ce qui a été vu** : la date du dernier contact serveur est bien écrite
      par le dépôt au lancement ; la bandelette paraît en Balayage sur du
      contenu réel, puis en Liste ; « Plus tard » l'éteint ; elle **reste
      éteinte après une bascule Balayage → Liste**, ce qui est le point que
      l'acquittement partagé devait garantir ; elle **revient après un
      redémarrage du processus**, l'acquittement ne vivant qu'en mémoire ; et
      un contact serveur réussi l'éteint — flux rechargé, date remise à
      l'instant, plus aucune bandelette.
      **Deux cas n'ont pas pu être constatés depuis le poste de
      développement** : l'appui sur « Recharger » — au lancement la page arrive
      et éteint l'avis avant tout appui — et le hors ligne, le mode avion
      coupant `adb`, qui passe par le même réseau. Les deux sont couverts en
      test, et **l'auteur a confirmé leur bon fonctionnement sur son appareil**
      le 2026-08-08.
- [x] `GOAL-014-T12` **La bandelette cesse d'être une surimpression.** Le
      constat sur appareil avait été mal lu : le bouton « Ouvrir l'article »
      poussé hors de la carte par un extrait long n'est pas un défaut — le
      contenu défile. Le défaut, c'est que la bandelette **recouvrait la fin de
      ce défilement**, donc le bouton là où il s'arrête : la seule commande
      d'ouverture de ce mode (SPECS.md §4.7) devenait inatteignable.
      Reproduit d'abord par un test — extrait long, `performScrollTo` sur le
      bouton, mesure du chevauchement — puis corrigé : l'avis prend sa place
      dans la mise en page, sous le flux, dans les deux modes. Un avis qui
      dure jusqu'à ce qu'on l'acquitte n'est pas fugace ; seul l'avis
      d'ouverture refusée reste posé par-dessus, et il ne rencontre jamais
      l'autre. Captures Balayage réenregistrées et **regardées**.
      **Reconstaté sur appareil** (Pixel 10 Pro, 2026-08-08) : sur un article
      d'un millier de caractères, la carte s'arrête au-dessus de la bandelette,
      et le bouton « Ouvrir l'article » revient **entièrement** à l'écran une
      fois le contenu défilé. Le défaut est en outre tenu par un test qui
      échouait avant la correction et passe après.

---

## Points bloqués

Aucun.

---

## Questions ouvertes

Les décisions fonctionnelles différées sont listées dans [SPECS.md §8](./SPECS.md).
Les incertitudes sur l'API distante sont listées dans
[docs/freshrss-api.md §6](./docs/freshrss-api.md). Chacune est tranchée par le
Goal qui la rencontre, puis **inscrite** — jamais laissée implicite dans le code.
