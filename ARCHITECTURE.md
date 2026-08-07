# ARCHITECTURE.md — Architecture technique

Source de vérité **technique** : comment l'application est conçue.

Le *quoi* est dans [SPECS.md](./SPECS.md), l'*ordre* dans [TASKS.md](./TASKS.md),
les *règles de travail* dans [AGENTS.md](./AGENTS.md). La référence de l'API
distante est dans [docs/freshrss-api.md](./docs/freshrss-api.md).

> Ce document décrit l'état **visé**, et signale explicitement ce qui n'existe
> pas encore. La §9 recense ce qui est réellement dans le dépôt : c'est elle qui
> doit être mise à jour à chaque étape, et un écart entre les deux est une
> incohérence à traiter, pas à ignorer.

---

## 1. Découpage en modules

```
:domain   Kotlin/JVM pur — décide
:app      Android — affiche, stocke, appelle
```

### 1.1 `:domain` n'a pas le SDK Android sur son classpath

C'est une contrainte de compilation, pas une convention : le module est un
`kotlin("jvm")`, le SDK Android n'y est pas. Une dépendance Android y devient
donc une **erreur de compilation**, et non une remarque de revue que l'on peut
oublier de faire.

Ce que cela garantit :

- le domaine se teste en JVM pure, sans Robolectric ni émulateur — des tests qui
  se comptent en millisecondes se lancent à chaque sauvegarde ;
- aucune règle métier ne peut dépendre d'un `Context`, d'un `Cursor` ou d'un
  `SharedPreferences` ;
- l'algorithme de mélange (SPECS.md §4.2), qui est le cœur de l'application,
  reste une fonction pure éprouvable exhaustivement.

`kotlinx-coroutines-core` est la seule dépendance de `:domain`. `Flow` et
`suspend` font partie du vocabulaire du domaine ; ce n'est pas une dépendance
Android.

### 1.2 Pourquoi deux modules et pas trois

Un module `:data` séparé serait défendable. Il n'apporterait ici aucune
contrainte que `:domain` ne porte déjà : c'est `:domain` qui définit les
interfaces, et le sens des dépendances est donc déjà imposé. Un troisième module
coûterait un temps de configuration Gradle à chaque construction pour une
garantie que l'on a déjà.

Ce choix se reconsidère si `:app` devient difficile à naviguer.

---

## 2. Le flux d'une donnée

```
UI (Compose)
 ↓ état immuable
ViewModel
 ↓ appel suspendu
Use Case                    :domain
 ↓ interface
Repository (interface)      :domain
 ↓ implémentation
Repository (implémentation) :app/data
 ↓
FreshRssApi  ·  Room  ·  DataStore
 ↓
HTTP (Ktor)  ·  SQLite  ·  fichier
```

Les dépendances pointent **toutes vers `:domain`**. Aucune classe de `:domain`
ne connaît Ktor, Room, DataStore ou Compose.

### 2.1 Ce qui doit rester confiné à la couche FreshRSS

Rien de ce qui suit ne doit fuir au-dessus de `FreshRssApi` et de son
repository. Un `ViewModel` qui manipulerait un `continuation` serait un défaut
d'architecture, pas un raccourci.

- `ClientLogin`, le jeton `Auth`, l'en-tête `GoogleLogin` ;
- le jeton de modification `T` ;
- les chemins des points d'entrée et le préfixe `/api/greader.php` ;
- la forme des réponses JSON, y compris `categories` comme porteur de l'état lu ;
- le jeton `continuation` et son unité (décimale) face aux identifiants d'article
  (hexadécimaux) ;
- les trois unités de temps de l'API (secondes, microsecondes, nanosecondes) ;
- les codes HTTP particuliers (`501` sur `output`, `503` sur API désactivée).

Le domaine ne connaît qu'un `Article`, un `Feed`, un `PageCursor` opaque et un
type d'erreur métier.

---

## 3. Injection de dépendances — Hilt

Hilt, hérité du template et conservé : le graphe est vérifié à la compilation,
ce qui est la propriété la plus utile pour un projet mené par étapes autonomes —
un module oublié ne compile pas, il ne plante pas à l'exécution.

Modules, tous dans `app/src/main/kotlin/…/di/` :

| Module | Fournit |
|---|---|
| `DispatcherModule` | Les trois `CoroutineDispatcher` qualifiés |
| `CoroutineScopeModule` | La portée `@ApplicationScope` |
| `DataStoreModule` | Le `DataStore<Preferences>` des réglages |
| `TimeModule` | L'implémentation de `Clock` |

### 3.1 Dispatchers injectés, jamais référencés

`kotlinx.coroutines.Dispatchers` n'est cité qu'à un seul endroit du projet :
`DispatcherModule`. Partout ailleurs, un `CoroutineDispatcher` qualifié est
injecté (`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`).

Sans cela, aucun test ne peut contrôler l'ordonnancement ni avancer le temps
virtuellement — et un test qui attend réellement est un test que l'on finit par
désactiver.

`DispatcherModuleTest` vérifie qu'aucun qualifier n'a été inversé : le
compilateur ne peut pas le voir, les trois ayant le même type.

### 3.2 Une seule source de temps

`System.currentTimeMillis()` n'est appelé que dans `TimeModule`. Tout le reste
reçoit un `Clock` (`:domain`). Les tests utilisent `FakeClock`, qui n'avance que
sur ordre.

C'est ce qui rend éprouvable la durée de visibilité du marquage automatique
(SPECS.md §4.5) sans attendre une seconde par test.

---

## 4. Accès réseau — Ktor

Choix retenu et raisons :

- **moteur OkHttp** — c'est le client HTTP éprouvé d'Android : gestion des
  reprises, du pool de connexions et de TLS déjà résolue ;
- **`kotlinx.serialization`** — sérialisation générée à la compilation, sans
  réflexion, donc compatible avec R8 sans règles de conservation à maintenir ;
- **`MockEngine`** (`ktor-client-mock`) — les tests de la couche API décrivent
  des réponses HTTP littérales, y compris des réponses malformées. C'est la seule
  façon d'éprouver la lecture d'un JSON réel sans serveur.

Contraintes propres à FreshRSS, à respecter dans l'implémentation :

- **les réponses d'erreur sont en texte brut**, jamais en JSON. Une
  désérialisation systématique masquerait le code HTTP réel — le
  `ContentNegotiation` ne doit s'appliquer qu'aux réponses `2xx` ;
- **`ClientLogin` répond en texte brut**, sous forme de paires `clé=valeur` :
  ce point d'entrée ne se lit pas comme les autres ;
- **`output=json` est obligatoire** sur `subscription/list`, `tag/list` et
  `unread-count` — l'omettre répond `501` ;
- **les identifiants d'article changent de base** selon le champ : hexadécimal
  dans `items[].id`, décimal dans `continuation` et dans le paramètre `i`. La
  conversion appartient à la couche API et à elle seule.

### 4.1 Deux sondes avant toute connexion

`FreshRssApi` expose deux vérifications que rien n'oblige à faire, et qu'il faut
pourtant faire — chacune évite un diagnostic faux :

- **`probe()`** cherche le corps `OK` d'un `GET` nu sur le point d'entrée. Sans
  elle, une faute de frappe dans l'adresse enverrait le mot de passe API à un
  serveur qui n'est pas celui de l'utilisateur, et produirait un `401` qu'il
  imputerait à ses identifiants.
- **`checkAuthorizationForwarding()`** constate que le serveur web transmet bien
  l'en-tête `Authorization`. Elle n'a de sens qu'**après** l'obtention du jeton :
  `ClientLogin` n'exige aucun en-tête, et la payer plus tôt coûterait un
  aller-retour à chaque tentative, y compris à celles vouées à échouer sur les
  identifiants. La payer plus tard conserverait une session vouée à boucler sur
  des `401`.

Leurs particularités — statut toujours `200`, en-tête factice requis, chaîne de
requête interdite — sont documentées dans docs/freshrss-api.md §1.

### 4.2 Pagination

> ⚠️ **Le piège le plus dangereux de cette API, et il est confirmé par
> l'expérience.** Un curseur invalide — vide, non numérique — ne produit
> **aucune erreur** : le serveur le ramène silencieusement au début du flux et
> renvoie à nouveau la première page, avec le même `continuation`. Une faute de
> sérialisation du curseur se manifeste donc par une boucle infinie muette.
> C'est pourquoi le paramètre `c` n'est **jamais** émis avec une valeur vide, et
> pourquoi `PageCursor` est un type dédié plutôt qu'une `String` nue.


Le curseur de FreshRSS est **relatif**, non positionnel : la réponse porte un
`continuation` égal à l'identifiant du dernier article renvoyé, et la requête
suivante le repasse en `c`. Voir [docs/freshrss-api.md §3.5](./docs/freshrss-api.md).

Deux conséquences que le code doit refléter :

- l'**absence** de `continuation` signifie « fin du flux » — c'est le seul signal
  de fin, il n'y a pas de compteur total ;
- un curseur invalide est silencieusement traité comme « début du flux » par le
  serveur. Une erreur de sérialisation se manifeste donc par une **répétition de
  la première page**, jamais par une erreur. Un test doit couvrir ce cas.

---

## 5. Persistance

### 5.1 Deux supports, sans recouvrement

| Support | Contenu |
|---|---|
| **Room** | Les collections : articles en cache, marquages en attente |
| **DataStore (chiffré)** | Les scalaires : adresse du serveur, identifiant, jeton, seuils |

La règle est stricte : une donnée vit dans l'un **ou** l'autre, jamais dans les
deux. Un réglage dupliqué finit toujours par diverger.

### 5.2 Le mot de passe API n'est jamais enregistré

Le jeton FreshRSS n'expirant pas, le conserver suffit à rouvrir l'application
sans reconnexion. Garder en plus le mot de passe n'apporterait rien et
doublerait la surface exposée (SPECS.md §3.4).

Le chiffrement passe par **AES/GCM sur `AndroidKeyStore`**, écrit à la main :
`androidx.security:security-crypto` aurait fait le même travail, mais la
bibliothèque est dépréciée et AGENTS.md §2 l'interdit.

`SecretCipher` abstrait le chiffrement pour une raison précise : Robolectric ne
simule pas `AndroidKeyStore`. Sans cette interface, ni la persistance ni
l'effacement à la déconnexion ne seraient éprouvables — c'est-à-dire précisément
la partie où les fautes se logent. Seule l'implémentation *keystore* reste non
couverte, et le dit.

### 5.3 Jeton refusé et déconnexion sont deux choses différentes

| Opération | Jetons | Adresse et identifiant |
|---|---|---|
| `invalidateSession()` — le serveur refuse le jeton | effacés | **conservés** |
| `signOut()` — geste délibéré de l'utilisateur | effacés | effacés |

Le rappel de saisie (`SignInHint`) ne contient aucun secret : c'est ce qui
permet de le conserver. L'utilisateur dont le jeton est refusé n'a probablement
qu'un mot de passe API à renouveler ; lui faire retaper l'adresse de son serveur
serait gratuit (SPECS.md §3.4).

### 5.4 Room n'est pas encore appliqué

Le plugin, ses dépendances et le bloc `room { schemaDirectory(…) }` ont été
retirés d'`app/build.gradle.kts` pendant la Phase 0 : une base sans entité ne
compile pas, et inventer une entité avant de connaître la forme d'un article
serait anticiper (AGENTS.md §2).

Le Goal du cache local les réapplique. Les dépendances restent déclarées dans le
catalogue de versions. Quand elles reviendront, **les schémas seront versionnés**
(`app/schemas/`) : c'est ce qui permet à Room de vérifier les migrations
automatiquement, et à une revue de voir une évolution de base dans le diff.

### 5.3 Secrets

Le jeton `Auth` et le mot de passe API sont chiffrés au repos, adossés au
*keystore* Android. Ils ne sont jamais journalisés, jamais inclus dans un
rapport d'erreur, jamais placés dans une URL.

Le jeton FreshRSS n'expire pas (voir docs/freshrss-api.md §2.1) : il est donc
conservé entre deux lancements. En revanche il s'invalide sans préavis si
l'utilisateur change son mot de passe API — un `401` sur une requête
authentifiée ramène à l'écran de connexion, il ne s'affiche pas comme une erreur
réseau.

---

## 6. Présentation

### 6.1 Un état immuable par écran

Chaque écran a une `data class ...UiState` produite par son `ViewModel` et
consommée par un Composable **sans état**.

- Le Composable **affiche** l'état, il ne le **dérive** pas. Aucun calcul dans
  un `@Composable`.
- Chaque écran a une `@Preview` privée qui fonctionne **sans injection** — si
  une prévisualisation exige un graphe Hilt, l'écran est trop couplé.
- Les ViewModels publient en `WhileSubscribed(5 s)` (`UiStateSharing`) : sans
  abonné, les observations s'arrêtent. Les cinq secondes de grâce couvrent une
  rotation sans tout réenregistrer.

### 6.2 Navigation

`AppDestination` rassemble route, libellés et icône. La barre de navigation est
**dérivée de l'énumération** : ajouter une destination consiste à ajouter une
entrée, et rien d'autre. `AppNavigationBarTest` constate cette dérivation, ce
qui vaut donc pour toute destination ajoutée ensuite.

### 6.3 L'écran d'attente

`PlaceholderScreen` occupe les destinations qui n'ont pas encore leur écran
réel. Il existe pour que l'ossature — thème, barre de titre, barre de
navigation, graphe, chaîne de captures — soit **exécutable et vérifiable dès la
Phase 0**, sans anticiper le contenu des écrans.

Chaque appel disparaît avec le Goal qui livre l'écran correspondant. Un
`PlaceholderScreen` encore présent après le Goal concerné est une dette :
TASKS.md la recense.

### 6.4 Aiguillage racine

`SessionGate` décide entre l'écran de connexion et l'application, à partir de la
seule présence d'une session. Aucun écran n'a donc à gérer de redirection : un
jeton refusé fait disparaître la session, et la racine bascule d'elle-même.

L'état `Unknown` n'est pas décoratif : la session vit sur disque, et sa première
lecture n'est pas instantanée. Partir de « déconnecté » ferait apparaître
l'écran de connexion un instant à chaque lancement, y compris pour un
utilisateur déjà connecté.

### 6.5 Le flux Discover *(à concevoir)*

Contraintes déjà établies par SPECS.md, et qui pèseront sur la conception :

- **liste paresseuse** : le flux est potentiellement long, tout composer serait
  intenable ;
- **la visibilité de chaque élément doit être mesurable** — proportion affichée
  et durée continue (SPECS.md §4.5). C'est le point technique le plus délicat de
  l'application, et il détermine largement la structure de la liste ;
- **la position de lecture doit survivre** à l'insertion d'articles en tête lors
  d'un rafraîchissement (SPECS.md §4.6) : les éléments doivent donc porter une
  clé stable ;
- **l'ordre doit être déterministe** : le mélange est calculé dans `:domain`, à
  partir d'une graine reproductible, et non tiré à l'affichage.

---

## 7. Erreurs

Une erreur traverse trois formes, et une seule est visible de l'utilisateur :

```
Exception technique (Ktor, SQLite)     couche data
        ↓ traduite
Erreur de domaine (type scellé)        :domain
        ↓ traduite
Message affichable                     :app/presentation
```

Aucune exception technique ne remonte au-dessus de la couche `data`. Aucune
chaîne de caractères destinée à l'utilisateur n'est produite en dessous de la
couche présentation : les messages sont des ressources, ce qui les rend
traduisibles et vérifiables.

SPECS.md §3.3 impose un message **distinct par cause** d'échec de connexion. Le
type d'erreur du domaine doit donc distinguer ces cas — un type d'erreur unique
rendrait la spécification inapplicable.

---

## 8. Tests

| Portée | Outil | Ce qui est éprouvé |
|---|---|---|
| `:domain` | JUnit, JVM pure | Mélange, décisions, transformations |
| Couche API | Ktor `MockEngine` | Lecture de réponses HTTP littérales |
| Repositories | Room en mémoire, DataStore temporaire | Persistance et rejeu |
| ViewModels | `kotlinx-coroutines-test` | Transitions d'état |
| Écrans | Compose UI Test + Robolectric | Ce qui est **affiché** |
| Rendu | Roborazzi | Ce à quoi cela **ressemble** |

Les doubles sont des **Fakes versionnés** (`domain/src/testFixtures/`), pas des
mocks générés : un Fake se lit, se déboguer et documente le contrat mieux qu'une
suite de `when(...).thenReturn(...)`.

### 8.1 Couverture de `:domain`

`koverVerify` impose 98 % sur `:domain`. Le seuil constate un acquis plutôt
qu'il ne fixe un objectif.

**Levé.** Le garde-fou mesure réellement depuis les premiers modèles
d'authentification : il a immédiatement échoué à 86,2 %, puis à 94,2 %, avant
d'être satisfait. Il n'était pas décoratif.

### 8.2 Rendu visuel

Les tests d'interface vérifient *ce qui est affiché* ; les captures vérifient
*à quoi cela ressemble*. Une régression de contraste ou de thème sombre ne casse
aucune assertion textuelle.

Chaque écran est capturé en clair **et** en sombre, la couleur dynamique
désactivée et le format d'écran figé — sans quoi la référence dépendrait du fond
d'écran de l'utilisateur ou de la configuration par défaut de Robolectric.

La base de capture rend le contenu dans un `Surface`, et non un `Box`. Un `Box`
ne fournit pas `LocalContentColor` : le texte qui ne fixe pas sa couleur
retombait sur du noir, invisible en thème sombre. La capture montrait donc un
défaut que l'application, qui rend ses écrans dans un `Scaffold`, n'a pas.
Constaté en Phase 0.

---

## 9. État réel du dépôt

**À maintenir à chaque étape.** Un écart entre cette section et le contenu du
dépôt est une incohérence — voir AGENTS.md §8.

```
.
├── PROMPT.md · SPECS.md · AGENTS.md · ARCHITECTURE.md
├── TASKS.md · CONTRIBUTING.md · README.md · CLAUDE.md
│
├── docs/
│   └── freshrss-api.md          Relevé de l'API FreshRSS
│
├── .claude/
│   ├── settings.json            Autorisations partagées (versionné)
│   ├── settings.local.json      JAVA_HOME, ANDROID_HOME (non versionné)
│   └── commands/
│       ├── goal.md · task.md · status.md · verify.md
│
├── config/detekt/detekt.yml
├── .github/workflows/ci.yml
├── gradle/libs.versions.toml    Catalogue de versions
│
├── domain/                      Kotlin/JVM pur
│   └── src/
│       ├── main/…/domain/
│       │   ├── auth/            AuthError · AuthResult · AuthRepository
│       │   │                    Credentials · AuthToken · AuthSession
│       │   │                    ServerAddress · SignInHint
│       │   ├── core/Outcome.kt  issue générique <valeur, erreur>
│       │   ├── feed/            Article · ArticleId · FeedRef · PageCursor
│       │   │                    ArticlePage · FeedError · ArticleRepository
│       │   └── time/Clock.kt
│       ├── test/…/domain/auth/  couverture ~100 %
│       └── testFixtures/…/domain/
│           ├── auth/FakeAuthRepository.kt
│           ├── feed/Articles.kt  fabriques d'articles
│           └── time/FakeClock.kt
│
└── app/                         Android
    └── src/
        ├── main/…/freshrssdiscover/
        │   ├── FreshRssDiscoverApplication.kt · MainActivity.kt
        │   ├── data/
        │   │   ├── api/         FreshRssApi · ApiOutcome · StreamContentsDto
        │   │   │                FreshRssHttpClient · AuthErrorMapping
        │   │   │                ArticleMapping
        │   │   ├── local/       SessionStore
        │   │   ├── network/     NetworkAvailability
        │   │   ├── repository/  DefaultAuthRepository · DefaultArticleRepository
        │   │   └── security/    SecretCipher · KeystoreSecretCipher
        │   ├── di/              Dispatchers · portées · DataStore · Clock
        │   │                    Network · Repository · Security
        │   └── presentation/
        │       ├── LoadingIndicator.kt · UiStateSharing.kt
        │       ├── PlaceholderScreen.kt · SessionGate.kt
        │       ├── login/       LoginScreen · LoginViewModel · LoginUiState
        │       │                LoginFailureLabels
        │       ├── navigation/  AppDestination · AppNavHost · AppNavigationBar
        │       └── theme/       Color · Spacing · Theme
        └── test/…/freshrssdiscover/
            ├── TestApplication.kt
            ├── data/            api · local · repository · security (FakeSecretCipher)
            ├── di/DispatcherModuleTest.kt
            └── presentation/
                ├── ScreenshotTest.kt          base Roborazzi
                ├── ScreensScreenshotTest.kt   12 références versionnées
                ├── MainDispatcherRule.kt · UiStateCollector.kt
                ├── login/ · navigation/
                └── SessionGateViewModelTest.kt
```

### 9.1 Ce qui n'existe pas encore

Aucune ligne de code métier n'est écrite. Précisément, sont **absents** :

- le cache local, et donc toute résilience hors ligne ;
- l'algorithme de mélange ;
- le flux Discover et l'écran de réglages — l'écran de connexion, lui, existe ;
- Room (voir §5.4), déclaré mais non câblé.

### 9.2 Ce qui est hérité du template, délibérément

Le dépôt provient de `c4software/tailscale-auto-rules`, dont la logique métier a
été retirée.

`MainDispatcherRule` a trouvé son usage avec le premier ViewModel.
`UiStateCollector` n'en a pas encore : il sert aux ViewModels publiant en
`WhileSubscribed`, ce qu'aucun ne fait pour l'instant. Il est conservé — c'est
une exception assumée à l'interdit « pas de code mort » (AGENTS.md §2), inscrite
ici pour qu'elle soit visible plutôt que tacite.
