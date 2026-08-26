# Dossier de soumission — Google Play

Tout ce qu'attend la Play Console pour la première publication, prêt à coller
ou à téléverser. Les textes sont livrés en **anglais** (langue par défaut de
l'application) et en **français**.

Identifiant de l'application : `fr.vbrosseau.freshrssdiscover`
Version des captures : `v1.10.0-1-gb5d2c92` (`app-debug`) ; la capture 02 refaite depuis `v1.16.1-dirty` (`app-debug`, mode Immersif)

---

## Inventaire

| Élément | Fichier | Contrainte Play | État |
|---|---|---|---|
| Titre | [`listing/*/title.txt`](./listing) | 30 caractères max | 17 caractères |
| Description courte | [`listing/*/short-description.txt`](./listing) | 80 caractères max | 64 (en) / 69 (fr) |
| Description complète | [`listing/*/full-description.txt`](./listing) | 4 000 caractères max | ≈ 3 000 |
| Notes de version | [`release-notes/`](./release-notes) | 500 caractères max | ≈ 430 |
| Icône | [`graphics/icon-512.png`](./graphics) | 512 × 512, PNG 32 bits | conforme |
| Image mise en avant | [`graphics/feature-graphic-*.png`](./graphics) | 1 024 × 500, PNG 32 bits | conforme |
| Captures téléphone | [`screenshots/phone/en-US/`](./screenshots/phone/en-US), [`screenshots/phone/fr-FR/`](./screenshots/phone/fr-FR) | 2 à 8, ratio 9:16, 1 080 × 2 400 | 4 par langue |
| Sécurité des données | [`data-safety.md`](./data-safety.md) | formulaire | rédigé |
| Contenu de l'application | [`app-content.md`](./app-content.md) | questionnaire | rédigé |
| Accès à l'application | [`demo-server/`](./demo-server/README.md) | identifiants pour l'examinateur | Worker déployé, parcours vérifié |
| Politique de confidentialité | [`privacy-policy-en.md`](./privacy-policy-en.md), [`privacy-policy-fr.md`](./privacy-policy-fr.md) | URL publique | publiée par la CI sur GitHub Pages |

Pas de fichier de déclarations de permissions : aucune des quatre permissions
déclarées (`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`,
`ACCESS_LOCAL_NETWORK`) ne déclenche d'examen manuel.

### Les captures, une par une

| Ordre | en-US | fr-FR | Ce qu'elle montre |
|---|---|---|---|
| 1 | `01-feed-list.png` | `01-flux-liste.png` | Le fil en mode Liste : deux cartes, deux sources différentes — l'entrelacement se voit |
| 2 | `02-feed-immersive.png` | `02-flux-immersif.png` | Le mode Immersif : un article qui remplit l'écran, illustration en fond, texte en bas |
| 3 | `03-settings.png` | `03-reglages.png` | Réglages : marquage automatique et ses deux seuils, rappel de lecture, cache |
| 4 | `04-feed-dark.png` | `04-flux-sombre.png` | Le même fil en thème sombre |

---

## Avant l'envoi

1. **La politique de confidentialité se publie toute seule sur GitHub Pages.**
   Le workflow [`pages.yml`](../.github/workflows/pages.yml) la régénère depuis
   les fichiers du dépôt à chaque modification poussée sur `main` : le texte
   déclaré à Google ne peut donc pas diverger de celui qui est versionné. URL à
   renseigner dans la fiche :

   ```
   https://c4software.github.io/FreshRSS-Discover/privacy-en.html
   ```

   Version française, pour la traduction fr-FR de la fiche :

   ```
   https://c4software.github.io/FreshRSS-Discover/privacy-fr.html
   ```

   **Pages est activé**, en mode « construction par workflow », et les deux URL
   répondent (`HTTP 200`, vérifié le 13 août 2026). Il n'y a donc rien à cocher
   dans l'interface, rien à refaire. Pour mémoire, l'activation s'est faite en
   une commande — le `GITHUB_TOKEN` d'un workflow n'y suffit pas :

   ```bash
   gh api -X POST repos/c4software/FreshRSS-Discover/pages -f build_type=workflow
   ```

   Revérifier tout de même que les deux URL répondent le jour de l'envoi : une
   politique injoignable fait rejeter la fiche.
2. **Vérifier que le serveur de démonstration répond.** L'examinateur n'a pas
   de serveur FreshRSS ; le dossier lui en fournit un, décrit dans
   [`demo-server/`](./demo-server/README.md) et déployé sur Cloudflare Workers :

   ```bash
   curl -s https://freshrss-discover-demo.freshrss-discover-demo.workers.dev/api/greader.php
   ```

   Doit répondre `OK`. Un examinateur bloqué sur l'écran de connexion rejette
   la fiche, et le Worker sert aussi à **chaque mise à jour** ultérieure — il
   n'est pas jetable après la première soumission.
3. **Créer le keystore de production** hors du dépôt, et déposer les quatre
   secrets `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD` dans le dépôt GitHub — voir le README racine,
   § *Production build*.
4. **Créer le compte de service** (Cloud Console → IAM → Comptes de service),
   l'inviter dans la Play Console (Utilisateurs et autorisations → *Publier
   sur les canaux de test*), et déposer sa clé JSON dans le secret
   `PLAY_SERVICE_ACCOUNT_JSON`. Le tout premier bundle se dépose à la main :
   l'API le refuse.
5. **Produire et publier l'artefact signé** : poser une étiquette `v*`, le
   workflow `release.yml` construit, signe, et envoie le bundle en **test
   interne** avec les notes de `release-notes/`.
6. **Vérifier la version** : le `versionName` de l'artefact doit être celui de
   l'étiquette, sans suffixe. Un nom comme `1.10.0-1-gb5d2c92` signale une
   construction qui ne descend pas directement d'une étiquette : elle n'est pas
   publiable.

## Ordre de saisie dans la Play Console

1. **Créer l'application** — nom `FreshRSS Discover`, langue par défaut
   *anglais (États-Unis)*, catégorie *Actualités et magazines*, application
   gratuite.
2. **Fiche Play Store principale** (en-US) — titre, descriptions, icône, image
   mise en avant, captures. Puis ajouter la traduction **fr-FR** et y coller
   les fichiers correspondants.
3. **Contenu de l'application** — dans l'ordre : politique de confidentialité
   (URL), accès à l'application (identifiants de démonstration), publicités,
   classification du contenu, public cible, sécurité des données. Réponses dans
   [`app-content.md`](./app-content.md) et [`data-safety.md`](./data-safety.md).
4. **Version en test interne** d'abord : y téléverser l'App Bundle et les notes
   de version. Vérifier l'installation depuis le Play Store sur un appareil
   réel, contre une instance FreshRSS réelle.
5. **Production** seulement une fois le test interne concluant.

---

## Comment les captures ont été produites

Sur l'émulateur de [`envTest/`](../envTest/README.md) — Pixel 6, API 36,
1 080 × 2 400 — connecté à une **vraie** instance FreshRSS, abonnée aux
quatorze flux de [`envTest/feeds.opml`](../envTest/feeds.opml) et actualisée
juste avant la prise. La barre d'état est figée par le mode démo de SystemUI
(`10:00`, batterie pleine, Wi-Fi plein) pour que les captures ne portent ni
heure réelle ni notification étrangère.

Les articles visibles sont **réels** : titres, extraits, illustrations, âges et
noms de source viennent des flux, à travers l'API FreshRSS. Aucune image n'est
retouchée ni composée, aucun écran n'est maquetté.

Les captures françaises ont été prises en basculant la **langue de
l'application seule** (`cmd locale set-app-locales`), pas celle du système :
c'est ce qui explique que la barre d'état reste identique d'une langue à
l'autre. Les titres d'articles, eux, restent ceux des flux — un fil FreshRSS
mêle les langues, et le montrer est plus honnête que de le cacher.

**À refaire après toute évolution de l'interface.** Une fiche Play qui montre
un écran disparu vaut moins qu'une fiche sans capture.

---

## Ce qui manque encore, et pourquoi

- **Une capture de l'écran de connexion** — la montrer supposerait de se
  déconnecter, donc de vider le cache et l'état lu accumulés par la pile de
  test. L'écran est décrit dans la description longue plutôt que payé ce
  prix-là.
- **Une capture du rappel de lecture** — la seule façon de le montrer serait de
  dérouler le volet système, qui expose alors les notifications du reste de
  l'appareil.
- **Les captures tablette** — facultatives tant que l'application n'est pas mise
  en avant sur grand écran ; aucune vérification n'a été faite sur ce format.
