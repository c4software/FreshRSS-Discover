# SPECS.md — Spécification fonctionnelle

Source de vérité **fonctionnelle** : ce que l'application doit faire.

Le *comment* est dans [ARCHITECTURE.md](./ARCHITECTURE.md), l'*ordre* dans
[TASKS.md](./TASKS.md), les *règles de travail* dans [AGENTS.md](./AGENTS.md).

---

## 1. Intention

**FreshRSS Discover** est un client Android pour un serveur
[FreshRSS](https://freshrss.org/) personnel.

Il ne cherche pas à reproduire un lecteur RSS classique — liste de flux, boîte
de réception, compteurs de non-lus à faire descendre à zéro. Il propose l'usage
inverse, celui de **Google Discover** : un flux vertical unique, mélangé, sans
fin apparente, que l'on parcourt sans avoir à décider par quel flux commencer.

```
FreshRSS
   ↓
Articles des différents flux
   ↓
Mélange des sources
   ↓
Flux vertical continu
   ↓
L'utilisateur fait défiler
   ↓
Les articles suffisamment visibles deviennent lus
   ↓
De nouveaux articles sont chargés
```

Ce que cela implique, et qui structure toute la suite :

- **Aucune organisation imposée à l'utilisateur.** Pas de navigation par flux ni
  par dossier dans le parcours principal.
- **Aucun geste pour marquer comme lu.** Lire, c'est faire défiler.
- **Aucune fin.** Le flux se prolonge tant qu'il reste des articles.
- **Aucun compteur culpabilisant.** Le nombre de non-lus n'est pas mis en avant.

## 2. Hors périmètre

Explicitement exclus, afin qu'aucun Goal ne les introduise par glissement :

- gestion des abonnements (ajout, suppression, classement) — cela se fait dans
  FreshRSS ;
- lecture hors ligne du contenu intégral des articles ;
- comptes multiples ;
- widgets, tuiles rapides, notifications ;
- partage social, commentaires, annotations ;
- synchronisation en arrière-plan périodique — l'application ne travaille que
  lorsqu'elle est ouverte.

Ces points ne sont pas refusés pour toujours ; ils ne font pas partie de la
première version, et les introduire demanderait de mettre à jour ce document.

---

## 3. Connexion au serveur

### 3.1 Ce que l'utilisateur saisit

| Champ | Contenu |
|---|---|
| Adresse du serveur | URL de l'instance FreshRSS |
| Identifiant | Nom d'utilisateur FreshRSS |
| Mot de passe API | **Distinct** du mot de passe de connexion |

L'adresse est saisie sous sa forme naturelle (`https://rss.exemple.org`).
L'application en dérive elle-même le point d'entrée
(`…/api/greader.php`) : demander à l'utilisateur de connaître ce chemin serait
lui faire porter un détail d'implémentation.

Une adresse sans schéma est complétée en `https://`. Le schéma `http://` reste
accepté — les instances auto-hébergées sur réseau local sont un cas réel — mais
l'application indique alors que la connexion n'est pas chiffrée.

### 3.2 Ce que l'application doit expliquer

Le mot de passe API est la principale cause d'échec de connexion, et son
existence n'est pas évidente. L'écran de connexion doit donc indiquer où le
trouver dans FreshRSS (*Profil → Mot de passe API*), et non se contenter de
signaler un échec.

### 3.3 Diagnostic des échecs

Chaque cause a son message. Un « échec de connexion » générique est un défaut.

| Cause | Ce que l'utilisateur doit lire |
|---|---|
| Adresse injoignable | Le serveur ne répond pas |
| Adresse joignable, mais pas une instance FreshRSS | Cette adresse ne semble pas être un serveur FreshRSS |
| API désactivée sur le serveur | L'API est désactivée : l'activer dans l'administration FreshRSS |
| Identifiant ou mot de passe API refusé | Vérifier l'identifiant et le **mot de passe API** |
| Absence de réseau | Aucune connexion réseau |
| En-tête `Authorization` non transmis par le serveur web | Les identifiants sont bons, mais le serveur ne transmet pas l'autorisation : corriger la configuration du reverse-proxy |

Le dernier cas mérite son message propre, même s'il est rare. Sans lui, la
connexion réussirait puis **tout** échouerait ensuite en « identifiants
refusés » : l'utilisateur changerait son mot de passe en vain, alors que la
correction est dans la configuration de son serveur.

L'application ne peut pas distinguer « identifiant inconnu » de « mot de passe
incorrect » : FreshRSS répond la même chose aux deux, et c'est délibéré — les
distinguer permettrait d'énumérer les comptes. Le message couvre donc les deux
hypothèses.

### 3.4 Persistance de la session

La session est conservée entre deux lancements : l'utilisateur ne se connecte
qu'une fois.

**Le mot de passe API n'est jamais enregistré.** Le jeton délivré par FreshRSS
n'expire pas : le conserver suffit à rouvrir l'application sans reconnexion.
Garder en plus le mot de passe n'apporterait rien et doublerait la surface
exposée.

Le jeton est un secret : il est stocké **chiffré**, adossé au *keystore* de
l'appareil, jamais journalisé, jamais inclus dans un rapport d'erreur.
L'adresse du serveur et l'identifiant, qui n'en sont pas, restent lisibles —
les masquer compliquerait le diagnostic sans rien protéger.

Si le secret devient illisible — la clé du *keystore* est perdue lorsque
l'utilisateur change son verrouillage d'écran ou restaure une sauvegarde sur un
autre appareil — l'application se comporte comme s'il n'y avait pas de session,
et ramène à l'écran de connexion. Elle ne plante pas.

Si le serveur refuse le jeton — cas réel lorsque l'utilisateur change son mot de
passe API — l'application revient à l'écran de connexion en expliquant pourquoi,
sans perdre l'adresse ni l'identifiant déjà saisis.

### 3.5 Déconnexion

Une action de déconnexion efface le jeton, les identifiants et le cache local.
Elle est confirmée, car elle est destructrice.

---

## 4. Le flux Discover

### 4.1 Contenu

Le flux présente les articles **non lus** de tous les abonnements, toutes
catégories confondues.

### 4.2 Mélange des sources

C'est le cœur de l'application, et sa seule règle réellement subtile.

Un tri par date seule ne suffit pas : un flux très prolifique occuperait des
écrans entiers d'affilée, et les flux peu actifs deviendraient invisibles. Le
mélange doit donc **répartir les sources**, sans pour autant présenter comme
récent un article ancien.

Règles, par ordre de priorité :

1. **Pas de monotonie de source.** Deux articles consécutifs du même flux sont
   évités tant qu'une autre source est disponible.
2. **Récence respectée.** L'ordre global reste globalement chronologique
   inverse : le mélange réordonne localement, il ne remonte pas un article vieux
   d'un mois au-dessus d'un article du jour.
3. **Déterminisme.** Deux affichages successifs du même ensemble d'articles
   produisent le même ordre. Un flux qui se réordonne au retour sur l'écran
   donne le sentiment d'avoir perdu quelque chose.
4. **Continuité entre les pages.** La règle 1 s'applique aussi à la jonction
   entre une page et la suivante.

### 4.3 Présentation d'un article

Chaque article expose :

- son **titre** ;
- le **nom de son flux d'origine**, sans lequel le mélange serait déroutant ;
- sa **date de publication**, en forme relative (« il y a 2 h ») ;
- son **image d'illustration**, lorsqu'elle existe ;
- un **extrait** de son contenu, écourté par l'application : le serveur envoie
  le résumé complet, qui atteint plusieurs dizaines de milliers de caractères sur
  certains flux (§8, question 7).

Un article sans image reste lisible : l'absence d'illustration ne doit pas
produire un espace vide, ni une image de remplacement générique.

### 4.4 Défilement infini

Une nouvelle page est demandée **avant** que l'utilisateur n'atteigne le bas, de
sorte que le défilement ne s'interrompe pas.

- Le chargement en cours est visible en bas du flux.
- Un échec de chargement affiche un message et une action « Réessayer », **sans
  vider ce qui est déjà affiché**.
- Lorsqu'il n'y a plus d'article, le flux se termine par un message explicite.
  Un flux qui cesse simplement de s'allonger est indistinguable d'une panne.

### 4.5 Marquage automatique comme lu

Un article est considéré comme lu lorsqu'il a été **suffisamment visible** :
au moins **60 % de sa hauteur** affichée pendant au moins **1 seconde continue**.

Ce double seuil est délibéré : la surface seule marquerait comme lus les
articles traversés par un défilement rapide ; la durée seule marquerait un
article à peine effleuré en bord d'écran.

Ces deux valeurs sont des **paramètres nommés**, pas des constantes dispersées :
elles seront ajustées à l'usage.

Comportement associé :

- Un article marqué lu **reste affiché** et à sa place. Le faire disparaître
  sous le doigt déplacerait le contenu en cours de lecture.
- Le marquage est **envoyé au serveur par lots**, pas un appel par article.
- Le marquage est **optimiste** : l'état local change immédiatement, la
  synchronisation suit. Un échec réseau ne doit pas se voir pendant la lecture.
- Un marquage non transmis est **conservé** et rejoué à la prochaine occasion,
  y compris après redémarrage de l'application.

### 4.6 Rafraîchissement

Un **tirer-pour-rafraîchir** demande les articles parus depuis la dernière
récupération.

- Les nouveaux articles sont insérés **en tête**.
- La position de lecture est **préservée** : l'utilisateur ne se retrouve pas
  déplacé.
- Le rafraîchissement ne réordonne pas ce qui est déjà affiché — voir la règle
  de déterminisme (§4.2).

### 4.7 Ouverture d'un article

Toucher un article ouvre le **lien d'origine** dans le navigateur, via un onglet
personnalisé (*Custom Tab*) : l'utilisateur garde le contexte de l'application
et retrouve sa session et ses réglages de navigateur.

Ouvrir un article le marque comme lu, quelle que soit sa visibilité passée.

Un article sans lien exploitable n'est pas cliquable, et le donne à voir.

---

## 5. Comportement réseau

### 5.1 Cache local

Les articles récupérés sont conservés localement. Au lancement, le flux affiche
**immédiatement** le contenu du cache, puis se met à jour.

Un écran vide pendant une requête réseau donnerait l'impression d'une
application sans contenu, alors qu'elle en a.

### 5.2 Hors ligne

Sans réseau :

- le flux reste consultable à partir du cache ;
- l'état est signalé sans être alarmant ;
- les marquages comme lus sont enregistrés localement et transmis au retour du
  réseau ;
- l'ouverture d'un article échoue avec un message explicite.

### 5.3 Purge

Le cache est borné. Les articles **lus et synchronisés** sont supprimés au-delà
d'un seuil d'ancienneté ; les articles non lus et les marquages en attente ne
sont jamais purgés.

---

## 6. Réglages

L'écran de réglages reste minimal :

- adresse du serveur et identifiant connectés (en lecture seule) ;
- seuils du marquage automatique (§4.5) ;
- taille du cache et action de purge manuelle ;
- déconnexion ;
- version de l'application et licence.

---

## 7. Exigences transversales

### 7.1 Accessibilité

- Toute image porte une description, ou est explicitement décorative.
- Les cibles tactiles font au moins 48 dp.
- L'application reste utilisable avec une taille de police système augmentée.
- Le contraste respecte le niveau **AA** en thème clair **et** sombre.

### 7.2 Interface

- Material 3, couleur dynamique lorsque la plateforme la fournit.
- Thème clair et thème sombre, tous deux vérifiés par capture (AGENTS.md §4).
- Bord à bord, sans contenu masqué par les barres système.

### 7.3 Langue

Interface en **français**. Les contenus d'articles sont affichés tels que
publiés.

### 7.4 Vie privée

L'application ne communique **qu'avec le serveur FreshRSS de l'utilisateur**.
Aucune télémétrie, aucun service tiers, aucune publicité. Les seules autres
connexions sortantes sont le chargement des images d'articles et l'ouverture
d'un lien dans le navigateur, l'une et l'autre à l'initiative de l'utilisateur.

---

## 8. Ce qui reste à trancher

Décisions volontairement différées. Chacune doit être arbitrée par le Goal qui
la rencontre, puis **inscrite ici** — pas laissée implicite dans le code.

### Tranchées

| # | Question | Réponse, et ce qui l'a décidée |
|---|---|---|
| 1 | Taille de page de l'API (`n`) | **40 articles.** Mesuré sur un flux réel : résumé médian de 1 324 caractères, 90ᵉ centile à 4 379. Une page de 40 pèse donc environ 55 ko, ce qui reste raisonnable sur réseau mobile tout en laissant assez d'avance pour que le défilement ne s'interrompe pas (§4.4). Le serveur accepte des valeurs bien supérieures — `n=100000` a renvoyé 4 645 articles sans broncher — mais tout demander d'un coup ne servirait qu'à retarder le premier affichage. |
| 6 | Origine de l'image d'illustration | **`enclosure` d'abord, première balise `<img>` du contenu ensuite.** L'ordre est celui de la fiabilité : une `enclosure` est une illustration déclarée, une `<img>` peut être un pixel de suivi ou un logo. Mais s'en tenir aux `enclosure` couvrirait **33 %** des articles, contre **73 %** avec le repli — mesuré sur 60 articles réels. Priver les deux tiers du flux d'illustration appauvrirait exactement ce qui fait un flux Discover. |

### Encore ouvertes

| # | Question | Quand la trancher |
|---|---|---|
| 2 | Formulation exacte de l'algorithme de mélange | Au Goal du mélange, à partir de données réelles |
| 3 | Seuil d'ancienneté de purge du cache | Au Goal du cache |
| 4 | Taille du lot de marquage et délai de regroupement | Au Goal de la synchronisation |
| 5 | Comportement si un flux ne contient que des articles lus | Au Goal du flux |
| 7 | Longueur de l'extrait affiché | Au Goal de la présentation. Le serveur ne tronque pas utilement : un résumé réel atteint 34 777 caractères. L'extrait doit donc être écourté côté application |
