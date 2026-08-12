# Politique de confidentialité — FreshRSS Discover

Dernière mise à jour : 13 août 2026

## En résumé

FreshRSS Discover ne collecte rien, ne nous envoie rien et ne conserve rien
hors de votre appareil. Aucun serveur à nous, aucun compte chez nous, aucune
télémétrie, aucune publicité. Le seul serveur auquel l'application parle est
**votre propre instance FreshRSS**, dont vous saisissez l'adresse vous-même.

## Les données que l'application manipule

Tout ce qui suit reste sur votre téléphone, dans le stockage privé de
l'application, et disparaît à sa désinstallation :

| Donnée | Pourquoi | Où elle va |
|---|---|---|
| L'adresse de votre serveur FreshRSS et votre identifiant | Pour joindre votre instance | Préférences locales, en clair, sur l'appareil seulement — ce ne sont pas des secrets |
| Le jeton de session délivré par votre serveur | Pour rouvrir l'application sans se reconnecter | Préférences locales, **chiffrées**, la clé étant détenue par le *keystore* de l'appareil |
| Un cache d'articles (titre, extrait, source, date, lien, adresse de l'illustration) | Pour lire le flux hors ligne et le rouvrir tel qu'il a été laissé | Base de données locale, sur l'appareil seulement |
| Les statuts de lecture en attente d'envoi | Pour qu'une coupure réseau ne perde pas ce que vous avez lu | Base de données locale, sur l'appareil seulement |
| Vos réglages (mode de présentation, seuils de marquage, rappel) | Pour mémoriser vos choix | Préférences locales, sur l'appareil seulement |

**Votre mot de passe d'API n'est jamais conservé.** Il sert une fois, à obtenir
le jeton de session auprès de votre serveur, puis il est abandonné.

L'application ne collecte jamais votre nom, votre adresse électronique, vos
contacts, vos fichiers, votre position, ni aucun identifiant d'appareil ou de
publicité.

## Les connexions sortantes

L'application ouvre des connexions vers trois destinations, et vers aucune
autre :

1. **Votre serveur FreshRSS**, pour s'authentifier, récupérer les articles et
   renvoyer les statuts de lecture. Les identifiants que vous avez saisis sont
   envoyés à ce serveur, et à rien d'autre.
2. **Les hôtes qui servent les illustrations des articles**, pour les afficher.
   Ce sont les sites vers lesquels pointent vos flux : l'application ne les
   choisit pas, vos abonnements les désignent.
3. **Votre navigateur**, lorsque vous ouvrez un article. La page est alors prise
   en charge par le navigateur, selon ses propres règles de confidentialité.

Il n'y a **aucune synchronisation en arrière-plan** : rien n'est récupéré
pendant que l'application est fermée. Le rappel de lecture quotidien lit le
cache local et n'ouvre aucune connexion.

## Les liaisons non chiffrées

Une adresse en `http://` est acceptée, parce qu'une instance auto-hébergée sur
un réseau local n'a souvent pas de certificat. L'écran de connexion dit
clairement, dès que l'adresse commence par `http://`, que la liaison n'est pas
chiffrée. Sur une telle liaison, vos identifiants circulent en clair sur votre
réseau — c'est une décision qui vous revient, et elle est annoncée avant d'être
prise.

## Les permissions

| Permission | À quoi elle sert |
|---|---|
| `INTERNET` | Joindre votre serveur FreshRSS et charger les images des articles |
| `ACCESS_NETWORK_STATE` | Distinguer « hors ligne » de « serveur injoignable », pour basculer sur le cache au lieu d'afficher une erreur trompeuse |
| `POST_NOTIFICATIONS` | Afficher le rappel de lecture quotidien. La refuser ne supprime que le rappel |
| `ACCESS_LOCAL_NETWORK` (Android 17+) | Joindre une instance FreshRSS hébergée sur votre réseau local. Elle ne donne aucun accès à votre position |

Aucune de ces permissions ne sert à collecter des données.

## La déconnexion

Se déconnecter efface de l'appareil les identifiants conservés, le jeton de
session et le cache local.

## Les enfants

L'application ne s'adresse pas aux enfants et ne collecte de données sur
personne.

## Les évolutions

Toute modification de cette politique sera publiée dans le dépôt public de
l'application, avec la version à laquelle elle s'applique.

## Contact

Questions ou demandes : ouvrez un ticket sur
<https://github.com/c4software/FreshRSS-Discover/issues>, ou écrivez à
<c4software@gmail.com>.
