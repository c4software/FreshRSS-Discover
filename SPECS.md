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
- widgets, tuiles rapides ;
- partage social, commentaires, annotations ;
- **synchronisation en arrière-plan** — l'application ne va chercher des
  articles que lorsqu'elle est ouverte, et aucune connexion ne part sans geste
  de l'utilisateur (§7.4).

Ces points ne sont pas refusés pour toujours ; ils ne font pas partie de la
première version, et les introduire demanderait de mettre à jour ce document.

> **Les notifications ont quitté cette liste**, à la demande de l'auteur : voir
> §4.9. Elles n'entament pas l'exclusion voisine — le rappel lit le **cache
> local** et ne se connecte à rien. C'est ce qui distingue une notification
> locale d'une synchronisation de fond, et ce qui fait qu'une seule des deux est
> ici.

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

**Arbitrage entre les règles 1 et 2.** Elles sont structurellement
incompatibles au-delà d'une certaine amplitude : répartir parfaitement les
sources exigerait parfois de remonter un article ancien très haut. **La récence
l'emporte.** Concrètement, un article n'est jamais présenté plus de sept
positions avant son rang chronologique ; passé cette borne, la monotonie de
source est acceptée plutôt que de mentir sur la fraîcheur.

Cette borne est exprimée en **positions**, non en durée. Un seuil temporel se
comporterait très différemment sur un flux qui publie trois articles par jour
et sur un flux qui en publie trois cents — la borne en positions est la même
partout, et c'est elle que l'utilisateur perçoit en faisant défiler.

C'est le seul arbitrage de cette section visible par l'utilisateur : un mélange
plus agressif se règle en desserrant cette borne.

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
elles seront ajustées à l'usage. Les deux seuils sont **inclusifs** — « au
moins » se lit littéralement. Ce n'est pas un détail : 0,6 n'est pas
représentable exactement en binaire, et un seuil exclusif rendrait la règle
dépendante de l'arrondi du calcul fait par l'interface.

Deux précisions que l'implémentation a rendues nécessaires :

- **« 60 % de sa hauteur » se mesure sur la part visible de l'écran, pas sur la
  hauteur propre de l'article.** Pris au pied de la lettre, un article plus haut
  que l'écran ne pourrait jamais atteindre 60 % de lui-même, et ne deviendrait
  donc **jamais** lu. C'est l'appelant qui borne la fraction en conséquence.
- **La visibilité doit être observée même quand rien ne bouge.** La règle porte
  sur une durée, et la durée ne s'écoule pas toute seule : sans observation
  périodique pendant que la liste est immobile, un article resté dix secondes à
  l'écran ne serait jamais marqué lu. C'est le piège d'intégration le plus
  probable de cette fonctionnalité.

Comportement associé :

- Un article marqué lu **reste affiché** et à sa place. Le faire disparaître
  sous le doigt déplacerait le contenu en cours de lecture.
- Le marquage est **envoyé au serveur par lots**, pas un appel par article : les
  marquages sont regroupés pendant quelques secondes avant d'être transmis
  (§8, question 4). Rien n'est perdu pendant ce délai — la file survit à la
  fermeture — mais la lecture n'est alors connue que de l'appareil.
- Le marquage est **optimiste** : l'état local change immédiatement, la
  synchronisation suit. Un échec réseau ne doit pas se voir pendant la lecture.
- Un marquage non transmis est **conservé** et rejoué à la prochaine occasion,
  y compris après redémarrage de l'application.

### 4.6 Rafraîchissement

Recharger repart de zéro : cela **vide ce qui est affiché**, recharge le début
du flux, et **revient automatiquement au premier article**.

Deux commandes le déclenchent, et elles font exactement la même chose :

| Commande | Disponible en | Pourquoi |
|---|---|---|
| **Tirer-pour-rafraîchir** | mode Liste | La convention du geste sur un flux vertical |
| **Bouton, sur la ligne du titre** | les deux modes | En plein écran il n'y a pas de liste à tirer ; et un tirage n'est pas praticable par tout le monde (§7.1) |

Le bouton n'est donc pas un doublon du geste : il est **la seule** commande du
mode Balayage — y superposer un tirage vertical donnerait deux gestes
concurrents sur la même surface — et il est l'alternative au geste en mode
Liste, où rien ne le remplaçait.

- Ce qui est affiché est remplacé, pas complété. Ce qui était là disparaît.
- La position de lecture n'est **pas** préservée : le rechargement ramène au
  début, et c'est ce qu'il annonce.
- La pagination repart du début : le curseur précédent est abandonné.
- Pendant l'attente, la commande **montre qu'elle travaille** plutôt que de se
  griser ou de disparaître : grisée elle dirait « indisponible » et non « en
  cours » ; disparue, l'appui semblerait perdu.

**Ce choix a été fait contre l'option inverse**, et il vaut d'être expliqué.
Insérer les nouveaux articles en tête sans bouger l'utilisateur préserve sa
lecture, mais laisse le flux s'allonger indéfiniment et rend le geste presque
invisible — on tire, et rien ne semble se passer. Le rechargement complet donne
au geste un effet immédiat et lisible, au prix de la position de défilement ;
c'est la convention des applications où le flux est le contenu principal, et
c'est celle-ci qui a été retenue.

Conséquence assumée : un utilisateur qui recharge par réflexe perd l'endroit où
il lisait. Le geste doit donc rester délibéré — il n'est déclenché que par un
tirage franc, jamais par un simple défilement vers le haut.

**Cela ne vaut que pour un rechargement demandé.** Une fermeture, elle, n'est
pas une demande : le lancement suivant rouvre le même flux, inchangé (§5.3).

#### Quand le flux affiché date

Rien ne se synchronise en arrière-plan (§2) et le cache s'affiche dès le
lancement (§5.1) : l'écran d'un flux vieux de dix heures serait, sans cela,
indiscernable de celui d'un flux frais.

Au-delà de **six heures** sans réponse du serveur (§8, question 9), une
bandelette le dit et propose de recharger. Elle porte deux commandes —
**recharger**, qui n'est rien d'autre que le rechargement décrit ci-dessus, et
**plus tard**, pour qui n'est pas en état de le faire maintenant.

- Elle s'efface **à la main**, jamais par minuteur : un message qui s'efface
  tout seul se rate, et celui-ci explique quelque chose qu'on n'a pas vu venir.
- Elle paraît dans les **deux modes** (§4.8), et l'y faire taire vaut pour les
  deux : c'est le même flux.
- Elle **ne paraît pas hors ligne** : le bandeau de §5.2 dit déjà pourquoi le
  flux est ancien, et proposer de recharger ouvrirait une porte qui ne mène
  nulle part.
- Elle ne paraît pas non plus pendant un rechargement, ni sur un écran sans
  article — il n'y a alors pas de flux ancien, mais un écran vide, qui a son
  propre message.
- **L'avoir fait taire ne vaut que pour l'état du moment.** Un rechargement
  réussi, puis six heures de plus, et l'invitation revient.

### 4.7 Ouverture d'un article

Toucher un article ouvre le **lien d'origine** dans le navigateur, via un onglet
personnalisé (*Custom Tab*) : l'utilisateur garde le contexte de l'application
et retrouve sa session et ses réglages de navigateur.

Ouvrir un article le marque comme lu, quelle que soit sa visibilité passée.

Un article sans lien exploitable n'est pas cliquable, et le donne à voir.

### 4.8 Deux modes de présentation

Le flux se parcourt de deux façons, au choix de l'utilisateur. **Le contenu est
le même** : mêmes articles, même mélange, mêmes règles de lecture et de
chargement. Seule la présentation change.

| Mode | Geste | Ce qu'il montre |
|---|---|---|
| **Liste** (par défaut) | défilement vertical | plusieurs articles à l'écran, en cartes |
| **Balayage** | balayage horizontal | **un** article à la fois, en plein écran |

Le mode Balayage reprend le geste des réseaux sociaux : on passe à l'article
suivant d'un balayage de gauche à droite, et on revient au précédent en sens
inverse. Ce n'est **pas** une navigation entre flux ou entre catégories — §1 et
§2 les excluent, et cela reste vrai ici. C'est le même flux mélangé, présenté
article par article.

Ce que ce mode implique, et qui n'est pas neutre :

- **Un article plein écran est intégralement visible.** La règle de §4.5
  s'applique telle quelle : il devient lu après la durée continue requise. Le
  seuil de surface, lui, est satisfait d'emblée — c'est donc la durée seule qui
  décide, et elle prend ici tout son sens.
- **Le retour en arrière ne « délit » pas.** Revenir sur un article déjà lu ne
  le remet pas en non-lu : le marquage n'est pas réversible par un geste de
  navigation.
- **Le chargement anticipé demeure** (§4.4) : la page suivante est demandée
  avant d'atteindre le dernier article chargé, et la fin du flux se dit
  explicitement plutôt que de bloquer le balayage.
- **L'extrait laisse place au contenu.** Le plein écran permet d'en montrer
  davantage que les trois lignes d'une carte ; la limite de §8 question 7 est
  propre au mode Liste.
- **Le mode est un réglage persistant** (§6) : l'application rouvre dans le
  mode que l'utilisateur a quitté.
- **Le geste est animé en pile de cartes.** L'article qui s'en va s'incline et
  s'efface en suivant le doigt ; le suivant attend derrière, centré et
  légèrement réduit, et grandit à mesure qu'il se découvre. C'est ce qui
  distingue une pile d'objets d'un défilement de plus, et c'est ce que le geste
  promet — mettre une carte de côté.
- **Il n'y a pas d'alternative au geste**, et c'est une lacune connue plutôt
  qu'un choix : §7.1 exige que l'application reste utilisable sans lui. Voir la
  tâche `GOAL-012-T07` de TASKS.md.

Le choix du mode ne modifie **jamais** l'ordre des articles : un utilisateur qui
bascule de l'un à l'autre retrouve le flux au même endroit, dans le même ordre
(règle de déterminisme de §4.2).

### 4.9 Rappel de lecture

Une notification quotidienne rappelle qu'il reste des articles à lire.

**Elle part à l'heure d'ouverture de la veille.** Pas à une heure choisie par le
développeur : une notification à 9 h chez quelqu'un qui lit le soir est une
interruption, pas un rappel. L'application retient le moment de sa **première**
ouverture du jour — celui où l'utilisateur tend la main vers elle — et c'est à
ce moment-là que le rappel tombe le lendemain.

**Elle ne part pas s'il n'y a rien à lire.** Un rappel annonçant que la pile est
vide est une interruption sans contrepartie, et c'est ce qui fait couper les
notifications d'une application.

**Elle cite des titres réels**, pris dans le flux, et annonce le nombre
d'articles restants. Un rappel qui ne dit pas ce qui attend ne se distingue pas
d'une publicité pour l'application elle-même.

**Sa formulation change d'un jour à l'autre.** Un message quotidien identique
cesse d'être lu au bout de trois jours : l'œil en apprend la forme et le balaie
sans le voir. La variation est **déterministe** — deux exécutions du même jour,
après un échec ou un redémarrage, donnent le même message.

**Ce qu'elle ne fait pas :** aucune requête réseau. Elle lit le cache local
(§5.4), et rien d'autre. Un article publié depuis la dernière ouverture n'y est
donc pas et ne sera pas annoncé ; c'est le prix assumé de §2, qui exclut
toujours la synchronisation en arrière-plan.

**Il n'y en a jamais deux.** Un nouveau rappel **remplace** le précédent au lieu
de s'empiler à côté : une pile de rappels quotidiens ne dit rien de plus qu'un
seul, et se balaie d'un geste sans être lue.

**Ouvrir l'application l'efface.** Le rappel a rempli son office au moment où
l'utilisateur arrive ; le laisser dans le volet en ferait un reliquat.

**Elle se désactive** depuis les réglages (§6). Sous Android 13, il n'y a aucune
permission de notification à retirer, et un rappel qu'on ne peut pas éteindre
est un défaut.

---

## 5. Comportement réseau

### 5.1 Cache local

Les articles récupérés sont conservés localement. Au lancement, le flux affiche
**immédiatement** le contenu du cache — et s'y arrête : **aucune requête ne part
tant qu'il y a quelque chose à montrer** (§8, question 10). Le flux du lancement
est celui qu'on a laissé, stable et identique d'une ouverture à l'autre ; sa
mise à jour est un geste — le rechargement de §4.6, que l'avis d'ancienneté
(§4.6, « quand le flux affiché date ») vient rappeler au bon moment. Le
défilement, lui, reste un geste comme un autre : atteindre le bas du connu
charge la suite (§4.4).

Un cache **vide** est l'unique exception : première ouverture, retour après
déconnexion — il n'y a rien à montrer, le premier chargement part de lui-même.
Un écran vide pendant une requête réseau donnerait l'impression d'une
application sans contenu ; un écran vide sans requête serait pire, une
application morte.

### 5.2 Hors ligne

Sans réseau :

- le flux reste consultable à partir du cache ;
- l'état est signalé sans être alarmant ;
- les marquages comme lus sont enregistrés localement et transmis au retour du
  réseau ;
- l'ouverture d'un article échoue avec un message explicite.

### 5.3 Le lancement rouvre en tête d'un flux stable

**La position de lecture n'est pas conservée.** Rouvrir l'application ramène en
haut du flux — le même flux, dans le même ordre, que celui qu'on a quitté
(§5.1).

Cette section a longtemps spécifié l'inverse : une reprise « au plus proche »,
mémorisée à la fermeture. Elle a été **retirée par décision d'auteur** le
2026-08-08, et il faut dire pourquoi, parce que la raison n'est pas « c'était
trop dur ». La mémoire de position se réécrivait à chaque lancement — l'article
en tête d'écran pendant les premières images écrasait la vraie place — et
chaque ouverture restaurait ce que le hasard de la précédente avait laissé.
Constaté sur appareil : un lancement sans un seul geste déplaçait la position
mémorisée. Le correctif existait, mais l'arbitrage est ailleurs : la reprise
protégeait contre un flux qui bougeait sous les pieds, et ce flux ne bouge
plus — le lancement n'interroge plus le réseau (§5.1). Sur un flux stable qui
rouvre à l'identique, retrouver sa place se fait en défilant ce qu'on
reconnaît ; la mécanique de mémorisation ne payait plus sa complexité.

Ce qui reste garanti, et qui compte davantage : le haut du flux au lancement
est **exactement** celui de la fermeture, nouveaux articles exclus puisqu'il
n'y en a pas sans geste.

### 5.4 Purge

Le cache est borné. Les articles **lus et synchronisés** sont supprimés au-delà
d'un seuil d'ancienneté (§8, question 3) ; les articles non lus ne sont jamais
purgés.

« **Et synchronisés** » se lit littéralement : un article dont le marquage attend
encore d'être transmis n'est **jamais** supprimé, même passé le seuil. Ce n'est
pas une précaution abstraite. La mémoire locale du « déjà lu » vit dans le cache
et nulle part ailleurs : effacer la ligne avant que le serveur ne connaisse le
marquage ferait redécrire l'article comme non lu au rafraîchissement suivant, et
il **réapparaîtrait dans le flux comme jamais lu**. Le cas se produit dès qu'un
appareil reste hors ligne plus longtemps que le seuil.

La purge manuelle, elle, ne demande **pas** de confirmation : elle n'emporte que
ce qui est à la fois lu, transmis et retéléchargeable. La déconnexion en demande
une parce qu'elle efface le jeton, les articles non lus et les marquages en
attente — rien n'en revient sans réseau ni mot de passe. Confirmer les deux
nivellerait la différence, et apprendrait à congédier la boîte de dialogue qui
compte.

---

## 6. Réglages

L'écran de réglages reste minimal :

- adresse du serveur et identifiant connectés (en lecture seule) ;
- **mode de présentation du flux** : Liste ou Balayage (§4.8) ;
- **rappel de lecture** : activé ou non (§4.9) ;
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
- **Aucune fonction ne dépend d'un seul geste.** Un lecteur d'écran se réserve
  le balayage horizontal pour sa propre exploration, et tout le monde n'a pas la
  précision ou la mobilité qu'un tirage demande. Le rechargement satisfait cette
  règle depuis §4.6.
- **La règle porte sur l'application, pas sur chacun de ses modes.** Avancer
  d'un article en mode Balayage demande un geste horizontal, et rien ne le
  remplace : les deux boutons qui l'avaient fait un temps encombraient l'écran
  pour un mode dont l'intérêt est justement de n'en avoir aucun. Ce n'est pas un
  écart, parce que le mode **Liste** — celui par défaut (§4.8) — donne accès au
  même flux, dans le même ordre, entièrement au défilement vertical et aux
  cibles ordinaires. Choisir le Balayage est une préférence, jamais un passage
  obligé, et le réglage qui en sort est lui-même atteignable sans ce geste.
  Conséquence assumée : qui emploie un lecteur d'écran et se retrouve en mode
  Balayage doit passer par les réglages pour revenir à la Liste.

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
| 2 | Formulation exacte de l'algorithme de mélange | **La récence l'emporte sur la répartition des sources**, avec une borne dure de sept positions, exprimée en rangs et non en durée (§4.2). Les deux règles sont structurellement incompatibles au-delà d'une certaine amplitude, et il fallait dire laquelle gagne. |
| 3 | Seuil d'ancienneté de purge du cache | **7 jours.** Au-delà, un article lu n'a plus de lecteur ; en deçà, il en a deux. Le **défilement arrière** d'abord : le flux est continu et sans repère, y remonter est le seul moyen de retrouver ce qu'on a survolé la veille — à 24 h le passé disparaîtrait entre deux lancements. Une semaine couvre le rythme réel : on revient le lundi, on retrouve son flux de vendredi. La **mémoire du « déjà lu »** ensuite, portée par le cache lui-même. 30 jours quadrupleraient le cache pour du contenu déjà consommé. |
| 4 | Taille de lot et délai de regroupement des marquages | **100 articles, fenêtre de 5 secondes à échéance fixe.** Le plancher du délai est la seconde de visibilité continue de §4.5 : au rythme maximal il n'apparaît qu'un article lu par seconde, donc une fenêtre plus courte se refermerait sur un **seul** article — la requête par article que §4.5 écarte. Le plafond est le geste de quitter l'application : pendant la fenêtre, la lecture n'est connue que de l'appareil. À 5 s cela reste l'exception ; à 30 s ce serait le cas courant. Fenêtre **fixe et non glissante** : un défilement continu produisant un lot toutes les 200 ms, une fenêtre relançable ne se refermerait jamais tant que l'utilisateur lit. |
| 6 | Origine de l'image d'illustration | **`enclosure` d'abord, première balise `<img>` du contenu ensuite.** L'ordre est celui de la fiabilité : une `enclosure` est une illustration déclarée, une `<img>` peut être un pixel de suivi ou un logo. Mais s'en tenir aux `enclosure` couvrirait **33 %** des articles, contre **73 %** avec le repli — mesuré sur 60 articles réels. Priver les deux tiers du flux d'illustration appauvrirait exactement ce qui fait un flux Discover. |
| 7 | Longueur de l'extrait affiché | **240 caractères, coupés sur une frontière de mot.** Trois lignes de `bodyMedium` sur 411 dp tiennent environ 180 caractères, 210 à la plus petite taille de police système ; 240 laisse la marge pour que la coupure visible soit l'ellipse et non un texte qui s'arrête net. Un mot tranché se lit comme un défaut, d'où la coupure sur l'espace précédente. Sans cela, chaque carte ferait mesurer jusqu'à 34 777 caractères à chaque recomposition. |
| 10 | Le lancement recharge-t-il le flux ? | **Non.** Décision d'auteur (2026-08-08) : le lancement montre le cache, stable, et aucune requête ne part sans geste — hors cache vide, où il n'y a rien à montrer. La requête automatique du lancement créait une course entre le disque et le réseau, dont l'issue décidait de l'écran ; et un flux qui bouge à l'ouverture se lit comme un flux qui se mélange. Le rechargement est un geste (§4.6), rappelé par l'avis d'ancienneté au-delà de six heures. |
| 9 | Seuil au-delà duquel le flux affiché est « ancien » (§4.6) | **6 heures.** Rien ne se synchronise en arrière-plan (§2), donc l'écran montre le cache jusqu'à ce que l'utilisateur demande autre chose : sans repère, un flux de la veille est indiscernable d'un flux frais. Un seuil court — une ou deux heures — transformerait l'invitation en réflexe quotidien, et une invitation qu'on apprend à ignorer ne dit plus rien. Six heures séparent nettement la session reprise dans l'heure, où le flux est encore celui qu'on a laissé, de la réouverture du lendemain matin. |

### Encore ouvertes

| # | Question | Quand la trancher |
|---|---|---|
| 5 | Comportement si un flux ne contient que des articles lus | Au Goal du flux |
| 8 | Longueur de l'extrait en mode Balayage (§4.8) | Au Goal de la vue Balayage. Le plein écran permet bien plus que les 240 caractères d'une carte |
