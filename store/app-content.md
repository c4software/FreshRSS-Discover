# Fiche « Contenu de l'application » — réponses

## Classification du contenu (questionnaire IARC)

| Question | Réponse |
|---|---|
| Catégorie | Actualités et magazines (application utilitaire, pas un jeu) |
| Violence, sexualité, langage grossier, substances, jeux d'argent | Non — l'application n'apporte aucun contenu propre |
| Contenu généré par les utilisateurs | Non — les articles proviennent des flux auxquels l'utilisateur s'est abonné sur **son** serveur, il n'y a ni publication ni espace communautaire |
| Communication entre utilisateurs | Non |
| Partage de la position | Non — aucune permission de localisation n'est déclarée |
| Achats numériques | Non |
| Publicités | Non |

**Le point à ne pas escamoter : l'application ouvre des liens dans le
navigateur**, et le contenu des flux n'est ni choisi ni modéré par nous — c'est
l'utilisateur qui décide de ses abonnements, sur son propre serveur. Le
questionnaire demande si l'application donne accès à du contenu Internet non
filtré : la réponse honnête est **oui**, par l'ouverture d'un article dans le
navigateur. Répondre « non » ici est le genre d'inexactitude qui fait
reclasser une fiche après coup.

Classification attendue avec cette réponse : **adolescents / 12+** selon les
territoires, plutôt que « tout public ». C'est le prix de la réponse juste, et
il est faible.

## Public cible et enfants

| Question | Réponse |
|---|---|
| Tranches d'âge visées | 18 ans et plus |
| L'application attire-t-elle les enfants ? | Non — client d'un serveur FreshRSS auto-hébergé |
| Conforme à la Families Policy | Sans objet (non destinée aux familles) |

## Publicités

Aucune publicité. Répondre **« Non, mon application ne contient pas de
publicités »**.

## Accès à l'application

Toutes les fonctionnalités passent par un compte — mais **il n'est pas chez
nous** : c'est celui de l'utilisateur sur son propre serveur FreshRSS. La
Play Console exige alors des identifiants de démonstration pour l'examinateur.
Sans eux, il ne verra que l'écran de connexion et la fiche sera rejetée.

L'instance publique `https://demo.freshrss.org` a été essayée le 13 août 2026 :
son API refuse `demo` / `demo` (`HTTP 401 Unauthorized`). Elle ne peut donc pas
servir de compte de démonstration.

Le dossier fournit à la place un **serveur de démonstration**, déployé sur
Cloudflare Workers : [`demo-server/`](./demo-server/README.md). Il imite le
chemin nominal de l'API FreshRSS et sert douze articles fictifs, sans que
l'application soit modifiée. Parcours vérifié de bout en bout sur l'émulateur
le 13 août 2026.

Réponses à saisir — « Toutes les fonctionnalités sont accessibles avec des
identifiants », nom d'utilisateur `demo`, mot de passe `demo`, puis dans les
instructions :

> L'application est un client pour FreshRSS, un agrégateur RSS auto-hébergé.
> Chaque utilisateur se connecte à son propre serveur ; l'adresse ci-dessous
> est une instance de démonstration fournie pour l'examen.
>
> 1. Adresse du serveur :
>    `https://freshrss-discover-demo.freshrss-discover-demo.workers.dev`
> 2. Identifiant : `demo`
> 3. Mot de passe d'API : `demo`
>
> Le troisième champ est le **mot de passe d'API** de FreshRSS (Profil → Mot de
> passe API), distinct du mot de passe de connexion au site. C'est la première
> cause d'échec de connexion. Ici les deux valent `demo`.
>
> Une fois connecté, l'écran Discover affiche le flux : faire défiler
> verticalement. L'onglet Paramètres permet de passer en mode « Immersif », où
> chaque article remplit l'écran et laisse place au suivant d'un geste vers le
> haut.

Ne jamais laisser d'identifiants morts dans ce champ : un examinateur qui reste
bloqué sur l'écran de connexion rejette la fiche. Le Worker doit donc rester en
ligne après la première soumission — Google le réutilise à chaque mise à jour.

## Application gouvernementale, finance, santé

Non à tout.

## Sécurité des données

Voir [`data-safety.md`](./data-safety.md).

## Déclarations de permissions à examen manuel

**Aucune.** L'application ne déclare ni permission d'accès à tous les fichiers,
ni SMS, ni journal d'appels, ni service de premier plan, ni
`QUERY_ALL_PACKAGES`. Les quatre permissions déclarées — `INTERNET`,
`ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `ACCESS_LOCAL_NETWORK` — ne
déclenchent pas de formulaire dédié.

## Politique de confidentialité

Publiée sur GitHub Pages par le workflow
[`pages.yml`](../.github/workflows/pages.yml) :

```
https://c4software.github.io/FreshRSS-Discover/privacy-en.html
```

Version française pour la fiche fr-FR :

```
https://c4software.github.io/FreshRSS-Discover/privacy-fr.html
```
