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

> **Un étage de ce schéma est vide, et c'est délibéré.** Le dépôt n'a
> aujourd'hui **aucune classe de use case** : les ViewModels appellent les
> interfaces de dépôt directement. Un use case qui se contenterait de relayer un
> appel serait l'anticipation qu'AGENTS.md §2 interdit. Les décisions qui
> auraient justifié cet étage vivent déjà dans `:domain` sous forme de fonctions
> pures — `interleaveBySource`, `ReadDetector`, `ReadTransmissionScheduler` —
> appelées par qui en a besoin. L'étage reste dans le schéma parce qu'il est la
> place réservée à la première règle qui coordonnera plusieurs dépôts.

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
| `DatabaseModule` | La base Room et ses DAO |
| `NetworkModule` | Le `HttpClient` Ktor |
| `SecurityModule` | L'implémentation de `SecretCipher` |
| `SettingsModule` | L'implémentation de `SettingsRepository` |
| `RepositoryModule` | Les liaisons interface `:domain` → implémentation `data` |

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
| **DataStore** | Les scalaires : adresse du serveur, identifiant, jeton, seuils, date du dernier contact serveur |

La règle est stricte : une donnée vit dans l'un **ou** l'autre, jamais dans les
deux. Un réglage dupliqué finit toujours par diverger.

Un **seul** fichier DataStore, partagé par `SessionStore`, `SettingsStore` et
`FeedFreshnessStore`, chacun sur ses clés préfixées. Le chiffrement n'y est pas
global : ce sont les **jetons** qui passent par `SecretCipher` (§5.2), pas
l'adresse du serveur ni les seuils. Chiffrer ce qui n'est pas un secret coûterait
le même prix sans rien protéger, et rendrait le stockage illisible au moment
précis où le lire aide à diagnostiquer.

Une exception assumée, et elle est dans `FeedFreshnessStore` : la **date** du
dernier contact serveur est persistée, mais l'**acquittement** de l'avis
d'ancienneté (SPECS.md §4.6) ne l'est pas — il vit dans un flux en mémoire.
Le persister ajouterait une clé pour une situation qui ne se présente pas : à la
réouverture, ou bien une requête aboutit et la date se remet à jour, ou bien
elle échoue et c'est le bandeau hors ligne qui parle. C'est aussi ce qui oblige
ce store à être `@Singleton` — l'acquittement doit survivre à la bascule entre
les deux modes de présentation, qui détruit un ViewModel et en construit un
autre.

### 5.2 Le mot de passe API n'est jamais enregistré

Le jeton FreshRSS n'expirant pas, le conserver suffit à rouvrir l'application
sans reconnexion. Garder en plus le mot de passe n'apporterait rien et
doublerait la surface exposée (SPECS.md §3.4).

Le chiffrement passe par **AES/GCM sur `AndroidKeyStore`**, écrit à la main :
`androidx.security:security-crypto` aurait fait le même travail, mais la
bibliothèque est dépréciée et AGENTS.md §2 l'interdit.

**Deux partages, et ils ne servent pas la même chose.** `SecretCipher` permet
d'éprouver ce qui entoure le chiffrement — persistance, effacement à la
déconnexion — sans magasin de clés, que Robolectric ne simule pas.
`SecretKeySource` permet d'éprouver le chiffrement **lui-même** : le format, le
vecteur d'initialisation, l'authentification GCM, et la conduite devant un texte
illisible. Sans ce second partage, tout `KeystoreSecretCipher` restait hors de
portée pour la seule raison qu'il fabriquait sa clé.

Ce qui demeure non couvert se réduit donc à `AndroidKeyStoreKeySource` — une
vingtaine de lignes qui n'appellent que la plateforme. Réessayé le 2026-08-08 :
le fournisseur `AndroidKeyStore` lève toujours `NoSuchAlgorithmException` sous
Robolectric.

### 5.3 Jeton refusé et déconnexion sont deux choses différentes

| Opération | Jetons | Adresse et identifiant |
|---|---|---|
| `invalidateSession()` — le serveur refuse le jeton | effacés | **conservés** |
| `signOut()` — geste délibéré de l'utilisateur | effacés | effacés |

Le rappel de saisie (`SignInHint`) ne contient aucun secret : c'est ce qui
permet de le conserver. L'utilisateur dont le jeton est refusé n'a probablement
qu'un mot de passe API à renouveler ; lui faire retaper l'adresse de son serveur
serait gratuit (SPECS.md §3.4).

### 5.4 Room, et ce que le cache ne fait pas reculer

Room porte les collections, DataStore les scalaires. Les schémas sont
versionnés dans `app/schemas/` : c'est ce qui permet à Room de vérifier
automatiquement les migrations, et à une revue de constater une évolution de
base dans le diff plutôt que de la déduire du code des entités.

**L'état lu local ne recule jamais.** Un article enregistré comme lu le reste,
même si le serveur le décrit encore comme non lu. Ce n'est pas une commodité :
un marquage parti hors ligne n'est transmis qu'au retour du réseau (SPECS.md
§5.2), et jusque-là le serveur ignore tout. Écraser l'état local par le sien
ferait **réapparaître dans le flux ce que l'utilisateur vient de lire** — la
régression la plus visible qu'un cache puisse produire. Dans l'autre sens, un
article lu ailleurs arrive lu et le devient ici : « lu » se propage, « non lu »
non.

**La purge s'appuie sur l'ancienneté dans le cache**, jamais sur la date de
publication. Purger sur la publication ferait disparaître dans la seconde un
vieil article que l'utilisateur vient d'ouvrir, et qui est encore à l'écran.

`ArticleCache` est la seule frontière entre le modèle de domaine et Room : les
entités ne la franchissent pas, sinon une annotation de persistance finirait
par contraindre la forme d'`Article`.

---

## 6. Présentation

### 6.1 Un état immuable par écran

Chaque écran a une `data class ...UiState` produite par son `ViewModel` et
consommée par un Composable **sans état**.

- Le Composable **affiche** l'état, il ne le **dérive** pas. Aucun calcul dans
  un `@Composable`.
- Chaque écran a une `@Preview` privée qui fonctionne **sans injection** — si
  une prévisualisation exige un graphe Hilt, l'écran est trop couplé.
- Un ViewModel qui **observe une source** publie en `WhileSubscribed(5 s)`
  (`UiStateSharing`) : sans abonné, l'observation s'arrête. Les cinq secondes de
  grâce couvrent une rotation sans tout réenregistrer. C'est le cas de
  `SettingsViewModel`, qui suit les réglages et l'état du cache.
  Un ViewModel qui ne fait qu'**accumuler le résultat de ses propres appels** —
  `DiscoverViewModel`, `LoginViewModel` — porte un `MutableStateFlow` : il n'y a
  aucune observation à interrompre, et la politique de partage n'aurait rien à
  arbitrer. `SessionGate` fait exception dans l'autre sens et démarre en
  `Eagerly` : l'aiguillage racine est observé pendant toute la vie de
  l'application, et le laisser retomber sur `Unknown` ferait clignoter l'écran
  de connexion à chaque retour d'arrière-plan.

### 6.2 Navigation

`AppDestination` rassemble route, libellés et icône. La barre de navigation est
**dérivée de l'énumération** : ajouter une destination consiste à ajouter une
entrée, et rien d'autre. `AppNavigationBarTest` constate cette dérivation, ce
qui vaut donc pour toute destination ajoutée ensuite.

### 6.3 Aiguillage racine

`SessionGate` décide entre l'écran de connexion et l'application, à partir de la
seule présence d'une session. Aucun écran n'a donc à gérer de redirection : un
jeton refusé fait disparaître la session, et la racine bascule d'elle-même.

L'état `Unknown` n'est pas décoratif : la session vit sur disque, et sa première
lecture n'est pas instantanée. Partir de « déconnecté » ferait apparaître
l'écran de connexion un instant à chaque lancement, y compris pour un
utilisateur déjà connecté.

### 6.4 Le cache n'est jamais habillé en page

Question que l'assemblage a posée, et dont la réponse structure tout le reste :
**comment rendre une page issue du cache sans la faire passer pour une fin de
flux ?**

`ArticlePage.nextCursor == null` signifie « fin du flux », et rien d'autre. Une
page de cache n'a pas de curseur : la rendre comme une `ArticlePage` ferait donc
afficher « vous avez tout lu » à un utilisateur simplement privé de réseau.

Le cache est donc une **source parallèle et permanente** —
`observeCachedArticles()`, un flux qui réémet à chaque écriture — pendant que
`loadPage()` continue de rapporter honnêtement `FeedError.NoNetwork`. L'appelant
dispose ainsi du **contenu** et de la **cause** séparément, ce qui lui permet de
signaler l'état sans alarmer, et surtout sans mentir.

Le même flux sert l'affichage immédiat au lancement (SPECS.md §5.1) et la
consultation hors ligne (§5.2) : ce sont deux usages d'un seul mécanisme.

### 6.5 Deux décisions du domaine que l'interface se contente d'appliquer

Le mélange et la détection de lecture sont des **fonctions pures de `:domain`**.
Ce n'est pas une élégance : ce sont les deux endroits où une régression serait
invisible à l'œil, et seuls des tests exhaustifs les tiennent.

**`interleaveBySource`** répartit les sources sans mentir sur la fraîcheur. Les
deux premières règles de SPECS.md §4.2 sont structurellement incompatibles au
delà d'une certaine amplitude ; l'arbitrage retenu — la récence l'emporte, avec
une borne de sept positions — est inscrit dans SPECS.md parce qu'il est visible
par l'utilisateur. La borne est exprimée en **rangs et non en durée** : un seuil
temporel se comporterait très différemment sur un flux qui publie trois articles
par jour et sur un qui en publie trois cents.

**`ReadDetector`** décide quand un article devient lu, à partir d'un double
seuil de surface et de durée continue. Il ne mesure rien lui-même et ne possède
aucune coroutine : il reçoit des observations et répond. Deux conséquences que
l'appelant doit assumer, et que SPECS.md §4.5 consigne désormais :

- la fraction est celle de la **part visible de l'écran**, pas de la hauteur
  propre de l'article — sinon un article plus haut que l'écran ne pourrait
  jamais être marqué lu ;
- l'appelant doit **observer même quand rien ne bouge**. La règle porte sur une
  durée, et la durée ne s'écoule pas toute seule : sans observation périodique,
  un article immobile dix secondes ne serait jamais marqué lu.

### 6.6 Le flux Discover

Contraintes déjà établies par SPECS.md, et qui pèseront sur la conception :

- **liste paresseuse** : le flux est potentiellement long, tout composer serait
  intenable ;
- **la visibilité de chaque élément doit être mesurable** — proportion affichée
  et durée continue (SPECS.md §4.5). C'est le point technique le plus délicat de
  l'application, et il détermine largement la structure de la liste ;
- **la position de lecture doit survivre à la fermeture de l'application**
  (SPECS.md §5.3), et c'est un **article** qui est mémorisé, jamais un rang : le
  flux s'allonge entre deux ouvertures. Les éléments de la liste portent donc une
  clé stable, qui sert à la fois à retrouver cet article et à ne pas recomposer
  ce qui n'a pas changé. Le tirer-pour-rafraîchir, lui, ne préserve rien : il
  remonte en tête, et l'annonce (SPECS.md §4.6) ;
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

### 8.0 Un garde-fou qui était vide

`ktlintCheck` ne vérifiait **aucune source Kotlin de `:app`** : le greffon
ktlint-gradle ne découvre pas les jeux de sources Android d'AGP 9, et n'y
enregistrait qu'une tâche sur les fichiers `.kts`. La commande de vérification
d'AGENTS.md §5 était donc partiellement vide depuis l'origine du dépôt.

Les règles de style passent désormais par **`detekt-formatting`**, qui les
embarque dans Detekt — lequel, lui, voit bien le module. Le jour de sa mise en
place, il a relevé 22 violations, dont quatre imports morts laissés par un
refactor antérieur.

La leçon vaut au-delà de ce cas : un outil de vérification qui ne signale jamais
rien mérite qu'on vérifie **ce qu'il regarde**, pas seulement qu'il passe.

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

## 9. Carte du dépôt

**Des paquets et leur rôle, pas une liste de fichiers.** Une arborescence
recopiée à la main est fausse dès le commit suivant : celle qui figurait ici
mentait sur une dizaine de fichiers, et la maintenir coûtait plus qu'elle ne
rapportait. Ce qui suit ne change qu'avec l'architecture, pas avec chaque
ajout — et se vérifie d'un `find`.

```
domain/                       Kotlin/JVM pur — décide, ne connaît ni HTTP ni disque
├── auth/                     session, identifiants, adresse du serveur, causes d'échec
├── core/                     Outcome<valeur, erreur>
├── feed/                     article, page, curseur, contrats de dépôt
├── read/                     détection de lecture, file de marquages, ordonnancement
├── reminder/                 heure et contenu du rappel de lecture
├── settings/                 réglages de lecture, cache
├── shuffle/                  répartition des sources
└── time/                     Clock

app/
├── data/
│   ├── api/                  FreshRSS : client, points d'entrée, DTO, conversions
│   ├── local/                DataStore (scalaires) et room/ (collections)
│   ├── network/              connectivité
│   ├── repository/           implémentations des contrats du domaine
│   └── security/             chiffrement des secrets au repos
├── di/                       un module Hilt par famille de dépendances
├── reminder/                 rappel de lecture : contrats, travailleur, notification
└── presentation/
    ├── browser/              ouverture de l'article d'origine
    ├── discover/             flux en liste
    ├── feed/                 ce que les deux modes partagent (rechargement, bandelette, ancienneté, illustration)
    ├── lifecycle/            ce qui réagit au passage en arrière-plan
    ├── login/                connexion
    ├── navigation/           destinations, graphe, mode de présentation
    ├── permission/           la permission de notifier, demandée au bon moment
    ├── settings/             réglages
    ├── swipe/                flux en pile de cartes, un article par écran
    └── theme/                couleurs, espacements
```

**Deux paquets pour un même flux, et c'est voulu.** `discover/` et `swipe/`
présentent les mêmes articles selon SPECS.md §4.8, mais rien de leur mise en
page n'est commun : une liste paresseuse et un pagineur n'ont ni le même état,
ni la même mesure de visibilité, ni les mêmes composants. Ce qu'ils partagent
vraiment — le modèle d'article affiché, les phases du flux, le bouton de
rechargement, la bandelette d'avis, la surveillance de l'ancienneté du flux, le
créneau d'illustration — vit dans `discover/` pour les deux premiers, hérités,
et dans `feed/` pour ce qui est né commun.

Ce dernier a d'ailleurs une histoire qui se répète : `FeedNotice` puis
`ArticleIllustration` ont tous deux commencé écrits **deux fois**, à
l'identique, avant qu'une correction ne doive être appliquée aux deux endroits.
Ce qui touche les deux modes se réunit avant d'être corrigé, pas après.

Les tests suivent la même structure, plus `startup/` pour ce qui n'appartient à
aucune couche — construction du graphe, migration de base, démarrage.

Ce que cette carte **ne dit pas**, délibérément : le nombre de tests, le nombre
de captures, l'état d'avancement. Ces chiffres vieillissent en un commit, et
[TASKS.md](./TASKS.md) les porte déjà.

### 9.1 Où chaque pièce du domaine est consommée

Cette section a longtemps recensé des pièces **écrites et éprouvées mais pas
encore branchées**. La distinction avait un sens précis : tant que l'assemblage
n'est pas fait, ce code est mort au sens d'AGENTS.md §2, quel que soit le nombre
de tests qui l'entourent.

**Cet écart est refermé.** Ce qui reste utile, et ce que cette table donne
désormais, c'est le **point de consommation** de chaque pièce — c'est lui qu'une
revue doit pouvoir retrouver, et lui qui redeviendrait faux en premier si une
régression détachait une décision du domaine de son appelant.

| Pièce de `:domain` | Consommée par |
|---|---|
| `interleaveBySource` (14 tests) | `DefaultArticleRepository` — page serveur et flux du cache |
| `ReadDetector` (18 tests) | `DiscoverViewModel`, alimenté par `ArticleVisibility` depuis la liste ; `SwipeViewModel`, alimenté par `pagerVisibility` depuis le pagineur |
| `ReadTransmissionScheduler` | `DefaultReadSyncRepository` — regroupement des lots |
| `ReadSyncRepository` | `DiscoverViewModel` et `SwipeViewModel` (marquage, rejeu au démarrage), `ReadFlushOnBackgroundObserver` (passage en arrière-plan) et `DefaultAuthRepository` (déconnexion) |
| `FeedPresentation` | `FeedPresentationViewModel`, qui aiguille la destination Discover vers l'un des deux modes |
| `FeedFreshness` (15 tests) | `FeedStalenessWatcher`, que les deux ViewModels du flux construisent sur leur portée |
| `FeedFreshnessRepository` | `DefaultArticleRepository` en **écriture** (chaque réponse serveur valide) et `FeedStalenessWatcher` en **lecture** |
| `CacheRepository` | `SettingsViewModel` — état du cache et purge manuelle |
| `SettingsRepository` | `SettingsViewModel`, les deux ViewModels du flux pour les seuils, et `FeedPresentationViewModel` pour le mode de présentation |

Côté `:app`, les mécanismes que la section signalait comme absents sont en place
et couverts : le cache alimente le premier affichage (SPECS.md §5.1) et le hors
ligne (§5.2), le rechargement est câblé jusqu'à `ArticleRepository.refresh()`
depuis les deux modes (§4.6), l'ouverture d'un article le marque lu (§4.7), et
la purge d'ancienneté est déclenchée une fois par démarrage de processus par
`CacheMaintenance` (§5.4).

### 9.3 Le rechargement franchit la frontière de l'ossature

Le bouton de rechargement est posé sur la barre de titre (SPECS.md §4.6), qui
appartient à `MainActivity` — au-dessus du graphe de navigation. L'action, elle,
appartient au ViewModel de la destination affichée, que l'ossature n'a aucune
raison de connaître.

C'est donc l'**action** qui remonte, sous la forme d'un `FeedRefresh` que la
destination publie et que la barre consomme. L'inverse — descendre la barre dans
chaque écran — obligerait chacun à redessiner un titre et une barre de
navigation, et ferait exister trois barres là où il en faut une.

La publication se fait par `DisposableEffect`, et le **retrait** y compte autant
que la pose : sans lui, quitter le flux pour les réglages y laisserait un bouton
branché sur un ViewModel qu'on ne regarde plus.

Le travail restant n'est plus de l'assemblage à rattraper : il est décrit tâche
par tâche dans [TASKS.md](./TASKS.md), qui est le seul document à jour sur ce
point.

### 9.4 Le rappel de lecture ne franchit pas la couche réseau

Le rappel quotidien (SPECS.md §4.9) lit `ArticleRepository.unreadFromCache`, et
ce contrat porte l'interdiction dans sa signature même : il ne rend pas de
`FeedResult`, parce qu'il n'a aucun échec réseau à rapporter.

Ce n'est pas une commodité mais la ligne qui sépare une **notification locale**
d'une **synchronisation en arrière-plan** — SPECS.md §2 accueille la première et
exclut toujours la seconde, et §7.4 veut qu'aucune connexion ne parte sans geste
de l'utilisateur. Une implémentation qui irait chercher une page « pour avoir
des titres plus frais » ferait basculer l'application d'un côté à l'autre de
cette ligne sans que rien ne le signale.

La conséquence est assumée et visible : un article publié depuis la dernière
ouverture n'est pas dans le cache, et ne sera donc pas annoncé.

Trois refus précèdent toute notification, et leur **ordre** compte : pas de
session — l'utilisateur n'est plus connecté, il n'y a rien à rappeler ; réglage
éteint ; puis cache vide. Les deux premiers n'arment pas le rappel du lendemain,
le troisième si — demain il y aura peut-être quelque chose à lire.

### 9.5 La version ne se saisit pas

`versionName` et `versionCode` sont dérivés de la même étiquette Git, dans
`app/build.gradle.kts`. Deux sources de vérité pour une même version sont une
divergence programmée : c'est celle qu'on découvre le jour où l'on publie une
1.1 portant encore le code de la 1.0.

Le code vaut `major × 1 000 000 + minor × 1 000 + patch`, strictement croissant
avec la version et borné loin sous le maximum accepté par Google Play. Il est
plancher à 1, parce qu'Android refuse un code nul et que le repli `0.0.0-…` en
produisait un.

`providers.exec` plutôt qu'un appel direct à `ProcessBuilder` : le dépôt utilise
le cache de configuration de Gradle, qu'un appel non déclaré invaliderait à
chaque construction.

### 9.8 Une image n'est jamais agrandie

Le créneau d'illustration est fixe (16/9) et l'image le remplit : c'est ce qui
empêche la liste de sursauter à l'arrivée de chaque image, et c'est aussi ce qui
étirait les vignettes trop étroites.

`ArticleIllustration` compare donc la largeur **source**, que Coil rend dans son
état de succès, à la largeur **mesurée** du créneau. La décision est une
fonction pure — `needsUpscaling` — plutôt qu'une condition noyée dans un `Box` :
elle s'éprouve sans rendu, là où une capture serait nécessaire pour vérifier
l'autre.

Deux choix d'échelle, et le second a coûté un essai sur appareil :

- le fond emploie `Crop` sur une copie **débordant** légèrement du créneau —
  `blur` estompe jusqu'aux bords, et sans ce débordement le cadre reparaîtrait
  en périphérie ;
- l'image de devant emploie `Inside`, et non `Fit`. `Fit` remplit la plus petite
  dimension, donc agrandit encore : le premier essai livrait une image toujours
  floue sur un fond correct. `Inside` ne grandit jamais au-delà de la taille
  native — la seule échelle qui n'invente aucun pixel.

`Modifier.blur` exige l'API 31 quand le projet descend à 26 : en dessous, rien
ne change (SPECS.md §8, question 12).

### 9.7 Le lancement ne parle à personne, et son ordre ne dépend de rien

Le flux du lancement doit rouvrir **à l'identique** (SPECS.md §5.1). Quatre
mécanismes le mettaient en défaut, chacun trouvé après le précédent, tous
constatés sur appareil le 2026-08-08 — ils sont notés ici parce qu'ils forment
un ensemble, et qu'un seul corrigé ne suffisait pas.

| Mécanisme | Ce qu'il produisait |
|---|---|
| Requête automatique au lancement | Mettait le disque et le réseau en course ; l'issue décidait de l'écran |
| Ordre du serveur ≠ ordre du cache | Le serveur trie par date de récupération, le cache par publication. Les pages sont désormais ramenées à l'ordre de publication (`DefaultArticleRepository.interleaved`) |
| Borne du cache appliquée **avant** le filtre des lus | Un cache dont les 200 plus récents étaient lus rendait une liste vide : l'écran le croyait vide et lançait le chargement de secours. 283 articles, 69 non lus, zéro affiché |
| Articles lus retirés du flux | L'ensemble à mélanger changeait à chaque session, donc l'ordre aussi |

Le principe qui les réunit : **le mélange doit porter sur un ensemble qui ne
bouge pas.** `interleaveBySource` choisit chaque position en regardant ses
voisins ; tout ce qui entre ou sort de l'ensemble redistribue le reste. Le
cache rend donc ses articles **lus compris**, et seul un rechargement demandé
renouvelle la liste.

Le rappel de lecture est la seule lecture du cache qui filtre encore les lus
(`unreadFromCache`) : il ne répond pas à la même question.

### 9.6 L'ancienneté du flux se mesure là où le serveur répond

La date qui sert à dire qu'un flux est ancien (SPECS.md §4.6) est écrite par
`DefaultArticleRepository`, dans sa branche de succès, à côté de l'écriture du
cache — et non par les ViewModels qui demandent les pages.

Deux raisons, et la seconde décide. La couche qui a parlé au serveur est la
seule à **savoir** qu'il a répondu : un `loadPage` réussi compte autant qu'un
rechargement explicite, et une page valide mais vide compte aussi. Surtout,
deux ViewModels demandent des pages : la règle écrite chez eux vivrait deux
fois, et les deux modes de présentation divergeraient au premier correctif
appliqué d'un seul côté.

Symétriquement, la **décision** — six heures, borne incluse, une horloge qui
recule ne rend rien ancien — est une fonction pure de `:domain`, à qui l'instant
courant est transmis. Ce qui reste à la présentation est ce qu'elle seule sait :
qu'elle est hors ligne, qu'elle rafraîchit déjà, qu'elle n'a rien à montrer.

### 9.2 Ce qui est hérité du template, délibérément

Le dépôt provient de `c4software/tailscale-auto-rules`, dont la logique métier a
été retirée.

`MainDispatcherRule` a trouvé son usage avec le premier ViewModel.
`UiStateCollector` a longtemps été la seule exception assumée à l'interdit « pas
de code mort » (AGENTS.md §2) : il sert aux ViewModels publiant en
`WhileSubscribed`, ce qu'aucun ne faisait alors. **L'exception est levée** — son
`keepCollecting` est employé par `SettingsViewModelTest`, dont l'état resterait
figé sur sa valeur initiale sans abonné.

Le dépôt n'a donc plus de dérogation à cet interdit, et n'en rouvrira une qu'en
l'inscrivant ici.
