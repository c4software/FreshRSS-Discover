package fr.vbrosseau.freshrssdiscover.di

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Verifies that the graph builds and that each qualifier delivers the right
 * dispatcher.
 *
 * Graph validity is already checked at compile time by the Hilt processor;
 * what the compiler cannot see is an inversion between two qualifiers of the
 * same type. That is what this test covers.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DispatcherModuleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    @Before
    fun injectDependencies() {
        hiltRule.inject()
    }

    @Test
    fun eachQualifierResolvesToItsOwnDispatcher() {
        assertSame(Dispatchers.IO, ioDispatcher)
        assertSame(Dispatchers.Default, defaultDispatcher)
    }

    @Test
    fun theTwoDispatchersAreDistinct() {
        // A qualifier inversion compiles without error: only a runtime check
        // detects it.
        assertNotSame(ioDispatcher, defaultDispatcher)
    }
}
