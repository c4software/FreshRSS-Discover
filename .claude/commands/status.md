---
description: Vue synthétique de l'avancement, dérivée de TASKS.md et du dépôt réel
---

# /status — où en est le projet

Produire une vue synthétique **dérivée de deux sources**, jamais d'une seule :

1. `TASKS.md` — ce que le projet **déclare** ;
2. le dépôt réel — ce qui **est**.

Un écart entre les deux n'est pas un détail de présentation : c'est le résultat
le plus utile de cette commande.

---

## Étape 1 — Lire l'état déclaré

Dans `TASKS.md` : la phase courante, la table de vue d'ensemble, l'état de
chaque tâche, les points bloqués.

## Étape 2 — Constater l'état réel

- `git status` et `git log --oneline -10` — travail non committé, derniers pas
- `ARCHITECTURE.md` §9 — la structure décrite correspond-elle au dépôt ?
- l'existence des fichiers que les tâches `[x]` prétendent avoir produits

Ne **pas** lancer la vérification complète : c'est le rôle de `/verify`, et
`/status` doit rester instantané. Si l'état de la construction importe, le dire
et renvoyer vers `/verify`.

## Étape 3 — Rapprocher

Chercher les trois incohérences d'`AGENTS.md` §8 :

| Symptôme | À signaler comme |
|---|---|
| Tâche `[x]`, fichiers absents | ⚠️ déclarée terminée mais introuvable |
| Tâche `[ ]`, fonctionnalité présente | ⚠️ faite mais non cochée |
| `ARCHITECTURE.md` §9 ≠ dépôt | ⚠️ documentation en retard |

Les signaler, **sans les corriger** : `/status` observe, il ne modifie rien.

---

## Étape 4 — Afficher

```
FreshRSS Discover

Phase courante:
Phase 1 — API FreshRSS

Goals:
[x] GOAL-001 Harness et initialisation
[-] GOAL-002 Authentification FreshRSS
[ ] GOAL-003 Récupération paginée des articles
[ ] GOAL-004 Cache local et résilience réseau
...

Tâche en cours:
GOAL-002-T05 — FreshRssApi.clientLogin()

Avancement du Goal:
████░░░░░░ 4/17

Bloqué:
Aucun

Incohérences:
Aucune

Travail non committé:
2 fichiers modifiés

Suivant:
GOAL-002-T06 — Traduction des codes HTTP en erreurs de domaine
```

Règles d'affichage :

- la barre d'avancement porte sur le **Goal en cours**, pas sur le projet : un
  pourcentage global n'aurait aucun sens, les Goals suivants n'étant pas encore
  découpés ;
- « Bloqué » liste les tâches `[!]` **avec leur raison** ;
- « Incohérences » n'est jamais omis : écrire « Aucune » plutôt que rien ;
- si aucune tâche n'est `[-]`, l'indiquer et nommer la prochaine `[ ]`.

Terminer par une phrase, pas davantage : ce qu'il est logique de faire ensuite.
