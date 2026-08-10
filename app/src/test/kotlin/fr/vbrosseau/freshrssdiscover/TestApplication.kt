package fr.vbrosseau.freshrssdiscover

import android.app.Application

/**
 * Application used by Robolectric tests instead of the real one.
 *
 * [FreshRssDiscoverApplication] starts, on creation, a settings observation
 * that lives as long as the process. In tests that process is the JVM: the
 * coroutine outlived the test that triggered it and hit an already torn-down
 * Robolectric environment, surfacing as a misleading
 * `UncaughtExceptionsBeforeTest` in the next, often unrelated, test class.
 *
 * A unit test does not need to start the whole application: each test composes
 * the objects it exercises.
 */
class TestApplication : Application()
