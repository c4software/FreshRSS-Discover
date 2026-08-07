# AGENTS.md — Règles de développement

Contrat de travail pour tout agent (Claude Code, Codex, …) ou développeur humain
intervenant sur ce dépôt. Il prévaut sur toute habitude personnelle.

Documents liés : [SPECS.md](./SPECS.md) (le quoi) ·
[ARCHITECTURE.md](./ARCHITECTURE.md) (le comment) · [TASKS.md](./TASKS.md)
(l'ordre) · [docs/freshrss-api.md](./docs/freshrss-api.md) (l'API distante) ·
[PROMPT.md](./PROMPT.md) (l'intention initiale, figée).

---

## 1. Méthode de travail

Le travail est organisé en **Goals**, eux-mêmes découpés en **tâches**. Tout est
consigné dans [TASKS.md](./TASKS.md), qui est la mémoire persistante du projet.

**Un commit par tâche, dans l'ordre. Les tâches s'enchaînent sans demander de
validation.**

Pour chaque tâche :

1. Énoncer brièvement le choix technique retenu, et pourquoi.
2. Passer la tâche à `[-]` dans TASKS.md.
3. Implémenter **cette tâche uniquement**.
4. Écrire les tests dans le même incrément que le code.
5. Lancer la vérification complète (§5) et **rapporter la sortie réelle**.
6. Mettre à jour la documentation impactée (§6).
7. Passer la tâche à `[x]`, committer, puis passer à la suivante.

Si une tâche se révèle plus grosse que prévu, la découper en plusieurs commits —
mais ne jamais fusionner deux tâches en un seul.

La granularité est **le commit, pas la conversation** : c'est lui qui rend le
travail relisible et réversible étape par étape. C'est ce qui rend l'avance
autonome sûre.

### 1.1 La règle fondamentale

Ne jamais considérer que :

```
code écrit = tâche terminée
```

La règle est :

```
code écrit → tests → vérification → documentation → TASKS.md = [x]
```

Une tâche dont la vérification échoue **n'est pas** `DONE`. Une tâche déclarée
`DONE` sans que la sortie de la vérification ait été constatée est un mensonge,
et le prochain agent le paiera.

### 1.2 Quand s'arrêter quand même

L'enchaînement automatique ne dispense pas de savoir s'interrompre. Quatre cas,
et seulement ceux-là :

- **La vérification (§5) échoue et la corriger demande un arbitrage** — abaisser
  une version, relâcher une règle de qualité, renoncer à un test.
- **La spécification est ambiguë sur une règle métier.** Ne jamais trancher en
  silence sur un comportement visible par l'utilisateur.
- **Une action sortante ou difficilement réversible** : `git push`, publication,
  réécriture d'historique, suppression de données.
- **Un choix structurant s'impose** qui contredirait
  [ARCHITECTURE.md](./ARCHITECTURE.md) ou [SPECS.md](./SPECS.md).

Hors de ces cas : décider, documenter la décision dans le message de commit, et
continuer.

### 1.3 Reprise après interruption

Un agent peut être arrêté à tout moment. Au redémarrage :

1. lire ce fichier ;
2. lire [TASKS.md](./TASKS.md) ;
3. identifier les tâches `[-]` (IN PROGRESS) ;
4. **vérifier l'état réel du code**, ne jamais supposer qu'une tâche `[-]` est
   terminée ;
5. lancer la vérification (§5) pour constater où en est le dépôt ;
6. reprendre la tâche.

La commande `/status` produit cette lecture automatiquement.

---

## 2. Interdits

- ❌ Livrer du code qui ne compile pas, ou une fonctionnalité sans ses tests.
- ❌ Déclarer une tâche terminée sans avoir constaté la sortie de la
  vérification.
- ❌ Laisser du code mort, une classe inutilisée, un paramètre ignoré.
  (Une exception, unique et documentée, est inscrite dans ARCHITECTURE.md §9.2.)
- ❌ Écrire un `TODO` sans tâche correspondante dans [TASKS.md](./TASKS.md).
- ❌ Utiliser une API Android dépréciée.
- ❌ Importer `android.*`, `androidx.*`, Room, DataStore, Ktor, Hilt ou Compose
  depuis `:domain`.
- ❌ Mettre de la logique métier dans un ViewModel ou un Composable.
- ❌ Laisser un détail de l'API FreshRSS (jeton, `continuation`, en-tête,
  identifiant hexadécimal) franchir la couche `data` — voir ARCHITECTURE.md §2.1.
- ❌ Créer un singleton métier (`object` porteur d'état) — la portée se déclare
  à Hilt.
- ❌ Appeler `System.currentTimeMillis()` ailleurs que dans l'implémentation de
  `Clock`.
- ❌ Référencer `kotlinx.coroutines.Dispatchers` ailleurs que dans
  `DispatcherModule`.
- ❌ Introduire une dépendance sans justification écrite dans le message de
  commit.
- ❌ Anticiper : ne pas créer de structure « pour plus tard ». Une abstraction
  arrive avec son deuxième cas d'usage, pas avant.

En cas de choix entre plusieurs solutions, l'ordre de préférence est :
**simplicité → lisibilité → testabilité → maintenabilité → API Android
officielle**.

---

## 3. L'API FreshRSS

Une règle, et elle n'a pas d'exception :

> **Ne jamais inventer le comportement d'un point d'entrée.**

Avant toute décision touchant à l'authentification, la pagination, la
récupération des articles, le statut lu, le marquage ou la gestion des erreurs :

1. lire [docs/freshrss-api.md](./docs/freshrss-api.md) ;
2. si le point n'y figure pas, ou y figure comme incertain (§6 de ce document),
   **lire la source** —
   [`p/api/greader.php`](https://github.com/FreshRSS/FreshRSS/blob/edge/p/api/greader.php)
   fait foi sur les paramètres et la forme des réponses, la
   [documentation officielle](https://freshrss.github.io/FreshRSS/fr/users/06_Mobile_access.html)
   sur l'usage attendu ;
3. **mettre à jour `docs/freshrss-api.md`** avec ce qui a été constaté.

Ne **jamais** déduire le comportement de l'API à partir d'une implémentation
existante dans ce dépôt : ce serait figer une erreur.

Un point qui reste incertain après lecture est **signalé comme tel**, pas
supposé. Il rejoint la §6 de `docs/freshrss-api.md` et une tâche de TASKS.md.

---

## 4. Tests

- **Aucune fonctionnalité sans tests, dans le même commit.**
- Un test par comportement, nommé d'après le comportement observable.
- Le domaine se teste en JVM pur : ni Robolectric, ni émulateur, ni Android.
- Les doubles sont des **Fakes** versionnés, pas des mocks générés.
- Le temps et les dispatchers sont injectés ; les tests utilisent
  `kotlinx-coroutines-test` et son ordonnanceur virtuel. Jamais de
  `Thread.sleep`.
- La couche API se teste avec le `MockEngine` de Ktor, sur des réponses HTTP
  **littérales** — y compris malformées, tronquées, ou en texte brut là où du
  JSON était attendu.
- Couverture visée : **~100 % sur `:domain`**.

### 4.1 Rendu visuel (Roborazzi)

Les tests d'interface vérifient **ce qui est affiché** ; les captures vérifient
**à quoi cela ressemble**. Une régression de mise en page, de contraste ou de
thème sombre ne casse aucune assertion textuelle.

- Les références vivent dans `app/src/test/screenshots/`, **versionnées** : une
  revue doit voir le changement visuel dans le diff, pas seulement lire qu'un
  test a échoué.
- Chaque écran est capturé en clair **et** en sombre. Le thème sombre n'est
  jamais celui qu'on regarde en développant : c'est là que les défauts de
  contraste s'installent sans être vus. Ce n'est pas théorique — la Phase 0 a
  livré un titre noir sur fond noir, invisible autrement.
- La couleur dynamique est désactivée et le format d'écran figé
  (`@Config(qualifiers)`) : sans cela, la référence dépendrait du fond d'écran
  ou de la configuration par défaut de Robolectric.

```bash
./gradlew :app:verifyRoborazziDebug   # comparer aux références
./gradlew :app:recordRoborazziDebug   # réenregistrer après un changement voulu
```

⚠️ **Ces commandes ne sont pas dans la vérification de §5, ni dans la CI.** Le
rendu graphique natif coûte plusieurs minutes de temps machine ; le payer à
chaque commit et à chaque Pull Request ne se justifie pas.

**Conséquence, à assumer :** une régression visuelle n'est rattrapée par
personne automatiquement. **Quiconque touche à l'interface lance
`verifyRoborazziDebug` avant de committer** — c'est le seul filet.

**Réenregistrer n'est pas anodin** : un `record` accepte en bloc toute
différence, y compris une régression. Ne le lancer qu'après avoir constaté que
le changement visuel est celui qu'on voulait, et **regarder les images**
produites avant de committer.

Un agent qui touche à l'interface **regarde réellement les captures** — il en a
les moyens — plutôt que de constater seulement qu'une tâche Gradle a réussi.

---

## 5. Vérification

Ce dépôt se construit avec le JDK embarqué d'Android Studio. `gradlew` lit
`JAVA_HOME` en priorité et ne consulte le `PATH` que si elle est absente :
**définir `JAVA_HOME` suffit, ne jamais bricoler le `PATH`.**

- **Agents Claude Code** — rien à faire : `JAVA_HOME` et `ANDROID_HOME` sont
  déclarées dans `.claude/settings.local.json` (non versionné, car les chemins
  dépendent de la machine). Lancer `./gradlew …` directement.
- **En shell interactif** :

  ```bash
  export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
  export ANDROID_HOME=$HOME/Android/Sdk
  ```

⚠️ Ne **jamais** préfixer une commande d'un `export PATH="$JAVA_HOME/bin:$PATH"`.
La valeur n'étant résolue qu'à l'exécution, la commande devient impossible à
rapprocher d'une règle d'autorisation : le harness redemande confirmation à
chaque appel, et aucune règle réutilisable ne peut être enregistrée.

### 5.1 Écrire des commandes qui ne redemandent pas confirmation

Une commande n'est mémorisable dans une règle d'autorisation que si sa forme se
répète. Quatre habitudes suffisent à éviter l'essentiel des demandes :

| À faire | Plutôt que |
|---|---|
| Écrire les fichiers avec les outils **Write** et **Edit** | `cat > fichier <<'EOF'`, `sed -i '…'`, `python3 - <<'PY'` |
| `git commit -m "…"` (l'identité est dans `.git/config`) | `git -c user.email=… -c user.name=… commit …` |
| Un motif `grep` stable, ou lire la sortie complète | un `grep -E "…"` différent à chaque appel |
| Une commande de vérification unique (ci-dessous) | des variantes de tâches Gradle au coup par coup |

Les règles partagées vivent dans `.claude/settings.json`, versionné et sans
chemin machine. `git push` en est **volontairement absent** : une action
sortante se confirme.

### 5.2 La commande

À passer **avant tout commit** :

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

C'est exactement ce que fait `/verify`.

`koverVerify` échoue sous 98 % de couverture sur `:domain`.

Correction automatique du formatage :

```bash
./gradlew ktlintFormat
```

⚠️ **`ktlintFormat` ne corrige que `:domain`.** Le greffon ktlint-gradle ne
découvre pas les jeux de sources Android d'AGP 9 : sur `:app`, il n'enregistre
de tâche que pour les fichiers `.kts`. Les règles de style y sont appliquées par
**`detekt-formatting`**, qui les signale sans les corriger.

Conséquence pratique : dans `:app`, une violation de style se répare à la main.
La plus fréquente est l'ordre des imports — lexicographique, avec `java`,
`javax`, `kotlin` et les alias en fin. Voir ARCHITECTURE.md §8.0 pour l'histoire
de ce garde-fou, qui est resté vide pendant plusieurs Goals.

Rien n'est déclaré terminé sans que cette commande soit passée **et sa sortie
réellement constatée**. En cas d'échec, rapporter la sortie ; ne jamais annoncer
un succès non observé.

### 5.3 Définition de « terminé »

- [ ] `./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug` passe.
- [ ] Si l'interface a changé : `./gradlew :app:verifyRoborazziDebug` passe, ou
      les références ont été réenregistrées **et regardées**.
- [ ] Les tests couvrent le comportement ajouté, y compris ses cas limites.
- [ ] Aucun code mort, aucun `TODO` orphelin.
- [ ] [ARCHITECTURE.md](./ARCHITECTURE.md) §9 reflète l'état réel du dépôt.
- [ ] La case correspondante de [TASKS.md](./TASKS.md) est cochée.
- [ ] Le commit suit §7.

---

## 6. Documentation

La documentation fait partie de la tâche, pas de sa suite.

| Changement | Fichier à mettre à jour |
|---|---|
| Nouveau comportement visible par l'utilisateur | [SPECS.md](./SPECS.md) |
| Décision d'architecture, dépendance, découpage | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| Structure réelle du dépôt | [ARCHITECTURE.md](./ARCHITECTURE.md) §9 |
| Constat sur l'API FreshRSS | [docs/freshrss-api.md](./docs/freshrss-api.md) |
| Nouvelle règle de développement | ce fichier |
| Procédure de contribution | [CONTRIBUTING.md](./CONTRIBUTING.md) |
| Avancement, nouvelle tâche, blocage | [TASKS.md](./TASKS.md) |

[PROMPT.md](./PROMPT.md) est **figé** : il conserve l'intention initiale et ne se
met pas à jour. Lorsqu'une règle applicable l'a remplacé sur un point, c'est ce
fichier-ci qui fait foi.

---

## 7. Git

**Un commit = une seule tâche cohérente.** Format
[Conventional Commits](https://www.conventionalcommits.org/) :

```
<type>(<portée>): <description à l'impératif, en minuscule>

<corps facultatif : pourquoi, pas quoi>

Réf: GOAL-00X-TYY
```

Types : `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`.

Portées usuelles : `domain`, `data`, `api`, `auth`, `feed`, `discover`, `cache`,
`ui`, `di`, `settings`, `gradle`, `harness`.

```
feat(auth): implémenter ClientLogin contre l'API FreshRSS
test(api): couvrir la pagination par curseur, curseur invalide compris
docs(architecture): décrire la confinement des détails FreshRSS
```

Référencer l'identifiant de tâche (`GOAL-002-T03`) dans le pied du message :
c'est ce qui relie l'historique Git à TASKS.md.

Ne jamais committer : `local.properties`, `.claude/settings.local.json`, un
keystore, une clé, un jeton, une capture de build.

---

## 8. Détection des incohérences

Le dépôt peut se retrouver dans un état contradictoire. Trois formes courantes :

| Symptôme | Source de vérité |
|---|---|
| TASKS.md dit `[x]`, le code ne compile pas | **Le code.** Repasser la tâche à `[-]` et corriger |
| TASKS.md dit `[ ]`, la fonctionnalité existe | **Le code.** Cocher, après avoir vérifié qu'elle est testée |
| ARCHITECTURE.md décrit A, le code fait B | **ARCHITECTURE.md**, sauf si B est meilleur — auquel cas mettre à jour le document et le dire |
| SPECS.md décrit un comportement absent | **SPECS.md.** C'est une tâche manquante |
| `docs/freshrss-api.md` contredit le serveur réel | **Le serveur.** Corriger le relevé |

Dans tous les cas :

1. identifier l'incohérence ;
2. **ne pas la masquer** ;
3. corriger le côté qui a tort ;
4. signaler la décision dans le rapport et dans le message de commit.

---

## 9. Conventions de code

Appliquées par ktlint, Detekt et `.editorconfig`.

### Mise en forme

- Indentation 4 espaces (2 pour XML, YAML, TOML, JSON).
- Ligne à 120 colonnes maximum.
- **Trailing commas systématiques** sur les listes multi-lignes.
- Imports explicites, jamais d'étoile.

### Nommage

| Élément | Convention | Exemple |
|---|---|---|
| Fichier Kotlin | Nom de la déclaration principale | `DiscoverViewModel.kt` |
| Classe, interface, enum | `PascalCase` | `FreshRssApi` |
| Fonction, propriété | `camelCase` | `loadNextPage`, `isRead` |
| `@Composable` | `PascalCase` | `DiscoverScreen` |
| Constante de fichier | `private val PascalCase` en tête de fichier | `private val CardHeight = 96.dp` |
| Test | `camelCase` descriptif, sans backticks | `anAbsentContinuationEndsTheFeed` |
| Fake | Préfixe `Fake` | `FakeArticleRepository` |

### Documentation du code

- KDoc **en français**, sur ce qui n'est pas évident : un choix, une contrainte,
  une raison. Pas de paraphrase de la signature.
- Un commentaire explique **pourquoi**, jamais **quoi**.

Exemple du style attendu :

```kotlin
/**
 * L'absence de `continuation` est le seul signal de fin de flux : l'API ne
 * renvoie aucun compteur total. Un curseur invalide, lui, est silencieusement
 * ramené au début côté serveur — d'où la vérification explicite.
 */
```

### Compose

- Un Composable public prend `modifier: Modifier = Modifier` en **premier
  paramètre optionnel**, après les paramètres obligatoires.
- Aucun calcul dans un Composable : il affiche `UiState`, il ne le dérive pas.
- Chaque écran a une `@Preview` privée qui fonctionne **sans injection**.
- Les dimensions récurrentes passent par `Spacing`, pas par des `.dp` épars.
- Toute chaîne affichée est une ressource, jamais un littéral.

---

## 10. Ce qu'il faut faire quand on est bloqué

- **Le SDK Android manque une plateforme** → l'installer via le SDK Manager
  d'Android Studio ; ne pas contourner en abaissant silencieusement une version.
- **Une dépendance impose un `compileSdk` supérieur** → le signaler et proposer
  le choix, plutôt que de rétrograder la dépendance sans le dire.
- **La spécification est ambiguë** → poser la question. Ne pas trancher en
  silence sur une règle métier.
- **Le comportement de l'API FreshRSS est incertain** → §3. Lire la source,
  constater, documenter. Ne jamais supposer.
- **Une abstraction résiste** → le dire. Contourner une abstraction est une
  dette ; la corriger est une étape.
