package fr.vbrosseau.freshrssdiscover.presentation.settings

import fr.vbrosseau.freshrssdiscover.domain.settings.ReadingSettings
import kotlin.math.roundToInt

/**
 * Pas du curseur de proportion visible, en points de pourcentage.
 *
 * Les crans ne sont pas une facilité d'implémentation : personne ne distingue
 * 62 % de 65 % à l'usage, et un curseur continu promettrait une précision qui
 * n'existe pas — tout en rendant la valeur voulue difficile à atteindre au
 * pouce (SPECS.md §7.1). Cinq positions se visent sans effort.
 */
private const val VISIBLE_FRACTION_PERCENT_STEP = 20

/** Pas du curseur de durée, en secondes. Une seconde est la plus petite différence perceptible ici. */
private const val CONTINUOUS_VISIBILITY_SECONDS_STEP = 1

private const val PERCENT = 100
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Ce que l'écran de réglages affiche, entièrement dérivé par le ViewModel.
 *
 * Rien n'y est calculable depuis un Composable (AGENTS.md §9) : les seuils sont
 * déjà convertis dans l'unité affichée — pourcentage et secondes — parce que
 * passer de `0.6f` et `1_000L` à « 60 % » et « 1 s » est un calcul, et qu'il
 * n'a pas sa place dans une fonction de rendu.
 */
data class SettingsUiState(
    /**
     * Session observée, `null` tant qu'elle n'est pas lue ou après déconnexion.
     *
     * L'écran distingue les deux affichages : sans compte, il n'y a ni adresse
     * ni identifiant à montrer, et la déconnexion n'a plus d'objet.
     */
    val account: SettingsAccount? = null,
    /** Part de hauteur affichée exigée par SPECS.md §4.5, en pourcentage entier. */
    val visibleFraction: SettingsThreshold = visibleFractionThresholdOf(ReadingSettings.Default),
    /** Durée d'affichage continu exigée par SPECS.md §4.5, en secondes. */
    val continuousVisibility: SettingsThreshold = continuousVisibilityThresholdOf(ReadingSettings.Default),
    /** Nom de version de l'application, tel que produit par la compilation. */
    val appVersion: String = "",
    /**
     * Vraie pendant que la confirmation de déconnexion est posée.
     *
     * Dans l'état publié et non dans l'écran : SPECS.md §3.5 fait de la
     * confirmation une étape du geste, et une `rememberSaveable` locale la
     * rendrait intestable depuis le ViewModel.
     */
    val isSignOutConfirmationVisible: Boolean = false,
)

/**
 * Un seuil réglable, déjà exprimé dans son unité d'affichage.
 *
 * Les bornes accompagnent la valeur au lieu d'être écrites dans l'écran :
 * elles viennent de `ReadingSettings`, et les recopier côté interface
 * recréerait exactement la duplication que cette tâche supprime — un curseur
 * qui laisserait choisir une valeur que le dépôt refuse d'enregistrer.
 *
 * [stepCount] est le nombre de crans **intermédiaires**, au sens du `Slider` de
 * Material 3 : cinq positions font quatre intervalles, donc trois crans entre
 * les extrémités.
 */
data class SettingsThreshold(
    val value: Int,
    val range: IntRange,
    val stepCount: Int,
)

/** Le compte connecté, en lecture seule (SPECS.md §6). */
data class SettingsAccount(
    val serverAddress: String,
    val username: String,
)

/** Convertit la fraction du domaine en pourcentage entier, bornes comprises. */
fun visibleFractionThresholdOf(settings: ReadingSettings): SettingsThreshold {
    val lowest = percentOf(ReadingSettings.VisibleFractionRange.start)
    val highest = percentOf(ReadingSettings.VisibleFractionRange.endInclusive)
    val range = lowest..highest
    return SettingsThreshold(
        value = percentOf(settings.visibleFraction),
        range = range,
        stepCount = stepCountOf(range, VISIBLE_FRACTION_PERCENT_STEP),
    )
}

/** Convertit la durée du domaine en secondes entières, bornes comprises. */
fun continuousVisibilityThresholdOf(settings: ReadingSettings): SettingsThreshold {
    val lowest = secondsOf(ReadingSettings.ContinuousVisibilityRange.first)
    val highest = secondsOf(ReadingSettings.ContinuousVisibilityRange.last)
    val range = lowest..highest
    return SettingsThreshold(
        value = secondsOf(settings.continuousVisibilityMillis),
        range = range,
        stepCount = stepCountOf(range, CONTINUOUS_VISIBILITY_SECONDS_STEP),
    )
}

private fun percentOf(fraction: Float): Int = (fraction * PERCENT).roundToInt()

private fun secondsOf(millis: Long): Int = (millis / MILLIS_PER_SECOND).toInt()

/**
 * Nombre de crans intermédiaires d'un curseur couvrant [range] par pas de [step].
 *
 * Retirer 1 est ce qui distingue les crans des positions : sans cela, le
 * curseur offrirait une position de plus que la plage n'en contient.
 */
private fun stepCountOf(range: IntRange, step: Int): Int = (range.last - range.first) / step - 1
