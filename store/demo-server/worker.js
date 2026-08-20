/**
 * Serveur de démonstration pour l'« App Access » du Play Store.
 *
 * Imite le strict nécessaire de l'API Google Reader de FreshRSS : le chemin
 * nominal connexion → affichage du flux, et rien d'autre. Les opérations
 * d'écriture (token, edit-tag) ne sont pas implémentées ; l'application les
 * appelle en arrière-plan lors du défilement et se contente d'un échec
 * silencieux.
 *
 * Déploiement : `npx wrangler deploy worker.js --name freshrss-discover-demo`
 * Identifiants à donner à Google : utilisateur `demo`, mot de passe API `demo`.
 */

const USERNAME = 'demo';
const API_PASSWORD = 'demo';

/** Jeton déterministe, comme celui de FreshRSS : il ne dépend que du compte. */
const AUTH_TOKEN = `${USERNAME}/demo0000000000000000000000000000000`;

const PREFIX = '/api/greader.php';

export default {
  async fetch(request) {
    const url = new URL(request.url);
    // L'application accepte que l'instance vive dans un sous-répertoire : on
    // ne raisonne que sur ce qui suit `/api/greader.php`.
    const index = url.pathname.indexOf(PREFIX);
    if (index === -1) return text('Not Found', 404);
    const path = url.pathname.slice(index + PREFIX.length).replace(/\/+$/, '');

    // §1.1 — reconnaissance de l'instance. Deux octets, sans retour à la ligne.
    if (path === '') return text('OK');

    // §1.2 — le verdict est dans le corps, jamais dans le statut.
    if (path === '/check/compatibility') {
      return request.headers.has('Authorization')
        ? text('PASS')
        : text('FAIL get HTTP Authorization header! Wrong Web server configuration.');
    }

    // §2.1 — ouverture de session.
    if (path === '/accounts/ClientLogin') {
      if (request.method !== 'POST') return text('Bad Request!', 400);
      const form = await request.formData();
      if (form.get('Email') !== USERNAME || form.get('Passwd') !== API_PASSWORD) {
        return text('Unauthorized!', 401);
      }
      return text(`SID=${AUTH_TOKEN}\nLSID=null\nAuth=${AUTH_TOKEN}\n`);
    }

    // §3.4 — le flux. Toute requête authentifiée passe par ici.
    if (path === '/reader/api/0/stream/contents/reading-list') {
      if (request.headers.get('Authorization') !== `GoogleLogin auth=${AUTH_TOKEN}`) {
        return text('Unauthorized!', 401);
      }
      // Pas de clé `continuation` : c'est le seul signal de fin de flux, et
      // une page unique suffit à la relecture.
      return json({
        id: 'user/-/state/com.google/reading-list',
        updated: Math.floor(Date.now() / 1000),
        items: ARTICLES.map(toItem),
      });
    }

    return text('Not Found', 404);
  },
};

/**
 * Articles fictifs, du plus récent au plus ancien.
 *
 * Les dates sont calculées à la volée : figées, elles vieilliraient et le flux
 * afficherait « il y a 8 mois » le jour de la relecture.
 *
 * Chaque `link` pointe vers une vraie page, proche du sujet de l'article :
 * le testeur Google ouvre les liens, et un domaine fictif (exemple.org est
 * parqué et couvert de publicité) a valu un rejet « Broken Functionality ».
 */
const ARTICLES = [
  {
    feed: 'Le Comptoir du Libre',
    title: 'Pourquoi votre lecteur RSS mérite mieux qu\'une liste',
    author: 'Camille Ferrand',
    summary:
      'Les flux s\'accumulent, la liste s\'allonge, et la lecture devient une corvée d\'inventaire. Un format plein écran change le rapport au non-lu.',
    hours: 2,
    link: 'https://fr.wikipedia.org/wiki/Agr%C3%A9gateur',
  },
  {
    feed: 'Carnet de veille',
    title: 'Auto-hébergement : ce que l\'on gagne, ce que l\'on paie',
    author: 'Noé Vasseur',
    summary:
      'Un serveur chez soi ne coûte pas que de l\'électricité. Retour sur trois ans de FreshRSS derrière un reverse proxy et une sauvegarde hebdomadaire.',
    hours: 5,
    link: 'https://fr.wikipedia.org/wiki/Auto-h%C3%A9bergement_(Internet)',
  },
  {
    feed: 'Fréquence Technique',
    title: 'Le protocole qui refuse de mourir',
    author: 'Salomé Ricard',
    summary:
      'Annoncé fini à chaque décennie, RSS alimente encore les podcasts, les agrégateurs et une bonne part du web indépendant.',
    hours: 9,
    link: 'https://fr.wikipedia.org/wiki/RSS',
  },
  {
    feed: 'Ligne de commande',
    title: 'Kotlin : rendre les états impossibles inexprimables',
    author: 'Yanis Perrot',
    summary:
      'Une classe scellée bien choisie supprime la moitié des tests. Petite démonstration sur un écran de connexion.',
    hours: 14,
    link: 'https://fr.wikipedia.org/wiki/Kotlin_(langage)',
  },
  {
    feed: 'Le Comptoir du Libre',
    title: 'Six agrégateurs libres passés au banc d\'essai',
    author: 'Camille Ferrand',
    summary:
      'Import OPML, API mobile, filtres, consommation mémoire : comparaison à données égales sur un millier d\'articles.',
    hours: 21,
    link: 'https://fr.wikipedia.org/wiki/FreshRSS',
  },
  {
    feed: 'Carnet de veille',
    title: 'Trier ses sources une fois par an',
    author: null,
    summary:
      'Un abonnement sur trois n\'a rien publié depuis dix-huit mois. Méthode pour élaguer sans rien perdre d\'utile.',
    hours: 27,
    link: 'https://fr.wikipedia.org/wiki/Veille_informationnelle',
  },
  {
    feed: 'Fréquence Technique',
    title: 'Ce que Material 3 change pour les listes infinies',
    author: 'Salomé Ricard',
    summary:
      'Élévation, teintes dynamiques, zones de sécurité : le défilement plein écran impose ses propres règles de lisibilité.',
    hours: 33,
    link: 'https://fr.wikipedia.org/wiki/Material_Design',
  },
  {
    feed: 'Ligne de commande',
    title: 'Marquer comme lu, hors ligne, sans rien perdre',
    author: 'Yanis Perrot',
    summary:
      'Une file d\'attente locale, un lot par requête, et le réseau redevient un détail d\'implémentation.',
    hours: 40,
    link: 'https://fr.wikipedia.org/wiki/Synchronisation_de_fichiers',
  },
  {
    feed: 'Le Comptoir du Libre',
    title: 'Les licences copyleft à l\'épreuve des magasins d\'applications',
    author: 'Margaux Delest',
    summary:
      'Publier du GPL sur une plateforme fermée reste possible, à condition de savoir ce que l\'on signe.',
    hours: 52,
    link: 'https://fr.wikipedia.org/wiki/Copyleft',
  },
  {
    feed: 'Carnet de veille',
    title: 'Un an sans algorithme de recommandation',
    author: 'Noé Vasseur',
    summary:
      'Retour d\'expérience sur une veille entièrement choisie : moins de volume, davantage de lectures menées à terme.',
    hours: 61,
    link: 'https://fr.wikipedia.org/wiki/Syst%C3%A8me_de_recommandation',
  },
  {
    feed: 'Fréquence Technique',
    title: 'Le poids réel d\'une page d\'actualité',
    author: 'Salomé Ricard',
    summary:
      'Trois mégaoctets pour huit mille signes. Décomposition ligne à ligne de ce qui transite vraiment.',
    hours: 74,
    link: 'https://fr.wikipedia.org/wiki/Page_web',
  },
  {
    feed: 'Ligne de commande',
    title: 'Tester une API que l\'on ne contrôle pas',
    author: null,
    summary:
      'Observer avant d\'implémenter, documenter ce que l\'on a observé, ne jamais inventer le comportement d\'un point d\'entrée.',
    hours: 88,
    link: 'https://fr.wikipedia.org/wiki/Interface_de_programmation',
  },
];

/** Met un article de démonstration à la forme exacte de `stream/contents`. */
function toItem(article, index) {
  const published = Math.floor(Date.now() / 1000) - article.hours * 3600;
  // Identifiant hexadécimal préfixé, comme le veut §3.4 : l'application le
  // relit en base 16 et écarte silencieusement ce qu'elle ne sait pas lire.
  const id = (0xb0b00 + index).toString(16);
  const link = article.link;

  return {
    id: `tag:google.com,2005:reader/item/${id}`,
    crawlTimeMsec: String(published * 1000),
    timestampUsec: String(published * 1000000),
    published,
    title: article.title,
    canonical: [{ href: link }],
    alternate: [{ href: link }],
    // Ni `…/read` ni `…/unread` : l'absence de l'état lu signifie non lu.
    categories: ['user/-/state/com.google/reading-list'],
    origin: {
      streamId: `feed/${slug(article.feed)}`,
      htmlUrl: 'https://fr.wikipedia.org/',
      title: article.feed,
    },
    summary: { content: `<p>${article.summary}</p>` },
    ...(article.author ? { author: article.author } : {}),
    enclosure: [
      {
        href: `https://picsum.photos/seed/frd${index + 1}/800/450`,
        type: 'image/jpeg',
      },
    ],
  };
}

function slug(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function text(body, status = 200) {
  return new Response(body, {
    status,
    headers: { 'Content-Type': 'text/plain; charset=UTF-8', 'X-Content-Type-Options': 'nosniff' },
  });
}

function json(body) {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
  });
}
