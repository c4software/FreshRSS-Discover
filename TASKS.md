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

**Phase 3 — Réglage du marquage, partage, documentation anglaise** 🚧 en cours
(GOAL-019, GOAL-020, GOAL-021)

Un point reste bloqué, hors de notre main : `GOAL-001-T17` — AGP 9.3.1 plante
toujours sur `lintAnalyzeDebugUnitTest`, réessayé le 2026-08-08. Il se lèvera
avec une version d'AGP, pas avec du code d'ici.

**Prochaine tâche** : `GOAL-019-T01`, `GOAL-020-T01` et `GOAL-021-T01`, menées
en parallèle par trois agents à la demande de l'auteur.

> ⚠️ **Aucune validation sur appareil n'est possible sur cette phase**, l'auteur
> l'a signalé le 2026-08-09 : le téléphone n'est pas disponible. La garantie
> repose donc entièrement sur les tests unitaires, les tests d'écran et les
> captures Roborazzi **regardées**. Les Goals précédents avaient chacun leur
> tâche « constaté sur appareil », et trois défauts sur trois n'y avaient été
> vus qu'ainsi (`GOAL-001-T22`) : ce filet-là manque, et il faut le dire plutôt
> que de laisser croire à une couverture équivalente.

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
| GOAL-012 | Vue Balayage, article par article | `[x]` |
| GOAL-013 | Rappel de lecture par notification locale | `[x]` |
| GOAL-014 | Toast d'ancienneté du flux | `[x]` |
| GOAL-015 | Lancement calme : cache seul, sans reprise | `[x]` |
| GOAL-016 | Les petites illustrations cessent d'être étirées | `[x]` |
| GOAL-017 | Un article déjà lu se voit | `[x]` |
| GOAL-018 | La CI cesse de tourner sur des actions dépréciées | `[-]` |
| GOAL-019 | Le marquage automatique devient optionnel | `[ ]` |
| GOAL-020 | La carte se partage, le fanion disparaît, le balayage s'ouvre d'un appui | `[ ]` |
| GOAL-021 | La documentation passe à l'anglais, l'interface devient bilingue | `[ ]` |

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
- [!] `GOAL-001-T17` **Lint Android désactivé sur les sources de test**
      (`ignoreTestSources = true`). AGP 9.3.1 plante sur ses propres composants
      d'analyse Kotlin.
      > **Réessayé le 2026-08-08, le plantage subsiste.** Passer à `false` fait
      > échouer `:app:lintAnalyzeDebugUnitTest` sur
      > `SymbolLightClassForClassOrObject.getSuperTypes`, exactement la trace
      > d'origine. À noter pour la prochaine tentative : `:app:lintDebug` seul
      > **passe** — c'est la variante de test unitaire qui plante, et s'arrêter
      > à `lintDebug` ferait croire le problème résolu.
      >
      > Ce que l'essai a tout de même appris : hors ce plantage, les sources de
      > test ne portent que cinq avertissements, tous de nommage
      > (`ComposableNaming` sur des aides de capture). Rien de structurel
      > n'attend derrière ce garde-fou.
      >
      > Reste bloquée jusqu'à une version d'AGP qui corrige.
- [x] `GOAL-001-T18` **Robolectric relevé de l'API 35 à 36**, le dernier niveau
      qu'il sait instancier — 37 lève `UnknownSdk`, essayé avant de trancher.
      Un écart d'un niveau subsiste avec `targetSdk`, et il ne se refermera
      qu'avec une version de Robolectric qui porte l'image 37.
      Le rendu bouge un peu au passage : les 48 références ont été
      réenregistrées et **regardées** en comparaison. Seul l'anticrénelage des
      arrondis diffère — curseurs, interrupteur, coins de carte — la mise en
      page, les textes et les couleurs sont inchangés.
- [x] `GOAL-001-T19` **CI neutralisée sur `push` — décision close, pas une
      dette** (`branches: [never]`). Confirmée par l'auteur le 2026-08-08 :
      elle figurait encore parmi les points bloqués, ce qui laissait croire à un
      obstacle en attente de levée. Il n'y en a pas.
      > Chaque exécution consomme du crédit de build, et la vérification locale
      > est exactement la même commande. Le déclencheur `pull_request` reste
      > actif — il consomme lui aussi, et se neutralise de la même façon si
      > besoin. La garantie repose donc entièrement sur AGENTS.md §5, dont la
      > sortie doit être **constatée** avant chaque commit.

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
| `SecretKeySource` extrait (GOAL-002-T18) | Le chiffrement lui-même restait inéprouvable parce qu'il fabriquait sa clé. Le partage réduit l'angle mort à l'appel de plateforme |
| Sonde de reconnaissance **avant** l'envoi des identifiants | Une faute de frappe enverrait sinon le mot de passe à un serveur tiers |
| Sonde de transmission d'en-tête **après** l'obtention du jeton | Plus tôt : un aller-retour gaspillé par tentative. Plus tard : une session vouée à boucler sur des 401 |
| `invalidateSession()` distinct de `signOut()` | Un jeton refusé conserve adresse et identifiant ; une déconnexion efface tout |

### Dettes ouvertes par ce Goal

- [x] `GOAL-002-T18` **Le chiffrement des secrets est éprouvé** — 9 tests, dont
      l'aller-retour, le vecteur d'initialisation qui change à chaque
      chiffrement, un octet altéré que GCM refuse, et le texte illisible traité
      comme une session absente plutôt qu'en plantant.
      Robolectric ne simule toujours pas `AndroidKeyStore` — réessayé, le
      fournisseur lève `NoSuchAlgorithmException`. La classe restait donc
      inéprouvable **pour la seule raison qu'elle fabriquait sa clé** : la
      provenance de la clé est passée derrière `SecretKeySource`, et ce qui
      reste non couvert tient en une vingtaine de lignes qui n'appellent que la
      plateforme.
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

**Statut : DONE** — validé sur appareil, position de lecture comprise.

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
- [x] `GOAL-012-T05` **Position partagée entre les deux modes.** Le mode
      Balayage retient la carte sous les yeux et reprend là où la lecture s'est
      arrêtée, par le **même** `ReadingPositionViewModel` que le mode Liste — la
      position appartient au flux, pas à la façon de le parcourir, et deux
      mémoires séparées se contrediraient à chaque bascule.
      L'obstacle qui l'avait fait différer est levé par la reprise « au plus
      proche » de `GOAL-009-T05`, et non contourné : en plein écran l'article
      quitté est presque toujours celui que le marquage vient de rendre lu, donc
      absent du flux suivant. `indexIn` retient le premier article qui n'est pas
      plus récent.
      `settledPage` et non `currentPage` : le second bascule dès que le geste
      dépasse la moitié de l'écran, y compris quand le doigt revient — on
      enregistrerait une position jamais atteinte. 5 tests d'écran, dont
      l'article disparu et le flux entièrement plus récent
- [x] `GOAL-012-T06` Réglage persistant du mode, dans l'écran de réglages (§6)
- [x] `GOAL-012-T07` **Accessibilité du geste de balayage — tranché, et non
      laissé en suspens.** Décision de l'auteur le 2026-08-08 : la règle de
      SPECS.md §7.1 porte sur l'**application**, pas sur chacun de ses modes.
      Le mode Liste — celui par défaut — donne accès au même flux, dans le même
      ordre, entièrement au défilement vertical et aux cibles ordinaires ; le
      Balayage est une préférence, jamais un passage obligé, et le réglage qui
      en sort s'atteint sans le geste en cause.
      L'historique de la tâche vaut d'être gardé : deux boutons « Précédent » /
      « Suivant » y avaient répondu, puis ont été retirés — ils encombraient
      l'écran d'un mode dont l'intérêt est de n'avoir aucune commande. Rouvrir
      la tâche à ce moment-là était juste ; la laisser ouverte indéfiniment
      faisait passer un arbitrage pour une dette.
      Conséquence assumée, inscrite dans SPECS.md §7.1 : qui emploie un lecteur
      d'écran et se retrouve en Balayage doit passer par les réglages
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
- [x] `GOAL-014-T13` **Régression corrigée : les réglages réémettaient à chaque
      page.** Trouvée le 2026-08-08 en cherchant pourquoi le flux paraissait se
      comporter autrement depuis la mise à jour.
      `GOAL-014-T03` fait écrire la date du contact serveur à **chaque page
      reçue**, dans le DataStore partagé. Or DataStore émet à chaque écriture du
      **fichier**, pas de la clé, et `observeReadingSettings` ne filtrait pas :
      les deux ViewModels du flux reconstruisaient donc leur `ReadDetector` à
      chaque page, remettant à zéro les chronomètres de visibilité en cours —
      un article regardé pendant un chargement n'était plus marqué lu
      (SPECS.md §4.5). Au lancement, où plusieurs pages s'enchaînent, l'effet
      se répétait.
      `distinctUntilChanged` sur tous les flux dérivés du DataStore, réglages et
      session. 3 tests dans `SettingsStoreTest`, dont un qui échoue si on retire
      le filtre — vérifié dans les deux sens.
      **La règle est en outre verrouillée là où elle se voit** (5 tests de
      `DiscoverViewModelTest`), à la demande de l'auteur : la première page du
      serveur ne réordonne pas ce que le cache affichait, n'en retire rien, et
      une réémission du cache ne remélange pas le flux. Un flux qui se mélange
      au lancement est un défaut, jamais un effet de bord acceptable
      (SPECS.md §4.2, règle 3).
      Ce que la régression **ne fait pas**, contrairement à ce qu'on pouvait
      craindre : elle ne recrée pas l'écran et ne déclenche aucune requête. Les
      flux qui pilotent la navigation et l'aiguillage de session sont des
      `StateFlow`, qui ne réémettent pas une valeur égale.
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

## GOAL-015 — Lancement calme : cache seul, sans reprise de position

**Statut : DONE** — validé sur appareil

Change SPECS.md §4.1, §5.1 et §5.3, tranche §8 questions 10 et 11. Décision d'auteur du
2026-08-08, prise devant deux défauts constatés sur appareil le même jour : la
tête du flux différait d'un lancement à l'autre (course entre le disque et le
réseau, ordre serveur ≠ ordre de publication — corrigé par `GOAL-005-T05`), et
la position mémorisée se réécrivait toute seule au lancement, l'article en tête
des premières images écrasant la vraie place. Plutôt que de corriger la
mémorisation — le correctif était écrit aux deux tiers — l'auteur retire la
fonctionnalité et le rechargement automatique avec elle : un flux stable qui
rouvre à l'identique n'a plus besoin qu'on lui garde une place.

### Ce qui a été tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| Reprise de lecture (§5.3) | **Supprimée**, code compris | Sa mémoire dérivait ; sur un flux devenu stable, elle ne payait plus sa complexité |
| Requête au lancement | **Aucune**, sauf cache vide | La course disque/réseau décidait de l'écran ; un cache vide reste amorcé tout seul, une application sans contenu serait morte |
| Défilement en bas de connu | **Continue de charger** | Défiler est une action ; seul le rechargement de la tête exige le bouton |
| L'avis d'ancienneté (GOAL-014) | Devient **le** rappel de mise à jour | Sans rechargement automatique, c'est lui qui dit quand le geste vaut la peine |

### Tâches

- [x] `GOAL-015-T01` **La reprise de position disparaît**, du domaine à l'écran :
      `ReadingPosition`, dépôt, store, ViewModel, effets des deux écrans,
      liaisons Hilt, `forget()` de la déconnexion, et chaque test qui les
      éprouvait — sept fichiers supprimés, aucun code mort laissé. Les clés
      `reading.*` des appareils existants deviennent orphelines dans le
      DataStore : inoffensives, plus jamais lues
- [x] `GOAL-015-T02` **Le lancement n'interroge plus le réseau** : les deux
      ViewModels affichent le cache et s'y tiennent ; un cache vide déclenche
      seul le premier chargement ; le défilement pagine comme avant.
      **Constaté sur appareil** : `feed.last_refresh_at` est rigoureusement
      inchangé après un lancement à froid — plus aucune requête ne part sans
      geste
- [x] `GOAL-015-T05` **Le mélange du cache cesse de dépendre des articles lus.**
      Le mélange choisit chaque position en regardant ses voisins : appliqué
      **après** le filtrage des lus, chaque article marqué lu quittait
      l'ensemble et redistribuait tous ses voisins. Le marquage étant automatique
      et continu (SPECS.md §4.5), le flux se réordonnait à chaque lancement —
      trois ouvertures consécutives, trois têtes différentes, constaté sur
      appareil. Le mélange s'applique désormais **avant** le filtrage : l'ordre
      des non-lus est un sous-ordre stable. 1 test
- [x] `GOAL-015-T04` ~~La purge peut encore redistribuer l'ordre~~ **Sans objet
      depuis `T08`** : le mélange porte sur le cache entier, lus compris, et la
      purge ne retire que des articles lus **et** synchronisés de plus d'une
      semaine — qui ne sont plus dans la fenêtre affichée. Énoncé initial : Elle retire
      des articles de l'ensemble sur lequel le mélange se calcule, et elle
      tourne une fois par démarrage de processus, en **course** avec la première
      lecture du cache. Elle ne touche que des articles lus depuis plus d'une
      semaine et déjà synchronisés : une fois le retard résorbé elle ne trouve
      plus rien, et l'ordre se fige. Le régime transitoire, lui, bouge encore.
      Le test qui l'établissait a été **retiré plutôt que gardé faux** : il
      contredisait celui du marquage, et l'arbitrage entre les deux appartient à
      la spécification, pas au code
- [x] `GOAL-015-T03` **Documentation** : SPECS §4.1, §4.6, §5.1, §5.3 et §8
      questions 10 et 11 ; ARCHITECTURE §9.7, qui réunit les quatre mécanismes
      en un seul principe ; README ; TASKS
- [x] `GOAL-015-T06` **Les pages du serveur sont ramenées à l'ordre de
      publication.** Le serveur trie sa `reading-list` par date de
      **récupération** : un article publié deux jours plus tôt ouvrait la
      première page. Cet ordre différait de celui du cache, trié par
      publication, et l'écran du lancement dépendait de qui répondait en
      premier. Départage à date égale identique au tri SQL du cache. 2 tests,
      écrits rouges. Tranché dans SPECS §8 question 11
- [x] `GOAL-015-T07` **La borne du cache s'applique après le filtre, plus
      avant.** Un cache dont les deux cents articles les plus récents avaient
      été lus rendait une liste **vide** : l'écran le croyait vide et
      déclenchait le chargement de secours à chaque ouverture — la requête que
      `T02` venait de retirer. Constaté sur appareil : 283 articles en cache,
      69 non lus, zéro affiché
- [x] `GOAL-015-T08` **Les articles lus restent dans le flux jusqu'au
      rechargement** (SPECS §4.1). C'est ce qui referme le sujet : le mélange
      choisit chaque position en regardant ses voisins, donc tout ce qui entre
      ou sort de l'ensemble redistribue le reste. Les articles lus en sortaient
      à chaque session — le marquage en consomme — et le flux paraissait se
      remélanger tout seul. 2 tests, dont celui de l'ordre inchangé après
      marquage.
      **Constaté sur appareil** : trois lancements à froid consécutifs, tête
      identique, `feed.last_refresh_at` inchangé. `GOAL-015-T04` et `T05`
      tombent avec — l'ordre ne dépend plus ni des lus ni de la purge
- [x] `GOAL-015-T09` **Test instable réparé, découvert par la CI.** La
      publication de `v1.4.0` a échoué sur
      `theStartupPurgeRemovesReadArticlesPastTheThreshold` — vert en local,
      rouge sur le runner. Cause : en adaptant les tests à `T07`, une lecture
      **suspendue** par le flux Room avait été remplacée par une requête SQL
      synchrone, qui peut devancer une purge lancée en tâche de fond. Le flux,
      lui, attend l'invalidation de Room.
      Le contournement n'avait d'ailleurs plus lieu d'être : depuis `T08` le
      flux du cache rend les articles lus. Les trois tests concernés y
      reviennent, et le code de production perd la requête que seuls les tests
      appelaient. Rejoué trois fois de suite depuis zéro

---

## GOAL-016 — Les petites illustrations cessent d'être étirées

**Statut : DONE** — validé sur appareil

Couvre SPECS.md §4.3, ajouté à la demande de l'auteur. Une illustration plus
petite que le créneau est aujourd'hui **agrandie** pour le remplir, et le
résultat est flou ou pixelisé. Le remède demandé est celui de certains réseaux
sociaux : la même image en fond, floutée et rognée, et l'image à sa taille
réelle par-dessus.

### Ce que l'analyse établit

| Constat | Où |
|---|---|
| Le créneau est fixé à **16/9**, jamais déduit de l'image — sans quoi la liste sursauterait à l'arrivée de chaque image | `ILLUSTRATION_ASPECT_RATIO`, les deux écrans |
| `ContentScale.Crop` remplit toujours le créneau : une image de 200 px de large sur un écran de 1080 est **agrandie 5 fois** | `ArticleIllustration`, les deux écrans |
| Le composant est **écrit deux fois**, à l'identique, comme l'était `FeedNotice` avant `GOAL-014-T06` | `DiscoverScreen`, `SwipeScreen` |
| Coil 3.4.0 donne la taille source dans `AsyncImagePainter.State.Success` : le seuil est mesurable sans requête supplémentaire | — |
| `Modifier.blur` exige **API 31** ; le projet descend à **26** | `android-minSdk = "26"` |
| Le chargeur de test rend une image **carrée de 400 px**, franche : de quoi éprouver le cas sans réseau | `FakeIllustrations.kt` |

### Ce qui a été tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| Quand une image est « trop petite » | **Quand il faudrait l'agrandir** : largeur source < largeur du créneau | C'est la définition exacte du défaut. Un seuil chiffré serait arbitraire et devrait être défendu ; celui-ci se mesure |
| Le fond | La **même image**, rognée et floutée, sur **tout** le créneau ; l'image nette centrée par-dessus, à sa taille | Le créneau reste plein, sans bande vide ni cadre. C'est le procédé demandé par l'auteur, employé par plusieurs réseaux sociaux |
| Sous API 31 | **Rien ne change** : l'étirement d'aujourd'hui | `Modifier.blur` n'y fait rien, et un fond net dupliqué serait pire que le défaut qu'on corrige. Un second mécanisme — teinte dominante — coûterait son écriture et ses tests pour une minorité d'appareils |
| Portée | Les **deux** modes, après avoir réuni le composant | Il est écrit deux fois : corriger sans réunir, c'est corriger deux fois puis diverger une fois |

### Tâches

- [x] `GOAL-016-T01` **L'illustration devient un seul composant**, dans
      `presentation/feed` : elle est aujourd'hui écrite à l'identique dans les
      deux écrans. Refactor pur — captures inchangées, tests d'écran inchangés
- [x] `GOAL-016-T02` **Le composant sait si l'image serait agrandie** : taille
      source lue dans l'état de Coil, comparée à la largeur mesurée du créneau.
      Décision pure, éprouvable sans rendu — 6 tests, dont les trois cas où l'on
      ne doit rien faire
- [x] `GOAL-016-T03` **Le fond flouté**, sous l'image à sa taille réelle, et
      seulement quand l'agrandissement aurait lieu. Sous API 31, l'étirement
      d'aujourd'hui, sans y toucher.
      La copie floutée déborde légèrement du créneau : `blur` estompe jusqu'aux
      bords, et sans ce débordement le cadre qu'on chasse reparaîtrait en
      périphérie.
      **`Inside` et non `Fit`**, corrigé après un premier essai sur appareil :
      `Fit` remplit la plus petite dimension du créneau, donc agrandit encore —
      l'image de devant restait floue, exactement le défaut qu'on prétendait
      corriger. `Inside` réduit ce qui déborde mais ne grandit jamais au-delà de
      la taille native : c'est la seule échelle qui n'invente aucun pixel
- [x] `GOAL-016-T04` **Captures Roborazzi** : une petite image et une grande,
      dans les deux modes, clair et sombre. Quatre références, **regardées**.
      > **Deux pièges du harnais, corrigés plutôt que contournés.** La première
      > capture employait un aplat uni : flouté ou net, rogné ou ajusté, il rend
      > exactement les mêmes pixels — elle aurait validé n'importe quoi.
      > L'illustration minuscule est donc devenue **bicolore**, un disque clair
      > sur fond sombre, où le sujet net, le fond estompé et le créneau plein se
      > distinguent.
      > L'image factice « ordinaire » mesurait par ailleurs 400 px : elle passait
      > elle aussi sous la largeur d'un créneau, donc sous le fond flouté. Toutes
      > les captures du dépôt auraient illustré le cas particulier en croyant
      > montrer le cas général. Portée à 1 600 px, elle redevient le cas
      > ordinaire — et les références existantes sont **inchangées au pixel**
- [x] `GOAL-016-T05` **Constaté sur appareil**, sur un article réel à petite
      illustration — validé par l'auteur. Le premier essai y a d'ailleurs révélé
      le défaut de `Fit`, qu'aucune capture n'avait montré : l'image factice du
      harnais est carrée, une vignette réelle ne l'est pas
- [x] `GOAL-016-T06` **Documentation** : SPECS §4.3 et §8 question 12,
      ARCHITECTURE §9.8, README, TASKS
- [x] `GOAL-016-T07` **Audit du code du jour**, demandé par l'auteur. Aucune
      infraction aux interdits d'AGENTS.md §2 — pas d'import Android dans
      `:domain`, aucun `Dispatchers.` ni `System.currentTimeMillis()` hors de
      leur module, aucune chaîne en dur dans un Composable, aucun `TODO`
      orphelin, aucun code mort.
      Deux écarts de **convention** corrigés : `needsUpscaling` et
      `FeedStalenessWatcher` étaient publics alors que le dépôt réserve
      `public` aux composables partagés et aux points d'entrée, et `internal` à
      tout le reste.
      Trois passages d'ARCHITECTURE.md étaient devenus faux avec la suppression
      de la position de lecture, dont une section entière — voir `GOAL-016-T03`

---

## GOAL-017 — Un article déjà lu se voit

**Statut : DONE** — validé sur appareil

Couvre SPECS.md §4.1 et §4.5. **Ce Goal répare une conséquence de
`GOAL-015-T08`**, trouvée en analysant les dérives depuis la v1.2.0 à la
demande de l'auteur.

Les articles lus restent désormais affichés jusqu'au rechargement — c'est ce
qui rend le flux stable au lancement. Mais rien ne les distingue : `isRead`
existe dans le modèle d'affichage, le ViewModel le tient à jour, et **aucun
écran ne le rend**. Tant que l'article disparaissait au lancement suivant, sa
disparition était le signal ; il reste maintenant, indiscernable d'un article
neuf, et l'on peut relire sans le savoir.

### Ce qui a été tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| La marque | Un **fanion**, en haut de la carte, par-dessus l'illustration | Choix de l'auteur. Se repère en balayant l'écran, là où une coche dans la ligne de flux demande de lire |
| Son emplacement | **Toujours le même** : en haut de la carte, que l'article ait une illustration ou non | C'est ce qui évite le second rendu, et le second jeu de captures, qu'un fanion posé dans le coin de l'image aurait imposés |
| La collision de sens | **Assumée** : le signet dit d'ordinaire « favori » | Signalée à l'auteur, qui tranche. À rouvrir si les articles suivis de FreshRSS sont un jour ajoutés — les deux se disputeraient le symbole |
| L'opacité de la carte | **Non retenue** | Elle atténuerait aussi le titre, et le contraste AA de SPECS.md §7.1 serait à remesurer sur chaque état |

### Tâches

- [x] `GOAL-017-T01` **Le fanion**, composant partagé dans `presentation/feed` :
      forme, contraste sur n'importe quelle image, description pour le lecteur
      d'écran
- [x] `GOAL-017-T02` **Posé dans les deux modes**, en haut de la carte, avec ou
      sans illustration. Le conteneur prend toute la largeur : sans cela il se
      dimensionnait au fanion seul quand l'article n'avait pas d'image, et
      l'alignement à droite n'avait rien sur quoi s'appuyer — le fanion
      apparaissait collé à gauche, vu sur capture
- [x] `GOAL-017-T03` **Captures Roborazzi** : lu et non lu, avec et sans
      illustration, clair et sombre — deux références, **regardées**, et c'est
      la première qui a révélé le défaut d'alignement
- [x] `GOAL-017-T04` **Constaté sur appareil** : capture prise deux secondes
      après un lancement à froid, les fanions y sont déjà. C'est ce constat qui
      a révélé le défaut de projection — l'auteur avait signalé « un petit délai
      avant l'affichage du fanion », et c'était en réalité un état faux
- [x] `GOAL-017-T07` **Le fanion cesse de décaler la carte, et paraît en
      fondu.** Signalé par l'auteur : sur un article **sans** illustration, le
      fanion occupait une hauteur dans le flux vertical et poussait le contenu
      vers le bas. Il survole désormais toute la carte, hors du flux — et hors
      du défilement en mode Balayage, où il aurait sinon glissé avec le texte
      alors qu'il qualifie l'article entier.
      L'apparition se fait en fondu : l'état lu s'établit en cours de lecture,
      et un fanion qui surgit sur la carte qu'on lit attire l'œil sur lui alors
      qu'il ne fait que constater
- [x] `GOAL-017-T06` **Le fanion est atténué**, à la demande de l'auteur : à
      pleine opacité il attirait l'œil sur ce qu'il y a de moins intéressant
      dans le flux. L'opacité porte sur la surface entière, coche comprise —
      n'atténuer que le fond aurait laissé la coche à pleine intensité, soit
      l'inverse du résultat cherché
- [x] `GOAL-017-T05` **Documentation** : SPECS §4.5, ARCHITECTURE §9.9 — qui
      retient la leçon des tests, non le seul correctif — README, TASKS

---

## GOAL-018 — La CI cesse de tourner sur des actions dépréciées

**Statut : IN PROGRESS**

Chaque publication signalait deux avertissements : `setup-java v4 is
deprecated`, et `Node.js 20 is deprecated` pour `download-artifact` et
`action-gh-release`. Rien ne cassait, et c'est précisément ce qui rend la chose
facile à laisser traîner — jusqu'au jour où GitHub retire le moteur Node 20 et
où la publication s'arrête sans prévenir.

### Ce que l'analyse établit

| Action | Avant | Après | Ce que la majeure apporte |
|---|---|---|---|
| `actions/checkout` | v4 | v7 | Node 24 |
| `actions/setup-java` | v4 | v5 | Node 24 ; c'est l'action explicitement dépréciée |
| `actions/upload-artifact` | v4 | v7 | Node 24, module ESM |
| `actions/download-artifact` | v4 | v8 | Node 24 ; l'empreinte du téléchargement devient **bloquante** au lieu d'un simple avertissement |
| `gradle/actions/setup-gradle` | v4 | v6 | Node 24 |
| `softprops/action-gh-release` | v2 | v3 | Node 24 |

Aucune rupture ne touche cet usage : les notes de version ont été lues avant de
changer les numéros. Le seul changement de comportement qui nous concerne —
l'empreinte vérifiée à l'arrivée — va dans le bon sens pour un artefact signé.

### Tâches

- [x] `GOAL-018-T01` **Monter les six actions**, puis constater la CI verte sur
      une pull request — c'est le seul déclencheur actif (`GOAL-001-T19`).
      Constaté : run vert, et **plus aucun avertissement d'action dépréciée**
      dans le journal. Ceux qui subsistent viennent de Gradle, pas de GitHub —
      voir `GOAL-018-T03`
- [ ] `GOAL-018-T02` **Constater la publication**, qui ne s'éprouve qu'au
      prochain tag : elle emploie deux actions que la CI ne traverse pas
- [ ] `GOAL-018-T03` **Les avertissements Gradle restants.** « Deprecated Gradle
      features were used in this build, making it incompatible with Gradle 10 »
      apparaît à chaque tâche. Ils ne viennent pas des actions mais du build
      lui-même — plugins ou scripts. `--warning-mode all` les nommera. Distinct
      de `T01`, et de portée différente : celui-ci touche la construction, pas
      la chaîne d'intégration

---

## GOAL-019 — Le marquage automatique devient optionnel

**Statut : TODO**

Demandé par l'auteur. SPECS.md §1 pose « lire, c'est faire défiler » comme
principe, et §4.5 en fait un mécanisme sans échappatoire : qui parcourt son flux
sans le lire consomme ses articles sans le vouloir, et le rechargement les
emporte. Un interrupteur **Actif / Non actif** rend la règle facultative.

### Ce qui est tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| Où vit le réglage | Dans `ReadingSettings`, aux côtés des deux seuils | Même lecteur — le détecteur de lecture — et même moment de lecture. Un flux distinct, comme celui du rappel, ferait observer deux sources à qui n'en applique qu'une |
| Valeur par défaut | **Actif** | C'est le comportement d'aujourd'hui, et celui que SPECS.md §1 décrit. Une installation existante ne doit rien voir changer |
| Ce que l'extinction arrête | La détection par visibilité, et **elle seule** | Ouvrir un article le marque toujours lu (SPECS.md §4.7) : c'est un geste délibéré, pas un marquage automatique. Les deux se confondraient si l'interrupteur emportait aussi l'ouverture |
| Les deux seuils, une fois éteints | **Affichés, grisés** | Les cacher ferait disparaître deux réglages sans dire pourquoi ; les laisser actifs proposerait d'ajuster ce qui ne s'applique plus |
| La file de marquages en attente | Inchangée | Ce qui est déjà marqué reste à transmettre. Éteindre le marquage n'annule pas les lectures passées |

### Tâches

- [ ] `GOAL-019-T01` `ReadingSettings.autoMarkAsReadEnabled`, actif par défaut,
      et son passage par `coerced` — tests de `:domain`
- [ ] `GOAL-019-T02` Persistance : clé DataStore, `observeReadingSettings` qui la
      rend, `SettingsRepository.setAutoMarkAsReadEnabled` — tests du store
- [ ] `GOAL-019-T03` **Les deux ViewModels du flux cessent d'alimenter le
      détecteur** quand le réglage est éteint, et le reprennent sans redémarrage
      quand il se rallume. L'ouverture d'un article marque toujours
- [ ] `GOAL-019-T04` Interrupteur dans l'écran de réglages, seuils grisés en
      dessous — tests d'écran et captures Roborazzi **regardées**
- [ ] `GOAL-019-T05` Documentation : SPECS.md §4.5 et §6, TASKS.md

---

## GOAL-020 — La carte se partage, le fanion disparaît, le balayage s'ouvre d'un appui

**Statut : IN PROGRESS**

Trois demandes de l'auteur sur la même surface — la carte d'article — donc un
seul Goal : elles se croisent dans `DiscoverScreen` et `SwipeScreen`, et les
traiter séparément reviendrait à corriger deux fois la même mise en page.

### Ce qui est tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| Le fanion des articles lus | **Retiré**, avec son composant, ses tests et ses captures | Demande de l'auteur : GOAL-017 l'avait posé pour que l'on ne relise pas sans le savoir, et l'usage a montré qu'il attire l'œil sur ce qu'il y a de moins intéressant dans le flux. L'atténuation de `GOAL-017-T06` allait déjà dans ce sens ; l'auteur va au bout |
| Ce que le retrait ne touche pas | `ArticleUiModel.isRead` et sa projection | L'état lu reste ce qui décide du marquage et de la purge (SPECS.md §5.4). Seule sa **représentation** disparaît. Retirer le champ ferait tomber la règle avec le décor |
| Le partage | Le **sélecteur natif** (`ACTION_SEND` via `createChooser`), sur les deux modes | Ce que l'auteur demande, et la seule forme qui n'engage aucun service tiers (SPECS.md §7.4) : l'application ne choisit pas la destination, elle passe la main au système |
| Ce qui est partagé | Le titre puis l'URL d'origine | Une URL nue ne dit pas ce qu'on envoie. L'extrait, lui, est écourté par nous : le transmettre partagerait notre troncature pour du contenu |
| Un article sans lien | Ne se partage pas, et le bouton n'y paraît pas | Même règle que l'ouverture (SPECS.md §4.7). Partager un titre seul enverrait un message sans objet |
| Le bouton « Ouvrir l'article » en Balayage | **Retiré** : la carte entière ouvre l'article | Demande de l'auteur. Le KDoc d'`OpenAction` défendait l'inverse — un appui pris pour une ouverture pendant un balayage hésitant. Compose distingue le `tap` du `drag` : le geste horizontal n'est pas consommé par le clic, et c'est ce qu'un test doit constater |
| Un article sans lien en Balayage | La carte n'est pas cliquable, et le dit | Ce que la mention « pas de lien » fait déjà. Elle reste |
| SPECS.md §2 | Le partage **quitte** le hors-périmètre | Il y figurait sous « partage social, commentaires, annotations ». Un sélecteur système n'est aucun des trois, mais l'exclusion était écrite assez largement pour le couvrir : la lever explicitement vaut mieux que l'interpréter |

### Tâches

- [x] `GOAL-020-T01` **Le fanion est retiré** : `ReadFlag`, ses appels dans les
      deux écrans, ses tests, ses chaînes et ses deux captures.
      `FeedTestTags` disparaît avec lui — il ne portait que `READ_FLAG`.
      SPECS.md §4.5 et ARCHITECTURE.md §9.9 sont traitées **ici** et non en
      `T06` : elles décrivent le fanion, et les laisser une tâche de plus
      aurait fait mentir la documentation sur du code déjà supprimé. La leçon
      de §9.9 — « tester l'écran ne teste pas ce qui l'alimente » — est
      conservée en ARCHITECTURE.md §8.3, où elle vaut pour tout champ
      d'`ArticleUiModel` et non pour le seul fanion.
      `verifyRoborazziDebug` passe sans réenregistrement : aucune capture
      restante ne portait d'article lu
- [x] `GOAL-020-T02` `ArticleSharer` dans `presentation/browser` : la décision —
      ce qui se partage, ce qui se tait — éprouvable en JVM, le lancement de
      l'intention isolé derrière une interface fonctionnelle, comme
      `CustomTabLauncher`.
      Deux écarts au modèle, tous deux voulus : `ArticleShareOutcome` n'a que
      deux valeurs — pas d'équivalent de `NoBrowser`, le sélecteur étant
      fourni par le système et disant lui-même qu'aucune application ne peut
      recevoir — et `isSupportedWebLink` passe de `private` à `internal`
      plutôt que d'être recopiée, pour que les deux règles de schémas ne
      puissent pas diverger. Le gabarit du texte partagé est une ressource,
      donnée au partageur par `rememberArticleSharer` : la composition reste
      éprouvable en JVM, la formulation reste traduisible
- [x] `GOAL-020-T03` **Bouton Partager sur la carte**, dans les deux modes,
      cible de 48 dp et description pour le lecteur d'écran — tests d'écran.
      `ArticleShareButton` vit dans `feed/`, comme `RefreshButton` : même
      action des deux côtés. Posé **sous** les textes de la carte et non sur
      la ligne du flux et de la date — là-haut, un lecteur d'écran annoncerait
      la commande avant le titre de l'article qu'elle partage.
      `onArticleShare` est **sans valeur par défaut** sur les deux écrans :
      un `{}` implicite laisserait un bouton visible et inerte.
      Références Roborazzi réenregistrées et **regardées** dans ce même
      incrément, plutôt que reportées à `T05` : entre les deux, la
      vérification visuelle aurait été rouge sans que cela signifie rien
- [ ] `GOAL-020-T04` **En Balayage, la carte entière ouvre l'article** et le
      bouton disparaît. Un test constate que le balayage passe toujours
- [ ] `GOAL-020-T05` Captures Roborazzi des deux modes, clair et sombre,
      **regardées**
- [ ] `GOAL-020-T06` Documentation : SPECS.md §2, §4.3, §4.7, §4.8,
      ARCHITECTURE.md §9.9 et §9, TASKS.md

---

## GOAL-021 — La documentation passe à l'anglais, l'interface devient bilingue

**Statut : TODO**

Demandé par l'auteur : la documentation du dépôt est **remplacée** par sa
traduction anglaise — il n'en reste pas deux versions, qui divergeraient au
premier commit. L'interface, elle, devient **bilingue** : anglais par défaut,
français conservé.

### Ce qui est tranché avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| Portée de la traduction | **Tous** les `.md` du dépôt, `docs/` compris | Réponse de l'auteur. Une documentation à moitié traduite oblige à deviner où chercher |
| Le français est-il conservé ? | **Non** | « Remplacer », littéralement. Deux versions d'AGENTS.md diverge­raient sans que rien ne le signale |
| PROMPT.md | Traduit, et reste figé | Il conserve l'intention initiale : sa langue change, pas son contenu |
| L'interface | **Bilingue** : `values/` en anglais, `values-fr/` en français | Réponse de l'auteur. L'anglais par défaut, parce que `values/` est ce que reçoit tout appareil dont la langue n'est pas prévue |
| Les captures Roborazzi | **Inchangées** | Le harnais fixe déjà `@Config(qualifiers = "fr-rFR…")` : les références restent françaises, et le déplacement des chaînes vers `values-fr/` ne les touche pas. Ce n'est pas un contournement — c'est ce qui rend le changement de langue vérifiable sans réenregistrer 58 images |
| Comment `values/` est éprouvé | Un test d'écran en `en-rUS` | Sans lui, une chaîne oubliée dans la traduction ne se verrait qu'à l'exécution sur un appareil anglophone. Les captures, elles, ne regardent que le français |
| KDoc et messages de commit | **Restent en français** | AGENTS.md §9 le demande, et l'auteur n'a demandé que la documentation. À rouvrir s'il le souhaite |

### Tâches

- [ ] `GOAL-021-T01` Traduire `README.md`, `AGENTS.md`, `CONTRIBUTING.md`,
      `CLAUDE.md`, `PROMPT.md` et `docs/freshrss-api.md`. Ces six-là ne sont
      touchés ni par GOAL-019 ni par GOAL-020 : ils se traduisent en parallèle
- [ ] `GOAL-021-T02` **Interface bilingue** : les six fichiers de chaînes
      passent en `values-fr/`, `values/` reçoit l'anglais, et un test d'écran en
      `en-rUS` constate que rien n'y manque
- [ ] `GOAL-021-T03` Traduire `SPECS.md`, `ARCHITECTURE.md` et `TASKS.md`.
      **Après** GOAL-019 et GOAL-020, qui les modifient — traduire d'abord
      obligerait à traduire deux fois
- [ ] `GOAL-021-T04` SPECS.md §7.3 réécrite : l'interface n'est plus « en
      français » mais bilingue, l'anglais par défaut

---

## Points bloqués

Un seul, hors de notre main :

- `GOAL-001-T17` — le lint Android ne peut pas analyser les sources de test :
  AGP 9.3.1 plante sur ses propres composants. Réessayé le 2026-08-08, la trace
  est inchangée. Se lèvera avec une version d'AGP, pas avec du code d'ici.

`GOAL-012-T07` en est sorti le 2026-08-08 : ce n'était pas un blocage mais un
arbitrage, tranché et inscrit dans SPECS.md §7.1.

---

## Questions ouvertes

Les décisions fonctionnelles différées sont listées dans [SPECS.md §8](./SPECS.md).
Les incertitudes sur l'API distante sont listées dans
[docs/freshrss-api.md §6](./docs/freshrss-api.md). Chacune est tranchée par le
Goal qui la rencontre, puis **inscrite** — jamais laissée implicite dans le code.
