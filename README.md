# FreshRSS Discover

Client Android pour un serveur [FreshRSS](https://freshrss.org/) personnel, qui
présente les articles à la manière de **Google Discover** : un flux vertical
unique, mélangé, sans fin apparente.

Pas de liste de flux à parcourir, pas de compteur de non-lus à faire descendre.
On fait défiler ; ce qui a été suffisamment vu devient lu.

> **État : en cours de développement.** La connexion au serveur, la lecture
> paginée du flux, le cache local et l'écran Discover fonctionnent. Le mélange
> des sources, la détection de lecture, la file de marquages et l'écran de
> réglages sont écrits et éprouvés, mais **pas encore tous branchés** —
> [ARCHITECTURE.md §9.1](./ARCHITECTURE.md) distingue précisément ce qui est
> assemblé de ce qui ne l'est pas, et [TASKS.md](./TASKS.md) donne l'avancement
> tâche par tâche.

---

## Ce que fera l'application

- connexion à un serveur FreshRSS via son API compatible Google Reader ;
- flux vertical unique, tous abonnements mélangés ;
- défilement infini paginé ;
- marquage automatique comme lu selon la visibilité réelle d'un article ;
- synchronisation du statut lu avec le serveur, y compris après une coupure ;
- tirer-pour-rafraîchir, sans perdre la position de lecture ;
- ouverture de l'article d'origine dans le navigateur ;
- cache local consultable hors ligne ;
- interface Material 3, thèmes clair et sombre.

La spécification complète est dans [SPECS.md](./SPECS.md).

## Ce qu'elle ne fera pas

Gestion des abonnements, comptes multiples, widgets, notifications, partage
social, synchronisation en arrière-plan. Voir [SPECS.md §2](./SPECS.md).

---

## Prérequis

- Un serveur **FreshRSS** dont l'**API est activée**
  (*Administration → Authentification → Autoriser l'accès par API*).
- Un **mot de passe API**, distinct du mot de passe de connexion
  (*Profil → Mot de passe API*). C'est la principale cause d'échec de connexion.
- **Android 8.0** (API 26) ou supérieur.

---

## Construire

Le projet se construit avec le JDK embarqué d'Android Studio.

```bash
export JAVA_HOME=$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
```

Vérification complète, à passer avant tout commit :

```bash
./gradlew ktlintCheck detekt lint test :domain:koverVerify assembleDebug
```

Rendu visuel (hors CI, plusieurs minutes) :

```bash
./gradlew :app:verifyRoborazziDebug   # comparer aux références
./gradlew :app:recordRoborazziDebug   # réenregistrer un changement voulu
```

Installer sur un appareil connecté :

```bash
./gradlew :app:installDebug
```

---

## Structure

```
:domain   Kotlin/JVM pur — les décisions. Le SDK Android n'y est pas.
:app      Android — l'affichage, le stockage, les appels réseau.
```

Le détail est dans [ARCHITECTURE.md](./ARCHITECTURE.md).

---

## Documentation

| Fichier | Contenu |
|---|---|
| [SPECS.md](./SPECS.md) | Ce que l'application doit faire |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Comment elle est conçue |
| [TASKS.md](./TASKS.md) | Ce qui est fait, en cours, et à faire |
| [AGENTS.md](./AGENTS.md) | Les règles de développement |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | Comment contribuer |
| [docs/freshrss-api.md](./docs/freshrss-api.md) | Relevé de l'API FreshRSS |
| [PROMPT.md](./PROMPT.md) | L'intention initiale, figée |

---

## Développement assisté

Le dépôt est piloté par un **Harness** : le travail est organisé en *Goals*
découpés en tâches, consignés dans [TASKS.md](./TASKS.md). Quatre commandes
Claude Code l'actionnent :

| Commande | Rôle |
|---|---|
| `/status` | Où en est le projet, et ce qui cloche |
| `/goal <objectif>` | Décomposer un objectif en tâches, puis les exécuter |
| `/task [GOAL-00X-TYY]` | Exécuter une tâche précise, ou la prochaine |
| `/verify` | Compiler, tester, et confronter TASKS.md à la réalité |

Un agent arrivant sur le dépôt lance `/status`, puis `/goal` ou `/task`. Il n'a
pas besoin qu'on lui redonne le contexte : il est dans les fichiers.

---

## Vie privée

L'application ne communique qu'avec **le serveur FreshRSS de l'utilisateur**.
Aucune télémétrie, aucun service tiers, aucune publicité. Les seules autres
connexions sortantes sont le chargement des images d'articles et l'ouverture
d'un lien dans le navigateur, l'une et l'autre à l'initiative de l'utilisateur.

---

## Licence

[MIT](./LICENSE) — © 2026 Valentin Brosseau.

---

## Origine

L'ossature technique — architecture, configuration Gradle, outillage de qualité,
conventions — provient de
[`c4software/tailscale-auto-rules`](https://github.com/c4software/tailscale-auto-rules),
dont la logique métier a été retirée.
