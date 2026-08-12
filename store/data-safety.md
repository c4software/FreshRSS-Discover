# Formulaire « Sécurité des données » — réponses

À reporter dans **Play Console → Contenu de l'application → Sécurité des
données**. Chaque réponse est justifiable par le code : aucune dépendance de
télémétrie, aucun SDK d'analyse, aucun identifiant publicitaire, et un seul
hôte contacté — celui que l'utilisateur saisit lui-même.

## Collecte et partage

| Question | Réponse |
|---|---|
| Votre application collecte-t-elle ou partage-t-elle des données utilisateur ? | **Non** |
| Toutes les données collectées sont-elles chiffrées en transit ? | Sans objet — rien n'est transmis au développeur |
| Proposez-vous un moyen de supprimer les données ? | Oui — la déconnexion efface identifiants, jeton et cache ; la désinstallation supprime tout |

La subtilité, et il faut savoir la défendre à l'examen : l'application
**transmet** bien des identifiants et des statuts de lecture — mais au serveur
**de l'utilisateur**, celui dont il a saisi l'adresse, et jamais au
développeur ni à un tiers. Play ne qualifie pas de collecte un transfert vers
une destination que l'utilisateur désigne et contrôle. C'est la même situation
qu'un client de messagerie vis-à-vis du serveur IMAP que l'on lui indique.

Si le formulaire refuse « Non » et exige un détail par catégorie, aucune case
n'est à cocher : rien ne part vers nous.

## Données conservées localement (non déclarables comme collecte)

| Donnée | Nature | Support |
|---|---|---|
| Adresse du serveur, identifiant | Réglage de connexion | DataStore, stockage privé, en clair |
| Jeton de session FreshRSS | Secret | DataStore, **chiffré**, clé dans le *keystore* de l'appareil |
| Cache d'articles (titre, extrait, source, date, lien, URL d'illustration) | Contenu du flux | Room, stockage privé |
| Statuts de lecture en attente d'envoi | File de synchronisation | Room, stockage privé |
| Réglages (mode de présentation, seuils, rappel) | Préférences | DataStore, stockage privé |

**Le mot de passe d'API n'est jamais conservé** (SPECS.md §3.4) : il sert une
fois à obtenir le jeton, puis il est abandonné. C'est un point à mettre en
avant plutôt qu'à taire.

## Points de vigilance à l'examen

- **Trafic en clair autorisé.** `usesCleartextTraffic="true"` est présent, et
  c'est une décision : une instance FreshRSS auto-hébergée sur un réseau local
  n'a souvent pas de certificat (SPECS.md §3.1). L'écran de connexion prévient
  que la liaison n'est pas chiffrée dès que l'adresse commence par `http://`.
  Si l'examen le relève, c'est cette phrase-là qui répond : le clair est
  possible, il est annoncé, et il n'est jamais silencieux.
- **`ACCESS_LOCAL_NETWORK`** (Android 17+) sert à joindre une instance sur le
  réseau local. Elle ne donne aucun accès à la position ; aucune permission de
  localisation n'est déclarée.
- **Aucune bibliothèque tierce d'analyse** : ni Firebase, ni Crashlytics, ni
  identifiant publicitaire.
- **Aucun compte de notre côté.** Le compte est celui de l'utilisateur sur son
  propre serveur FreshRSS, hors de notre périmètre.
- **Aucune synchronisation en arrière-plan** : l'application ne récupère rien
  lorsqu'elle est fermée, et le rappel quotidien lit le cache local sans ouvrir
  de connexion.
