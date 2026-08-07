---
description: Travailler une tâche précise de TASKS.md, ou la prochaine pertinente
argument-hint: "[GOAL-00X-TYY]"
---

# /task — exécuter une tâche

Tâche demandée : **$ARGUMENTS**

```
Lire la tâche → Lire le contexte → Implémenter → Tester
             → Vérifier → Mettre à jour TASKS.md
```

---

## Étape 1 — Choisir la tâche

**Si un identifiant est fourni** (`GOAL-002-T03`) : c'est celle-là.

- Introuvable dans `TASKS.md` → le dire, montrer les tâches voisines du même
  Goal, et s'arrêter. Ne pas inventer de tâche.
- Déjà `[x]` → le signaler et **vérifier que c'est vrai** (le code existe, les
  tests passent). Si c'est faux, c'est une incohérence : AGENTS.md §8.
- `[!]` → relire la raison du blocage avant toute chose. Est-elle toujours
  valable ?

**Si aucun identifiant n'est fourni**, sélectionner :

1. une tâche `[-]` s'il en existe une — le travail en cours passe avant tout ;
2. sinon, la première tâche `[ ]` du Goal en cours ;
3. sinon, la première tâche du prochain Goal — mais si ce Goal n'est pas
   découpé, **ne pas improviser** : renvoyer vers `/goal`.

Annoncer la tâche retenue et pourquoi, avant de commencer.

---

## Étape 2 — Lire le contexte

- `AGENTS.md` — les règles s'appliquent à cette tâche aussi
- l'entrée du Goal dans `TASKS.md`, et les tâches qui la précèdent
- la section de `SPECS.md` que la tâche couvre
- la section de `ARCHITECTURE.md` concernée, **et §9** pour l'état réel
- `docs/freshrss-api.md` si la tâche touche à l'API — sa §6 en particulier
- les fichiers de code que la tâche modifie, et leurs tests

Puis **constater l'état réel du code**. Ne jamais supposer qu'une tâche `[-]`
est à moitié faite, ni qu'elle ne l'est pas : regarder.

---

## Étape 3 — Implémenter

1. Passer la tâche à `[-]` dans `TASKS.md`.
2. Énoncer le choix technique retenu, et pourquoi.
3. Implémenter **cette tâche uniquement**. Ce qui déborde devient une nouvelle
   tâche dans `TASKS.md`, pas un ajout silencieux.
4. Écrire les tests **dans le même incrément** que le code.

Rappels qui coûtent cher à oublier (AGENTS.md §2) :

- rien d'Android dans `:domain` ;
- aucun détail de l'API FreshRSS au-dessus de la couche `data` ;
- `Dispatchers` seulement dans `DispatcherModule`, `System.currentTimeMillis()`
  seulement dans `TimeModule` ;
- pas de `TODO` sans tâche correspondante ;
- toute chaîne affichée est une ressource.

---

## Étape 4 — Vérifier

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

Si l'interface a changé, **en plus** :

```bash
./gradlew :app:verifyRoborazziDebug
```

En cas de différence visuelle voulue : réenregistrer, puis **regarder les
images** avant de committer. Un `record` accepte en bloc, y compris une
régression.

**Constater la sortie réelle.** Ne jamais annoncer un succès non observé.

---

## Étape 5 — Clore

- Mettre à jour la documentation impactée (AGENTS.md §6), `ARCHITECTURE.md` §9
  comprise si la structure a changé.
- Passer la tâche à `[x]`.
- Committer, en référençant l'identifiant (AGENTS.md §7).
- Annoncer la tâche suivante, sans l'entreprendre.

Si la tâche n'aboutit pas : la laisser `[-]`, ou la passer à `[!]` **avec la
raison écrite juste en dessous** dans `TASKS.md`. Un blocage non écrit est un
blocage perdu.
