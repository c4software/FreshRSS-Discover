---
description: Compiler, tester, analyser et confronter TASKS.md à l'état réel
---

# /verify — vérification du projet

Cette commande **constate**. Elle ne corrige rien de sa propre initiative et ne
coche aucune case.

Trois niveaux de résultat :

| Niveau | Sens |
|---|---|
| **PASS** | Constaté conforme |
| **WARN** | Fonctionne, mais mérite attention |
| **FAIL** | Bloquant. Aucune tâche ne peut être déclarée terminée |

---

## 1 — Construction, tests, analyse statique

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

`JAVA_HOME` et `ANDROID_HOME` viennent de `.claude/settings.local.json`. Ne
jamais bricoler le `PATH` (AGENTS.md §5).

Reporter **la sortie réelle**. En cas d'échec, citer le message d'erreur, pas un
résumé — et surtout ne jamais annoncer un succès non observé.

Rappeler, tant que la dette `GOAL-001-T14` est ouverte :

> ⚠️ `koverVerify` passe sans rien mesurer tant que `:domain` n'a pas de code
> exécutable. Ce PASS ne vaut pas garantie de couverture.

## 2 — Rendu visuel

Uniquement **si l'interface a changé** depuis le dernier enregistrement — le
rendu graphique natif coûte plusieurs minutes.

```bash
./gradlew :app:verifyRoborazziDebug
```

- Échec → **FAIL**, en nommant les captures en écart.
- Non lancé → **WARN**, en disant pourquoi.

En cas de différence voulue, réenregistrer puis **regarder les images**. Ne pas
se contenter du succès de la tâche Gradle.

## 3 — Fichiers structurants

Présents et non vides : `SPECS.md`, `AGENTS.md`, `ARCHITECTURE.md`, `TASKS.md`,
`CONTRIBUTING.md`, `README.md`, `CLAUDE.md`, `docs/freshrss-api.md`,
`.claude/commands/{goal,task,status,verify}.md`.

## 4 — État Git

- `git status` — travail non committé
- Aucun secret ni chemin machine indexé : `local.properties`,
  `.claude/settings.local.json`, `*.jks`, `*.keystore`
- Les derniers messages de commit suivent AGENTS.md §7 et référencent une tâche

## 5 — Erreurs évidentes

- `TODO` ou `FIXME` sans tâche correspondante dans `TASKS.md` → **FAIL**
  (AGENTS.md §2)
- Import Android, Room, Ktor, Hilt ou Compose dans `:domain` → **FAIL**
- `Dispatchers.` hors de `DispatcherModule` → **FAIL**
- `System.currentTimeMillis()` hors de `TimeModule` → **FAIL**
- Chaîne affichée en dur dans un Composable → **WARN**
- Un jeton, mot de passe ou en-tête `Authorization` dans un appel de
  journalisation → **FAIL**

## 6 — Cohérence de TASKS.md

C'est le contrôle qui distingue cette commande d'un simple build.

Pour **chaque tâche `[x]`**, vérifier que ce qu'elle prétend avoir produit
existe réellement et est testé. Une tâche cochée dont le code est absent, ou
présent sans tests, est **FAIL** — et la case doit être décochée, pas ignorée.

Vérifier aussi :

- `ARCHITECTURE.md` §9 décrit bien le dépôt actuel → sinon **WARN**
- aucune fonctionnalité implémentée n'est restée `[ ]` → sinon **WARN**
- chaque tâche `[!]` porte sa raison écrite → sinon **WARN**

---

## Rapport

```
/verify — FreshRSS Discover

[PASS] Construction, tests, analyse statique
[WARN] Rendu visuel — non lancé, interface inchangée
[PASS] Fichiers structurants
[PASS] État Git
[FAIL] Erreurs évidentes — TODO sans tâche : FreshRssApi.kt:42
[PASS] Cohérence de TASKS.md

Résultat : FAIL

À corriger:
- FreshRssApi.kt:42 — ouvrir une tâche dans TASKS.md, ou retirer le TODO
```

Le résultat global est **FAIL** dès qu'un seul contrôle échoue.

Terminer par ce qu'il faut corriger, sans le corriger : la décision revient à
`/task` ou `/goal`.
