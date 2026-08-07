# API FreshRSS — référence de travail

Référence de l'**API compatible Google Reader** de FreshRSS, telle qu'utilisée
par ce projet.

## Provenance et niveau de confiance

Deux sources, de valeur inégale :

| Source | Ce qu'elle établit |
|---|---|
| [Documentation officielle « Accès mobile »](https://freshrss.github.io/FreshRSS/fr/users/06_Mobile_access.html) | L'URL de base, `ClientLogin`, l'en-tête d'autorisation, la liste des points d'entrée principaux, le mot de passe API |
| [`p/api/greader.php`](https://github.com/FreshRSS/FreshRSS/blob/edge/p/api/greader.php) et [`app/Models/Entry.php`](https://github.com/FreshRSS/FreshRSS/blob/edge/app/Models/Entry.php), branche `edge` | Les **noms exacts des paramètres**, leurs valeurs par défaut, la forme des réponses, la sémantique de pagination |
| **`https://demo.freshrss.org/`**, interrogé le 2026-08-07 | Les **codes et corps réellement renvoyés** sur les chemins non authentifiés |
| Une **instance personnelle**, interrogée le 2026-08-07 | Tout ce qui exige un compte : la réponse de `ClientLogin` en succès, le jeton de modification, la forme réelle des articles, le comportement effectif de la pagination |

L'instance personnelle n'est pas nommée ici, et ne doit pas l'être : elle n'a
servi que de banc d'essai. Les constats qu'elle a permis sont reproductibles sur
n'importe quelle installation FreshRSS disposant d'un mot de passe API.

La documentation officielle ne détaille pas les paramètres de pagination ni la
forme du JSON renvoyé. Tout ce qui figure ci-dessous sans mention contraire a
donc été **lu dans le code source**, et non deviné.

Ce qui porte la mention **« constaté »** a été vérifié contre un serveur réel —
le serveur de démonstration pour les chemins ouverts, une instance personnelle
pour les chemins authentifiés. Cela a d'ailleurs **corrigé une erreur de
lecture** : voir §2.1, où la frontière entre `400` et `401` n'est pas celle que
la source laissait supposer. Les points qui restent incertains sont regroupés en
fin de document.

> ⚠️ Cette page est un relevé, pas un contrat. FreshRSS peut la faire évoluer.
> Un Goal qui touche à l'API relit la source avant d'implémenter (AGENTS.md §7).

---

## 1. URL de base

```
https://<serveur-freshrss>/api/greader.php
```

Le préfixe `/api/greader.php` est retiré du chemin par le routeur, qui tolère
également son absence. Les chemins ci-dessous sont donnés complets.

### 1.1 Reconnaître une instance FreshRSS — *constaté*

```
GET /api/greader.php
```

répond **`OK`** — exactement deux octets, sans saut de ligne — avec un statut
`200`. C'est le moyen le plus économique de valider une adresse saisie par
l'utilisateur, et le seul discriminant fiable pour
[`AuthError.NotAFreshRssServer`](../domain/src/main/kotlin/fr/vbrosseau/freshrssdiscover/domain/auth/AuthError.kt).

Deux pièges, tous deux constatés :

- **le `Content-Type` est `text/html`**, pas `text/plain`. Se fier au type MIME
  pour décider ne marcherait pas ;
- **la moindre chaîne de requête casse la détection.** `GET …/greader.php?x=1`
  répond `400 Bad Request` : le court-circuit qui renvoie `OK` exige un chemin
  **et** une chaîne de requête vides. La sonde doit donc être un `GET` nu.

Un hôte joignable qui n'est pas une instance FreshRSS répond ce qu'il veut —
`404` sur `example.com`, constaté. Aucun code ne caractérise ce cas : seul le
corps `OK` caractérise le cas favorable.

> ⚠️ **Cette sonde ne dit rien de l'état de l'API.** Elle répond `OK` avec un
> statut `200` **même lorsque l'accès par API est désactivé** sur le serveur —
> constaté. Le court-circuit qui renvoie `OK` est placé avant la vérification
> `api_enabled` dans le routeur, et n'en tient donc aucun compte.
>
> Conséquence directe sur l'ordre des appels : reconnaître l'instance ne suffit
> pas, c'est `ClientLogin` qui révèle le `503`. Une implémentation qui
> conclurait « serveur valide » après cette seule sonde afficherait ensuite un
> diagnostic faux.

### 1.2 Vérifier la configuration du serveur web — *constaté*

```
GET /api/greader.php/check/compatibility
```

Atteste que le serveur web **transmet bien l'en-tête `Authorization`**. Certains
reverse-proxies le suppriment ; sans cette sonde, toute authentification
échouerait ensuite en `401`, avec un message accusant à tort les identifiants de
l'utilisateur.

Deux particularités, sans lesquelles la sonde ne sert à rien :

- **le statut est `200` dans les deux cas.** Le verdict est dans le **corps** :
  `PASS`, ou `FAIL <raison>`. Tester le code HTTP ne vérifie rien ;
- **la requête doit elle-même porter un en-tête `Authorization`**, fût-il
  factice. La sonde constate la présence de l'en-tête qu'elle reçoit — appelée
  sans en-tête, elle répond toujours `FAIL get HTTP Authorization header!
  Wrong Web server configuration.`, y compris sur un serveur parfaitement
  configuré. Constaté sur le serveur de démonstration : `FAIL` sans en-tête,
  `PASS` avec `Authorization: GoogleLogin auth=x/y`.

---

## 2. Authentification

### 2.1 ClientLogin

```
POST /api/greader.php/accounts/ClientLogin
Content-Type: application/x-www-form-urlencoded

Email=<utilisateur>&Passwd=<mot de passe API>
```

Le serveur accepte aussi ces paramètres en query string, mais **journalise un
avertissement de dépréciation** : le mot de passe apparaîtrait dans les
journaux. Toujours utiliser `POST`.

Le mot de passe attendu est le **mot de passe API**, distinct du mot de passe
de connexion. Il se définit dans FreshRSS sous *Profil → Mot de passe API*. Un
compte sans mot de passe API configuré ne peut pas se connecter : le champ
correspondant est vide côté serveur et la comparaison échoue systématiquement.

**Réponse en succès** — *constaté contre une instance personnelle, le
2026-08-07* : statut `200`, `text/plain`, **exactement trois lignes, dans cet
ordre** :

```
SID=<utilisateur>/<condensat>
LSID=null
Auth=<utilisateur>/<condensat>
```

`SID` et `Auth` portent bien la **même** valeur — la source le laissait
entendre, l'expérience le confirme. `LSID` vaut littéralement la chaîne `null`,
et non une valeur absente : un analyseur qui traiterait les trois lignes de la
même façon récupérerait la chaîne `"null"`, pas un vide.

Seule `Auth` nous intéresse. L'ordre étant stable, il reste néanmoins plus sûr
de chercher la ligne par son préfixe `Auth=` que par son rang : rien dans la
source ne garantit contractuellement cet ordre.

Le jeton est un condensat déterministe de (sel du serveur + utilisateur +
condensat du mot de passe API). Conséquences pratiques :

- **il n'expire pas** — il reste valable tant que le mot de passe API et le sel
  du serveur ne changent pas ;
- il est donc **stockable** et réutilisable entre deux lancements ;
- il **s'invalide sans préavis** si l'utilisateur change son mot de passe API.
  Une réponse `401` sur une requête authentifiée doit donc ramener l'utilisateur
  à l'écran de connexion, pas afficher une erreur réseau.

**Échecs** — *constaté, et différent de ce que la source laissait supposer* :

| Situation | Réponse | Corps |
|---|---|---|
| Nom d'utilisateur **syntaxiquement invalide** — vide, espaces, `!`, `../` | `400` | `Bad Request!` |
| Nom **bien formé** mais inconnu du serveur | `401` | `Unauthorized!` |
| Mot de passe API incorrect | `401` | `Unauthorized!` |
| Compte sans mot de passe API configuré | `401` | `Unauthorized!` |
| API désactivée sur le serveur | `503` | `Service Unavailable!` |

> ⚠️ **Correction d'une erreur de lecture.** La source appelle
> `checkUsername()` avant tout, ce qui donnait à croire qu'un utilisateur
> inconnu produisait `400`. C'est faux : cette fonction ne valide que la
> **syntaxe** du nom. Un nom bien formé mais inexistant poursuit son chemin,
> trouve une configuration vide, et échoue en `401` comme un mauvais mot de
> passe.
>
> **Conséquence directe sur SPECS.md §3.3** : « utilisateur inconnu » et
> « mot de passe refusé » sont **indistinguables**, et c'est le comportement
> souhaitable — distinguer les deux permettrait d'énumérer les comptes. Le
> message affiché doit donc couvrir les deux hypothèses à la fois, ce qu'il fait
> déjà (« Vérifier l'identifiant et le **mot de passe API** »).
>
> Un `400` n'est donc **jamais** la faute de l'utilisateur sur son mot de passe :
> c'est une anomalie de saisie du seul identifiant, ou de programmation.

Les corps d'erreur sont en `text/plain; charset=UTF-8`, avec
`X-Content-Type-Options: nosniff` — constaté.

L'API doit être activée globalement dans FreshRSS (*Administration →
Authentification → Autoriser l'accès par API*).

**Ce que répond alors le serveur — constaté**, API réellement désactivée :

| Chemin | Réponse |
|---|---|
| `/api/greader.php` (sonde de reconnaissance) | **`OK`, statut `200`** — inchangé |
| `/accounts/ClientLogin` | `503`, `Service Unavailable!` |
| `/check/compatibility` | `503`, `Service Unavailable!` |
| `/reader/api/0/…` | `503`, `Service Unavailable!` |

La première ligne est contre-intuitive et elle compte : la sonde de
reconnaissance est aveugle à l'état de l'API. Dire « tous les points d'entrée
répondent `503` » serait faux, et conduirait à une détection qui ne marche pas.

### 2.2 En-tête des requêtes authentifiées

```
Authorization: GoogleLogin auth=<utilisateur>/<jeton>
```

Toutes les requêtes hors `/accounts/*` l'exigent. Sans lui, ou avec un jeton
invalide : `401`.

### 2.3 Jeton de modification

```
GET /api/greader.php/reader/api/0/token
```

Renvoie en texte brut une chaîne de **57 caractères** (condensat complété par
des `Z`), suivie d'un saut de ligne. La longueur a été *constatée contre une
instance personnelle, le 2026-08-07* : elle vaut exactement 57, ce que la
lecture de la source laissait attendre.

Cette longueur ne doit pas pour autant être codée en dur comme critère de
validité : elle n'est garantie par aucun contrat, et un jeton refusé se signale
de toute façon par un `401`, pas par sa taille.

Ce jeton se transmet dans le champ **`T`** du corps `POST` de toute opération
modifiante (`edit-tag`, `mark-all-as-read`, `rename-tag`, `disable-tag`).

Constat de lecture du code, à connaître mais **à ne pas exploiter** : la
vérification accepte aussi `T` vide ou `T=x`, par compatibilité avec des clients
existants. Ce projet envoie toujours le vrai jeton — dépendre d'une tolérance
non documentée serait fragile.

Le jeton étant lui aussi déterministe, il peut être obtenu une fois puis
réutilisé. Un `401` sur une opération modifiante signifie que le jeton n'est
plus valable : le redemander une fois, et si l'échec persiste, traiter comme une
perte de session.

### 2.4 Identité du compte connecté — *constaté*

```
GET /api/greader.php/reader/api/0/user-info?output=json
```

*Constaté contre une instance personnelle, le 2026-08-07* :

```json
{
  "userId": "…",
  "userName": "…",
  "userProfileId": "…",
  "userEmail": ""
}
```

Un point à retenir : **`userEmail` peut être vide**. FreshRSS n'impose pas
d'adresse de courriel à ses comptes, et le champ est alors présent mais réduit à
la chaîne vide — il n'est pas omis. Un client qui s'en servirait pour afficher
l'utilisateur doit donc se rabattre sur `userName`, jamais sur `userEmail`.

Ce point d'entrée est le moyen le plus léger de vérifier qu'un jeton stocké est
toujours valable au démarrage : il ne renvoie aucun article et se contente de
`401` si le jeton a été invalidé.

---

## 3. Lecture

### 3.1 Liste des abonnements

```
GET /api/greader.php/reader/api/0/subscription/list?output=json
```

`output=json` est **obligatoire** : toute autre valeur répond `501 Not
Implemented`. Cette contrainte vaut aussi pour `tag/list` et `unread-count`.

```json
{
  "subscriptions": [
    {
      "id": "feed/12",
      "title": "Titre du flux",
      "categories": [{ "id": "user/-/label/Tech", "label": "Tech" }],
      "url": "https://exemple.org/rss",
      "htmlUrl": "https://exemple.org/",
      "iconUrl": "https://serveur/f.php?…",
      "frss:priority": "…"
    }
  ]
}
```

Les flux de priorité « masqué » sont absents de la liste.

### 3.2 Liste des étiquettes et dossiers

```
GET /api/greader.php/reader/api/0/tag/list?output=json
```

Renvoie les états système (`user/-/state/com.google/starred`,
`…/reading-list`, `user/-/state/org.freshrss/main`, `…/important`), puis une
entrée par catégorie (`type: "folder"`) et par étiquette.

### 3.3 Compteurs de non-lus

```
GET /api/greader.php/reader/api/0/unread-count?output=json
```

```json
{
  "max": 128,
  "unreadcounts": [
    { "id": "feed/12", "count": 7, "newestItemTimestampUsec": "1700000000000000" },
    { "id": "user/-/label/Tech", "count": 31, "newestItemTimestampUsec": "…" },
    { "id": "user/-/state/com.google/reading-list", "count": 128, "newestItemTimestampUsec": "…" }
  ]
}
```

`newestItemTimestampUsec` est en **microsecondes**, transmis comme chaîne.

### 3.4 Contenu d'un flux — le point d'entrée central

```
GET /api/greader.php/reader/api/0/stream/contents/reading-list
```

Le chemin peut désigner d'autres flux :

| Chemin | Contenu |
|---|---|
| `…/stream/contents/reading-list` | Tous les articles, hors flux masqués |
| `…/stream/contents/user/-/state/com.google/reading-list` | Identique (forme longue) |
| `…/stream/contents/user/-/state/com.google/starred` | Favoris |
| `…/stream/contents/user/-/state/org.freshrss/main` | Flux de priorité « principale » |
| `…/stream/contents/user/-/state/org.freshrss/important` | Flux de priorité « importante » |
| `…/stream/contents/feed/<id ou url>` | Un flux précis |
| `…/stream/contents/user/-/label/<nom>` | Une catégorie ou une étiquette |

Sans segment de flux, `reading-list` s'applique par défaut.

#### Paramètres

| Paramètre | Type | Défaut | Effet |
|---|---|---|---|
| `n` | entier | **20** | Nombre maximal d'articles renvoyés |
| `c` | entier (chaîne) | — | Jeton de continuation (voir §3.5) |
| `r` | `d` \| `n` \| `o` | `d` | Ordre : `d`/`n` = date décroissante, `o` = croissante |
| `xt` | identifiant d'état | — | **Exclure** les articles portant cet état |
| `it` | identifiant d'état | — | **Ne garder que** les articles portant cet état |
| `ot` | horodatage Unix (s) | — | Articles postérieurs à cette date |
| `nt` | horodatage Unix (s) | — | Articles antérieurs à cette date |
| `ck` | horodatage Unix (s) | — | Anti-cache. Accepté, sans effet fonctionnel |
| `output` | `json` | — | **Sans effet ici** : la réponse est toujours du JSON |

Valeurs admises pour `xt` et `it` :

```
user/-/state/com.google/read
user/-/state/com.google/unread
user/-/state/com.google/starred
```

Pour un flux Discover, l'appel utile est donc :

```
GET …/stream/contents/reading-list?n=40&xt=user/-/state/com.google/read
```

`ot` et `nt` filtrent sur la date de publication **ou** de modification de
l'article, pas sur sa date de récupération par le serveur — contrairement à ce
que laisse entendre la documentation historique de Google Reader.

#### Réponse

```json
{
  "id": "user/-/state/com.google/reading-list",
  "updated": 1700000000,
  "items": [ … ],
  "continuation": "45219"
}
```

Le champ `id` vaut **toujours** `user/-/state/com.google/reading-list`, quel que
soit le flux demandé : il ne peut pas servir à identifier la requête.

*Constaté contre une instance personnelle, le 2026-08-07* : la racine de la
réponse ne porte **que** ces quatre clés — `id`, `updated`, `items`,
`continuation` — et `continuation` disparaît en fin de flux (voir §3.5). Aucune
clé de métadonnée supplémentaire n'est à attendre.

Forme d'un article (mode `compat`, celui de ce point d'entrée) :

```json
{
  "id": "tag:google.com,2005:reader/item/00000000000b0b1f",
  "crawlTimeMsec": "1700000000000",
  "timestampUsec": "1700000000000000",
  "published": 1699999000,
  "title": "Titre de l'article",
  "canonical": [{ "href": "https://exemple.org/article" }],
  "alternate": [{ "href": "https://exemple.org/article" }],
  "categories": [
    "user/-/state/com.google/reading-list",
    "user/-/label/Tech",
    "user/-/state/org.freshrss/main",
    "user/-/state/com.google/read"
  ],
  "origin": {
    "streamId": "feed/12",
    "htmlUrl": "https://exemple.org/",
    "title": "Titre du flux"
  },
  "summary": { "content": "<p>…</p>" },
  "author": "Nom de l'auteur",
  "enclosure": [{ "href": "https://…/image.jpg", "type": "image/jpeg", "length": 12345 }]
}
```

*Constaté contre une instance personnelle, le 2026-08-07*, sur des articles
réels : les clés effectivement présentes sont `id`, `crawlTimeMsec`,
`timestampUsec`, `published`, `title`, `canonical`, `alternate`, `categories`,
`origin`, `summary` et `author` — `origin` portant lui-même `streamId`,
`htmlUrl` et `title`. Deux absences méritent d'être notées :

- **`content` est absent**, comme annoncé plus bas : seul `summary` existe dans
  ce mode. Le constat confirme la lecture de la source ;
- **`enclosure` est absent** de tous les articles observés. Ce n'est pas une
  particularité de l'instance : beaucoup de flux RSS n'émettent tout simplement
  aucune pièce jointe. La conséquence est directe pour ce projet — un client qui
  ne chercherait l'illustration d'un article que dans `enclosure` n'en trouverait
  presque jamais. **Il faut se rabattre sur les balises `<img>` du contenu HTML**
  de `summary.content`, et ne traiter `enclosure` que comme un bonus quand il est
  là.

Points à retenir, tous vérifiés dans `Entry.php` :

- **`id` est hexadécimal**, préfixé de `tag:google.com,2005:reader/item/`. Le
  jeton `continuation` et le paramètre `i` d'`edit-tag` sont, eux, **décimaux**.
  Ce sont deux représentations du même entier — la conversion est à la charge du
  client, et c'est une source d'erreur classique.
- Le contenu est dans **`summary.content`**, et **tronqué** par le serveur dans
  ce mode. Le champ `content` (non tronqué) n'apparaît que dans d'autres modes,
  inaccessibles depuis ce point d'entrée.
- **Il n'existe pas de champ « lu »** : l'état se lit dans `categories`, par la
  présence de `user/-/state/com.google/read`. Son absence signifie « non lu » —
  `…/unread` n'est jamais émis dans ce mode.
- `author`, `enclosure` et `origin.htmlUrl` sont **facultatifs**.
- `published` est en secondes ; `timestampUsec` en microsecondes ;
  `crawlTimeMsec` en millisecondes. Trois unités différentes dans le même objet.

#### `categories` mélange trois formes hétérogènes — *constaté*

C'est le piège le moins visible de cette réponse. *Constaté contre une instance
personnelle, le 2026-08-07* : un même tableau `categories` peut contenir, côte à
côte, trois natures d'entrées qui ne partagent aucune convention de nommage.

| Nature | Forme | Exemples constatés |
|---|---|---|
| État système | préfixée `user/-/state/…` | `user/-/state/com.google/reading-list`, `user/-/state/org.freshrss/main`, `user/-/state/com.google/read` |
| Catégorie (dossier) | préfixée `user/-/label/…` | `user/-/label/Sans catégorie` |
| **Étiquette utilisateur** | **texte nu, sans aucun préfixe** | `AirPods Ultra`, `iPhone Ultra`, `MacBook Ultra` |

Deux conséquences, l'une immédiate, l'autre plus sournoise.

**On ne peut pas supposer que toute entrée est préfixée.** Un client qui
découperait chaque entrée sur `user/-/label/` pour en extraire un nom lisible
laisserait tomber les étiquettes utilisateur, qui sont pourtant les plus
susceptibles d'intéresser le lecteur. Il faut traiter le cas « pas de préfixe
connu » comme un cas nominal, et non comme une donnée corrompue.

**Le test d'appartenance doit être une égalité exacte, jamais un `startsWith`
ni un `contains`.** Puisque les étiquettes utilisateur sont du texte libre, rien
n'empêche en théorie un utilisateur d'en nommer une littéralement
`user/-/state/com.google/read`. Un test approximatif ferait alors passer pour lus
tous les articles ainsi étiquetés — et, symétriquement, une étiquette contenant
le mot `read` suffirait à égarer un `contains`. L'état de lecture d'un article
est une information trop structurante pour être déduite d'une correspondance
partielle : la seule règle sûre est l'égalité de chaîne complète avec
`user/-/state/com.google/read`.

### 3.5 Pagination

Le mécanisme n'est **pas** un simple décalage. Il fonctionne ainsi, tel que lu
dans `streamContents()` :

1. La réponse porte un champ `continuation` **uniquement** si le nombre
   d'articles renvoyés atteint `n` — autrement dit, s'il peut rester quelque
   chose. Son absence signifie « fin du flux ».
2. La valeur est l'identifiant **décimal** du dernier article renvoyé.
3. La requête suivante répète les mêmes paramètres en ajoutant `c=<valeur>`.
4. Le serveur demande alors `n + 1` articles à partir de cet identifiant inclus,
   puis **écarte le premier** — celui déjà transmis.

#### Ce que l'expérience confirme — *constaté*

*Constaté contre une instance personnelle, le 2026-08-07.* Les trois
affirmations ci-dessus ne sont plus des déductions de lecture : elles ont été
éprouvées, et elles se vérifient.

**1. Le `continuation` est bien l'identifiant décimal du dernier article
renvoyé.** Une page de trois articles dont les `id` hexadécimaux se terminent
respectivement par `dde5`, `dde4` et `dde3` s'est accompagnée d'un
`continuation` valant `1786131047833059` — c'est-à-dire exactement la valeur
décimale de l'identifiant `…dde3`, le dernier de la page. La conversion
hexadécimal ↔ décimal évoquée plus haut n'est donc pas théorique : elle est la
clé de tout raisonnement sur la pagination.

**2. La page suivante ne contient aucun doublon.** Rappelée avec
`c=1786131047833059`, la requête a renvoyé `dde2`, `dde1`, `dde0` — et **pas**
`dde3`. L'article servant de curseur n'est pas retransmis : le rejet du premier
élément décrit au point 4 fonctionne comme annoncé. Un client n'a donc aucune
déduplication à faire de son côté.

**3. Un curseur invalide répète silencieusement la première page.** C'est le
point le plus grave, et il mérite d'être isolé.

> ⚠️ **Le piège le plus dangereux de cette API.** Interrogé avec
> `c=nimportequoi`, le serveur ne renvoie **aucune erreur** : ni `400`, ni
> message, ni champ signalant l'anomalie. Il répond `200`, avec **la première
> page** du flux et **le même `continuation`** que celui d'un appel sans `c` du
> tout.
>
> Constaté. La conséquence est sévère : une erreur de sérialisation du curseur —
> un entier passé sous une forme que PHP ne sait pas lire, une valeur
> hexadécimale envoyée là où le décimal est attendu, une chaîne vide, un `null`
> textuel — ne produit **jamais un échec**, mais une **boucle infinie sur la
> première page**. Le client croit paginer, reçoit indéfiniment les mêmes
> articles, et rien dans la réponse ne le lui signale.
>
> Deux protections s'imposent côté client : sérialiser le curseur en décimal de
> façon vérifiée, et **détecter la répétition** — si une page renvoie un
> `continuation` identique au précédent, ou des identifiants déjà vus, il faut
> arrêter la boucle et traiter cela comme une anomalie, jamais continuer.

#### Fin de flux — *constaté*

L'absence de `continuation` est bien le **seul** signal de fin, et il est
fiable. *Constaté contre une instance personnelle, le 2026-08-07* : appelée avec
`n=100000`, la requête a renvoyé 4645 articles — la totalité du flux — et la
réponse ne portait **aucun champ `continuation`**. Il n'y a donc pas d'autre
marqueur à chercher : un client s'arrête quand la clé est absente, point.

Au passage, `n=100000` a été **accepté sans erreur** : aucune borne supérieure
n'a été rencontrée à cette valeur. Il ne faut pas en conclure qu'il n'y en a
pas — seulement qu'aucune n'a été atteinte ici. Et surtout, demander tout le
flux d'un seul appel reste **déconseillé** : la réponse est intégralement
matérialisée en mémoire, côté serveur comme côté client, et la latence croît
avec le nombre d'articles. La pagination existe pour être utilisée.

#### Conséquences pour le client

- La pagination est **relative à un curseur**, pas à un rang : insérer un
  article en tête du flux entre deux pages ne provoque ni doublon ni saut.
- Un `c` non numérique est silencieusement ramené à `0`, c'est-à-dire au début
  du flux. Une erreur de sérialisation du curseur se manifeste donc par une
  **répétition de la première page**, jamais par une erreur — constaté.
- Le curseur n'est valable que pour un même jeu de paramètres (`n` compris) : le
  conserver en changeant de filtre n'a pas de sens.

### 3.6 Identifiants seuls

```
GET /api/greader.php/reader/api/0/stream/items/ids?s=<streamId>&n=…&c=…&xt=…
```

Mêmes paramètres de filtrage et de pagination que `stream/contents`, mais
renvoie les seuls identifiants. Utile pour réconcilier un cache local sans
retélécharger les contenus.

Ici, le flux se désigne par le paramètre **`s`**, et non par le chemin.

```
POST /api/greader.php/reader/api/0/stream/items/contents
i=<id>&i=<id>&…
```

Récupère plusieurs articles par identifiant. Paramètre `i` répété, en `POST`.

---

## 4. Écriture

### 4.1 Marquer comme lu / non lu

```
POST /api/greader.php/reader/api/0/edit-tag
Content-Type: application/x-www-form-urlencoded

T=<jeton>&a=user/-/state/com.google/read&i=<id>&i=<id>
```

| Champ | Rôle |
|---|---|
| `T` | Jeton de modification (§2.3) |
| `a` | État à **ajouter**. Répétable |
| `r` | État à **retirer**. Répétable |
| `i` | Identifiant d'article. **Répétable** |

- Marquer lu : `a=user/-/state/com.google/read`
- Marquer non lu : `r=user/-/state/com.google/read`
- Favori : `a=` / `r=user/-/state/com.google/starred`

Le champ `i` accepte les deux formes : décimale, ou hexadécimale préfixée
(`tag:google.com,2005:reader/item/…`). Le serveur détecte la forme et convertit.

**Le traitement est par lot** : un seul appel peut porter plusieurs `i`. C'est
ce qui permet à un flux Discover de grouper les marquages plutôt que d'émettre
une requête par article visible.

`user/-/state/com.google/broadcast`, `…/like` et `…/tracking-kept-unread` sont
acceptés mais **ignorés** — FreshRSS ne les implémente pas.

**Réponse** : `OK` en texte brut. Aucun corps JSON, aucun compte-rendu par
article. Un article inexistant ne produit pas d'erreur.

### 4.2 Tout marquer comme lu

```
POST /api/greader.php/reader/api/0/mark-all-as-read
T=<jeton>&s=<streamId>&ts=<horodatage>
```

`ts` est en **nanosecondes** et signifie « uniquement les articles plus anciens
que ». Il doit être composé de chiffres uniquement, sinon `400`.

Attention : trois unités de temps coexistent dans cette API — `ot`/`nt` en
secondes, `newestItemTimestampUsec` en microsecondes, `ts` en nanosecondes.

---

## 5. Codes d'erreur

| Code | Signification côté FreshRSS | Traitement attendu côté client |
|---|---|---|
| `400` | Requête mal formée, ou identifiant syntaxiquement invalide | Anomalie de saisie ou de programmation : ne pas réessayer |
| `401` | Jeton absent ou invalide, identifiant inconnu, mot de passe API changé — **ou chemin inconnu** | Retour à l'écran de connexion |
| `404` | **L'hôte n'est pas une instance FreshRSS**, ou le préfixe d'URL est faux | Adresse de serveur à corriger |
| `500` | Configuration serveur absente | Erreur serveur, réessai possible |
| `501` | `output` autre que `json` là où il est exigé | Anomalie de programmation |
| `503` | API désactivée sur le serveur | Message explicite : « activez l'API dans FreshRSS » |

> ⚠️ **`404` ne signifie pas « point d'entrée inconnu ».** L'autorisation est
> vérifiée **avant** le routage : un chemin inexistant sous `/reader/api/0/`
> répond `401`, pas `404`. Constaté sur `…/reader/api/0/nexistepas`.
>
> Conséquence : un `404` reçu d'un client authentifié désigne l'**hôte**, pas le
> chemin — mauvaise adresse de serveur, ou installation FreshRSS dans un
> sous-répertoire non pris en compte. Et un `401` ne prouve pas que les
> identifiants sont mauvais : il peut aussi trahir une faute de frappe dans un
> chemin. D'où l'intérêt de la sonde §1.1 **avant** toute tentative de connexion.

Les corps d'erreur sont en **texte brut**, jamais en JSON — constaté :
`text/plain; charset=UTF-8`. Tenter de désérialiser une réponse d'erreur
échouerait et masquerait le vrai code.

---

## 6. Points à valider contre un serveur réel

Ces éléments n'ont pas pu être établis avec certitude par la seule lecture. Ils
doivent être **constatés** avant d'être tenus pour acquis — et cette section
mise à jour en conséquence.

| # | Point | Pourquoi c'est incertain |
|---|---|---|
| 1 | Valeur maximale acceptée pour `n` | `n=100000` a été **accepté sans erreur** (§3.5) : aucune borne n'a été atteinte à cette valeur. Cela ne prouve pas qu'il n'en existe aucune — une limite peut exister en aval (mémoire, temps d'exécution PHP), et n'apparaîtrait que sur un flux plus volumineux |
| 2 | Longueur réelle de la troncature de `summary.content` | La constante `API_MAX_COMPAT_CONTENT_LENGTH` n'a pas été lue |
| 3 | Comportement de `continuation` en ordre croissant (`r=o`) | La logique de curseur a été éprouvée en ordre décroissant seulement (§3.5) ; l'ordre inverse n'a pas été essayé |
| 4 | Présence effective de `enclosure` selon les flux | Dépend des flux RSS sources, pas de FreshRSS. Constat partiel : **absente de tous les articles observés** (§3.4), ce qui suffit à décider de ne pas s'y fier |
| 5 | Nombre d'`i` acceptés dans un `edit-tag` | Limité en pratique par la taille du corps POST et `max_input_vars` de PHP |
| 6 | Forme exacte de `frss:priority` | Valeurs issues d'une énumération non lue |

Chacun de ces points est porté par une tâche dans [TASKS.md](../TASKS.md). Aucun
ne doit être « supposé » dans le code : si un Goal en a besoin, il commence par
le constater.

### Ce qui a été constaté, et vaut désormais acquis

- reconnaissance d'une instance : `GET` nu sur la racine → corps `OK` (§1.1) ;
- une chaîne de requête sur la racine → `400` ;
- `check/compatibility` : statut toujours `200`, verdict dans le corps, en-tête
  `Authorization` requis dans la requête même (§1.2) ;
- frontière `400` / `401` de `ClientLogin` : syntaxe contre existence (§2.1) ;
- utilisateur inconnu et mot de passe faux sont **indistinguables** (§2.1) ;
- chemin inconnu sous `/reader/api/0/` → `401`, jamais `404` (§5) ;
- corps d'erreur en `text/plain; charset=UTF-8` (§5) ;
- API désactivée : `503` partout **sauf** sur la sonde de reconnaissance, qui
  continue de répondre `OK` (§1.1 et §2.1).

Acquis depuis l'accès à une instance personnelle *(2026-08-07)* :

- **`ClientLogin` en succès** : `200`, `text/plain`, exactement trois lignes
  `SID` / `LSID=null` / `Auth`, `SID` et `Auth` portant la même valeur (§2.1) ;
- **le jeton de modification fait exactement 57 caractères** (§2.3) ;
- `user-info` renvoie `userId`, `userName`, `userProfileId`, `userEmail` — ce
  dernier pouvant être **vide** (§2.4) ;
- **forme réelle d'un article** : racine à quatre clés, article à onze clés,
  `origin` à trois. **`content` est absent**, **`enclosure` aussi** sur tous les
  articles observés — l'illustration doit être cherchée dans les `<img>` du
  contenu (§3.4) ;
- **`categories` mélange trois formes** : états système préfixés, catégories
  préfixées, et **étiquettes utilisateur en texte nu**. Le test d'appartenance
  doit être une **égalité exacte** (§3.4) ;
- **le `continuation` est bien l'identifiant décimal du dernier article
  renvoyé**, et la page suivante ne contient **aucun doublon** (§3.5) ;
- **un curseur invalide répète silencieusement la première page**, sans erreur
  HTTP — donc boucle infinie possible, à détecter côté client (§3.5) ;
- **l'absence de `continuation` est bien le seul signal de fin de flux** :
  4645 articles renvoyés d'un coup, sans `continuation` (§3.5) ;
- `n=100000` est **accepté sans erreur** ; aucune borne atteinte, ce qui ne veut
  pas dire qu'il n'en existe pas (§3.5 et §6, point 1).
