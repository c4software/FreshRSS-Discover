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

⚠️ **Ce champ est le seul du dossier qui reste à compléter, et il ne peut pas
l'être depuis le dépôt.** L'instance publique `https://demo.freshrss.org` a été
essayée le 13 août 2026 : son API refuse `demo` / `demo` (`HTTP 401
Unauthorized`). Elle ne peut donc pas servir de compte de démonstration.

Il faut, avant l'envoi, **exposer une instance FreshRSS joignable depuis
l'extérieur** — la pile de test locale ne convient pas, elle n'est pas publique
— avec l'accès API activé et un compte dédié à l'examen, puis coller ceci en
remplaçant les trois valeurs :

> L'application est un client d'un serveur FreshRSS personnel : il faut une
> instance et un compte pour dépasser l'écran de connexion.
>
> - Adresse du serveur : `https://…`
> - Identifiant : `…`
> - Mot de passe d'API : `…`
>
> Attention : le champ demandé par l'application est le **mot de passe d'API**
> de FreshRSS (Profil → Mot de passe API), distinct du mot de passe de
> connexion au site. C'est la première cause d'échec de connexion.
>
> Une fois connecté, l'écran Discover affiche le flux : faire défiler
> verticalement. L'onglet Paramètres permet de passer en mode « Swipe », où
> chaque article occupe l'écran et s'écarte d'un geste horizontal.

Ne jamais laisser d'identifiants morts dans ce champ : un examinateur qui reste
bloqué sur l'écran de connexion rejette la fiche.

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
