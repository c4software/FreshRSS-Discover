# GOAL-014 — Toast actionnable quand le flux affiché est ancien

## Contexte

Le flux Discover ne se synchronise jamais tout seul : SPECS.md §2 exclut la
synchronisation en arrière-plan, et le seul rafraîchissement est celui que
l'utilisateur demande (§4.6, tirer-pour-rafraîchir ou bouton de la barre). Au
lancement, le cache s'affiche immédiatement (§5.1) — y compris quand la dernière
réponse du serveur date de la veille. Rien, aujourd'hui, ne le dit : l'écran
d'un flux vieux de dix heures est indiscernable de celui d'un flux frais.

Ce Goal ajoute une bandelette actionnable qui, au-delà de **6 h** sans réponse
du serveur, invite à rafraîchir. Elle est **acquittée à la main** — c'est déjà
le choix assumé du dépôt pour `OfflineOpenNotice` (« un message qui s'efface
tout seul se rate ») — et visible dans les **deux modes**, Liste et Balayage.

Rien de tel n'existe : aucun champ `lastSync`/`fetchedAt` dans le dépôt, aucun
hôte de snackbar centralisé, et deux `Snackbar` déjà dupliqués entre
`DiscoverScreen.kt` et `SwipeScreen.kt`.

## Décisions tranchées avant d'écrire

| Point | Décision | Raison |
|---|---|---|
| Seuil | **6 h** | Choix de l'auteur. À inscrire dans SPECS.md §8 « Tranchées » |
| Qui décide | Fonction pure de `:domain`, `feed/FeedFreshness.kt` | Précédent `reminderPlanFor` (GOAL-013) : ni horloge, ni chaîne, ni Android. Pas de nouveau paquet |
| Qui horodate | **`DefaultArticleRepository`**, sur toute réponse serveur valide (`loadPage` comprise) | Deux ViewModels appellent `refresh()` ; horodater côté VM dupliquerait la règle et laisserait les deux modes diverger |
| Support | DataStore, clé `feed.last_refresh_at` | « Room porte les collections, DataStore les scalaires » (ARCHITECTURE.md §5.1) |
| Hors ligne | Bandelette **supprimée** tant que `isOffline` | Le bandeau hors ligne dit déjà pourquoi le flux est ancien ; proposer « Rafraîchir » là où l'appel échouera est une fausse porte, et empilerait deux bandelettes au même `BottomCenter` |
| Acquittement | **En mémoire**, partagé entre les deux modes (`FeedFreshnessStore` en `@Singleton`), clé = l'horodatage acquitté | Le mettre dans le VM ferait revenir la bandelette à chaque bascule Liste↔Balayage. Comparer les horodatages fait revenir l'avis après un refresh réussi + 6 h, sans horloge supplémentaire |
| Actions | « **Rafraîchir** » + fermeture explicite | `Snackbar` Material 3 a un `dismissAction` distinct de `action`. Une seule action forcerait l'utilisateur hors d'état de rafraîchir à subir le message |

## Découpage

### `GOAL-014-T01` — Le domaine décide de l'ancienneté

Créer `domain/src/main/kotlin/.../domain/feed/FeedFreshness.kt` :

```kotlin
const val STALE_FEED_THRESHOLD_MILLIS: Long = 6 * 60 * 60 * 1_000L

data class FeedFreshness(
    val lastRefreshEpochMillis: Long? = null,
    val acknowledgedRefreshEpochMillis: Long? = null,
) {
    fun isStale(nowEpochMillis: Long): Boolean
    fun showsStaleNotice(nowEpochMillis: Long): Boolean
}

interface FeedFreshnessRepository {
    fun observeFreshness(): Flow<FeedFreshness>
    suspend fun recordRefresh()
    suspend fun acknowledgeStale()
}
```

Règles à figer dans le KDoc autant que dans le code :
- `lastRefreshEpochMillis == null` → **jamais ancien** (aucun point de référence,
  et au premier lancement une requête est en vol) ;
- `now - last >= SEUIL`, borne incluse ;
- `now < last` (horloge reculée, restauration de sauvegarde) → **pas ancien**, par
  la seule arithmétique. Ne pas ajouter de règle « futur = ancien » : elle ferait
  surgir la bandelette juste après un refresh réussi lors d'un ajustement d'heure ;
- acquitté ⇔ `acknowledgedRefreshEpochMillis == lastRefreshEpochMillis`.

Fake pour `:app` : `domain/src/testFixtures/.../feed/FakeFeedFreshnessRepository.kt`,
sur le modèle des fakes existants.

Tests (JUnit 5, `:domain` à 98 %) : jamais rafraîchi ; 5 h 59 ; exactement 6 h ;
6 h 01 ; horloge reculée ; horodatage dans le futur ; acquitté sur le même
horodatage à 12 h ; acquitté puis nouvel horodatage récent ; acquitté puis
nouvel horodatage puis 6 h → **avis de nouveau** ; acquittement à `null` invalidé
par le premier refresh ; instant antérieur à l'époque.

### `GOAL-014-T02` — L'horodatage est persisté

Créer `app/.../data/local/FeedFreshnessStore.kt`, `@Singleton`, implémentant
`FeedFreshnessRepository` : `longPreferencesKey("feed.last_refresh_at")` dans le
DataStore existant, `Clock` injecté pour `recordRefresh()`, et un
`MutableStateFlow<Long?>` interne — **non persisté** — pour l'acquittement,
combiné au flux DataStore. `@Singleton` ici (contrairement à `ReminderTimeStore`)
parce que c'est l'acquittement en mémoire qui doit survivre à la bascule de mode.

Liaison Hilt dans `di/RepositoryModule.kt`. Modèle de test :
`ReminderTimeStoreTest` (Robolectric + DataStore temporaire).

Tests : store vide ; `recordRefresh` écrit l'instant de l'horloge ; deuxième
écriture écrase ; `acknowledgeStale` porte l'horodatage courant ; acquittement
sur store vide ; `recordRefresh` après acquittement rouvre la possibilité de
l'avis ; le flux réémet à chaque écriture.

### `GOAL-014-T03` — Le dépôt enregistre chaque contact serveur réussi

Modifier `data/repository/DefaultArticleRepository.kt` : nouvelle dépendance
`FeedFreshnessRepository`, appel à `recordRefresh()` dans la branche
`ApiOutcome.Success` de `toFeedResult` (l.146-150), au même endroit que
`cache.save`. Mettre à jour `DefaultArticleRepositoryTest` dans le même incrément.

Tests : `loadPage` réussi → un enregistrement ; `refresh` réussi → un
enregistrement ; `HttpError 401` / `TransportError` / `MalformedResponse` /
session absente → aucun ; page **vide mais valide** → enregistrement quand même
(le serveur a répondu, c'est le flux qui n'a rien de neuf).

### `GOAL-014-T04` — Le mode Liste porte l'avis

`presentation/discover/DiscoverUiState.kt` gagne `isStaleNoticeAvailable`, et la
dérivée `showsStaleNotice` sur le modèle de `showsOfflineBanner` (l.59-60) :

```kotlin
val showsStaleNotice: Boolean
    get() = isStaleNoticeAvailable && !isOffline && !isRefreshing && articles.isNotEmpty()
```

`DiscoverViewModel` : `observeFreshness()` collecté dans `viewModelScope`, et
`fun dismissStaleNotice()` → `acknowledgeStale()`.

**Point délicat.** Le seuil s'atteint sans qu'aucun événement ne se produise
(application ouverte, écran éteint). Retenu : un **ticker** dans le VM,
`STALE_CHECK_PERIOD_MILLIS = 5 * 60_000`, **arrêté dès que l'avis est visible**
(plus rien à calculer avant un acquittement ou un refresh). 5 min de granularité
sont invisibles sur un seuil de 6 h, et le coût est dérisoire face à
`sampleVisibility`, qui échantillonne à 200 ms. À reconstater dès cette tâche :
`MainDispatcherRule` fournit un `UnconfinedTestDispatcher` hors `runTest`, la
boucle suspend au premier `delay` et ne bloque pas les tests existants.

Tests : 7 h + articles → visible ; 5 h → invisible ; hors ligne → invisible ;
`isRefreshing` → invisible ; liste vide → invisible ; `dismissStaleNotice()`
masque **et** appelle `acknowledgeStale` une fois ; une nouvelle émission ne le
fait pas revenir ; acquitté → refresh réussi → 6 h → visible de nouveau ;
l'action emprunte exactement `refresh()` ; refresh en échec → `isOffline` →
masqué ; avancer l'horloge de 6 h **sans aucun événement** le fait apparaître
(`MainDispatcherRule(StandardTestDispatcher())` + `advanceTimeBy`).

### `GOAL-014-T05` — Le mode Balayage porte le même avis

Symétrique de T04 sur `SwipeUiState.kt` / `SwipeViewModel.kt`. Un test de plus,
qui justifie l'acquittement partagé : acquitter côté Liste, construire le
`SwipeViewModel` sur le **même** `FakeFeedFreshnessRepository`, l'avis reste muet.

### `GOAL-014-T06` — Factoriser la bandelette (refactor pur)

Créer `app/.../presentation/feed/FeedNotice.kt` — `feed/` est déjà, par
ARCHITECTURE.md §9, « ce que les deux modes partagent » :

```kotlin
@Composable
fun FeedNotice(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissModifier: Modifier = Modifier,
)
```

Absorbe les trois duplications réelles de `OfflineOpenNotice`
(`DiscoverScreen.kt` l.324-344, `SwipeScreen.kt` l.603-620) : `MinTouchTarget`,
`SnackbarDefaults.actionContentColor`, `padding(Spacing.md)`. Les `testTag` restent
**par écran** (`discover:offline-notice-dismiss`, `swipe:…`) et passent par
`actionModifier`/`dismissModifier` — les tests d'écran existants les assertent.

Critère de la tâche : aucun changement de comportement, les tests d'écran
existants passent **inchangés**. Nouveaux tests du composant : message affiché,
action déclenchée, fermeture optionnelle absente par défaut, cible tactile ≥ 48 dp.

### `GOAL-014-T07` — L'avis s'affiche en mode Liste

`DiscoverScreen.kt` (nouveaux paramètres `onStaleNoticeRefresh` /
`onStaleNoticeDismiss`, branchés dans `AppNavHost.DiscoverRoute`),
`DiscoverTestTags.kt`, et des chaînes **partagées** dans `strings_feed.xml` à
côté de `feed_refresh` : `feed_stale_notice`, `feed_stale_refresh`,
`feed_stale_dismiss`.

Tests : visible / invisible selon `showsStaleNotice` ; appui sur « Rafraîchir »
déclenche le rappel une fois ; appui sur la fermeture acquitte ; hors ligne +
ancien → seul le bandeau hors ligne ; jamais deux bandelettes simultanées ;
l'avis ne s'efface pas seul quand l'horloge de test avance.

### `GOAL-014-T08` — L'avis s'affiche en mode Balayage

Idem sur `SwipeScreen.kt`, `SwipeTestTags.kt`, `AppNavHost.SwipeRoute`, mêmes
chaînes. Un test de plus : la bandelette ne masque pas le bouton d'ouverture de
l'article.

### `GOAL-014-T09` — Captures Roborazzi

`DiscoverFeedScreenshotTest.kt` et `SwipeScreenshotTest.kt` : quatre références
`app/src/test/screenshots/{discover,balayage}-flux-ancien-{clair,sombre}.png`.
Deux situations et pas une — la bandelette posée sur une **carte** et sur une
**illustration plein écran** ne se jugent pas au même endroit.
`./gradlew :app:recordRoborazziDebug`, puis `:app:verifyRoborazziDebug`, captures
**regardées**.

### `GOAL-014-T10` — Documentation

- **SPECS.md** §4.6 : le seuil de 6 h, la formulation, l'acquittement manuel, la
  suppression hors ligne. §8 « Tranchées » : nouvelle ligne (question n° 9) pour
  le seuil et ce qui l'a décidé.
- **ARCHITECTURE.md** §5.1 (le DataStore gagne « date du dernier contact
  serveur » ; dire que l'acquittement n'est **pas** persisté, et pourquoi) et §9
  (`FeedFreshness`, `FeedFreshnessRepository`, `FeedNotice` — « l'ancienneté du
  flux se mesure là où le serveur répond, pas là où on l'affiche »).
- **TASKS.md** : Goal, tâches cochées, table de décisions, dettes éventuelles
  **comme tâches**. Au passage, la table « Vue d'ensemble » (l.46-58) et la
  section « Phase courante » (l.29-40) sont périmées — elles s'arrêtent à
  GOAL-011 et donnent GOAL-004..007 pour `[-]` alors que leurs tâches sont
  toutes `[x]`. Les remettre à jour ici plutôt que d'y ajouter une ligne fausse
  de plus.

## Vérification

Après **chaque** tâche, avant son commit :

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

À partir de T07, et à chaque tâche touchant l'interface :

```bash
./gradlew :app:verifyRoborazziDebug        # recordRoborazziDebug si les références changent
```

Sortie **constatée** à chaque fois ; une tâche dont la vérification échoue reste
`[-]`, ou passe à `[!]` avec sa raison écrite. Un commit par tâche, Conventional
Commits, portée `feed`, pied `Réf: GOAL-014-TYY`.

Contrôle manuel en fin de Goal, sur appareil : forcer `feed.last_refresh_at` à
J-1, ouvrir l'application, vérifier que la bandelette paraît dans les deux modes,
que « Rafraîchir » recharge et la fait disparaître, que la fermeture la fait
taire, qu'elle ne revient pas en basculant de mode, et qu'en mode avion c'est le
bandeau hors ligne qui s'affiche seul.

## Risques repérés

1. **Le ticker et les tests de VM** — à reconstater dès T04 avant d'écrire T05.
2. **`AppGraphTest`** construit le graphe réel : la liaison Hilt de T02 y casse
   en premier si elle est oubliée.
3. **`DefaultArticleRepository` gagne une dépendance** (T03) : tous ses
   constructeurs de test à mettre à jour dans le même incrément.
4. **Couverture `:domain` à 98 %** : `FeedFreshness` est une `data class`, un
   `toString` jamais appelé peut faire tomber le seuil.
