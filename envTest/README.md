# envTest — pile de test locale

Un émulateur Android et une **vraie** instance FreshRSS, montés sur la machine
de développement en deux commandes. Optionnel : rien dans la vérification
d'[AGENTS.md](../AGENTS.md) §5 n'en dépend, et la CI l'ignore.

```bash
./envTest/test-stack.sh init       # une fois : fabrique tout
./envTest/test-stack.sh run        # ensuite : relance et réinstalle
./envTest/test-stack.sh emulator   # l'émulateur seul, avec sa fenêtre
./envTest/test-stack.sh stop       # à la fin de chaque Goal : éteint tout
```

> **Éteindre à la fin de chaque Goal, toujours** ([AGENTS.md](../AGENTS.md)
> §5.3). Un émulateur retient quatre gigaoctets et un cœur, un conteneur retient
> un port ; laissés en marche, ils sont payés par toutes les tâches suivantes, y
> compris celles qui n'en avaient pas besoin. `stop` éteint sans rien détruire —
> l'AVD, le conteneur, l'utilisateur, les flux et l'état lu accumulé survivent,
> et `run` les retrouve. L'extinction ne coûte donc jamais rien, et c'est
> précisément pourquoi il n'y a pas d'excuse à l'oublier.

---

## Pourquoi elle existe

Le dépôt compte plus de cinq cents tests et une soixantaine de captures, et
cela n'a jamais suffi. `GOAL-001-T22` avait relevé **trois défauts sur trois**
qu'aucun test n'avait vus — un écran passant sous la barre d'état, un titre noir
sur fond noir, deux titres empilés. Chacun n'est apparu qu'à la première
exécution réelle.

Cette pile a fait la même chose le jour de sa création. La toute première
tentative de connexion a échoué sur :

```
java.net.UnknownServiceException: CLEARTEXT communication to 10.0.2.2
not permitted by network security policy
```

[SPECS.md](../SPECS.md) §3.1 promet depuis l'origine que le schéma `http://`
reste accepté — les instances auto-hébergées sur réseau local sont un cas
réel — et l'écran de connexion va jusqu'à prévenir que la liaison n'est pas
chiffrée. Le manifeste, lui, ne l'autorisait nulle part. **Aucune instance en
clair n'était joignable**, et l'échec se présentait en « le serveur ne répond
pas » : le diagnostic qui envoie l'utilisateur chercher la panne du mauvais
côté. Quatorze Goals avaient passé dessus. Voir `GOAL-022` dans
[TASKS.md](../TASKS.md).

Aucun test ne pouvait l'attraper : le `MockEngine` de Ktor n'a pas de politique
réseau, et une capture Roborazzi ne franchit aucune couche de transport.

**La leçon, et c'est elle qui justifie ce dossier :** ce qui vit sous la couche
de transport, dans le manifeste ou dans la plateforme, ne se vérifie qu'en
exécutant. Avant d'annoncer qu'un parcours fonctionne, l'exécuter.

---

## Ce que le dossier contient

| Fichier | Rôle |
|---|---|
| `test-stack.sh` | Les deux commandes, `init` et `run` |
| `config.env` | Ports, identifiants, définition de l'AVD, image système |
| `feeds.opml` | Les flux auxquels l'instance de test s'abonne |

Rien de secret n'y figure, et rien ne doit y arriver : les identifiants
n'ouvrent qu'un conteneur jetable, sur cette machine.

---

## `init`, `run`, `stop`

`init` fabrique, `run` réutilise, `stop` éteint. La distinction entre les deux
premières n'est pas cosmétique : `init` réinstalle FreshRSS et **efface son
contenu**, ce qui emporte l'état lu accumulé par les essais précédents.

| | `init` | `run` | `stop` |
|---|---|---|---|
| AVD | créé (réécrit s'il existe) | réutilisé | conservé |
| Émulateur | démarré | démarré s'il ne l'est pas | **éteint** |
| Conteneur FreshRSS | créé — **refuse** s'il existe déjà | redémarré | **arrêté**, non supprimé |

### `emulator`, à part

`emulator` ne fait qu'une chose : démarrer l'AVD **avec sa fenêtre**, sans
conteneur, sans construction, sans installation. C'est la commande de l'essai à
la main, quand on veut regarder l'application plutôt que la photographier.

La différence de drapeaux n'est pas cosmétique. `init` et `run` lancent en
`-no-window -gpu swiftshader_indirect` : le rendu est logiciel, ce qui suffit à
une capture prise par `adb`. `emulator` laisse l'émulateur choisir son GPU —
forcer le rendu logiciel donnerait une interface qui défile au processeur,
exactement ce qu'on ne veut pas sous les yeux.

Elle n'exige pas `docker`, n'y touchant jamais, et se contente de l'AVD
existant : ce qui y est installé le reste, session ouverte comprise. Elle sert
aussi bien à essayer l'application contre le [serveur de démonstration du
dossier Play](../store/demo-server/README.md), qui n'a besoin d'aucune instance
FreshRSS locale.

La fenêtre est le défaut, pas une obligation :

```bash
WITH_WINDOW=0 ./envTest/test-stack.sh emulator
```

démarre le même émulateur **sans écran**, drapeaux de `init` et `run` compris.
C'est la forme à employer pour une validation automatisée, une session SSH ou
une machine sans serveur graphique — les cas où `init` et `run` imposeraient un
conteneur FreshRSS dont on n'a que faire.

`init` et `run`, eux, sont **inchangés** : toujours `-no-window`, toujours le
rendu logiciel. Rien de ce qui produisait les captures existantes n'a bougé.
| Utilisateur, mot de passe API, flux | créés | conservés | conservés |
| Flux | récupérés | rafraîchis | — |
| Application | construite et installée | construite et installée | — |

`init` **refuse** de s'exécuter sur un conteneur existant plutôt que de
l'écraser : les articles lus d'une session de test sont parfois exactement ce
qu'on cherchait à observer. Pour repartir de zéro, le dire explicitement :

```bash
docker rm -f freshrss-test && ./envTest/test-stack.sh init
```

---

## Se connecter depuis l'application

Le script affiche ces valeurs à la fin. À saisir dans l'écran de connexion :

| Champ | Valeur |
|---|---|
| Adresse du serveur | `http://10.0.2.2:8088` |
| Identifiant | `discover` |
| Mot de passe API | `ApiTest2026` |

`10.0.2.2` est l'adresse par laquelle l'émulateur atteint la machine hôte :
`localhost` y désignerait l'émulateur lui-même. Et `http://`, pas `https://` —
c'est le cas que la pile sert à couvrir.

Remplir les champs sans toucher l'écran, la tabulation faisant le passage de
l'un à l'autre — taper aux coordonnées échoue dès que le clavier redispose la
page :

```bash
adb shell input tap 540 559
adb shell input text 'http://10.0.2.2:8088'
adb shell input keyevent 61 && adb shell input text 'discover'
adb shell input keyevent 61 && adb shell input text 'ApiTest2026'
adb shell input keyevent 111   # refermer le clavier
```

---

## Ce que cette pile ne remplace pas

- **Les captures Roborazzi.** Elles restent le garde-fou du rendu, en clair et
  en sombre, et elles sont figées sur `fr-rFR` : l'émulateur ne les remplace ni
  ne les met à jour (AGENTS.md §4.1).
- **Un appareil réel.** Un émulateur x86 ne dit rien du comportement d'un
  appareil sous mémoire contrainte, ni des onglets personnalisés de tel
  navigateur, ni du sélecteur de partage tel que le constructeur l'a habillé.

Ce qu'elle apporte, et que rien d'autre n'apporte : le parcours **entier**,
depuis un serveur qui répond vraiment jusqu'aux pixels affichés.
