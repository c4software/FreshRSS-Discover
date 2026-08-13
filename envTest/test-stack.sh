#!/usr/bin/env bash
#
# Pile de test locale : un émulateur Android et une instance FreshRSS réelle.
# Voir envTest/README.md pour le pourquoi et le mode d'emploi.
#
#   ./envTest/test-stack.sh init      fabrique tout, une fois
#   ./envTest/test-stack.sh run       relance ce qui existe déjà
#   ./envTest/test-stack.sh emulator  l'émulateur seul, avec sa fenêtre
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$HERE")"
# shellcheck source=/dev/null
source "$HERE/config.env"

BASE_URL="http://${EMULATOR_HOST_ALIAS}:${FRESHRSS_PORT}"
SYSTEM_IMAGE="system-images/android-${AVD_API}/${AVD_TAG}/${AVD_ABI}"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
AVD_DIR="$AVD_HOME/${AVD_NAME}.avd"

say()  { printf '\n\033[1m▸ %s\033[0m\n' "$*"; }
warn() { printf '\033[33m  %s\033[0m\n' "$*"; }
die()  { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# Toujours viser l'émulateur **nommément**, jamais « l'appareil connecté ».
#
# Un téléphone réel branché en USB ou joignable par le réseau — c'est arrivé en
# pleine validation — fait répondre « more than one device/emulator » à chaque
# appel, et le script s'arrête sans rien avoir touché. Pire s'il n'y en avait
# qu'un : le script installerait la construction de test sur le téléphone de
# quelqu'un. Le sélecteur ferme les deux portes.
emulator_serial() {
    "$ANDROID_HOME/platform-tools/adb" devices |
        awk '/^emulator-[0-9]+\tdevice$/ { print $1; exit }'
}

adb() {
    local serial
    serial=$(emulator_serial)
    if [ -n "$serial" ]; then
        "$ANDROID_HOME/platform-tools/adb" -s "$serial" "$@"
    else
        "$ANDROID_HOME/platform-tools/adb" "$@"
    fi
}

# ─── Vérifications préalables ─────────────────────────────────────────────────
#
# Elles échouent tôt et disent quoi faire. Une pile qui démarre à moitié coûte
# plus de temps à diagnostiquer qu'un refus net.

# Ce que réclame l'émulateur seul. Séparé de `require_tools` pour que
# « emulator » ne refuse pas de démarrer faute de docker : cette commande ne
# touche jamais au conteneur.
require_emulator_tools() {
    [ -n "${ANDROID_HOME:-}" ] || die "ANDROID_HOME n'est pas définie (AGENTS.md §5)."
    [ -x "$ANDROID_HOME/emulator/emulator" ] || die "L'émulateur est absent de $ANDROID_HOME/emulator."
    [ -x "$ANDROID_HOME/platform-tools/adb" ] || die "adb est absent de $ANDROID_HOME/platform-tools."

    [ -d "$ANDROID_HOME/$SYSTEM_IMAGE" ] || {
        warn "Images système présentes :"
        find "$ANDROID_HOME/system-images" -maxdepth 3 -mindepth 3 -type d 2>/dev/null |
            sed "s|$ANDROID_HOME/||;s|^|    |" || true
        die "L'image $SYSTEM_IMAGE manque. Installez-la par le SDK Manager d'Android Studio, ou ajustez AVD_API/AVD_TAG/AVD_ABI dans envTest/config.env."
    }

    # Sans KVM l'émulateur démarre quand même, mais si lentement que la boucle
    # d'attente expirerait. Mieux vaut le dire que laisser croire à une panne.
    [ -w /dev/kvm ] || warn "/dev/kvm n'est pas accessible : le démarrage sera très lent."
}

require_tools() {
    require_emulator_tools
    command -v docker >/dev/null || die "docker est introuvable."
}

# ─── Émulateur ────────────────────────────────────────────────────────────────

create_avd() {
    # L'AVD est écrit à la main, sans `avdmanager` : les cmdline-tools ne font
    # pas partie d'une installation d'Android Studio par défaut, et exiger un
    # téléchargement de plus pour deux fichiers ini serait disproportionné.
    say "Création de l'AVD $AVD_NAME"
    mkdir -p "$AVD_DIR"

    cat > "$AVD_HOME/${AVD_NAME}.ini" <<EOF
avd.ini.encoding=UTF-8
path=$AVD_DIR
path.rel=avd/${AVD_NAME}.avd
target=android-${AVD_API}
EOF

    cat > "$AVD_DIR/config.ini" <<EOF
AvdId=$AVD_NAME
PlayStore.enabled=false
abi.type=$AVD_ABI
avd.ini.displayname=$AVD_NAME
avd.ini.encoding=UTF-8
disk.dataPartition.size=6442450944
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.audioInput=yes
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.dPad=no
hw.device.manufacturer=Google
hw.device.name=pixel_6
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.initialOrientation=Portrait
hw.keyboard=yes
hw.lcd.density=$AVD_DENSITY
hw.lcd.height=$AVD_HEIGHT
hw.lcd.width=$AVD_WIDTH
hw.mainKeys=no
hw.ramSize=$AVD_RAM_MB
hw.sdCard=no
hw.sensors.orientation=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=$SYSTEM_IMAGE/
runtime.network.latency=none
runtime.network.speed=full
showDeviceFrame=no
tag.display=$AVD_TAG
tag.id=$AVD_TAG
vm.heapSize=512
EOF
}

start_emulator() {
    if adb devices | grep -q '^emulator-.*device$'; then
        say "Émulateur déjà en ligne"
        return
    fi

    say "Démarrage de l'émulateur"
    # Deux jeux de drapeaux, pour deux usages.
    #
    # Sans fenêtre — le cas de `init` et `run` : la pile sert à des captures
    # prises par adb, pas à regarder un écran, et le rendu logiciel suffit.
    #
    # Avec fenêtre — le cas de `emulator` : ni `-no-window`, ni `-no-boot-anim`,
    # et **pas** de `-gpu` imposé. Laisser l'émulateur choisir donne le rendu
    # matériel ; forcer `swiftshader_indirect` ferait défiler une interface
    # rendue par le processeur, ce qui est précisément ce qu'on ne veut pas
    # quand on regarde à l'œil.
    local flags=(-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot)
    [ "${WITH_WINDOW:-0}" = 1 ] && flags=(-no-audio -no-snapshot)

    # `setsid` et non un simple `&` : sans lui l'émulateur reste **enfant du
    # script**, et le script l'attend à sa sortie — `init` ne rendait jamais la
    # main, alors que tout son travail était fait. Constaté à la première
    # exécution réelle du script, ce qui vaut d'être noté : le défaut ne se voit
    # pas à la lecture. `disown` retire en plus le travail de la table des jobs,
    # pour que rien ne le réclame.
    (
        cd "$ANDROID_HOME/emulator" || exit 1
        setsid ./emulator -avd "$AVD_NAME" "${flags[@]}" \
                          < /dev/null > "$HERE/.emulator.log" 2>&1 &
        disown
    )

    printf '  démarrage'
    for _ in $(seq 1 120); do
        [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && {
            printf ' prêt\n'; return
        }
        printf '.'; sleep 5
    done
    die "L'émulateur n'a pas fini de démarrer. Journal : $HERE/.emulator.log"
}

# ─── Instance FreshRSS ────────────────────────────────────────────────────────

start_freshrss() {
    if [ -n "$(docker ps -q -f "name=^${FRESHRSS_CONTAINER}$")" ]; then
        say "FreshRSS déjà en ligne"
        return
    fi
    if [ -n "$(docker ps -aq -f "name=^${FRESHRSS_CONTAINER}$")" ]; then
        say "Redémarrage du conteneur FreshRSS"
        docker start "$FRESHRSS_CONTAINER" >/dev/null
    else
        say "Création du conteneur FreshRSS"
        # CRON_MIN vide : rien ne se rafraîchit tout seul. Un flux qui bouge
        # entre deux lancements rendrait inobservable la stabilité que
        # SPECS.md §5.1 promet au démarrage.
        docker run -d --name "$FRESHRSS_CONTAINER" \
            -p "${FRESHRSS_PORT}:80" -e TZ=Europe/Paris -e CRON_MIN='' \
            "$FRESHRSS_IMAGE" >/dev/null
    fi

    printf '  attente du serveur'
    for _ in $(seq 1 60); do
        curl -fsS -o /dev/null "http://localhost:${FRESHRSS_PORT}/" 2>/dev/null && {
            printf ' prêt\n'; return
        }
        printf '.'; sleep 2
    done
    die "FreshRSS ne répond pas sur le port ${FRESHRSS_PORT}."
}

fr_cli() { docker exec "$FRESHRSS_CONTAINER" php "/var/www/FreshRSS/cli/$@"; }

provision_freshrss() {
    say "Installation de FreshRSS"
    fr_cli do-install.php --default-user "$FRESHRSS_USER" --auth-type form \
        --environment production --base-url "$BASE_URL" --language en \
        --title "FreshRSS Discover — test" --db-type sqlite --api-enabled >/dev/null

    say "Création de l'utilisateur $FRESHRSS_USER"
    fr_cli create-user.php --user "$FRESHRSS_USER" --password "$FRESHRSS_PASSWORD" \
        --api-password "$FRESHRSS_API_PASSWORD" --language en >/dev/null

    # Sans cela l'API répond 401 avec « configuration cannot be found » : les
    # fichiers créés par la CLI n'appartiennent pas à l'utilisateur du serveur
    # web. L'installateur le rappelle, et c'est facile à manquer.
    docker exec "$FRESHRSS_CONTAINER" sh /var/www/FreshRSS/cli/access-permissions.sh >/dev/null

    say "Abonnement aux flux de envTest/feeds.opml"
    docker cp "$HERE/feeds.opml" "$FRESHRSS_CONTAINER:/tmp/feeds.opml" >/dev/null
    fr_cli import-for-user.php --user "$FRESHRSS_USER" --filename /tmp/feeds.opml >/dev/null 2>&1 || true
    fr_cli actualize-user.php --user "$FRESHRSS_USER" 2>&1 | tail -1
}

check_api() {
    local auth
    auth=$(curl -fsS -X POST \
        -d "Email=${FRESHRSS_USER}&Passwd=${FRESHRSS_API_PASSWORD}" \
        "http://localhost:${FRESHRSS_PORT}/api/greader.php/accounts/ClientLogin" |
        sed -n 's/^Auth=//p')
    [ -n "$auth" ] || die "ClientLogin a échoué : l'API ne délivre pas de jeton."

    local unread
    unread=$(curl -fsS -H "Authorization: GoogleLogin auth=$auth" \
        "http://localhost:${FRESHRSS_PORT}/api/greader.php/reader/api/0/unread-count?output=json" |
        sed -n 's/.*"max":\([0-9]*\).*/\1/p')
    say "API vivante — ${unread:-0} articles non lus"
}

refresh_feeds() {
    say "Rafraîchissement des flux"

    # `actualize-user.php` respecte le TTL de chaque flux et **annonce un échec
    # quand il n'a rien eu à rafraîchir** — « actualized 0 feeds » suivi de
    # « failed! », sur une sortie à zéro. Relayer cela tel quel ferait lire une
    # panne là où il n'y a qu'un flux encore frais, et c'est le cas courant :
    # deux `run` à moins d'une heure d'intervalle.
    local out
    out=$(fr_cli actualize-user.php --user "$FRESHRSS_USER" 2>&1 || true)

    if printf '%s' "$out" | grep -q 'actualized 0 feeds'; then
        printf '  aucun flux à rafraîchir : leur TTL n'"'"'est pas écoulé.\n'
    else
        printf '%s' "$out" | grep 'actualized' | sed 's/^/  /' || printf '  %s\n' "$(printf '%s' "$out" | tail -1)"
    fi
}

# ─── Application ──────────────────────────────────────────────────────────────

install_app() {
    say "Construction et installation de l'application"
    ( cd "$ROOT" && ./gradlew :app:assembleDebug -q )

    # `sys.boot_completed` ne dit pas que le gestionnaire de paquets répond :
    # juste après un démarrage à froid, `adb install` échoue encore. Constaté —
    # le script annonçait alors la pile prête sur l'**ancienne** application, ce
    # qui est le pire des cas : on valide en croyant regarder son travail.
    for _ in $(seq 1 60); do
        adb shell pm path android >/dev/null 2>&1 && break
        sleep 2
    done

    # Deux précautions, et chacune répond à un fait constaté.
    #
    # `adb install` **sort en 0 même quand il échoue** : c'est sa sortie qu'il
    # faut lire, pas son code de retour. Sans cela le script annonçait la pile
    # prête sur l'**ancienne** application — le pire des cas, puisqu'on valide
    # alors en croyant regarder son travail.
    #
    # Et il faut **réessayer** : après un démarrage à froid l'installation
    # échoue encore un moment, quand bien même `sys.boot_completed` est vrai et
    # que `pm` répond. Le second appel passe. Plutôt que de deviner la bonne
    # condition d'attente, on retente — puis on abandonne franchement.
    local out
    for attempt in $(seq 1 6); do
        out=$(adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" 2>&1 || true)
        if printf '%s' "$out" | grep -q 'Success'; then
            printf '  installée\n'
            return
        fi
        [ "$attempt" -eq 1 ] && warn "installation refusée, nouvel essai"
        sleep 5
    done

    die "L'installation a échoué six fois :"$'\n'"$out"
    # Accordée d'office : la boîte de dialogue système recouvre l'écran de
    # connexion et fausserait la première capture.
    adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
}

# ─── Extinction ───────────────────────────────────────────────────────────────

stop_stack() {
    # Éteindre, pas détruire : l'AVD, le conteneur, l'utilisateur, les flux et
    # les articles lus survivent, et `run` les retrouve. C'est la différence
    # avec la ligne « tout détruire » du récapitulatif.
    if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ] &&
       adb devices | grep -q '^emulator-.*device$'; then
        say "Extinction de l'émulateur"
        adb emu kill >/dev/null 2>&1 || true
        for _ in $(seq 1 30); do
            adb devices | grep -q '^emulator-.*device$' || break
            sleep 1
        done
    else
        say "Émulateur déjà éteint"
    fi

    if command -v docker >/dev/null && [ -n "$(docker ps -q -f "name=^${FRESHRSS_CONTAINER}$")" ]; then
        say "Arrêt du conteneur FreshRSS"
        docker stop "$FRESHRSS_CONTAINER" >/dev/null
    else
        say "Conteneur FreshRSS déjà arrêté"
    fi

    printf '\n  Pile éteinte. « run » la relance avec son contenu intact.\n\n'
}

emulator_summary() {
    cat <<EOF

┌─ Émulateur prêt ────────────────────────────────────────────────────────────
│  Rien d'autre n'a été démarré : ni conteneur FreshRSS, ni construction.
│
│  Lancer l'application    adb shell am start -n ${APP_ID}/.MainActivity
│  Réinstaller             ./gradlew :app:installDebug
│  Repartir à zéro         adb shell pm clear ${APP_ID}
│
│  Éteindre                ./envTest/test-stack.sh stop
└─────────────────────────────────────────────────────────────────────────────
EOF
}

summary() {
    cat <<EOF

┌─ Pile de test prête ────────────────────────────────────────────────────────
│  FreshRSS (navigateur)   http://localhost:${FRESHRSS_PORT}
│  FreshRSS (émulateur)    ${BASE_URL}
│  Identifiant             ${FRESHRSS_USER}
│  Mot de passe API        ${FRESHRSS_API_PASSWORD}
│
│  Lancer l'application    adb shell am start -n ${APP_ID}/.MainActivity
│  Capturer l'écran        adb exec-out screencap -p > ecran.png
│  Journal de l'API        adb logcat -s FreshRssApi
│
│  Éteindre (fin de Goal)  ./envTest/test-stack.sh stop
│  Tout détruire           docker rm -f ${FRESHRSS_CONTAINER} && rm -rf ${AVD_DIR} ${AVD_HOME}/${AVD_NAME}.ini
└─────────────────────────────────────────────────────────────────────────────
EOF
}

# ─── Entrée ───────────────────────────────────────────────────────────────────

case "${1:-}" in
    init)
        require_tools
        [ -d "$AVD_DIR" ] && warn "L'AVD $AVD_NAME existe déjà, il est réécrit."
        [ -n "$(docker ps -aq -f "name=^${FRESHRSS_CONTAINER}$")" ] &&
            die "Le conteneur ${FRESHRSS_CONTAINER} existe déjà. « run » le réutilise ; pour repartir de zéro : docker rm -f ${FRESHRSS_CONTAINER}"
        create_avd
        start_emulator
        start_freshrss
        provision_freshrss
        check_api
        install_app
        summary
        ;;
    run)
        require_tools
        [ -d "$AVD_DIR" ] || die "L'AVD $AVD_NAME n'existe pas. Lancez « init » d'abord."
        [ -n "$(docker ps -aq -f "name=^${FRESHRSS_CONTAINER}$")" ] ||
            die "Le conteneur ${FRESHRSS_CONTAINER} n'existe pas. Lancez « init » d'abord."
        start_emulator
        start_freshrss
        refresh_feeds
        check_api
        install_app
        summary
        ;;
    emulator)
        # L'émulateur seul, avec sa fenêtre, et rien d'autre : ni conteneur, ni
        # construction, ni installation. C'est la commande de l'essai à la main
        # — sur la pile locale comme sur le serveur de démonstration de
        # store/demo-server/, qui n'a besoin d'aucun FreshRSS ici.
        #
        # Ce qui est déjà installé sur l'AVD le reste, session ouverte comprise.
        #
        # La fenêtre est le défaut, mais elle se refuse : `WITH_WINDOW=0` rend
        # la commande utilisable sans écran — validation automatisée, session
        # SSH, machine sans serveur graphique — là où `init` et `run`
        # imposeraient le conteneur FreshRSS dont ces cas n'ont que faire.
        require_emulator_tools
        [ -d "$AVD_DIR" ] || die "L'AVD $AVD_NAME n'existe pas. Lancez « init » d'abord."
        WITH_WINDOW="${WITH_WINDOW:-1}" start_emulator
        emulator_summary
        ;;
    stop)
        stop_stack
        ;;
    *)
        cat <<EOF
Pile de test locale — un émulateur Android et une instance FreshRSS réelle.

  ./envTest/test-stack.sh init   fabrique tout : AVD, conteneur, utilisateur,
                                 flux, puis construit et installe l'application
  ./envTest/test-stack.sh run    relance ce qui existe déjà, rafraîchit les
                                 flux et réinstalle l'application
  ./envTest/test-stack.sh emulator
                                 l'émulateur seul, **avec sa fenêtre**, pour
                                 essayer à la main : ni conteneur, ni
                                 construction, ni installation
  ./envTest/test-stack.sh stop   éteint émulateur et conteneur, sans rien
                                 détruire — à faire à la fin de chaque Goal

Configuration : envTest/config.env — flux : envTest/feeds.opml
Pourquoi cette pile existe : envTest/README.md
EOF
        exit 1
        ;;
esac
