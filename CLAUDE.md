# CLAUDE.md

Les règles de développement de ce dépôt sont consignées dans un fichier unique,
partagé par tous les agents et par les contributeurs humains :

👉 **[AGENTS.md](./AGENTS.md)** — méthode de travail, interdits, tests,
commande de vérification, conventions de code et de commit.

Lire également, dans cet ordre :

1. [SPECS.md](./SPECS.md) — la spécification fonctionnelle (le **quoi**)
2. [ARCHITECTURE.md](./ARCHITECTURE.md) — l'architecture technique (le **comment**)
3. [TASKS.md](./TASKS.md) — la feuille de route et l'avancement réel (l'**ordre**)

Si le travail touche à l'API FreshRSS — authentification, pagination, articles,
statut lu, marquage, erreurs — lire **aussi**
[docs/freshrss-api.md](./docs/freshrss-api.md), et sa §6 en particulier :
**ne jamais inventer le comportement d'un point d'entrée** (AGENTS.md §3).

## Commandes du Harness

| Commande | Rôle |
|---|---|
| `/status` | Où en est le projet, et ce qui cloche |
| `/goal <objectif>` | Décomposer un objectif en tâches, puis les exécuter |
| `/task [GOAL-00X-TYY]` | Exécuter une tâche précise, ou la prochaine |
| `/verify` | Compiler, tester, et confronter TASKS.md à la réalité |

En arrivant sur le dépôt, commencer par `/status`.

## Points de vigilance

**Une tâche de `TASKS.md` à la fois**, tests inclus, vérification passée **et sa
sortie constatée**, puis commit — avant de poursuivre.

`code écrit ≠ tâche terminée` :

```
code écrit → tests → vérification → documentation → TASKS.md = [x]
```

Ne jamais annoncer un succès non observé. Ne jamais supposer qu'une tâche `[-]`
est terminée : le vérifier.
