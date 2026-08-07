# CONTRIBUTING.md — Contribuer

Ce document décrit la **procédure**. Les **règles** sont dans
[AGENTS.md](./AGENTS.md), et elles s'appliquent aux humains comme aux agents :
il n'y a pas deux niveaux d'exigence.

---

## 1. Avant d'écrire une ligne

Lire, dans cet ordre :

1. [AGENTS.md](./AGENTS.md) — la méthode de travail et les interdits
2. [SPECS.md](./SPECS.md) — ce que l'application doit faire
3. [ARCHITECTURE.md](./ARCHITECTURE.md) — comment elle est conçue
4. [TASKS.md](./TASKS.md) — où le travail s'est arrêté

Si votre contribution touche à l'API FreshRSS, lire **aussi**
[docs/freshrss-api.md](./docs/freshrss-api.md).

## 2. Préparer sa machine

```bash
export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
```

Ne **jamais** modifier le `PATH` pour y placer le JDK : voir AGENTS.md §5.

Les utilisateurs de Claude Code n'ont rien à exporter — ces variables sont dans
`.claude/settings.local.json`, non versionné car les chemins dépendent de la
machine.

## 3. Choisir sur quoi travailler

Toute contribution correspond à une **tâche de [TASKS.md](./TASKS.md)**.

- La tâche existe → la prendre, et la passer à `[-]`.
- Elle n'existe pas → l'ajouter d'abord, sous le Goal qui la porte. Si aucun
  Goal ne la porte, en ouvrir un.

Ce détour n'est pas de la bureaucratie : c'est ce qui permet à un contributeur
suivant — humain ou agent — de reprendre le travail sans vous.

## 4. Écrire

**Une tâche, un commit.** Le code et ses tests dans le même incrément.

Rappels qui coûtent cher à oublier :

- rien d'Android dans `:domain` — c'est une erreur de compilation, pas une
  remarque de revue ;
- aucun détail de l'API FreshRSS au-dessus de la couche `data` ;
- ne jamais deviner le comportement d'un point d'entrée : lire la source,
  constater, puis **mettre à jour `docs/freshrss-api.md`** ;
- pas de `TODO` sans tâche correspondante ;
- toute chaîne affichée est une ressource.

Formatage automatique :

```bash
./gradlew ktlintFormat
```

## 5. Vérifier

La commande de vérification et ses règles sont dans
[AGENTS.md §5](./AGENTS.md) — **elles n'ont qu'un seul endroit**, et ce n'est pas
ici. La recopier reviendrait à créer un second endroit à changer, et donc à
garantir qu'un des deux décrochera.

Ce que ce document ajoute, parce que c'est de la procédure et non de la règle :
lancez-la **avant chaque commit**, pas seulement avant la Pull Request. Un lot de
dix commits dont on découvre à la fin que le troisième casse la vérification est
un lot qu'il faut démonter.

## 6. Committer

[Conventional Commits](https://www.conventionalcommits.org/), avec la référence
de tâche :

```
feat(auth): implémenter ClientLogin contre l'API FreshRSS

Le jeton FreshRSS est déterministe et n'expire pas : il est donc conservé
entre deux lancements plutôt que redemandé à chaque démarrage.

Réf: GOAL-002-T05
```

Le corps explique **pourquoi**, jamais **quoi** — le diff dit déjà le quoi.

Ne jamais committer : `local.properties`, `.claude/settings.local.json`, un
keystore, une clé, un jeton.

## 7. Proposer

Une Pull Request par tâche, ou par Goal si les tâches sont indissociables.

Le gabarit ([`.github/pull_request_template.md`](.github/pull_request_template.md))
demande notamment de confirmer que la vérification est passée **et que sa sortie
a été constatée**. Ce n'est pas une formalité : une case cochée sans constat est
la seule chose que la CI ne peut pas rattraper.

`git push` n'est pas dans les autorisations partagées de `.claude/settings.json` :
une action sortante se confirme explicitement.

## 8. Mettre à jour la documentation

Elle fait partie de la contribution, pas de sa suite. Le tableau « quel
changement va dans quel fichier » est dans [AGENTS.md §6](./AGENTS.md), pour la
même raison qu'au point 5 : un seul endroit.

Une seule chose à retenir ici : [PROMPT.md](./PROMPT.md) est **figé**. Il
conserve l'intention initiale et ne se met jamais à jour, même quand elle a été
dépassée — les écarts sont consignés à la fin du fichier.

## 9. Si vous êtes bloqué

Le dire, dans `TASKS.md`, en passant la tâche à `[!]` **avec la raison écrite
juste en dessous**. Un blocage non écrit est un blocage perdu, et le prochain
contributeur le redécouvrira à ses frais.

Voir AGENTS.md §10 pour les cas courants.
